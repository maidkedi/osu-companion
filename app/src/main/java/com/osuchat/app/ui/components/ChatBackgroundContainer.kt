package com.osuchat.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.osuchat.app.domain.model.SeasonalBackgroundItem
import com.osuchat.app.domain.model.WallpaperConfig
import com.osuchat.app.ui.theme.DarkBackground
import com.osuchat.app.ui.theme.DarkSurface
import com.osuchat.app.ui.theme.LocalOsuSurfaces
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.WallpaperPresetColors
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class WallpaperPreset(
    val id: Int,
    val name: String,
    val colors: List<Color>,
    val accent: Color
)

val BUILT_IN_WALLPAPER_PRESETS = listOf(
    WallpaperPreset(
        id = 0,
        name = "Obsidian Pink",
        colors = listOf(DarkBackground),
        accent = WallpaperPresetColors.ObsidianAccent
    ),
    WallpaperPreset(
        id = 1,
        name = "Charcoal Triangles",
        colors = listOf(DarkSurface),
        accent = WallpaperPresetColors.CharcoalAccent
    ),
    WallpaperPreset(
        id = 2,
        name = "Slate Grid",
        colors = listOf(WallpaperPresetColors.SlateBg),
        accent = WallpaperPresetColors.SlateAccent
    ),
    WallpaperPreset(
        id = 3,
        name = "Rose Matte",
        colors = listOf(WallpaperPresetColors.RoseBg),
        accent = WallpaperPresetColors.RoseAccent
    ),
    WallpaperPreset(
        id = 4,
        name = "Steel Rings",
        colors = listOf(WallpaperPresetColors.SteelBg),
        accent = WallpaperPresetColors.SteelAccent
    ),
    WallpaperPreset(
        id = 5,
        name = "Pure Matte",
        colors = listOf(WallpaperPresetColors.PureMatteBg),
        accent = WallpaperPresetColors.PureMatteAccent
    )
)

internal fun computeWallpaperScaleFactor(srcSize: Size, dstSize: Size, scale: Float): Float {
    if (srcSize.width <= 0f || srcSize.height <= 0f || dstSize.width <= 0f || dstSize.height <= 0f) {
        return 1f
    }
    val safeScale = scale.coerceIn(WallpaperConfig.MIN_SCALE, WallpaperConfig.MAX_SCALE)
    val scaleW = dstSize.width / srcSize.width
    val scaleH = dstSize.height / srcSize.height
    val cropScale = maxOf(scaleW, scaleH)
    val fitScale = minOf(scaleW, scaleH)
    if (safeScale >= 1.0f) {
        return cropScale * safeScale
    }
    val minFactor = minOf(fitScale, cropScale * 0.5f)
    val t = ((safeScale - WallpaperConfig.MIN_SCALE) / (1.0f - WallpaperConfig.MIN_SCALE)).coerceIn(0f, 1f)
    return minFactor + (cropScale - minFactor) * t
}

private class WallpaperContentScale(private val zoomScale: Float) : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
        val factor = computeWallpaperScaleFactor(srcSize, dstSize, zoomScale)
        return ScaleFactor(factor, factor)
    }

    override fun equals(other: Any?): Boolean =
        other is WallpaperContentScale && other.zoomScale == zoomScale

    override fun hashCode(): Int = zoomScale.hashCode()
}

@Composable
fun ChatBackgroundContainer(
    config: WallpaperConfig,
    modifier: Modifier = Modifier,
    onImageIntrinsicSize: ((Size) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val preset = BUILT_IN_WALLPAPER_PRESETS.getOrElse(config.presetId) {
        BUILT_IN_WALLPAPER_PRESETS.first()
    }
    val context = LocalContext.current

    Box(modifier = modifier) {
        var imageLoadFailed by remember(config.customImageUri) { mutableStateOf(false) }
        val safeBlur = config.blurRadiusDp.coerceIn(0f, 24f)
        val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && safeBlur > 0f) {
            Modifier.blur(radius = safeBlur.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
        } else {
            Modifier
        }

        if (!config.customImageUri.isNullOrBlank() && !imageLoadFailed) {
            val safeScale = config.scale.coerceIn(WallpaperConfig.MIN_SCALE, WallpaperConfig.MAX_SCALE)
            val safeBiasX = config.biasX.coerceIn(WallpaperConfig.MIN_BIAS, WallpaperConfig.MAX_BIAS)
            val safeBiasY = config.biasY.coerceIn(WallpaperConfig.MIN_BIAS, WallpaperConfig.MAX_BIAS)
            val imageRequest = remember(config.customImageUri, context) {
                ImageRequest.Builder(context)
                    .data(Uri.parse(config.customImageUri))
                    .size(2048)
                    .scale(Scale.FIT)
                    .precision(Precision.INEXACT)
                    .build()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .clipToBounds()
                    .then(blurModifier)
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = "Chat background",
                    alignment = BiasAlignment(horizontalBias = safeBiasX, verticalBias = safeBiasY),
                    contentScale = remember(safeScale) { WallpaperContentScale(safeScale) },
                    onSuccess = { onImageIntrinsicSize?.invoke(it.painter.intrinsicSize) },
                    onError = { imageLoadFailed = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            val defaultBg = MaterialTheme.colorScheme.background
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurModifier)
            ) {
                drawPresetWallpaper(preset, defaultBg)
            }
        }

        // Adjustable Dimming Overlay (0% - 85%)
        val safeDim = config.dimAmount.coerceIn(0f, 0.85f)
        if (safeDim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = safeDim))
            )
        }

        content()
    }
}

