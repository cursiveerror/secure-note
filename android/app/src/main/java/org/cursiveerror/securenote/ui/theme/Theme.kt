package org.cursiveerror.securenote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ArchitecturalColorScheme = lightColorScheme(
    primary = ColorAccent,
    onPrimary = Color.White,
    primaryContainer = ColorActiveFill,
    onPrimaryContainer = ColorAccent,
    surface = Color.White,
    background = Color(0xFFF7F9FB),
    onSurface = ColorTextPrimary,
    onBackground = ColorTextPrimary,
    outline = ColorBorder
)

@Composable
fun SecureNoteTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ArchitecturalColorScheme,
        typography = Typography,
        content = content
    )
}
