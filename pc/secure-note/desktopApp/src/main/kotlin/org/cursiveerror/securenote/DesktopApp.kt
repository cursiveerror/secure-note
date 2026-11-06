package org.cursiveerror.securenote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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

// Unified palette derived from icon #64ABD0 (darkened for contrast and readability)
private val BrandPrimary = Color(0xFF357A9E)
private val BrandOnPrimary = Color.White
private val BrandPrimaryContainer = Color(0xFFE4EFF5)
private val BrandOnPrimaryContainer = Color(0xFF103A50)
private val BrandBorder = Color(0xFFCBD8E0)
private val BrandSidebarBg = Color(0xFFF4F7F9)
private val BrandTextPrimary = Color(0xFF16252F)
private val BrandTextSecondary = Color(0xFF5A7280)
private val BrandPlaceholder = Color(0xFF869EAC)

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
            primary = BrandPrimary,
            onPrimary = BrandOnPrimary,
            primaryContainer = BrandPrimaryContainer,
            onPrimaryContainer = BrandOnPrimaryContainer,
            surface = Color.White,
            surfaceVariant = BrandSidebarBg,
            onSurface = BrandTextPrimary,
            outline = BrandBorder
        )
    ) {
        Scaffold(
            bottomBar = {
                // Status bar at the bottom
                Surface(
                    color = Color(0xFFEAF1F5),
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
                                color = BrandTextSecondary,
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
                            color = BrandTextSecondary
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
                    // LEFT SIDEBAR: Notes list + search
                    Column(
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .background(BrandSidebarBg)
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
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandTextPrimary
                                )

                                OutlinedButton(
                                    onClick = { showPairingDialog = true },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = BrandPrimary
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandPrimary)
                                ) {
                                    Text("Pair Device", fontSize = 11.sp, fontWeight = FontWeight.Medium)
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
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandPrimary,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("+ New Note", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Fixed search box without clipping
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 13.sp,
                                    color = BrandTextPrimary
                                ),
                                cursorBrush = SolidColor(BrandPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .border(1.dp, BrandBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                decorationBox = { innerTextField ->
                                    Box(
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Search notes...",
                                                fontSize = 13.sp,
                                                color = BrandPlaceholder
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }

                        HorizontalDivider(color = BrandBorder)

                        // Notes List
                        if (filteredNotes.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No matching notes" else "No notes created yet.",
                                    fontSize = 12.sp,
                                    color = BrandTextSecondary,
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

                    // Vertical Divider
                    VerticalDivider(color = BrandBorder, modifier = Modifier.fillMaxHeight())

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
                                        color = BrandTextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Select a note from the left sidebar or click '+ New Note'.",
                                        fontSize = 12.sp,
                                        color = BrandTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Notification pill
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
                        color = Color(0xFF263A45),
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

        // Dialog for Pairing / QR Code
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
        color = if (isSelected) BrandPrimaryContainer else Color.Transparent,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9EC0D4)) else null
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
                    color = if (isSelected) BrandOnPrimaryContainer else BrandTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = formattedDate,
                    fontSize = 10.sp,
                    color = BrandTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = note.content.ifBlank { "Empty note" },
                fontSize = 11.sp,
                color = if (isSelected) Color(0xFF255B77) else BrandTextSecondary,
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
                    color = BrandTextSecondary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCopy,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandPrimary)
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

        HorizontalDivider(color = BrandBorder)

        // Editor Body with clean, non-clipping text fields
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 18.dp)
        ) {
            // Title field
            BasicTextField(
                value = editTitle,
                onValueChange = {
                    editTitle = it
                    onUpdate(it, editContent)
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandTextPrimary
                ),
                cursorBrush = SolidColor(BrandPrimary),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (editTitle.isEmpty()) {
                            Text(
                                text = "Note Title",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandPlaceholder
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Content field
            BasicTextField(
                value = editContent,
                onValueChange = {
                    editContent = it
                    onUpdate(editTitle, it)
                },
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    color = Color(0xFF263A45)
                ),
                cursorBrush = SolidColor(BrandPrimary),
                modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.TopStart,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (editContent.isEmpty()) {
                            Text(
                                text = "Write your note here...",
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                color = BrandPlaceholder
                            )
                        }
                        innerTextField()
                    }
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
    val qrMatrix = remember(qrContent) { CryptoManager.generateQrMatrix(qrContent, 240) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Device Pairing", fontWeight = FontWeight.Bold, color = BrandTextPrimary)
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
                    color = BrandTextSecondary
                )

                Surface(
                    modifier = Modifier.size(240.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandBorder)
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
                                        color = Color(0xFF16252F),
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
                    color = BrandTextSecondary
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFEAF1F5),
                    modifier = Modifier.fillMaxWidth().clickable { onCopyKey() }
                ) {
                    Text(
                        text = keyBase64,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = BrandOnPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                OutlinedButton(
                    onClick = onCopyKey,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandPrimary)
                ) {
                    Text("Copy Key", fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandPrimary,
                    contentColor = Color.White
                )
            ) {
                Text("Close")
            }
        }
    )
}
