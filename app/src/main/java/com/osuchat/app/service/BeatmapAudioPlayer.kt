package com.osuchat.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.osuchat.app.domain.model.AudioPlaybackState
import com.osuchat.app.domain.model.AudioQueueTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BeatmapAudioPlayer @Inject constructor(
    @ApplicationContext context: Context
) {

    companion object {
        private const val DUCK_VOLUME = 0.2f
        private const val FULL_VOLUME = 1.0f
    }

    private enum class PlayerState {
        IDLE,
        INITIALIZING,
        PREPARING,
        PREPARED,
        STARTED,
        PAUSED,
        COMPLETED,
        ERROR
    }

    private val appContext: Context = context.applicationContext
    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playerLock = Any()

    private var mediaPlayer: MediaPlayer? = null
    private var playerState: PlayerState = PlayerState.IDLE
    private var activeBeatmapsetId: Long? = null
    private var playerGeneration: Long = 0L
    private var tickerJob: Job? = null

    private var queueTracks: List<AudioQueueTrack> = emptyList()
    private var currentQueueIndex: Int = -1
    private var autoAdvanceEnabled: Boolean = false

    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false
    private var pausedByTransientFocusLoss: Boolean = false
    private var pauseWhenPrepared: Boolean = false
    private var isDucked: Boolean = false
    private var isNoisyReceiverRegistered: Boolean = false

    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            synchronized(playerLock) {
                pausedByTransientFocusLoss = false
                pauseFromExternalEventLocked(abandonFocus = true)
            }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        synchronized(playerLock) {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    hasAudioFocus = false
                    pausedByTransientFocusLoss = false
                    isDucked = false
                    pauseFromExternalEventLocked(abandonFocus = true)
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    hasAudioFocus = false
                    isDucked = false
                    if (
                        playerState == PlayerState.STARTED ||
                        playerState == PlayerState.PREPARING ||
                        playerState == PlayerState.INITIALIZING
                    ) {
                        pausedByTransientFocusLoss = true
                        pauseFromExternalEventLocked(abandonFocus = false)
                    }
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    isDucked = true
                    val mp = mediaPlayer
                    if (
                        mp != null &&
                        (playerState == PlayerState.STARTED ||
                            playerState == PlayerState.PREPARED ||
                            playerState == PlayerState.PAUSED)
                    ) {
                        runCatching { mp.setVolume(DUCK_VOLUME, DUCK_VOLUME) }
                    }
                }

                AudioManager.AUDIOFOCUS_GAIN -> {
                    hasAudioFocus = true
                    isDucked = false
                    val mp = mediaPlayer
                    if (
                        mp != null &&
                        (playerState == PlayerState.STARTED ||
                            playerState == PlayerState.PREPARED ||
                            playerState == PlayerState.PAUSED)
                    ) {
                        runCatching { mp.setVolume(FULL_VOLUME, FULL_VOLUME) }
                    }
                    if (pausedByTransientFocusLoss) {
                        pausedByTransientFocusLoss = false
                        pauseWhenPrepared = false
                        val id = activeBeatmapsetId
                        if (
                            mp != null &&
                            id != null &&
                            (playerState == PlayerState.PREPARED || playerState == PlayerState.PAUSED)
                        ) {
                            startPlayerLocked(mp, id, playerGeneration)
                        }
                    }
                }
            }
        }
    }

    fun togglePlay(beatmapsetId: Long, previewUrl: String) {
        togglePlayPause(beatmapsetId, previewUrl)
    }

    fun togglePlayPause(beatmapsetId: Long, previewUrl: String) {
        synchronized(playerLock) {
            val player = mediaPlayer
            if (activeBeatmapsetId == beatmapsetId && player != null) {
                togglePlayPauseLocked(beatmapsetId, previewUrl)
                return
            }

            val existingIndex = queueTracks.indexOfFirst { it.beatmapsetId == beatmapsetId }
            if (existingIndex >= 0) {
                currentQueueIndex = existingIndex
            } else {
                queueTracks = listOf(
                    AudioQueueTrack(
                        beatmapsetId = beatmapsetId,
                        title = "Beatmap #$beatmapsetId",
                        coverUrl = "https://assets.ppy.sh/beatmaps/$beatmapsetId/covers/list.jpg",
                        previewAudioUrl = previewUrl
                    )
                )
                currentQueueIndex = 0
            }
            playNewLocked(beatmapsetId, previewUrl)
        }
    }

    fun playQueue(
        tracks: List<AudioQueueTrack>,
        startIndex: Int = 0,
        autoAdvance: Boolean = false
    ) {
        val validTracks = tracks.filter { it.beatmapsetId > 0L }
        if (validTracks.isEmpty()) {
            stopAndClear()
            return
        }
        val targetIndex = startIndex.coerceIn(0, validTracks.lastIndex)
        val targetTrack = validTracks[targetIndex]

        synchronized(playerLock) {
            val sameQueueAndTrack = queueTracks == validTracks &&
                currentQueueIndex == targetIndex &&
                activeBeatmapsetId == targetTrack.beatmapsetId &&
                mediaPlayer != null
            if (sameQueueAndTrack) {
                autoAdvanceEnabled = autoAdvance
                togglePlayPauseLocked(targetTrack.beatmapsetId, targetTrack.previewAudioUrl)
                return
            }

            val alreadyActiveSet = activeBeatmapsetId == targetTrack.beatmapsetId &&
                mediaPlayer != null &&
                (playerState == PlayerState.STARTED ||
                    playerState == PlayerState.PREPARED ||
                    playerState == PlayerState.PREPARING ||
                    playerState == PlayerState.INITIALIZING)
            queueTracks = validTracks
            currentQueueIndex = targetIndex
            autoAdvanceEnabled = autoAdvance

            if (alreadyActiveSet) {
                _state.value = buildStateLocked(
                    beatmapsetId = _state.value.beatmapsetId,
                    isPlaying = _state.value.isPlaying,
                    isPreparing = _state.value.isPreparing,
                    progress = _state.value.progress,
                    error = null
                )
            } else {
                playNewLocked(targetTrack.beatmapsetId, targetTrack.previewAudioUrl)
            }
        }
    }

    fun toggleCurrentPlayPause() {
        synchronized(playerLock) {
            val track = queueTracks.getOrNull(currentQueueIndex)
            if (track != null) {
                togglePlayPauseLocked(track.beatmapsetId, track.previewAudioUrl)
            } else {
                val activeId = activeBeatmapsetId ?: _state.value.beatmapsetId ?: return
                togglePlayPauseLocked(activeId, "")
            }
        }
    }

    fun playNext() {
        synchronized(playerLock) {
            if (queueTracks.isEmpty()) return
            val nextIndex = currentQueueIndex + 1
            if (nextIndex !in queueTracks.indices) return
            currentQueueIndex = nextIndex
            val nextTrack = queueTracks[nextIndex]
            playNewLocked(nextTrack.beatmapsetId, nextTrack.previewAudioUrl)
        }
    }

    fun playPrevious() {
        synchronized(playerLock) {
            if (queueTracks.isEmpty()) return
            val prevIndex = currentQueueIndex - 1
            if (prevIndex !in queueTracks.indices) {
                val current = queueTracks.getOrNull(currentQueueIndex) ?: return
                playNewLocked(current.beatmapsetId, current.previewAudioUrl)
                return
            }
            currentQueueIndex = prevIndex
            val prevTrack = queueTracks[prevIndex]
            playNewLocked(prevTrack.beatmapsetId, prevTrack.previewAudioUrl)
        }
    }

    fun stopAndClear() {
        synchronized(playerLock) {
            stopLocked()
        }
    }

    private fun togglePlayPauseLocked(beatmapsetId: Long, previewUrl: String) {
        val player = mediaPlayer
        if (activeBeatmapsetId == beatmapsetId && player != null) {
            when (playerState) {
                PlayerState.INITIALIZING,
                PlayerState.PREPARING -> {
                    if (queueTracks.size > 1) {
                        pauseWhenPrepared = !pauseWhenPrepared
                        _state.value = buildStateLocked(
                            beatmapsetId = beatmapsetId,
                            isPlaying = false,
                            isPreparing = !pauseWhenPrepared,
                            progress = 0f
                        )
                    } else {
                        stopLocked()
                    }
                    return
                }

                PlayerState.STARTED -> {
                    pausedByTransientFocusLoss = false
                    pausePlayerLocked()
                    return
                }

                PlayerState.PREPARED,
                PlayerState.PAUSED,
                PlayerState.COMPLETED -> {
                    if (!requestAudioFocusLocked()) {
                        _state.value = _state.value.copy(
                            isPlaying = false,
                            isPreparing = false,
                            error = "Audio focus unavailable"
                        )
                        return
                    }
                    pausedByTransientFocusLoss = false
                    pauseWhenPrepared = false
                    val wasCompleted = playerState == PlayerState.COMPLETED
                    if (wasCompleted) {
                        val sought = runCatching { player.seekTo(0) }.isSuccess
                        if (!sought) {
                            playNewLocked(beatmapsetId, previewUrl)
                            return
                        }
                    }
                    startPlayerLocked(player, beatmapsetId, playerGeneration, resetProgress = wasCompleted)
                    return
                }

                PlayerState.IDLE,
                PlayerState.ERROR -> {
                    // Fall through to fresh playback setup.
                }
            }
        }
        playNewLocked(beatmapsetId, previewUrl)
    }

    private fun buildStateLocked(
        beatmapsetId: Long? = activeBeatmapsetId,
        isPlaying: Boolean = false,
        isPreparing: Boolean = false,
        progress: Float = 0f,
        error: String? = null
    ): AudioPlaybackState {
        val current = queueTracks.getOrNull(currentQueueIndex)
        val queueSize = queueTracks.size
        return AudioPlaybackState(
            beatmapsetId = beatmapsetId,
            isPlaying = isPlaying,
            isPreparing = isPreparing,
            progress = progress,
            error = error,
            currentTrack = current,
            queueIndex = if (current != null) currentQueueIndex else -1,
            queueSize = queueSize,
            hasNext = current != null && currentQueueIndex + 1 < queueSize,
            hasPrevious = current != null && currentQueueIndex > 0,
            autoAdvance = autoAdvanceEnabled
        )
    }

    private fun buildPreviewCandidatesLocked(beatmapsetId: Long, previewUrl: String): List<String> {
        val trimmed = previewUrl.trim()
        val normalizedPrimary = when {
            trimmed.startsWith("http") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> ""
        }
        val trackBeatmapId = queueTracks.getOrNull(currentQueueIndex)
            ?.takeIf { it.beatmapsetId == beatmapsetId }
            ?.beatmapId ?: 0L
        return buildList {
            if (normalizedPrimary.isNotBlank()) add(normalizedPrimary)
            if (beatmapsetId > 0L) add("https://b.ppy.sh/preview/$beatmapsetId.mp3")
            if (trackBeatmapId > 0L) add("https://catboy.best/preview/audio/$trackBeatmapId")
        }.distinct()
    }

    private fun playNewLocked(beatmapsetId: Long, previewUrl: String) {
        releasePlayerLocked()

        val candidates = buildPreviewCandidatesLocked(beatmapsetId, previewUrl)
        if (candidates.isEmpty()) {
            playerState = PlayerState.ERROR
            _state.value = buildStateLocked(
                beatmapsetId = null,
                isPlaying = false,
                isPreparing = false,
                progress = 0f,
                error = "Preview unavailable"
            )
            return
        }

        if (!requestAudioFocusLocked()) {
            _state.value = buildStateLocked(
                beatmapsetId = null,
                isPlaying = false,
                isPreparing = false,
                progress = 0f,
                error = "Audio focus unavailable"
            )
            return
        }

        val generation = ++playerGeneration
        activeBeatmapsetId = beatmapsetId
        pauseWhenPrepared = false
        pausedByTransientFocusLoss = false

        _state.value = buildStateLocked(
            beatmapsetId = beatmapsetId,
            isPlaying = false,
            isPreparing = true,
            progress = 0f,
            error = null
        )

        startCandidateLocked(
            generation = generation,
            beatmapsetId = beatmapsetId,
            candidates = candidates,
            candidateIndex = 0
        )
    }

    private fun startCandidateLocked(
        generation: Long,
        beatmapsetId: Long,
        candidates: List<String>,
        candidateIndex: Int
    ) {
        if (playerGeneration != generation || candidateIndex !in candidates.indices) return

        val resolvedUrl = candidates[candidateIndex]
        val player = MediaPlayer()
        mediaPlayer = player
        playerState = PlayerState.INITIALIZING

        player.apply {
            setAudioAttributes(audioAttributes)
            setOnPreparedListener { mp ->
                synchronized(playerLock) {
                    if (
                        mediaPlayer !== mp ||
                        playerGeneration != generation ||
                        playerState != PlayerState.PREPARING
                    ) {
                        disposeAsync(mp, PlayerState.PREPARED)
                        return@setOnPreparedListener
                    }
                    playerState = PlayerState.PREPARED
                    if (pauseWhenPrepared || !hasAudioFocus) {
                        pauseWhenPrepared = false
                        playerState = PlayerState.PAUSED
                        _state.value = buildStateLocked(
                            beatmapsetId = beatmapsetId,
                            isPlaying = false,
                            isPreparing = false,
                            progress = 0f,
                            error = null
                        )
                        return@setOnPreparedListener
                    }
                    startPlayerLocked(mp, beatmapsetId, generation, resetProgress = true)
                }
            }
            setOnCompletionListener { mp ->
                synchronized(playerLock) {
                    if (mediaPlayer !== mp || playerGeneration != generation) return@setOnCompletionListener
                    stopLocked()
                }
            }
            setOnErrorListener { mp, _, _ ->
                synchronized(playerLock) {
                    if (mediaPlayer !== mp || playerGeneration != generation) {
                        disposeAsync(mp, PlayerState.ERROR)
                        return@setOnErrorListener true
                    }
                    if (candidateIndex + 1 < candidates.size) {
                        mediaPlayer = null
                        playerState = PlayerState.IDLE
                        disposeAsync(mp, PlayerState.ERROR)
                        startCandidateLocked(generation, beatmapsetId, candidates, candidateIndex + 1)
                        return@setOnErrorListener true
                    }
                    playerState = PlayerState.ERROR
                    releasePlayerLocked()
                    abandonAudioFocusLocked()
                    _state.value = buildStateLocked(
                        beatmapsetId = null,
                        isPlaying = false,
                        isPreparing = false,
                        progress = 0f,
                        error = "Preview unavailable"
                    )
                }
                true
            }
        }

        runCatching {
            player.setDataSource(resolvedUrl)
            playerState = PlayerState.PREPARING
            player.prepareAsync()
        }.onFailure { err ->
            if (mediaPlayer === player && playerGeneration == generation) {
                if (candidateIndex + 1 < candidates.size) {
                    mediaPlayer = null
                    playerState = PlayerState.IDLE
                    disposeAsync(player, PlayerState.INITIALIZING)
                    startCandidateLocked(generation, beatmapsetId, candidates, candidateIndex + 1)
                } else {
                    playerState = PlayerState.ERROR
                    releasePlayerLocked()
                    abandonAudioFocusLocked()
                    _state.value = buildStateLocked(
                        beatmapsetId = null,
                        error = err.message ?: "Failed to load preview"
                    )
                }
            }
        }
    }

    private fun startPlayerLocked(
        mp: MediaPlayer,
        beatmapsetId: Long,
        generation: Long,
        resetProgress: Boolean = false
    ) {
        if (mediaPlayer !== mp || playerGeneration != generation) return
        if (
            playerState != PlayerState.PREPARED &&
            playerState != PlayerState.PAUSED &&
            playerState != PlayerState.COMPLETED
        ) {
            return
        }

        val volume = if (isDucked) DUCK_VOLUME else FULL_VOLUME
        val started = runCatching {
            mp.setVolume(volume, volume)
            mp.start()
        }.isSuccess

        if (started) {
            playerState = PlayerState.STARTED
            registerNoisyReceiverLocked()
            _state.value = buildStateLocked(
                beatmapsetId = beatmapsetId,
                isPlaying = true,
                isPreparing = false,
                progress = if (resetProgress) 0f else _state.value.progress,
                error = null
            )
            startProgressTickerLocked(beatmapsetId, generation)
        } else {
            playerState = PlayerState.ERROR
            releasePlayerLocked()
            abandonAudioFocusLocked()
            _state.value = buildStateLocked(
                beatmapsetId = null,
                error = "Playback failed"
            )
        }
    }

    private fun pausePlayerLocked() {
        cancelTickerLocked()
        unregisterNoisyReceiverLocked()
        val mp = mediaPlayer ?: return
        if (playerState != PlayerState.STARTED) return

        val paused = runCatching {
            if (mp.isPlaying) {
                mp.pause()
            }
        }.isSuccess

        if (paused) {
            playerState = PlayerState.PAUSED
            _state.value = _state.value.copy(
                isPlaying = false,
                isPreparing = false
            )
        } else {
            playerState = PlayerState.ERROR
            releasePlayerLocked()
            abandonAudioFocusLocked()
            _state.value = buildStateLocked(beatmapsetId = null)
        }
    }

    private fun pauseFromExternalEventLocked(abandonFocus: Boolean) {
        when (playerState) {
            PlayerState.INITIALIZING,
            PlayerState.PREPARING -> {
                pauseWhenPrepared = true
            }

            PlayerState.STARTED -> {
                pausePlayerLocked()
            }

            else -> Unit
        }
        if (abandonFocus) {
            abandonAudioFocusLocked()
        }
    }

    private fun startProgressTickerLocked(beatmapsetId: Long, generation: Long) {
        cancelTickerLocked()
        tickerJob = scope.launch {
            while (isActive) {
                val keepRunning = synchronized(playerLock) {
                    if (!isActive) return@synchronized false
                    val mp = mediaPlayer ?: return@synchronized false
                    if (playerGeneration != generation || activeBeatmapsetId != beatmapsetId) {
                        return@synchronized false
                    }
                    if (playerState != PlayerState.STARTED) return@synchronized false
                    val playing = runCatching { mp.isPlaying }.getOrDefault(false)
                    if (!playing) return@synchronized false
                    val duration = runCatching { mp.duration }.getOrDefault(1).coerceAtLeast(1)
                    val pos = runCatching { mp.currentPosition }.getOrDefault(0).coerceIn(0, duration)
                    val progress = (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    _state.value = _state.value.copy(
                        beatmapsetId = beatmapsetId,
                        isPlaying = true,
                        isPreparing = false,
                        progress = progress
                    )
                    true
                }
                if (!keepRunning) break
                delay(150L)
            }
        }
    }

    fun stop() {
        synchronized(playerLock) {
            stopLocked()
        }
    }

    fun release() {
        stop()
    }

    private fun stopLocked() {
        queueTracks = emptyList()
        currentQueueIndex = -1
        releasePlayerLocked()
        abandonAudioFocusLocked()
        _state.value = AudioPlaybackState()
    }

    private fun cancelTickerLocked() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun releasePlayerLocked() {
        cancelTickerLocked()
        unregisterNoisyReceiverLocked()
        playerGeneration++
        activeBeatmapsetId = null
        pauseWhenPrepared = false
        pausedByTransientFocusLoss = false
        isDucked = false

        val old = mediaPlayer
        val oldState = playerState
        mediaPlayer = null
        playerState = PlayerState.IDLE
        if (old != null) {
            disposeAsync(old, oldState)
        }
    }

    private fun requestAudioFocusLocked(): Boolean {
        if (hasAudioFocus) return true
        val am = audioManager ?: return true
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(audioFocusChangeListener, mainHandler)
            .build()

        val result = runCatching { am.requestAudioFocus(req) }
            .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        return if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusRequest = req
            hasAudioFocus = true
            isDucked = false
            true
        } else {
            hasAudioFocus = false
            false
        }
    }

    private fun abandonAudioFocusLocked() {
        hasAudioFocus = false
        pausedByTransientFocusLoss = false
        isDucked = false
        val req = audioFocusRequest ?: return
        audioFocusRequest = null
        runCatching { audioManager?.abandonAudioFocusRequest(req) }
    }

    private fun registerNoisyReceiverLocked() {
        if (isNoisyReceiverRegistered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        val registered = runCatching {
            ContextCompat.registerReceiver(
                appContext,
                becomingNoisyReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }.isSuccess
        if (registered) {
            isNoisyReceiverRegistered = true
        }
    }

    private fun unregisterNoisyReceiverLocked() {
        if (!isNoisyReceiverRegistered) return
        isNoisyReceiverRegistered = false
        runCatching { appContext.unregisterReceiver(becomingNoisyReceiver) }
    }

    private fun disposeAsync(mp: MediaPlayer, stateAtDispose: PlayerState) {
        runCatching { mp.setOnPreparedListener(null) }
        runCatching { mp.setOnCompletionListener(null) }
        runCatching { mp.setOnErrorListener(null) }
        scope.launch(Dispatchers.IO) {
            safelyDispose(mp, stateAtDispose)
        }
    }

    private fun safelyDispose(mp: MediaPlayer, stateAtDispose: PlayerState) {
        if (
            stateAtDispose == PlayerState.STARTED ||
            stateAtDispose == PlayerState.PAUSED ||
            stateAtDispose == PlayerState.COMPLETED
        ) {
            runCatching { if (mp.isPlaying) mp.stop() }
        }
        runCatching { mp.reset() }
        runCatching { mp.release() }
    }
}
