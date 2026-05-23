package com.shifttac.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ShiftTacColorScheme = darkColorScheme(
    primary = XColor,
    secondary = OColor,
    background = Bg0,
    surface = Bg1,
    onBackground = TextColor,
    onSurface = TextColor,
    tertiary = WarnColor,
)

@Composable
fun ShiftTacTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShiftTacColorScheme,
        typography = ShiftTacTypography,
        content = content,
    )
}
