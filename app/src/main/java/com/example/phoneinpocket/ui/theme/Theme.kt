package com.example.phoneinpocket.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PocketDark = darkColorScheme(
    primary = AmberGlow,
    onPrimary = Ink900,
    secondary = AquaMist,
    onSecondary = Ink900,
    tertiary = AlertCoral,
    background = Ink900,
    onBackground = PaperWhite,
    surface = Ink800,
    onSurface = PaperWhite,
    surfaceVariant = Ink700,
    onSurfaceVariant = SlateMuted,
    outline = Ink700,
)

@Composable
fun PhoneInPocketTheme(content: @Composable () -> Unit) {

    MaterialTheme(
        colorScheme = PocketDark,
        typography = Typography,
        content = content,
    )
}