package com.example

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.LocalAppThemeColors
import java.io.File

private val numericNameCounters = mutableMapOf<String, Int>()
private val currentTypeCounters = mutableMapOf<String, Int>()

fun generateUserFriendlyName(type: String, id: String, isScreenshot: Boolean = false): String {
    val effectiveId = if (id.isEmpty()) "unknown_id_${System.identityHashCode(Any())}" else id
    if (isScreenshot) {
        val key = "Screenshot-$effectiveId"
        if (!numericNameCounters.containsKey(key)) {
            val c = currentTypeCounters.getOrDefault("Screenshot", 1)
            numericNameCounters[key] = c
            currentTypeCounters["Screenshot"] = c + 1
        }
        val count = numericNameCounters[key]
        return if (count == 1) "Screenshot" else "Screenshot $count"
    }

    val displayType = when(type.lowercase()) {
        "image", "photo" -> "Photo"
        "video" -> "Video"
        "audio", "music" -> "Audio"
        "document", "doc" -> "Document"
        else -> type.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
    val key = "$displayType-$effectiveId"
    if (!numericNameCounters.containsKey(key)) {
        val c = currentTypeCounters.getOrDefault(displayType, 1)
        numericNameCounters[key] = c
        currentTypeCounters[displayType] = c + 1
    }
    return "$displayType ${numericNameCounters[key]}"
}

fun isVaultVideo(fileStr: String): Boolean {
    val parts = fileStr.split("|||")
    val mime = if (parts.size >= 4) parts[3].lowercase() else ""
    val name = if (parts.size >= 3) parts[2].lowercase() else ""
    val path = if (parts.size >= 5) parts[4].lowercase() else ""
    return mime.startsWith("video/") || fileStr.contains("|||video/") ||
        name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") ||
        name.endsWith(".mov") || name.endsWith(".webm") || name.endsWith(".flv") ||
        name.endsWith(".3gp") || name.endsWith(".m4v") || name.endsWith(".ts") ||
        path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".avi") ||
        path.endsWith(".mov") || path.endsWith(".webm") || path.endsWith(".flv")
}

fun isVaultImage(fileStr: String): Boolean {
    val parts = fileStr.split("|||")
    val mime = if (parts.size >= 4) parts[3].lowercase() else ""
    val name = if (parts.size >= 3) parts[2].lowercase() else ""
    val path = if (parts.size >= 5) parts[4].lowercase() else ""
    return mime.startsWith("image/") || fileStr.contains("|||image/") ||
        name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
        name.endsWith(".webp") || name.endsWith(".gif") ||
        path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
        path.endsWith(".webp") || path.endsWith(".gif")
}

fun isVaultAudio(fileStr: String): Boolean {
    val parts = fileStr.split("|||")
    val mime = if (parts.size >= 4) parts[3].lowercase() else ""
    val name = if (parts.size >= 3) parts[2].lowercase() else ""
    val path = if (parts.size >= 5) parts[4].lowercase() else ""
    return mime.startsWith("audio/") || fileStr.contains("|||audio/") ||
        name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a") ||
        name.endsWith(".ogg") || name.endsWith(".flac") || name.endsWith(".aac") ||
        path.endsWith(".mp3") || path.endsWith(".wav") || path.endsWith(".m4a")
}

fun isVaultDocument(fileStr: String): Boolean {
    val parts = fileStr.split("|||")
    if (parts.size < 4) return false
    return !isVaultVideo(fileStr) && !isVaultImage(fileStr) && !isVaultAudio(fileStr)
}

