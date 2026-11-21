package com.osuchat.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.osuchat.app.BuildConfig
import com.osuchat.app.data.preferences.UserPreferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class GitHubReleaseDto(
    @SerializedName("tag_name") val tagName: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("body") val body: String? = null,
    @SerializedName("html_url") val htmlUrl: String? = null,
    @SerializedName("draft") val draft: Boolean? = null,
    @SerializedName("prerelease") val prerelease: Boolean? = null,
    @SerializedName("published_at") val publishedAt: String? = null,
    @SerializedName("assets") val assets: List<GitHubAssetDto>? = null
)

data class GitHubAssetDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("browser_download_url") val browserDownloadUrl: String? = null,
    @SerializedName("size") val size: Long? = null,
    @SerializedName("content_type") val contentType: String? = null
)

data class AppUpdateInfo(
    val versionTag: String,
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val publishedAt: String
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val update: AppUpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object SkippedOrThrottled : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

sealed interface ApkDownloadProgress {
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : ApkDownloadProgress
    data class Completed(val apkFile: File) : ApkDownloadProgress
    data class Failed(val message: String) : ApkDownloadProgress
}

sealed interface AppUpdateDialogState {
    data object Hidden : AppUpdateDialogState
    data class Available(val update: AppUpdateInfo) : AppUpdateDialogState
    data class Downloading(
        val update: AppUpdateInfo,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : AppUpdateDialogState
    data class AwaitingInstallPermission(val update: AppUpdateInfo, val apkFile: File) : AppUpdateDialogState
    data class ReadyToInstall(val update: AppUpdateInfo, val apkFile: File) : AppUpdateDialogState
    data class Failed(val update: AppUpdateInfo, val message: String) : AppUpdateDialogState
}

@Singleton
class AppUpdateRepository internal constructor(
    private val cacheRootDir: File,
    private val gson: Gson,
    private val client: OkHttpClient,
    private val latestReleaseUrl: String,
    private val currentVersionName: String,
    private val userAgent: String,
    private val clockMs: () -> Long,
    private val readLastCheckAt: suspend () -> Long,
    private val writeLastCheckAt: suspend (Long) -> Unit,
    private val readSkippedVersion: suspend () -> String,
    private val writeSkippedVersion: suspend (String) -> Unit
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        prefs: UserPreferencesDataStore,
        gson: Gson
    ) : this(
        cacheRootDir = context.cacheDir,
        gson = gson,
        client = buildStandaloneClient(),
        latestReleaseUrl = DEFAULT_LATEST_RELEASE_URL,
        currentVersionName = BuildConfig.VERSION_NAME,
        userAgent = "OsuChat-Android/${BuildConfig.VERSION_NAME}",
        clockMs = { System.currentTimeMillis() },
        readLastCheckAt = { prefs.getLastUpdateCheckAt() },
        writeLastCheckAt = { prefs.setLastUpdateCheckAt(it) },
        readSkippedVersion = { prefs.getSkippedUpdateVersion() },
        writeSkippedVersion = { prefs.setSkippedUpdateVersion(it) }
    )

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val _dialogState = MutableStateFlow<AppUpdateDialogState>(AppUpdateDialogState.Hidden)
    val dialogState: StateFlow<AppUpdateDialogState> = _dialogState.asStateFlow()

    suspend fun checkForUpdate(force: Boolean): UpdateCheckResult = withContext(Dispatchers.IO) {
        val now = clockMs()
        if (!force) {
            val lastCheck = readLastCheckAt()
            if (lastCheck > 0L && (now - lastCheck) in 0L until CHECK_THROTTLE_MS) {
                return@withContext UpdateCheckResult.SkippedOrThrottled
            }
        }

        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", userAgent)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                if (code == 404) {
                    writeLastCheckAt(now)
                    return@withContext UpdateCheckResult.UpToDate
                }
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Error(
                        "Update check failed (HTTP $code)"
                    )
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext UpdateCheckResult.Error("Empty response from GitHub")
                }

                val release = runCatching {
                    gson.fromJson(body, GitHubReleaseDto::class.java)
                }.getOrNull() ?: return@withContext UpdateCheckResult.Error("Invalid update metadata")

                writeLastCheckAt(now)

                if (release.draft == true || release.prerelease == true) {
                    return@withContext UpdateCheckResult.UpToDate
                }

                val remoteTag = release.tagName?.trim().orEmpty()
                if (remoteTag.isEmpty() || !isNewerVersion(remoteTag, currentVersionName)) {
                    return@withContext UpdateCheckResult.UpToDate
                }

                if (!force) {
                    val skipped = readSkippedVersion()
                    if (skipped.isNotBlank() &&
                        normalizeVersionString(skipped) == normalizeVersionString(remoteTag)
                    ) {
                        return@withContext UpdateCheckResult.SkippedOrThrottled
                    }
                }

                val apkAsset = selectApkAsset(release.assets)
                    ?: return@withContext UpdateCheckResult.Error("No APK found in release $remoteTag")

                val downloadUrl = apkAsset.browserDownloadUrl?.trim().orEmpty()
                val updateInfo = AppUpdateInfo(
                    versionTag = remoteTag,
                    versionName = normalizeVersionString(remoteTag),
                    releaseName = release.name?.trim().takeUnless { it.isNullOrBlank() } ?: remoteTag,
                    releaseNotes = release.body?.trim().orEmpty(),
                    releaseUrl = release.htmlUrl?.trim().takeUnless { it.isNullOrBlank() }
                        ?: DEFAULT_RELEASE_PAGE_URL,
                    apkDownloadUrl = downloadUrl,
                    apkFileName = apkAsset.name?.trim().takeUnless { it.isNullOrBlank() } ?: APK_FILE_NAME,
                    apkSizeBytes = (apkAsset.size ?: 0L).coerceAtLeast(0L),
                    publishedAt = release.publishedAt?.trim().orEmpty()
                )
                UpdateCheckResult.UpdateAvailable(updateInfo)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            UpdateCheckResult.Error(sanitizeError(e, "Network error while checking for updates"))
        } catch (e: Throwable) {
            UpdateCheckResult.Error(sanitizeError(e, "Unable to check for updates"))
        }
    }

    fun downloadApk(downloadUrl: String): Flow<ApkDownloadProgress> = flow {
        val updatesDir = File(cacheRootDir, UPDATES_DIR_NAME).apply { mkdirs() }
        val partFile = File(updatesDir, APK_PART_FILE_NAME)
        val finalFile = File(updatesDir, APK_FILE_NAME)
        if (partFile.exists()) {
            partFile.delete()
        }

        val request = Request.Builder()
            .url(downloadUrl)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", userAgent)
            .get()
            .build()

        val call = client.newCall(request)
        val job = currentCoroutineContext()[Job]
        val cancelHandle = job?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                call.cancel()
            }
        }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    emit(ApkDownloadProgress.Failed("Download failed (HTTP ${response.code})"))
                    return@flow
                }
                val body = response.body
                if (body == null) {
                    emit(ApkDownloadProgress.Failed("Empty APK response"))
                    return@flow
                }

                val totalBytes = body.contentLength().coerceAtLeast(-1L)
                var downloaded = 0L
                var lastEmitMs = 0L
                var lastPercent = -1L

                emit(ApkDownloadProgress.Downloading(0L, totalBytes))

                body.byteStream().use { input ->
                    FileOutputStream(partFile, false).use { output ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read > 0) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                val percent = if (totalBytes > 0L) (downloaded * 100L) / totalBytes else -1L
                                if (now - lastEmitMs >= PROGRESS_EMIT_INTERVAL_MS ||
                                    (percent >= 0L && percent > lastPercent)
                                ) {
                                    lastEmitMs = now
                                    lastPercent = percent
                                    emit(ApkDownloadProgress.Downloading(downloaded, totalBytes))
                                }
                            }
                        }
                        output.flush()
                    }
                }

                currentCoroutineContext().ensureActive()
                if (downloaded <= 0L || !partFile.exists()) {
                    partFile.delete()
                    emit(ApkDownloadProgress.Failed("Downloaded file was empty"))
                    return@flow
                }

                if (finalFile.exists() && !finalFile.delete()) {
                    partFile.delete()
                    emit(ApkDownloadProgress.Failed("Could not replace previous installer"))
                    return@flow
                }

                if (!partFile.renameTo(finalFile)) {
                    partFile.delete()
                    emit(ApkDownloadProgress.Failed("Could not finalize downloaded APK"))
                    return@flow
                }

                emit(ApkDownloadProgress.Downloading(downloaded, totalBytes.takeIf { it > 0L } ?: downloaded))
                emit(ApkDownloadProgress.Completed(finalFile))
            }
        } catch (e: CancellationException) {
            partFile.delete()
            throw e
        } catch (e: Throwable) {
            partFile.delete()
            emit(ApkDownloadProgress.Failed(sanitizeError(e, "APK download failed")))
        } finally {
            cancelHandle?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun cleanupOldApks() = withContext(Dispatchers.IO) {
        if (downloadJob?.isActive == true) return@withContext
        val updatesDir = File(cacheRootDir, UPDATES_DIR_NAME)
        if (!updatesDir.exists() || !updatesDir.isDirectory) return@withContext
        updatesDir.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.endsWith(".apk", ignoreCase = true) || file.name.endsWith(".part", ignoreCase = true))) {
                runCatching { file.delete() }
            }
        }
    }

    fun presentUpdate(update: AppUpdateInfo) {
        if (downloadJob?.isActive == true) return
        _dialogState.value = AppUpdateDialogState.Available(update)
    }

    fun startDownload(update: AppUpdateInfo) {
        downloadJob?.cancel()
        _dialogState.value = AppUpdateDialogState.Downloading(
            update = update,
            bytesDownloaded = 0L,
            totalBytes = update.apkSizeBytes.takeIf { it > 0L } ?: -1L
        )
        downloadJob = repoScope.launch {
            downloadApk(update.apkDownloadUrl).collect { event ->
                when (event) {
                    is ApkDownloadProgress.Downloading -> {
                        val expectedTotal = event.totalBytes.takeIf { it > 0L }
                            ?: update.apkSizeBytes.takeIf { it > 0L }
                            ?: -1L
                        _dialogState.value = AppUpdateDialogState.Downloading(
                            update = update,
                            bytesDownloaded = event.bytesDownloaded,
                            totalBytes = expectedTotal
                        )
                    }
                    is ApkDownloadProgress.Completed -> {
                        _dialogState.value = AppUpdateDialogState.ReadyToInstall(
                            update = update,
                            apkFile = event.apkFile
                        )
                    }
                    is ApkDownloadProgress.Failed -> {
                        _dialogState.value = AppUpdateDialogState.Failed(
                            update = update,
                            message = event.message
                        )
                    }
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _dialogState.value = AppUpdateDialogState.Hidden
    }

    fun dismissDialog() {
        if (_dialogState.value is AppUpdateDialogState.Downloading) {
            cancelDownload()
        } else {
            _dialogState.value = AppUpdateDialogState.Hidden
        }
    }

    fun skipCurrentUpdate(versionTag: String) {
        repoScope.launch {
            runCatching { writeSkippedVersion(versionTag) }
        }
        if (_dialogState.value is AppUpdateDialogState.Downloading) {
            downloadJob?.cancel()
            downloadJob = null
        }
        _dialogState.value = AppUpdateDialogState.Hidden
    }

    fun markAwaitingInstallPermission(update: AppUpdateInfo, apkFile: File) {
        _dialogState.value = AppUpdateDialogState.AwaitingInstallPermission(update, apkFile)
    }

    fun markReadyToInstall(update: AppUpdateInfo, apkFile: File) {
        _dialogState.value = AppUpdateDialogState.ReadyToInstall(update, apkFile)
    }

    fun markFailed(update: AppUpdateInfo, message: String) {
        _dialogState.value = AppUpdateDialogState.Failed(update, message)
    }

    private fun sanitizeError(err: Throwable, fallback: String): String {
        val msg = err.message?.trim().orEmpty()
        return if (msg.isNotEmpty() && msg.length <= 96 && !msg.contains("http", ignoreCase = true)) {
            msg
        } else {
            fallback
        }
    }

    companion object {
        const val UPDATE_REPO_OWNER = "maidkedi"
        const val UPDATE_REPO_NAME = "osu-companion"
        const val CHECK_THROTTLE_MS = 6L * 60L * 60L * 1000L

        private const val UPDATES_DIR_NAME = "updates"
        private const val APK_FILE_NAME = "osuchat_update.apk"
        private const val APK_PART_FILE_NAME = "osuchat_update.apk.part"
        private const val DOWNLOAD_BUFFER_BYTES = 8 * 1024
        private const val PROGRESS_EMIT_INTERVAL_MS = 100L

        private const val DEFAULT_LATEST_RELEASE_URL =
            "https://api.github.com/repos/$UPDATE_REPO_OWNER/$UPDATE_REPO_NAME/releases/latest"
        private const val DEFAULT_RELEASE_PAGE_URL =
            "https://github.com/$UPDATE_REPO_OWNER/$UPDATE_REPO_NAME/releases/latest"

        fun buildStandaloneClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        internal fun normalizeVersionString(raw: String): String {
            return raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore("+")
                .trim()
        }

        private data class ParsedSemVer(
            val core: List<Long>,
            val preRelease: List<String>
        )

        private fun parseSemVer(raw: String): ParsedSemVer? {
            val cleaned = normalizeVersionString(raw)
            if (cleaned.isEmpty()) return null
            val dashIndex = cleaned.indexOf('-')
            val coreStr = if (dashIndex >= 0) cleaned.substring(0, dashIndex) else cleaned
            val preStr = if (dashIndex >= 0) cleaned.substring(dashIndex + 1) else ""
            val coreParts = coreStr.split('.')
            if (coreParts.isEmpty()) return null
            val parsedCore = ArrayList<Long>(coreParts.size)
            for (part in coreParts) {
                if (part.isEmpty() || !part.all { it in '0'..'9' }) return null
                val num = part.toLongOrNull() ?: return null
                parsedCore.add(num)
            }
            val preParts = if (preStr.isEmpty()) {
                emptyList()
            } else {
                preStr.split('.').filter { it.isNotEmpty() }
            }
            return ParsedSemVer(parsedCore, preParts)
        }

        fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
            val remote = parseSemVer(remoteTag) ?: return false
            val current = parseSemVer(currentVersion) ?: return false

            val len = maxOf(remote.core.size, current.core.size)
            for (i in 0 until len) {
                val r = remote.core.getOrElse(i) { 0L }
                val c = current.core.getOrElse(i) { 0L }
                if (r != c) return r > c
            }

            if (remote.preRelease.isEmpty() && current.preRelease.isNotEmpty()) return true
            if (remote.preRelease.isNotEmpty() && current.preRelease.isEmpty()) return false
            if (remote.preRelease.isEmpty() && current.preRelease.isEmpty()) return false

            val preLen = maxOf(remote.preRelease.size, current.preRelease.size)
            for (i in 0 until preLen) {
                if (i >= remote.preRelease.size) return false
                if (i >= current.preRelease.size) return true
                val rId = remote.preRelease[i]
                val cId = current.preRelease[i]
                if (rId == cId) continue
                val rNum = rId.takeIf { it.all { ch -> ch in '0'..'9' } }?.toLongOrNull()
                val cNum = cId.takeIf { it.all { ch -> ch in '0'..'9' } }?.toLongOrNull()
                return when {
                    rNum != null && cNum != null -> rNum > cNum
                    rNum != null && cNum == null -> false
                    rNum == null && cNum != null -> true
                    else -> rId.lowercase(Locale.US) > cId.lowercase(Locale.US)
                }
            }
            return false
        }

        fun selectApkAsset(assets: List<GitHubAssetDto>?): GitHubAssetDto? {
            if (assets.isNullOrEmpty()) return null
            val apkAssets = assets.filter { asset ->
                val name = asset.name?.trim().orEmpty()
                val url = asset.browserDownloadUrl?.trim().orEmpty()
                name.endsWith(".apk", ignoreCase = true) &&
                    (url.startsWith("https://", ignoreCase = true) ||
                        url.startsWith("http://", ignoreCase = true))
            }
            if (apkAssets.isEmpty()) return null
            return apkAssets.firstOrNull {
                it.name.orEmpty().contains("release", ignoreCase = true) &&
                    it.browserDownloadUrl.orEmpty().startsWith("https://", ignoreCase = true)
            } ?: apkAssets.firstOrNull {
                it.name.orEmpty().contains("release", ignoreCase = true)
            } ?: apkAssets.firstOrNull {
                it.browserDownloadUrl.orEmpty().startsWith("https://", ignoreCase = true)
            } ?: apkAssets.first()
        }
    }
}
