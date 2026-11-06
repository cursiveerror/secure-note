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

// Custom Architectural Palette (refined from icon hue #64ABD0)
private val ColorAccent = Color(0xFF26688A)
private val ColorAccentHover = Color(0xFF1D5370)
private val ColorActiveFill = Color(0xFFEAF1F6)
private val ColorActiveBorder = Color(0xFF26688A)
private val ColorSidebar = Color(0xFFF7F9FB)
private val ColorBorder = Color(0xFFDCE4EB)
private val ColorTextPrimary = Color(0xFF111C24)
private val ColorTextMuted = Color(0xFF5D7382)
private val ColorTextPlaceholder = Color(0xFF8FA5B3)

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

    MaterialTheme {
        Scaffold(
            bottomBar = {
                // Architectural Monospace Status Line
                Surface(
                    color = Color(0xFFECEFF2),
                    modifier = Modifier.fillMaxWidth().height(26.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2E7D32))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "READY // LAN $serverIp:8000 // UDP 8888",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF485E6C),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "CIPHER: AES-256-GCM",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = ColorAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "ENTRIES: ${notes.size}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ColorTextMuted
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
                    // LEFT SIDEBAR: Notes Directory
                    Column(
                        modifier = Modifier
                            .width(310.dp)
                            .fillMaxHeight()
                            .background(ColorSidebar)
                    ) {
                        // Brand Header
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Custom sharp geometric mark
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
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp,
                                        color = ColorTextPrimary
                                    )
                                }

                                Surface(
                                    modifier = Modifier
                                        .clickable { showPairingDialog = true }
                                        .border(1.dp, ColorBorder, RoundedCornerShape(4.dp)),
                                    color = Color.White,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "PAIR",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action button: + New Entry
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .clickable {
                                        val newTitle = "Untitled"
                                        val newContent = ""
                                        onAddNote(newTitle, newContent)
                                        notificationMessage = "Created new note"
                                    },
                                shape = RoundedCornerShape(4.dp),
                                color = ColorAccent
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+ NEW NOTE",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Custom filter field (zero clipping)
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 12.sp,
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
                                                fontSize = 12.sp,
                                                color = ColorTextPlaceholder
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }

                        HorizontalDivider(color = ColorBorder, thickness = 1.dp)

                        // Notes Listing
                        if (filteredNotes.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "NO MATCHES" else "NO ENTRIES\nPRESS '+ NEW NOTE'",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = ColorTextMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredNotes, key = { it.id }) { note ->
                                    val isSelected = note.id == selectedNoteId
                                    DistinctNoteItem(
                                        note = note,
                                        isSelected = isSelected,
                                        onClick = { selectedNoteId = note.id }
                                    )
                                }
                            }
                        }
                    }

                    // Clean Hairline Divider
                    VerticalDivider(color = ColorBorder, thickness = 1.dp)

                    // RIGHT PANE: Editorial Canvas
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    ) {
                        if (selectedNote != null) {
                            DistinctEditorPane(
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
                                    notificationMessage = "Deleted note"
                                }
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "NO NOTE SELECTED",
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ColorTextMuted
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Select an item from directory or create a new entry.",
                                        fontSize = 12.sp,
                                        color = ColorTextPlaceholder
                                    )
                                }
                            }
                        }
                    }
                }

                // Architectural Toast notification
                notificationMessage?.let { msg ->
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(1800)
                        notificationMessage = null
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 38.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = ColorTextPrimary,
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = msg.uppercase(Locale.getDefault()),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Clean Pairing Dialog
        if (showPairingDialog) {
            ArchitecturalPairingDialog(
                onDismiss = { showPairingDialog = false },
                onCopyKey = {
                    val key = CryptoManager.getKeyBase64()
                    clipboardManager.setText(AnnotatedString(key))
                    notificationMessage = "Key copied"
                }
            )
        }
    }
}

