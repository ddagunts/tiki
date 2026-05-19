package com.tkey.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TKeyColors = darkColorScheme(
    primary = Accent,
    onPrimary = AccentInk,
    primaryContainer = AccentDim,
    onPrimaryContainer = Accent,

    secondary = Info,
    onSecondary = Ink,
    secondaryContainer = Hairline,
    onSecondaryContainer = TextPrimary,

    tertiary = Warning,
    onTertiary = Ink,

    background = Ink,
    onBackground = TextPrimary,

    surface = Graphite,
    onSurface = TextPrimary,
    surfaceVariant = GraphiteHi,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Graphite,
    surfaceContainerHigh = GraphiteHi,
    surfaceContainerHighest = GraphiteHi,
    surfaceContainerLow = Ink,
    surfaceContainerLowest = Ink,

    outline = Hairline,
    outlineVariant = HairlineHi,

    error = Danger,
    onError = Ink,
    errorContainer = Color(0x33F87171),
    onErrorContainer = Danger,
)

private val TKeyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val Sans = FontFamily.SansSerif
private val Mono = FontFamily.Monospace

private val TKeyTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Light, fontSize = 44.sp, letterSpacing = (-1.0).sp),
    displayMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Light, fontSize = 32.sp, letterSpacing = (-0.6).sp),
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.2.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 11.sp, letterSpacing = 0.5.sp),
)

@Composable
fun TKeyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TKeyColors,
        shapes = TKeyShapes,
        typography = TKeyTypography,
        content = content,
    )
}
