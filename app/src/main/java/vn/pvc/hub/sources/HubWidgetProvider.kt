package vn.pvc.hub.sources

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vn.pvc.hub.MainActivity
import vn.pvc.hub.R
import vn.pvc.hub.data.AppPrefs
import vn.pvc.hub.data.HubDatabase

/** Widget màn hình chính: số tin chưa đọc và 5 tin mới nhất. Chạm để mở PVC-Hub. */
class HubWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                render(context.applicationContext, manager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val ROWS = intArrayOf(R.id.w_row1, R.id.w_row2, R.id.w_row3, R.id.w_row4, R.id.w_row5)

        /** Cập nhật mọi widget đang đặt trên màn hình (gọi khi có tin mới hoặc đổi trạng thái đọc). */
        fun refresh(context: Context) {
            val app = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val m = AppWidgetManager.getInstance(app)
                    val ids = m.getAppWidgetIds(ComponentName(app, HubWidgetProvider::class.java))
                    if (ids.isNotEmpty()) render(app, m, ids)
                }
            }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val dao = HubDatabase.get(context).dao()
            val now = System.currentTimeMillis()
            val unread = dao.unreadTotal(now)
            val items = dao.latest(now, ROWS.size)

            val views = RemoteViews(context.packageName, R.layout.hub_widget)
            views.setTextViewText(
                R.id.w_title,
                if (unread > 0) "PVC-Hub  ·  $unread chưa đọc" else "PVC-Hub  ·  không có tin chưa đọc"
            )
            val locked = AppPrefs(context).appLock
            ROWS.forEachIndexed { i, rowId ->
                val item = items.getOrNull(i)
                if (locked) {
                    if (i == 0) {
                        views.setTextViewText(rowId, "Nội dung được ẩn vì đang bật khóa ứng dụng")
                        views.setViewVisibility(rowId, View.VISIBLE)
                    } else {
                        views.setViewVisibility(rowId, View.GONE)
                    }
                } else if (item != null) {
                    val dot = if (item.read) "" else "● "
                    views.setTextViewText(rowId, "$dot${item.sender}: ${item.preview}")
                    views.setViewVisibility(rowId, View.VISIBLE)
                } else if (i == 0) {
                    views.setTextViewText(rowId, "Hộp thư trống")
                    views.setViewVisibility(rowId, View.VISIBLE)
                } else {
                    views.setViewVisibility(rowId, View.GONE)
                }
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.w_root, open)
            manager.updateAppWidget(ids, views)
        }
    }
}
