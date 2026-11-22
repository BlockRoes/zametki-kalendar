package ru.blok.calnotes

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF2E7D5B), onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F1D2), onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4D6357), tertiary = Color(0xFFB8860B),
    error = Color(0xFFBA1A1A),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF98D5B6), onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005237), onPrimaryContainer = Color(0xFFB4F1D2),
    tertiary = Color(0xFFFFC94A), error = Color(0xFFFFB4AB),
)

@Composable
fun CalNotesTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
