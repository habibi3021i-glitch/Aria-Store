package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SleekGreenDarkPrimary,
    secondary = SleekGreenDarkSecondary,
    tertiary = RatingGold,
    background = SleekGreenDarkBackground,
    surface = SleekGreenDarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color(0xFFE2E3E0),
    onSurface = Color(0xFFE2E3E0),
    surfaceVariant = SleekGreenDarkSurfaceVariant,
    outline = SleekGreenDarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = SleekGreenPrimary,
    secondary = SleekGreenSecondary,
    tertiary = RatingGold,
    background = SleekGreenBackground,
    surface = SleekGreenSurface,
    onPrimary = Color.White,
    onSecondary = SleekGreenOnSecondary,
    onBackground = SleekGreenTextPrimary,
    onSurface = SleekGreenTextPrimary,
    surfaceVariant = SleekGreenSurfaceVariant,
    outline = SleekGreenOutline
)

@Composable
fun MyApplicationTheme(
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
