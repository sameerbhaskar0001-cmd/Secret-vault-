package com.example

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalAppThemeColors
import kotlinx.coroutines.delay

@Composable
fun NoteDetailScreen(
    noteId: String?,
    viewModel: CalculatorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val notes by viewModel.vaultNotes.collectAsState()
    
    val noteStr = remember(noteId, notes) {
        if (noteId != null) {
            notes.firstOrNull { it.split("|||").firstOrNull() == noteId }
        } else {
            null
        }
    }
    
    val parts = remember(noteStr) {
        noteStr?.split("|||", limit = 3) ?: emptyList()
    }
    val timestamp = remember(parts) {
        if (parts.isNotEmpty()) parts[0] else java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
    }
    val initialTitle = remember(parts) {
        if (parts.size >= 2) parts[1] else ""
    }
    val initialBody = remember(parts) {
        if (parts.size >= 3) parts[2] else ""
    }
    
    var editedNoteTitle by remember { mutableStateOf("") }
    var editedNoteContentValue by remember { mutableStateOf(TextFieldValue("")) }
    var isEditingNote by remember { mutableStateOf(true) }
    
    // Track if we have already saved this note as a new note, to avoid creating duplicates on every keystore edit
    var activeNoteStr by remember { mutableStateOf<String?>(noteStr) }
    
    LaunchedEffect(noteStr) {
        if (noteStr != null) {
            editedNoteTitle = initialTitle
            editedNoteContentValue = TextFieldValue(
                text = initialBody,
                selection = TextRange(initialBody.length)
            )
            activeNoteStr = noteStr
        } else {
            editedNoteTitle = ""
            editedNoteContentValue = TextFieldValue("")
        }
    }
    
    val themePurple = LocalAppThemeColors.current.themePurple
    
    // Auto-save logic
    LaunchedEffect(editedNoteTitle, editedNoteContentValue.text) {
        if (isEditingNote && (editedNoteTitle.isNotEmpty() || editedNoteContentValue.text.isNotEmpty())) {
            delay(1000)
            val currentTitle = if (editedNoteTitle.isBlank()) "Untitled Note" else editedNoteTitle
            val currentBody = editedNoteContentValue.text
            val currentActiveStr = activeNoteStr
            if (currentActiveStr != null) {
                viewModel.editVaultNote(currentActiveStr, currentTitle, currentBody)
                activeNoteStr = "$timestamp|||$currentTitle|||$currentBody"
            } else {
                val newNoteStr = viewModel.addVaultNote(currentTitle, currentBody)
                activeNoteStr = newNoteStr
            }
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF070A14)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0C1020),
                            Color(0xFF05070E)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            viewModel.triggerKeypressEffects(context)
                            // Manual save before exiting
                            if (editedNoteTitle.isNotEmpty() || editedNoteContentValue.text.isNotEmpty()) {
                                val currentTitle = if (editedNoteTitle.isBlank()) "Untitled Note" else editedNoteTitle
                                val currentBody = editedNoteContentValue.text
                                val currentActiveStr = activeNoteStr
                                if (currentActiveStr != null) {
                                    viewModel.editVaultNote(currentActiveStr, currentTitle, currentBody)
                                } else {
                                    viewModel.addVaultNote(currentTitle, currentBody)
                                }
                            }
                            onBack()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.04f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = themePurple,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    Text(
                        text = if (isEditingNote) "Editing Note" else "Secret Note",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    
                    IconButton(
                        onClick = {
                            viewModel.triggerKeypressEffects(context)
                            if (isEditingNote) {
                                if (editedNoteTitle.isNotEmpty() || editedNoteContentValue.text.isNotEmpty()) {
                                    val currentTitle = if (editedNoteTitle.isBlank()) "Untitled Note" else editedNoteTitle
                                    val currentBody = editedNoteContentValue.text
                                    val currentActiveStr = activeNoteStr
                                    if (currentActiveStr != null) {
                                        viewModel.editVaultNote(currentActiveStr, currentTitle, currentBody)
                                        activeNoteStr = "$timestamp|||$currentTitle|||$currentBody"
                                    } else {
                                        val newNoteStr = viewModel.addVaultNote(currentTitle, currentBody)
                                        activeNoteStr = newNoteStr
                                    }
                                }
                                isEditingNote = false
                            } else {
                                isEditingNote = true
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.04f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isEditingNote) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = if (isEditingNote) "Done" else "Edit",
                            tint = themePurple,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                // Custom Divider
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                
                // Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isEditingNote) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Title Input
                            OutlinedTextField(
                                value = editedNoteTitle,
                                onValueChange = { editedNoteTitle = it; viewModel.updateLastInteraction() },
                                placeholder = { Text("Title", fontSize = 22.sp, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold) },
                                textStyle = TextStyle(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            // Toolbar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { 
                                        viewModel.triggerKeypressEffects(context)
                                        editedNoteContentValue = toggleTag(editedNoteContentValue, "<b>", "</b>") 
                                        viewModel.updateLastInteraction()
                                    },
                                    modifier = Modifier.size(36.dp).background(if (isTagActive(editedNoteContentValue, "<b>", "</b>")) Color.White.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                                ) {
                                    Text("B", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 16.sp)
                                }
                                IconButton(
                                    onClick = { 
                                        viewModel.triggerKeypressEffects(context)
                                        editedNoteContentValue = toggleTag(editedNoteContentValue, "<i>", "</i>") 
                                        viewModel.updateLastInteraction()
                                    },
                                    modifier = Modifier.size(36.dp).background(if (isTagActive(editedNoteContentValue, "<i>", "</i>")) Color.White.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                                ) {
                                    Text("I", fontStyle = FontStyle.Italic, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = { 
                                        viewModel.triggerKeypressEffects(context)
                                        editedNoteContentValue = toggleTag(editedNoteContentValue, "<u>", "</u>") 
                                        viewModel.updateLastInteraction()
                                    },
                                    modifier = Modifier.size(36.dp).background(if (isTagActive(editedNoteContentValue, "<u>", "</u>")) Color.White.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                                ) {
                                    Text("U", textDecoration = TextDecoration.Underline, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                
                                Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color.White.copy(alpha = 0.1f)))
                                
                                IconButton(
                                    onClick = { 
                                        viewModel.triggerKeypressEffects(context)
                                        editedNoteContentValue = toggleLinePrefix(editedNoteContentValue, "• ") 
                                        viewModel.updateLastInteraction()
                                    },
                                    modifier = Modifier.size(36.dp).background(if (isPrefixActive(editedNoteContentValue, "• ")) Color.White.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                                ) {
                                    Icon(Icons.Default.List, contentDescription = "Bullet List", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                
                                IconButton(
                                    onClick = { 
                                        viewModel.triggerKeypressEffects(context)
                                        editedNoteContentValue = toggleLinePrefix(editedNoteContentValue, "1. ") 
                                        viewModel.updateLastInteraction()
                                    },
                                    modifier = Modifier.size(36.dp).background(if (isPrefixActive(editedNoteContentValue, "1. ")) Color.White.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                                ) {
                                    Text("1.", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                                }
                                
                                IconButton(
                                    onClick = { 
                                        viewModel.triggerKeypressEffects(context)
                                        editedNoteContentValue = toggleLinePrefix(editedNoteContentValue, "[ ] ") 
                                        viewModel.updateLastInteraction()
                                    },
                                    modifier = Modifier.size(36.dp).background(if (isPrefixActive(editedNoteContentValue, "[ ] ")) Color.White.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                                ) {
                                    Icon(Icons.Default.CheckBox, contentDescription = "Checklist", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                            
                            // Note Body Input
                            OutlinedTextField(
                                value = editedNoteContentValue,
                                onValueChange = { editedNoteContentValue = it; viewModel.updateLastInteraction() },
                                placeholder = { Text("Start typing your secret notes...", fontSize = 16.sp, color = Color.White.copy(alpha = 0.3f)) },
                                textStyle = TextStyle(
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    lineHeight = 26.sp
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                visualTransformation = RichTextVisualTransformation(themePurple),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                    } else {
                        // Read-only View
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                        ) {
                            Text(
                                text = editedNoteTitle.ifBlank { "Untitled Note" },
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Last edited: $timestamp",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
                            Spacer(modifier = Modifier.height(18.dp))
                            
                            // Rendered note view
                            RenderNoteContent(
                                content = editedNoteContentValue.text,
                                themePurple = themePurple,
                                onToggleChecklist = { lineIndex, checked ->
                                    viewModel.triggerKeypressEffects(context)
                                    val lines = editedNoteContentValue.text.split("\n").toMutableList()
                                    if (lines.size > lineIndex) {
                                        val currentLine = lines[lineIndex]
                                        if (checked) {
                                            lines[lineIndex] = currentLine.replaceFirst("[ ] ", "[x] ")
                                        } else {
                                            lines[lineIndex] = currentLine.replaceFirst("[x] ", "[ ] ")
                                        }
                                        val newBody = lines.joinToString("\n")
                                        val currentActiveStr = activeNoteStr
                                        if (currentActiveStr != null) {
                                            viewModel.editVaultNote(currentActiveStr, editedNoteTitle, newBody)
                                            activeNoteStr = "$timestamp|||$editedNoteTitle|||$newBody"
                                        } else {
                                            val newNoteStr = viewModel.addVaultNote(editedNoteTitle, newBody)
                                            activeNoteStr = newNoteStr
                                        }
                                        editedNoteContentValue = TextFieldValue(
                                            text = newBody,
                                            selection = TextRange(newBody.length)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
