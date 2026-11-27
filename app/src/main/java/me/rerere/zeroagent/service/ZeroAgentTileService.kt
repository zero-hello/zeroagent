package me.rerere.zeroagent.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import me.rerere.zeroagent.ui.pages.quickchat.QuickChatActivity

class ZeroAgentTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun(::openQuickChat) else openQuickChat()
    }

    private fun openQuickChat() {
        val intent = QuickChatActivity.quickAskIntent(this)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else {
            openQuickChatLegacy(intent)
        }
    }
    // Android 12 and 13 require the Intent overload.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openQuickChatLegacy(intent: Intent) = startActivityAndCollapse(intent)
}
