package com.osuchat.app.ui.theme

import android.app.Activity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.osuchat.app.domain.model.ThemeMode

/**
 * Spatial rhythm locked strictly to the 4dp grid (with 2dp reserved solely for ultra-thin progress tracks
 * and intra-turn chat bubble grouping).
 */
object OsuSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
}

/**
 * Standardized corner radii:
 * 4.dp micro-badges/mod pills, 8.dp chips/inputs/buttons, 12.dp cards/list containers/bubbles,
 * 16.dp bottom sheets/dialogs.
 */
object OsuRadius {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 8.dp
    val lg: Dp = 12.dp
    val xl: Dp = 16.dp
    val hairline: Dp = 0.5.dp
    val accentBar: Dp = 3.dp
    val PillShape: Shape = RoundedCornerShape(percent = 50)

    fun inner(outer: Dp, padding: Dp): Dp = (outer - padding).coerceAtLeast(4.dp)

    fun innerShape(outer: Dp = lg, padding: Dp): RoundedCornerShape =
        RoundedCornerShape(inner(outer, padding))
}

/**
 * Restrained motion system: single ease-out curve, fast 150-220ms transitions, zero bounce.
 */
object OsuMotion {
    val EaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    const val FastMs = 150
    const val StandardMs = 200
    const val ExpandMs = 220

    fun <T> colorSpec(durationMillis: Int = FastMs): AnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = EaseOut)

    fun floatSpec(durationMillis: Int = StandardMs): FiniteAnimationSpec<Float> =
        tween(durationMillis = durationMillis, easing = EaseOut)

    fun expandEnter(): EnterTransition =
        fadeIn(tween(ExpandMs, easing = EaseOut)) +
            expandVertically(tween(ExpandMs, easing = EaseOut))

    fun expandExit(): ExitTransition =
        fadeOut(tween(FastMs, easing = EaseOut)) +
            shrinkVertically(tween(ExpandMs, easing = EaseOut))

    fun fadeEnter(): EnterTransition = fadeIn(tween(FastMs, easing = EaseOut))

    fun fadeExit(): ExitTransition = fadeOut(tween(FastMs, easing = EaseOut))
}

@Immutable
data class OsuSurfaceTokens(
    val isDark: Boolean,
    val isAmoled: Boolean,
    val canvas: Color,
    val surfaceLow: Color,
    val surfaceDefault: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentSubtle: Color,
    val outgoingBubble: Color,
    val incomingBubble: Color,
    val mediaScrimTop: Color,
    val mediaScrimBottom: Color,
    val mediaScrimSolid: Color,
    val mediaControlSurface: Color,
    val fullscreenBackdrop: Color
)

private val DarkOsuSurfaces = OsuSurfaceTokens(
    isDark = true,
    isAmoled = false,
    canvas = DarkBackground,
    surfaceLow = DarkSurfaceLow,
    surfaceDefault = DarkSurface,
    surfaceHigh = DarkSurfaceHigh,
    surfaceHighest = DarkSurfaceHighest,
    hairline = DarkHairline,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textMuted = DarkTextMuted,
    accent = OsuPink,
    accentSubtle = OsuPink.copy(alpha = 0.14f),
    outgoingBubble = DarkOutgoingBubble,
    incomingBubble = DarkIncomingBubble,
    mediaScrimTop = MediaScrimTop,
    mediaScrimBottom = MediaScrimBottom,
    mediaScrimSolid = MediaScrimSolid,
    mediaControlSurface = MediaControlSurface,
    fullscreenBackdrop = FullscreenBackdrop
)

private val AmoledOsuSurfaces = OsuSurfaceTokens(
    isDark = true,
    isAmoled = true,
    canvas = AmoledBackground,
    surfaceLow = AmoledSurfaceLow,
    surfaceDefault = AmoledSurfaceContainer,
    surfaceHigh = AmoledSurfaceHigh,
    surfaceHighest = AmoledSurfaceHighest,
    hairline = AmoledHairline,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textMuted = DarkTextMuted,
    accent = OsuPink,
    accentSubtle = OsuPink.copy(alpha = 0.15f),
    outgoingBubble = AmoledOutgoingBubble,
    incomingBubble = AmoledIncomingBubble,
    mediaScrimTop = MediaScrimTop,
    mediaScrimBottom = MediaScrimBottom,
    mediaScrimSolid = MediaScrimSolid,
    mediaControlSurface = MediaControlSurface,
    fullscreenBackdrop = FullscreenBackdrop
)

