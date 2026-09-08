package com.example

import android.content.Context
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Phase 6.1 & 6.2 — Secret Vault Integration Foundation & Native Browser Upload
 * Centralized integration layer and abstractions for Vault downloads and uploads.
 */

enum class DownloadDestination {
    DEVICE,
    SECRET_VAULT
}

enum class BrowserFileSource {
    SECRET_VAULT,
    GALLERY_PHOTOS,
    DEVICE_FILES
}

enum class BrowserUploadType {
    MEDIA_UPLOAD,
    DOCUMENT_FILE_UPLOAD
}

class BrowserUploadRequest(
    val mimeTypes: List<String>,
    val isMultiple: Boolean,
    val uploadType: BrowserUploadType,
    onResult: (List<android.net.Uri>?) -> Unit
) {
    private val isCompleted = java.util.concurrent.atomic.AtomicBoolean(false)
    private val callback = onResult

    val onResult: (List<android.net.Uri>?) -> Unit = { uris ->
        if (isCompleted.compareAndSet(false, true)) {
            callback(uris)
        }
    }
}

/**
 * Hook for premium entitlements. Phase 11 will implement this.
 */
interface PremiumEntitlementProvider {
    fun hasPremiumEntitlement(): Boolean
}

object VaultBrowserIntegration {
    // Phase 11 can register a real premium check here
    var premiumProvider: PremiumEntitlementProvider? = null

    // Phase 6.2 Active upload prompt request state
    var activeUploadRequest by mutableStateOf<BrowserUploadRequest?>(null)

    /**
     * Resolves the actual download destination.
     * Architectural integration point: allow direct download to preferred destination (DEVICE or SECRET_VAULT)
     * without premium restrictions.
     */
    fun resolveDestination(preferred: DownloadDestination): DownloadDestination {
        return preferred
    }

    /**
     * Determines whether the requested MIME types are strictly image/video (MEDIA_UPLOAD)
     * or document files (DOCUMENT_FILE_UPLOAD).
     */
    fun determineUploadType(mimeTypes: List<String>): BrowserUploadType {
        if (mimeTypes.isEmpty()) return BrowserUploadType.DOCUMENT_FILE_UPLOAD
        val isAllMedia = mimeTypes.all { type ->
            val t = type.lowercase().trim()
            t.startsWith("image/") || t.startsWith("video/")
        }
        return if (isAllMedia) BrowserUploadType.MEDIA_UPLOAD else BrowserUploadType.DOCUMENT_FILE_UPLOAD
    }

    /**
     * Internal secure sharing: Expose a secure temporary content URI for a Vault file.
     * Webpages and other internal components can stream the content safely.
     */
    fun getSecureUriForVaultFile(context: Context, path: String, name: String): android.net.Uri? {
        return prepareVaultFileForUpload(context, path, name)
    }

    /**
     * Internal secure sharing: Imports a file from a content/browser URI directly into the secure Vault.
     * Reuses the existing Vault storage and encryption pipeline cleanly.
     */
    fun importBrowserFileToVault(context: Context, uri: android.net.Uri, viewModel: CalculatorViewModel): Boolean {
        return try {
            viewModel.addVaultFile(context, uri, skipDelete = true)
        } catch (e: Exception) {
            android.util.Log.e("VaultBrowserIntegration", "Failed to import browser file to Vault safely", e)
            false
        }
    }
}

/**
 * Handles the storage delegation for browser downloads based on destination.
 */
class VaultDownloadHandler(private val context: Context) {
    /**
     * Determines where and how the downloaded bytes are saved.
     * Reuses existing Vault / device storage APIs.
     */
    fun saveDownloadedFile(
        filename: String,
        mimeType: String,
        bytes: ByteArray,
        destination: DownloadDestination,
        deviceSaver: (String, String, ByteArray) -> String?,
        vaultSaver: (String, String, ByteArray) -> String?
    ): String? {
        val resolvedDest = VaultBrowserIntegration.resolveDestination(destination)
        return when (resolvedDest) {
            DownloadDestination.SECRET_VAULT -> {
                // Future premium path: download directly into the Secret Vault
                vaultSaver(filename, mimeType, bytes)
            }
            DownloadDestination.DEVICE -> {
                // Free/default path: download to device public/app storage
                deviceSaver(filename, mimeType, bytes)
            }
        }
    }

