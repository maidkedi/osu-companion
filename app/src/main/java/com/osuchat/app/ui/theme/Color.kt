package com.osuchat.app.ui.theme

import androidx.compose.ui.graphics.Color

// Single-purpose osu! focal accent (used strictly on primary actions, active states, and highlights).
val OsuPink = Color(0xFFFF66AA)
val OsuPinkDark = Color(0xFFC92E69)
val OsuPinkContainer = Color(0xFFFFE5F0)
val OsuPinkContainerDark = Color(0xFF3A1628)

// Semantic status tokens (bound strictly to live domain/connection/delivery state).
val StatusOnline = Color(0xFF7FA82A)
val StatusConnecting = Color(0xFFE5A124)
val StatusOffline = Color(0xFF8A8A96)
val StatusError = Color(0xFFE05252)
val StatusRead = Color(0xFF56A8D6)

// Semantic compatibility aliases for existing chat/status call sites.
val LazerCyan = StatusRead
val LazerMint = StatusOnline

// Matte obsidian / slate-charcoal elevation steps (Dark theme).
val DarkBackground = Color(0xFF121216)
val DarkSurfaceLow = Color(0xFF17171D)
val DarkSurface = Color(0xFF1D1D24)
val DarkSurfaceHigh = Color(0xFF24242D)
val DarkSurfaceHighest = Color(0xFF2C2C37)
val DarkSurfaceVariant = DarkSurfaceHigh
val DarkBorder = Color(0xFF2A2A35)
val DarkHairline = Color(0x28EDEDF2)
val DarkOutgoingBubble = Color(0xFF2D1824)
val DarkIncomingBubble = Color(0xFF1E1E26)

// True AMOLED pure black steps (AMOLED theme).
val AmoledBackground = Color(0xFF000000)
val AmoledSurfaceLow = Color(0xFF09090C)
val AmoledSurface = AmoledSurfaceLow
val AmoledSurfaceContainer = Color(0xFF111116)
val AmoledSurfaceHigh = Color(0xFF18181F)
val AmoledSurfaceHighest = Color(0xFF20202A)
val AmoledBorder = Color(0xFF1F1F28)
val AmoledHairline = Color(0x2DEDEDF2)
val AmoledOutgoingBubble = Color(0xFF24111C)
val AmoledIncomingBubble = Color(0xFF111116)

// Crisp matte neutral steps (Light theme).
val LightBackground = Color(0xFFF5F6F8)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceLow = Color(0xFFF0F1F5)
val LightSurfaceHigh = Color(0xFFE7E9EF)
val LightSurfaceHighest = Color(0xFFDCDFE8)
val LightSurfaceVariant = LightSurfaceHigh
val LightBorder = Color(0xFFD6D8E2)
val LightHairline = Color(0x2418181E)
val LightOutgoingBubble = Color(0xFFFCEAF2)
val LightIncomingBubble = Color(0xFFFFFFFF)

// 3-tier neutral text hierarchy.
val DarkTextPrimary = Color(0xFFEDEDF2)
val DarkTextSecondary = Color(0xFFA1A1B0)
val DarkTextMuted = Color(0xFF787887)

val LightTextPrimary = Color(0xFF18181E)
val LightTextSecondary = Color(0xFF555563)
val LightTextMuted = Color(0xFF6E6E7D)

// Domain tokens: osu! game modes.
val ModeOsu = OsuPink
val ModeTaiko = Color(0xFFE05252)
val ModeCatch = Color(0xFF5CA843)
val ModeMania = Color(0xFF9C72E8)

// Domain tokens: multiplayer tournament teams.
val TeamRed = Color(0xFFE05252)
val TeamBlue = Color(0xFF4B9DF2)

// Domain tokens: leaderboard podium standings.
val PodiumGold = Color(0xFFF2C94C)
val PodiumSilver = Color(0xFFC4CAD6)
val PodiumBronze = Color(0xFFD68952)

// Domain tokens: score grades.
val GradeSilver = Color(0xFFE2E6EF)
val GradeGold = Color(0xFFF2C94C)
val GradeS = Color(0xFFE5A124)
val GradeC = Color(0xFF9B6BCC)
val GradeD = Color(0xFFD97742)
val GradeInkDark = Color(0xFF14141A)

