package org.cursiveerror.securenote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SoftSkyColorScheme = lightColorScheme(
    primary = ColorBrand,
    onPrimary = Color.White,
    primaryContainer = ColorBrandSoft,
    onPrimaryContainer = ColorBrandDark,
    surface = Color.White,
    background = ColorBackground,
    onSurface = ColorTextPrimary,
    onBackground = ColorTextPrimary,
    outline = ColorBorder
)

@Composable
fun SecureNoteTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SoftSkyColorScheme,
        typography = Typography,
        content = content
    )
}