private fun DrawScope.drawPresetWallpaper(
    preset: WallpaperPreset,
    defaultBackground: Color = DarkBackground
) {
    drawRect(
        color = if (preset.id == 0) defaultBackground else preset.colors.firstOrNull() ?: defaultBackground,
        size = size
    )

    val w = size.width
    val h = size.height
    when (preset.id) {
        0, 3 -> {
            drawCircle(
                color = preset.accent.copy(alpha = 0.08f),
                radius = w * 0.36f,
                center = Offset(w * 0.84f, h * 0.20f),
                style = Stroke(width = 2.5f)
            )
            drawCircle(
                color = preset.accent.copy(alpha = 0.08f),
                radius = w * 0.24f,
                center = Offset(w * 0.16f, h * 0.78f),
                style = Stroke(width = 2f)
            )
        }
        1 -> {
            val positions = listOf(
                Triple(0.18f, 0.22f, 0.13f),
                Triple(0.76f, 0.34f, 0.18f),
                Triple(0.32f, 0.66f, 0.15f),
                Triple(0.82f, 0.80f, 0.11f)
            )
            for ((rx, ry, rs) in positions) {
                val cx = w * rx
                val cy = h * ry
                val r = w * rs
                val path = Path().apply {
                    moveTo(cx, cy - r)
                    lineTo(cx + r * 0.86f, cy + r * 0.5f)
                    lineTo(cx - r * 0.86f, cy + r * 0.5f)
                    close()
                }
                drawPath(path, color = preset.accent.copy(alpha = 0.09f), style = Stroke(width = 1.5f))
            }
        }
        2 -> {
            val step = (w / 7f).coerceAtLeast(48f)
            var x = -h
            while (x < w + h) {
                drawLine(
                    color = preset.accent.copy(alpha = 0.07f),
                    start = Offset(x, 0f),
                    end = Offset(x + h * 0.6f, h),
                    strokeWidth = 1.5f
                )
                x += step
            }
        }
        4 -> {
            for (i in 1..4) {
                drawCircle(
                    color = preset.accent.copy(alpha = 0.06f),
                    radius = w * (0.14f * i),
                    center = Offset(w * 0.5f, h * 0.45f),
                    style = Stroke(width = 1.5f)
                )
            }
        }
        else -> {
            drawLine(
                color = preset.accent.copy(alpha = 0.08f),
                start = Offset(0f, h * 0.35f),
                end = Offset(w, h * 0.35f),
                strokeWidth = 1f
            )
        }
    }
}

