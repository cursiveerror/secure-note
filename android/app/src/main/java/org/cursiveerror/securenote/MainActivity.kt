package org.cursiveerror.securenote

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.cursiveerror.securenote.ui.theme.SecureNoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.UUID

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val isSynced: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SecureNoteTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NoteScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun NoteScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var notes by remember { mutableStateOf(loadLocalNotes(context)) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }

    // Стан вибраної нотатки для перегляду та редагування
    var selectedNote by remember { mutableStateOf<Note?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Верхня панель: Заголовок + окрема кнопка Синхронізації з ПК
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SecureNote",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            FilledTonalButton(
                onClick = {
                    if (isSyncing) return@FilledTonalButton

                    val unsyncedCount = notes.count { !it.isSynced }
                    if (unsyncedCount == 0) {
                        statusMessage = "All notes are already synced"
                        return@FilledTonalButton
                    }

                    isSyncing = true
                    statusMessage = "Searching for PC in local Wi-Fi..."

                    scope.launch {
                        val serverIp = discoverServerIp(context)
                        if (serverIp == null) {
                            statusMessage = "PC not found. Make sure phone and PC are on the same Wi-Fi"
                            isSyncing = false
                            return@launch
                        }

                        statusMessage = "Found PC at $serverIp! Syncing $unsyncedCount note(s)..."

                        val (updatedList, countSynced) = syncNotesWithServer(notes, serverIp)
                        saveLocalNotes(context, updatedList)
                        notes = updatedList
                        isSyncing = false

                        statusMessage = if (countSynced > 0) {
                            "Synced $countSynced note(s) with PC ($serverIp)!"
                        } else {
                            "Failed to deliver note(s) to $serverIp."
                        }
                    }
                },
                enabled = !isSyncing
            ) {
                Text(if (isSyncing) "Searching..." else "Sync")
            }
        }

        // Повідомлення статусу
        if (statusMessage.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        // 2. Блок створення нової нотатки
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("Enter title...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    placeholder = { Text("Enter content...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Button(
                    onClick = {
                        if (title.isBlank() && content.isBlank()) {
                            statusMessage = "Write something before saving"
                            return@Button
                        }

                        val newNote = Note(
                            title = title.trim(),
                            content = content.trim(),
                            isSynced = false
                        )
                        val updatedList = listOf(newNote) + notes
                        saveLocalNotes(context, updatedList)
                        notes = updatedList

                        title = ""
                        content = ""
                        statusMessage = "Note saved"
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save")
                }
            }
        }

        // 3. Список нотаток
        Text(
            text = "Saved Notes (${notes.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(notes, key = { it.id }) { note ->
                // Натискання на всю картку відкриває перегляд і редагування
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedNote = note },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (note.title.isNotBlank()) note.title else "Untitled",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )

                            // Лаконічний бейдж статусу
                            Surface(
                                color = if (note.isSynced) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (note.isSynced) "Synced" else "Local",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (note.isSynced) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = note.content,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Тільки корисні дії: швидке копіювання та видалення
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    val textToCopy = if (note.title.isNotBlank()) "${note.title}\n\n${note.content}" else note.content
                                    clipboardManager.setText(AnnotatedString(textToCopy))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Copy")
                            }

                            TextButton(
                                onClick = {
                                    val updatedList = notes.filter { it.id != note.id }
                                    saveLocalNotes(context, updatedList)
                                    notes = updatedList
                                    statusMessage = "Note deleted"
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    // Діалогове вікно перегляду та редагування (відкривається при тапі на картку)
    selectedNote?.let { currentNote ->
        var editTitle by remember(currentNote) { mutableStateOf(currentNote.title) }
        var editContent by remember(currentNote) { mutableStateOf(currentNote.content) }

        AlertDialog(
            onDismissRequest = { selectedNote = null },
            title = { Text("Edit Note") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        label = { Text("Content") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updatedList = notes.map {
                            if (it.id == currentNote.id) {
                                it.copy(
                                    title = editTitle.trim(),
                                    content = editContent.trim(),
                                    isSynced = false
                                )
                            } else it
                        }
                        saveLocalNotes(context, updatedList)
                        notes = updatedList
                        selectedNote = null
                        statusMessage = "Note updated locally"
                    }
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { selectedNote = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// 📡 UDP Broadcast Автовиявлення комп'ютера у Wi-Fi мережі
suspend fun discoverServerIp(context: Context): String? = withContext(Dispatchers.IO) {
    var socket: DatagramSocket? = null
    var multicastLock: WifiManager.MulticastLock? = null
    try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicastLock = wifiManager?.createMulticastLock("notes_discovery_lock")?.apply {
            setReferenceCounted(true)
            acquire()
        }

        socket = DatagramSocket().apply {
            broadcast = true
            soTimeout = 2500
        }

        val requestData = "DISCOVER_NOTES_SERVER".toByteArray(Charsets.UTF_8)
        val broadcastAddress = InetAddress.getByName("255.255.255.255")
        val sendPacket = DatagramPacket(requestData, requestData.size, broadcastAddress, 8888)
        socket.send(sendPacket)

        val receiveBuffer = ByteArray(256)
        val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
        socket.receive(receivePacket)

        val reply = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8).trim()
        if (reply.startsWith("NOTES_SERVER_ACK")) {
            return@withContext receivePacket.address.hostAddress
        }
    } catch (_: Exception) {
    } finally {
        socket?.close()
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock.release()
            }
        } catch (_: Exception) {}
    }
    null
}

suspend fun syncNotesWithServer(currentNotes: List<Note>, serverIp: String): Pair<List<Note>, Int> {
    return withContext(Dispatchers.IO) {
        var syncedCount = 0
        val updatedList = currentNotes.map { note ->
            if (!note.isSynced) {
                val success = sendNoteToHost(note.title, note.content, serverIp)
                if (success) {
                    syncedCount++
                    note.copy(isSynced = true)
                } else {
                    note
                }
            } else {
                note
            }
        }
        Pair(updatedList, syncedCount)
    }
}

fun sendNoteToHost(title: String, content: String, host: String): Boolean {
    return try {
        val url = URL("http://$host:8000")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true
        conn.connectTimeout = 3000
        conn.readTimeout = 3000

        val escapedTitle = title.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val escapedContent = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val jsonPayload = """{"title": "$escapedTitle", "content": "$escapedContent"}"""

        conn.outputStream.use { os ->
            os.write(jsonPayload.toByteArray(Charsets.UTF_8))
        }

        conn.responseCode in 200..299
    } catch (_: Exception) {
        false
    }
}

fun saveLocalNotes(context: Context, notes: List<Note>) {
    try {
        val file = File(context.filesDir, "notes.json")
        val jsonArray = JSONArray()
        for (note in notes) {
            val obj = JSONObject()
            obj.put("id", note.id)
            obj.put("title", note.title)
            obj.put("content", note.content)
            obj.put("isSynced", note.isSynced)
            jsonArray.put(obj)
        }
        file.writeText(jsonArray.toString())
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun loadLocalNotes(context: Context): List<Note> {
    val file = File(context.filesDir, "notes.json")
    if (!file.exists()) return emptyList()

    return try {
        val jsonString = file.readText()
        val jsonArray = JSONArray(jsonString)
        val list = mutableListOf<Note>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                Note(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.optString("title", ""),
                    content = obj.optString("content", ""),
                    isSynced = obj.optBoolean("isSynced", false)
                )
            )
        }
        list
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

@Preview(showBackground = true)
@Composable
fun NoteScreenPreview() {
    SecureNoteTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            NoteScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}
