package org.cursiveerror.securenote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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

    // Synchronize selection if note list changes
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
            surfaceVariant = Color(0xFFF1F3F5),
            onSurface = Color(0xFF1F1F1F)
        )
    ) {
        Scaffold(
            bottomBar = {
                // Bottom Status Bar
                Surface(
                    color = Color(0xFFECEFF1),
                    modifier = Modifier.fillMaxWidth().height(28.dp),
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
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sync Server: http://$serverIp:8000 (UDP: 8888)",
                                fontSize = 11.sp,
                                color = Color(0xFF37474F),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "🔐 AES-256-GCM Active",
                                fontSize = 11.sp,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "${notes.size} note${if (notes.size == 1) "" else "s"}",
                            fontSize = 11.sp,
                            color = Color(0xFF546E7A)
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
                // Master-Detail Layout
                Row(modifier = Modifier.fillMaxSize()) {
                    // LEFT SIDEBAR: Notes list + search
                    Column(
                        modifier = Modifier
                            .width(330.dp)
                            .fillMaxHeight()
                            .background(Color(0xFFF7F8FA))
                            .border(width = 1.dp, color = Color(0xFFE0E0E0))
                    ) {
                        // Sidebar Header
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SecureNote",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A1A1A)
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IconButton(
                                        onClick = { showPairingDialog = true },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Text("📱", fontSize = 16.sp)
                                    }

                                    Button(
                                        onClick = {
                                            val newTitle = "Untitled Note"
                                            val newContent = ""
                                            onAddNote(newTitle, newContent)
                                            notificationMessage = "New note created!"
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("+ New", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Search bar
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search notes...", fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                textStyle = TextStyle(fontSize = 13.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )
                        }

                        // Notes List
                        if (filteredNotes.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No notes match search" else "No notes yet.\nClick '+ New' to start!",
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
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

                    // RIGHT PANE: Note Editor / Viewer
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
                                    notificationMessage = "Note copied to clipboard!"
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
                                        text = "No Note Selected",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Select a note from the left, or click '+ New' to create one.",
                                        fontSize = 13.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                        }
                    }
                }

                // Temporary notification toast
                notificationMessage?.let { msg ->
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(2000)
                        notificationMessage = null
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 36.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF263238),
                        shadowElevation = 8.dp
                    ) {
                        Text(
                            text = msg,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            fontSize = 13.sp
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
                    notificationMessage = "Pairing Key copied to clipboard!"
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
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFFE3F2FD) else Color.White,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF90CAF9)) else null,
        tonalElevation = if (isSelected) 2.dp else 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title.ifBlank { "Untitled" },
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) Color(0xFF0D47A1) else Color(0xFF212121),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = formattedDate,
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = note.content.ifBlank { "No additional text" },
                fontSize = 12.sp,
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
    val dateFormat = remember { SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm", Locale.getDefault()) }
    val formattedDate = remember(note.timestamp) { dateFormat.format(Date(note.timestamp)) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Editor Header Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            color = Color.White,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCopy,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Copy", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFEBEE),
                            contentColor = Color(0xFFC62828)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Delete", fontSize = 12.sp)
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFFEEEEEE))

        // Editor Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            // Note Title
            TextField(
                value = editTitle,
                onValueChange = {
                    editTitle = it
                    onUpdate(it, editContent)
                },
                placeholder = { Text("Note Title", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Note Content
            TextField(
                value = editContent,
                onValueChange = {
                    editContent = it
                    onUpdate(editTitle, it)
                },
                placeholder = { Text("Write your secure note here...", fontSize = 15.sp) },
                modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    color = Color(0xFF37474F)
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
                    Canvas(
                        modifier = Modifier.fillMaxSize().padding(14.dp)
                    ) {
                        val cellW = size.width / qrMatrix.width
                        val cellH = size.height / qrMatrix.height
                        for (x in 0 until qrMatrix.width) {
                            for (y in 0 until qrMatrix.height) {
                                if (qrMatrix.get(x, y)) {
                                    drawRect(
                                        color = Color.Black,
                                        topLeft = Offset(x * cellW, y * cellH),
                                        size = Size(cellW, cellH)
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
