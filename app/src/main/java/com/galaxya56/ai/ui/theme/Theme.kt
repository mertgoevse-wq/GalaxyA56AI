package com.galaxya56.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * "Field Lab" identity.
 *
 * The A56 is a measurement instrument as much as a creative toy: its real
 * limits (RAM, thermals, NPU reach) are part of the interface. The palette is
 * therefore a darkroom green-graphite with one signal-colour accent, not a
 * generic purple AI gradient. Serif headlines carry the editorial/print side;
 * monospace metadata carries the instrument side.
 */
val Ink = Color(0xFF0D100E)
val Surface1 = Color(0xFF151916)
val Surface2 = Color(0xFF1C211D)
val Surface3 = Color(0xFF262D27)
val Line = Color(0xFF354038)
val TextMain = Color(0xFFF0EEE4)
val TextMuted = Color(0xFFA2ADA4)
val Accent = Color(0xFFD8F26B)
val AccentSoft = Color(0xFF2E3A1D)
val AccentText = Color(0xFFF2FFC4)
val Danger = Color(0xFFFF8A7A)
val Signal = Color(0xFFE6A56F)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A2108),
    primaryContainer = AccentSoft,
    onPrimaryContainer = AccentText,
    secondary = Color(0xFFA9C7B8),
    onSecondary = Color(0xFF0C1712),
    secondaryContainer = Color(0xFF20312A),
    onSecondaryContainer = Color(0xFFD3EADF),
    tertiary = Signal,
    background = Ink,
    onBackground = TextMain,
    surface = Surface1,
    onSurface = TextMain,
    surfaceVariant = Surface3,
    onSurfaceVariant = TextMuted,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    outline = Line,
    outlineVariant = Line,
    error = Danger,
    onError = Color(0xFF2B0A06),
    inverseSurface = TextMain,
    inverseOnSurface = Ink
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.5.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp
    )
)

@Composable
fun A56Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = AppTypography,
        content = content
    )
}
