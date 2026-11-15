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

val Ink = Color(0xFF0B0B0C)
val Surface1 = Color(0xFF131315)
val Surface2 = Color(0xFF1A1A1E)
val Surface3 = Color(0xFF232329)
val Line = Color(0xFF2C2C33)
val TextMain = Color(0xFFECECEF)
val TextMuted = Color(0xFF9C9CA6)
val Accent = Color(0xFFE08A5B)
val AccentSoft = Color(0xFF3B241A)
val AccentText = Color(0xFFFFDCC8)
val Danger = Color(0xFFE57373)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1B0F08),
    primaryContainer = AccentSoft,
    onPrimaryContainer = AccentText,
    secondary = Color(0xFF8FB8A8),
    onSecondary = Color(0xFF0B1512),
    secondaryContainer = Color(0xFF1D2B26),
    onSecondaryContainer = Color(0xFFCFE9DE),
    tertiary = Color(0xFF9E9AD6),
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
    onError = Color(0xFF2A0A0A),
    inverseSurface = TextMain,
    inverseOnSurface = Ink
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
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
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp
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