private val LightOsuSurfaces = OsuSurfaceTokens(
    isDark = false,
    isAmoled = false,
    canvas = LightBackground,
    surfaceLow = LightSurfaceLow,
    surfaceDefault = LightSurface,
    surfaceHigh = LightSurfaceHigh,
    surfaceHighest = LightSurfaceHighest,
    hairline = LightHairline,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textMuted = LightTextMuted,
    accent = OsuPinkDark,
    accentSubtle = OsuPinkDark.copy(alpha = 0.11f),
    outgoingBubble = LightOutgoingBubble,
    incomingBubble = LightIncomingBubble,
    mediaScrimTop = MediaScrimTop,
    mediaScrimBottom = MediaScrimBottom,
    mediaScrimSolid = MediaScrimSolid,
    mediaControlSurface = MediaControlSurface,
    fullscreenBackdrop = FullscreenBackdrop
)

val LocalOsuSurfaces = staticCompositionLocalOf { DarkOsuSurfaces }

val OsuShapes = Shapes(
    extraSmall = RoundedCornerShape(OsuRadius.xs),
    small = RoundedCornerShape(OsuRadius.md),
    medium = RoundedCornerShape(OsuRadius.md),
    large = RoundedCornerShape(OsuRadius.lg),
    extraLarge = RoundedCornerShape(OsuRadius.xl)
)

@Composable
fun osuHairlineBorder(color: Color = MaterialTheme.colorScheme.outlineVariant): BorderStroke =
    BorderStroke(OsuRadius.hairline, color)

private val DarkColorScheme = darkColorScheme(
    primary = OsuPink,
    onPrimary = Color(0xFF2B0015),
    primaryContainer = OsuPinkContainerDark,
    onPrimaryContainer = Color(0xFFFFD9E8),
    secondary = Color(0xFFC2C3D0),
    onSecondary = DarkSurface,
    secondaryContainer = DarkSurfaceHighest,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = DarkTextSecondary,
    onTertiary = DarkSurface,
    tertiaryContainer = DarkSurfaceHigh,
    onTertiaryContainer = DarkTextPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkBackground,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkTextSecondary,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    outline = Color(0xFF3E3E4B),
    outlineVariant = DarkHairline,
    error = StatusError,
    onError = Color(0xFF2B0606),
    errorContainer = Color(0xFF3B1618),
    onErrorContainer = Color(0xFFFFD6D6)
)

private val OsuAmoledColorScheme = darkColorScheme(
    primary = OsuPink,
    onPrimary = Color(0xFF2B0015),
    primaryContainer = OsuPinkContainerDark,
    onPrimaryContainer = Color(0xFFFFD9E8),
    secondary = Color(0xFFC2C3D0),
    onSecondary = AmoledSurfaceLow,
    secondaryContainer = AmoledSurfaceHighest,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = DarkTextSecondary,
    onTertiary = AmoledSurfaceLow,
    tertiaryContainer = AmoledSurfaceHigh,
    onTertiaryContainer = DarkTextPrimary,
    background = AmoledBackground,
    onBackground = DarkTextPrimary,
    surface = AmoledBackground,
    onSurface = DarkTextPrimary,
    surfaceVariant = AmoledSurfaceContainer,
    onSurfaceVariant = DarkTextSecondary,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = AmoledBackground,
    surfaceContainerLow = AmoledSurfaceLow,
    surfaceContainer = AmoledSurfaceContainer,
    surfaceContainerHigh = AmoledSurfaceHigh,
    surfaceContainerHighest = AmoledSurfaceHighest,
    outline = Color(0xFF2E2E3A),
    outlineVariant = AmoledHairline,
    error = StatusError,
    onError = Color(0xFF2B0606),
    errorContainer = Color(0xFF3B1618),
    onErrorContainer = Color(0xFFFFD6D6)
)

private val LightColorScheme = lightColorScheme(
    primary = OsuPinkDark,
    onPrimary = Color.White,
    primaryContainer = OsuPinkContainer,
    onPrimaryContainer = Color(0xFF3D001E),
    secondary = Color(0xFF4D4E5C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E9F0),
    onSecondaryContainer = LightTextPrimary,
    tertiary = LightTextSecondary,
    onTertiary = Color.White,
    tertiaryContainer = LightSurfaceHigh,
    onTertiaryContainer = LightTextPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightBackground,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightTextSecondary,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHighest,
    outline = Color(0xFFB4B6C2),
    outlineVariant = LightHairline,
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFDE8E8),
    onErrorContainer = Color(0xFF410E0B)
)

@Composable
fun OsuChatTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        themeMode == ThemeMode.AMOLED -> OsuAmoledColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val surfaceTokens = when {
        themeMode == ThemeMode.AMOLED -> AmoledOsuSurfaces
        darkTheme -> DarkOsuSurfaces
        else -> LightOsuSurfaces
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalOsuSurfaces provides surfaceTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = OsuShapes,
            typography = OsuTypography,
            content = content
        )
    }
}
