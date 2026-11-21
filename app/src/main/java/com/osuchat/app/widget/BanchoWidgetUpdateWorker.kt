package com.osuchat.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.osuchat.app.R
import com.osuchat.app.data.local.ProfileHistoryDao
import com.osuchat.app.data.local.ProfileHistoryEntity
import com.osuchat.app.data.network.OsuApiPoller
import com.osuchat.app.data.network.OsuApiService
import com.osuchat.app.data.preferences.UserPreferencesDataStore
import com.osuchat.app.data.preferences.WidgetStatsSnapshot
import com.osuchat.app.domain.model.AuthMode
import com.osuchat.app.service.ChatNotificationManager
import com.osuchat.app.util.RankPpMath
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class BanchoWidgetUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun prefs(): UserPreferencesDataStore
        fun apiPoller(): OsuApiPoller
        fun apiService(): OsuApiService
        fun okHttpClient(): OkHttpClient
        fun profileHistoryDao(): ProfileHistoryDao
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(
            applicationContext,
            ChatNotificationManager.SERVICE_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Updating osu! widget")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
        return ForegroundInfo(WIDGET_WORKER_NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (isStopped) return@withContext Result.success()
        if (!BanchoStatsWidget.hasActiveWidgets(applicationContext)) {
            return@withContext Result.success()
        }

        var prefs: UserPreferencesDataStore? = null
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                WorkerEntryPoint::class.java
            )
            val dataStorePrefs = entryPoint.prefs().also { prefs = it }
            val apiPoller = entryPoint.apiPoller()
            val apiService = entryPoint.apiService()
            val okHttpClient = entryPoint.okHttpClient()

            val settings = dataStorePrefs.settingsFlow.first()
            if (isStopped) return@withContext Result.success()

            if (settings.authMode == AuthMode.NONE) {
                BanchoStatsWidget.clearAvatarCache(applicationContext)
                BanchoStatsWidget.renderSnapshotToAllWidgets(
                    applicationContext,
                    WidgetStatsSnapshot(
                        widgetTheme = settings.widgetTheme,
                        widgetTranslucent = settings.widgetTranslucent
                    ),
                    null
                )
                return@withContext Result.success()
            }

            val token = suspendRunCatching { apiPoller.getValidBearerToken() }.getOrNull()
            if (isStopped) return@withContext Result.success()

            if (token != null) {
                val me = suspendRunCatching { apiService.getMe(token) }.getOrNull()
                if (isStopped) return@withContext Result.success()
                val friends = suspendRunCatching { apiService.getFriends(token) }.getOrNull()
                val onlineCount = friends?.count { it.isOnline == true }

                if (me != null) {
                    val safeUsername = me.username?.trim()?.takeIf { it.isNotBlank() }
                    val fallbackAvatar = me.id.takeIf { it > 0L }?.let { "https://a.ppy.sh/$it" }
                    dataStorePrefs.saveWidgetSnapshot(
                        username = safeUsername,
                        avatarUrl = me.avatarUrl ?: fallbackAvatar,
                        globalRank = me.statistics?.globalRank,
                        countryRank = me.statistics?.countryRank ?: me.statistics?.rank?.country,
                        countryCode = me.country?.code ?: me.countryCode,
                        pp = me.statistics?.pp,
                        accuracy = me.statistics?.hitAccuracy,
                        onlineFriendsCount = onlineCount
                    )
                    val globalRank = RankPpMath.sanitizeRank(me.statistics?.globalRank) ?: 0
                    val countryRank = RankPpMath.sanitizeRank(
                        me.statistics?.countryRank ?: me.statistics?.rank?.country
                    ) ?: 0
                    val pp = RankPpMath.sanitizeFiniteNonNegative(me.statistics?.pp) ?: 0.0
                    val accuracy = RankPpMath.sanitizeFiniteNonNegative(me.statistics?.hitAccuracy) ?: 0.0
                    val playCount = (me.statistics?.playCount ?: 0).coerceAtLeast(0)
                    val mode = me.playmode?.trim()?.lowercase(Locale.US)
                        ?.takeIf { it in VALID_OSU_MODES } ?: "osu"
                    if (globalRank > 0 || pp > 0.0) {
                        val today = LocalDate.now(ZoneOffset.UTC)
                        val historyDao = entryPoint.profileHistoryDao()
                        suspendRunCatching {
                            historyDao.upsertSnapshot(
                                ProfileHistoryEntity(
                                    dayKey = today.toString(),
                                    mode = mode,
                                    globalRank = globalRank,
                                    countryRank = countryRank,
                                    pp = pp,
                                    accuracy = accuracy,
                                    playCount = playCount,
                                    recordedAt = System.currentTimeMillis()
                                )
                            )
                            historyDao.pruneOlderThan(today.minusDays(365).toString())
                        }
                    }
                } else if (onlineCount != null) {
                    dataStorePrefs.saveWidgetSnapshot(onlineFriendsCount = onlineCount)
                }
            }

            if (isStopped) return@withContext Result.success()

            val updatedSnapshot = dataStorePrefs.getWidgetSnapshot()
            if (updatedSnapshot.username.isBlank()) {
                BanchoStatsWidget.clearAvatarCache(applicationContext)
                BanchoStatsWidget.renderSnapshotToAllWidgets(
                    applicationContext,
                    WidgetStatsSnapshot(
                        widgetTheme = updatedSnapshot.widgetTheme,
                        widgetTranslucent = updatedSnapshot.widgetTranslucent
                    ),
                    null
                )
                return@withContext Result.success()
            }

            val avatarUrl = updatedSnapshot.avatarUrl
            val bitmap = if (!avatarUrl.isNullOrBlank() && !isStopped) {
                downloadAndCacheCircularAvatar(okHttpClient, avatarUrl)
                    ?: BanchoStatsWidget.loadCachedAvatarBitmap(applicationContext)
            } else {
                BanchoStatsWidget.loadCachedAvatarBitmap(applicationContext)
            }

            if (isStopped) {
                if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
                return@withContext Result.success()
            }

            BanchoStatsWidget.renderSnapshotToAllWidgets(applicationContext, updatedSnapshot, bitmap)
            Result.success()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // Preserve existing widget theme, stats, and responsive grid bindings on OOM/error.
            val fallbackSnapshot = prefs?.let { store ->
                suspendRunCatching { store.getWidgetSnapshot() }.getOrNull()
            }
            if (fallbackSnapshot != null && !isStopped) {
                runCatching {
                    BanchoStatsWidget.renderSnapshotToAllWidgets(
                        applicationContext,
                        fallbackSnapshot,
                        null
                    )
                }
            }
            if (!isStopped && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private suspend inline fun <T> suspendRunCatching(crossinline block: suspend () -> T): ResultWrapper<T> {
        return try {
            ResultWrapper(value = block())
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            ResultWrapper(value = null)
        }
    }

    private class ResultWrapper<T>(val value: T?) {
        fun getOrNull(): T? = value
    }

    private fun readCappedBytes(body: ResponseBody, maxBytes: Int): ByteArray? {
        val declaredLength = body.contentLength()
        if (declaredLength > maxBytes) return null
        val bufferSize = if (declaredLength in 1..maxBytes) declaredLength.toInt() else 8192
        val out = ByteArrayOutputStream(bufferSize)
        val chunk = ByteArray(8192)
        var total = 0
        body.byteStream().use { input ->
            while (true) {
                if (isStopped) return null
                val read = input.read(chunk)
                if (read == -1) break
                total += read
                if (total > maxBytes) return null
                out.write(chunk, 0, read)
            }
        }
        return if (total > 0) out.toByteArray() else null
    }

    private fun downloadAndCacheCircularAvatar(
        client: OkHttpClient,
        url: String
    ): Bitmap? {
        return runCatching {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful || isStopped) return@runCatching null
                val body = response.body ?: return@runCatching null
                val bytes = readCappedBytes(body, MAX_AVATAR_DOWNLOAD_BYTES) ?: return@runCatching null

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..MAX_RAW_BITMAP_DIM ||
                    bounds.outHeight !in 1..MAX_RAW_BITMAP_DIM
                ) {
                    return@runCatching null
                }

                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = BanchoStatsWidget.calculateInSampleSize(
                        bounds.outWidth,
                        bounds.outHeight,
                        TARGET_AVATAR_PX
                    )
                }
                val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    ?: return@runCatching null
                val circular = try {
                    if (raw.isRecycled || raw.width <= 0 || raw.height <= 0) {
                        return@runCatching null
                    }
                    cropCircularBitmap(raw, TARGET_AVATAR_PX)
                } finally {
                    if (!raw.isRecycled) raw.recycle()
                }
                val clamped = BanchoStatsWidget.clampWidgetBitmap(circular, TARGET_AVATAR_PX)
                if (clamped == null || clamped !== circular) {
                    if (!circular.isRecycled) circular.recycle()
                }
                val finalBitmap = clamped ?: return@runCatching null

                val targetFile = BanchoStatsWidget.avatarCacheFile(applicationContext)
                val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
                var writeSuccess = false
                try {
                    FileOutputStream(tempFile).use { out ->
                        writeSuccess = finalBitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                    }
                    if (writeSuccess && !isStopped) {
                        if (!tempFile.renameTo(targetFile)) {
                            tempFile.copyTo(targetFile, overwrite = true)
                            tempFile.delete()
                        }
                    } else {
                        tempFile.delete()
                    }
                } catch (_: Throwable) {
                    runCatching { tempFile.delete() }
                }
                finalBitmap
            }
        }.getOrNull()
    }

    private fun cropCircularBitmap(source: Bitmap, targetPx: Int): Bitmap {
        val size = minOf(source.width, source.height).coerceAtLeast(1)
        val xOffset = ((source.width - size) / 2).coerceAtLeast(0)
        val yOffset = ((source.height - size) / 2).coerceAtLeast(0)
        var squared: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            val squaredBmp = Bitmap.createBitmap(source, xOffset, yOffset, size, size).also {
                if (it !== source) squared = it
            }
            val scaledBmp = Bitmap.createScaledBitmap(squaredBmp, targetPx, targetPx, true).also {
                if (it !== source) scaled = it
            }
            if (squared !== scaled && squared != null && !squared!!.isRecycled) {
                squared!!.recycle()
                squared = null
            }

            val output = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = Rect(0, 0, targetPx, targetPx)
            val radius = targetPx / 2f
            canvas.drawARGB(0, 0, 0, 0)
            canvas.drawCircle(radius, radius, radius, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(scaledBmp, rect, rect, paint)
            return output
        } finally {
            if (squared != null && !squared!!.isRecycled) squared!!.recycle()
            if (scaled != null && !scaled!!.isRecycled) scaled!!.recycle()
        }
    }

    companion object {
        private val VALID_OSU_MODES = setOf("osu", "taiko", "fruits", "mania")
        private const val TARGET_AVATAR_PX = 96
        private const val MAX_AVATAR_DOWNLOAD_BYTES = 5 * 1024 * 1024
        private const val MAX_RAW_BITMAP_DIM = 16384
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val WIDGET_WORKER_NOTIFICATION_ID = 1003
    }
}