@Composable
fun DistinctNoteItem(
    note: Note,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(note.timestamp) { dateFormat.format(Date(note.timestamp)) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (isSelected) ColorActiveFill else Color.Transparent)
    ) {
        // Vertical active accent indicator strip
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .matchParentSize()
                    .align(Alignment.CenterStart)
                    .background(ColorActiveBorder)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title.ifBlank { "Untitled" },
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) ColorAccent else ColorTextPrimary,
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

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = note.content.ifBlank { "(Empty)" },
                fontSize = 11.sp,
                color = if (isSelected) Color(0xFF28546C) else ColorTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Subtly separates notes
        HorizontalDivider(
            color = Color(0xFFEDF2F6),
            thickness = 0.5.dp,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun DistinctEditorPane(
    note: Note,
    onUpdate: (String, String) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    var editTitle by remember(note.id) { mutableStateOf(note.title) }
    var editContent by remember(note.id) { mutableStateOf(note.content) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val formattedDate = remember(note.timestamp) { dateFormat.format(Date(note.timestamp)) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Architectural Header Bar
        Surface(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            color = Color.White
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TIMESTAMP // $formattedDate  ·  CHARS // ${editContent.length}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = ColorTextMuted
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        modifier = Modifier
                            .clickable { onCopy() }
                            .border(1.dp, ColorBorder, RoundedCornerShape(3.dp)),
                        shape = RoundedCornerShape(3.dp),
                        color = Color.White
                    ) {
                        Text(
                            text = "COPY",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = ColorTextPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                            text = "DELETE",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFC62828),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = ColorBorder, thickness = 1.dp)

        // Workspace
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp)
        ) {
            // Note Title (Editorial clean font)
            BasicTextField(
                value = editTitle,
                onValueChange = {
                    editTitle = it
                    onUpdate(it, editContent)
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorTextPrimary,
                    letterSpacing = (-0.5).sp
                ),
                cursorBrush = SolidColor(ColorAccent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (editTitle.isEmpty()) {
                            Text(
                                text = "Untitled Document",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ColorTextPlaceholder,
                                letterSpacing = (-0.5).sp
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFFF0F4F7), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Note Content
            BasicTextField(
                value = editContent,
                onValueChange = {
                    editContent = it
                    onUpdate(editTitle, it)
                },
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    color = Color(0xFF263843)
                ),
                cursorBrush = SolidColor(ColorAccent),
                modifier = Modifier.fillMaxSize(),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.TopStart,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (editContent.isEmpty()) {
                            Text(
                                text = "Write your notes securely here...",
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                color = ColorTextPlaceholder
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
fun ArchitecturalPairingDialog(
    onDismiss: () -> Unit,
    onCopyKey: () -> Unit
) {
    val keyBase64 = remember { CryptoManager.getKeyBase64() }
    val qrContent = remember { "SECURENOTE_KEY:$keyBase64" }
    val qrMatrix = remember(qrContent) { CryptoManager.generateQrMatrix(qrContent, 220) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "PAIRING // AES-256",
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = ColorTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Scan QR from mobile client or copy the symmetric secret key below.",
                    fontSize = 12.sp,
                    color = ColorTextMuted,
                    textAlign = TextAlign.Center
                )

                Surface(
                    modifier = Modifier.size(220.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorder)
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
                                        color = Color(0xFF111C24),
                                        topLeft = Offset(x * cellW, y * cellH),
                                        size = Size(cellW + 0.5f, cellH + 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = Color(0xFFECEFF2),
                    modifier = Modifier.fillMaxWidth().clickable { onCopyKey() }
                ) {
                    Text(
                        text = keyBase64,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ColorTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .clickable { onCopyKey() }
                        .border(1.dp, ColorAccent, RoundedCornerShape(3.dp)),
                    shape = RoundedCornerShape(3.dp),
                    color = Color.White
                ) {
                    Text(
                        text = "COPY SECRET KEY",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = ColorAccent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Surface(
                modifier = Modifier
                    .clickable { onDismiss() },
                shape = RoundedCornerShape(3.dp),
                color = ColorTextPrimary
            ) {
                Text(
                    text = "CLOSE",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    )
}
