package vn.pvc.hub.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandGreen = Color(0xFF00A479)
val BrandGreenDark = Color(0xFF00805F)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00805F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC6F2E3),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4A635A),
    background = Color(0xFFF2F8F5),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFF2F8F5),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDBE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    outlineVariant = Color(0xFFC4D0CA)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5BE0B8),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF005140),
    onPrimaryContainer = Color(0xFFC6F2E3),
    secondary = Color(0xFFB2CCC1),
    background = Color(0xFF101413),
    onBackground = Color(0xFFE0E3E1),
    surface = Color(0xFF101413),
    onSurface = Color(0xFFE0E3E1),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    outlineVariant = Color(0xFF2E3532)
)

@Composable
fun PvcHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
