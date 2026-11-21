package com.osuchat.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.osuchat.app.di.ExternalChatImageClient
import com.osuchat.app.domain.model.OsuScoreItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class RenderedScoreCard(
    val file: File,
    val width: Int,
    val height: Int,
    val ifFcPp: Double?
)

@Singleton
class ScoreCardRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    @ExternalChatImageClient private val imageLoader: ImageLoader,
    private val rosuPpEngine: RosuPpEngine
) {

    suspend fun renderScoreCard(score: OsuScoreItem): Result<RenderedScoreCard> = withContext(Dispatchers.IO) {
        try {
            val ifFcDeferredAndImages = coroutineScope {
                val ifFcDeferred = async { computeIfFcPp(score) }
                val coverDeferred = async { loadSoftwareBitmap(resolveCoverUrl(score)) }
                val avatarDeferred = async { loadSoftwareBitmap(resolveAvatarUrl(score)) }
                Triple(ifFcDeferred.await(), coverDeferred.await(), avatarDeferred.await())
            }
            val (ifFcPp, coverBitmap, avatarBitmap) = ifFcDeferredAndImages

            val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                drawScoreCard(
                    canvas = canvas,
                    score = score,
                    ifFcPp = ifFcPp,
                    coverBitmap = coverBitmap,
                    avatarBitmap = avatarBitmap
                )
                val outDir = File(context.cacheDir, SCORE_CARDS_DIR).apply { mkdirs() }
                pruneOldCards(outDir, maxKeep = 9)
                val safeId = score.id.takeIf { it > 0L } ?: System.currentTimeMillis()
                val outFile = File(outDir, "score_${safeId}_${System.currentTimeMillis()}.png")
                FileOutputStream(outFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
                Result.success(
                    RenderedScoreCard(
                        file = outFile,
                        width = CARD_WIDTH,
                        height = CARD_HEIGHT,
                        ifFcPp = ifFcPp
                    )
                )
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                if (coverBitmap != null && !coverBitmap.isRecycled) coverBitmap.recycle()
                if (avatarBitmap != null && !avatarBitmap.isRecycled) avatarBitmap.recycle()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun saveToGallery(file: File, displayName: String): Result<Uri> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() <= 0L) {
            return@withContext Result.failure(IllegalStateException("Score card PNG not found"))
        }
        runCatching {
            val cleanName = displayName.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
                .ifBlank { "osu_score_${System.currentTimeMillis()}" }
                .let { if (it.endsWith(".png", ignoreCase = true)) it else "$it.png" }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, cleanName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/OsuCompanion")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create MediaStore entry")
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                } ?: error("Could not open gallery output stream")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val picturesDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "OsuCompanion"
                ).apply { mkdirs() }
                val destFile = File(picturesDir, cleanName)
                file.copyTo(destFile, overwrite = true)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
            }
        }
    }

    private suspend fun computeIfFcPp(score: OsuScoreItem): Double? {
        val bmId = score.beatmapId?.takeIf { it > 0L } ?: return null
        val mapMaxCombo = score.beatmapMaxCombo?.takeIf { it > 0 }
        val isChoke = score.countMiss > 0 || (mapMaxCombo != null && score.maxCombo < (mapMaxCombo - 5))
        if (!isChoke) return null

        val totalHits = score.count300 + score.count100 + score.count50 + score.countMiss
        val fcAcc = if (totalHits > 0 && score.mode.equals("osu", ignoreCase = true)) {
            val adj300 = score.count300 + score.countMiss
            val num = (300.0 * adj300 + 100.0 * score.count100 + 50.0 * score.count50)
            (num / (300.0 * totalHits) * 100.0).coerceIn(0.0, 100.0)
        } else {
            score.accuracy.coerceIn(0.0, 100.0)
        }
        val modSet = score.mods
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() && it != "CL" && it != "NM" }
            .toSet()

        val calc = runCatching {
            rosuPpEngine.calculateRosuPp(
                beatmapId = bmId,
                mode = score.mode.ifBlank { "osu" },
                mods = modSet,
                accuracyPercent = fcAcc,
                nMisses = 0,
                combo = mapMaxCombo,
                isLazer = score.isLazer
            )
        }.getOrNull() ?: return null
        val fcPp = calc.pp.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val currentPp = score.pp?.takeIf { it.isFinite() && it > 0.0 }
        if (currentPp != null && fcPp <= currentPp + 1.0) return null
        return fcPp
    }

    private suspend fun loadSoftwareBitmap(url: String?): Bitmap? {
        val cleanUrl = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            val req = ImageRequest.Builder(context)
                .data(cleanUrl)
                .allowHardware(false)
                .size(1200)
                .build()
            val result = imageLoader.execute(req) as? SuccessResult ?: return null
            val drawable = result.drawable
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                val src = drawable.bitmap
                if (src.config == Bitmap.Config.ARGB_8888) {
                    src.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    src.copy(Bitmap.Config.ARGB_8888, false)
                }
            } else {
                val w = drawable.intrinsicWidth.coerceIn(64, 1200)
                val h = drawable.intrinsicHeight.coerceIn(64, 630)
                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(out)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(c)
                out
            }
        }.getOrNull()
    }

    private fun resolveCoverUrl(score: OsuScoreItem): String? {
        if (score.coverUrl.isNotBlank()) return score.coverUrl
        val setId = score.beatmapsetId?.takeIf { it > 0L } ?: return null
        return "https://assets.ppy.sh/beatmaps/$setId/covers/cover@2x.jpg"
    }

    private fun resolveAvatarUrl(score: OsuScoreItem): String? {
        if (!score.userAvatarUrl.isNullOrBlank()) return score.userAvatarUrl
        return score.userId.takeIf { it > 0L }?.let { "https://a.ppy.sh/$it" }
    }

    private fun drawScoreCard(
        canvas: Canvas,
        score: OsuScoreItem,
        ifFcPp: Double?,
        coverBitmap: Bitmap?,
        avatarBitmap: Bitmap?
    ) {
        val w = CARD_WIDTH.toFloat()
        val h = CARD_HEIGHT.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val numFmt = NumberFormat.getIntegerInstance(Locale.US)

        // Base background #121216
        canvas.drawColor(Color.parseColor("#121216"))

        val cardRect = RectF(16f, 16f, w - 16f, h - 16f)
        val clipPath = Path().apply { addRoundRect(cardRect, 24f, 24f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clipPath)

        // Beatmap cover background (CENTER_CROP) + fallback gradient
        if (coverBitmap != null && !coverBitmap.isRecycled && coverBitmap.width > 0 && coverBitmap.height > 0) {
            val scale = maxOf(w / coverBitmap.width.toFloat(), h / coverBitmap.height.toFloat())
            val drawW = coverBitmap.width * scale
            val drawH = coverBitmap.height * scale
            val left = (w - drawW) / 2f
            val top = (h - drawH) / 2f
            val srcRect = Rect(0, 0, coverBitmap.width, coverBitmap.height)
            val dstRect = RectF(left, top, left + drawW, top + drawH)
            canvas.drawBitmap(coverBitmap, srcRect, dstRect, paint)
        } else {
            val bgGrad = LinearGradient(
                0f, 0f, w, h,
                Color.parseColor("#1D1924"),
                Color.parseColor("#121216"),
                Shader.TileMode.CLAMP
            )
            paint.shader = bgGrad
            canvas.drawRect(cardRect, paint)
            paint.shader = null
        }

        // High-contrast scrim for crisp typography legibility
        val horizScrim = LinearGradient(
            0f, 0f, w, h * 0.4f,
            intArrayOf(
                Color.argb(232, 18, 18, 22),
                Color.argb(192, 18, 18, 22),
                Color.argb(226, 18, 18, 22)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = horizScrim
        canvas.drawRect(cardRect, paint)
        paint.shader = null

        val bottomScrim = LinearGradient(
            0f, h * 0.45f, 0f, h,
            Color.argb(40, 18, 18, 22),
            Color.argb(242, 18, 18, 22),
            Shader.TileMode.CLAMP
        )
        paint.shader = bottomScrim
        canvas.drawRect(cardRect, paint)
        paint.shader = null

        // Top osu! pink accent stripe
        paint.color = Color.parseColor("#FF66AA")
        canvas.drawRect(16f, 16f, w - 16f, 22f, paint)

        // Top Bar: Avatar + Username + Country + Mode Badge + Timestamp
        val avatarCenterX = 86f
        val avatarCenterY = 86f
        val avatarRadius = 38f

        if (avatarBitmap != null && !avatarBitmap.isRecycled && avatarBitmap.width > 0 && avatarBitmap.height > 0) {
            val avPath = Path().apply { addCircle(avatarCenterX, avatarCenterY, avatarRadius, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(avPath)
            val dst = RectF(
                avatarCenterX - avatarRadius,
                avatarCenterY - avatarRadius,
                avatarCenterX + avatarRadius,
                avatarCenterY + avatarRadius
            )
            canvas.drawBitmap(avatarBitmap, Rect(0, 0, avatarBitmap.width, avatarBitmap.height), dst, paint)
            canvas.restore()
        } else {
            paint.color = Color.parseColor("#2A2A35")
            canvas.drawCircle(avatarCenterX, avatarCenterY, avatarRadius, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.parseColor("#FF66AA")
        canvas.drawCircle(avatarCenterX, avatarCenterY, avatarRadius, paint)
        paint.style = Paint.Style.FILL

        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val cleanUser = TextUtils.ellipsize(
            score.username.ifBlank { "Player" },
            namePaint,
            (w - 420f).coerceAtLeast(240f),
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(cleanUser, 142f, 80f, namePaint)
        val userWidth = namePaint.measureText(cleanUser)

        // Country & mode pills next to/below username
        val subTagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D6D8E4")
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val modeLabel = when (score.mode.lowercase()) {
            "taiko" -> "osu!taiko"
            "fruits", "catch" -> "osu!catch"
            "mania" -> "osu!mania"
            else -> "osu!"
        }
        val countryPart = score.userCountryCode.takeIf { it.isNotBlank() && it != "XX" }?.uppercase()
        val metaLine = listOfNotNull(countryPart, modeLabel).joinToString("  •  ")
        subTagPaint.color = Color.parseColor("#B4B6C6")
        canvas.drawText(metaLine, 142f, 110f, subTagPaint)
        val ignoredUsed = userWidth // keeps userWidth referenced cleanly if extended

        // Timestamp top-right
        val dateText = formatCardDate(score.createdAt)
        if (dateText.isNotBlank()) {
            val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#A5A7B8")
                textSize = 19f
                typeface = Typeface.MONOSPACE
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(dateText, w - 52f, 84f, timePaint)
        }

        // Center Right: Grade Emblem Box
        val gradeBox = RectF(w - 228f, 164f, w - 52f, 340f)
        val (gradeLabel, gradeBg, gradeFg) = gradeStyle(score.rank)
        paint.color = gradeBg
        canvas.drawRoundRect(gradeBox, 20f, 20f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(65, 255, 255, 255)
        canvas.drawRoundRect(gradeBox, 20f, 20f, paint)
        paint.style = Paint.Style.FILL

        val gradeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gradeFg
            textSize = if (gradeLabel.length > 1) 84f else 98f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val gradeCenterY = gradeBox.centerY() - ((gradeTextPaint.descent() + gradeTextPaint.ascent()) / 2f)
        canvas.drawText(gradeLabel, gradeBox.centerX(), gradeCenterY, gradeTextPaint)

        // Center Left: Beatmap Title, Artist, Difficulty, SR/BPM & Mods
        val maxTextW = (gradeBox.left - 76f).coerceAtLeast(480f)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val titleStr = TextUtils.ellipsize(
            score.beatmapTitle.ifBlank { "Unknown Beatmap" },
            titlePaint,
            maxTextW,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(titleStr, 52f, 205f, titlePaint)

        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D0D2DF")
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val artistStr = TextUtils.ellipsize(
            score.beatmapArtist.ifBlank { "Unknown Artist" },
            artistPaint,
            maxTextW,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(artistStr, 52f, 244f, artistPaint)

        val diffPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF66AA")
            textSize = 23f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val srPart = score.starRating.takeIf { it.isFinite() && it > 0.0 }
            ?.let { String.format(Locale.US, "★ %.2f", it) }
        val bpmPart = score.bpm.takeIf { it.isFinite() && it > 0.0 }
            ?.let { "${it.roundToInt()} BPM" }
        val diffLine = buildString {
            append("[")
            append(score.beatmapVersion.ifBlank { "Difficulty" })
            append("]")
            listOfNotNull(srPart, bpmPart).forEach { append("   •   $it") }
        }
        val clippedDiff = TextUtils.ellipsize(diffLine, diffPaint, maxTextW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(clippedDiff, 52f, 282f, diffPaint)

        // Client Badge + Mod Pills (avoids duplicate CL / NM+CL clutter)
        val gameplayMods = score.gameplayMods
        val clientPill = score.clientBadgeLabel.uppercase(Locale.US)
        val displayPills = buildList {
            add(clientPill to true)
            gameplayMods.forEach { add(it to false) }
        }
        var modX = 52f
        val modTop = 304f
        val modHeight = 36f
        val modPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        for ((mod, isClientBadge) in displayPills.take(8)) {
            val mw = modPaint.measureText(mod) + 24f
            if (modX + mw > gradeBox.left - 24f) break
            val mRect = RectF(modX, modTop, modX + mw, modTop + modHeight)
            val isNeutralClassic = isClientBadge && !score.isLazer
            paint.color = when {
                isNeutralClassic -> Color.parseColor("#24242E")
                isClientBadge -> Color.parseColor("#2E1D2E")
                else -> Color.parseColor("#33FF66AA")
            }
            canvas.drawRoundRect(mRect, 10f, 10f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = when {
                isNeutralClassic -> Color.parseColor("#4A4A5E")
                isClientBadge -> Color.parseColor("#CCFF66AA")
                else -> Color.parseColor("#AAFF66AA")
            }
            canvas.drawRoundRect(mRect, 10f, 10f, paint)
            paint.style = Paint.Style.FILL
            val my = mRect.centerY() - ((modPaint.descent() + modPaint.ascent()) / 2f)
            canvas.drawText(mod, mRect.left + 12f, my, modPaint)
            modX += mw + 10f
        }

        // Bottom Stats Bar (#181820 surface + hairline border)
        val statsRect = RectF(48f, 382f, w - 48f, 540f)
        paint.color = Color.argb(228, 24, 24, 32)
        canvas.drawRoundRect(statsRect, 18f, 18f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.parseColor("#363646")
        canvas.drawRoundRect(statsRect, 18f, 18f, paint)
        paint.style = Paint.Style.FILL

        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9EA0B2")
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 32f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val ppPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF66AA")
            textSize = 46f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val ifFcPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFB3D6")
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        // Column 1: PP + If-FC
        val col1X = statsRect.left + 26f
        canvas.drawText("PERFORMANCE", col1X, statsRect.top + 38f, labelPaint)
        val ppText = when {
            score.pp != null && score.pp.isFinite() -> "${numFmt.format(score.pp.roundToInt())}pp"
            !score.passed -> "FAILED"
            else -> "UNRANKED"
        }
        canvas.drawText(ppText, col1X, statsRect.top + 96f, ppPaint)
        if (ifFcPp != null && ifFcPp.isFinite()) {
            canvas.drawText("${numFmt.format(ifFcPp.roundToInt())}pp if FC", col1X, statsRect.top + 128f, ifFcPaint)
        }

        // Columns 2..5 evenly distributed
        val col2X = statsRect.left + 320f
        val col3X = statsRect.left + 505f
        val col4X = statsRect.left + 725f
        val col5X = statsRect.left + 945f

        val safeAcc = score.accuracy.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0) ?: 0.0
        drawStatCol(
            canvas, col2X, statsRect.top, labelPaint, valuePaint,
            label = "ACCURACY",
            value = String.format(Locale.US, "%.2f%%", safeAcc)
        )
        val comboText = if (score.beatmapMaxCombo != null && score.beatmapMaxCombo > 0) {
            "${score.maxCombo}/${score.beatmapMaxCombo}x"
        } else {
            "${score.maxCombo}x"
        }
        drawStatCol(canvas, col3X, statsRect.top, labelPaint, valuePaint, "COMBO", comboText)
        val scoreText = if (score.score > 0L) numFmt.format(score.score) else "—"
        drawStatCol(canvas, col4X, statsRect.top, labelPaint, valuePaint, "SCORE", scoreText)

        // Hit counts 300 / 100 / 50 / Miss
        canvas.drawText("HITS (300/100/50/X)", col5X, statsRect.top + 38f, labelPaint)
        val hitsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 23f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val hitsLine = if (score.count300 + score.count100 + score.count50 + score.countMiss > 0) {
            "${score.count300}/${score.count100}/${score.count50}/${score.countMiss}"
        } else {
            "${score.countMiss}m"
        }
        canvas.drawText(hitsLine, col5X, statsRect.top + 88f, hitsPaint)

        // Footer caption
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A0A2B2")
            textSize = 18f
            typeface = Typeface.SANS_SERIF
        }
        paint.color = Color.parseColor("#FF66AA")
        canvas.drawCircle(56f, h - 42f, 5f, paint)
        canvas.drawText("osu! Companion", 72f, h - 36f, footerPaint)

        val linkText = score.beatmapId?.takeIf { it > 0L }?.let { "osu.ppy.sh/b/$it" }
            ?: score.beatmapsetId?.takeIf { it > 0L }?.let { "osu.ppy.sh/s/$it" }
            ?: ""
        if (linkText.isNotEmpty()) {
            footerPaint.textAlign = Paint.Align.RIGHT
            footerPaint.typeface = Typeface.MONOSPACE
            canvas.drawText(linkText, w - 52f, h - 36f, footerPaint)
        }

        canvas.restore()

        // Outer subtle hairline border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#323240")
        canvas.drawRoundRect(cardRect, 24f, 24f, paint)
    }

    private fun drawStatCol(
        canvas: Canvas,
        x: Float,
        top: Float,
        labelPaint: TextPaint,
        valuePaint: TextPaint,
        label: String,
        value: String
    ) {
        canvas.drawText(label, x, top + 38f, labelPaint)
        canvas.drawText(value, x, top + 90f, valuePaint)
    }

    private fun gradeStyle(raw: String): Triple<String, Int, Int> {
        val g = raw.trim().uppercase()
        val label = when (g) {
            "XH", "X" -> "SS"
            "SH" -> "S"
            "" -> "—"
            else -> g
        }
        val bg = when (g) {
            "XH", "SH" -> Color.parseColor("#E2E6EF")
            "X" -> Color.parseColor("#F2C94C")
            "S" -> Color.parseColor("#E5A124")
            "A" -> Color.parseColor("#4ADE80")
            "B" -> Color.parseColor("#60A5FA")
            "C" -> Color.parseColor("#9B6BCC")
            "D" -> Color.parseColor("#D97742")
            "F" -> Color.parseColor("#EF4444")
            else -> Color.parseColor("#2A2A35")
        }
        val fg = when (g) {
            "XH", "X", "SH", "S", "A", "B" -> Color.parseColor("#14141A")
            else -> Color.WHITE
        }
        return Triple(label, bg, fg)
    }

    private fun formatCardDate(iso: String?): String {
        val clean = iso?.trim()?.takeIf { it.isNotEmpty() } ?: return ""
        return runCatching {
            val instant = Instant.parse(clean)
            DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", Locale.US)
                .withZone(ZoneId.systemDefault())
                .format(instant)
        }.getOrDefault(clean.take(10))
    }

    private fun pruneOldCards(dir: File, maxKeep: Int) {
        val files = dir.listFiles { f -> f.isFile && f.extension.equals("png", true) }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        if (files.size > maxKeep) {
            files.drop(maxKeep).forEach { runCatching { it.delete() } }
        }
    }

    companion object {
        const val CARD_WIDTH = 1200
        const val CARD_HEIGHT = 630
        private const val SCORE_CARDS_DIR = "score_cards"
    }
}
