package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.LocalAppThemeColors
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class VoiceNote(
    val id: String,
    val displayName: String,
    val fileName: String,
    val filePath: String,
    val timestamp: Long,
    val durationMs: Long,
    val fileSize: Long
)

private fun getVoiceNotesDir(context: Context): File {
    val dir = File(context.filesDir, "secure_voice_notes")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return dir
}

private fun loadVoiceNotes(context: Context): List<VoiceNote> {
    val dir = getVoiceNotesDir(context)
    val metadataFile = File(dir, "metadata.json")
    if (!metadataFile.exists()) return emptyList()
    
    return try {
        val jsonStr = metadataFile.readText()
        val jsonArray = JSONArray(jsonStr)
        val list = mutableListOf<VoiceNote>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                VoiceNote(
                    id = obj.getString("id"),
                    displayName = obj.getString("displayName"),
                    fileName = obj.getString("fileName"),
                    filePath = obj.getString("filePath"),
                    timestamp = obj.getLong("timestamp"),
                    durationMs = obj.getLong("durationMs"),
                    fileSize = obj.getLong("fileSize")
                )
            )
        }
        list.sortedByDescending { it.timestamp }
    } catch (e: Exception) {
        android.util.Log.e("SecureVoiceNote", "Failed to load voice notes metadata", e)
        emptyList()
    }
}

