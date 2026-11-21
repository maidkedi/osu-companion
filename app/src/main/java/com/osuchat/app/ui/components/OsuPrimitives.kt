package com.osuchat.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.osuchat.app.ui.theme.LocalOsuSurfaces
import com.osuchat.app.ui.theme.OsuMotion
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.StatusConnecting
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum

enum class OsuBannerTone {
    ERROR,
    WARNING,
    INFO
}

@Composable
fun OsuHairlineDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = OsuRadius.hairline,
        color = color
    )
}

@Composable
fun OsuSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailingMeta: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!trailingMeta.isNullOrBlank()) {
                Text(
                    text = trailingMeta,
                    style = MaterialTheme.typography.labelSmall.tnum(),
                    color = LocalOsuSurfaces.current.textMuted,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        if (action != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = action
            )
        }
    }
}

@Composable
fun OsuGroupedContainer(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    shape: Shape = RoundedCornerShape(OsuRadius.lg),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = containerColor,
        border = osuHairlineBorder(borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(content = content)
    }
}

/**
 * Signature osu!lazer detail: crisp 3.dp vertical left accent bar paired with a stepped tonal fill.
 */
@Composable
fun OsuAccentBarContainer(
    showAccentBar: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    shape: Shape = RoundedCornerShape(OsuRadius.lg),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val animatedFill by animateColorAsState(
        targetValue = containerColor,
        animationSpec = OsuMotion.colorSpec(),
        label = "accentBarFill"
    )
    val animatedBar by animateColorAsState(
        targetValue = if (showAccentBar) accentColor else Color.Transparent,
        animationSpec = OsuMotion.colorSpec(),
        label = "accentBarColor"
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base },
        shape = shape,
        color = animatedFill,
        border = osuHairlineBorder(borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            if (showAccentBar) {
                Box(
                    modifier = Modifier
                        .width(OsuRadius.accentBar)
                        .fillMaxHeight()
                        .background(animatedBar)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                content = content
            )
        }
    }
}

@Composable
fun OsuFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isNumeric: Boolean = false
) {
    val surfaces = LocalOsuSurfaces.current
    val shape = RoundedCornerShape(OsuRadius.md)
    val bgColor by animateColorAsState(
        targetValue = if (selected) surfaces.accentSubtle else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = OsuMotion.colorSpec(),
        label = "chipBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = OsuMotion.colorSpec(),
        label = "chipText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = OsuMotion.colorSpec(),
        label = "chipBorder"
    )
    val baseStyle = MaterialTheme.typography.labelLarge
    val textStyle = if (isNumeric) baseStyle.tnum() else baseStyle

    Row(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(bgColor)
            .border(OsuRadius.hairline, borderColor, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = OsuSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = label,
            style = textStyle,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun <T> OsuSegmentedBar(
    items: List<T>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    isNumeric: Boolean = false
) {
    val surfaces = LocalOsuSurfaces.current
    val outerShape = RoundedCornerShape(OsuRadius.md)
    val innerShape = RoundedCornerShape(OsuRadius.xs)
    Surface(
        shape = outerShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = osuHairlineBorder(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OsuSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val bgColor by animateColorAsState(
                    targetValue = if (selected) surfaces.accentSubtle else Color.Transparent,
                    animationSpec = OsuMotion.colorSpec(),
                    label = "segBg"
                )
                val fgColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = OsuMotion.colorSpec(),
                    label = "segFg"
                )
                val baseStyle = MaterialTheme.typography.labelMedium
                val labelStyle = if (isNumeric) baseStyle.tnum() else baseStyle

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(innerShape)
                        .background(bgColor)
                        .clickable(role = Role.Tab) { onSelect(index) }
                        .padding(horizontal = OsuSpacing.xs),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelProvider(item),
                        style = labelStyle,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = fgColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun OsuStatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    emphasized: Boolean = false
) {
    Column(
        modifier = modifier.padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.md)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalOsuSurfaces.current.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(OsuSpacing.xxs))
        val base = if (emphasized) {
            MaterialTheme.typography.titleLarge
        } else {
            MaterialTheme.typography.titleMedium
        }
        Text(
            text = value,
            style = base.tnum(),
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun OsuMicroTag(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    isNumeric: Boolean = false
) {
    val shape = RoundedCornerShape(OsuRadius.xs)
    val style = if (isNumeric) MaterialTheme.typography.labelSmall.tnum() else MaterialTheme.typography.labelSmall
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .padding(horizontal = OsuSpacing.sm, vertical = OsuSpacing.xxs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = style,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun OsuEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OsuSpacing.xxl, vertical = OsuSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(OsuRadius.lg))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if ((primaryActionLabel != null && onPrimaryAction != null) ||
            (secondaryActionLabel != null && onSecondaryAction != null)
        ) {
            Spacer(modifier = Modifier.height(OsuSpacing.xs))
            Row(
                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (primaryActionLabel != null && onPrimaryAction != null) {
                    Button(
                        onClick = onPrimaryAction,
                        shape = RoundedCornerShape(OsuRadius.md)
                    ) {
                        Text(primaryActionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    OutlinedButton(
                        onClick = onSecondaryAction,
                        shape = RoundedCornerShape(OsuRadius.md),
                        border = osuHairlineBorder()
                    ) {
                        Text(secondaryActionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun OsuInlineBanner(
    text: String,
    tone: OsuBannerTone,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val containerColor = when (tone) {
        OsuBannerTone.ERROR -> MaterialTheme.colorScheme.errorContainer
        OsuBannerTone.WARNING -> MaterialTheme.colorScheme.surfaceContainerHigh
        OsuBannerTone.INFO -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when (tone) {
        OsuBannerTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        OsuBannerTone.WARNING, OsuBannerTone.INFO -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when (tone) {
        OsuBannerTone.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
        OsuBannerTone.WARNING -> StatusConnecting.copy(alpha = 0.5f)
        OsuBannerTone.INFO -> MaterialTheme.colorScheme.outlineVariant
    }
    val iconTint = when (tone) {
        OsuBannerTone.ERROR -> MaterialTheme.colorScheme.error
        OsuBannerTone.WARNING -> StatusConnecting
        OsuBannerTone.INFO -> MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OsuRadius.md),
        color = containerColor,
        border = osuHairlineBorder(borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OsuSpacing.md, vertical = OsuSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)
            ) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            }
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(OsuRadius.md)
                ) {
                    Text(
                        text = actionLabel,
                        color = if (tone == OsuBannerTone.ERROR) contentColor else MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OsuSheetTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = OsuSpacing.lg, end = OsuSpacing.sm, top = OsuSpacing.md, bottom = OsuSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    content = trailing
                )
            }
            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        OsuHairlineDivider()
    }
}
