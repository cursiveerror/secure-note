package org.cursiveerror.securenote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopApp(
    serverIp: String,
    notes: List<Note>,
    onAddNote: (String, String) -> Unit,
    onEditNote: (String, String, String) -> Unit,
    onDeleteNote: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showPairingDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var notificationMessage by remember { mutableStateOf<String?>(null) }

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

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF64ABD0),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE3F2FD),
            onPrimaryContainer = Color(0xFF003250),
            surface = Color(0xFFFBFBFB),
            onSurface = Color(0xFF1C1B1F)
        )
    ) {
        Scaffold(
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "SecureNote Desktop",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2E7D32))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Sync Server: http://$serverIp:8000 (AES-256-GCM Active)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { showPairingDialog = true },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Pair Phone (QR)")
                                }

                                Button(
                                    onClick = { showAddDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("+ New Note", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search notes by title or content...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFFF5F6F8))
            ) {
                if (filteredNotes.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No notes match '$searchQuery'" else "No notes stored yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Try searching for a different keyword" else "Open SecureNote on your Android phone, pair via QR code, and tap 'Sync with PC'!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.DarkGray
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(filteredNotes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(note.content))
                                    notificationMessage = "Copied to clipboard!"
                                },
                                onEdit = { editingNote = note },
                                onDelete = { onDeleteNote(note.id) }
                            )
                        }
                    }
                }

                // Temporary notification banner
                notificationMessage?.let { msg ->
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(2000)
                        notificationMessage = null
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF323232),
                        shadowElevation = 6.dp
                    ) {
                        Text(
                            text = msg,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Dialog for Pairing / QR Code
        if (showPairingDialog) {
            PairingDialog(
                onDismiss = { showPairingDialog = false },
                onCopyKey = {
                    val key = CryptoManager.getKeyBase64()
                    clipboardManager.setText(AnnotatedString(key))
                    notificationMessage = "Pairing Key copied!"
                }
            )
        }

        // Dialog for adding a new note
        if (showAddDialog) {
            NoteEditDialog(
                title = "New Note",
                initialTitle = "",
                initialContent = "",
                onDismiss = { showAddDialog = false },
                onConfirm = { t, c ->
                    onAddNote(t, c)
                    showAddDialog = false
                    notificationMessage = "Note created!"
                }
            )
        }

        // Dialog for editing existing note
        editingNote?.let { note ->
            NoteEditDialog(
                title = "Edit Note",
                initialTitle = note.title,
                initialContent = note.content,
                onDismiss = { editingNote = null },
                onConfirm = { t, c ->
                    onEditNote(note.id, t, c)
                    editingNote = null
                    notificationMessage = "Note updated!"
                }
            )
        }
    }
}

@Composable
fun PairingDialog(
    onDismiss: () -> Unit,
    onCopyKey: () -> Unit
) {
    val keyBase64 = remember { CryptoManager.getKeyBase64() }
    val qrContent = remember { "SECURENOTE_KEY:$keyBase64" }
    val qrMatrix = remember(qrContent) { CryptoManager.generateQrMatrix(qrContent, 260) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Pair Android Device", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Scan this QR code in the SecureNote Android app to establish end-to-end AES-256 encrypted sync.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = Color.DarkGray
                )

                Surface(
                    modifier = Modifier.size(260.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 3.dp
                ) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.fillMaxSize().padding(14.dp)
                    ) {
                        val cellW = size.width / qrMatrix.width
                        val cellH = size.height / qrMatrix.height
                        for (x in 0 until qrMatrix.width) {
                            for (y in 0 until qrMatrix.height) {
                                if (qrMatrix.get(x, y)) {
                                    drawRect(
                                        color = Color.Black,
                                        topLeft = androidx.compose.ui.geometry.Offset(x * cellW, y * cellH),
                                        size = androidx.compose.ui.geometry.Size(cellW, cellH)
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Or copy pairing key manually:",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF0F2F5),
                    modifier = Modifier.fillMaxWidth().clickable { onCopyKey() }
                ) {
                    Text(
                        text = keyBase64,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                OutlinedButton(
                    onClick = onCopyKey,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Copy Key")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun NoteCard(
    note: Note,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(note.timestamp) { dateFormat.format(Date(note.timestamp)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp)
        ) {
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

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCopy,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Copy")
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Edit")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFEBEE),
                        contentColor = Color(0xFFC62828)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
fun NoteEditDialog(
    title: String,
    initialTitle: String,
    initialContent: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var noteTitle by remember { mutableStateOf(initialTitle) }
    var noteContent by remember { mutableStateOf(initialContent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(min = 450.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = noteContent,
                    onValueChange = { noteContent = it },
                    label = { Text("Content") },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    maxLines = 10
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (noteTitle.isNotBlank() || noteContent.isNotBlank()) {
                        onConfirm(noteTitle.trim(), noteContent.trim())
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
