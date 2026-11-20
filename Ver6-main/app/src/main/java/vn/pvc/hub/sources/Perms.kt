package vn.pvc.hub.sources

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import vn.pvc.hub.data.AccountStore

/** Trạng thái 5 bước thiết lập cần thiết để PVC-Hub chạy đủ chức năng. */
data class SetupState(
    val gmail: Boolean,
    val sms: Boolean,
    val calls: Boolean,
    val notif: Boolean,
    val battery: Boolean
) {
    val total: Int get() = 5
    val doneCount: Int get() = listOf(gmail, sms, calls, notif, battery).count { it }
    val allDone: Boolean get() = doneCount == total
}

object Perms {
    fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun notificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun batteryUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun readSetup(context: Context, accounts: AccountStore): SetupState = SetupState(
        gmail = accounts.accounts().isNotEmpty(),
        sms = has(context, Manifest.permission.READ_SMS),
        calls = has(context, Manifest.permission.READ_CALL_LOG),
        notif = notificationAccess(context),
        battery = batteryUnrestricted(context)
    )
}
