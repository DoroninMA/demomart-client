package com.demomart.client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = DeepGreen,
    onPrimary = Color.White,
    primaryContainer = GreenContainer,
    onPrimaryContainer = OnGreenContainer,
    secondary = Coffee,
    background = Cream,
    onBackground = Ink,
    surface = CardSurface,
    onSurface = Ink,
    error = ErrorRed,
)

@Composable
fun DemoMartTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = DemoMartTypography, content = content)
}
