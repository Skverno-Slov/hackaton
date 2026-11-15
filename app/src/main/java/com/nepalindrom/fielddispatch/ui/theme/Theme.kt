package com.nepalindrom.fielddispatch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = RostelecomPurpleLight,
    onPrimary = Color.White,
    primaryContainer = RostelecomPurpleContainerDark,
    onPrimaryContainer = RostelecomPurpleContainer,
    secondary = RostelecomOrange,
    onSecondary = Color.White,
    secondaryContainer = RostelecomOrangeLight,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    onBackground = Color(0xFFF1F1F5),
    onSurface = Color(0xFFF1F1F5)
)

private val LightColorScheme = lightColorScheme(
    primary = RostelecomPurple,
    onPrimary = Color.White,
    primaryContainer = RostelecomPurpleContainer,
    onPrimaryContainer = RostelecomPurpleContainerDark,
    secondary = RostelecomOrange,
    onSecondary = Color.White,
    secondaryContainer = RostelecomOrangeLight,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightCard,
    onBackground = Color(0xFF1C1D2D),
    onSurface = Color(0xFF1C1D2D)
)

@Composable
fun FieldDispatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
