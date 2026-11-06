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

// Cerulean & Sky Palette derived directly from app icon (#64ABD0)
private val ColorIconSky = Color(0xFF64ABD0)
private val ColorBrand = Color(0xFF438EB5)
private val ColorBrandDark = Color(0xFF286282)
private val ColorBrandSoft = Color(0xFFEAF3F8)
private val ColorSidebar = Color(0xFFF8FAFC)
private val ColorBackground = Color(0xFFF3F7FA)
private val ColorBorder = Color(0xFFE2EAF0)
private val ColorTextPrimary = Color(0xFF1A262F)
private val ColorTextMuted = Color(0xFF637887)
private val ColorTextPlaceholder = Color(0xFF98AFBE)
private val ColorSuccess = Color(0xFF2E7D32)

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
                // Subtle status footer matching icon tone
                Surface(
                    color = Color(0xFFEEF3F7),
                    modifier = Modifier.fillMaxWidth().height(28.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(ColorSuccess)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AES-256 Encrypted",
                                fontSize = 11.sp,
                                color = ColorBrandDark,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "·  LAN: $serverIp:8000  ·  UDP 8888",
                                fontSize = 11.sp,
                                color = ColorTextMuted
                            )
                        }

                        Text(
                            text = "${notes.size} notes",
                            fontSize = 11.sp,
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
                        // Brand Header without standalone icon
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Secure Note",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorTextPrimary,
                                        letterSpacing = (-0.3).sp
                                    )
                                    Text(
                                        text = "Local & Encrypted",
                                        fontSize = 11.sp,
                                        color = ColorTextMuted
                                    )
                                }

                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showPairingDialog = true }
                                        .border(1.dp, ColorBorder, RoundedCornerShape(8.dp)),
                                    color = Color.White
                                ) {
                                    Text(
                                        text = "Pair device",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ColorBrand,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action button: + New note (soft squircle)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        val newTitle = "Untitled"
                                        val newContent = ""
                                        onAddNote(newTitle, newContent)
                                        notificationMessage = "New note created"
                                    },
                                color = ColorBrand
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+ New note",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Search field (comfortable padding, zero clipping)
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 12.sp,
                                    color = ColorTextPrimary
                                ),
                                cursorBrush = SolidColor(ColorBrand),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White)
                                    .border(1.dp, ColorBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                decorationBox = { innerTextField ->
                                    Box(
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Search notes...",
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
                                    text = if (searchQuery.isNotBlank()) "No notes match your search" else "No notes yet\nClick '+ New note' to start",
                                    fontSize = 12.sp,
                                    color = ColorTextMuted,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(filteredNotes, key = { it.id }) { note ->
                                    val isSelected = note.id == selectedNoteId
                                    SoftSquircleNoteItem(
                                        note = note,
                                        isSelected = isSelected,
                                        onClick = { selectedNoteId = note.id }
                                    )
                                }
                            }
                        }
                    }

                    // Soft Divider
                    VerticalDivider(color = ColorBorder, thickness = 1.dp)

                    // RIGHT PANE: Editorial Canvas
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    ) {
                        if (selectedNote != null) {
                            SoftEditorPane(
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
                                        text = "Select a note to view",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ColorTextMuted
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Or create a new note from the left sidebar.",
                                        fontSize = 12.sp,
                                        color = ColorTextPlaceholder
                                    )
                                }
                            }
                        }
                    }
                }

                // Toast notification
                notificationMessage?.let { msg ->
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(1800)
                        notificationMessage = null
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 38.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = ColorTextPrimary,
                        shadowElevation = 6.dp
                    ) {
                        Text(
                            text = msg,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Clean Pairing Dialog
        if (showPairingDialog) {
            SoftPairingDialog(
                onDismiss = { showPairingDialog = false },
                onCopyKey = {
                    val key = CryptoManager.getKeyBase64()
                    clipboardManager.setText(AnnotatedString(key))
                    notificationMessage = "Key copied to clipboard"
                }
            )
        }
    }
}

@Composable
fun SoftSquircleNoteItem(
    note: Note,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(note.timestamp) { dateFormat.format(Date(note.timestamp)) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) ColorBrandSoft else Color.Transparent,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBE0ED)) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title.ifBlank { "Untitled note" },
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) ColorBrandDark else ColorTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = formattedDate,
                    fontSize = 11.sp,
                    color = ColorTextMuted
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = note.content.ifBlank { "No additional text" },
                fontSize = 12.sp,
                color = if (isSelected) Color(0xFF33576C) else ColorTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SoftEditorPane(
    note: Note,
    onUpdate: (String, String) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    var editTitle by remember(note.id) { mutableStateOf(note.title) }
    var editContent by remember(note.id) { mutableStateOf(note.content) }
    val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy · HH:mm", Locale.getDefault()) }
    val formattedDate = remember(note.timestamp) { dateFormat.format(Date(note.timestamp)) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Calm Top Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            color = Color.White
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved · $formattedDate · ${editContent.length} characters",
                    fontSize = 11.sp,
                    color = ColorTextMuted
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onCopy() }
                            .border(1.dp, ColorBorder, RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White
                    ) {
                        Text(
                            text = "Copy text",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = ColorTextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDelete() }
                            .border(1.dp, Color(0xFFFFD9D9), RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF7F7)
                    ) {
                        Text(
                            text = "Delete",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFC62828),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFFF0F4F7), thickness = 1.dp)

        // Workspace
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 24.dp)
        ) {
            // Note Title
            BasicTextField(
                value = editTitle,
                onValueChange = {
                    editTitle = it
                    onUpdate(it, editContent)
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextPrimary,
                    letterSpacing = (-0.4).sp
                ),
                cursorBrush = SolidColor(ColorBrand),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (editTitle.isEmpty()) {
                            Text(
                                text = "Untitled note",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorTextPlaceholder,
                                letterSpacing = (-0.4).sp
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFF4F7FA), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

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
                    color = ColorTextPrimary
                ),
                cursorBrush = SolidColor(ColorBrand),
                modifier = Modifier.fillMaxSize(),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.TopStart,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (editContent.isEmpty()) {
                            Text(
                                text = "Write your thoughts securely here...",
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
fun SoftPairingDialog(
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
                text = "Pair with Mobile App",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ColorTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Scan this QR code from your phone or copy the key below to sync your notes securely over local Wi-Fi.",
                    fontSize = 13.sp,
                    color = ColorTextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Surface(
                    modifier = Modifier.size(220.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorder)
                ) {
                    Canvas(
                        modifier = Modifier.fillMaxSize().padding(12.dp)
                    ) {
                        val cellW = size.width / qrMatrix.width.toFloat()
                        val cellH = size.height / qrMatrix.height.toFloat()
                        for (x in 0 until qrMatrix.width) {
                            for (y in 0 until qrMatrix.height) {
                                if (qrMatrix.get(x, y)) {
                                    drawRect(
                                        color = Color(0xFF1B2832),
                                        topLeft = Offset(x * cellW, y * cellH),
                                        size = Size(cellW + 0.5f, cellH + 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F8),
                    modifier = Modifier.fillMaxWidth().clickable { onCopyKey() }
                ) {
                    Text(
                        text = keyBase64,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ColorTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCopyKey() }
                        .border(1.dp, ColorBrand, RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White
                ) {
                    Text(
                        text = "Copy secret key",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorBrand,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDismiss() },
                shape = RoundedCornerShape(8.dp),
                color = ColorBrand
            ) {
                Text(
                    text = "Done",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    )
}