fun cleanDisplayName(rawName: String, fallbackType: String = "file", id: String = ""): String {
    var cleaned = rawName
    val prefixes = listOf("IMG_", "VID_", "AUD_", "DOC_", "PXL_", "Screenshot_")
    var hasScreenshotPrefix = false
    for (prefix in prefixes) {
        if (cleaned.startsWith(prefix, ignoreCase = true)) {
            cleaned = cleaned.substring(prefix.length)
            if (prefix.equals("Screenshot_", ignoreCase = true)) {
                hasScreenshotPrefix = true
            }
            break
        }
    }
    val lastDot = cleaned.lastIndexOf('.')
    val ext = if (lastDot > 0) cleaned.substring(lastDot + 1).lowercase() else ""
    if (lastDot > 0) {
        cleaned = cleaned.substring(0, lastDot)
    }
    
    val isNumeric = cleaned.all { it.isDigit() || it == '_' || it == '-' }
    if (isNumeric || cleaned.trim().isEmpty()) {
        val typeToUse = if (fallbackType != "file" && fallbackType.isNotEmpty()) fallbackType else {
            when (ext) {
                "jpg", "jpeg", "png", "webp", "gif" -> "Photo"
                "mp4", "mkv", "avi", "mov" -> "Video"
                "mp3", "wav", "ogg", "m4a", "aac" -> "Audio"
                "pdf", "doc", "docx", "txt" -> "Document"
                else -> "File"
            }
        }
        val effectiveId = if (id.isNotEmpty()) id else rawName
        return generateUserFriendlyName(typeToUse, effectiveId, hasScreenshotPrefix)
    }
    
    return cleaned
}

