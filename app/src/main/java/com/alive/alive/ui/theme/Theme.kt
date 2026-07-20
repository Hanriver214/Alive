package com.alive.alive.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = AliveGreen,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = AliveAccent,
    secondary = AliveAccent,
    background = AliveSurface,
    surface = androidx.compose.ui.graphics.Color.White
)

private val DarkColors = darkColorScheme(
    primary = AliveAccent,
    onPrimary = AliveGreenDark,
    primaryContainer = AliveGreenDark,
    secondary = AliveGreen,
    background = AliveSurfaceDark,
    surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E)
)

@Composable
fun AliveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AliveTypography,
        content = content
    )
}
