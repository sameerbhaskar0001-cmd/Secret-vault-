package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import com.example.ui.theme.LocalAppThemeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataCleanerScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeColors = LocalAppThemeColors.current
    val ThemePurple = themeColors.themePurple
    val TextMedium = themeColors.textMedium

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var originalName by remember { mutableStateOf("") }
    var originalSize by remember { mutableStateOf(0L) }
    var originalMetadata by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    
    var isCleaning by remember { mutableStateOf(false) }
    var cleanedFile by remember { mutableStateOf<File?>(null) }
    var cleanedFileSize by remember { mutableStateOf(0L) }
    var cleanedMetadata by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    
    var showSuccessCard by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(0) } // 0 = Metadata, 1 = Preview/Compare

    // Reset when selecting a new file
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            cleanedFile = null
            cleanedFileSize = 0L
            cleanedMetadata = emptyMap()
            showSuccessCard = false
            currentTab = 0
            
            // Extract original image info & metadata
            val (name, size) = getFileInfo(context, uri)
            originalName = name
            originalSize = size
            originalMetadata = extractMetadata(context, uri)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header Row
            Row(
                 modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF161B2B).copy(alpha = 0.95f))
                        .border(
                            width = 1.2.dp,
                            color = Color.White.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                        .testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "EXIF TRACKING REMOVER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemePurple.copy(alpha = 0.8f),
                        letterSpacing = 1.2.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Metadata Cleaner",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                
                // Security Badge (Offline indication)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF4CAF50).copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Offline Secure",
                        tint = Color(0xFF81C784),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Local",
                        color = Color(0xFF81C784),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedUri == null) {
                    // Empty/Onboarding State Card
                    UnifiedGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        bgColor = Color(0xFF1B2031).copy(alpha = 0.95f),
                        elevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(ThemePurple.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    contentDescription = "Cleaner logo",
                                    tint = ThemePurple,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Scrub Hidden Metadata",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Photos contain sensitive EXIF tags like camera details, GPS coordinates, capture date, and device model. Remove them instantly before sharing or saving.",
                                    color = TextMedium,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }

                            Button(
                                onClick = { pickerLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4C1D95),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("select_image_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Photo, contentDescription = null, tint = Color.White)
                                    Text(
                                        text = "Select Photo", 
                                        color = Color.White, 
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Explanatory info boxes
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PrivacyFeatureRow(
                            icon = Icons.Default.MyLocation,
                            title = "Remove GPS Coordinates",
                            desc = "Stops people from tracking exactly where you took the photo."
                        )
                        PrivacyFeatureRow(
                            icon = Icons.Default.CalendarToday,
                            title = "Erase Date & Time",
                            desc = "Hides exact capture details for complete timing privacy."
                        )
                        PrivacyFeatureRow(
                            icon = Icons.Default.Smartphone,
                            title = "Device & Camera Data",
                            desc = "Clears device manufacturer, camera settings, and software tags."
                        )
                    }
                } else {
                    // Image selected state
                    UnifiedGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        bgColor = Color(0xFF1B2031).copy(alpha = 0.95f),
                        elevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Selected file meta row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AsyncImage(
                                    model = selectedUri,
                                    contentDescription = "Selected image preview",
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = originalName,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Original Size: ${viewModel.formatFileSize(originalSize)}",
                                        color = TextMedium,
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(
                                    onClick = { selectedUri = null },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red.copy(alpha = 0.1f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove chosen image",
                                        tint = Color(0xFFEF5350),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            
                            // Toggle tabs for Metadata details / Comparison
                            if (cleanedFile != null) {
                                TabRow(
                                    selectedTabIndex = currentTab,
                                    containerColor = Color.Transparent,
                                    contentColor = ThemePurple,
                                    divider = {},
                                    indicator = { tabPositions ->
                                        TabRowDefaults.Indicator(
                                            modifier = Modifier.tabIndicatorOffset(tabPositions[currentTab]),
                                            color = ThemePurple,
                                            height = 3.dp
                                        )
                                    }
                                ) {
                                    Tab(
                                        selected = currentTab == 0,
                                        onClick = { currentTab = 0 },
                                        text = { Text("Metadata", fontWeight = FontWeight.SemiBold) }
                                    )
                                    Tab(
                                        selected = currentTab == 1,
                                        onClick = { currentTab = 1 },
                                        text = { Text("Comparison", fontWeight = FontWeight.SemiBold) }
                                    )
                                }
                            }

                            if (currentTab == 0) {
                                // Metadata lists
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = if (cleanedFile == null) "Available Metadata Tags" else "Original Metadata Tags",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    if (originalMetadata.isEmpty() || originalMetadata.all { it.key == "Resolution" }) {
                                        // Resolution is usually preserved or inherent, but removable EXIF tags are missing
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color.White.copy(alpha = 0.03f))
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF81C784),
                                                    modifier = Modifier.size(32.dp)
                                                )
                                                Text(
                                                    text = "No Removable Metadata Found",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "This image is already clean of EXIF tracking tags.",
                                                    color = TextMedium,
                                                    fontSize = 11.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color.White.copy(alpha = 0.03f))
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            originalMetadata.forEach { (key, value) ->
                                                MetadataItemRow(
                                                    label = key,
                                                    value = value,
                                                    icon = when (key) {
                                                        "Camera/Model" -> Icons.Default.Smartphone
                                                        "Date/Time" -> Icons.Default.CalendarToday
                                                        "GPS Location" -> Icons.Default.MyLocation
                                                        "Device Software" -> Icons.Default.Info
                                                        else -> Icons.Default.Info
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Display cleaned status if cleaned
                                    if (cleanedFile != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Cleaned Metadata Status",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0xFF4CAF50).copy(alpha = 0.1f))
                                                .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Verified,
                                                    contentDescription = null,
                                                    tint = Color(0xFF81C784),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = "100% Privacy Cleaned",
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "All GPS, Device settings, camera parameters, and timestamps have been fully purged.",
                                                        color = TextMedium,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Compare original vs cleaned
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Original Compare
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color.White.copy(alpha = 0.03f))
                                                .padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "ORIGINAL",
                                                color = Color.Red.copy(alpha = 0.7f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            AsyncImage(
                                                model = selectedUri,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = viewModel.formatFileSize(originalSize),
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "${originalMetadata.filter { it.key != "Resolution" }.size} risk tags",
                                                    color = TextMedium,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        // Cleaned Compare
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0xFF4CAF50).copy(alpha = 0.05f))
                                                .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                                                .padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "CLEANED COPY",
                                                color = Color(0xFF81C784),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            AsyncImage(
                                                model = cleanedFile,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = viewModel.formatFileSize(cleanedFileSize),
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "0 risk tags (Purged)",
                                                    color = Color(0xFF81C784),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Clean/Purge Metadata action button (when not cleaned yet)
                            if (cleanedFile == null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        isCleaning = true
                                        scope.launch(Dispatchers.Default) {
                                            try {
                                                val result = cleanMetadataLocally(context, selectedUri!!, originalName)
                                                if (result != null) {
                                                    cleanedFile = result
                                                    cleanedFileSize = result.length()
                                                    cleanedMetadata = emptyMap() // verify it's cleared
                                                    
                                                    // Save to the private Calculator Vault directly
                                                    viewModel.registerDirectVaultFile(
                                                        context = context,
                                                        file = result,
                                                        originalName = "cleaned_${originalName}",
                                                        mimeType = "image/jpeg"
                                                    )
                                                    
                                                    val prefs = context.getSharedPreferences("exchange_calc_prefs", android.content.Context.MODE_PRIVATE)
                                                    if (!prefs.getBoolean("first_metadata_cleaned", false)) {
                                                        prefs.edit()
                                                            .putBoolean("first_metadata_cleaned", true)
                                                            .putLong("time_metadata_cleaned", System.currentTimeMillis())
                                                            .apply()
                                                    }
                                                    showSuccessCard = true
                                                } else {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Could not clean metadata", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                }
                                            } finally {
                                                isCleaning = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4C1D95),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isCleaning,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("clean_metadata_button")
                                ) {
                                    if (isCleaning) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color.White)
                                            Text("Purge & Save Privately", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Show gorgeous Success card when process completes
                AnimatedVisibility(
                    visible = showSuccessCard,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    UnifiedGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        bgColor = Color(0xFF132F23).copy(alpha = 0.95f),
                        elevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success checkmark",
                                    tint = Color(0xFF81C784),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Metadata Cleaned & Saved!",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "The original photo is intact on your device. The cleaned version has been safely imported into your private Calculator Vault.",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp
                                )
                            }

                            Button(
                                onClick = { 
                                    // Export to gallery
                                    val resolver = context.contentResolver
                                    val values = android.content.ContentValues().apply {
                                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "cleaned_${System.currentTimeMillis()}.jpg")
                                        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                                        }
                                    }
                                    val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                        android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                                    } else {
                                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                    }
                                    val itemUri = resolver.insert(collection, values)
                                    if (itemUri != null) {
                                        try {
                                            resolver.openOutputStream(itemUri)?.use { out ->
                                                cleanedFile?.inputStream()?.use { input ->
                                                    input.copyTo(out)
                                                }
                                            }
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                                values.clear()
                                                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                                                resolver.update(itemUri, values, null, null)
                                            }
                                            android.widget.Toast.makeText(context, "Saved to Gallery", android.widget.Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Failed to save", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export to Gallery", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { selectedUri = null },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("success_dismiss_button")
                                ) {
                                    Text(
                                        text = "New Photo", 
                                        color = Color.White, 
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Button(
                                    onClick = onBack,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF81C784)),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Text("Done", color = Color(0xFF132F23), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun PrivacyFeatureRow(
    icon: ImageVector,
    title: String,
    desc: String
) {
    val themeColors = LocalAppThemeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(themeColors.themePurple.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = themeColors.themePurple,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                color = themeColors.textMedium,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun MetadataItemRow(
    label: String,
    value: String,
    icon: ImageVector
) {
    val themeColors = LocalAppThemeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = themeColors.themePurple.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = themeColors.textMedium,
            fontSize = 12.sp,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}

// Extract EXIF Metadata safely from image Uri
fun extractMetadata(context: Context, uri: Uri): Map<String, String> {
    val metadata = mutableMapOf<String, String>()
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val exifInterface = ExifInterface(inputStream)
            
            val make = exifInterface.getAttribute(ExifInterface.TAG_MAKE)
            val model = exifInterface.getAttribute(ExifInterface.TAG_MODEL)
            if (!make.isNullOrBlank() || !model.isNullOrBlank()) {
                val cameraInfo = listOfNotNull(make, model).joinToString(" ")
                metadata["Camera/Model"] = cameraInfo
            }
            
            val dateTime = exifInterface.getAttribute(ExifInterface.TAG_DATETIME) ?:
                           exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            if (!dateTime.isNullOrBlank()) {
                metadata["Date/Time"] = dateTime
            }
            
            val latLong = exifInterface.latLong
            if (latLong != null && latLong.size == 2) {
                metadata["GPS Location"] = String.format(Locale.US, "%.5f, %.5f", latLong[0], latLong[1])
            }
            
            val software = exifInterface.getAttribute(ExifInterface.TAG_SOFTWARE)
            if (!software.isNullOrBlank()) {
                metadata["Device Software"] = software
            }
            
            val width = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
            val height = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)
            if (!width.isNullOrBlank() && !height.isNullOrBlank() && width != "0") {
                metadata["Resolution"] = "${width} x ${height}"
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return metadata
}

// Get basic filename and filesize
fun getFileInfo(context: Context, uri: Uri): Pair<String, Long> {
    var name = "Image"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIdx != -1) {
                    name = cursor.getString(nameIdx) ?: "Image"
                }
                val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIdx != -1) {
                    size = cursor.getLong(sizeIdx)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    // Fallback name if query returned empty/generic name
    if (name == "Image" || name.isBlank()) {
        name = "img_${System.currentTimeMillis()}.jpg"
    }
    
    return Pair(name, size)
}

// Clean all metadata locally by decoding the image to bitmap, then compressing without Exif tags
suspend fun cleanMetadataLocally(context: Context, uri: Uri, originalName: String): File? {
    return withContext(Dispatchers.IO) {
        try {
            // Load original bitmap
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()
            
            if (bitmap == null) return@withContext null
            
            // Generate clean target file inside private vault_files directory
            val vaultDir = File(context.filesDir, "vault_files")
            if (!vaultDir.exists()) {
                vaultDir.mkdirs()
            }
            
            val extension = File(originalName).extension.ifEmpty { "jpg" }
            val cleanedFileName = "${System.currentTimeMillis()}.$extension"
            val destFile = File(vaultDir, cleanedFileName)
            
            // Compress and save bitmap to output stream, completely stripping exif
            val outStream = FileOutputStream(destFile)
            val format = when (extension.lowercase()) {
                "png" -> Bitmap.CompressFormat.PNG
                "webp" -> Bitmap.CompressFormat.WEBP
                else -> Bitmap.CompressFormat.JPEG
            }
            
            val success = bitmap.compress(format, 95, outStream)
            outStream.flush()
            outStream.close()
            bitmap.recycle()
            
            if (success && destFile.exists()) {
                destFile
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