private fun saveVoiceNotes(context: Context, notes: List<VoiceNote>) {
    val dir = getVoiceNotesDir(context)
    val metadataFile = File(dir, "metadata.json")
    try {
        val jsonArray = JSONArray()
        for (note in notes) {
            val obj = JSONObject().apply {
                put("id", note.id)
                put("displayName", note.displayName)
                put("fileName", note.fileName)
                put("filePath", note.filePath)
                put("timestamp", note.timestamp)
                put("durationMs", note.durationMs)
                put("fileSize", note.fileSize)
            }
            jsonArray.put(obj)
        }
        metadataFile.writeText(jsonArray.toString(4))
    } catch (e: Exception) {
        android.util.Log.e("SecureVoiceNote", "Failed to save voice notes metadata", e)
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024f
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024f
    return String.format(Locale.US, "%.1f MB", mb)
}

fun shareVoiceNote(context: Context, note: VoiceNote) {
    try {
        val originalFile = File(note.filePath)
        if (!originalFile.exists()) {
            android.widget.Toast.makeText(context, "Voice recording file is missing.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // Copy to a temporary file in cacheDir so it remains private and doesn't get shown in public File Manager
        val tempDir = File(context.cacheDir, "shared_voice_notes")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        
        // Clean up old shared voice notes to save space
        tempDir.listFiles()?.forEach { file ->
            try {
                file.delete()
            } catch (e: Exception) {
                // ignore
            }
        }

        val safeDisplayName = note.displayName.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").trim()
        val shareFileName = if (safeDisplayName.isNotEmpty()) {
            "$safeDisplayName.m4a"
        } else {
            "voice_note_${note.id}.m4a"
        }
        
        val tempFile = File(tempDir, shareFileName)
        
        originalFile.inputStream().use { input ->
            java.io.FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        val shareUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            tempFile
        )

        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
            clipData = android.content.ClipData.newRawUri("Share Secure Voice Note", shareUri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = android.content.Intent.createChooser(shareIntent, "Share Secure Voice Note")
        chooserIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        chooserIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        
        val resInfoList = context.packageManager.queryIntentActivities(shareIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            context.grantUriPermission(packageName, shareUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        android.util.Log.e("SecureVoiceNote", "Failed to share voice note", e)
        android.widget.Toast.makeText(context, "Failed to share: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun AudioWaveformVisualizer(
    amplitudes: List<Float>,
    isRecording: Boolean,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val themeColors = LocalAppThemeColors.current
    val ThemePurple = themeColors.themePurple

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val barCount = 35
        val barWidth = (width / barCount) * 0.65f
        val gapWidth = (width / barCount) * 0.35f
        
        for (i in 0 until barCount) {
            val amp = if (isRecording && !isPaused) {
                if (i < amplitudes.size) amplitudes[i] else 0.05f
            } else {
                // Gentle ambient rest wave
                0.05f + (Math.sin(i.toDouble() * 0.35 + (System.currentTimeMillis() / 400.0)).toFloat().coerceAtLeast(0f) * 0.08f)
            }
            
            val barHeight = amp * height * 0.9f
            val x = i * (barWidth + gapWidth) + gapWidth / 2
            val y = (height - barHeight) / 2
            
            drawRoundRect(
                color = if (isRecording && !isPaused) ThemePurple else Color.White.copy(alpha = 0.2f),
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

@Composable
fun SecureVoiceNoteScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val themeColors = LocalAppThemeColors.current
    val ThemePurple = themeColors.themePurple
    val TextMedium = themeColors.textMedium
    val isWhiteTheme = ThemePurple.red > 0.9f && ThemePurple.green > 0.9f && ThemePurple.blue > 0.9f

    // Permissions
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasMicPermission = granted
        }
    )

    // Data lists & Search
    var voiceNotesList by remember { mutableStateOf<List<VoiceNote>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Load existing items initially
    LaunchedEffect(Unit) {
        voiceNotesList = loadVoiceNotes(context)
    }

    // MediaRecorder states
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var activeRecordingFile by remember { mutableStateOf<File?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isPausedRecording by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableStateOf(0) }
    var amplitudes by remember { mutableStateOf<List<Float>>(emptyList()) }

    // MediaPlayer states
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingNoteId by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playProgressMs by remember { mutableStateOf(0) }
    var totalDurationMs by remember { mutableStateOf(0) }

    // Dialogs & Toast states
    var noteToRename by remember { mutableStateOf<VoiceNote?>(null) }
    var renameInputText by remember { mutableStateOf("") }
    var noteToDelete by remember { mutableStateOf<VoiceNote?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    // Clean resource lifecycle on dispose
    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
            } catch (e: Exception) {}
            try {
                mediaPlayer?.apply {
                    stop()
                    release()
                }
            } catch (e: Exception) {}
        }
    }

    // Continuous update for playing progress
    LaunchedEffect(isPlaying, playingNoteId) {
        if (isPlaying && playingNoteId != null) {
            while (isPlaying && playingNoteId != null) {
                val currentPos = mediaPlayer?.currentPosition ?: 0
                playProgressMs = currentPos
                kotlinx.coroutines.delay(100)
            }
        }
    }

    // Continuous update for recording elapsed timer
    LaunchedEffect(isRecording, isPausedRecording) {
        if (isRecording && !isPausedRecording) {
            while (isRecording && !isPausedRecording) {
                kotlinx.coroutines.delay(1000)
                recordingDurationSec += 1
            }
        }
    }

    // Continuous update for visualizer amplitudes
    LaunchedEffect(isRecording, isPausedRecording) {
        if (isRecording && !isPausedRecording) {
            while (isRecording && !isPausedRecording) {
                val amp = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (e: Exception) {
                    0
                }
                val normalized = (amp.toFloat() / 32767f).coerceIn(0.02f, 1.0f)
                amplitudes = (amplitudes + normalized).takeLast(40)
                kotlinx.coroutines.delay(100)
            }
        }
    }

    // Playback functions
    fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            android.util.Log.e("SecureVoiceNote", "Failed to stop playback", e)
        } finally {
            mediaPlayer = null
            playingNoteId = null
            isPlaying = false
            playProgressMs = 0
            totalDurationMs = 0
        }
    }

    fun playVoiceNote(note: VoiceNote) {
        if (playingNoteId == note.id) {
            try {
                if (isPlaying) {
                    mediaPlayer?.pause()
                    isPlaying = false
                } else {
                    mediaPlayer?.start()
                    isPlaying = true
                }
            } catch (e: Exception) {
                android.util.Log.e("SecureVoiceNote", "Failed to play/pause note", e)
            }
            return
        }

        stopPlayback()

        val file = File(note.filePath)
        if (!file.exists()) {
            infoMessage = "Voice recording file is missing."
            return
        }

        val player = MediaPlayer()
        try {
            player.setDataSource(file.absolutePath)
            player.prepare()
            player.start()
            
            mediaPlayer = player
            playingNoteId = note.id
            isPlaying = true
            totalDurationMs = player.duration
            playProgressMs = 0

            player.setOnCompletionListener {
                stopPlayback()
            }
        } catch (e: Exception) {
            android.util.Log.e("SecureVoiceNote", "Failed to prepare MediaPlayer", e)
            infoMessage = "Cannot play recording."
        }
    }

    // Recording functions
    fun startRecording() {
        if (!hasMicPermission) {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            return
        }

        stopPlayback()

        val dir = getVoiceNotesDir(context)
        val id = UUID.randomUUID().toString()
        val file = File(dir, "rec_$id.m4a")
        activeRecordingFile = file

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true
            isPausedRecording = false
            recordingDurationSec = 0
            amplitudes = emptyList()
        } catch (e: Exception) {
            android.util.Log.e("SecureVoiceNote", "Failed to start MediaRecorder", e)
            infoMessage = "Microphone failed to record."
            isRecording = false
            activeRecordingFile = null
        }
    }

    fun togglePauseRecording() {
        if (mediaRecorder == null) return
        try {
            if (isPausedRecording) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    mediaRecorder?.resume()
                    isPausedRecording = false
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    mediaRecorder?.pause()
                    isPausedRecording = true
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SecureVoiceNote", "Error toggling pause", e)
        }
    }

    fun stopRecording() {
        if (mediaRecorder == null) return
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            android.util.Log.e("SecureVoiceNote", "Error stopping MediaRecorder", e)
        } finally {
            mediaRecorder = null
        }

        activeRecordingFile?.let { file ->
            if (file.exists() && file.length() > 0) {
                val id = file.nameWithoutExtension.removePrefix("rec_")
                val timestamp = System.currentTimeMillis()
                val durationMs = recordingDurationSec * 1000L
                val fileSize = file.length()

                val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
                val formattedDate = sdf.format(Date(timestamp))
                val defaultName = "Voice Note $formattedDate"

                val newNote = VoiceNote(
                    id = id,
                    displayName = defaultName,
                    fileName = file.name,
                    filePath = file.absolutePath,
                    timestamp = timestamp,
                    durationMs = durationMs,
                    fileSize = fileSize
                )

                voiceNotesList = (listOf(newNote) + voiceNotesList).sortedByDescending { it.timestamp }
                saveVoiceNotes(context, voiceNotesList)
                infoMessage = "Voice note saved inside secure vault!"
            } else {
                infoMessage = "Recording failed (no audio captured)."
            }
        }

        isRecording = false
        isPausedRecording = false
        recordingDurationSec = 0
        activeRecordingFile = null
    }

    // Filter voice notes by query
    val filteredNotes = voiceNotesList.filter {
        it.displayName.contains(searchQuery, ignoreCase = true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                        .testTag("voice_notes_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "PRIVATE RECORDER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemePurple.copy(alpha = 0.8f),
                        letterSpacing = 1.8.sp
                    )
                    Text(
                        text = "Secure Voice Note",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // MAIN INTERFACE
            if (!hasMicPermission) {
                // Mic permission request layout
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(ThemePurple.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MicOff,
                                    contentDescription = null,
                                    tint = ThemePurple,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Text(
                                text = "Microphone Access Required",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "To record private voice notes directly into your calculator vault, we need your permission to access the device's microphone.",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )

                            Button(
                                onClick = { permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("grant_mic_permission_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ThemePurple)
                            ) {
                                Text(
                                    text = "Grant Permission",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isWhiteTheme) Color(0xFF1B2031) else Color.White
                                )
                            }
                        }
                    }
                }
            } else {
                // Interactive private voice note recorder interface
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Recorder Console Card
                    UnifiedGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        bgColor = Color(0xFF161A29).copy(alpha = 0.95f),
                        elevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isRecording && !isPausedRecording) Color.Red else Color.Gray)
                                    )
                                    Text(
                                        text = if (isRecording) {
                                            if (isPausedRecording) "Recording Paused" else "Recording Audio..."
                                        } else "Recorder Ready",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Text(
                                    text = if (isRecording) formatDuration(recordingDurationSec * 1000L) else "00:00",
                                    color = if (isRecording && !isPausedRecording) Color.Red else Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Dynamic Waveform Visualizer
                            AudioWaveformVisualizer(
                                amplitudes = amplitudes,
                                isRecording = isRecording,
                                isPaused = isPausedRecording,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                            )

                            // Controls Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isRecording) {
                                    // Pause / Resume recording button
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        IconButton(
                                            onClick = { togglePauseRecording() },
                                            modifier = Modifier
                                                .padding(end = 16.dp)
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.08f))
                                                .testTag("pause_recording_button")
                                        ) {
                                            Icon(
                                                imageVector = if (isPausedRecording) Icons.Default.PlayArrow else Icons.Default.Pause,
                                                contentDescription = if (isPausedRecording) "Resume" else "Pause",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    // Stop recording button
                                    IconButton(
                                        onClick = { stopRecording() },
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(Color.Red)
                                            .testTag("stop_recording_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Stop & Save",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                } else {
                                    // Start recording button
                                    Button(
                                        onClick = { startRecording() },
                                        modifier = Modifier
                                            .height(54.dp)
                                            .fillMaxWidth(0.8f)
                                            .testTag("start_recording_button"),
                                        shape = RoundedCornerShape(27.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ThemePurple)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = null,
                                                tint = if (isWhiteTheme) Color(0xFF1B2031) else Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "Start Recording",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isWhiteTheme) Color(0xFF1B2031) else Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SEARCH AND HISTORIC LIST OF RECORDINGS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "SECURE VAULT RECORDINGS (${filteredNotes.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMedium,
                            modifier = Modifier.padding(start = 4.dp)
                        )

                        if (voiceNotesList.isNotEmpty()) {
                            Text(
                                text = "Local Storage Only",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF00E676).copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("recordings_search_bar"),
                        placeholder = { Text("Search recordings...", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ThemePurple,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedContainerColor = Color.White.copy(alpha = 0.03f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = ThemePurple
                        )
                    )

                    // Lazy list of secure recordings
                    if (filteredNotes.isEmpty()) {
                        PremiumVaultEmptyState(
                            emptyIcon = if (searchQuery.isNotEmpty()) Icons.Default.SearchOff else Icons.Default.Mic,
                            emptyTitle = if (searchQuery.isNotEmpty()) "No matching recordings" else "No secure voice notes saved yet",
                            emptySubtitle = if (searchQuery.isNotEmpty()) "Try searching a different name" else "Your voice notes are kept fully local and encrypted",
                            searchQuery = searchQuery,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredNotes, key = { it.id }) { note ->
                                val isSelectedNote = playingNoteId == note.id
                                
                                UnifiedGlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = if (isSelectedNote) 1.dp else 0.dp,
                                            color = if (isSelectedNote) ThemePurple.copy(alpha = 0.4f) else Color.Transparent,
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                        .testTag("voice_note_card_${note.id}"),
                                    shape = RoundedCornerShape(18.dp),
                                    bgColor = if (isSelectedNote) Color(0xFF1B2036).copy(alpha = 0.95f) else Color(0xFF141825).copy(alpha = 0.95f),
                                    elevation = 2.dp
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Top part (Details)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // Play control button
                                            IconButton(
                                                onClick = { playVoiceNote(note) },
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelectedNote && isPlaying) ThemePurple 
                                                        else ThemePurple.copy(alpha = 0.15f)
                                                    )
                                                    .testTag("play_note_toggle_${note.id}")
                                            ) {
                                                Icon(
                                                    imageVector = if (isSelectedNote && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Play/Pause",
                                                    tint = if (isSelectedNote && isPlaying) {
                                                        if (isWhiteTheme) Color(0xFF141825) else Color.White
                                                    } else ThemePurple,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            // Text description
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = note.displayName,
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = formatDuration(note.durationMs),
                                                        color = ThemePurple,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .size(3.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.White.copy(alpha = 0.3f))
                                                    )
                                                    Text(
                                                        text = formatFileSize(note.fileSize),
                                                        color = Color.White.copy(alpha = 0.4f),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }

                                            // Actions
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        shareVoiceNote(context, note)
                                                    },
                                                    modifier = Modifier.size(36.dp).testTag("share_note_button_${note.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Share,
                                                        contentDescription = "Share",
                                                        tint = Color.White.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        noteToRename = note
                                                        renameInputText = note.displayName
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Rename",
                                                        tint = Color.White.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { noteToDelete = note },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete",
                                                        tint = Color.Red.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Player Seek bar (Visible only if active/playing)
                                        if (isSelectedNote) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.Black.copy(alpha = 0.2f))
                                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                            ) {
                                                Slider(
                                                    value = playProgressMs.toFloat(),
                                                    onValueChange = { newValue ->
                                                        playProgressMs = newValue.toInt()
                                                        mediaPlayer?.seekTo(newValue.toInt())
                                                    },
                                                    valueRange = 0f..(if (totalDurationMs > 0) totalDurationMs.toFloat() else 100f),
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = Color.White,
                                                        activeTrackColor = ThemePurple,
                                                        inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(24.dp)
                                                        .testTag("playback_seek_bar")
                                                )
                                                
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 2.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = formatDuration(playProgressMs.toLong()),
                                                        color = Color.White.copy(alpha = 0.5f),
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(
                                                        text = formatDuration(totalDurationMs.toLong()),
                                                        color = Color.White.copy(alpha = 0.5f),
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // RENAME RECORDING DIALOG
        if (noteToRename != null) {
            AlertDialog(
                onDismissRequest = { noteToRename = null },
                title = { Text("Rename Secure Voice Note", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter a private name for this recording:", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        OutlinedTextField(
                            value = renameInputText,
                            onValueChange = { renameInputText = it },
                            modifier = Modifier.fillMaxWidth().testTag("rename_note_input"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ThemePurple,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                                cursorColor = ThemePurple
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val activeNote = noteToRename
                            if (activeNote != null && renameInputText.isNotBlank()) {
                                voiceNotesList = voiceNotesList.map {
                                    if (it.id == activeNote.id) it.copy(displayName = renameInputText) else it
                                }
                                saveVoiceNotes(context, voiceNotesList)
                                noteToRename = null
                                infoMessage = "Recording renamed successfully!"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ThemePurple),
                        enabled = renameInputText.isNotBlank()
                    ) {
                        Text("Save", color = if (isWhiteTheme) Color(0xFF1B2031) else Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { noteToRename = null }) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                    }
                },
                containerColor = Color(0xFF1B2031),
                shape = RoundedCornerShape(20.dp)
            )
        }

        // DELETE RECORDING DIALOG
        if (noteToDelete != null) {
            val note = noteToDelete!!
            AlertDialog(
                onDismissRequest = { noteToDelete = null },
                title = { Text("Delete Secure Voice Note?", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "Are you sure you want to permanently delete \"${note.displayName}\"? This action cannot be undone, and the recording will be deleted forever from this local vault.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (playingNoteId == note.id) {
                                stopPlayback()
                            }
                            try {
                                val file = File(note.filePath)
                                if (file.exists()) {
                                    file.delete()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("SecureVoiceNote", "Failed to delete file", e)
                            }
                            voiceNotesList = voiceNotesList.filter { it.id != note.id }
                            saveVoiceNotes(context, voiceNotesList)
                            noteToDelete = null
                            infoMessage = "Voice recording deleted permanently."
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { noteToDelete = null }) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                    }
                },
                containerColor = Color(0xFF1B2031),
                shape = RoundedCornerShape(20.dp)
            )
        }

        // FLOATING INFO BANNER TOASTS
        AnimatedVisibility(
            visible = infoMessage != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            LaunchedEffect(infoMessage) {
                if (infoMessage != null) {
                    kotlinx.coroutines.delay(2500)
                    infoMessage = null
                }
            }
            UnifiedGlassCard(
                shape = RoundedCornerShape(12.dp),
                bgColor = Color(0xFF1B2031).copy(alpha = 0.95f),
                elevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = ThemePurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = infoMessage ?: "",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
