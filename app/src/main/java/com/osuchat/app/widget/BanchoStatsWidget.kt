package com.osuchat.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.osuchat.app.MainActivity
import com.osuchat.app.R
import com.osuchat.app.data.preferences.UserPreferencesDataStore
import com.osuchat.app.data.preferences.WidgetStatsSnapshot
import com.osuchat.app.domain.model.WidgetTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

class BanchoStatsWidget : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun prefs(): UserPreferencesDataStore
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val appContext = context.applicationContext
        schedulePeriodicUpdates(appContext)
        enqueueImmediateRefresh(appContext)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelScheduledUpdates(context.applicationContext)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val appContext = context.applicationContext
        schedulePeriodicUpdates(appContext)
        renderWidgetsAsync(appContext, appWidgetManager, appWidgetIds)
        enqueueImmediateRefresh(appContext)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        renderWidgetsAsync(context.applicationContext, appWidgetManager, intArrayOf(appWidgetId))
    }

    private fun renderWidgetsAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                renderFromPreferences(appContext, appWidgetManager, appWidgetIds)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // Keep receiver process alive; render minimal fallback if DataStore or bitmap decode fails.
                runCatching {
                    renderSnapshotToWidgetIds(
                        appContext,
                        appWidgetManager,
                        appWidgetIds,
                        WidgetStatsSnapshot(),
                        null
                    )
                }
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            enqueueImmediateRefresh(context.applicationContext)
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.osuchat.app.widget.ACTION_REFRESH_WIDGET"
        private const val PERIODIC_WORK_NAME = "bancho_stats_widget_periodic"
        private const val IMMEDIATE_WORK_NAME = "bancho_stats_widget_immediate"
        private const val AVATAR_CACHE_FILE = "bancho_widget_avatar.png"
        const val MAX_WIDGET_BITMAP_PX = 128
        const val MAX_WIDGET_BITMAP_BYTES = 256 * 1024
        private const val MAX_CACHE_FILE_BYTES = 2L * 1024L * 1024L
        private const val MAX_RAW_BITMAP_DIM = 16384

        fun hasActiveWidgets(context: Context): Boolean {
            val appContext = context.applicationContext
            return runCatching {
                val manager = AppWidgetManager.getInstance(appContext)
                manager.getAppWidgetIds(ComponentName(appContext, BanchoStatsWidget::class.java)).isNotEmpty()
            }.getOrDefault(false)
        }

        fun schedulePeriodicUpdates(context: Context) {
            val appContext = context.applicationContext
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val periodicRequest = PeriodicWorkRequestBuilder<BanchoWidgetUpdateWorker>(
                    30,
                    TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicRequest
                )
            }
        }

        fun enqueueImmediateRefresh(context: Context) {
            val appContext = context.applicationContext
            runCatching {
                val request = OneTimeWorkRequestBuilder<BanchoWidgetUpdateWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
                // Coalesce concurrent triggers so live widget refresh work is never cancelled mid-flight.
                WorkManager.getInstance(appContext).enqueueUniqueWork(
                    IMMEDIATE_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request
                )
            }
        }

        fun cancelScheduledUpdates(context: Context) {
            val appContext = context.applicationContext
            runCatching {
                val wm = WorkManager.getInstance(appContext)
                wm.cancelUniqueWork(PERIODIC_WORK_NAME)
                wm.cancelUniqueWork(IMMEDIATE_WORK_NAME)
            }
        }

        fun updateFromSnapshot(context: Context, snapshot: WidgetStatsSnapshot) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, BanchoStatsWidget::class.java))
            if (ids.isEmpty()) return
            val bitmap = if (snapshot.username.isNotBlank()) {
                loadCachedAvatarBitmap(appContext)
            } else {
                null
            }
            renderSnapshotToWidgetIds(appContext, manager, ids, snapshot, bitmap)
        }

        private suspend fun renderFromPreferences(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            if (appWidgetIds.isEmpty()) return
            val appContext = context.applicationContext
            val entryPoint = EntryPointAccessors.fromApplication(
                appContext,
                WidgetEntryPoint::class.java
            )
            val snapshot = entryPoint.prefs().getWidgetSnapshot()
            val avatarBitmap = if (snapshot.username.isNotBlank()) {
                loadCachedAvatarBitmap(appContext)
            } else {
                null
            }
            renderSnapshotToWidgetIds(appContext, appWidgetManager, appWidgetIds, snapshot, avatarBitmap)
        }

        suspend fun updateFromPreferences(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, BanchoStatsWidget::class.java))
            if (ids.isEmpty()) return
            renderFromPreferences(appContext, manager, ids)
        }

        fun notifyWidgetSettingsChanged(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, BanchoStatsWidget::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val updateIntent = Intent(appContext, BanchoStatsWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            appContext.sendBroadcast(updateIntent)
        }

        suspend fun refreshThemeAndNotify(context: Context) {
            withContext(Dispatchers.IO) {
                updateFromPreferences(context.applicationContext)
            }
        }

        fun renderSnapshotToAllWidgets(
            context: Context,
            snapshot: WidgetStatsSnapshot,
            avatarBitmap: Bitmap?
        ) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, BanchoStatsWidget::class.java))
            if (ids.isEmpty()) return
            renderSnapshotToWidgetIds(appContext, manager, ids, snapshot, avatarBitmap)
        }

        fun avatarCacheFile(context: Context): File = File(context.applicationContext.cacheDir, AVATAR_CACHE_FILE)

        fun clearAvatarCache(context: Context) {
            runCatching {
                val file = avatarCacheFile(context)
                if (file.exists()) {
                    file.delete()
                }
                val tmpFile = File(file.parentFile, "$AVATAR_CACHE_FILE.tmp")
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
            }
        }

        fun loadCachedAvatarBitmap(context: Context): Bitmap? {
            val file = avatarCacheFile(context)
            if (!file.exists() || !file.isFile) return null
            val fileLength = file.length()
            if (fileLength <= 0L || fileLength > MAX_CACHE_FILE_BYTES) return null
            return runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth !in 1..MAX_RAW_BITMAP_DIM ||
                    bounds.outHeight !in 1..MAX_RAW_BITMAP_DIM
                ) {
                    return@runCatching null
                }

                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_WIDGET_BITMAP_PX)
                }
                val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return@runCatching null
                val clamped = clampWidgetBitmap(decoded, MAX_WIDGET_BITMAP_PX, MAX_WIDGET_BITMAP_BYTES)
                if (clamped == null || clamped !== decoded) {
                    if (!decoded.isRecycled) {
                        decoded.recycle()
                    }
                }
                clamped
            }.getOrNull()
        }

        fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
            if (width <= 0 || height <= 0 || maxDim <= 0) return 1
            var inSampleSize = 1
            while ((width / inSampleSize) > maxDim * 2 || (height / inSampleSize) > maxDim * 2) {
                inSampleSize *= 2
            }
            return inSampleSize
        }

        fun clampWidgetBitmap(
            bitmap: Bitmap?,
            maxPx: Int = MAX_WIDGET_BITMAP_PX,
            maxBytes: Int = MAX_WIDGET_BITMAP_BYTES
        ): Bitmap? {
            if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
            return runCatching {
                var current: Bitmap = bitmap
                var ownsCurrent = false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && current.config == Bitmap.Config.HARDWARE) {
                    val softCopy = current.copy(Bitmap.Config.ARGB_8888, false) ?: return@runCatching null
                    current = softCopy
                    ownsCurrent = true
                }

                if (current.width > maxPx || current.height > maxPx) {
                    val scale = maxPx.toFloat() / max(current.width, current.height).toFloat()
                    val targetW = (current.width * scale).roundToInt().coerceAtLeast(1)
                    val targetH = (current.height * scale).roundToInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(current, targetW, targetH, true)
                    if (ownsCurrent && scaled !== current && !current.isRecycled) {
                        current.recycle()
                    }
                    current = scaled
                    ownsCurrent = true
                }

                if (current.allocationByteCount > maxBytes) {
                    val byteRatio = sqrt(maxBytes.toDouble() / current.allocationByteCount.toDouble()).toFloat()
                    val targetW = (current.width * byteRatio).roundToInt().coerceIn(1, maxPx)
                    val targetH = (current.height * byteRatio).roundToInt().coerceIn(1, maxPx)
                    val shrunk = Bitmap.createScaledBitmap(current, targetW, targetH, true)
                    if (ownsCurrent && shrunk !== current && !current.isRecycled) {
                        current.recycle()
                    }
                    current = shrunk
                }

                current
            }.getOrNull()
        }

        private fun renderSnapshotToWidgetIds(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            snapshot: WidgetStatsSnapshot,
            avatarBitmap: Bitmap?
        ) {
            val numFmt = NumberFormat.getIntegerInstance(Locale.US)
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPending = PendingIntent.getActivity(
                context,
                1001,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val refreshIntent = Intent(context, BanchoStatsWidget::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPending = PendingIntent.getBroadcast(
                context,
                1002,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val isLoggedOut = snapshot.username.isBlank()
            val usernameText = if (isLoggedOut) "osu! Companion" else snapshot.username
            val onlineCount = snapshot.onlineFriendsCount.coerceAtLeast(0)
            val onlineFriendsText = when {
                isLoggedOut -> ""
                snapshot.widgetTheme == WidgetTheme.NOTHING_OS -> "$onlineCount ON"
                else -> {
                    val suffix = if (onlineCount == 1) "friend" else "friends"
                    "$onlineCount $suffix online"
                }
            }
            val globalRankText = if (isLoggedOut) {
                "—"
            } else {
                snapshot.globalRank?.takeIf { it > 0 }?.let { "#${numFmt.format(it)}" } ?: "—"
            }
            val compactGlobalRankText = when {
                isLoggedOut && snapshot.widgetTheme == WidgetTheme.NOTHING_OS -> "SIGN IN"
                isLoggedOut -> "Sign in"
                else -> globalRankText
            }
            val validCountry = !isLoggedOut &&
                snapshot.countryCode.isNotBlank() &&
                !snapshot.countryCode.equals("XX", ignoreCase = true) &&
                !snapshot.countryCode.equals("null", ignoreCase = true)
            val countryCodeLabel = when {
                validCountry && snapshot.widgetTheme == WidgetTheme.NOTHING_OS ->
                    snapshot.countryCode.uppercase(Locale.US)
                validCountry -> "COUNTRY (${snapshot.countryCode.uppercase(Locale.US)})"
                snapshot.widgetTheme == WidgetTheme.NOTHING_OS -> "LOCAL"
                else -> "COUNTRY"
            }
            val countryRankText = if (isLoggedOut) {
                "—"
            } else {
                snapshot.countryRank?.takeIf { it > 0 }?.let { "#${numFmt.format(it)}" } ?: "—"
            }
            val ppText = if (isLoggedOut) {
                "—"
            } else {
                snapshot.pp?.takeIf { it.isFinite() && it >= 0.0 }?.let {
                    "${numFmt.format(it.roundToInt())} pp"
                } ?: "—"
            }
            val accuracyText = if (isLoggedOut) {
                "—%"
            } else {
                snapshot.accuracy?.takeIf { it.isFinite() && it >= 0.0 }?.let {
                    String.format(Locale.US, "%.2f%%", it)
                } ?: "—%"
            }
            val footerText = when {
                isLoggedOut && snapshot.widgetTheme == WidgetTheme.NOTHING_OS -> "TAP TO SIGN IN"
                isLoggedOut -> "Tap to sign in"
                snapshot.updatedAtMillis > 0L && snapshot.widgetTheme == WidgetTheme.NOTHING_OS ->
                    "SYNC ${timeFmt.format(Date(snapshot.updatedAtMillis))}"
                snapshot.updatedAtMillis > 0L ->
                    "Updated ${timeFmt.format(Date(snapshot.updatedAtMillis))}"
                snapshot.widgetTheme == WidgetTheme.NOTHING_OS -> "OSU.STATS"
                else -> "Tap to refresh"
            }

            val safeBitmap = if (isLoggedOut) {
                null
            } else {
                clampWidgetBitmap(avatarBitmap, MAX_WIDGET_BITMAP_PX, MAX_WIDGET_BITMAP_BYTES)
            }

            fun buildThemedRemoteViews(compact: Boolean, avatarForRender: Bitmap?): RemoteViews {
                val layoutId = when (snapshot.widgetTheme) {
                    WidgetTheme.NOTHING_OS -> if (compact) {
                        R.layout.widget_bancho_stats_nothing_compact
                    } else {
                        R.layout.widget_bancho_stats_nothing
                    }
                    WidgetTheme.MATERIAL_YOU -> if (compact) {
                        R.layout.widget_bancho_stats_material_compact
                    } else {
                        R.layout.widget_bancho_stats_material
                    }
                    WidgetTheme.OSU_DARK -> if (compact) {
                        R.layout.widget_bancho_stats_osu_compact
                    } else {
                        R.layout.widget_bancho_stats
                    }
                }
                return RemoteViews(context.packageName, layoutId).apply {
                    // Fade only the background shape so translucent 80% keeps text crisp and opaque
                    setInt(
                        R.id.widget_bg_image,
                        "setImageAlpha",
                        if (snapshot.widgetTranslucent) 204 else 255
                    )
                    setTextViewText(R.id.widget_username, usernameText)
                    setTextViewText(R.id.widget_global_rank, if (compact) compactGlobalRankText else globalRankText)
                    setTextViewText(R.id.widget_pp, ppText)

                    if (!compact) {
                        setViewVisibility(
                            R.id.widget_friends_badge,
                            if (isLoggedOut) View.GONE else View.VISIBLE
                        )
                        setViewVisibility(
                            R.id.widget_friends_dot,
                            if (!isLoggedOut && onlineCount > 0) View.VISIBLE else View.GONE
                        )
                        setTextViewText(R.id.widget_friends_online, onlineFriendsText)
                        setTextViewText(R.id.widget_country_label, countryCodeLabel)
                        setTextViewText(R.id.widget_country_rank, countryRankText)
                        setTextViewText(R.id.widget_accuracy, accuracyText)
                        setTextViewText(R.id.widget_updated_at, footerText)

                        if (avatarForRender != null && !avatarForRender.isRecycled) {
                            setImageViewBitmap(R.id.widget_avatar, avatarForRender)
                        } else {
                            setImageViewResource(R.id.widget_avatar, R.mipmap.ic_launcher_round)
                        }
                    }

                    setOnClickPendingIntent(R.id.widget_root, openAppPending)
                    setOnClickPendingIntent(R.id.widget_refresh_btn, refreshPending)
                }
            }

            fun pushToWidget(
                appWidgetId: Int,
                compactViews: RemoteViews,
                expandedViews: RemoteViews
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val responsiveViews = RemoteViews(
                        mapOf(
                            SizeF(100f, 90f) to compactViews,
                            SizeF(210f, 90f) to expandedViews
                        )
                    )
                    appWidgetManager.updateAppWidget(appWidgetId, responsiveViews)
                } else {
                    val minWidthDp = runCatching {
                        appWidgetManager.getAppWidgetOptions(appWidgetId)
                            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                    }.getOrDefault(250)
                    val views = if (minWidthDp in 1..209) compactViews else expandedViews
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }

            val compactViews = buildThemedRemoteViews(compact = true, avatarForRender = null)
            val expandedViews = buildThemedRemoteViews(compact = false, avatarForRender = safeBitmap)
            var fallbackExpandedViews: RemoteViews? = null

            for (appWidgetId in appWidgetIds) {
                try {
                    pushToWidget(appWidgetId, compactViews, expandedViews)
                } catch (_: Throwable) {
                    // Retry without bitmap if Binder TransactionTooLargeException or OOM occurs so
                    // theme, stats, click PendingIntents, and responsive grid bindings stay intact.
                    if (safeBitmap != null) {
                        val noBitmapExpanded = fallbackExpandedViews
                            ?: buildThemedRemoteViews(compact = false, avatarForRender = null)
                                .also { fallbackExpandedViews = it }
                        runCatching {
                            pushToWidget(appWidgetId, compactViews, noBitmapExpanded)
                        }
                    }
                }
            }
        }
    }
}
