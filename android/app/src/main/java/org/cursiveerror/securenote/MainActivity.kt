package org.cursiveerror.securenote

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cursiveerror.securenote.ui.theme.SecureNoteTheme
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val isSynced: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SecureNoteTheme {
                NoteScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var notes by remember { mutableStateOf(loadLocalNotes(context)) }
    var searchQuery by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }
    var isPaired by remember { mutableStateOf(CryptoManager.isPaired(context)) }
    var showPairingDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedNote by remember { mutableStateOf<Note?>(null) }

    val filteredNotes = remember(notes, searchQuery) {
        if (searchQuery.isBlank()) {
            notes
        } else {
            notes.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SecureNote",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                actions = {
                    OutlinedButton(
                        onClick = { showPairingDialog = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = if (isPaired) "AES-256" else "Pair PC",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isPaired) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    FilledTonalButton(
                        onClick = {
                            if (isSyncing) return@FilledTonalButton
                            isSyncing = true
                            statusMessage = "Searching for PC in local Wi-Fi..."

                            scope.launch {
                                val serverIp = discoverServerIp(context)
                                if (serverIp == null) {
                                    statusMessage = "PC not found. Make sure both devices are on the same Wi-Fi."
                                    isSyncing = false
                                    return@launch
                                }

                                statusMessage = "Found PC at $serverIp. Syncing..."
                                val (mergedList, syncMsg) = syncNotesWithServer(context, notes, serverIp)
                                saveLocalNotes(context, mergedList)
                                notes = mergedList
                                isSyncing = false
                                statusMessage = syncMsg
                            }
                        },
                        enabled = !isSyncing,
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isSyncing) "Syncing..." else "Sync",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text("+", fontSize = 26.sp, fontWeight = FontWeight.Normal)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status bar message banner
            AnimatedVisibility(visible = statusMessage.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { statusMessage = "" },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Dismiss", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search notes...", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
            )

            // Notes list or empty state
            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No notes match search" else "No notes yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Try another keyword" else "Tap '+' to create your first note.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        NoteCardItem(
                            note = note,
                            onClick = { selectedNote = note },
                            onCopy = {
                                val textToCopy = if (note.title.isNotBlank()) "${note.title}\n\n${note.content}" else note.content
                                clipboardManager.setText(AnnotatedString(textToCopy))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = {
                                val updated = notes.filter { it.id != note.id }
                                saveLocalNotes(context, updated)
                                notes = updated
                                statusMessage = "Note deleted"
                            }
                        )
                    }
                }
            }
        }
    }

    // Dialog for creating a new note
    if (showCreateDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newContent by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Note", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newContent,
                        onValueChange = { newContent = it },
                        label = { Text("Content") },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        maxLines = 8
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank() || newContent.isNotBlank()) {
                            val newNote = Note(
                                title = newTitle.trim(),
                                content = newContent.trim(),
                                isSynced = false,
                                timestamp = System.currentTimeMillis()
                            )
                            val updated = listOf(newNote) + notes
                            saveLocalNotes(context, updated)
                            notes = updated
                            showCreateDialog = false
                            statusMessage = "Note saved locally"
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog for viewing and editing note
    selectedNote?.let { currentNote ->
        var editTitle by remember(currentNote) { mutableStateOf(currentNote.title) }
        var editContent by remember(currentNote) { mutableStateOf(currentNote.content) }

        AlertDialog(
            onDismissRequest = { selectedNote = null },
            title = { Text("Edit Note", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        label = { Text("Content") },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        maxLines = 8
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = notes.map {
                            if (it.id == currentNote.id) {
                                it.copy(
                                    title = editTitle.trim(),
                                    content = editContent.trim(),
                                    isSynced = false,
                                    timestamp = System.currentTimeMillis()
                                )
                            } else it
                        }
                        saveLocalNotes(context, updated)
                        notes = updated
                        selectedNote = null
                        statusMessage = "Note updated locally"
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedNote = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog for pairing with PC (QR scanner + manual paste)
    if (showPairingDialog) {
        var inputKey by remember { mutableStateOf(CryptoManager.getSavedKey(context) ?: "") }

        AlertDialog(
            onDismissRequest = { showPairingDialog = false },
            title = { Text("Device Pairing", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Scan the QR code displayed in the PC app ('Pair Device') or enter the key manually below:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray
                    )

                    Button(
                        onClick = {
                            try {
                                val scanner = GmsBarcodeScanning.getClient(context)
                                scanner.startScan()
                                    .addOnSuccessListener { barcode ->
                                        val rawValue = barcode.rawValue ?: ""
                                        if (rawValue.isNotBlank()) {
                                            CryptoManager.saveKey(context, rawValue)
                                            isPaired = true
                                            showPairingDialog = false
                                            statusMessage = "Paired with PC via QR Code. AES-256 active."
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(context, "Scanner: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Scanner error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan QR Code")
                    }

                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it },
                        label = { Text("Pairing Key") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3
                    )

                    if (isPaired) {
                        Text(
                            text = "Status: AES-256-GCM Encrypted",
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputKey.isNotBlank()) {
                            CryptoManager.saveKey(context, inputKey)
                            isPaired = true
                            showPairingDialog = false
                            statusMessage = "Paired with PC. AES-256 active."
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                if (isPaired) {
                    TextButton(
                        onClick = {
                            CryptoManager.clearKey(context)
                            isPaired = false
                            inputKey = ""
                            statusMessage = "Unpaired from PC"
                        }
                    ) {
                        Text("Unpair", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { showPairingDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
fun NoteCardItem(
    note: Note,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(note.timestamp) { dateFormat.format(Date(note.timestamp)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title.ifBlank { "Untitled Note" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    color = if (note.isSynced) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (note.isSynced) "Synced" else "Local",
                        color = if (note.isSynced) Color(0xFF2E7D32) else Color(0xFFE65100),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = note.content.ifBlank { "Empty note" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onCopy,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Copy", fontSize = 12.sp)
                }

                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete", fontSize = 12.sp)
                }
            }
        }
    }
}

// UDP Broadcast Auto-Discovery
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

// Two-Way LAN Synchronization
suspend fun syncNotesWithServer(context: Context, currentNotes: List<Note>, serverIp: String): Pair<List<Note>, String> {
    return withContext(Dispatchers.IO) {
        var pushedCount = 0

        // Phase 1: Push unsynced notes to PC
        val afterPushList = currentNotes.map { note ->
            if (!note.isSynced) {
                val success = sendNoteToHost(note.id, note.title, note.content, note.timestamp, serverIp, context)
                if (success) {
                    pushedCount++
                    note.copy(isSynced = true)
                } else {
                    note
                }
            } else {
                note
            }
        }

        // Phase 2: Pull all notes from PC
        val (pulledNotes, pullSuccess) = fetchNotesFromHost(serverIp, context)
        if (!pullSuccess) {
            val msg = if (pushedCount > 0) "Sent $pushedCount note(s) to PC." else "Could not reach PC server."
            return@withContext Pair(afterPushList, msg)
        }

        // Phase 3: Merge notes by timestamp
        val noteMap = afterPushList.associateBy { it.id }.toMutableMap()
        var newFromPcCount = 0

        for (remote in pulledNotes) {
            val existing = noteMap[remote.id]
            if (existing == null) {
                noteMap[remote.id] = remote.copy(isSynced = true)
                newFromPcCount++
            } else {
                if (remote.timestamp > existing.timestamp) {
                    noteMap[remote.id] = remote.copy(isSynced = true)
                }
            }
        }

        val mergedList = noteMap.values.sortedByDescending { it.timestamp }
        val finalMsg = "Sync complete. Sent $pushedCount, received $newFromPcCount from PC."
        Pair(mergedList, finalMsg)
    }
}

fun sendNoteToHost(id: String, title: String, content: String, timestamp: Long, host: String, context: Context): Boolean {
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
        val rawJson = """{"id":"$id","title":"$escapedTitle","content":"$escapedContent","timestamp":$timestamp}"""

        val savedKey = CryptoManager.getSavedKey(context)
        val jsonPayload = if (!savedKey.isNullOrBlank()) {
            val (iv, data) = CryptoManager.encrypt(rawJson, savedKey)
            """{"iv":"$iv","data":"$data"}"""
        } else {
            rawJson
        }

        conn.outputStream.use { os ->
            os.write(jsonPayload.toByteArray(Charsets.UTF_8))
        }

        conn.responseCode in 200..299
    } catch (_: Exception) {
        false
    }
}

fun fetchNotesFromHost(host: String, context: Context): Pair<List<Note>, Boolean> {
    return try {
        val url = URL("http://$host:8000/notes")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 3000
        conn.readTimeout = 3000

        if (conn.responseCode !in 200..299) {
            return Pair(emptyList(), false)
        }

        val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val savedKey = CryptoManager.getSavedKey(context)

        val jsonArrayText = if (responseText.trim().startsWith("{") && responseText.contains("\"iv\"")) {
            val envelope = JSONObject(responseText)
            val iv = envelope.getString("iv")
            val data = envelope.getString("data")
            if (savedKey.isNullOrBlank()) {
                return Pair(emptyList(), false)
            }
            CryptoManager.decrypt(data, iv, savedKey)
        } else {
            responseText
        }

        val array = JSONArray(jsonArrayText)
        val list = mutableListOf<Note>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                Note(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.optString("title", ""),
                    content = obj.optString("content", ""),
                    isSynced = true,
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }
        Pair(list, true)
    } catch (_: Exception) {
        Pair(emptyList(), false)
    }
}

fun saveLocalNotes(context: Context, notes: List<Note>) {
    try {
        val file = File(context.filesDir, "notes.json")
        val jsonArray = JSONArray()
        for (note in notes) {
            val obj = JSONObject().apply {
                put("id", note.id)
                put("title", note.title)
                put("content", note.content)
                put("isSynced", note.isSynced)
                put("timestamp", note.timestamp)
            }
            jsonArray.put(obj)
        }
        file.writeText(jsonArray.toString(2))
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
                    isSynced = obj.optBoolean("isSynced", false),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
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
        NoteScreen()
    }
}
