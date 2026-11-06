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
import javax.swing.SwingUtilities

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
            onSync = { incomingNotes ->
                val noteMap = LinkedHashMap<String, Note>()
                synchronized(notes) {
                    for (n in notes) {
                        noteMap[n.id] = n
                    }
                }

                for (inc in incomingNotes) {
                    val existing = noteMap[inc.id]
                    if (existing == null) {
                        noteMap[inc.id] = inc
                    } else {
                        if (inc.timestamp > existing.timestamp) {
                            noteMap[inc.id] = inc
                        }
                    }
                }

                val merged = noteMap.values.sortedByDescending { it.timestamp }
                NoteRepository.saveNotes(merged)

                SwingUtilities.invokeLater {
                    notes.clear()
                    notes.addAll(merged)
                }

                merged
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
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    isDeleted = false
                )
                notes.add(0, newNote)
                NoteRepository.saveNotes(notes.toList())
            },
            onEditNote = { id, title, content ->
                val index = notes.indexOfFirst { it.id == id }
                if (index >= 0) {
                    val updated = notes[index].copy(
                        title = title,
                        content = content,
                        timestamp = System.currentTimeMillis()
                    )
                    notes[index] = updated
                    NoteRepository.saveNotes(notes.toList())
                }
            },
            onDeleteNote = { id ->
                val index = notes.indexOfFirst { it.id == id }
                if (index >= 0) {
                    val updated = notes[index].copy(
                        isDeleted = true,
                        timestamp = System.currentTimeMillis()
                    )
                    notes[index] = updated
                    NoteRepository.saveNotes(notes.toList())
                }
            }
        )
    }
}