@Composable
fun WallpaperCustomizerPanel(
    config: WallpaperConfig,
    onConfigChange: (WallpaperConfig) -> Unit,
    onResetToGlobal: (() -> Unit)? = null,
    seasonalBackgrounds: List<SeasonalBackgroundItem> = emptyList(),
    isSeasonalLoading: Boolean = false,
    downloadingSeasonalUrl: String? = null,
    onSelectSeasonalBackground: ((SeasonalBackgroundItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val surfaces = LocalOsuSurfaces.current
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val persistedUri = persistWallpaperUri(context, uri)
            onConfigChange(
                config.copy(
                    customImageUri = persistedUri.toString(),
                    scale = 1.0f,
                    biasX = 0.0f,
                    biasY = 0.0f
                )
            )
        }
    }

    val previewOuterShape = RoundedCornerShape(OsuRadius.lg)
    val previewBubbleShape = RoundedCornerShape(OsuRadius.lg)
    val swatchShape = RoundedCornerShape(OsuRadius.md)
    val buttonShape = RoundedCornerShape(OsuRadius.md)

    val hasCustomImage = !config.customImageUri.isNullOrBlank()
    var localScale by remember(config.customImageUri) {
        mutableFloatStateOf(config.scale.coerceIn(WallpaperConfig.MIN_SCALE, WallpaperConfig.MAX_SCALE))
    }
    var localBiasX by remember(config.customImageUri) {
        mutableFloatStateOf(config.biasX.coerceIn(WallpaperConfig.MIN_BIAS, WallpaperConfig.MAX_BIAS))
    }
    var localBiasY by remember(config.customImageUri) {
        mutableFloatStateOf(config.biasY.coerceIn(WallpaperConfig.MIN_BIAS, WallpaperConfig.MAX_BIAS))
    }
    var lastLocalEditMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(config.scale, config.biasX, config.biasY) {
        if (SystemClock.uptimeMillis() - lastLocalEditMs > 300L) {
            localScale = config.scale.coerceIn(WallpaperConfig.MIN_SCALE, WallpaperConfig.MAX_SCALE)
            localBiasX = config.biasX.coerceIn(WallpaperConfig.MIN_BIAS, WallpaperConfig.MAX_BIAS)
            localBiasY = config.biasY.coerceIn(WallpaperConfig.MIN_BIAS, WallpaperConfig.MAX_BIAS)
        }
    }

    val latestConfig by rememberUpdatedState(config)
    val latestOnConfigChange by rememberUpdatedState(onConfigChange)
    fun updateFraming(newScale: Float, newBiasX: Float, newBiasY: Float) {
        val s = newScale.coerceIn(WallpaperConfig.MIN_SCALE, WallpaperConfig.MAX_SCALE)
        val bx = newBiasX.coerceIn(WallpaperConfig.MIN_BIAS, WallpaperConfig.MAX_BIAS)
        val by = newBiasY.coerceIn(WallpaperConfig.MIN_BIAS, WallpaperConfig.MAX_BIAS)
        localScale = s
        localBiasX = bx
        localBiasY = by
        lastLocalEditMs = SystemClock.uptimeMillis()
        latestOnConfigChange(latestConfig.copy(scale = s, biasX = bx, biasY = by))
    }

    val previewConfig = if (hasCustomImage) {
        config.copy(scale = localScale, biasX = localBiasX, biasY = localBiasY)
    } else {
        config
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (hasCustomImage) {
            val screenCfg = LocalConfiguration.current
            val phoneAspect = (
                screenCfg.screenWidthDp.toFloat() / screenCfg.screenHeightDp.toFloat().coerceAtLeast(1f)
                ).coerceIn(0.45f, 0.62f)
            var previewSize by remember { mutableStateOf(IntSize.Zero) }
            var intrinsicSize by remember(config.customImageUri) { mutableStateOf(Size.Unspecified) }

            val curScale by rememberUpdatedState(localScale)
            val curBiasX by rememberUpdatedState(localBiasX)
            val curBiasY by rememberUpdatedState(localBiasY)
            val curPreviewSize by rememberUpdatedState(previewSize)
            val curIntrinsic by rememberUpdatedState(intrinsicSize)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(previewOuterShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(OsuRadius.hairline, MaterialTheme.colorScheme.outlineVariant, previewOuterShape)
                    .padding(vertical = OsuSpacing.md),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .height(220.dp)
                            .aspectRatio(phoneAspect)
                            .clip(swatchShape)
                            .border(OsuRadius.hairline, MaterialTheme.colorScheme.outline, swatchShape)
                            .onSizeChanged { previewSize = it }
                            .pointerInput(config.customImageUri) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val nextScale = (curScale * zoom).coerceIn(
                                        WallpaperConfig.MIN_SCALE,
                                        WallpaperConfig.MAX_SCALE
                                    )
                                    val dst = Size(
                                        curPreviewSize.width.toFloat(),
                                        curPreviewSize.height.toFloat()
                                    )
                                    val src = curIntrinsic.takeIf {
                                        it != Size.Unspecified && it.width > 0f && it.height > 0f
                                    } ?: Size(1920f, 1080f)
                                    val factor = computeWallpaperScaleFactor(src, dst, nextScale)
                                    val availX = (dst.width - src.width * factor) / 2f
                                    val availY = (dst.height - src.height * factor) / 2f
                                    val nextBiasX = if (abs(availX) > 1f) {
                                        (curBiasX + pan.x / availX).coerceIn(
                                            WallpaperConfig.MIN_BIAS,
                                            WallpaperConfig.MAX_BIAS
                                        )
                                    } else {
                                        curBiasX
                                    }
                                    val nextBiasY = if (abs(availY) > 1f) {
                                        (curBiasY + pan.y / availY).coerceIn(
                                            WallpaperConfig.MIN_BIAS,
                                            WallpaperConfig.MAX_BIAS
                                        )
                                    } else {
                                        curBiasY
                                    }
                                    updateFraming(nextScale, nextBiasX, nextBiasY)
                                }
                            }
                    ) {
                        ChatBackgroundContainer(
                            config = previewConfig,
                            onImageIntrinsicSize = { intrinsicSize = it },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            WallpaperPreviewBubbles(previewBubbleShape = previewBubbleShape)
                        }
                    }
                    Spacer(modifier = Modifier.height(OsuSpacing.sm))
                    Text(
                        text = "Drag to pan · Pinch to zoom",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(OsuSpacing.md))

            val isDefaultFraming = abs(localScale - 1.0f) <= 0.005f &&
                abs(localBiasX) <= 0.005f &&
                abs(localBiasY) <= 0.005f
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Framing",
                    style = MaterialTheme.typography.titleSmall
                )
                TextButton(
                    onClick = { updateFraming(1.0f, 0.0f, 0.0f) },
                    enabled = !isDefaultFraming,
                    shape = buttonShape,
                    contentPadding = PaddingValues(horizontal = OsuSpacing.md, vertical = OsuSpacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(OsuSpacing.xs))
                    Text("Reset framing", style = MaterialTheme.typography.labelMedium)
                }
            }

            Text(
                text = "Zoom: ${String.format(Locale.US, "%.2fx", localScale)}",
                style = MaterialTheme.typography.bodyMedium.tnum(),
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = localScale,
                onValueChange = { updateFraming(it, localBiasX, localBiasY) },
                valueRange = WallpaperConfig.MIN_SCALE..WallpaperConfig.MAX_SCALE
            )

            Text(
                text = "Horizontal: ${formatSignedPercent(localBiasX)}",
                style = MaterialTheme.typography.bodyMedium.tnum(),
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = localBiasX,
                onValueChange = { updateFraming(localScale, it, localBiasY) },
                valueRange = WallpaperConfig.MIN_BIAS..WallpaperConfig.MAX_BIAS
            )

            Text(
                text = "Vertical: ${formatSignedPercent(localBiasY)}",
                style = MaterialTheme.typography.bodyMedium.tnum(),
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = localBiasY,
                onValueChange = { updateFraming(localScale, localBiasX, it) },
                valueRange = WallpaperConfig.MIN_BIAS..WallpaperConfig.MAX_BIAS
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(previewOuterShape)
                    .border(OsuRadius.hairline, MaterialTheme.colorScheme.outlineVariant, previewOuterShape)
            ) {
                ChatBackgroundContainer(config = config, modifier = Modifier.fillMaxSize()) {
                    WallpaperPreviewBubbles(previewBubbleShape = previewBubbleShape)
                }
            }
        }

        Spacer(modifier = Modifier.height(OsuSpacing.md))

        Text(
            text = "Presets",
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(OsuSpacing.sm))

        LazyRow(
            contentPadding = PaddingValues(horizontal = OsuSpacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.md)
        ) {
            items(BUILT_IN_WALLPAPER_PRESETS, key = { it.id }) { preset ->
                val isSelected = config.customImageUri == null && config.presetId == preset.id
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        onConfigChange(
                            config.copy(
                                presetId = preset.id,
                                customImageUri = null
                            )
                        )
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(swatchShape)
                            .border(
                                width = if (isSelected) 1.5.dp else OsuRadius.hairline,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = swatchShape
                            )
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawPresetWallpaper(preset)
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(OsuSpacing.xs)
                                    .size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(OsuSpacing.xs))
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (onSelectSeasonalBackground != null || seasonalBackgrounds.isNotEmpty() || isSeasonalLoading) {
            Spacer(modifier = Modifier.height(OsuSpacing.lg))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
            ) {
                Text(
                    text = "Official osu! seasonal",
                    style = MaterialTheme.typography.titleSmall
                )
                if (isSeasonalLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(OsuSpacing.sm))

            if (seasonalBackgrounds.isEmpty()) {
                Text(
                    text = if (isSeasonalLoading) {
                        "Loading seasonal artworks…"
                    } else {
                        "Seasonal artworks unavailable offline"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = OsuSpacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                ) {
                    items(seasonalBackgrounds, key = { it.url }) { item ->
                        val isSelected = !config.customImageUri.isNullOrBlank() &&
                            config.customImageUri.contains(item.localFilePrefix)
                        val isDownloading = downloadingSeasonalUrl == item.url
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(112.dp)
                                .clickable(enabled = downloadingSeasonalUrl == null && onSelectSeasonalBackground != null) {
                                    onSelectSeasonalBackground?.invoke(item)
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(112.dp)
                                    .height(64.dp)
                                    .clip(swatchShape)
                                    .border(
                                        width = if (isSelected) 1.5.dp else OsuRadius.hairline,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = swatchShape
                                    )
                            ) {
                                AsyncImage(
                                    model = item.url,
                                    contentDescription = "Seasonal art by ${item.artistUsername}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (isDownloading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(surfaces.mediaScrimTop),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(OsuSpacing.xs)
                                            .size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(OsuSpacing.xs))
                            Text(
                                text = "by ${item.artistUsername}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(OsuSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
        ) {
            Button(
                onClick = {
                    mediaPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                shape = buttonShape,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(OsuSpacing.sm))
                Text("Pick photo", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            if (onResetToGlobal != null) {
                OutlinedButton(
                    onClick = onResetToGlobal,
                    shape = buttonShape,
                    border = osuHairlineBorder(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(OsuSpacing.sm))
                    Text("Reset to global", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else if (config.customImageUri != null) {
                OutlinedButton(
                    onClick = { onConfigChange(config.copy(customImageUri = null)) },
                    shape = buttonShape,
                    border = osuHairlineBorder(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear photo", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Spacer(modifier = Modifier.height(OsuSpacing.md))

        val safeDim = config.dimAmount.coerceIn(0f, 0.85f)
        Text(
            text = "Dim overlay: ${(safeDim * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodyMedium.tnum(),
            fontWeight = FontWeight.Medium
        )
        Slider(
            value = safeDim,
            onValueChange = {
                onConfigChange(
                    config.copy(
                        dimAmount = it.coerceIn(0f, 0.85f)
                    )
                )
            },
            valueRange = 0f..0.85f
        )

        Spacer(modifier = Modifier.height(OsuSpacing.sm))

        val isBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val safeBlur = config.blurRadiusDp.coerceIn(0f, 24f)
        Text(
            text = "Blur radius: ${safeBlur.roundToInt()} dp",
            style = MaterialTheme.typography.bodyMedium.tnum(),
            fontWeight = FontWeight.Medium,
            color = if (isBlurSupported) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Slider(
            value = safeBlur,
            onValueChange = {
                onConfigChange(
                    config.copy(
                        blurRadiusDp = it.coerceIn(0f, 24f)
                    )
                )
            },
            valueRange = 0f..24f,
            enabled = isBlurSupported
        )
        if (!isBlurSupported) {
            Text(
                text = "Wallpaper blur requires Android 12 or newer",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WallpaperPreviewBubbles(previewBubbleShape: RoundedCornerShape) {
    val surfaces = LocalOsuSurfaces.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(OsuSpacing.sm),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .clip(previewBubbleShape)
                .background(surfaces.incomingBubble)
                .border(
                    OsuRadius.hairline,
                    MaterialTheme.colorScheme.outlineVariant,
                    previewBubbleShape
                )
                .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm)
        ) {
            Text(
                text = "BanchoBot: Map updated",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.End)
                .clip(previewBubbleShape)
                .background(surfaces.outgoingBubble)
                .border(
                    OsuRadius.hairline,
                    MaterialTheme.colorScheme.outlineVariant,
                    previewBubbleShape
                )
                .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm)
        ) {
            Text(
                text = "Ready for multi",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatSignedPercent(bias: Float): String {
    val pct = (bias.coerceIn(WallpaperConfig.MIN_BIAS, WallpaperConfig.MAX_BIAS) * 100f).roundToInt()
    return if (pct > 0) "+$pct%" else "$pct%"
}

private fun persistWallpaperUri(context: Context, uri: Uri): Uri {
    val persistGranted = runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        true
    }.getOrDefault(false)

    if (persistGranted) return uri

    // Fallback: copy picked image to internal filesDir so it survives device reboots
    return runCatching {
        val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val dest = File(dir, "wallpaper_${System.currentTimeMillis()}.img")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (dest.exists() && dest.length() > 0L) Uri.fromFile(dest) else uri
    }.getOrDefault(uri)
}