    /**
     * File-based streaming overload: Moves or copies directly from a streaming disk file into Vault
     * or app downloads without buffering byte arrays into memory.
     */
    fun saveDownloadedFile(
        filename: String,
        mimeType: String,
        sourceFile: File,
        destination: DownloadDestination,
        deviceFileSaver: (String, String, File) -> String?,
        vaultFileSaver: (String, String, File) -> String?
    ): String? {
        val resolvedDest = VaultBrowserIntegration.resolveDestination(destination)
        return when (resolvedDest) {
            DownloadDestination.SECRET_VAULT -> {
                vaultFileSaver(filename, mimeType, sourceFile)
            }
            DownloadDestination.DEVICE -> {
                deviceFileSaver(filename, mimeType, sourceFile)
            }
        }
    }
}

/**
 * Abstraction for future uploads from different file sources.
 */
interface VaultUploadProvider {
    /**
     * Prepares/resolves files for upload from selected sources.
     * Ensures vault-origin files remain secure.
     */
    fun resolveUploadSource(source: BrowserFileSource, onFileSelected: (File?, String?) -> Unit)
}

/**
 * Foundation implementation of VaultUploadProvider.
 */
class DefaultVaultUploadProvider(private val context: Context) : VaultUploadProvider {
    override fun resolveUploadSource(source: BrowserFileSource, onFileSelected: (File?, String?) -> Unit) {
        when (source) {
            BrowserFileSource.SECRET_VAULT -> {
                // Foundation: Integration point to retrieve a secure, isolated file from the Vault
                // This will be wired up to the Vault media/document browser in a secure manner
                onFileSelected(null, null)
            }
            BrowserFileSource.GALLERY_PHOTOS -> {
                // Native photo picker trigger foundation
                onFileSelected(null, null)
            }
            BrowserFileSource.DEVICE_FILES -> {
                // Native SAF/file manager selection foundation
                onFileSelected(null, null)
            }
        }
    }
}

/**
 * Utility function to prepare a vault file for secure browser upload.
 * It copies/streams the file safely to the app's cache directory using a temporary file
 * and returns a secure FileProvider URI, protecting the original physical path.
 */
