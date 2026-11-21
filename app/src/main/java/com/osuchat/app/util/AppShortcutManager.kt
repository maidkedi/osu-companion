package com.osuchat.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.osuchat.app.MainActivity
import com.osuchat.app.R
import com.osuchat.app.domain.model.ChatChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppShortcutManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun syncDynamicShortcuts(isAuthenticated: Boolean, channels: List<ChatChannel>) {
        if (!isAuthenticated) {
            clearDynamicShortcuts()
            return
        }

        val maxAllowed = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
            .coerceIn(0, 4)
        if (maxAllowed <= 0) return

        val topChannels = channels
            .filter { it.isJoined && it.id.isNotBlank() }
            .sortedWith(
                compareByDescending<ChatChannel> { it.isPinned }
                    .thenByDescending { it.lastMessageTimestamp }
                    .thenBy { it.displayTitle.lowercase() }
            )
            .distinctBy { it.id.lowercase() }
            .take(2)

        val shortcuts = ArrayList<ShortcutInfoCompat>(maxAllowed)
        topChannels.forEachIndexed { index, ch ->
            if (shortcuts.size >= maxAllowed) return@forEachIndexed
            val title = ch.displayTitle.ifBlank { ch.name }.ifBlank { ch.id }
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(MainActivity.EXTRA_OPEN_CHANNEL_ID, ch.id)
                putExtra(EXTRA_CHANNEL_ID, ch.id)
            }
            shortcuts += ShortcutInfoCompat.Builder(context, "chat_${ch.id}")
                .setShortLabel(title.take(20))
                .setLongLabel(title.take(40))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_chat))
                .setRank(index)
                .setIntent(intent)
                .build()
        }

        if (shortcuts.size < maxAllowed) {
            val searchIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_SHORTCUT_ACTION, ACTION_BEATMAP_SEARCH)
            }
            shortcuts += ShortcutInfoCompat.Builder(context, SHORTCUT_ID_BEATMAP_SEARCH)
                .setShortLabel("Beatmap Search")
                .setLongLabel("Search osu! Beatmaps")
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_search))
                .setRank(shortcuts.size)
                .setIntent(searchIntent)
                .build()
        }

        if (shortcuts.size < maxAllowed) {
            val mpIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_SHORTCUT_ACTION, ACTION_MP_SETUP)
            }
            shortcuts += ShortcutInfoCompat.Builder(context, SHORTCUT_ID_MP_SETUP)
                .setShortLabel("Quick #mp_ Setup")
                .setLongLabel("Create Tournament Lobby")
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_mp))
                .setRank(shortcuts.size)
                .setIntent(mpIntent)
                .build()
        }

        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }

    fun clearDynamicShortcuts() {
        runCatching {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        }
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_SHORTCUT_ACTION = "extra_shortcut_action"
        const val ACTION_BEATMAP_SEARCH = "beatmap_search"
        const val ACTION_MP_SETUP = "mp_setup"
        private const val SHORTCUT_ID_BEATMAP_SEARCH = "action_beatmap_search"
        private const val SHORTCUT_ID_MP_SETUP = "action_mp_setup"
    }
}
