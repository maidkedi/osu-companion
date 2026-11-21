package com.osuchat.app

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.osuchat.app.data.network.CloudflareChallengeManager
import com.osuchat.app.domain.repository.ChatRepository
import com.osuchat.app.service.BeatmapAudioPlayer
import com.osuchat.app.util.RosuPpEngine
import com.osuchat.app.widget.BanchoStatsWidget
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OsuChatApp : Application(), DefaultLifecycleObserver {

    @Inject
    lateinit var chatRepository: ChatRepository

    @Inject
    lateinit var rosuPpEngine: RosuPpEngine

    @Inject
    lateinit var beatmapAudioPlayer: BeatmapAudioPlayer

    @Inject
    lateinit var cloudflareChallengeManager: CloudflareChallengeManager

    override fun onCreate() {
        super<Application>.onCreate()
        cloudflareChallengeManager.clearCloudflareCookies()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        BanchoStatsWidget.schedulePeriodicUpdates(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        chatRepository.setAppInForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        chatRepository.setAppInForeground(false)
        beatmapAudioPlayer.stop()
        rosuPpEngine.destroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            rosuPpEngine.destroy()
        }
    }

    override fun onTerminate() {
        rosuPpEngine.destroy()
        beatmapAudioPlayer.release()
        super.onTerminate()
    }
}
