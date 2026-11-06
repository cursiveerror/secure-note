package org.cursiveerror.securenote

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val timestamp: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
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

    var allNotes by remember { mutableStateOf(loadLocalNotes(context)) }
    val notes = remember(allNotes) { allNotes.filter { !it.isDeleted } }

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
                            .height(52.dp)
                            .padding(horizontal = 16.dp),
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
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showPairingDialog = true }
                                    .border(1.dp, if (isPaired) ColorSuccess else ColorBorder, RoundedCornerShape(8.dp)),
                                color = if (isPaired) ColorSuccessBg else Color.White
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxHeight().padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isPaired) "Paired" else "Pair",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isPaired) ColorSuccess else ColorBrand
                                    )
                                }
                            }

                            // Sync button (Phone only)
                            Surface(
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isSyncing) {
                                        if (isSyncing) return@clickable
                                        isSyncing = true
                                        statusMessage = "Connecting..."

                                        scope.launch {
                                            val (mergedList, syncMsg) = syncNotesWithServer(context, allNotes)
                                            saveLocalNotes(context, mergedList)
                                            allNotes = mergedList
                                            isSyncing = false
                                            statusMessage = syncMsg
                                        }
                                    },
                                color = ColorBrand
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isSyncing) "Syncing..." else "Sync",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
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
                    .size(52.dp)
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
                    Canvas(modifier = Modifier.size(16.dp)) {
                        val stroke = 2.5.dp.toPx()
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val half = size.width / 2f
                        drawLine(
                            color = Color.White,
                            start = Offset(cx - half, cy),
                            end = Offset(cx + half, cy),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(cx, cy - half),
                            end = Offset(cx, cy + half),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ColorBackground)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                val updated = allNotes.map {
                                    if (it.id == note.id) {
                                        it.copy(
                                            isDeleted = true,
                                            isSynced = false,
                                            timestamp = System.currentTimeMillis()
                                        )
                                    } else it
                                }
                                saveLocalNotes(context, updated)
                                allNotes = updated
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
                    text = "New Note",
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
                            val newNote = Note(
                                title = newTitle.trim(),
                                content = newContent.trim(),
                                isSynced = false,
                                timestamp = System.currentTimeMillis(),
                                isDeleted = false
                            )
                            val updated = listOf(newNote) + allNotes
                            saveLocalNotes(context, updated)
                            allNotes = updated
                            showCreateDialog = false
                            statusMessage = "Created"
                        },
                    color = ColorBrand
                ) {
                    Text(
                        text = "Create",
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
                    text = "Edit Note",
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
                            val updated = allNotes.map {
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
                            allNotes = updated
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
        var inputIp by remember { mutableStateOf(CryptoManager.getSavedServerIp(context) ?: "") }

        AlertDialog(
            onDismissRequest = { showPairingDialog = false },
            title = {
                Text(
                    text = "Pair with PC",
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
                        text = "Scan QR from PC screen or configure manually",
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
                                                val (savedKey, savedIp) = CryptoManager.saveKey(context, rawValue)
                                                inputKey = savedKey
                                                if (savedIp != null) inputIp = savedIp
                                                isPaired = true
                                                showPairingDialog = false
                                                statusMessage = if (savedIp != null) "Paired ($savedIp)" else "Paired (AES-256)"
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
                            text = "Scan QR Code",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }

                    // PC IP manual field
                    BasicTextField(
                        value = inputIp,
                        onValueChange = { inputIp = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ColorTextPrimary
                        ),
                        cursorBrush = SolidColor(ColorBrand),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, ColorBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        decorationBox = { inner ->
                            Box {
                                if (inputIp.isEmpty()) Text("PC IP (e.g. 192.168.1.100)", fontSize = 12.sp, color = ColorTextPlaceholder)
                                inner()
                            }
                        }
                    )

                    // Key field
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
                            .height(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, ColorBorder, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        decorationBox = { inner ->
                            Box {
                                if (inputKey.isEmpty()) Text("AES Key (Base64)...", fontSize = 11.sp, color = ColorTextPlaceholder)
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
                                text = "✓ Paired (AES-256-GCM)",
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
                            }
                            if (inputIp.isNotBlank()) {
                                CryptoManager.saveServerIp(context, inputIp)
                            }
                            isPaired = CryptoManager.isPaired(context)
                            showPairingDialog = false
                            statusMessage = "Settings saved"
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
                                inputIp = ""
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
            soTimeout = 1500
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

// Full 2-Way Atomic Synchronization
suspend fun syncNotesWithServer(context: Context, currentNotes: List<Note>): Pair<List<Note>, String> {
    return withContext(Dispatchers.IO) {
        var serverIp = CryptoManager.getSavedServerIp(context)
        val discovered = discoverServerIp(context)
        if (!discovered.isNullOrBlank()) {
            serverIp = discovered
            CryptoManager.saveServerIp(context, discovered)
        }

        if (serverIp.isNullOrBlank()) {
            return@withContext Pair(currentNotes, "PC not found. Make sure PC app is running on same Wi-Fi.")
        }

        val clientArray = JSONArray()
        for (n in currentNotes) {
            clientArray.put(JSONObject().apply {
                put("id", n.id)
                put("title", n.title)
                put("content", n.content)
                put("timestamp", n.timestamp)
                put("isDeleted", n.isDeleted)
            })
        }

        val rawPayload = JSONObject().apply {
            put("clientNotes", clientArray)
        }.toString()

        val savedKey = CryptoManager.getSavedKey(context)
        var wasEncrypted = false

        val payloadBytes = if (!savedKey.isNullOrBlank()) {
            try {
                val (iv, data) = CryptoManager.encrypt(rawPayload, savedKey)
                wasEncrypted = true
                """{"iv":"$iv","data":"$data"}""".toByteArray(Charsets.UTF_8)
            } catch (_: Exception) {
                rawPayload.toByteArray(Charsets.UTF_8)
            }
        } else {
            rawPayload.toByteArray(Charsets.UTF_8)
        }

        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$serverIp:8000/sync")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doOutput = true
                connectTimeout = 3000
                readTimeout = 4000
            }

            conn.outputStream.use { os ->
                os.write(payloadBytes)
            }

            val code = conn.responseCode
            if (code == 401) {
                return@withContext Pair(currentNotes, "Key mismatch! Please scan PC's QR code to re-pair.")
            }
            if (code !in 200..299) {
                return@withContext Pair(currentNotes, "Server error ($code)")
            }

            val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

            val decryptedText = if (wasEncrypted && responseText.trim().startsWith("{") && responseText.contains("\"iv\"")) {
                val envelope = JSONObject(responseText)
                val iv = envelope.getString("iv")
                val data = envelope.getString("data")
                CryptoManager.decrypt(data, iv, savedKey!!)
            } else {
                responseText
            }

            val respObj = JSONObject(decryptedText)
            val notesArr = respObj.optJSONArray("notes") ?: JSONArray()
            val mergedList = mutableListOf<Note>()
            for (i in 0 until notesArr.length()) {
                val obj = notesArr.getJSONObject(i)
                mergedList.add(
                    Note(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", ""),
                        content = obj.optString("content", ""),
                        isSynced = true,
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isDeleted = obj.optBoolean("isDeleted", false)
                    )
                )
            }

            val activeCount = mergedList.count { !it.isDeleted }
            Pair(mergedList, "Synced ($activeCount notes)")
        } catch (e: Exception) {
            Pair(currentNotes, "Connection failed: ${e.message ?: "PC unreachable"}")
        } finally {
            conn?.disconnect()
        }
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
                put("isDeleted", note.isDeleted)
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
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    isDeleted = obj.optBoolean("isDeleted", false)
                )
            )
        }
        list
    } catch (_: Exception) {
        emptyList()
    }
}
