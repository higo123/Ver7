package vn.pvc.hub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.pvc.hub.data.Source

/** Màu nhận diện cho từng nguồn liên lạc. */
object SourceStyle {
    fun color(source: String): Color = when (source) {
        Source.EMAIL.name -> Color(0xFFD93025)
        Source.SMS.name -> Color(0xFF00A479)
        Source.CALL.name -> Color(0xFFEF6C00)
        Source.ZALO.name -> Color(0xFF0068FF)
        Source.TELEGRAM.name -> Color(0xFF229ED9)
        Source.MESSENGER.name -> Color(0xFF8E44AD)
        Source.WHATSAPP.name -> Color(0xFF25D366)
        Source.VIBER.name -> Color(0xFF7360F2)
        Source.SIGNAL.name -> Color(0xFF3A76F0)
        Source.LINE.name -> Color(0xFF06C755)
        Source.FACEBOOK.name -> Color(0xFF1877F2)
        Source.INSTAGRAM.name -> Color(0xFFE1306C)
        Source.THREADS.name -> Color(0xFF444444)
        Source.TIKTOK.name -> Color(0xFF2B2B2B)
        Source.X.name -> Color(0xFF333333)
        Source.LINKEDIN.name -> Color(0xFF0A66C2)
        else -> Color(0xFF607D8B)
    }
}

/** Ảnh đại diện tròn lấy chữ cái đầu của người gửi. */
@Composable
fun Avatar(name: String, color: Color) {
    val initial = name.trim().firstOrNull { it.isLetterOrDigit() }
        ?.uppercaseChar()?.toString() ?: "#"
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun SourceBadge(label: String, color: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun DayHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
    )
}

/** Thẻ nền trắng (tối ở chế độ tối) có viền mảnh. */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkTheme()) Color(0xFF1B211F) else Color.White
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content
    )
}
