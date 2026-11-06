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
import androidx.compose.foundation.shape.CircleShape
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
            // Architectural Header Rail
            Surface(
                color = Color.White,
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Geometric logo mark
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(ColorAccent, RoundedCornerShape(3.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "S",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SECURENOTE",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = ColorTextPrimary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Pair button
                            Surface(
                                modifier = Modifier
                                    .clickable { showPairingDialog = true }
                                    .border(1.dp, if (isPaired) Color(0xFF2E7D32) else ColorBorder, RoundedCornerShape(4.dp)),
                                color = if (isPaired) Color(0xFFE8F5E9) else Color.White,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isPaired) "AES-256" else "PAIR",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPaired) Color(0xFF2E7D32) else ColorAccent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // Sync button
                            Surface(
                                modifier = Modifier
                                    .clickable(enabled = !isSyncing) {
                                        if (isSyncing) return@clickable
                                        isSyncing = true
                                        statusMessage = "Searching for PC in local Wi-Fi..."

                                        scope.launch {
                                            val serverIp = discoverServerIp(context)
                                            if (serverIp == null) {
                                                statusMessage = "PC not found on local Wi-Fi"
                                                isSyncing = false
                                                return@launch
                                            }

                                            statusMessage = "Syncing with $serverIp..."
                                            val (mergedList, syncMsg) = syncNotesWithServer(context, notes, serverIp)
                                            saveLocalNotes(context, mergedList)
                                            notes = mergedList
                                            isSyncing = false
                                            statusMessage = syncMsg
                                        }
                                    },
                                shape = RoundedCornerShape(4.dp),
                                color = ColorAccent
                            ) {
                                Text(
                                    text = if (isSyncing) "SYNCING..." else "SYNC",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = ColorBorder, thickness = 1.dp)
                }
            }
        },
        floatingActionButton = {
            // Sleek architectural pill button
            Surface(
                modifier = Modifier
                    .clickable { showCreateDialog = true }
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(6.dp),
                color = ColorAccent,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "+ NEW NOTE",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF7F9FB))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status bar message banner
            AnimatedVisibility(visible = statusMessage.isNotEmpty()) {
                Surface(
                    color = Color(0xFFECEFF2),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = statusMessage.uppercase(Locale.getDefault()),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ColorTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "DISMISS",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = ColorAccent,
                            modifier = Modifier
                                .clickable { statusMessage = "" }
                                .padding(start = 8.dp)
                        )
                    }
                }
            }

            // Custom Filter Field (zero clipping, clean box)
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 13.sp,
                    color = ColorTextPrimary
                ),
                cursorBrush = SolidColor(ColorAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .border(1.dp, ColorBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Filter notes...",
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "NO MATCHES" else "NO NOTES",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = ColorTextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Try different query." else "Tap '+ NEW NOTE' to create.",
                            fontSize = 12.sp,
                            color = ColorTextPlaceholder
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 72.dp)
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        DistinctMobileCard(
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
                                statusMessage = "Deleted note"
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
                    text = "NEW ENTRY",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
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
                        textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextPrimary),
                        cursorBrush = SolidColor(ColorAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF7F9FB), RoundedCornerShape(4.dp))
                            .border(1.dp, ColorBorder, RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        decorationBox = { inner ->
                            Box {
                                if (newTitle.isEmpty()) Text("Title", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextPlaceholder)
                                inner()
                            }
                        }
                    )

                    BasicTextField(
                        value = newContent,
                        onValueChange = { newContent = it },
                        textStyle = TextStyle(fontSize = 14.sp, color = ColorTextPrimary, lineHeight = 20.sp),
                        cursorBrush = SolidColor(ColorAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(Color(0xFFF7F9FB), RoundedCornerShape(4.dp))
                            .border(1.dp, ColorBorder, RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        decorationBox = { inner ->
                            Box {
                                if (newContent.isEmpty()) Text("Content...", fontSize = 14.sp, color = ColorTextPlaceholder)
                                inner()
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Surface(
                    modifier = Modifier.clickable {
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
                            statusMessage = "Saved locally"
                        }
                    },
                    shape = RoundedCornerShape(3.dp),
                    color = ColorAccent
                ) {
                    Text(
                        text = "SAVE",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            },
            dismissButton = {
                Text(
                    text = "CANCEL",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = ColorTextMuted,
                    modifier = Modifier
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
                    text = "EDIT ENTRY",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
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
                        textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextPrimary),
                        cursorBrush = SolidColor(ColorAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF7F9FB), RoundedCornerShape(4.dp))
                            .border(1.dp, ColorBorder, RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        decorationBox = { inner ->
                            Box {
                                if (editTitle.isEmpty()) Text("Title", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextPlaceholder)
                                inner()
                            }
                        }
                    )

                    BasicTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        textStyle = TextStyle(fontSize = 14.sp, color = ColorTextPrimary, lineHeight = 20.sp),
                        cursorBrush = SolidColor(ColorAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(Color(0xFFF7F9FB), RoundedCornerShape(4.dp))
                            .border(1.dp, ColorBorder, RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        decorationBox = { inner ->
                            Box {
                                if (editContent.isEmpty()) Text("Content...", fontSize = 14.sp, color = ColorTextPlaceholder)
                                inner()
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Surface(
                    modifier = Modifier.clickable {
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
                        statusMessage = "Updated locally"
                    },
                    shape = RoundedCornerShape(3.dp),
                    color = ColorAccent
                ) {
                    Text(
                        text = "SAVE",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            },
            dismissButton = {
                Text(
                    text = "CANCEL",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = ColorTextMuted,
                    modifier = Modifier
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
                    text = "DEVICE PAIRING // AES-256",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
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
                        text = "Scan QR from PC desktop window or paste symmetric key below.",
                        fontSize = 12.sp,
                        color = ColorTextMuted
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
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
                                                statusMessage = "Paired with PC via QR Code"
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(context, "Scan: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                        shape = RoundedCornerShape(4.dp),
                        color = ColorAccent
                    ) {
                        Text(
                            text = "SCAN QR CODE",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }

                    BasicTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it },
                        textStyle = TextStyle(
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ColorTextPrimary
                        ),
                        cursorBrush = SolidColor(ColorAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                            .background(Color(0xFFF7F9FB), RoundedCornerShape(4.dp))
                            .border(1.dp, ColorBorder, RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        decorationBox = { inner ->
                            Box {
                                if (inputKey.isEmpty()) Text("Paste Base64 secret key...", fontSize = 11.sp, color = ColorTextPlaceholder)
                                inner()
                            }
                        }
                    )

                    if (isPaired) {
                        Text(
                            text = "STATUS // CIPHER ACTIVE",
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                Surface(
                    modifier = Modifier.clickable {
                        if (inputKey.isNotBlank()) {
                            CryptoManager.saveKey(context, inputKey)
                            isPaired = true
                            showPairingDialog = false
                            statusMessage = "Paired with PC"
                        }
                    },
                    shape = RoundedCornerShape(3.dp),
                    color = ColorAccent
                ) {
                    Text(
                        text = "SAVE",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            },
            dismissButton = {
                if (isPaired) {
                    Text(
                        text = "UNPAIR",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC62828),
                        modifier = Modifier
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
                        text = "CANCEL",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = ColorTextMuted,
                        modifier = Modifier
                            .clickable { showPairingDialog = false }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        )
    }
}

@Composable
fun DistinctMobileCard(
    note: Note,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(note.timestamp) { dateFormat.format(Date(note.timestamp)) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(1.dp, ColorBorder, RoundedCornerShape(4.dp)),
        shape = RoundedCornerShape(4.dp),
        color = Color.White
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left 3px indicator line (Green for Synced, Cerulean for Local)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(if (note.isSynced) Color(0xFF2E7D32) else ColorAccent)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
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
                        fontFamily = FontFamily.Monospace,
                        color = ColorTextMuted
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = note.content.ifBlank { "(Empty)" },
                    fontSize = 12.sp,
                    color = ColorTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Quick Copy and Delete buttons
            Row(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .clickable { onCopy() }
                        .border(1.dp, ColorBorder, RoundedCornerShape(3.dp)),
                    shape = RoundedCornerShape(3.dp),
                    color = Color.White
                ) {
                    Text(
                        text = "COPY",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = ColorTextPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .clickable { onDelete() }
                        .border(1.dp, Color(0xFFFFCDD2), RoundedCornerShape(3.dp)),
                    shape = RoundedCornerShape(3.dp),
                    color = Color(0xFFFFF8F8)
                ) {
                    Text(
                        text = "DEL",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC62828),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
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
            val msg = if (pushedCount > 0) "Sent $pushedCount note(s) to PC." else "Could not reach PC server."
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
        val finalMsg = "Synced. Sent $pushedCount, received $newFromPcCount from PC."
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
