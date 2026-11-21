package com.osuchat.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.osuchat.app.R
import com.osuchat.app.domain.model.ConnectionStatus
import com.osuchat.app.ui.theme.LocalOsuSurfaces
import com.osuchat.app.ui.theme.ModeCatch
import com.osuchat.app.ui.theme.ModeMania
import com.osuchat.app.ui.theme.ModeOsu
import com.osuchat.app.ui.theme.ModeTaiko
import com.osuchat.app.ui.theme.OsuMotion
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.StatusConnecting
import com.osuchat.app.ui.theme.StatusError
import com.osuchat.app.ui.theme.StatusOffline
import com.osuchat.app.ui.theme.StatusOnline

@Composable
fun PlayerAvatar(
    name: String,
    avatarUrl: String?,
    size: Dp = 44.dp,
    isChannel: Boolean = false,
    showOnlineDot: Boolean = false,
    isOnline: Boolean = false,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val surfaces = LocalOsuSurfaces.current
    val avatarDescription = if (isChannel) "Channel $name" else "$name avatar"
    val initials = name.trimStart('#').take(2).uppercase().ifEmpty { "?" }
    val fallbackBg = when {
        isChannel && isActive -> surfaces.accentSubtle
        isChannel -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fallbackFg = when {
        isChannel && isActive -> MaterialTheme.colorScheme.primary
        isChannel -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val frameBorderColor = when {
        isChannel && isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = avatarDescription }
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(fallbackBg)
                .border(OsuRadius.hairline, frameBorderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isChannel) "#" else initials,
                color = fallbackFg,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.38f).sp
            )

            if (!isChannel && !avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                )
            }
        }

        if (showOnlineDot) {
            val dotColor by animateColorAsState(
                targetValue = if (isOnline) StatusOnline else StatusOffline,
                animationSpec = OsuMotion.colorSpec(),
                label = "onlineDot"
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size.value * 0.27f).dp.coerceAtLeast(10.dp))
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
            )
        }
    }
}

@Composable
fun ConnectionStatusChip(
    status: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val (targetColor, label) = when (status) {
        ConnectionStatus.ONLINE -> StatusOnline to "Online"
        ConnectionStatus.CONNECTING -> StatusConnecting to "Connecting"
        ConnectionStatus.RECONNECTING -> StatusConnecting to "Reconnecting"
        ConnectionStatus.ERROR -> StatusError to "Error"
        ConnectionStatus.OFFLINE -> StatusOffline to "Offline"
    }
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = OsuMotion.colorSpec(),
        label = "statusColor"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(OsuRadius.xs),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(OsuRadius.hairline, color.copy(alpha = 0.28f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(OsuSpacing.xs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

fun countryCodeToFlagEmoji(countryCode: String): String {
    val code = countryCode.trim().uppercase()
    if (code.length != 2 || code == "XX" || !code.all { it in 'A'..'Z' }) {
        return ""
    }
    val first = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
    val second = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

data class OsuModeVisual(
    val apiMode: String,
    val label: String,
    val symbol: String,
    val color: Color,
    @DrawableRes val iconRes: Int
)

fun osuModeVisual(mode: String?): OsuModeVisual = when (mode?.trim()?.lowercase()) {
    "taiko" -> OsuModeVisual("taiko", "taiko", "taiko", ModeTaiko, R.drawable.ic_mode_taiko)
    "fruits", "catch" -> OsuModeVisual("fruits", "catch", "catch", ModeCatch, R.drawable.ic_mode_catch)
    "mania" -> OsuModeVisual("mania", "mania", "mania", ModeMania, R.drawable.ic_mode_mania)
    else -> OsuModeVisual("osu", "osu!", "osu!", ModeOsu, R.drawable.ic_mode_osu)
}

@Composable
fun OsuModeBadge(
    mode: String?,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val visual = osuModeVisual(mode)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(OsuRadius.xs),
        color = visual.color.copy(alpha = 0.14f),
        border = BorderStroke(OsuRadius.hairline, visual.color.copy(alpha = 0.35f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) OsuSpacing.xs else OsuSpacing.sm,
                vertical = OsuSpacing.xxs
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
        ) {
            Icon(
                painter = painterResource(id = visual.iconRes),
                contentDescription = null,
                tint = visual.color,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = visual.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = visual.color,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
