package com.spacemuse.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Sage green + warm clay — see docs/product/user-flows.md for the intended
// "calm, editorial" tone of the design experience itself.
private val LightColors = lightColorScheme(
    primary = Color(0xFF3F6B4A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC3E4C9),
    onPrimaryContainer = Color(0xFF082111),
    secondary = Color(0xFFB08968),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3DEC9),
    onSecondaryContainer = Color(0xFF3A2412),
    background = Color(0xFFFAF9F5),
    onBackground = Color(0xFF1B1C18),
    surface = Color(0xFFFAF9F5),
    onSurface = Color(0xFF1B1C18),
    surfaceVariant = Color(0xFFE3E3DA),
    onSurfaceVariant = Color(0xFF46483F),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BC4A6),
    onPrimary = Color(0xFF0B3919),
    primaryContainer = Color(0xFF23512E),
    onPrimaryContainer = Color(0xFFC3E4C9),
    secondary = Color(0xFFD9B99B),
    onSecondary = Color(0xFF422C16),
    secondaryContainer = Color(0xFF5B4128),
    onSecondaryContainer = Color(0xFFF3DEC9),
    background = Color(0xFF12130F),
    onBackground = Color(0xFFE3E3DA),
    surface = Color(0xFF12130F),
    onSurface = Color(0xFFE3E3DA),
    surfaceVariant = Color(0xFF46483F),
    onSurfaceVariant = Color(0xFFC6C7BB),
    error = Color(0xFFFFB4AB),
)

private val SpaceMuseTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
    )
}

@Composable
fun SpaceMuseTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = SpaceMuseTypography, content = content)
}
