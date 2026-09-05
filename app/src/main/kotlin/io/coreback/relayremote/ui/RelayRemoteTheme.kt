package io.coreback.relayremote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val RelayBlack = Color(0xFF000000)
val RelayCarbon = Color(0xFF111318)
val RelayCarbonRaised = Color(0xFF191C22)
val RelayCyan = Color(0xFF78D7FF)
val RelayViolet = Color(0xFF9387FF)
val RelayMoon = Color(0xFFF4F7FA)
val RelayFog = Color(0xFF8B929C)
val RelayDanger = Color(0xFFFF7C8A)

private val RelayColors = darkColorScheme(
    primary = RelayCyan,
    onPrimary = Color(0xFF002733),
    secondary = RelayViolet,
    onSecondary = Color(0xFF17102E),
    background = RelayBlack,
    onBackground = RelayMoon,
    surface = RelayCarbon,
    onSurface = RelayMoon,
    surfaceVariant = RelayCarbonRaised,
    onSurfaceVariant = RelayFog,
    error = RelayDanger,
)

private val RelayTypography = androidx.compose.material3.Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.15.sp,
    ),
)

@Composable
fun RelayRemoteTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = RelayColors,
        typography = RelayTypography,
        content = content,
    )
}
