package org.cursiveerror.securenote

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import java.util.UUID

fun main() = application {
    val notes = remember {
        mutableStateListOf<Note>().apply {
            addAll(NoteRepository.loadNotes())
        }
    }

    val serverIp = remember { SyncServer.getLocalIpAddress() }

    DisposableEffect(Unit) {
        val server = SyncServer(
            httpPort = 8000,
            udpPort = 8888,
            onNoteReceived = { newNote ->
                val existingIndex = notes.indexOfFirst {
                    it.id == newNote.id || (it.title == newNote.title && it.content == newNote.content)
                }
                if (existingIndex >= 0) {
                    notes[existingIndex] = newNote
                } else {
                    notes.add(0, newNote)
                }
                NoteRepository.saveNotes(notes.toList())
            },
            getCurrentNotes = { notes.toList() }
        )
        server.start()

        onDispose {
            server.stop()
        }
    }

    var isVisible by remember { mutableStateOf(true) }
    val windowState = rememberWindowState(width = 960.dp, height = 720.dp)
    val trayState = rememberTrayState()
    val appIcon = getAppIcon()

    Tray(
        state = trayState,
        icon = appIcon,
        tooltip = "SecureNote Desktop",
        onAction = { isVisible = true },
        menu = {
            Item("Open SecureNote", onClick = { isVisible = true })
            Separator()
            Item("Exit", onClick = ::exitApplication)
        }
    )

    Window(
        onCloseRequest = { isVisible = false },
        visible = isVisible,
        title = "SecureNote Desktop",
        state = windowState,
        icon = appIcon
    ) {
        DesktopApp(
            serverIp = serverIp,
            notes = notes,
            onAddNote = { title, content ->
                val newNote = Note(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    content = content
                )
                notes.add(0, newNote)
                NoteRepository.saveNotes(notes.toList())
            },
            onEditNote = { id, title, content ->
                val index = notes.indexOfFirst { it.id == id }
                if (index >= 0) {
                    val updated = notes[index].copy(title = title, content = content)
                    notes[index] = updated
                    NoteRepository.saveNotes(notes.toList())
                }
            },
            onDeleteNote = { id ->
                notes.removeAll { it.id == id }
                NoteRepository.saveNotes(notes.toList())
            }
        )
    }
}
