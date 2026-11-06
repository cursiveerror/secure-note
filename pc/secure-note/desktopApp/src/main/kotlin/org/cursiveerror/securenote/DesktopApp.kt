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
import androidx.compose.ui.graphics.StrokeCap
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
private val ColorBrand = Color(0xFF438EB5)
private val ColorBrandDark = Color(0xFF286282)
private val ColorBrandSoft = Color(0xFFEAF3F8)
private val ColorSidebar = Color(0xFFF8FAFC)
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
    isPaired: Boolean = false,
    onUnpair: () -> Unit = {},
    onAddNote: (String, String) -> Unit,
    onEditNote: (String, String, String) -> Unit,
    onDeleteNote: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }
    val activeNotes = notes.filter { !it.isDeleted }
    var selectedNoteId by remember { mutableStateOf<String?>(activeNotes.firstOrNull()?.id) }
    var showPairingDialog by remember { mutableStateOf(false) }
    var notificationMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeNotes.map { it.id }) {
        if (selectedNoteId == null || activeNotes.none { it.id == selectedNoteId }) {
            selectedNoteId = activeNotes.firstOrNull()?.id
        }
    }

    val filteredNotes = remember(activeNotes, searchQuery) {
        if (searchQuery.isBlank()) {
            activeNotes
        } else {
            activeNotes.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val selectedNote = remember(activeNotes, selectedNoteId) {
        activeNotes.firstOrNull { it.id == selectedNoteId }
    }

    MaterialTheme {
        Scaffold(
            bottomBar = {
                // Compact status footer
                Surface(
                    color = Color(0xFFEEF3F7),
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
                                    .background(if (isPaired) ColorSuccess else Color(0xFFF59E0B))
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = if (isPaired) "Paired" else "Unpaired",
                                fontSize = 11.sp,
                                color = if (isPaired) ColorSuccess else ColorBrandDark,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "·  LAN $serverIp:8000",
                                fontSize = 11.sp,
                                color = ColorTextMuted
                            )
                        }

                        Text(
                            text = "${activeNotes.size} notes",
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
                            .width(290.dp)
                            .fillMaxHeight()
                            .background(ColorSidebar)
                    ) {
                        // Header Row: Brand Title + Aligned Action Buttons (Pair & +)
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
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorTextPrimary,
                                letterSpacing = (-0.3).sp
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Pair / Paired button
                                Surface(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showPairingDialog = true }
                                        .border(
                                            1.dp,
                                            if (isPaired) Color(0xFFC3E6CD) else ColorBorder,
                                            RoundedCornerShape(8.dp)
                                        ),
                                    color = if (isPaired) Color(0xFFE8F5E9) else Color.White
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxHeight().padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (isPaired) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF2E7D32))
                                            )
                                        }
                                        Text(
                                            text = if (isPaired) "Paired" else "Pair",
                                            fontSize = 11.sp,
                                            fontWeight = if (isPaired) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (isPaired) Color(0xFF2E7D32) else ColorBrand
                                        )
                                    }
                                }

                                // Geometric, perfectly centered '+' button
                                Surface(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onAddNote("Untitled", "")
                                            notificationMessage = "Created"
                                        },
                                    color = ColorBrand
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(modifier = Modifier.size(12.dp)) {
                                            val stroke = 2.dp.toPx()
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
                        }

                        // Full-width Search Bar (strictly aligned to 16dp horizontal padding)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 10.dp)
                        ) {
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
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .border(1.dp, ColorBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp),
                                decorationBox = { innerTextField ->
                                    Box(
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Search...",
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

                        // Notes List (strictly aligned to 16dp horizontal padding)
                        if (filteredNotes.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No matches" else "No notes",
                                    fontSize = 12.sp,
                                    color = ColorTextMuted
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(filteredNotes, key = { it.id }) { note ->
                                    val isSelected = note.id == selectedNoteId
                                    CompactNoteItem(
                                        note = note,
                                        isSelected = isSelected,
                                        onClick = { selectedNoteId = note.id }
                                    )
                                }
                            }
                        }
                    }

                    VerticalDivider(color = ColorBorder, thickness = 1.dp)

                    // RIGHT PANE: Note Editor
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    ) {
                        if (selectedNote != null) {
                            CompactEditorPane(
                                note = selectedNote,
                                onUpdate = { title, content ->
                                    onEditNote(selectedNote.id, title, content)
                                },
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(selectedNote.content))
                                    notificationMessage = "Copied"
                                },
                                onDelete = {
                                    onDeleteNote(selectedNote.id)
                                    notificationMessage = "Deleted"
                                }
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Select a note",
                                    fontSize = 13.sp,
                                    color = ColorTextMuted
                                )
                            }
                        }
                    }
                }

                // Compact Toast
                notificationMessage?.let { msg ->
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(1500)
                        notificationMessage = null
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 34.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = ColorTextPrimary,
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = msg,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (showPairingDialog) {
            CompactPairingDialog(
                serverIp = serverIp,
                isPaired = isPaired,
                onUnpair = {
                    onUnpair()
                    notificationMessage = "Unpaired and key regenerated"
                },
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
fun CompactNoteItem(
    note: Note,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(note.timestamp) { dateFormat.format(Date(note.timestamp)) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) ColorBrandSoft else Color.Transparent,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBE0ED)) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp)
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
                    color = if (isSelected) ColorBrandDark else ColorTextPrimary,
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

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = note.content.ifBlank { "Empty" },
                fontSize = 11.sp,
                color = if (isSelected) Color(0xFF33576C) else ColorTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CompactEditorPane(
    note: Note,
    onUpdate: (String, String) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    var editTitle by remember(note.id) { mutableStateOf(note.title) }
    var editContent by remember(note.id) { mutableStateOf(note.content) }
    val dateFormat = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(note.timestamp) { dateFormat.format(Date(note.timestamp)) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar (aligned height with sidebar header: 52dp)
        Surface(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            color = Color.White
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$formattedDate · ${editContent.length} chars",
                    fontSize = 11.sp,
                    color = ColorTextMuted
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onCopy() }
                            .border(1.dp, ColorBorder, RoundedCornerShape(8.dp)),
                        color = Color.White
                    ) {
                        Box(
                            modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Copy",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = ColorTextPrimary
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDelete() }
                            .border(1.dp, Color(0xFFFFD9D9), RoundedCornerShape(8.dp)),
                        color = Color(0xFFFFF7F7)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Delete",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFC62828)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = ColorBorder, thickness = 1.dp)

        // Workspace
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
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
                    color = ColorTextPrimary,
                    letterSpacing = (-0.3).sp
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
                                text = "Title",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorTextPlaceholder,
                                letterSpacing = (-0.3).sp
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFF4F7FA), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            BasicTextField(
                value = editContent,
                onValueChange = {
                    editContent = it
                    onUpdate(editTitle, it)
                },
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
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
                                text = "Note...",
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
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
fun CompactPairingDialog(
    serverIp: String,
    isPaired: Boolean,
    onUnpair: () -> Unit,
    onDismiss: () -> Unit,
    onCopyKey: () -> Unit
) {
    val keyBase64 = remember(isPaired) { CryptoManager.getKeyBase64() }
    val qrContent = remember(serverIp, keyBase64) {
        "SECURENOTE_PAIR:{\"ip\":\"$serverIp\",\"port\":8000,\"key\":\"$keyBase64\"}"
    }
    val qrMatrix = remember(qrContent) { CryptoManager.generateQrMatrix(qrContent, 200) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isPaired) "Device Pairing (Paired)" else "Pair with Phone",
                fontSize = 15.sp,
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
                if (isPaired) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE8F5E9),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC3E6CD)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✓ Phone is paired and connected",
                            color = Color(0xFF2E7D32),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Scan QR with phone to connect",
                        fontSize = 12.sp,
                        color = ColorTextMuted
                    )
                }

                Surface(
                    modifier = Modifier.size(200.dp),
                    shape = RoundedCornerShape(12.dp),
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
                    shape = RoundedCornerShape(6.dp),
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onCopyKey() }
                        .border(1.dp, ColorBrand, RoundedCornerShape(6.dp)),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White
                ) {
                    Text(
                        text = "Copy key",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorBrand,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        },
        confirmButton = {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onDismiss() },
                shape = RoundedCornerShape(6.dp),
                color = ColorBrand
            ) {
                Text(
                    text = "Done",
                    fontSize = 11.sp,
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFC62828),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            onUnpair()
                            onDismiss()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    )
}
