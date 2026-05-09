package io.tl.haptic.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val SensualColorScheme = darkColorScheme(
    primary = SensualPink,
    onPrimary = Color(0xFF56003A),
    primaryContainer = Color(0xFF7A0054),
    onPrimaryContainer = Color(0xFFFFD9E8),
    secondary = Color(0xFFCC7BA6),
    onSecondary = Color(0xFF341A2B),
    secondaryContainer = Color(0xFF4D3041),
    onSecondaryContainer = Color(0xFFFAD8F0),
    tertiary = Color(0xFFC883B0),
    onTertiary = Color(0xFF321D2D),
    background = SensualDarkBg,
    onBackground = Color(0xFFEEDBDF),
    surface = SensualDarkBg,
    onSurface = Color(0xFFEEDBDF),
    surfaceVariant = Color(0xFF2D1520),
    onSurfaceVariant = Color(0xFFD0BCC4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF998288),
    outlineVariant = Color(0xFF4E3A42)
)

@Composable
fun HapticGeneratorTheme(
    isSensualMode: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isSensualMode -> SensualColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)
        else -> lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