// Domain tokens: official osu! star rating difficulty spectrum.
val StarEasy = Color(0xFF4FC0FF)
val StarNormal = Color(0xFF4FFFD5)
val StarHard = Color(0xFF7CFF4F)
val StarInsane = Color(0xFFF6F05C)
val StarExpert = Color(0xFFFF66AA)
val StarExpertPlus = Color(0xFFB15EFF)
val StarUltraDark = Color(0xFF18181E)

// Domain tokens: Top-100 mod distribution chart.
val ModNm = Color(0xFF8A8A96)
val ModHd = Color(0xFFE5A124)
val ModHr = Color(0xFFE05252)
val ModDt = Color(0xFF9B6BCC)

// Theme-invariant photographic scrims and fullscreen media backdrop.
val MediaScrimTop = Color(0x6609090E)
val MediaScrimBottom = Color(0xD909090E)
val MediaScrimSolid = Color(0xC409090E)
val MediaControlSurface = Color(0xCC1C1C24)
val FullscreenBackdrop = Color(0xF208080B)

// Built-in wallpaper preset tones.
object WallpaperPresetColors {
    val ObsidianAccent = OsuPink
    val CharcoalAccent = Color(0xFF8A8E9E)
    val SlateBg = Color(0xFF14181F)
    val SlateAccent = Color(0xFF56A8D6)
    val RoseBg = Color(0xFF1B1418)
    val RoseAccent = Color(0xFFE07AA8)
    val SteelBg = Color(0xFF14171C)
    val SteelAccent = Color(0xFF6B93B8)
    val PureMatteBg = Color(0xFF0D0D10)
    val PureMatteAccent = Color(0xFF5A5A64)
}

// Home-screen widget live preview tokens.
object WidgetPreviewColors {
    val NothingDarkSurface = Color(0xFF0B0B0C)
    val NothingDarkBorder = Color(0xFF2A2A2E)
    val NothingDarkTile = Color(0xFF141417)
    val NothingDarkTileBorder = Color(0xFF242429)
    val NothingDarkText = Color(0xFFEDEDF2)
    val NothingDarkSecondary = Color(0xFF8E8E98)
    val NothingAccentRed = Color(0xFFD71921)
    val NothingDarkBadgeBg = Color(0x1FD71921)

    val NothingLightSurface = Color(0xFFF5F5F7)
    val NothingLightBorder = Color(0xFFD8D8DF)
    val NothingLightTile = Color(0xFFFFFFFF)
    val NothingLightTileBorder = Color(0xFFE3E3EA)
    val NothingLightText = Color(0xFF0B0B0C)
    val NothingLightSecondary = Color(0xFF5F5F68)
    val NothingLightBadgeBg = Color(0x17D71921)

    val OsuDarkSurface = DarkBackground
    val OsuDarkBorder = Color(0xFF2A2A34)
    val OsuDarkTile = Color(0xFF18181E)
    val OsuDarkTileBorder = Color(0xFF262630)
    val OsuDarkText = DarkTextPrimary
    val OsuDarkSecondary = DarkTextSecondary
    val OsuDarkAccent = OsuPink
    val OsuDarkBadgeBg = Color(0x1FFF66AA)

    val OsuLightSurface = LightBackground
    val OsuLightBorder = Color(0xFFDDDFE5)
    val OsuLightTile = LightSurfaceLow
    val OsuLightText = LightTextPrimary
    val OsuLightSecondary = LightTextSecondary
    val OsuLightAccent = OsuPinkDark
    val OsuLightBadgeBg = Color(0x1AC92E69)

    val OsuGreenDot = Color(0xFF9BC71E)
    val OsuGreenBadgeBg = Color(0x1F88B300)

    val StageDarkBg = Color(0xFF0E0E12)
    val StageLightBg = Color(0xFFE6E8EE)
    val StageDarkGrid = Color(0x14FFFFFF)
    val StageLightGrid = Color(0x14000000)
}
