package vn.pvc.hub.sources

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vn.pvc.hub.MainActivity
import vn.pvc.hub.data.HubDatabase

/** Ô PVC-Hub trong bảng Cài đặt nhanh: hiện số tin chưa đọc, chạm để mở app. */
class HubTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        CoroutineScope(Dispatchers.IO).launch {
            val unread = runCatching {
                HubDatabase.get(applicationContext).dao().unreadTotal(System.currentTimeMillis())
            }.getOrDefault(0)
            Handler(Looper.getMainLooper()).post {
                val tile = qsTile ?: return@post
                tile.label = "PVC-Hub"
                tile.state = if (unread > 0) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= 29) {
                    tile.subtitle = if (unread > 0) "$unread chưa đọc" else "Không có tin mới"
                }
                tile.updateTile()
            }
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
