package vn.pvc.hub.sources

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Sau khi khởi động lại máy, bật lại lịch đồng bộ và dịch vụ nhận email. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            SyncRunner.schedule(context)
            NotificationCaptureService.rebind(context)
        }
    }
}
