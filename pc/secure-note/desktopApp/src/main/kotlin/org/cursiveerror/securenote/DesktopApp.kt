package org.cursiveerror.securenote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
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
    var selectedNoteId by remember { mutableStateOf<String?>(notes.firstOrNull()?.id) }
    var showPairingDialog by remember { mutableStateOf(false) }
    var notificationMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(notes) {
        if (selectedNoteId == null || notes.none { it.id == selectedNoteId }) {
            selectedNoteId = notes.firstOrNull()?.id
        }
    }

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

    val selectedNote = remember(notes, selectedNoteId) {
        notes.firstOrNull { it.id == selectedNoteId }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1976D2),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE3F2FD),
            onPrimaryContainer = Color(0xFF0D47A1),
            surface = Color.White,
            surfaceVariant = Color(0xFFF5F5F5),
            onSurface = Color(0xFF1C1B1F)
        )
    ) {
        Scaffold(
            bottomBar = {
                // Minimalist footer status bar (no emojis)
                Surface(
                    color = Color(0xFFF0F2F5),
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2E7D32))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Server: http://$serverIp:8000 (UDP: 8888)",
                                fontSize = 11.sp,
                                color = Color(0xFF424242),
                                fontWeight = FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "AES-256-GCM Active",
                                fontSize = 11.sp,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = "${notes.size} note${if (notes.size == 1) "" else "s"}",
                            fontSize = 11.sp,
                            color = Color(0xFF757575)
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // LEFT SIDEBAR: Notes list + controls
                    Column(
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .background(Color(0xFFF9FAFB))
                    ) {
                        // Header
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SecureNote",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF212121)
                                )

                                OutlinedButton(
                                    onClick = { showPairingDialog = true },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Pair Device", fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    val newTitle = "Untitled Note"
                                    val newContent = ""
                                    onAddNote(newTitle, newContent)
                                    notificationMessage = "New note created"
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("+ New Note", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search notes...", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                textStyle = TextStyle(fontSize = 12.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )
                        }

                        HorizontalDivider(color = Color(0xFFE5E7EB))

                        // Notes List
                        if (filteredNotes.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No matching notes" else "No notes created yet.",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(filteredNotes, key = { it.id }) { note ->
                                    val isSelected = note.id == selectedNoteId
                                    NoteListItem(
                                        note = note,
                                        isSelected = isSelected,
                                        onClick = { selectedNoteId = note.id }
                                    )
                                }
                            }
                        }
                    }

                    // Vertical Divider between master and detail
                    VerticalDivider(color = Color(0xFFE5E7EB), modifier = Modifier.fillMaxHeight())

                    // RIGHT PANE: Note Editor
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    ) {
                        if (selectedNote != null) {
                            NoteEditorPane(
                                note = selectedNote,
                                onUpdate = { title, content ->
                                    onEditNote(selectedNote.id, title, content)
                                },
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(selectedNote.content))
                                    notificationMessage = "Copied to clipboard"
                                },
                                onDelete = {
                                    onDeleteNote(selectedNote.id)
                                    notificationMessage = "Note deleted"
                                }
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "No note selected",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Select a note from the left sidebar or click '+ New Note'.",
                                        fontSize = 12.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                        }
                    }
                }

                // Clean notification pill
                notificationMessage?.let { msg ->
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(2000)
                        notificationMessage = null
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 42.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF323232),
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = msg,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Dialog for Pairing / QR Code (No stickers)
        if (showPairingDialog) {
            PairingDialog(
                onDismiss = { showPairingDialog = false },
                onCopyKey = {
                    val key = CryptoManager.getKeyBase64()
                    clipboardManager.setText(AnnotatedString(key))
                    notificationMessage = "Pairing key copied"
                }
            )
        }
    }
}

@Composable
fun NoteListItem(
    note: Note,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(note.timestamp) { dateFormat.format(Date(note.timestamp)) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color(0xFFE3F2FD) else Color.Transparent
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title.ifBlank { "Untitled" },
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color(0xFF0D47A1) else Color(0xFF212121),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = formattedDate,
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = note.content.ifBlank { "Empty note" },
                fontSize = 11.sp,
                color = if (isSelected) Color(0xFF1565C0) else Color(0xFF757575),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun NoteEditorPane(
    note: Note,
    onUpdate: (String, String) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    var editTitle by remember(note.id) { mutableStateOf(note.title) }
    var editContent by remember(note.id) { mutableStateOf(note.content) }
    val dateFormat = remember { SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(note.timestamp) { dateFormat.format(Date(note.timestamp)) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            color = Color.White
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCopy,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Copy", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFEBEE),
                            contentColor = Color(0xFFC62828)
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Delete", fontSize = 11.sp)
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFFE5E7EB))

        // Editor Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 18.dp)
        ) {
            TextField(
                value = editTitle,
                onValueChange = {
                    editTitle = it
                    onUpdate(it, editContent)
                },
                placeholder = { Text("Note Title", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1B1F)
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = editContent,
                onValueChange = {
                    editContent = it
                    onUpdate(editTitle, it)
                },
                placeholder = { Text("Write note content here...", fontSize = 14.sp, color = Color.Gray) },
                modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFF263238)
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
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
    val qrMatrix = remember(qrContent) { CryptoManager.generateQrMatrix(qrContent, 240) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Device Pairing", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Scan this QR code in the SecureNote Android app to pair devices with AES-256 encryption.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = Color.DarkGray
                )

                Surface(
                    modifier = Modifier.size(240.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Canvas(
                        modifier = Modifier.fillMaxSize().padding(10.dp)
                    ) {
                        val cellW = size.width / qrMatrix.width.toFloat()
                        val cellH = size.height / qrMatrix.height.toFloat()
                        for (x in 0 until qrMatrix.width) {
                            for (y in 0 until qrMatrix.height) {
                                if (qrMatrix.get(x, y)) {
                                    drawRect(
                                        color = Color.Black,
                                        topLeft = Offset(x * cellW, y * cellH),
                                        size = Size(cellW + 0.5f, cellH + 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Or copy pairing key manually:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF0F2F5),
                    modifier = Modifier.fillMaxWidth().clickable { onCopyKey() }
                ) {
                    Text(
                        text = keyBase64,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                OutlinedButton(
                    onClick = onCopyKey,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Copy Key", fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