fun prepareVaultFileForUpload(context: Context, originalPath: String, originalName: String): android.net.Uri? {
    return try {
        val originalFile = File(originalPath)
        if (!originalFile.exists() || !originalFile.canRead()) {
            android.util.Log.e("VaultUpload", "Vault file does not exist or is unreadable: $originalPath")
            return null
        }
        val tempDir = File(context.cacheDir, "temp_browser_uploads")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        val sanitizedName = File(originalName).name
        val uniqueName = "temp_upload_${System.currentTimeMillis()}_$sanitizedName"
        val tempFile = File(tempDir, uniqueName)
        
        originalFile.inputStream().use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output) // Secure, memory-safe chunked streaming
            }
        }
        tempFile.setReadable(true, false)
        tempFile.setWritable(true, false)
        
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            tempFile
        )
        try {
            context.grantUriPermission(
                context.packageName,
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val webPackages = listOf(
                "com.google.android.webview",
                "com.android.webview",
                "com.android.chrome",
                "org.mozilla.geckoview"
            )
            webPackages.forEach { pkg ->
                try {
                    context.grantUriPermission(
                        pkg,
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
        uri
    } catch (e: Exception) {
        android.util.Log.e("VaultUpload", "Failed to prepare vault file for upload", e)
        null
    }
}

/**
 * Utility function to prepare freshly captured camera media for secure browser upload.
 * It copies the media safely to the temp_browser_uploads directory with a standardized
 * display name, generates the FileProvider URI, and grants temporary read permission.
 */
fun prepareCapturedMediaForUpload(context: Context, capturedFile: File, mimeType: String): android.net.Uri? {
    return try {
        if (!capturedFile.exists() || !capturedFile.canRead()) {
            android.util.Log.e("VaultUpload", "Captured file does not exist or is unreadable: ${capturedFile.absolutePath}")
            return null
        }
        val tempDir = File(context.cacheDir, "temp_browser_uploads")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        val isVideo = mimeType.startsWith("video/")
        val ext = if (isVideo) "mp4" else "jpg"
        val prefix = if (isVideo) "Video" else "Photo"
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val displayName = "${prefix}_$timestamp.$ext"
        val uniqueName = "upload_${System.currentTimeMillis()}_$displayName"
        val tempFile = File(tempDir, uniqueName)

        capturedFile.inputStream().use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        tempFile.setReadable(true, false)
        tempFile.setWritable(true, false)

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            tempFile
        )
        try {
            context.grantUriPermission(
                context.packageName,
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val webPackages = listOf(
                "com.google.android.webview",
                "com.android.webview",
                "com.android.chrome",
                "org.mozilla.geckoview"
            )
            webPackages.forEach { pkg ->
                try {
                    context.grantUriPermission(
                        pkg,
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
        uri
    } catch (e: Exception) {
        android.util.Log.e("VaultUpload", "Failed to prepare captured media for upload", e)
        null
    }
}

/**
 * Automatically cleans up temporary browser upload files to satisfy temporary access guidelines.
 * Keeps recently created uploads, but deletes anything older than 5 minutes.
 */
fun cleanTemporaryUploads(context: Context, forceAll: Boolean = false) {
    try {
        val tempDir = File(context.cacheDir, "temp_browser_uploads")
        if (tempDir.exists()) {
            val now = System.currentTimeMillis()
            tempDir.listFiles()?.forEach { file ->
                if (forceAll || (now - file.lastModified() > 300_000)) {
                    file.delete()
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("VaultUpload", "Failed to clean temporary uploads", e)
    }
}

@Composable
fun BrowserUploadSourceDialog(
    viewModel: CalculatorViewModel
) {
    val activeUpload = VaultBrowserIntegration.activeUploadRequest ?: return
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        cleanTemporaryUploads(context)
    }

    val LightBg = Color(0xFFF8F9FA)
    val LightCard = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF111111)
    val TextSecondary = Color(0xFF666666)
    val BorderColor = Color(0xFFE8E8E8)
    val AccentColor = Color(0xFFFF6A00)
    val DangerColor = Color(0xFFC62828)
    val SuccessColor = Color(0xFF2E7D32)

    val mimeFilter = if (activeUpload.mimeTypes.isNotEmpty()) {
        val f = activeUpload.mimeTypes.first().trim()
        if (f.isEmpty()) "*/*" else f
    } else {
        "*/*"
    }

    val singleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.isPickingFile = false
        viewModel.updateLastInteraction()
        if (uri != null) {
            activeUpload.onResult(listOf(uri))
        } else {
            activeUpload.onResult(null)
        }
        VaultBrowserIntegration.activeUploadRequest = null
    }

    val multipleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        viewModel.isPickingFile = false
        viewModel.updateLastInteraction()
        if (uris != null && uris.isNotEmpty()) {
            activeUpload.onResult(uris)
        } else {
            activeUpload.onResult(null)
        }
        VaultBrowserIntegration.activeUploadRequest = null
    }

    val tempPhotoFile = remember {
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        File(cacheDir, "upload_temp_capture.jpg").apply {
            parentFile?.mkdirs()
        }
    }
    val tempPhotoUri = remember {
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            tempPhotoFile
        )
    }
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        viewModel.isPickingFile = false
        viewModel.updateLastInteraction()
        if (success) {
            activeUpload.onResult(listOf(tempPhotoUri))
        } else {
            activeUpload.onResult(null)
        }
        VaultBrowserIntegration.activeUploadRequest = null
    }

    val tempVideoFile = remember {
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        File(cacheDir, "upload_temp_capture.mp4").apply {
            parentFile?.mkdirs()
        }
    }
    val tempVideoUri = remember {
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            tempVideoFile
        )
    }
    val recordVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        viewModel.isPickingFile = false
        viewModel.updateLastInteraction()
        if (success) {
            activeUpload.onResult(listOf(tempVideoUri))
        } else {
            activeUpload.onResult(null)
        }
        VaultBrowserIntegration.activeUploadRequest = null
    }

    var dialogSubScreen by remember { mutableStateOf("home") }
    val vaultFiles by viewModel.vaultFiles.collectAsStateWithLifecycle()
    val selectedVaultFiles = remember { mutableStateListOf<String>() }
    val isImageAllowed = mimeFilter.contains("image") || mimeFilter == "*/*"
    val isVideoAllowed = mimeFilter.contains("video") || mimeFilter == "*/*"
    val isOnlyVideo = isVideoAllowed && !isImageAllowed

    if (dialogSubScreen == "camera") {
        SecureCameraView(
            viewModel = viewModel,
            onDismiss = {
                activeUpload.onResult(null)
                VaultBrowserIntegration.activeUploadRequest = null
                dialogSubScreen = "home"
            },
            onMediaCaptured = { capturedFile, mimeType ->
                val uri = prepareCapturedMediaForUpload(context, capturedFile, mimeType)
                if (uri != null) {
                    activeUpload.onResult(listOf(uri))
                } else {
                    activeUpload.onResult(null)
                }
                VaultBrowserIntegration.activeUploadRequest = null
                dialogSubScreen = "home"
            },
            initialVideoMode = isOnlyVideo
        )
    } else if (!viewModel.isPickingFile) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                if (!viewModel.isPickingFile) {
                    viewModel.updateLastInteraction()
                    activeUpload.onResult(null)
                    VaultBrowserIntegration.activeUploadRequest = null
                }
            }
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (dialogSubScreen == "home") {
                        Text(
                            text = "Choose Upload Source",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Select a file to upload to the website.",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Option 1: Vault Storage
                        Button(
                            onClick = { dialogSubScreen = "vault" },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("upload_source_vault"),
                            colors = ButtonDefaults.buttonColors(containerColor = LightBg),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderColor),
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lock, null, tint = AccentColor, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Secure Vault Storage", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("Upload files currently encrypted inside vault", color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }

                        // Option 2: Camera Capture (Photo/Video)
                        val isImageAllowed = mimeFilter.contains("image") || mimeFilter == "*/*"
                        val isVideoAllowed = mimeFilter.contains("video") || mimeFilter == "*/*"
                        if (isImageAllowed || isVideoAllowed) {
                            Button(
                                onClick = {
                                    dialogSubScreen = "camera"
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("upload_source_camera"),
                                colors = ButtonDefaults.buttonColors(containerColor = LightBg),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, BorderColor),
                                contentPadding = PaddingValues(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PhotoCamera, null, tint = SuccessColor, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        val captureText = if (isImageAllowed && isVideoAllowed) "Camera Capture (Photo/Video)" else if (isImageAllowed) "Camera Capture (Photo)" else "Camera Capture (Video)"
                                        Text(captureText, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text("Capture a new photo or record video directly", color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // Option 3: Public System Files
                        Button(
                            onClick = {
                                viewModel.isPickingFile = true
                                if (activeUpload.isMultiple) {
                                    multipleLauncher.launch(mimeFilter)
                                } else {
                                    singleLauncher.launch(mimeFilter)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("upload_source_public"),
                            colors = ButtonDefaults.buttonColors(containerColor = LightBg),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderColor),
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Public System Files", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("Select public files from system photo/file picker", color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                viewModel.isPickingFile = false
                                viewModel.updateLastInteraction()
                                activeUpload.onResult(null)
                                VaultBrowserIntegration.activeUploadRequest = null
                            },
                            modifier = Modifier.align(Alignment.End).testTag("upload_source_cancel")
                        ) {
                            Text("Cancel", color = DangerColor)
                        }
                    } else if (dialogSubScreen == "vault") {
                    Text(
                        text = "Secure Vault Files",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = if (activeUpload.isMultiple) "Select one or more files to upload." else "Select a file to upload.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val requestedMimes = activeUpload.mimeTypes.map { it.lowercase().trim() }
                    val filteredVaultFiles = remember(vaultFiles, requestedMimes) {
                        if (requestedMimes.isEmpty() || requestedMimes.any { it == "*/*" || it.isEmpty() }) {
                            vaultFiles
                        } else {
                            vaultFiles.filter { fileSerialized ->
                                val parts = fileSerialized.split("|||")
                                if (parts.size >= 4) {
                                    val mime = parts[3].lowercase().trim()
                                    requestedMimes.any { requested ->
                                        if (requested.endsWith("/*")) {
                                            val prefix = requested.substringBefore("/")
                                            mime.startsWith("$prefix/")
                                        } else {
                                            mime == requested
                                        }
                                    }
                                } else {
                                    false
                                }
                            }
                        }
                    }

                    if (filteredVaultFiles.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No matching files in your secure vault.", color = TextSecondary, fontSize = 14.sp)
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                        ) {
                            items(filteredVaultFiles.size) { index ->
                                val fileSerialized = filteredVaultFiles[index]
                                val parts = fileSerialized.split("|||")
                                if (parts.size >= 5) {
                                    val id = parts[0]
                                    val name = parts[2]
                                    val mime = parts[3]
                                    val path = parts[4]
                                    val size = parts[5]

                                    val isSelected = selectedVaultFiles.contains(fileSerialized)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (activeUpload.isMultiple) {
                                                    if (isSelected) {
                                                        selectedVaultFiles.remove(fileSerialized)
                                                    } else {
                                                        selectedVaultFiles.add(fileSerialized)
                                                    }
                                                } else {
                                                    val uri = prepareVaultFileForUpload(context, path, name)
                                                    if (uri != null) {
                                                        activeUpload.onResult(listOf(uri))
                                                    } else {
                                                        activeUpload.onResult(null)
                                                    }
                                                    VaultBrowserIntegration.activeUploadRequest = null
                                                }
                                            }
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (mime.startsWith("image/")) Icons.Default.Image else Icons.Default.InsertDriveFile,
                                            contentDescription = null,
                                            tint = AccentColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                            Text("$size • $mime", color = TextSecondary, fontSize = 11.sp)
                                        }
                                        if (activeUpload.isMultiple) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    if (checked) {
                                                        selectedVaultFiles.add(fileSerialized)
                                                    } else {
                                                        selectedVaultFiles.remove(fileSerialized)
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = AccentColor)
                                            )
                                        }
                                    }
                                    androidx.compose.material3.HorizontalDivider(color = BorderColor)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { dialogSubScreen = "home" }
                        ) {
                            Text("Back", color = TextSecondary)
                        }
                        if (activeUpload.isMultiple && filteredVaultFiles.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val uris = selectedVaultFiles.mapNotNull { fileSerialized ->
                                        val parts = fileSerialized.split("|||")
                                        if (parts.size >= 5) {
                                            val name = parts[2]
                                            val path = parts[4]
                                            prepareVaultFileForUpload(context, path, name)
                                        } else null
                                    }
                                    activeUpload.onResult(uris.ifEmpty { null })
                                    VaultBrowserIntegration.activeUploadRequest = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                            ) {
                                Text("Upload (${selectedVaultFiles.size})", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
}
