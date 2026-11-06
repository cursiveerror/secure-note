package org.cursiveerror.securenote

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cursiveerror.securenote.ui.theme.*
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
            Surface(
                color = Color.White,
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SecureNote",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTextPrimary,
                            letterSpacing = (-0.3).sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Pair button
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showPairingDialog = true }
                                    .border(1.dp, if (isPaired) ColorSuccess else ColorBorder, RoundedCornerShape(8.dp)),
                                color = if (isPaired) ColorSuccessBg else Color.White
                            ) {
                                Text(
                                    text = if (isPaired) "Paired" else "Pair",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isPaired) ColorSuccess else ColorBrand,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            // Sync button
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isSyncing) {
                                        if (isSyncing) return@clickable
                                        isSyncing = true
                                        statusMessage = "Searching..."

                                        scope.launch {
                                            val serverIp = discoverServerIp(context)
                                            if (serverIp == null) {
                                                statusMessage = "PC not found"
                                                isSyncing = false
                                                return@launch
                                            }

                                            statusMessage = "Syncing..."
                                            val (mergedList, syncMsg) = syncNotesWithServer(context, notes, serverIp)
                                            saveLocalNotes(context, mergedList)
                                            notes = mergedList
                                            isSyncing = false
                                            statusMessage = syncMsg
                                        }
                                    },
                                color = ColorBrand
                            ) {
                                Text(
                                    text = if (isSyncing) "Syncing..." else "Sync",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = ColorBorder, thickness = 1.dp)
                }
            }
        },
        floatingActionButton = {
            Surface(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showCreateDialog = true }
                    .padding(bottom = 2.dp),
                shape = RoundedCornerShape(14.dp),
                color = ColorBrand,
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ColorBackground)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status bar message banner
            AnimatedVisibility(visible = statusMessage.isNotEmpty()) {
                Surface(
                    color = ColorBrandSoft,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBE0ED)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = statusMessage,
                            fontSize = 11.sp,
                            color = ColorBrandDark,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "✕",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorBrand,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { statusMessage = "" }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Search Field
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 13.sp,
                    color = ColorTextPrimary
                ),
                cursorBrush = SolidColor(ColorBrand),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .border(1.dp, ColorBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search...",
                                fontSize = 13.sp,
                                color = ColorTextPlaceholder
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Notes list or empty state
            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No matches" else "No notes",
                        fontSize = 13.sp,
                        color = ColorTextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 76.dp)
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        CompactMobileNoteCard(
                            note = note,
                            onClick = { selectedNote = note },
                            onCopy = {
                                val textToCopy = if (note.title.isNotBlank()) "${note.title}\n\n${note.content}" else note.content
                                clipboardManager.setText(AnnotatedString(textToCopy))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = {
                                val updated = notes.filter { it.id != note.id }
                                saveLocalNotes(context, updated)
                                notes = updated
                                statusMessage = "Deleted"
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal: New Note
    if (showCreateDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newContent by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    text = "New",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorTextPrimary),
                        cursorBrush = SolidColor(ColorBrand),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, ColorBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        decorationBox = { inner ->
                            Box {
                                if (newTitle.isEmpty()) Text("Title", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorTextPlaceholder)
                                inner()
                            }
                        }
                    )

                    BasicTextField(
                        value = newContent,
                        onValueChange = { newContent = it },
                        textStyle = TextStyle(fontSize = 14.sp, color = ColorTextPrimary, lineHeight = 20.sp),
                        cursorBrush = SolidColor(ColorBrand),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, ColorBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        decorationBox = { inner ->
                            Box {
                                if (newContent.isEmpty()) Text("Note...", fontSize = 14.sp, color = ColorTextPlaceholder)
                                inner()
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
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
                                statusMessage = "Saved"
                            }
                        },
                    color = ColorBrand
                ) {
                    Text(
                        text = "Save",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            },
            dismissButton = {
                Text(
                    text = "Cancel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorTextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showCreateDialog = false }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        )
    }

    // Modal: Edit Note
    selectedNote?.let { currentNote ->
        var editTitle by remember(currentNote) { mutableStateOf(currentNote.title) }
        var editContent by remember(currentNote) { mutableStateOf(currentNote.content) }

        AlertDialog(
            onDismissRequest = { selectedNote = null },
            title = {
                Text(
                    text = "Edit",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorTextPrimary),
                        cursorBrush = SolidColor(ColorBrand),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, ColorBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        decorationBox = { inner ->
                            Box {
                                if (editTitle.isEmpty()) Text("Title", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorTextPlaceholder)
                                inner()
                            }
                        }
                    )

                    BasicTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        textStyle = TextStyle(fontSize = 14.sp, color = ColorTextPrimary, lineHeight = 20.sp),
                        cursorBrush = SolidColor(ColorBrand),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, ColorBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        decorationBox = { inner ->
                            Box {
                                if (editContent.isEmpty()) Text("Note...", fontSize = 14.sp, color = ColorTextPlaceholder)
                                inner()
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
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
                            statusMessage = "Saved"
                        },
                    color = ColorBrand
                ) {
                    Text(
                        text = "Save",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            },
            dismissButton = {
                Text(
                    text = "Cancel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorTextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { selectedNote = null }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        )
    }

    // Modal: Device Pairing
    if (showPairingDialog) {
        var inputKey by remember { mutableStateOf(CryptoManager.getSavedKey(context) ?: "") }

        AlertDialog(
            onDismissRequest = { showPairingDialog = false },
            title = {
                Text(
                    text = "Pair",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Scan QR or enter key",
                        fontSize = 12.sp,
                        color = ColorTextMuted
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                try {
                                    val scanner = GmsBarcodeScanning.getClient(context)
                                    scanner.startScan()
                                        .addOnSuccessListener { barcode ->
                                            val rawValue = barcode.rawValue ?: ""
                                            if (rawValue.isNotBlank()) {
                                                CryptoManager.saveKey(context, rawValue)
                                                isPaired = true
                                                showPairingDialog = false
                                                statusMessage = "Paired"
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                        color = ColorBrand
                    ) {
                        Text(
                            text = "Scan QR",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 9.dp)
                        )
                    }

                    BasicTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it },
                        textStyle = TextStyle(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ColorTextPrimary
                        ),
                        cursorBrush = SolidColor(ColorBrand),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, ColorBorder, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        decorationBox = { inner ->
                            Box {
                                if (inputKey.isEmpty()) Text("Key...", fontSize = 11.sp, color = ColorTextPlaceholder)
                                inner()
                            }
                        }
                    )

                    if (isPaired) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ColorSuccessBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Paired (AES-256)",
                                color = ColorSuccess,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            if (inputKey.isNotBlank()) {
                                CryptoManager.saveKey(context, inputKey)
                                isPaired = true
                                showPairingDialog = false
                                statusMessage = "Paired"
                            }
                        },
                    color = ColorBrand
                ) {
                    Text(
                        text = "Save",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            },
            dismissButton = {
                if (isPaired) {
                    Text(
                        text = "Unpair",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFC62828),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                CryptoManager.clearKey(context)
                                isPaired = false
                                inputKey = ""
                                statusMessage = "Unpaired"
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                } else {
                    Text(
                        text = "Cancel",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorTextMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showPairingDialog = false }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        )
    }
}

@Composable
fun CompactMobileNoteCard(
    note: Note,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(note.timestamp) { dateFormat.format(Date(note.timestamp)) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title.ifBlank { "Untitled" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = formattedDate,
                    fontSize = 10.sp,
                    color = ColorTextMuted
                )
            }

            if (note.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = note.content,
                    fontSize = 12.sp,
                    color = ColorTextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (note.isSynced) ColorSuccessBg else ColorBrandSoft
                ) {
                    Text(
                        text = if (note.isSynced) "Synced" else "Local",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (note.isSynced) ColorSuccess else ColorBrandDark,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onCopy() }
                            .border(1.dp, ColorBorder, RoundedCornerShape(6.dp)),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White
                    ) {
                        Text(
                            text = "Copy",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = ColorTextPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onDelete() }
                            .border(1.dp, Color(0xFFFFD9D9), RoundedCornerShape(6.dp)),
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFFF7F7)
                    ) {
                        Text(
                            text = "Delete",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFC62828),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
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

        val (pulledNotes, pullSuccess) = fetchNotesFromHost(serverIp, context)
        if (!pullSuccess) {
            val msg = if (pushedCount > 0) "Sent $pushedCount" else "PC unreachable"
            return@withContext Pair(afterPushList, msg)
        }

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
        val finalMsg = "Synced (+$pushedCount / -$newFromPcCount)"
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
