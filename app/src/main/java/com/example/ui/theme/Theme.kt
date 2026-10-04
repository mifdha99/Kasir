package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val LightColorScheme = lightColorScheme(
    primary = KasirTealPrimary,
    onPrimary = KasirTealOnPrimary,
    primaryContainer = KasirTealContainer,
    onPrimaryContainer = KasirTealOnContainer,
    secondary = KasirAmberSecondary,
    onSecondary = KasirAmberOnSecondary,
    secondaryContainer = KasirAmberContainer,
    onSecondaryContainer = KasirAmberOnContainer,
    tertiary = KasirBlueTertiary,
    tertiaryContainer = KasirBlueContainer,
    background = KasirLightBackground,
    surface = KasirLightSurface,
    surfaceVariant = KasirLightSurfaceVariant,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569)
)

private val DarkColorScheme = darkColorScheme(
    primary = KasirDarkPrimary,
    onPrimary = KasirDarkOnPrimary,
    primaryContainer = KasirDarkContainer,
    onPrimaryContainer = KasirDarkOnContainer,
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF451A03),
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = Color(0xFFFEF3C7),
    background = KasirDarkBackground,
    surface = KasirDarkSurface,
    surfaceVariant = KasirDarkSurfaceVariant,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFFCBD5E1)
)

@Composable
fun KasirKuTheme(
    themeMode: String = "LIGHT",
    textScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode.uppercase()) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val currentDensity = LocalDensity.current
    val scaledDensity = Density(
        density = currentDensity.density,
        fontScale = (currentDensity.fontScale * textScale.coerceIn(0.85f, 1.3f))
    )

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