data class VaultItemData(
    val id: String,
    val timestamp: Long,
    val title: String,
    val isFav: Boolean,
    val folder: String,
    val type: String, // "image", "video", "document", "audio", "note"
    val path: String = "",
    val rawString: String, // needed for favorite logic
    val durationMs: Long = 0L
) {
    val cleanTitle: String get() = if (type == "note") title else cleanDisplayName(title, type, id)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultContentScreen(
    viewModel: CalculatorViewModel,
    context: Context,
    title: String,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptySubtitle: String,
    addLabel: String,
    items: List<VaultItemData>,
    folders: List<String>,
    lockedFolders: Set<String>,
    tempUnlockedFolders: Set<String>,
    onNavigateBack: () -> Unit,
    onAddClick: () -> Unit,
    onItemClick: (VaultItemData, index: Int, allFiltered: List<VaultItemData>) -> Unit,
    onDeleteItems: (Set<String>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onMoveItems: (Set<String>, String) -> Unit,
    onShareItems: ((Set<String>) -> Unit)? = null,
    onNavigateToBackup: (() -> Unit)? = null
) {
    val BrandBg = LocalAppThemeColors.current.brandBg
    val ThemePurple = LocalAppThemeColors.current.themePurple
    val KeypadBg = LocalAppThemeColors.current.keypadBg

    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf("Newest") }
    var showSortOptions by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf("All") }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItemRaws by remember { mutableStateOf(setOf<String>()) }
    
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }

    val filteredItems = items.filter { item ->
        val matchesFolder = when (selectedFolder) {
            "All" -> true
            "Favorites" -> item.isFav
            "Uncategorized" -> item.folder.isEmpty()
            else -> item.folder == selectedFolder
        }
        val matchesSearch = if (searchQuery.isEmpty()) {
            true
        } else {
            val matchesTitle = item.title.contains(searchQuery, ignoreCase = true)
            val matchesBody = if (item.type == "note") {
                val parts = item.rawString.split("|||")
                val content = if (parts.size >= 3) parts[2].replace(Regex("<[^>]*>"), "") else ""
                content.contains(searchQuery, ignoreCase = true)
            } else {
                false
            }
            matchesTitle || matchesBody
        }
        matchesFolder && matchesSearch
    }.let { list ->
        when (sortOrder) {
            "Newest" -> list.sortedByDescending { it.timestamp }
            "Oldest" -> list.sortedBy { it.timestamp }
            "Name" -> list.sortedBy { it.cleanTitle.lowercase() }
            else -> list
        }
    }

    val pinnedNotes by viewModel.pinnedNotes.collectAsState()

    val (pinnedItems, otherItems) = remember(filteredItems, pinnedNotes, title) {
        if (title == "Notes" && pinnedNotes.isNotEmpty()) {
            filteredItems.partition { pinnedNotes.contains(it.rawString) }
        } else {
            Pair(emptyList(), filteredItems)
        }
    }

    // Ambient Premium Background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BrandBg,
                        KeypadBg,
                        Color(0xFF0F131F)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isSelectionMode) {
                // Selection Top Bar
                Surface(
                    color = Color.White.copy(alpha = 0.03f),
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            IconButton(
                                onClick = {
                                    isSelectionMode = false
                                    selectedItemRaws = emptySet()
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Text(
                                text = "${selectedItemRaws.size} Selected",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )
                        }
                        IconButton(
                            onClick = {
                                if (selectedItemRaws.size == filteredItems.size && filteredItems.isNotEmpty()) {
                                    selectedItemRaws = emptySet()
                                } else {
                                    selectedItemRaws = filteredItems.map { it.rawString }.toSet()
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ThemePurple.copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = ThemePurple, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            } else {
                // Premium Normal Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            viewModel.triggerKeypressEffects(context)
                            onNavigateBack()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF161B2B).copy(alpha = 0.95f))
                            .border(
                                width = 1.2.dp,
                                color = Color.White.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    val categoryLabel = when (title) {
                        "Photos", "Videos" -> "SECURE MEDIA"
                        "Documents" -> "ENCRYPTED FILE BOX"
                        "Notes" -> "PRIVATE NOTEBOOK"
                        else -> "VAULT CONTENT"
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = categoryLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ThemePurple.copy(alpha = 0.8f),
                            letterSpacing = 1.8.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = (-0.3).sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    
                    Box {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (title == "Music & Audio") {
                                val isBackgroundEnabled by viewModel.backgroundAudioPlaybackEnabled.collectAsStateWithLifecycle()
                                IconButton(
                                    onClick = {
                                        viewModel.setBackgroundAudioPlaybackEnabled(!isBackgroundEnabled)
                                        Toast.makeText(context, if (!isBackgroundEnabled) "Background playback enabled" else "Background playback disabled", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isBackgroundEnabled) ThemePurple.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                                        .border(1.2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isBackgroundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                        contentDescription = "Toggle Background Playback",
                                        tint = if (isBackgroundEnabled) ThemePurple else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            VaultBackupAlertButton(
                                onNavigateToBackup = onNavigateToBackup
                            )
                            IconButton(
                                onClick = { showSortOptions = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(1.2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                            ) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        DropdownMenu(
                            expanded = showSortOptions,
                            onDismissRequest = { showSortOptions = false },
                            modifier = Modifier.background(KeypadBg, RoundedCornerShape(12.dp))
                        ) {
                            listOf("Newest", "Oldest", "Name").forEach { order ->
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            text = order, 
                                            color = if (sortOrder == order) ThemePurple else Color.White, 
                                            fontWeight = if (sortOrder == order) FontWeight.Bold else FontWeight.Medium
                                        ) 
                                    },
                                    onClick = {
                                        sortOrder = order
                                        showSortOptions = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Premium Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .height(54.dp),
                    placeholder = { Text("Search ${title.lowercase()}...", color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(22.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.White.copy(alpha = 0.06f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = ThemePurple
                    ),
                    shape = RoundedCornerShape(27.dp),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
                )
            }


            // Small Information Row
            if (!isSelectionMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${filteredItems.size} ${if (filteredItems.size == 1) title.removeSuffix("s") else title}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )
                    val infoSubtitle = when (title) {
                        "Photos", "Videos" -> "Encrypted • Recently Added"
                        "Documents" -> "Encrypted • Secure Storage"
                        "Notes" -> "Encrypted • Private Notes"
                        else -> "Encrypted • Secured"
                    }
                    Text(
                        text = infoSubtitle,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Main Content Area
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                if (filteredItems.isEmpty()) {
                    PremiumVaultEmptyState(
                        emptyIcon = emptyIcon,
                        emptyTitle = emptyTitle,
                        emptySubtitle = emptySubtitle,
                        searchQuery = searchQuery,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp) // Space for action button
                    ) {
                        if (title == "Notes" && pinnedItems.isNotEmpty()) {
                            // Pinned Section Header
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = null,
                                        tint = ThemePurple,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PINNED",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }

                            // Pinned Items
                            items(pinnedItems.size, key = { index -> "pinned_${pinnedItems[index].id}" }) { index ->
                                val item = pinnedItems[index]
                                val isSelected = selectedItemRaws.contains(item.rawString)
                                
                                VaultItemCard(
                                    item = item,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    isPinned = true,
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedItemRaws = if (isSelected) selectedItemRaws - item.rawString else selectedItemRaws + item.rawString
                                            if (selectedItemRaws.isEmpty()) isSelectionMode = false
                                        } else {
                                            onItemClick(item, filteredItems.indexOf(item), filteredItems)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedItemRaws = setOf(item.rawString)
                                        }
                                    }
                                )
                            }

                            // Others Section Header
                            if (otherItems.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                                    ) {
                                        Text(
                                            text = "OTHERS",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Others / Remaining Items
                        items(otherItems.size, key = { index -> "other_${otherItems[index].id}" }) { index ->
                            val item = otherItems[index]
                            val isSelected = selectedItemRaws.contains(item.rawString)
                            val itemIsPinned = title == "Notes" && pinnedNotes.contains(item.rawString)
                            
                            VaultItemCard(
                                item = item,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                isPinned = itemIsPinned,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedItemRaws = if (isSelected) selectedItemRaws - item.rawString else selectedItemRaws + item.rawString
                                        if (selectedItemRaws.isEmpty()) isSelectionMode = false
                                    } else {
                                        onItemClick(item, filteredItems.indexOf(item), filteredItems)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedItemRaws = setOf(item.rawString)
                                    }
                                }
                            )
                        }
                    }
                }
                
                // Floating Action Button
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isSelectionMode,
                    enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
                    exit = scaleOut(animationSpec = tween(150)) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp)
                ) {
                    val contentColor = if (ThemePurple.red > 0.95f && ThemePurple.green > 0.95f && ThemePurple.blue > 0.95f) BrandBg else Color.White
                    Button(
                        onClick = onAddClick,
                        modifier = Modifier.height(64.dp).shadow(24.dp, RoundedCornerShape(32.dp), ambientColor = ThemePurple, spotColor = ThemePurple),
                        shape = RoundedCornerShape(32.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ThemePurple),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                        contentPadding = PaddingValues(horizontal = 32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = addLabel, tint = contentColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(addLabel, color = contentColor, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, letterSpacing = 0.5.sp)
                    }
                }
                
                // Selection Bottom Bar
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSelectionMode,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {
                    Surface(
                        color = Color(0xFF161B2B).copy(alpha = 0.98f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .height(96.dp),
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        shadowElevation = 24.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SelectionActionButton(
                                icon = Icons.Default.LockOpen,
                                label = "Unhide",
                                color = Color.White,
                                onClick = {
                                    var unhidCount = 0
                                    val total = selectedItemRaws.size
                                    selectedItemRaws.forEach { raw ->
                                        viewModel.unhideVaultFile(context, raw, onSuccess = {
                                            unhidCount++
                                            if (unhidCount == total) {
                                                android.widget.Toast.makeText(context, "Successfully unhid $total items", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }, onFailure = {
                                            android.widget.Toast.makeText(context, "Failed to unhide some items", android.widget.Toast.LENGTH_SHORT).show()
                                        })
                                    }
                                    isSelectionMode = false
                                    selectedItemRaws = emptySet()
                                }
                            )
                            val hasFavs = filteredItems.filter { selectedItemRaws.contains(it.rawString) }.any { it.isFav }
                            SelectionActionButton(
                                icon = if (hasFavs) Icons.Default.StarOutline else Icons.Default.Star,
                                label = if (hasFavs) "Unfavorite" else "Favorite",
                                color = Color.White,
                                onClick = {
                                    selectedItemRaws.forEach { onToggleFavorite(it) }
                                    isSelectionMode = false
                                    selectedItemRaws = emptySet()
                                }
                            )
                            if (onShareItems != null && selectedItemRaws.isNotEmpty()) {
                                SelectionActionButton(
                                    icon = Icons.Default.Share,
                                    label = "Share",
                                    color = Color.White,
                                    onClick = {
                                        onShareItems(selectedItemRaws)
                                        isSelectionMode = false
                                        selectedItemRaws = emptySet()
                                    }
                                )
                            }
                            SelectionActionButton(
                                icon = Icons.Default.Delete,
                                label = "Delete",
                                color = Color(0xFFEF5350),
                                onClick = {
                                    onDeleteItems(selectedItemRaws)
                                    isSelectionMode = false
                                    selectedItemRaws = emptySet()
                                }
                            )
                        }
                    }
                }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        var newFolderName by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Folder", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name", color = Color.White.copy(alpha=0.6f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ThemePurple,
                        unfocusedBorderColor = Color.White.copy(alpha=0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        onCreateFolder(newFolderName.trim())
                        selectedFolder = newFolderName.trim()
                        showCreateFolderDialog = false
                    }
                }) {
                    Text("Create", color = ThemePurple, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha=0.6f))
                }
            },
            containerColor = KeypadBg
        )
    }

    if (showMoveDialog) {
        val allTargetFolders = listOf("Uncategorized") + folders
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Move to Folder", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(allTargetFolders) { folder ->
                        Text(
                            text = if (folder == "Uncategorized") "Remove from Folder" else folder,
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMoveItems(selectedItemRaws, if (folder == "Uncategorized") "" else folder)
                                    isSelectionMode = false
                                    selectedItemRaws = emptySet()
                                    showMoveDialog = false
                                }
                                .padding(vertical = 16.dp, horizontal = 8.dp)
                        )
                        androidx.compose.material3.Divider(color = Color.White.copy(alpha = 0.1f))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha=0.6f))
                }
            },
            containerColor = KeypadBg
        )
    }
}

@Composable
fun FolderChip(
    label: String,
    selected: Boolean,
    isLocked: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val ThemePurple = LocalAppThemeColors.current.themePurple
    val selectedBg = ThemePurple.copy(alpha = 0.2f)
    val selectedContentColor = ThemePurple
    val unselectedBg = Color.White.copy(alpha = 0.05f)

    Surface(
        color = if (selected) selectedBg else unselectedBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (selected) ThemePurple.copy(alpha = 0.5f) else Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isLocked) {
                Icon(Icons.Default.Lock, contentDescription = "Locked", modifier = Modifier.size(16.dp), tint = if (selected) selectedContentColor else Color.White.copy(alpha = 0.5f))
            } else if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (selected) selectedContentColor else Color.White.copy(alpha = 0.5f))
            }
            Text(
                text = label,
                color = if (selected) selectedContentColor else Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultItemCard(
    item: VaultItemData,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isPinned: Boolean = false
) {
    val themePurple = LocalAppThemeColors.current.themePurple
    
    val borderAlpha by animateFloatAsState(if (isSelected) 1f else 0f)
    val borderColor = if (isSelected) themePurple else Color.White.copy(alpha = 0.05f)
    val borderWidth by animateDpAsState(if (isSelected) 3.dp else 1.dp)
    val cornerRadius = 24.dp

    val cardAspectRatio = if (item.type == "image" || item.type == "video") 1f else 0.82f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(cardAspectRatio)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(cornerRadius), ambientColor = Color.Black.copy(alpha=0.6f), spotColor = Color.Black.copy(alpha=0.6f))
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF1C2235))
            .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        when (item.type) {
            "image", "video" -> {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(ctx)
                        .data(File(item.path))
                        .crossfade(true)
                        .build(),
                    imageLoader = VaultImageLoader.get(ctx),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (item.type == "video") {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                    if (item.durationMs > 0) {
                        val sec = (item.durationMs / 1000) % 60
                        val min = (item.durationMs / 1000) / 60
                        val durStr = String.format("%02d:%02d", min, sec)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(durStr, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            "audio" -> {
                val parts = item.rawString.split("|||")
                val dateStr = if (parts.size >= 2) parts[1].substringBefore(",") else ""
                val sizeStr = if (parts.size >= 6) parts[5] else ""
                val ext = item.title.substringAfterLast('.').lowercase()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(themePurple.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = themePurple,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = ext.uppercase().take(4),
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = item.cleanTitle,
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        val metaParts = listOfNotNull(
                            sizeStr.takeIf { it.isNotEmpty() },
                            dateStr.takeIf { it.isNotEmpty() }
                        )
                        Text(
                            text = metaParts.joinToString(" • "),
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            "document" -> {
                val parts = item.rawString.split("|||")
                val dateStr = if (parts.size >= 2) parts[1].substringBefore(",") else "" // e.g. "09 Jul 2026"
                val sizeStr = if (parts.size >= 6) parts[5] else ""
                val ext = item.title.substringAfterLast('.').lowercase()
                
                val (iconColor, icon) = when (ext) {
                    "pdf" -> Pair(Color(0xFFEF5350), Icons.Default.PictureAsPdf)
                    "txt" -> Pair(Color(0xFF42A5F5), Icons.Default.Description)
                    else -> Pair(Color(0xFF29B6F6), Icons.Default.Article)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Row with Icon / Extension Tag
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(iconColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        // Small capsule showing extension
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = ext.uppercase().take(4),
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Bottom info block
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = item.cleanTitle,
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        val metaParts = listOfNotNull(
                            sizeStr.takeIf { it.isNotEmpty() },
                            dateStr.takeIf { it.isNotEmpty() }
                        )
                        Text(
                            text = metaParts.joinToString(" • "),
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            "note" -> {
                val parts = item.rawString.split("|||")
                val rawContent = if (parts.size >= 3) parts[2] else ""
                val cleanContent = rawContent.replace(Regex("<[^>]*>"), "")
                val dateStr = parts[0].substringBefore(",") // e.g. "09 Jul 2026"
                
                Column(
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                    ) {
                        Text(
                            text = item.title, 
                            color = Color.White.copy(alpha = 0.95f), 
                            fontSize = 14.sp, 
                            fontWeight = FontWeight.Bold, 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis
                        )
                        if (cleanContent.isNotBlank()) {
                            Text(
                                text = cleanContent, 
                                color = Color.White.copy(alpha = 0.55f), 
                                fontSize = 11.sp, 
                                fontWeight = FontWeight.Normal, 
                                maxLines = 3, 
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 15.sp
                            )
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateStr,
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (item.isFav) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Favorite",
                                tint = Color(0xFFFFD600),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // Overlay removed for cleaner gallery view. Duration is handled inside the video Box.
        
        // Favorite Badge
        if (item.isFav && !isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Favorite",
                    tint = Color(0xFFFFD600),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Pinned Badge
        if (isPinned && !isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = "Pinned",
                    tint = themePurple,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Selection Overlay
        if (isSelectionMode) {
            val overlayAlpha by animateFloatAsState(if (isSelected) 0.4f else 0.6f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isSelected) themePurple.copy(alpha=overlayAlpha) else Color.Black.copy(alpha=overlayAlpha))
            )
            
            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = themePurple,
                    modifier = Modifier.size(28.dp).background(Color.White, CircleShape)
                )
            }
            
            if (!isSelected) {
                Icon(
                    Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Select",
                    tint = Color.White.copy(alpha=0.9f),
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(28.dp)
                )
            }
        }
    }
}

@Composable
fun VideoDurationText(path: String, modifier: Modifier = Modifier) {
    var durationText by remember { mutableStateOf("") }

    LaunchedEffect(path) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(path)
                val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                val timeInMillis = time?.toLongOrNull() ?: 0L
                if (timeInMillis > 0) {
                    val sec = (timeInMillis / 1000) % 60
                    val min = (timeInMillis / 1000) / 60
                    durationText = String.format(Locale.getDefault(), "%02d:%02d", min, sec)
                }
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    if (durationText.isNotEmpty()) {
        Text(
            text = durationText, 
            color = Color.White, 
            fontSize = 11.sp, 
            fontWeight = FontWeight.ExtraBold, 
            modifier = modifier,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun SelectionActionButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(26.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PremiumVaultEmptyState(
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptySubtitle: String,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    val ThemePurple = LocalAppThemeColors.current.themePurple
    val infiniteTransition = rememberInfiniteTransition(label = "PremiumPulse")

    // Ripple wave 1: expands and fades
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RippleScale"
    )
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RippleAlpha"
    )

    // Breathing middle ring
    val middleScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "MiddleScale"
    )

    // Core pulsing button
    val coreScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CoreScale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(bottom = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(190.dp),
            contentAlignment = Alignment.Center
        ) {
            // 1. Ripple wave ring
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .scale(rippleScale)
                    .clip(CircleShape)
                    .background(ThemePurple.copy(alpha = rippleAlpha * 0.08f))
                    .border(1.dp, ThemePurple.copy(alpha = rippleAlpha * 0.25f), CircleShape)
            )

            // 2. Middle breathing ring
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(middleScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                ThemePurple.copy(alpha = 0.15f),
                                ThemePurple.copy(alpha = 0.04f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(1.dp, ThemePurple.copy(alpha = 0.08f), CircleShape)
            )

            // 3. Inner core solid button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(coreScale)
                    .clip(CircleShape)
                    .background(Color(0xFF141A29))
                    .border(1.5.dp, ThemePurple.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Glow accent behind the icon inside the inner core
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    ThemePurple.copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Icon(
                    imageVector = emptyIcon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = ThemePurple
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Title text
        Text(
            text = if (searchQuery.isNotEmpty()) "No results found" else emptyTitle,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle text
        Text(
            text = if (searchQuery.isNotEmpty()) "Try adjusting your search terms." else emptySubtitle,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

