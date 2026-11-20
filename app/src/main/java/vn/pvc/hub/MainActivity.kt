package vn.pvc.hub

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import vn.pvc.hub.data.AppPrefs
import vn.pvc.hub.ui.HubApp
import vn.pvc.hub.ui.LockScreen
import vn.pvc.hub.ui.PvcHubTheme

class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(false)
    private var leftAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locked = AppPrefs(this).appLock
        vn.pvc.hub.sources.NotificationCaptureService.rebind(this)
        setContent {
            PvcHubTheme {
                if (locked) {
                    LockScreen(activity = this, onUnlocked = { locked = false })
                } else {
                    HubApp()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Khi bật khóa: ẩn nội dung trong ảnh chụp màn hình và danh sách ứng dụng gần đây.
        if (AppPrefs(this).appLock) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onStop() {
        super.onStop()
        leftAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // Rời app quá 30 giây thì khóa lại.
        if (leftAt > 0 && AppPrefs(this).appLock && System.currentTimeMillis() - leftAt > 30_000L) {
            locked = true
        }
    }
}
