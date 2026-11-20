package vn.pvc.hub.sources

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import vn.pvc.hub.MainActivity
import vn.pvc.hub.R
import vn.pvc.hub.data.AppPrefs
import vn.pvc.hub.data.HubDatabase
import vn.pvc.hub.data.HubItem
import kotlin.math.abs

/**
 * Peek: một tay cầm nhỏ nổi ở mép màn hình. Chạm vào hiện bảng tin chưa đọc,
 * kéo lên xuống để đổi vị trí. Cần quyền "Hiển thị trên các ứng dụng khác".
 */
class PeekService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val wm by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private var handle: View? = null
    private var panel: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        showForeground()
        main.post {
            removeViews()
            addHandle()
        }
        return START_STICKY
    }

    private fun showForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Peek", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("PVC-Hub Peek đang bật")
            .setContentText("Chạm tay cầm ở mép màn hình để xem nhanh tin")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, FG_ID, n, type)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** Màu RGB kèm độ đậm theo phần trăm. */
    private fun withAlpha(rgb: Int, percent: Int): Int =
        ((percent.coerceIn(0, 100) * 255 / 100) shl 24) or (rgb and 0xFFFFFF)

    private fun edgeGravity(): Int =
        if (AppPrefs(this).peekSide == 1) Gravity.END else Gravity.START

    private fun addHandle() {
        val prefs = AppPrefs(this)
        val scale = prefs.peekSize / 100f
        val v = View(this)
        v.background = GradientDrawable().apply {
            setColor(withAlpha(0x00805F, prefs.peekAlpha))
            cornerRadius = dp(6).toFloat()
        }
        val lp = WindowManager.LayoutParams(
            dp((14 * scale).toInt().coerceAtLeast(6)), dp((110 * scale).toInt()),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = edgeGravity() or Gravity.CENTER_VERTICAL
        lp.y = prefs.peekY

        var downY = 0f
        var startOffset = 0
        var dragging = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downY = e.rawY
                    startOffset = lp.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - downY
                    if (abs(dy) > dp(8)) dragging = true
                    if (dragging) {
                        lp.y = startOffset + dy.toInt()
                        runCatching { wm.updateViewLayout(v, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) prefs.peekY = lp.y else togglePanel()
                    true
                }
                else -> false
            }
        }
        runCatching { wm.addView(v, lp) }
        handle = v
    }

    private fun togglePanel() {
        if (panel != null) hidePanel() else showPanel()
    }

    private fun showPanel() {
        scope.launch {
            val dao = HubDatabase.get(applicationContext).dao()
            val now = System.currentTimeMillis()
            val unread = runCatching { dao.unreadTotal(now) }.getOrDefault(0)
            val items = runCatching { dao.latest(now, 6) }.getOrDefault(emptyList())
            main.post { buildPanel(unread, items) }
        }
    }

    private fun buildPanel(unread: Int, items: List<HubItem>) {
        if (panel != null) return
        val prefs = AppPrefs(this)
        val scale = prefs.peekPanelSize / 100f
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(14), dp(16), dp(10))
        root.background = GradientDrawable().apply {
            setColor(0xFFFFFFFF.toInt()) // khung đọc tin luôn đặc để dễ đọc, độ trong suốt chỉ áp cho nút
            cornerRadius = dp(16).toFloat()
        }

        fun text(s: String, size: Float, color: Int, bold: Boolean = false): TextView {
            val t = TextView(this)
            t.text = s
            t.textSize = size * scale
            t.setTextColor(color)
            if (bold) t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
            t.maxLines = 1
            t.ellipsize = TextUtils.TruncateAt.END
            t.setPadding(0, dp(3), 0, dp(3))
            return t
        }

        root.addView(
            text(
                if (unread > 0) "PVC-Hub · $unread chưa đọc" else "PVC-Hub · không có tin chưa đọc",
                15f, 0xFF00805F.toInt(), true
            )
        )
        if (items.isEmpty() && !AppPrefs(this).appLock) {
            root.addView(text("Hộp thư trống", 13f, 0xFF212121.toInt()))
        }
        if (AppPrefs(this).appLock) {
            root.addView(text("Nội dung được ẩn vì đang bật khóa ứng dụng", 13f, 0xFF212121.toInt()))
        }
        (if (AppPrefs(this).appLock) emptyList() else items).forEach {
            val dot = if (it.read) "" else "● "
            val row = text("$dot${it.sender}: ${it.preview}", 13f, 0xFF212121.toInt())
            row.setOnClickListener { openHub() }
            root.addView(row)
        }
        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.gravity = Gravity.END
        val open = text("MỞ PVC-HUB", 13f, 0xFF00805F.toInt(), true)
        open.setPadding(dp(10), dp(10), dp(10), dp(6))
        open.setOnClickListener { openHub() }
        val close = text("ĐÓNG", 13f, 0xFF616161.toInt(), true)
        close.setPadding(dp(10), dp(10), dp(0), dp(6))
        close.setOnClickListener { hidePanel() }
        buttons.addView(open)
        buttons.addView(close)
        root.addView(buttons)

        root.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) hidePanel()
            false
        }
        val lp = WindowManager.LayoutParams(
            minOf(dp((300 * scale).toInt()), resources.displayMetrics.widthPixels - dp(40)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = edgeGravity() or Gravity.CENTER_VERTICAL
        lp.x = dp(20)
        runCatching { wm.addView(root, lp) }
        panel = root
    }

    private fun hidePanel() {
        panel?.let { p -> runCatching { wm.removeView(p) } }
        panel = null
    }

    private fun openHub() {
        hidePanel()
        runCatching {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun removeViews() {
        hidePanel()
        handle?.let { h -> runCatching { wm.removeView(h) } }
        handle = null
    }

    override fun onDestroy() {
        main.post { removeViews() }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "pvc_peek"
        private const val FG_ID = 1002

        /** Bật hoặc tắt Peek cho khớp với cài đặt. */
        fun apply(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, PeekService::class.java)
            try {
                val want = AppPrefs(app).peekEnabled && Settings.canDrawOverlays(app)
                if (want) ContextCompat.startForegroundService(app, intent) else app.stopService(intent)
            } catch (e: Exception) {
                // Android không cho khởi động khi app đang ở nền: sẽ thử lại khi mở app.
            }
        }
    }
}
