package com.osuchat.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.osuchat.app.BuildConfig
import com.osuchat.app.data.repository.AppUpdateDialogState
import com.osuchat.app.data.repository.AppUpdateInfo
import com.osuchat.app.ui.theme.LocalOsuSurfaces
import com.osuchat.app.ui.theme.OsuMotion
import com.osuchat.app.ui.theme.OsuRadius
import com.osuchat.app.ui.theme.OsuSpacing
import com.osuchat.app.ui.theme.osuHairlineBorder
import com.osuchat.app.ui.theme.tnum
import com.osuchat.app.util.ApkInstaller
import java.io.File
import java.util.Locale

@Composable
fun AppUpdateDialog(
    state: AppUpdateDialogState,
    onStartDownload: (AppUpdateInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onDismiss: () -> Unit,
    onSkipVersion: (String) -> Unit,
    onAwaitingPermission: (AppUpdateInfo, File) -> Unit,
    onReadyToInstall: (AppUpdateInfo, File) -> Unit,
    onInstallError: (AppUpdateInfo, String) -> Unit
) {
    if (state is AppUpdateDialogState.Hidden) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val surfaces = LocalOsuSurfaces.current
    val latestState by rememberUpdatedState(state)

    var lastAutoInstalledPath by remember { mutableStateOf<String?>(null) }

    fun attemptInstall(update: AppUpdateInfo, apkFile: File) {
        if (!apkFile.exists()) {
            onInstallError(update, "Installer file no longer exists")
            return
        }
        if (!ApkInstaller.canInstallPackages(context)) {
            onAwaitingPermission(update, apkFile)
            return
        }
        ApkInstaller.launchInstall(context, apkFile).onFailure {
            onInstallError(update, "Could not open system package installer")
        }
    }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val current = latestState as? AppUpdateDialogState.AwaitingInstallPermission
            ?: return@rememberLauncherForActivityResult
        if (ApkInstaller.canInstallPackages(context)) {
            onReadyToInstall(current.update, current.apkFile)
        }
    }

    LaunchedEffect(state) {
        when (state) {
            is AppUpdateDialogState.ReadyToInstall -> {
                val path = state.apkFile.absolutePath
                if (lastAutoInstalledPath != path) {
                    lastAutoInstalledPath = path
                    attemptInstall(state.update, state.apkFile)
                }
            }
            is AppUpdateDialogState.Downloading,
            is AppUpdateDialogState.AwaitingInstallPermission -> {
                lastAutoInstalledPath = null
            }
            else -> Unit
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val current = latestState as? AppUpdateDialogState.AwaitingInstallPermission
                ?: return@LifecycleEventObserver
            if (event == Lifecycle.Event.ON_RESUME && ApkInstaller.canInstallPackages(context)) {
                onReadyToInstall(current.update, current.apkFile)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val update = when (state) {
        is AppUpdateDialogState.Available -> state.update
        is AppUpdateDialogState.Downloading -> state.update
        is AppUpdateDialogState.AwaitingInstallPermission -> state.update
        is AppUpdateDialogState.ReadyToInstall -> state.update
        is AppUpdateDialogState.Failed -> state.update
        AppUpdateDialogState.Hidden -> return
    }
    val isDownloading = state is AppUpdateDialogState.Downloading
    val controlShape = RoundedCornerShape(OsuRadius.md)

    Dialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = !isDownloading
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(OsuRadius.xl),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = osuHairlineBorder(),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(OsuSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(OsuRadius.md))
                            .background(surfaces.accentSubtle),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(OsuSpacing.xxs)
                    ) {
                        Text(
                            text = when (state) {
                                is AppUpdateDialogState.Downloading -> "Downloading update"
                                is AppUpdateDialogState.AwaitingInstallPermission -> "Install permission required"
                                is AppUpdateDialogState.ReadyToInstall -> "Ready to install"
                                is AppUpdateDialogState.Failed -> "Update failed"
                                else -> "Update available"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
                        ) {
                            OsuMicroTag(
                                text = "v${BuildConfig.VERSION_NAME}",
                                isNumeric = true
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = surfaces.textMuted,
                                modifier = Modifier.size(12.dp)
                            )
                            OsuMicroTag(
                                text = "v${update.versionName}",
                                containerColor = surfaces.accentSubtle,
                                contentColor = MaterialTheme.colorScheme.primary,
                                isNumeric = true
                            )
                            if (update.apkSizeBytes > 0L) {
                                Text(
                                    text = formatBytesMb(update.apkSizeBytes),
                                    style = MaterialTheme.typography.labelSmall.tnum(),
                                    color = surfaces.textMuted,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                OsuHairlineDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(OsuSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(OsuSpacing.md)
                ) {
                    when (state) {
                        is AppUpdateDialogState.Available -> {
                            ReleaseNotesBox(notes = update.releaseNotes)
                        }

                        is AppUpdateDialogState.Downloading -> {
                            val hasTotal = state.totalBytes > 0L
                            val rawFraction = if (hasTotal) {
                                (state.bytesDownloaded.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            val animatedProgress by animateFloatAsState(
                                targetValue = rawFraction,
                                animationSpec = OsuMotion.floatSpec(),
                                label = "apkDownloadProgress"
                            )
                            val percentText = if (hasTotal) {
                                "${(rawFraction * 100f).toInt().coerceIn(0, 100)}%"
                            } else {
                                "Downloading…"
                            }
                            val sizeText = if (hasTotal) {
                                "${formatBytesMb(state.bytesDownloaded)} / ${formatBytesMb(state.totalBytes)}"
                            } else {
                                formatBytesMb(state.bytesDownloaded)
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(controlShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(OsuSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = sizeText,
                                        style = MaterialTheme.typography.labelMedium.tnum(),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = percentText,
                                        style = MaterialTheme.typography.labelMedium.tnum(),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (hasTotal) {
                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(OsuRadius.PillShape),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        strokeCap = StrokeCap.Round
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(OsuRadius.PillShape),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        strokeCap = StrokeCap.Round
                                    )
                                }
                            }
                        }

                        is AppUpdateDialogState.AwaitingInstallPermission -> {
                            OsuInlineBanner(
                                title = "Allow package installs",
                                text = "Enable \"Allow from this source\" for osu! Companion to install v${update.versionName}",
                                tone = OsuBannerTone.WARNING,
                                icon = Icons.Rounded.Security
                            )
                        }

                        is AppUpdateDialogState.ReadyToInstall -> {
                            OsuInlineBanner(
                                title = "APK downloaded",
                                text = "${update.apkFileName} is ready to install",
                                tone = OsuBannerTone.INFO,
                                icon = Icons.Rounded.CheckCircle
                            )
                        }

                        is AppUpdateDialogState.Failed -> {
                            OsuInlineBanner(
                                title = "Could not complete update",
                                text = state.message,
                                tone = OsuBannerTone.ERROR,
                                icon = Icons.Rounded.ErrorOutline
                            )
                        }

                        AppUpdateDialogState.Hidden -> Unit
                    }

                    when (state) {
                        is AppUpdateDialogState.Available -> {
                            Button(
                                onClick = { onStartDownload(update) },
                                shape = controlShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(OsuSpacing.sm))
                                Text("Download & Install")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { onSkipVersion(update.versionTag) },
                                    shape = controlShape,
                                    border = osuHairlineBorder(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Skip this version",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(
                                    onClick = onDismiss,
                                    shape = controlShape,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Later")
                                }
                            }
                        }

                        is AppUpdateDialogState.Downloading -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                            ) {
                                OutlinedButton(
                                    onClick = { ApkInstaller.openReleaseInBrowser(context, update.releaseUrl) },
                                    shape = controlShape,
                                    border = osuHairlineBorder(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(OsuSpacing.xs))
                                    Text(
                                        text = "Open in browser",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(
                                    onClick = onCancelDownload,
                                    shape = controlShape
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }

                        is AppUpdateDialogState.AwaitingInstallPermission -> {
                            Button(
                                onClick = {
                                    val intent = ApkInstaller.buildUnknownSourcesSettingsIntent(context)
                                    runCatching { unknownSourcesLauncher.launch(intent) }.onFailure {
                                        onInstallError(update, "Could not open install settings")
                                    }
                                },
                                shape = controlShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(OsuSpacing.sm))
                                Text("Allow & Install")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                            ) {
                                OutlinedButton(
                                    onClick = { ApkInstaller.openReleaseInBrowser(context, update.releaseUrl) },
                                    shape = controlShape,
                                    border = osuHairlineBorder(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(OsuSpacing.xs))
                                    Text(
                                        text = "Open in browser",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(
                                    onClick = onDismiss,
                                    shape = controlShape
                                ) {
                                    Text("Later")
                                }
                            }
                        }

                        is AppUpdateDialogState.ReadyToInstall -> {
                            Button(
                                onClick = { attemptInstall(update, state.apkFile) },
                                shape = controlShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SystemUpdate,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(OsuSpacing.sm))
                                Text("Install now")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = onDismiss,
                                    shape = controlShape
                                ) {
                                    Text("Later")
                                }
                            }
                        }

                        is AppUpdateDialogState.Failed -> {
                            Button(
                                onClick = { onStartDownload(update) },
                                shape = controlShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(OsuSpacing.sm))
                                Text("Retry download")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(OsuSpacing.sm)
                            ) {
                                OutlinedButton(
                                    onClick = { ApkInstaller.openReleaseInBrowser(context, update.releaseUrl) },
                                    shape = controlShape,
                                    border = osuHairlineBorder(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(OsuSpacing.xs))
                                    Text(
                                        text = "Open in browser",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(
                                    onClick = onDismiss,
                                    shape = controlShape
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }

                        AppUpdateDialogState.Hidden -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseNotesBox(notes: String) {
    val surfaces = LocalOsuSurfaces.current
    val cleanNotes = remember(notes) {
        notes.trim().ifBlank { "Bug fixes and stability improvements" }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 180.dp)
            .clip(RoundedCornerShape(OsuRadius.md))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .verticalScroll(rememberScrollState())
            .padding(OsuSpacing.md),
        verticalArrangement = Arrangement.spacedBy(OsuSpacing.xs)
    ) {
        Text(
            text = "Release notes",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = cleanNotes,
            style = MaterialTheme.typography.bodySmall,
            color = surfaces.textSecondary
        )
    }
}

private fun formatBytesMb(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    val mb = safe / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}
