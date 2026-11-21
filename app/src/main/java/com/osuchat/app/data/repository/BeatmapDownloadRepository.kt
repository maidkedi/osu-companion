package com.osuchat.app.data.repository

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.osuchat.app.domain.model.OszDownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BeatmapDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PRIMARY_MIRROR_BASE = "https://catboy.best/d/"
        private const val FALLBACK_MIRROR_BASE = "https://api.nerinyan.moe/d/"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        private const val DOWNLOAD_RELATIVE_SUBDIR = "osu-chat"
        private const val OSZ_MIME_TYPE = "application/octet-stream"
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _downloadStates = MutableStateFlow<Map<Long, OszDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<Long, OszDownloadState>> = _downloadStates.asStateFlow()

    private val setLocks = ConcurrentHashMap<Long, Mutex>()

    private fun lockFor(beatmapsetId: Long): Mutex = setLocks.getOrPut(beatmapsetId) { Mutex() }

    private fun setState(beatmapsetId: Long, state: OszDownloadState) {
        _downloadStates.update { current ->
            current + (beatmapsetId to state)
        }
    }

    fun clearError(beatmapsetId: Long) {
        _downloadStates.update { current ->
            if (current[beatmapsetId] is OszDownloadState.Error) {
                current + (beatmapsetId to OszDownloadState.Idle)
            } else {
                current
            }
        }
    }

    fun sanitizeOszFileName(beatmapsetId: Long, artist: String, title: String): String {
        fun cleanPart(raw: String, fallback: String): String {
            val cleaned = raw
                .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
                .replace(Regex("\\s+"), " ")
                .trim()
                .trim('.')
            return cleaned.ifBlank { fallback }
        }
        val cleanArtist = cleanPart(artist, "Unknown Artist").take(64)
        val cleanTitle = cleanPart(title, "Beatmap").take(96)
        return "$beatmapsetId $cleanArtist - $cleanTitle.osz"
    }

    suspend fun downloadOszToDevice(
        beatmapsetId: Long,
        artist: String,
        title: String
    ): Result<OszDownloadState.Downloaded> = withContext(Dispatchers.IO) {
        if (beatmapsetId <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Invalid beatmapset ID"))
        }

        lockFor(beatmapsetId).withLock {
            val fileName = sanitizeOszFileName(beatmapsetId, artist, title)
            runCatching {
                setState(beatmapsetId, OszDownloadState.Downloading(progressPercent = null))
                val cachedFile = ensureCachedOszFile(beatmapsetId, fileName, emitProgress = true)
                val savedUri = writeOszToDownloads(cachedFile, fileName)
                val downloaded = OszDownloadState.Downloaded(
                    uri = savedUri.toString(),
                    fileName = fileName
                )
                setState(beatmapsetId, downloaded)
                downloaded
            }.onFailure { err ->
                setState(
                    beatmapsetId,
                    OszDownloadState.Error(err.message?.takeIf { it.isNotBlank() } ?: "Download failed")
                )
            }
        }
    }

    suspend fun prepareShareableOszUri(
        beatmapsetId: Long,
        artist: String,
        title: String
    ): Result<Pair<Uri, String>> = withContext(Dispatchers.IO) {
        if (beatmapsetId <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Invalid beatmapset ID"))
        }

        lockFor(beatmapsetId).withLock {
            val fileName = sanitizeOszFileName(beatmapsetId, artist, title)
            val prevState = _downloadStates.value[beatmapsetId]
            runCatching {
                val cachedFile = getCachedOszFile(fileName)
                val readyFile = if (cachedFile.exists() && cachedFile.length() > 0L) {
                    cachedFile
                } else {
                    setState(beatmapsetId, OszDownloadState.Downloading(progressPercent = null))
                    ensureCachedOszFile(beatmapsetId, fileName, emitProgress = true)
                }
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    readyFile
                )
                if (prevState is OszDownloadState.Downloaded) {
                    setState(beatmapsetId, prevState)
                } else {
                    setState(
                        beatmapsetId,
                        OszDownloadState.Downloaded(
                            uri = contentUri.toString(),
                            fileName = fileName
                        )
                    )
                }
                contentUri to fileName
            }.onFailure { err ->
                setState(
                    beatmapsetId,
                    OszDownloadState.Error(err.message?.takeIf { it.isNotBlank() } ?: "Could not prepare .osz for share")
                )
            }
        }
    }

    fun createShareOszIntent(uri: Uri, fileName: String): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = OSZ_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            putExtra(Intent.EXTRA_TITLE, fileName)
            clipData = ClipData.newRawUri(fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "Share $fileName")
    }

    private fun getOszCacheDir(): File {
        val dir = File(context.cacheDir, "osz")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getCachedOszFile(fileName: String): File = File(getOszCacheDir(), fileName)

    private fun ensureCachedOszFile(
        beatmapsetId: Long,
        fileName: String,
        emitProgress: Boolean
    ): File {
        val targetFile = getCachedOszFile(fileName)
        if (targetFile.exists() && targetFile.length() > 0L) {
            return targetFile
        }

        val mirrors = listOf(
            "$PRIMARY_MIRROR_BASE$beatmapsetId",
            "$FALLBACK_MIRROR_BASE$beatmapsetId"
        )
        var lastError: Throwable? = null
        for (mirrorUrl in mirrors) {
            val partFile = File(getOszCacheDir(), "$fileName.part")
            if (partFile.exists()) partFile.delete()
            try {
                streamFromMirror(
                    url = mirrorUrl,
                    destination = partFile,
                    onProgress = { pct ->
                        if (emitProgress) {
                            setState(beatmapsetId, OszDownloadState.Downloading(progressPercent = pct))
                        }
                    }
                )
                if (targetFile.exists()) targetFile.delete()
                if (!partFile.renameTo(targetFile)) {
                    partFile.copyTo(targetFile, overwrite = true)
                    partFile.delete()
                }
                return targetFile
            } catch (t: Throwable) {
                if (partFile.exists()) partFile.delete()
                lastError = t
            }
        }
        throw (lastError ?: IOException("Failed to download beatmapset $beatmapsetId from mirrors"))
    }

    private fun streamFromMirror(
        url: String,
        destination: File,
        onProgress: (Int?) -> Unit
    ) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "*/*")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Mirror HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty mirror response")
            val contentType = response.header("Content-Type").orEmpty()
            if (contentType.contains("text/html", ignoreCase = true)) {
                throw IOException("Mirror returned HTML instead of .osz archive")
            }

            val contentLength = body.contentLength()
            var totalBytesRead = 0L
            var lastReportedPct = -1

            onProgress(if (contentLength > 0L) 0 else null)

            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        totalBytesRead += read
                        if (contentLength > 0L) {
                            val pct = ((totalBytesRead * 100L) / contentLength).toInt().coerceIn(0, 100)
                            if (pct != lastReportedPct) {
                                lastReportedPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (totalBytesRead <= 0L) {
                throw IOException("Downloaded .osz archive is empty")
            }
        }
    }

    private fun writeOszToDownloads(sourceFile: File, fileName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, OSZ_MIME_TYPE)
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_RELATIVE_SUBDIR"
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val itemUri = resolver.insert(collection, values)
                ?: throw IOException("Failed to create MediaStore entry for $fileName")
            try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out, bufferSize = 16 * 1024)
                    }
                    out.flush()
                } ?: throw IOException("Failed to open output stream for $fileName")

                val doneValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(itemUri, doneValues, null, null)
                itemUri
            } catch (t: Throwable) {
                runCatching { resolver.delete(itemUri, null, null) }
                throw t
            }
        } else {
            val baseDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(baseDownloads, DOWNLOAD_RELATIVE_SUBDIR)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val destFile = File(targetDir, fileName)
            sourceFile.copyTo(destFile, overwrite = true)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destFile
            )
        }
    }
}
