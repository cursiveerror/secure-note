package org.cursiveerror.securenote

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "secure-note",
    ) {
        App()
    }
}