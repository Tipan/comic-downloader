package com.lanyeeee.jmcomic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = JmOrange,
    onPrimary = Color.White,
    primaryContainer = JmOrangeLight,
    onPrimaryContainer = JmOrangeDark,
    secondary = JmOrangeDark,
    surface = Color.White,
    background = Color(0xFFF7F7F7),
)

private val DarkColors = darkColorScheme(
    primary = JmOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D2A14),
    onPrimaryContainer = JmOrangeLight,
    secondary = JmOrangeDark,
)

@Composable
fun JmComicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
