package vn.pvc.hub.ui

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Khóa ứng dụng bằng vân tay/khuôn mặt (và khóa màn hình từ Android 11). */
object AppLock {
    private fun authenticators(): Int =
        if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    /** Máy đã cài vân tay/khuôn mặt hoặc khóa màn hình để dùng được không. */
    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(
        activity: FragmentActivity,
        title: String,
        onOk: () -> Unit,
        onFail: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val bp = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onOk()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFail(errString.toString())
            }
        })
        val b = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(authenticators())
        if (Build.VERSION.SDK_INT < 30) b.setNegativeButtonText("Hủy")
        bp.authenticate(b.build())
    }
}

@Composable
fun LockScreen(activity: FragmentActivity, onUnlocked: () -> Unit) {
    var msg by remember { mutableStateOf("") }
    val unlock = {
        // Máy không còn khóa/vân tay nào thì không nhốt người dùng ở ngoài.
        if (!AppLock.available(activity)) {
            onUnlocked()
        } else {
            AppLock.prompt(activity, "Mở PVC-Hub", onUnlocked) { msg = it }
        }
    }
    LaunchedEffect(Unit) { unlock() }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = BrandGreenDark)
        Spacer(Modifier.height(12.dp))
        Text("PVC-Hub đang khóa", style = MaterialTheme.typography.titleLarge)
        if (msg.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(msg, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { unlock() },
            colors = ButtonDefaults.buttonColors(containerColor = BrandGreenDark, contentColor = Color.White)
        ) { Text("Mở khóa") }
    }
}
