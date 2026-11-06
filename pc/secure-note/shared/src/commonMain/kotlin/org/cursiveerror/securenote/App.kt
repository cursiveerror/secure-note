package org.cursiveerror.securenote

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import secure_note.shared.generated.resources.Res
import secure_note.shared.generated.resources.app_icon

@Composable
fun getAppIcon(): Painter {
    return painterResource(Res.drawable.app_icon)
}
