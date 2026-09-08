package com.example

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.example.ui.theme.AppTheme
import java.io.FileOutputStream
import java.io.InputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale


enum class ApiStatus {
    IDLE, LOADING, SUCCESS, ERROR
}

enum class CurrencyField {
    USD, INR
}

data class Currency(
    val code: String,
    val name: String,
    val symbol: String,
    val emoji: String,
    val defaultUsdRate: Double
)


data class BrowserBookmark(val title: String, val url: String)
data class BrowserHistory(val title: String, val url: String, val timestamp: Long)

class CalculatorViewModel(application: Application) : AndroidViewModel(application) {
    val isCameraActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    val cameraTriggerFlow = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val browserTabs = androidx.compose.runtime.mutableStateListOf<com.example.TabState>()
    var isBrowserTabsLoaded by androidx.compose.runtime.mutableStateOf(false)
    var activeTabId by androidx.compose.runtime.mutableStateOf<String?>(null)

    // --- Audio Player State ---
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var currentAudioPath: String? = null
    
    private val _isAudioPlaying = MutableStateFlow(false)
    val isAudioPlaying: StateFlow<Boolean> = _isAudioPlaying.asStateFlow()
    
    private val _audioPosition = MutableStateFlow(0f)
    val audioPosition: StateFlow<Float> = _audioPosition.asStateFlow()
    
    private val _audioDuration = MutableStateFlow(1f)
    val audioDuration: StateFlow<Float> = _audioDuration.asStateFlow()
    
    private val _audioSpeed = MutableStateFlow(1.0f)
    val audioSpeed: StateFlow<Float> = _audioSpeed.asStateFlow()
    
    private var audioPositionJob: kotlinx.coroutines.Job? = null
    
    val currentAudioPlayingPath: String? get() = currentAudioPath

    fun setAudioSpeed(speed: Float) {
        _audioSpeed.value = speed
        try {
            mediaPlayer?.let { mp ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    try {
                        val params = mp.playbackParams.setSpeed(speed)
                        mp.playbackParams = params
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playOrToggleAudio(path: String, context: android.content.Context) {
        if (currentAudioPath == path && mediaPlayer != null) {
            if (_isAudioPlaying.value) {
                pauseAudio()
            } else {
                resumeAudio()
            }
            return
        }
        
        stopAudio()
        currentAudioPath = path
        try {
            mediaPlayer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.media.MediaPlayer(context.applicationContext)
            } else {
                android.media.MediaPlayer()
            }
            mediaPlayer?.setDataSource(path)
            mediaPlayer?.prepare()
            _audioDuration.value = (mediaPlayer?.duration ?: 1).toFloat()
            mediaPlayer?.setOnCompletionListener {
                _isAudioPlaying.value = false
                _audioPosition.value = 0f
                stopAudioPositionUpdates()
                try {
                    it.seekTo(0)
                } catch (e: Exception) {}
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    val params = mediaPlayer?.playbackParams?.setSpeed(_audioSpeed.value)
                    if (params != null) {
                        mediaPlayer?.playbackParams = params
                    }
                } catch (e: Exception) {}
            }
            mediaPlayer?.start()
            _isAudioPlaying.value = true
            startAudioPositionUpdates()
        } catch (e: Exception) {
            e.printStackTrace()
            currentAudioPath = null
        }
    }
    
    fun resumeAudio() {
        try {
            mediaPlayer?.start()
            _isAudioPlaying.value = true
            startAudioPositionUpdates()
        } catch (e: Exception) {}
    }

    fun pauseAudio() {
        try {
            mediaPlayer?.pause()
            _isAudioPlaying.value = false
            stopAudioPositionUpdates()
        } catch (e: Exception) {}
    }
    
    override fun onCleared() {
        super.onCleared()
        stopAudio()
        activeDownloadJobs.values.forEach { it.cancel() }
        activeDownloadJobs.clear()
        activeDownloadConnections.values.forEach { try { it.disconnect() } catch (e: Exception) {} }
        activeDownloadConnections.clear()
    }

    fun stopAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null
        _isAudioPlaying.value = false
        currentAudioPath = null
        _audioPosition.value = 0f
        stopAudioPositionUpdates()
    }
    
    fun seekAudio(position: Float) {
        try {
            mediaPlayer?.seekTo(position.toInt())
            _audioPosition.value = position
        } catch (e: Exception) {}
    }
    
    private fun startAudioPositionUpdates() {
        audioPositionJob?.cancel()
        audioPositionJob = viewModelScope.launch {
            while (true) {
                try {
                    _audioPosition.value = (mediaPlayer?.currentPosition ?: 0).toFloat()
                } catch (e: Exception) {}
                kotlinx.coroutines.delay(250)
            }
        }
    }
    
    private fun stopAudioPositionUpdates() {
        audioPositionJob?.cancel()
        audioPositionJob = null
    }

    private val _memoryPressureEvent = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val memoryPressureEvent: kotlinx.coroutines.flow.SharedFlow<Int> = _memoryPressureEvent

    fun handleMemoryPressure(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            _memoryPressureEvent.tryEmit(level)
        }
    }

    fun onAppBackgrounded() {
        if (!_backgroundAudioPlaybackEnabled.value) {
            pauseAudio()
        }
    }
    var browserCustomView by androidx.compose.runtime.mutableStateOf<android.view.View?>(null)
    var browserCustomViewCallback by androidx.compose.runtime.mutableStateOf<android.webkit.WebChromeClient.CustomViewCallback?>(null)


    private val prefs = application.getSharedPreferences("exchange_calc_prefs", Context.MODE_PRIVATE)
    val initialSystemTimezone: String = java.util.TimeZone.getDefault().id

    // --- Preferred Time Zone State ---
    private val _preferredTimezone = MutableStateFlow(prefs.getString("preferred_timezone", "System") ?: "System")
    val preferredTimezone: StateFlow<String> = _preferredTimezone.asStateFlow()

    fun setPreferredTimezone(tz: String) {
        prefs.edit().putString("preferred_timezone", tz).apply()
        _preferredTimezone.value = tz
        applyTimezone(tz)
    }

    fun applyTimezone(tz: String) {
        try {
            val tzId = if (tz == "System") initialSystemTimezone else tz
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(tzId))
        } catch (e: Exception) {}
    }

    // --- Supported Currencies list ---
    val currencies = listOf(
        Currency("USD", "US Dollar", "$", "🇺🇸", 1.0),
        Currency("INR", "Indian Rupee", "₹", "🇮🇳", 83.50),
        Currency("EUR", "Euro", "€", "🇪🇺", 0.92),
        Currency("GBP", "British Pound", "£", "🇬🇧", 0.79),
        Currency("JPY", "Japanese Yen", "¥", "🇯🇵", 155.20),
        Currency("CAD", "Canadian Dollar", "$", "🇨🇦", 1.37),
        Currency("AUD", "Australian Dollar", "$", "🇦🇺", 1.51),
        Currency("AED", "UAE Dirham", "د.إ", "🇦🇪", 3.67),
        Currency("CNY", "Chinese Yuan", "¥", "🇨🇳", 7.26)
    )

    private val _sourceCurrency = MutableStateFlow(currencies[0]) // default USD
    val sourceCurrency: StateFlow<Currency> = _sourceCurrency.asStateFlow()

    private val _targetCurrency = MutableStateFlow(currencies[1]) // default INR
    val targetCurrency: StateFlow<Currency> = _targetCurrency.asStateFlow()

    // --- Calculator State ---
    private val _expression = MutableStateFlow("")
    val expression: StateFlow<String> = _expression.asStateFlow()

    private val _calcResult = MutableStateFlow("")
    val calcResult: StateFlow<String> = _calcResult.asStateFlow()

    private val _isEvaluated = MutableStateFlow(false)
    val isEvaluated: StateFlow<Boolean> = _isEvaluated.asStateFlow()

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    // --- Currency Exchange State ---
    private val _exchangeRate = MutableStateFlow(83.50)
    val exchangeRate: StateFlow<Double> = _exchangeRate.asStateFlow()

    private val _usdInput = MutableStateFlow("")
    val usdInput: StateFlow<String> = _usdInput.asStateFlow()

    private val _inrInput = MutableStateFlow("")
    val inrInput: StateFlow<String> = _inrInput.asStateFlow()

    private val _activeCurrencyField = MutableStateFlow(CurrencyField.USD)
    val activeCurrencyField: StateFlow<CurrencyField> = _activeCurrencyField.asStateFlow()

    // --- Custom Canvas Bezier Trend Chart State ---
    private val _historicalRates = MutableStateFlow<List<Double>>(emptyList())
    val historicalRates: StateFlow<List<Double>> = _historicalRates.asStateFlow()

    private val _historicalDates = MutableStateFlow<List<String>>(emptyList())
    val historicalDates: StateFlow<List<String>> = _historicalDates.asStateFlow()

    // --- Smart Split Bill State ---
    private val _numPeople = MutableStateFlow(2)
    val numPeople: StateFlow<Int> = _numPeople.asStateFlow()

    private val _tipPercentage = MutableStateFlow(15)
    val tipPercentage: StateFlow<Int> = _tipPercentage.asStateFlow()

    // --- Settings / Sound & Haptic State ---
    private val _soundEnabled = MutableStateFlow(prefs.getBoolean("sound_enabled", true))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _hapticProfile = MutableStateFlow(prefs.getString("haptic_profile", "Crisp") ?: "Crisp")
    val hapticProfile: StateFlow<String> = _hapticProfile.asStateFlow()

    private val _apiStatus = MutableStateFlow(ApiStatus.IDLE)
    val apiStatus: StateFlow<ApiStatus> = _apiStatus.asStateFlow()

    private val _lastUpdated = MutableStateFlow(prefs.getString("last_rates_update", "Never") ?: "Never")
    val lastUpdated: StateFlow<String> = _lastUpdated.asStateFlow()

    // --- Option 4: Secret Vault State ---
    private val _vaultPin = MutableStateFlow(prefs.getString("vault_pin", "7777") ?: "7777")
    val vaultPin: StateFlow<String> = _vaultPin.asStateFlow()

    private val _decoyPin = MutableStateFlow(prefs.getString("decoy_pin", "1111") ?: "1111")
    val decoyPin: StateFlow<String> = _decoyPin.asStateFlow()

    // --- Google Drive Cloud Backup State ---
    val googleDriveManager = GoogleDriveManager(application)
    
    private val _googleDriveEmail = MutableStateFlow<String?>(googleDriveManager.userEmail)
    val googleDriveEmail: StateFlow<String?> = _googleDriveEmail.asStateFlow()

    private val _googleDriveName = MutableStateFlow<String?>(googleDriveManager.userName)
    val googleDriveName: StateFlow<String?> = _googleDriveName.asStateFlow()

    private val _googleDriveConnected = MutableStateFlow(googleDriveManager.isConnected)
    val googleDriveConnected: StateFlow<Boolean> = _googleDriveConnected.asStateFlow()

    private val _cloudBackupInfo = MutableStateFlow<BackupMetadata?>(null)
    val cloudBackupInfo: StateFlow<BackupMetadata?> = _cloudBackupInfo.asStateFlow()

    private val _recoveryCode = MutableStateFlow(prefs.getString("recovery_code", "") ?: "")
    val recoveryCode: StateFlow<String> = _recoveryCode.asStateFlow()

    init {
        applyTimezone(_preferredTimezone.value)
        if (googleDriveManager.isConnected) {
            fetchCloudBackupInfo()
        }
        generateRecoveryCodeIfNeeded()

        // Initialize Tracking Protection Engine overrides and callbacks
        SecretBrowserTrackingProtection.isGlobalEnabled = { _trackingProtectionEnabled.value }
        SecretBrowserTrackingProtection.getSiteOverride = { host ->
            val norm = host.lowercase().trim()
            if (_trackingSiteEnabled.value.contains(norm)) {
                true
            } else if (_trackingSiteDisabled.value.contains(norm)) {
                false
            } else {
                null
            }
        }
        SecretBrowserTrackingProtection.onTrackerBlocked = { tabId, currentSiteUrl ->
            // Increment global count
            val newTotal = _totalTrackersBlocked.value + 1
            _totalTrackersBlocked.value = newTotal
            prefs.edit().putInt("browser_total_trackers_blocked", newTotal).apply()

            // Increment the blocked count for this tab if valid tabId is supplied
            if (tabId != null) {
                val index = browserTabs.indexOfFirst { it.id == tabId }
                if (index != -1) {
                    val oldTab = browserTabs[index]
                    browserTabs[index] = oldTab.copy(blockedCount = oldTab.blockedCount + 1)
                }
            }
        }

        // Cleanup secure sharing temporary files from previous sessions
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tempDir = File(application.cacheDir, "shared_temp")
                if (tempDir.exists() && tempDir.isDirectory) {
                    tempDir.listFiles()?.forEach { file ->
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun fetchCloudBackupInfo() {
        viewModelScope.launch {
            _cloudBackupInfo.value = googleDriveManager.getBackupInfo()
        }
    }

    fun startGoogleDriveConnection(
        activity: Activity,
        onLaunchIntent: (PendingIntent) -> Unit,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val requestedScopes = listOf(
                    Scope("https://www.googleapis.com/auth/drive.file"),
                    Scope("https://www.googleapis.com/auth/userinfo.email"),
                    Scope("https://www.googleapis.com/auth/userinfo.profile")
                )
                
                val authRequestBuilder = AuthorizationRequest.builder()
                    .setRequestedScopes(requestedScopes)
                
                val authorizationRequest = authRequestBuilder.build()
                val client = Identity.getAuthorizationClient(activity)
                
                client.authorize(authorizationRequest)
                    .addOnSuccessListener { result ->
                        if (result.hasResolution()) {
                            result.pendingIntent?.let { onLaunchIntent(it) }
                                ?: onFailure("Failed to retrieve connection resolution intent.")
                        } else {
                            viewModelScope.launch {
                                try {
                                    val token = result.accessToken
                                    if (!token.isNullOrEmpty()) {
                                        googleDriveManager.updateUserInfoAfterAuth(token)
                                        _googleDriveEmail.value = googleDriveManager.userEmail
                                        _googleDriveName.value = googleDriveManager.userName
                                        _googleDriveConnected.value = true
                                        fetchCloudBackupInfo()
                                        onSuccess()
                                    } else {
                                        onFailure("No access token found in response.")
                                    }
                                } catch (e: Exception) {
                                    val apiEx = e as? com.google.android.gms.common.api.ApiException
                                    val statusCode = apiEx?.statusCode
                                    val statusMessage = apiEx?.localizedMessage ?: e.message
                                    Log.e("GoogleDriveAuth", "Direct auth success processing failed. Code: $statusCode, Message: $statusMessage")
                                    onFailure(e.localizedMessage ?: "Failed to process authorization.")
                                }
                            }
                        }
                    }
                    .addOnFailureListener { exception ->
                        val apiEx = exception as? com.google.android.gms.common.api.ApiException
                        val statusCode = apiEx?.statusCode
                        val statusMessage = apiEx?.localizedMessage ?: exception.message
                        val stackTrace = android.util.Log.getStackTraceString(exception)
                        Log.e("GoogleDriveAuth", "Failed to initiate Google Drive connection. Code: $statusCode, Message: $statusMessage\n$stackTrace")
                        
                        val errorDetails = if (apiEx != null) {
                            "Google Play Services Error: Code $statusCode - $statusMessage"
                        } else {
                            exception.localizedMessage ?: "Failed to initiate Google Drive connection."
                        }
                        onFailure(errorDetails)
                    }
            } catch (e: Exception) {
                val apiEx = e as? com.google.android.gms.common.api.ApiException
                val statusCode = apiEx?.statusCode
                val statusMessage = apiEx?.localizedMessage ?: e.message
                Log.e("GoogleDriveAuth", "Exception in startGoogleDriveConnection. Code: $statusCode, Message: $statusMessage", e)
                onFailure(e.localizedMessage ?: "Unknown error starting Google Drive connection.")
            }
        }
    }

    fun handleGoogleDriveAuthResultIntent(
        data: Intent,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val client = Identity.getAuthorizationClient(googleDriveManager.context)
                val result = client.getAuthorizationResultFromIntent(data)
                val token = result.accessToken
                if (!token.isNullOrEmpty()) {
                    googleDriveManager.updateUserInfoAfterAuth(token)
                    _googleDriveEmail.value = googleDriveManager.userEmail
                    _googleDriveName.value = googleDriveManager.userName
                    _googleDriveConnected.value = true
                    fetchCloudBackupInfo()
                    onSuccess()
                } else {
                    onFailure("No access token found in response.")
                }
            } catch (e: Exception) {
                val apiEx = e as? com.google.android.gms.common.api.ApiException
                val statusCode = apiEx?.statusCode
                val statusMessage = apiEx?.localizedMessage ?: e.message
                val stackTrace = android.util.Log.getStackTraceString(e)
                Log.e("GoogleDriveAuth", "Failed to parse Google Drive connection result. Code: $statusCode, Message: $statusMessage\n$stackTrace")
                
                val errorDetails = if (apiEx != null) {
                    "Google Play Services Error: Code $statusCode - $statusMessage"
                } else {
                    e.localizedMessage ?: "Failed to parse Google Drive connection result."
                }
                onFailure(errorDetails)
            }
        }
    }

    fun disconnectGoogleDrive() {
        googleDriveManager.disconnect()
        _googleDriveEmail.value = null
        _googleDriveName.value = null
        _googleDriveConnected.value = false
        _cloudBackupInfo.value = null
    }

    fun backupToGoogleDrive(context: Context, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val tempFile = File(context.cacheDir, "calculator_vault_backup.zip")
                if (tempFile.exists()) tempFile.delete()
                
                val outputStream = FileOutputStream(tempFile)
                val exportSuccess = exportBackupToZip(context, outputStream)
                if (!exportSuccess) {
                    onFailure("Failed to prepare local backup file.")
                    return@launch
                }

                val uploadSuccess = withContext(Dispatchers.IO) {
                    googleDriveManager.uploadBackup(tempFile)
                }

                try { tempFile.delete() } catch (e: Exception) {}

                if (uploadSuccess) {
                    fetchCloudBackupInfo()
                    onSuccess()
                } else {
                    onFailure("Google Drive upload failed. Please try again.")
                }
            } catch (e: Exception) {
                onFailure("Backup failed: ${e.localizedMessage}")
            }
        }
    }

    fun restoreFromGoogleDrive(context: Context, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val tempFile = File(context.cacheDir, "calculator_vault_backup.zip")
                if (tempFile.exists()) tempFile.delete()

                val downloadSuccess = withContext(Dispatchers.IO) {
                    googleDriveManager.downloadBackup(tempFile)
                }

                if (!downloadSuccess) {
                    onFailure("Failed to download backup file from Google Drive.")
                    return@launch
                }

                val importSuccess = withContext(Dispatchers.IO) {
                    tempFile.inputStream().use { inputStream ->
                        importBackupFromZip(context, inputStream)
                    }
                }

                try { tempFile.delete() } catch (e: Exception) {}

                if (importSuccess) {
                    onSuccess()
                } else {
                    onFailure("Failed to import/unzip downloaded backup file.")
                }
            } catch (e: Exception) {
                onFailure("Restore failed: ${e.localizedMessage}")
            }
        }
    }

    private val _vaultUnlocked = MutableStateFlow(false)
    val vaultUnlocked: StateFlow<Boolean> = _vaultUnlocked.asStateFlow()

    private val _decoyActive = MutableStateFlow(false)
    val decoyActive: StateFlow<Boolean> = _decoyActive.asStateFlow()

    private val _vaultNotes = MutableStateFlow<List<String>>(emptyList())
    val vaultNotes: StateFlow<List<String>> = _vaultNotes.asStateFlow()

    private val _vaultFiles = MutableStateFlow<List<String>>(emptyList())
    val vaultFiles: StateFlow<List<String>> = _vaultFiles.asStateFlow()

    // --- Advanced Vault Folder, Favorite and Recent States ---
    private val _vaultFolders = MutableStateFlow<List<String>>(emptyList())
    val vaultFolders: StateFlow<List<String>> = _vaultFolders.asStateFlow()

    private val _fileFolders = MutableStateFlow<Map<String, String>>(emptyMap())
    val fileFolders: StateFlow<Map<String, String>> = _fileFolders.asStateFlow()

    private val _noteFolders = MutableStateFlow<Map<String, String>>(emptyMap())
    val noteFolders: StateFlow<Map<String, String>> = _noteFolders.asStateFlow()

    private val _favoriteFiles = MutableStateFlow<Set<String>>(emptySet())
    val favoriteFiles: StateFlow<Set<String>> = _favoriteFiles.asStateFlow()

    private val _favoriteNotes = MutableStateFlow<Set<String>>(emptySet())
    val favoriteNotes: StateFlow<Set<String>> = _favoriteNotes.asStateFlow()

    private val _pinnedNotes = MutableStateFlow<Set<String>>(emptySet())
    val pinnedNotes: StateFlow<Set<String>> = _pinnedNotes.asStateFlow()

    private val _recentlyOpened = MutableStateFlow<List<RecentItem>>(emptyList())
    val recentlyOpened: StateFlow<List<RecentItem>> = _recentlyOpened.asStateFlow()

    private val _qrScanHistory = MutableStateFlow<List<QrScanItem>>(emptyList())
    val qrScanHistory: StateFlow<List<QrScanItem>> = _qrScanHistory.asStateFlow()

    fun loadQrScanHistory() {
        val isDecoy = _decoyActive.value
        val key = if (isDecoy) "decoy_scan_history" else "vault_scan_history"
        val jsonStr = prefs.getString(key, null)
        val list = mutableListOf<QrScanItem>()
        if (jsonStr != null) {
            try {
                val array = org.json.JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val itemJson = array.getString(i)
                    val obj = org.json.JSONObject(itemJson)
                    list.add(
                        QrScanItem(
                            id = obj.getString("id"),
                            rawValue = obj.getString("rawValue"),
                            type = obj.getString("type"),
                            timestamp = obj.getLong("timestamp"),
                            title = obj.getString("title"),
                            formattedDetails = obj.getString("formattedDetails")
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _qrScanHistory.value = list.sortedByDescending { it.timestamp }
    }

    fun addQrScanItem(rawValue: String, type: String, title: String, formattedDetails: String): QrScanItem {
        val item = QrScanItem(
            id = System.currentTimeMillis().toString() + "_" + java.util.UUID.randomUUID().toString().take(6),
            rawValue = rawValue,
            type = type,
            timestamp = System.currentTimeMillis(),
            title = title,
            formattedDetails = formattedDetails
        )
        val updatedList = listOf(item) + _qrScanHistory.value
        _qrScanHistory.value = updatedList
        saveQrScanHistory(updatedList)
        return item
    }

    fun deleteQrScanItem(id: String) {
        val updatedList = _qrScanHistory.value.filter { it.id != id }
        _qrScanHistory.value = updatedList
        saveQrScanHistory(updatedList)
    }

    fun clearAllQrScanHistory() {
        _qrScanHistory.value = emptyList()
        saveQrScanHistory(emptyList())
    }

    private fun saveQrScanHistory(list: List<QrScanItem>) {
        val array = org.json.JSONArray()
        for (item in list) {
            val obj = org.json.JSONObject().apply {
                put("id", item.id)
                put("rawValue", item.rawValue)
                put("type", item.type)
                put("timestamp", item.timestamp)
                put("title", item.title)
                put("formattedDetails", item.formattedDetails)
            }
            array.put(obj.toString())
        }
        val isDecoy = _decoyActive.value
        val key = if (isDecoy) "decoy_scan_history" else "vault_scan_history"
        prefs.edit().putString(key, array.toString()).apply()
    }

    fun loadFolders() {
        val saved = prefs.getStringSet("vault_folders", setOf("Personal", "Work", "Finance")) ?: setOf("Personal", "Work", "Finance")
        _vaultFolders.value = saved.toList().sorted()
    }

    fun addFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val current = _vaultFolders.value.toSet() + trimmed
        _vaultFolders.value = current.toList().sorted()
        prefs.edit().putStringSet("vault_folders", current).apply()
    }

    fun deleteFolder(name: String) {
        val current = _vaultFolders.value.toSet() - name
        _vaultFolders.value = current.toList().sorted()
        prefs.edit().putStringSet("vault_folders", current).apply()

        // Clean up associations
        val currentFiles = _fileFolders.value.toMutableMap()
        val toRemoveFiles = currentFiles.filter { it.value == name }.keys
        toRemoveFiles.forEach { id ->
            currentFiles.remove(id)
            prefs.edit().remove("folder_file_$id").apply()
        }
        _fileFolders.value = currentFiles

        val currentNotes = _noteFolders.value.toMutableMap()
        val toRemoveNotes = currentNotes.filter { it.value == name }.keys
        toRemoveNotes.forEach { noteStr ->
            currentNotes.remove(noteStr)
            prefs.edit().remove("folder_note_$noteStr").apply()
        }
        _noteFolders.value = currentNotes
    }

    fun setFolderForFile(id: String, folder: String) {
        val current = _fileFolders.value.toMutableMap()
        if (folder.isEmpty() || folder == "Default") {
            current.remove(id)
            prefs.edit().remove("folder_file_$id").apply()
        } else {
            current[id] = folder
            prefs.edit().putString("folder_file_$id", folder).apply()
        }
        _fileFolders.value = current
    }

    fun setFolderForNote(noteStr: String, folder: String) {
        val current = _noteFolders.value.toMutableMap()
        if (folder.isEmpty() || folder == "Default") {
            current.remove(noteStr)
            prefs.edit().remove("folder_note_$noteStr").apply()
        } else {
            current[noteStr] = folder
            prefs.edit().putString("folder_note_$noteStr", folder).apply()
        }
        _noteFolders.value = current
    }

    fun loadFolderAssociations() {
        val fileMap = mutableMapOf<String, String>()
        _vaultFiles.value.forEach { fileStr ->
            val parts = fileStr.split("|||")
            if (parts.isNotEmpty()) {
                val id = parts[0]
                val folder = prefs.getString("folder_file_$id", "") ?: ""
                if (folder.isNotEmpty()) {
                    fileMap[id] = folder
                }
            }
        }
        _fileFolders.value = fileMap

        val noteMap = mutableMapOf<String, String>()
        _vaultNotes.value.forEach { noteStr ->
            val folder = prefs.getString("folder_note_$noteStr", "") ?: ""
            if (folder.isNotEmpty()) {
                noteMap[noteStr] = folder
            }
        }
        _noteFolders.value = noteMap
    }

    fun loadFavorites() {
        _favoriteFiles.value = prefs.getStringSet("favorite_files", emptySet()) ?: emptySet()
        _favoriteNotes.value = prefs.getStringSet("favorite_notes", emptySet()) ?: emptySet()
        _pinnedNotes.value = prefs.getStringSet("pinned_notes", emptySet()) ?: emptySet()
    }

    fun toggleFavoriteFile(id: String) {
        val current = _favoriteFiles.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _favoriteFiles.value = current
        prefs.edit().putStringSet("favorite_files", current).apply()
    }

    fun toggleFavoriteNote(noteStr: String) {
        val current = _favoriteNotes.value.toMutableSet()
        if (current.contains(noteStr)) {
            current.remove(noteStr)
        } else {
            current.add(noteStr)
        }
        _favoriteNotes.value = current
        prefs.edit().putStringSet("favorite_notes", current).apply()
    }

    fun togglePinnedNote(noteStr: String) {
        val current = _pinnedNotes.value.toMutableSet()
        if (current.contains(noteStr)) {
            current.remove(noteStr)
        } else {
            current.add(noteStr)
        }
        _pinnedNotes.value = current
        prefs.edit().putStringSet("pinned_notes", current).apply()
    }

    fun loadRecentlyOpened() {
        val saved = prefs.getStringSet("recently_opened_items", emptySet()) ?: emptySet()
        val list = saved.mapNotNull { itemStr ->
            val parts = itemStr.split("|||")
            if (parts.size >= 5) {
                RecentItem(
                    type = parts[0],
                    id = parts[1],
                    name = parts[2],
                    extra = parts[3],
                    timestamp = parts[4].toLongOrNull() ?: 0L
                )
            } else null
        }.sortedByDescending { it.timestamp }
        _recentlyOpened.value = list
    }

    fun recordOpenedItem(id: String, type: String, name: String, extra: String = "") {
        val timestamp = System.currentTimeMillis()
        val newItem = RecentItem(id, type, name, timestamp, extra)
        val currentList = _recentlyOpened.value.filterNot { it.id == id && it.type == type }
        val updatedList = (listOf(newItem) + currentList).take(10)
        _recentlyOpened.value = updatedList

        val serialized = updatedList.map { "${it.type}|||${it.id}|||${it.name}|||${it.extra}|||${it.timestamp}" }.toSet()
        prefs.edit().putStringSet("recently_opened_items", serialized).apply()
    }

    fun getVaultStorageDetails(): StorageDetails {
        var photosSize = 0L
        var photosCount = 0
        var videosSize = 0L
        var videosCount = 0
        var documentsSize = 0L
        var documentsCount = 0

        _vaultFiles.value.forEach { fileStr ->
            val parts = fileStr.split("|||")
            if (parts.size >= 5) {
                val mimeType = parts[3]
                val path = parts[4]
                val file = java.io.File(path)
                val size = if (file.exists()) file.length() else 0L

                if (mimeType.startsWith("image/")) {
                    photosSize += size
                    photosCount++
                } else if (mimeType.startsWith("video/")) {
                    videosSize += size
                    videosCount++
                } else {
                    documentsSize += size
                    documentsCount++
                }
            }
        }

        var notesSize = 0L
        val notesCount = _vaultNotes.value.size
        _vaultNotes.value.forEach { noteStr ->
            notesSize += noteStr.toByteArray().size
        }

        val totalSize = photosSize + videosSize + documentsSize + notesSize

        return StorageDetails(
            photosSize = photosSize,
            photosCount = photosCount,
            videosSize = videosSize,
            videosCount = videosCount,
            documentsSize = documentsSize,
            documentsCount = documentsCount,
            notesSize = notesSize,
            notesCount = notesCount,
            totalSize = totalSize
        )
    }

    private val _recentlyDeletedFiles = MutableStateFlow<List<String>>(emptyList())
    val recentlyDeletedFiles: StateFlow<List<String>> = _recentlyDeletedFiles.asStateFlow()

    private val _intruderAttempts = MutableStateFlow<List<String>>(emptyList())
    
    val storageInfo: StateFlow<VaultStorageInfo> = combine(
        _vaultFiles, _vaultNotes, _recentlyDeletedFiles
    ) { files, notes, trash ->
        var photos = 0L
        var videos = 0L
        var docs = 0L
        var audio = 0L
        var notesSize = 0L
        var trashSize = 0L
        
        files.forEach { file ->
            val parts = file.split("|||")
            if (parts.size >= 5) {
                val mimeType = parts[3].lowercase()
                val path = parts[4]
                val size = File(path).length()
                when {
                    mimeType.startsWith("image/") -> photos += size
                    mimeType.startsWith("video/") -> videos += size
                    mimeType.startsWith("audio/") -> audio += size
                    else -> docs += size
                }
            }
        }
        
        notes.forEach { note ->
            notesSize += note.toByteArray().size.toLong()
        }
        
        trash.forEach { item ->
            val parts = item.split("|||")
            if (parts.size >= 5) {
                // If it's a file, parts[4] is absolute path
                if (parts[4].startsWith("/")) {
                    trashSize += File(parts[4]).length()
                } else {
                    trashSize += item.toByteArray().size.toLong()
                }
            } else {
                trashSize += item.toByteArray().size.toLong()
            }
        }
        
        VaultStorageInfo(
            totalBytes = photos + videos + docs + audio + notesSize,
            photosBytes = photos,
            videosBytes = videos,
            docsBytes = docs,
            audioBytes = audio,
            notesBytes = notesSize,
            trashBytes = trashSize
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, VaultStorageInfo())

    val intruderAttempts: StateFlow<List<String>> = _intruderAttempts.asStateFlow()

    private var consecutiveFailedAttempts = 0

    private val _intruderSelfieEnabled = MutableStateFlow(prefs.getBoolean("intruder_selfie_enabled", true))
    val intruderSelfieEnabled: StateFlow<Boolean> = _intruderSelfieEnabled.asStateFlow()

    private val _failedAttemptsThreshold = MutableStateFlow(prefs.getInt("failed_attempts_threshold", 1))
    val failedAttemptsThreshold: StateFlow<Int> = _failedAttemptsThreshold.asStateFlow()


    private val _intruderDetectionEnabled = MutableStateFlow(prefs.getBoolean("intruder_detection_enabled", true))
    val intruderDetectionEnabled: StateFlow<Boolean> = _intruderDetectionEnabled.asStateFlow()

    private val _backgroundAudioPlaybackEnabled = MutableStateFlow(prefs.getBoolean("background_audio_playback_enabled", false))
    val backgroundAudioPlaybackEnabled: StateFlow<Boolean> = _backgroundAudioPlaybackEnabled.asStateFlow()

    fun setBackgroundAudioPlaybackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("background_audio_playback_enabled", enabled).apply()
        _backgroundAudioPlaybackEnabled.value = enabled
    }

    private val _autoLockDuration = MutableStateFlow(prefs.getInt("auto_lock_duration", -1))
    val autoLockDuration: StateFlow<Int> = _autoLockDuration.asStateFlow()

    private val _blurThumbnails = MutableStateFlow(prefs.getBoolean("blur_thumbnails", false))
    val blurThumbnails: StateFlow<Boolean> = _blurThumbnails.asStateFlow()

    private val _lockedFolders = MutableStateFlow(prefs.getStringSet("locked_folders", emptySet()) ?: emptySet())
    val lockedFolders: StateFlow<Set<String>> = _lockedFolders.asStateFlow()

    private val _tempUnlockedFolders = MutableStateFlow<Set<String>>(emptySet())
    val tempUnlockedFolders: StateFlow<Set<String>> = _tempUnlockedFolders.asStateFlow()

    private val _lastInteractionTime = MutableStateFlow(System.currentTimeMillis())
    val lastInteractionTime: StateFlow<Long> = _lastInteractionTime.asStateFlow()

    // --- Hero Profile States ---
    private val _ownerName = MutableStateFlow(prefs.getString("owner_name", "Vault Owner") ?: "Vault Owner")
    val ownerName: StateFlow<String> = _ownerName.asStateFlow()

    private val _ownerAvatarUri = MutableStateFlow(prefs.getString("owner_avatar_uri", "") ?: "")
    val ownerAvatarUri: StateFlow<String> = _ownerAvatarUri.asStateFlow()

    private val _ownerAvatarScale = MutableStateFlow(
        prefs.getFloat("owner_avatar_scale_${prefs.getString("owner_avatar_uri", "") ?: ""}", 1.0f)
    )
    val ownerAvatarScale: StateFlow<Float> = _ownerAvatarScale.asStateFlow()

    private val _ownerAvatarOffsetX = MutableStateFlow(
        prefs.getFloat("owner_avatar_offset_x_${prefs.getString("owner_avatar_uri", "") ?: ""}", 0f)
    )
    val ownerAvatarOffsetX: StateFlow<Float> = _ownerAvatarOffsetX.asStateFlow()

    private val _ownerAvatarOffsetY = MutableStateFlow(
        prefs.getFloat("owner_avatar_offset_y_${prefs.getString("owner_avatar_uri", "") ?: ""}", 0f)
    )
    val ownerAvatarOffsetY: StateFlow<Float> = _ownerAvatarOffsetY.asStateFlow()

    private val _premiumState = MutableStateFlow(prefs.getString("premium_state", "Free") ?: "Free")
    val premiumState: StateFlow<String> = _premiumState.asStateFlow()

    private val _vaultId = MutableStateFlow(getOrCreateVaultId())
    val vaultId: StateFlow<String> = _vaultId.asStateFlow()

    private val _overallSecurityRating = MutableStateFlow("Excellent")
    val overallSecurityRating: StateFlow<String> = _overallSecurityRating.asStateFlow()

    private val _securityItems = MutableStateFlow(
        listOf(
            SecurityItemState("encryption", "Encryption", "Active", "Safe"),
            SecurityItemState("privacy", "Privacy Protection", "Excellent", "Safe"),
            SecurityItemState("cloud", "Cloud Access", "Disabled", "Warning"),
            SecurityItemState("backup", "Backup Protection", "Not Configured", "Attention"),
            SecurityItemState("risk", "Risk Level", "Minimal", "Safe")
        )
    )
    val securityItems: StateFlow<List<SecurityItemState>> = _securityItems.asStateFlow()

    private fun getVoiceNotesTimestamp(): Long? {
        val dir = File(getApplication<Application>().filesDir, "secure_voice_notes")
        val metadataFile = File(dir, "metadata.json")
        if (!metadataFile.exists()) return null
        return try {
            val jsonStr = metadataFile.readText()
            val jsonArray = org.json.JSONArray(jsonStr)
            if (jsonArray.length() > 0) {
                jsonArray.getJSONObject(0).optLong("timestamp", System.currentTimeMillis())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun formatDate(timestampMs: Long): String {
        val sdf = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.getDefault())
        try {
            val tzId = if (preferredTimezone.value == "System") initialSystemTimezone else preferredTimezone.value
            sdf.timeZone = java.util.TimeZone.getTimeZone(tzId)
        } catch (e: Exception) {}
        return sdf.format(java.util.Date(timestampMs))
    }

    val journeyTimeline: StateFlow<List<JourneyTimelineItem>> = combine(
        _vaultFiles, _vaultNotes, _qrScanHistory, _premiumState, preferredTimezone
    ) { files, notes, qrScans, premium, tz ->
        val list = mutableListOf<JourneyTimelineItem>()
        
        // 1. Vault Created
        var createdTime = prefs.getLong("vault_created_time", 0L)
        if (createdTime == 0L) {
            createdTime = System.currentTimeMillis()
            prefs.edit().putLong("vault_created_time", createdTime).apply()
        }
        list.add(
            JourneyTimelineItem(
                id = "vault_created",
                icon = "🔐",
                title = "Vault Created",
                date = formatDate(createdTime),
                description = "Your secure journey started.",
                timestamp = createdTime
            )
        )
        
        // Compute storage counts from files
        var photosCount = 0
        var videosCount = 0
        var audioCount = 0
        var docsCount = 0
        
        files.forEach { file ->
            val parts = file.split("|||")
            if (parts.size >= 5) {
                val mimeType = parts[3].lowercase()
                val isAudioExt = file.lowercase().endsWith(".mp3") || file.lowercase().endsWith(".wav") || file.lowercase().endsWith(".m4a") || file.lowercase().endsWith(".ogg") || file.lowercase().endsWith(".flac") || file.lowercase().endsWith(".aac")
                if (mimeType.startsWith("image/")) {
                    photosCount++
                } else if (mimeType.startsWith("video/")) {
                    videosCount++
                } else if (mimeType.startsWith("audio/") || isAudioExt) {
                    audioCount++
                } else {
                    docsCount++
                }
            }
        }
        
        // 2. First Secret Saved
        if (files.isNotEmpty()) {
            val firstFileTime = files.mapNotNull {
                it.split("|||").getOrNull(0)?.toLongOrNull()
            }.minOrNull() ?: (createdTime + 60000)
            list.add(
                JourneyTimelineItem(
                    id = "first_secret",
                    icon = "📂",
                    title = "First Secret Saved",
                    date = formatDate(firstFileTime),
                    description = "Safely hid your first private file inside the vault.",
                    timestamp = firstFileTime
                )
            )
        }
        
        // 3. First Secure Note
        if (notes.isNotEmpty()) {
            var noteTime = prefs.getLong("first_note_time", 0L)
            if (noteTime == 0L) {
                noteTime = System.currentTimeMillis()
                prefs.edit().putLong("first_note_time", noteTime).apply()
            }
            list.add(
                JourneyTimelineItem(
                    id = "first_note",
                    icon = "📝",
                    title = "First Secure Note",
                    date = formatDate(noteTime),
                    description = "Created an encrypted text note in your secret notepad.",
                    timestamp = noteTime
                )
            )
        }
        
        // 4. First Hidden Photo
        if (photosCount > 0) {
            var photoTime = prefs.getLong("first_photo_time", 0L)
            if (photoTime == 0L) {
                photoTime = System.currentTimeMillis()
                prefs.edit().putLong("first_photo_time", photoTime).apply()
            }
            list.add(
                JourneyTimelineItem(
                    id = "first_photo",
                    icon = "📷",
                    title = "First Hidden Photo",
                    date = formatDate(photoTime),
                    description = "Moved a private memory into the secure image gallery.",
                    timestamp = photoTime
                )
            )
        }
        
        // 5. First Hidden Video
        if (videosCount > 0) {
            var videoTime = prefs.getLong("first_video_time", 0L)
            if (videoTime == 0L) {
                videoTime = System.currentTimeMillis()
                prefs.edit().putLong("first_video_time", videoTime).apply()
            }
            list.add(
                JourneyTimelineItem(
                    id = "first_video",
                    icon = "🎥",
                    title = "First Hidden Video",
                    date = formatDate(videoTime),
                    description = "Imported your first confidential video into the media player.",
                    timestamp = videoTime
                )
            )
        }
        
        // 5.5 First Hidden Audio
        if (audioCount > 0) {
            var audioTime = prefs.getLong("first_audio_time", 0L)
            if (audioTime == 0L) {
                audioTime = System.currentTimeMillis()
                prefs.edit().putLong("first_audio_time", audioTime).apply()
            }
            list.add(
                JourneyTimelineItem(
                    id = "first_audio",
                    icon = "🎵",
                    title = "First Hidden Audio",
                    date = formatDate(audioTime),
                    description = "Secured your first audio file inside the encrypted vault.",
                    timestamp = audioTime
                )
            )
        }
        
        // 6. First Hidden Document
        if (docsCount > 0) {
            var docTime = prefs.getLong("first_doc_time", 0L)
            if (docTime == 0L) {
                docTime = System.currentTimeMillis()
                prefs.edit().putLong("first_doc_time", docTime).apply()
            }
            list.add(
                JourneyTimelineItem(
                    id = "first_doc",
                    icon = "📄",
                    title = "First Hidden Document",
                    date = formatDate(docTime),
                    description = "Safeguarded your first critical PDF or text document.",
                    timestamp = docTime
                )
            )
        }
        
        // 7. First Secure Browser Session
        val hasBrowserHistory = prefs.getString("browser_history", "[]") != "[]"
        if (hasBrowserHistory) {
            var browserTime = prefs.getLong("first_browser_time", 0L)
            if (browserTime == 0L) {
                browserTime = System.currentTimeMillis()
                prefs.edit().putLong("first_browser_time", browserTime).apply()
            }
            list.add(
                JourneyTimelineItem(
                    id = "first_browser",
                    icon = "🌐",
                    title = "First Secure Browser Session",
                    date = formatDate(browserTime),
                    description = "Browsed anonymously with auto-clearing tracking.",
                    timestamp = browserTime
                )
            )
        }
        
        // 8. First Voice Note
        val voiceNotesTime = getVoiceNotesTimestamp()
        if (voiceNotesTime != null) {
            list.add(
                JourneyTimelineItem(
                    id = "first_voice_note",
                    icon = "🎤",
                    title = "First Voice Note",
                    date = formatDate(voiceNotesTime),
                    description = "Recorded a secure, encrypted private voice memo.",
                    timestamp = voiceNotesTime
                )
            )
        }
        
        // 9. First QR Scan
        if (qrScans.isNotEmpty()) {
            val qrTime = qrScans.lastOrNull()?.timestamp ?: createdTime
            list.add(
                JourneyTimelineItem(
                    id = "first_qr_scan",
                    icon = "📱",
                    title = "First QR Scan",
                    date = formatDate(qrTime),
                    description = "Scanned and saved a private QR code or link.",
                    timestamp = qrTime
                )
            )
        }
        
        // 10. First Metadata Cleaned
        val hasCleanedMetadata = prefs.getBoolean("first_metadata_cleaned", false)
        if (hasCleanedMetadata) {
            val metadataTime = prefs.getLong("time_metadata_cleaned", createdTime)
            list.add(
                JourneyTimelineItem(
                    id = "first_metadata_cleaned",
                    icon = "🧹",
                    title = "First Metadata Cleaned",
                    date = formatDate(metadataTime),
                    description = "Stripped private EXIF location data from an image.",
                    timestamp = metadataTime
                )
            )
        }
        
        // 11. First Password Generated
        val hasGeneratedPassword = prefs.getBoolean("first_password_generated", false)
        if (hasGeneratedPassword) {
            val passwordTime = prefs.getLong("time_password_generated", createdTime)
            list.add(
                JourneyTimelineItem(
                    id = "first_password_generated",
                    icon = "🔑",
                    title = "First Password Generated",
                    date = formatDate(passwordTime),
                    description = "Created an uncrackable local password for your accounts.",
                    timestamp = passwordTime
                )
            )
        }
        
        // 12. Security Status Improved
        val isSecurityImproved = prefs.getBoolean("security_status_improved", false)
        if (isSecurityImproved) {
            val securityTime = prefs.getLong("time_security_improved", createdTime)
            list.add(
                JourneyTimelineItem(
                    id = "security_improved",
                    icon = "🛡",
                    title = "Security Status Improved",
                    date = formatDate(securityTime),
                    description = "Enhanced your vault defenses and security ratings.",
                    timestamp = securityTime
                )
            )
        }
        
        // 13. Premium Activated
        if (premium == "Premium" || premium == "Lifetime") {
            var premiumTime = prefs.getLong("premium_activated_time", 0L)
            if (premiumTime == 0L) {
                premiumTime = System.currentTimeMillis()
                prefs.edit().putLong("premium_activated_time", premiumTime).apply()
            }
            list.add(
                JourneyTimelineItem(
                    id = "premium_activated",
                    icon = "💎",
                    title = "Premium Activated",
                    date = formatDate(premiumTime),
                    description = "Unlocked premium privacy features and tools.",
                    timestamp = premiumTime
                )
            )
        }
        
        // 14. Lifetime Activated
        if (premium == "Lifetime") {
            var lifetimeTime = prefs.getLong("lifetime_activated_time", 0L)
            if (lifetimeTime == 0L) {
                lifetimeTime = System.currentTimeMillis()
                prefs.edit().putLong("lifetime_activated_time", lifetimeTime).apply()
            }
            list.add(
                JourneyTimelineItem(
                    id = "lifetime_activated",
                    icon = "👑",
                    title = "Lifetime Activated",
                    date = formatDate(lifetimeTime),
                    description = "Enjoying lifetime ultimate vault protection.",
                    timestamp = lifetimeTime
                )
            )
        }
        
        list.sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private fun getOrCreateVaultId(): String {
        val existing = prefs.getString("vault_id", null)
        if (!existing.isNullOrEmpty()) return existing
        val chars = "0123456789ABCDEF"
        val randomPart = (1..6).map { chars[(0 until chars.length).random()] }.joinToString("")
        val newId = "SV-$randomPart"
        prefs.edit().putString("vault_id", newId).apply()
        return newId
    }

    var isPickingFile = false

    // --- Advanced Stealth & Vault States ---
    private val _biometricEnabled = MutableStateFlow(prefs.getBoolean("biometric_enabled", false))
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val _biometricMode = MutableStateFlow(prefs.getString("biometric_mode", "hidden") ?: "hidden")
    val biometricMode: StateFlow<String> = _biometricMode.asStateFlow()

    private val _securityQuestion = MutableStateFlow(prefs.getString("security_question", "") ?: "")
    val securityQuestion: StateFlow<String> = _securityQuestion.asStateFlow()

    private val _securityAnswer = MutableStateFlow(prefs.getString("security_answer", "") ?: "")
    val securityAnswer: StateFlow<String> = _securityAnswer.asStateFlow()

    private val _showRecoveryTrigger = MutableStateFlow(false)
    val showRecoveryTrigger: StateFlow<Boolean> = _showRecoveryTrigger.asStateFlow()

    fun setShowRecoveryTrigger(show: Boolean) {
        _showRecoveryTrigger.value = show
    }

    private val _panicEnabled = MutableStateFlow(prefs.getBoolean("panic_enabled", false))
    val panicEnabled: StateFlow<Boolean> = _panicEnabled.asStateFlow()

    private val _panicAction = MutableStateFlow(prefs.getString("panic_action", "lock") ?: "lock")
    val panicAction: StateFlow<String> = _panicAction.asStateFlow()

    private val _panicExitAction = MutableStateFlow(prefs.getString("panic_exit_action", "close") ?: "close")
    val panicExitAction: StateFlow<String> = _panicExitAction.asStateFlow()

    private val _activeAppIcon = MutableStateFlow(prefs.getString("active_app_icon", "LauncherCalculator") ?: "LauncherCalculator")
    val activeAppIcon: StateFlow<String> = _activeAppIcon.asStateFlow()

    private val _lockedApps = MutableStateFlow(prefs.getStringSet("locked_apps", emptySet()) ?: emptySet())
    val lockedApps: StateFlow<Set<String>> = _lockedApps.asStateFlow()

    private val _preventScreenshots = MutableStateFlow(prefs.getBoolean("prevent_screenshots", false))
    val preventScreenshots: StateFlow<Boolean> = _preventScreenshots.asStateFlow()

    private val _screenDownLock = MutableStateFlow(prefs.getBoolean("screen_down_lock", false))
    val screenDownLock: StateFlow<Boolean> = _screenDownLock.asStateFlow()

    private val _fileLossProtection = MutableStateFlow(prefs.getBoolean("file_loss_protection", false))
    val fileLossProtection: StateFlow<Boolean> = _fileLossProtection.asStateFlow()

    private val _lockOnBackground = MutableStateFlow(prefs.getBoolean("lock_on_background", true))
    val lockOnBackground: StateFlow<Boolean> = _lockOnBackground.asStateFlow()

    private val _hideNotifications = MutableStateFlow(prefs.getBoolean("hide_notifications", false))
    val hideNotifications: StateFlow<Boolean> = _hideNotifications.asStateFlow()

    private val _clipboardProtection = MutableStateFlow(prefs.getBoolean("clipboard_protection", true))
    val clipboardProtection: StateFlow<Boolean> = _clipboardProtection.asStateFlow()

    private val _stealthMode = MutableStateFlow(prefs.getBoolean("stealth_mode", true))
    val stealthMode: StateFlow<Boolean> = _stealthMode.asStateFlow()

    private val _secureShareBranding = MutableStateFlow(true)
    val secureShareBranding: StateFlow<Boolean> = _secureShareBranding.asStateFlow()

    private val _pendingDeleteSender = MutableStateFlow<android.content.IntentSender?>(null)
    val pendingDeleteSender: StateFlow<android.content.IntentSender?> = _pendingDeleteSender.asStateFlow()

    var pendingDeleteOriginalPaths: List<String> = emptyList()
    var stagedVaultFiles = mutableListOf<String>()

    fun clearPendingDelete() {
        _pendingDeleteSender.value = null
        pendingDeleteOriginalPaths = emptyList()
        // If user denied deletion, we might still want to show the file in the vault, or rollback.
        // Let's just show it.
        if (stagedVaultFiles.isNotEmpty()) {
            val updatedFiles = _vaultFiles.value + stagedVaultFiles
            _vaultFiles.value = updatedFiles.sortedByDescending { it }
            val isDecoy = _decoyActive.value
            val filesKey = if (isDecoy) "decoy_files" else "vault_files"
            prefs.edit().putStringSet(filesKey, _vaultFiles.value.toSet()).apply()
            stagedVaultFiles.clear()
        }
    }

    fun onOriginalFileDeleted(context: android.content.Context) {
        // MediaStore is already updated via createDeleteRequest or contentResolver.delete
        pendingDeleteOriginalPaths = emptyList()
        
        // 7. Refresh the Vault UI.
        if (stagedVaultFiles.isNotEmpty()) {
            val updatedFiles = _vaultFiles.value + stagedVaultFiles
            _vaultFiles.value = updatedFiles.sortedByDescending { it }
            val isDecoy = _decoyActive.value
            val filesKey = if (isDecoy) "decoy_files" else "vault_files"
            prefs.edit().putStringSet(filesKey, _vaultFiles.value.toSet()).apply()
            stagedVaultFiles.clear()
        }
    }

    // --- Multi-Language Localization State ---
    private val _selectedLanguage = MutableStateFlow(prefs.getString("selected_language", "en") ?: "en")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    fun setSelectedLanguage(langCode: String) {
        prefs.edit().putString("selected_language", langCode).apply()
        _selectedLanguage.value = langCode
    }

    // --- Dynamic Theme Selection State ---
    private val _selectedTheme = MutableStateFlow(
        try {
            AppTheme.valueOf(prefs.getString("selected_theme", AppTheme.GRAPHITE.name) ?: AppTheme.GRAPHITE.name)
        } catch (e: Exception) {
            AppTheme.GRAPHITE
        }
    )
    val selectedTheme: StateFlow<AppTheme> = _selectedTheme.asStateFlow()

    fun setSelectedTheme(theme: AppTheme) {
        prefs.edit().putString("selected_theme", theme.name).apply()
        _selectedTheme.value = theme
    }

    fun t(key: String): String {
        return TranslationProvider.translate(key, _selectedLanguage.value)
    }

    private val decimalFormat = DecimalFormat("#.######", DecimalFormatSymbols(Locale.US))
    private val currencyFormat = DecimalFormat("#.##", DecimalFormatSymbols(Locale.US))

    init {
        // Force clear old cached INR rate if it equals 95.6
        if (prefs.contains("rate_INR") && prefs.getFloat("rate_INR", 0f) == 95.6f) {
            prefs.edit().remove("rate_INR").apply()
        }

        val srcCode = prefs.getString("source_currency", "USD") ?: "USD"
        val tgtCode = prefs.getString("target_currency", "INR") ?: "INR"
        _sourceCurrency.value = currencies.find { it.code == srcCode } ?: currencies[0]
        _targetCurrency.value = currencies.find { it.code == tgtCode } ?: currencies[1]

        // Load custom rates if they exist in prefs
        _history.value = listOf("100 USD = 8350 INR", "50 USD = 4175 INR")
        updateExchangeRateFlow()
        updateHistoricalRates()
        fetchLatestRates()

        // Load initial vault data (Real or Decoy based on decoyActive)
        loadVaultData()

        // Load intruder attempts
        val savedIntruders = prefs.getStringSet("intruder_attempts", emptySet()) ?: emptySet()
        _intruderAttempts.value = savedIntruders.toList().sortedByDescending { it }

        // Auto Lock loop
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(2000)
                if (isPickingFile || VaultBrowserIntegration.activeUploadRequest != null) {
                    _lastInteractionTime.value = System.currentTimeMillis()
                    continue
                }
                if (_vaultUnlocked.value && _autoLockDuration.value != -1) {
                    val idleMillis = System.currentTimeMillis() - _lastInteractionTime.value
                    val limitMillis = _autoLockDuration.value * 1000L
                    if (idleMillis >= limitMillis) {
                        lockVault()
                    }
                }
            }
        }
    }

    fun fetchLatestRates() {
        viewModelScope.launch(Dispatchers.IO) {
            _apiStatus.value = ApiStatus.LOADING
            try {
                // Timestamp generation to bypass any intermediate network cache
                val timestamp = System.currentTimeMillis()
                
                // Fetching raw live data directly
                val response = CurrencyApi.service.getLatestRates("USD") 
                
                if (response.result == "success") {
                    val editor = prefs.edit()
                    currencies.forEach { currency ->
                        if (currency.code != "USD") {
                            val rateInResponse = response.rates[currency.code]
                            if (rateInResponse != null && rateInResponse > 0) {
                                editor.putFloat("rate_${currency.code}", rateInResponse.toFloat())
                            }
                        }
                    }
                    
                    val updateTime = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(java.util.Date())
                    editor.putString("last_rates_update", updateTime)
                    editor.apply()
                    
                    withContext(Dispatchers.Main) {
                        _lastUpdated.value = updateTime
                        _apiStatus.value = ApiStatus.SUCCESS
                        
                        // Immediate live UI synchronization
                        updateExchangeRateFlow()
                        val isSourceActive = _activeCurrencyField.value == CurrencyField.USD
                        recalculateConversion(
                            fromSource = isSourceActive, 
                            input = if (isSourceActive) _usdInput.value else _inrInput.value
                        )
                        updateHistoricalRates()
                    }
                } else {
                    withContext(Dispatchers.Main) { _apiStatus.value = ApiStatus.ERROR }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { _apiStatus.value = ApiStatus.ERROR }
            }
        }
    }

    // --- Settings Methods ---
    fun toggleSound() {
        val next = !_soundEnabled.value
        _soundEnabled.value = next
        prefs.edit().putBoolean("sound_enabled", next).apply()
    }

    fun selectHapticProfile(profile: String) {
        _hapticProfile.value = profile
        prefs.edit().putString("haptic_profile", profile).apply()
    }

    fun triggerKeypressEffects(context: android.content.Context) {
        // play haptic feedback
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // API 29+ (Android 10+)
                val effectId = when (_hapticProfile.value) {
                    "Crisp" -> android.os.VibrationEffect.EFFECT_CLICK
                    "Soft" -> android.os.VibrationEffect.EFFECT_TICK
                    "Heavy" -> android.os.VibrationEffect.EFFECT_HEAVY_CLICK
                    else -> -1
                }
                if (effectId != -1) {
                    try {
                        val effect = android.os.VibrationEffect.createPredefined(effectId)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            val attributes = android.os.VibrationAttributes.Builder()
                                .setUsage(android.os.VibrationAttributes.USAGE_TOUCH)
                                .build()
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) { vibrator.vibrate(effect, attributes) } else { vibrator.vibrate(effect) }
                        } else {
                            vibrator.vibrate(effect)
                        }
                    } catch (e: Exception) {
                        // Fallback in case of custom OEM failure
                        val fallbackDuration = when (_hapticProfile.value) {
                            "Crisp" -> 35L
                            "Soft" -> 18L
                            "Heavy" -> 85L
                            else -> 0L
                        }
                        if (fallbackDuration > 0) {
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(fallbackDuration, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
                }
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // API 26+ (Android 8.0+)
                val duration = when (_hapticProfile.value) {
                    "Crisp" -> 35L
                    "Soft" -> 18L
                    "Heavy" -> 85L
                    else -> 0L
                }
                if (duration > 0) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else {
                // Older devices
                val duration = when (_hapticProfile.value) {
                    "Crisp" -> 35L
                    "Soft" -> 18L
                    "Heavy" -> 85L
                    else -> 0L
                }
                if (duration > 0) {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
            }
        }

        // play native click sound
        if (_soundEnabled.value) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.playSoundEffect(android.media.AudioManager.FX_KEY_CLICK)
        }
    }

    fun triggerCalculatorKeypressEffects(context: android.content.Context, key: String) {
        // play native click sound
        if (_soundEnabled.value) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.playSoundEffect(android.media.AudioManager.FX_KEY_CLICK)
        }

        // play haptic feedback based on key type
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // API 29+ (Android 10+)
                val effectId = when {
                    key == "=" -> android.os.VibrationEffect.EFFECT_HEAVY_CLICK
                    key.matches(Regex("[0-9.]")) -> android.os.VibrationEffect.EFFECT_TICK
                    else -> android.os.VibrationEffect.EFFECT_CLICK // Medium tick for operators
                }
                try {
                    val effect = android.os.VibrationEffect.createPredefined(effectId)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        val attributes = android.os.VibrationAttributes.Builder()
                            .setUsage(android.os.VibrationAttributes.USAGE_TOUCH)
                            .build()
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) { vibrator.vibrate(effect, attributes) } else { vibrator.vibrate(effect) }
                    } else {
                        vibrator.vibrate(effect)
                    }
                } catch (e: Exception) {
                    val fallbackDuration = when {
                        key == "=" -> 85L
                        key.matches(Regex("[0-9.]")) -> 18L
                        else -> 35L
                    }
                    if (fallbackDuration > 0) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(fallbackDuration, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                }
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val duration = when {
                    key == "=" -> 85L
                    key.matches(Regex("[0-9.]")) -> 18L
                    else -> 35L
                }
                if (duration > 0) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else {
                val duration = when {
                    key == "=" -> 85L
                    key.matches(Regex("[0-9.]")) -> 18L
                    else -> 35L
                }
                if (duration > 0) {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
            }
        }
    }

    // --- Split Bill Methods ---
    fun incrementPeople() {
        _numPeople.value = (_numPeople.value + 1).coerceAtMost(100)
    }

    fun decrementPeople() {
        _numPeople.value = (_numPeople.value - 1).coerceAtLeast(1)
    }

    fun updateTipPercentage(pct: Int) {
        _tipPercentage.value = pct.coerceIn(0, 100)
    }

    // --- Currency Selector Methods ---
    fun selectSourceCurrency(currency: Currency) {
        _sourceCurrency.value = currency
        prefs.edit().putString("source_currency", currency.code).apply()
        updateExchangeRateFlow()
        recalculateConversion(fromSource = _activeCurrencyField.value == CurrencyField.USD, input = if (_activeCurrencyField.value == CurrencyField.USD) _usdInput.value else _inrInput.value)
        updateHistoricalRates()
    }

    fun selectTargetCurrency(currency: Currency) {
        _targetCurrency.value = currency
        prefs.edit().putString("target_currency", currency.code).apply()
        updateExchangeRateFlow()
        recalculateConversion(fromSource = _activeCurrencyField.value == CurrencyField.USD, input = if (_activeCurrencyField.value == CurrencyField.USD) _usdInput.value else _inrInput.value)
        updateHistoricalRates()
    }

    fun swapCurrencies() {
        val temp = _sourceCurrency.value
        _sourceCurrency.value = _targetCurrency.value
        _targetCurrency.value = temp
        prefs.edit()
            .putString("source_currency", _sourceCurrency.value.code)
            .putString("target_currency", _targetCurrency.value.code)
            .apply()

        // Swap actual inputs for seamless conversion swap
        val tempInput = _usdInput.value
        _usdInput.value = _inrInput.value
        _inrInput.value = tempInput

        updateExchangeRateFlow()
        recalculateConversion(fromSource = _activeCurrencyField.value == CurrencyField.USD, input = if (_activeCurrencyField.value == CurrencyField.USD) _usdInput.value else _inrInput.value)
        updateHistoricalRates()
    }

    fun getRate(currency: Currency): Double {
        if (currency.code == "USD") return 1.0
        return prefs.getFloat("rate_${currency.code}", currency.defaultUsdRate.toFloat()).toDouble()
    }

    private fun updateExchangeRateFlow() {
        val src = _sourceCurrency.value
        val tgt = _targetCurrency.value
        val srcRate = getRate(src)
        val tgtRate = getRate(tgt)
        _exchangeRate.value = tgtRate / srcRate
    }

    fun updateHistoricalRates() {
        val src = _sourceCurrency.value
        val tgt = _targetCurrency.value
        val baseRate = getRate(tgt) / getRate(src)

        // Generate 7 days of dates
        val dates = ArrayList<String>()
        val sdf = java.text.SimpleDateFormat("dd MMM", Locale.US)
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -6)
        for (i in 0 until 7) {
            dates.add(sdf.format(cal.time))
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        _historicalDates.value = dates

        // Generate 7 days of stable fluctuating simulated rate feed
        val seed = (src.code + tgt.code).hashCode().toLong()
        val random = java.util.Random(seed)
        val rates = ArrayList<Double>()
        for (i in 0 until 7) {
            val percent = (random.nextDouble() - 0.5) * 0.03 // +/- 1.5% max fluctuation
            rates.add(baseRate * (1.0 + percent))
        }
        rates[6] = baseRate // today is exactly the live rate
        _historicalRates.value = rates
    }

    // --- Calculator Methods ---
    fun onCalcKeyPress(key: String) {
        val currentExpr = _expression.value
        val isCurrentEval = _isEvaluated.value

        when (key) {
            "C" -> {
                _expression.value = ""
                _calcResult.value = ""
                _isEvaluated.value = false
            }
            "⌫" -> {
                if (isCurrentEval) {
                    _expression.value = ""
                    _calcResult.value = ""
                    _isEvaluated.value = false
                } else if (currentExpr.isNotEmpty()) {
                    _expression.value = currentExpr.dropLast(1)
                    updateLiveResult()
                }
            }
            "=" -> {
                if (currentExpr == "999999") {
                    _showRecoveryTrigger.value = true
                    _expression.value = ""
                    _calcResult.value = ""
                    _isEvaluated.value = false
                } else if (tryUnlockVault(currentExpr)) {
                    _expression.value = ""
                    _calcResult.value = ""
                    _isEvaluated.value = false
                } else if (currentExpr.isNotEmpty() && !isCurrentEval) {
                    try {
                        val result = evaluateExpression(currentExpr)
                        val formattedResult = formatDouble(result)
                        
                        // Save calculation in history
                        val historyItem = "$currentExpr = $formattedResult"
                        _history.value = (listOf(historyItem) + _history.value).take(20)
                        
                        _expression.value = currentExpr
                        _calcResult.value = formattedResult
                        _isEvaluated.value = true
                    } catch (e: Exception) {
                        _calcResult.value = "Error"
                        _isEvaluated.value = true
                    }
                }
            }
            "+/-" -> {
                if (isCurrentEval) {
                    val lastRes = _calcResult.value
                    if (lastRes.isNotEmpty() && lastRes != "Error") {
                        try {
                            _expression.value = toggleLastNumberSign(lastRes)
                            _calcResult.value = ""
                            _isEvaluated.value = false
                            updateLiveResult()
                        } catch (e: Exception) {}
                    }
                } else {
                    try {
                        _expression.value = toggleLastNumberSign(currentExpr)
                        updateLiveResult()
                    } catch (e: Exception) {
                        // Ignore if invalid
                    }
                }
            }
            "%" -> {
                if (isCurrentEval) {
                    val lastRes = _calcResult.value
                    if (lastRes.isNotEmpty() && lastRes != "Error" && lastRes.last().isDigit()) {
                        _expression.value = lastRes + "÷100"
                        _calcResult.value = ""
                        _isEvaluated.value = false
                        updateLiveResult()
                    }
                } else if (currentExpr.isNotEmpty() && currentExpr.last().isDigit()) {
                    _expression.value = currentExpr + "÷100"
                    updateLiveResult()
                }
            }
            "+", "-", "×", "÷" -> {
                if (isCurrentEval) {
                    val lastRes = _calcResult.value
                    if (lastRes.isNotEmpty() && lastRes != "Error") {
                        _expression.value = lastRes + key
                        _calcResult.value = ""
                        _isEvaluated.value = false
                    }
                } else {
                    if (currentExpr.isNotEmpty()) {
                        val lastChar = currentExpr.last().toString()
                        if (lastChar == "+" || lastChar == "-" || lastChar == "×" || lastChar == "÷") {
                            _expression.value = currentExpr.dropLast(1) + key
                        } else if (lastChar != ".") {
                            _expression.value = currentExpr + key
                        }
                    } else if (key == "-") {
                        _expression.value = "-"
                    }
                }
            }
            "." -> {
                if (isCurrentEval) {
                    _expression.value = "0."
                    _calcResult.value = ""
                    _isEvaluated.value = false
                } else {
                    if (canAppendDecimal(currentExpr)) {
                        _expression.value = currentExpr + "."
                    }
                }
            }
            else -> { // Digits 0-9
                if (isCurrentEval) {
                    _expression.value = key
                    _calcResult.value = ""
                    _isEvaluated.value = false
                    updateLiveResult()
                } else {
                    _expression.value = currentExpr + key
                    updateLiveResult()
                }
            }
        }
    }

    private fun updateLiveResult() {
        val currentExpr = _expression.value
        if (currentExpr.isEmpty()) {
            _calcResult.value = ""
            return
        }
        
        val lastChar = currentExpr.last()
        if (lastChar == '+' || lastChar == '-' || lastChar == '×' || lastChar == '÷' || lastChar == '.') {
            return
        }

        try {
            val result = evaluateExpression(currentExpr)
            _calcResult.value = formatDouble(result)
        } catch (e: Exception) {
            // Keep previous live result or clear it
        }
    }

    private fun canAppendDecimal(expr: String): Boolean {
        if (expr.isEmpty()) return true
        val lastNumber = expr.split("+", "-", "×", "÷").last()
        return !lastNumber.contains(".")
    }

    private fun toggleLastNumberSign(expr: String): String {
        if (expr.isEmpty()) return "-"
        val parts = expr.split("(?<=[+×÷-])|(?=[+×÷-])".toRegex())
        if (parts.isEmpty()) return expr

        val lastPart = parts.last()
        if (lastPart.isEmpty()) return expr

        return if (lastPart.toDoubleOrNull() != null) {
            val invertedValue = -lastPart.toDouble()
            val prefix = expr.substring(0, expr.length - lastPart.length)
            val formattedInverted = formatDouble(invertedValue)
            if (invertedValue < 0 && prefix.isNotEmpty() && prefix.last() == '-') {
                prefix.dropLast(1) + "+" + formattedInverted.drop(1)
            } else {
                prefix + formattedInverted
            }
        } else {
            expr
        }
    }

    private fun formatDouble(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            decimalFormat.format(value)
        }
    }

    // --- Currency Conversion Methods ---
    fun onCurrencyFieldSelect(field: CurrencyField) {
        _activeCurrencyField.value = field
    }

    fun onCurrencyKeyPress(key: String) {
        val isSourceActive = _activeCurrencyField.value == CurrencyField.USD
        val activeInputFlow = if (isSourceActive) _usdInput else _inrInput
        val currentInput = activeInputFlow.value

        when (key) {
            "C" -> {
                _usdInput.value = ""
                _inrInput.value = ""
            }
            "⌫" -> {
                if (currentInput.isNotEmpty()) {
                    val newValue = currentInput.dropLast(1)
                    activeInputFlow.value = newValue
                    recalculateConversion(fromSource = isSourceActive, input = newValue)
                }
            }
            "." -> {
                if (!currentInput.contains(".") && currentInput.isNotEmpty()) {
                    activeInputFlow.value = currentInput + "."
                } else if (currentInput.isEmpty()) {
                    activeInputFlow.value = "0."
                }
            }
            else -> { // Digits 0-9
                if (currentInput == "0" && (key == "0" || key == "00")) return
                
                val newValue = if (currentInput == "0" && key != "00") key else if (currentInput == "0" && key == "00") "0" else currentInput + key
                activeInputFlow.value = newValue
                recalculateConversion(fromSource = isSourceActive, input = newValue)
            }
        }
    }

    fun updateExchangeRate(newRate: Double) {
        val src = _sourceCurrency.value
        val tgt = _targetCurrency.value
        if (newRate > 0) {
            if (src.code == "USD") {
                prefs.edit().putFloat("rate_${tgt.code}", newRate.toFloat()).apply()
            } else if (tgt.code == "USD") {
                val invertedRate = 1.0 / newRate
                prefs.edit().putFloat("rate_${src.code}", invertedRate.toFloat()).apply()
            } else {
                val srcUsdRate = getRate(src)
                val newTgtRate = srcUsdRate * newRate
                prefs.edit().putFloat("rate_${tgt.code}", newTgtRate.toFloat()).apply()
            }
            updateExchangeRateFlow()
            recalculateConversion(fromSource = true, input = _usdInput.value)
            updateHistoricalRates()
        }
    }

    fun applyQuickAdd(amount: Double) {
        val isSourceActive = _activeCurrencyField.value == CurrencyField.USD
        if (isSourceActive) {
            val currentVal = _usdInput.value.toDoubleOrNull() ?: 0.0
            val newVal = currentVal + amount
            val formatted = formatDouble(newVal)
            _usdInput.value = formatted
            recalculateConversion(fromSource = true, input = formatted)
        } else {
            val currentVal = _inrInput.value.toDoubleOrNull() ?: 0.0
            val newVal = currentVal + amount
            val formatted = formatDouble(newVal)
            _inrInput.value = formatted
            recalculateConversion(fromSource = false, input = formatted)
        }
    }

    private fun recalculateConversion(fromSource: Boolean, input: String) {
        val numericValue = input.toDoubleOrNull() ?: 0.0
        val src = _sourceCurrency.value
        val tgt = _targetCurrency.value
        val srcRate = getRate(src)
        val tgtRate = getRate(tgt)

        if (input.isEmpty()) {
            if (fromSource) _inrInput.value = "" else _usdInput.value = ""
            return
        }

        if (fromSource) {
            val valueInUsd = numericValue / srcRate
            val targetValue = valueInUsd * tgtRate
            _inrInput.value = currencyFormat.format(targetValue)
        } else {
            val valueInUsd = numericValue / tgtRate
            val sourceValue = valueInUsd * srcRate
            _usdInput.value = currencyFormat.format(sourceValue)
        }
    }

    // --- Expression Parsing Engine (Non-nested Helper State) ---
    private var parsePos = -1
    private var parseCh = -1
    private var parseCleanExpr = ""

    private fun nextChar() {
        parseCh = if (++parsePos < parseCleanExpr.length) parseCleanExpr[parsePos].code else -1
    }

    private fun eat(charToEat: Int): Boolean {
        while (parseCh == ' '.code) nextChar()
        if (parseCh == charToEat) {
            nextChar()
            return true
        }
        return false
    }

    private fun evaluateExpression(expr: String): Double {
        parseCleanExpr = expr.replace("×", "*").replace("÷", "/")
        parsePos = -1
        parseCh = -1
        nextChar()
        val result = parseExpr()
        if (parsePos < parseCleanExpr.length) throw IllegalArgumentException("Unexpected: " + parseCh.toChar())
        return result
    }

    private fun parseExpr(): Double {
        var x = parseTerm()
        while (true) {
            if (eat('+'.code)) x += parseTerm()
            else if (eat('-'.code)) x -= parseTerm()
            else return x
        }
    }

    private fun parseTerm(): Double {
        var x = parseFactor()
        while (true) {
            if (eat('*'.code)) x *= parseFactor()
            else if (eat('/'.code)) {
                val divisor = parseFactor()
                if (divisor == 0.0) throw ArithmeticException("Division by zero")
                x /= divisor
            } else return x
        }
    }

    private fun parseFactor(): Double {
        if (eat('+'.code)) return parseFactor()
        if (eat('-'.code)) return -parseFactor()

        var x: Double
        val startPos = parsePos
        if (eat('('.code)) {
            x = parseExpr()
            eat(')'.code)
        } else if ((parseCh >= '0'.code && parseCh <= '9'.code) || parseCh == '.'.code) {
            while ((parseCh >= '0'.code && parseCh <= '9'.code) || parseCh == '.'.code) nextChar()
            val substring = parseCleanExpr.substring(startPos, parsePos)
            x = substring.toDoubleOrNull() ?: 0.0
        } else {
            throw IllegalArgumentException("Unexpected character: " + parseCh.toChar())
        }
        return x
    }

    // --- Option 4: Secret Vault Methods ---
    fun getVaultPin(): String {
        return _vaultPin.value
    }

    fun setVaultPin(newPin: String) {
        prefs.edit().putString("vault_pin", newPin).commit()
        _vaultPin.value = newPin
    }

    fun getDecoyPin(): String {
        return _decoyPin.value
    }

    fun setDecoyPin(newPin: String) {
        prefs.edit().putString("decoy_pin", newPin).commit()
        _decoyPin.value = newPin
    }

    fun loadVaultData() {
        viewModelScope.launch(Dispatchers.IO) {
            val isDecoy = _decoyActive.value
            val notesKey = if (isDecoy) "decoy_notes" else "vault_notes"
            val filesKey = if (isDecoy) "decoy_files" else "vault_files"

            val savedNotes = prefs.getStringSet(notesKey, emptySet()) ?: emptySet()
            _vaultNotes.value = savedNotes.toList().sortedByDescending { it }

            val savedFiles = prefs.getStringSet(filesKey, emptySet()) ?: emptySet()
            _vaultFiles.value = savedFiles.toList().sortedByDescending { it }

            // --- Load & Auto-cleanup Recently Deleted Files ---
            val recentKey = if (isDecoy) "recently_deleted_decoy_files" else "recently_deleted_files"
            val savedRecent = prefs.getStringSet(recentKey, emptySet()) ?: emptySet()
            
            val currentMillis = System.currentTimeMillis()
            val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000
            val validRecent = mutableListOf<String>()
            val toDeleteFiles = mutableListOf<String>()

            for (item in savedRecent) {
                val parts = item.split("|||")
                if (parts.size >= 7) {
                    val deletedTime = parts[6].toLongOrNull() ?: 0L
                    if (currentMillis - deletedTime >= thirtyDaysMillis) {
                        toDeleteFiles.add(item)
                    } else {
                        validRecent.add(item)
                    }
                } else {
                    // Default fallback
                    validRecent.add(item)
                }
            }

            // Permanently delete expired files from disk
            for (item in toDeleteFiles) {
                try {
                    val parts = item.split("|||")
                    if (parts.size >= 5) {
                        val file = File(parts[4])
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (toDeleteFiles.isNotEmpty()) {
                prefs.edit().putStringSet(recentKey, validRecent.toSet()).apply()
            }

            _recentlyDeletedFiles.value = validRecent.sortedByDescending {
                val parts = it.split("|||")
                if (parts.size >= 7) parts[6].toLongOrNull() ?: 0L else 0L
            }

            loadFolders()
            loadFolderAssociations()
            loadFavorites()
            loadRecentlyOpened()
            loadQrScanHistory()
        }
    }

    fun tryUnlockVault(pin: String): Boolean {
        if (pin == getVaultPin()) {
            unlockVault(isDecoy = false)
            return true
        } else if (pin == getDecoyPin()) {
            unlockVault(isDecoy = true)
            return true
        } else {
            if (pin.all { it.isDigit() } && pin.length >= 4) {
                if (_intruderDetectionEnabled.value) {
                    logFailedUnlockAttempt(pin)
                }
            }
        }
        return false
    }

    fun verifyFolderPin(pin: String): Boolean {
        val expectedPin = if (_decoyActive.value) getDecoyPin() else getVaultPin()
        return pin == expectedPin
    }

    fun unlockVault(isDecoy: Boolean) {
        consecutiveFailedAttempts = 0
        _decoyActive.value = isDecoy
        loadVaultData()
        updateLastInteraction()
        _vaultUnlocked.value = true
    }

    fun lockVault() {
        _vaultUnlocked.value = false
        _decoyActive.value = false
        _expression.value = ""
        _calcResult.value = ""
        _isEvaluated.value = false
        clearTempUnlockedFolders()
        loadVaultData()
    }

    fun logFailedUnlockAttempt(pin: String) {
        val count = consecutiveFailedAttempts + 1
        consecutiveFailedAttempts = count

        val timestamp = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(java.util.Date())
        val attemptSerialized = "$timestamp|||$pin||||||$count"
        val updatedAttempts = listOf(attemptSerialized) + _intruderAttempts.value
        val limitedAttempts = updatedAttempts.take(50) // keep last 50
        _intruderAttempts.value = limitedAttempts
        prefs.edit().putStringSet("intruder_attempts", limitedAttempts.toSet()).apply()

        // Check if we should capture an intruder selfie
        if (_intruderSelfieEnabled.value && count >= _failedAttemptsThreshold.value) {
            captureIntruderSelfie()
        }
    }

    fun captureIntruderSelfie() {
        val context = getApplication<Application>()
        val cameraPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        )
        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.w("Vault", "Camera permission not granted for Intruder Selfie")
            return
        }

        viewModelScope.launch(Dispatchers.Main) {
            try {
                val cameraProviderFuture = CameraInitializer.initAndGetProvider(context)
                cameraProviderFuture.addListener({
                    var cameraProvider: ProcessCameraProvider? = null
                    try {
                        cameraProvider = cameraProviderFuture.get()

                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()

                        val selfieDir = File(context.filesDir, "intruder_selfies")
                        if (!selfieDir.exists()) {
                            selfieDir.mkdirs()
                        }
                        val id = System.currentTimeMillis().toString()
                        val destFile = File(selfieDir, "selfie_$id.jpg")

                        val outputOpts = ImageCapture.OutputFileOptions.Builder(destFile).build()

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            ProcessLifecycleOwner.get(),
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            imageCapture
                        )

                        imageCapture.takePicture(
                            outputOpts,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    android.util.Log.i("Vault", "Intruder selfie saved successfully: ${destFile.absolutePath}")
                                    addSelfieToLastAttempt(destFile.absolutePath)
                                    cameraProvider.unbindAll()
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    android.util.Log.e("Vault", "Intruder selfie capture failed: ${exception.message}", exception)
                                    cameraProvider.unbindAll()
                                }
                            }
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("Vault", "Error starting camera in callback", e)
                        try {
                            cameraProvider?.unbindAll()
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (e: Exception) {
                android.util.Log.e("Vault", "Error in captureIntruderSelfie execution", e)
            }
        }
    }

    fun addSelfieToLastAttempt(photoPath: String) {
        val currentList = _intruderAttempts.value
        if (currentList.isNotEmpty()) {
            val lastAttempt = currentList.first()
            val parts = lastAttempt.split("|||")
            if (parts.size >= 2) {
                val timestamp = parts[0]
                val pin = parts[1]
                val failedCount = if (parts.size >= 4) parts[3] else "1"
                val updatedLast = "$timestamp|||$pin|||$photoPath|||$failedCount"
                val updatedList = listOf(updatedLast) + currentList.drop(1)
                _intruderAttempts.value = updatedList
                prefs.edit().putStringSet("intruder_attempts", updatedList.toSet()).apply()
            }
        }
    }

    fun clearIntruderAttempts() {
        _intruderAttempts.value = emptyList()
        prefs.edit().remove("intruder_attempts").apply()
        // Also delete any saved selfie photos to clean up disk
        try {
            val selfieDir = File(getApplication<Application>().filesDir, "intruder_selfies")
            if (selfieDir.exists()) {
                selfieDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addVaultNote(title: String, content: String): String {
        val timestamp = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(java.util.Date())
        val noteSerialized = "$timestamp|||$title|||$content"
        val updatedNotes = _vaultNotes.value + noteSerialized
        _vaultNotes.value = updatedNotes.sortedByDescending { it }
        
        val isDecoy = _decoyActive.value
        val notesKey = if (isDecoy) "decoy_notes" else "vault_notes"
        prefs.edit().putStringSet(notesKey, updatedNotes.toSet()).apply()
        return noteSerialized
    }

    fun editVaultNote(oldNoteSerialized: String, newTitle: String, newContent: String) {
        val parts = oldNoteSerialized.split("|||")
        val timestamp = if (parts.isNotEmpty()) parts[0] else java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(java.util.Date())
        val newNoteSerialized = "$timestamp|||$newTitle|||$newContent"
        
        val updatedNotes = _vaultNotes.value.map { 
            if (it == oldNoteSerialized) newNoteSerialized else it 
        }
        _vaultNotes.value = updatedNotes.sortedByDescending { it }
        
        val isDecoy = _decoyActive.value
        val notesKey = if (isDecoy) "decoy_notes" else "vault_notes"
        prefs.edit().putStringSet(notesKey, updatedNotes.toSet()).apply()
        
        val currentFolders = _noteFolders.value.toMutableMap()
        if (currentFolders.containsKey(oldNoteSerialized)) {
            val folder = currentFolders.remove(oldNoteSerialized)!!
            currentFolders[newNoteSerialized] = folder
            prefs.edit().remove("folder_note_$oldNoteSerialized")
                .putString("folder_note_$newNoteSerialized", folder).apply()
            _noteFolders.value = currentFolders
        }
        
        val currentFavs = _favoriteNotes.value.toMutableSet()
        if (currentFavs.contains(oldNoteSerialized)) {
            currentFavs.remove(oldNoteSerialized)
            currentFavs.add(newNoteSerialized)
            _favoriteNotes.value = currentFavs
            prefs.edit().putStringSet("favorite_notes", currentFavs).apply()
        }

        val currentPinned = _pinnedNotes.value.toMutableSet()
        if (currentPinned.contains(oldNoteSerialized)) {
            currentPinned.remove(oldNoteSerialized)
            currentPinned.add(newNoteSerialized)
            _pinnedNotes.value = currentPinned
            prefs.edit().putStringSet("pinned_notes", currentPinned).apply()
        }
    }

    fun deleteVaultNote(noteSerialized: String) {
        val updatedNotes = _vaultNotes.value - noteSerialized
        _vaultNotes.value = updatedNotes
        val isDecoy = _decoyActive.value
        val notesKey = if (isDecoy) "decoy_notes" else "vault_notes"
        prefs.edit().putStringSet(notesKey, updatedNotes.toSet()).apply()

        val currentPinned = _pinnedNotes.value.toMutableSet()
        if (currentPinned.contains(noteSerialized)) {
            currentPinned.remove(noteSerialized)
            _pinnedNotes.value = currentPinned
            prefs.edit().putStringSet("pinned_notes", currentPinned).apply()
        }
    }

    // --- Option 4: Secret Vault File Management ---
    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        val index = if (digitGroups < units.size) digitGroups else units.size - 1
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, index.toDouble())) + " " + units[index]
    }

    private fun checkDocumentSupportsDelete(context: Context, uri: Uri): Boolean {
        if (!android.provider.DocumentsContract.isDocumentUri(context, uri)) {
            return false
        }
        return try {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(android.provider.DocumentsContract.Document.COLUMN_FLAGS),
                null,
                null,
                null
            )
            var supportsDelete = false
            cursor?.use {
                if (it.moveToFirst()) {
                    val flags = it.getInt(0)
                    supportsDelete = (flags and android.provider.DocumentsContract.Document.FLAG_SUPPORTS_DELETE) != 0
                }
            }
            supportsDelete
        } catch (e: Exception) {
            android.util.Log.e("Vault", "Error checking FLAG_SUPPORTS_DELETE for $uri", e)
            false
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var name = ""
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        name = it.getString(nameIdx) ?: ""
                    }
                }
            }
            if (name.isNotEmpty()) name else uri.lastPathSegment ?: "unnamed_file"
        } catch (e: Exception) {
            uri.lastPathSegment ?: "unnamed_file"
        }
    }

    
    fun batchDeleteOriginalFiles(context: Context, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = context.contentResolver
            val authoritativeUris = mutableListOf<Uri>()
            val documentUrisToDelete = mutableListOf<Uri>()
            val pathsToScan = mutableListOf<String>()

            for (uri in uris) {
                var resolvedUri = uri
                var originalName = "unnamed_file"
                var mimeType = "application/octet-stream"
                var size = 0L
                var originalPath = ""

                try {
                    val cursor = resolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIdx != -1) {
                                val nameVal = it.getString(nameIdx)
                                if (!nameVal.isNullOrEmpty()) originalName = nameVal
                            }
                            val sizeIdx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (sizeIdx != -1) size = it.getLong(sizeIdx)
                            val dataIdx = it.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                            if (dataIdx != -1) originalPath = it.getString(dataIdx) ?: ""
                        }
                    }
                } catch (e: Exception) {}

                mimeType = resolver.getType(uri) ?: mimeType
                if (mimeType.isEmpty() || mimeType == "application/octet-stream") {
                    val ext = java.io.File(originalName).extension.lowercase()
                    mimeType = when (ext) {
                        "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp" -> "image/$ext"
                        "mp4", "mkv", "3gp", "avi", "mov", "webm" -> "video/$ext"
                        "pdf" -> "application/pdf"
                        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        "doc" -> "application/msword"
                        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        "xls" -> "application/vnd.ms-excel"
                        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                        "ppt" -> "application/vnd.ms-powerpoint"
                        "txt", "csv", "log" -> "text/plain"
                        "zip" -> "application/zip"
                        else -> "application/octet-stream"
                    }
                }

                if (originalPath.isNotEmpty()) {
                    pathsToScan.add(originalPath)
                    try {
                        val f = java.io.File(originalPath)
                        if (f.exists() && f.delete()) {
                            android.util.Log.d("Vault", "Direct file deletion success: $originalPath")
                        }
                    } catch (e: Exception) {}
                }

                if (uri.scheme == "file") {
                    try {
                        val f = java.io.File(uri.path ?: "")
                        if (f.exists() && f.delete()) {
                            android.util.Log.d("Vault", "Direct file URI deletion success: ${uri.path}")
                        }
                    } catch (e: Exception) {}
                }

                // Step 1.1: Try resolving via DocumentsContract if it's com.android.providers.media.documents
                if (android.provider.DocumentsContract.isDocumentUri(context, uri) && uri.authority == "com.android.providers.media.documents") {
                    try {
                        val docId = android.provider.DocumentsContract.getDocumentId(uri)
                        val split = docId.split(":")
                        if (split.size >= 2) {
                            val type = split[0]
                            val mediaId = split[1].toLongOrNull()
                            if (mediaId != null) {
                                val baseUri = when (type) {
                                    "image" -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                    "video" -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                    "audio" -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                                    else -> android.provider.MediaStore.Files.getContentUri("external")
                                }
                                resolvedUri = android.content.ContentUris.withAppendedId(baseUri, mediaId)
                            }
                        }
                    } catch (e: Exception) {}
                }

                // Step 1.2: Resolve non-media external URIs using display name & size or path query
                if (!resolvedUri.toString().contains("media/external")) {
                    try {
                        if (resolvedUri.toString().contains("photopicker")) {
                            val mediaId = resolvedUri.lastPathSegment?.toLongOrNull()
                            if (mediaId != null) {
                                val baseUri = when {
                                    mimeType.startsWith("image/") -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                    mimeType.startsWith("video/") -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                    else -> android.provider.MediaStore.Files.getContentUri("external")
                                }
                                resolvedUri = android.content.ContentUris.withAppendedId(baseUri, mediaId)
                            }
                        }
                        if (!resolvedUri.toString().contains("media/external") && originalPath.isNotEmpty()) {
                            val baseUri = when {
                                mimeType.startsWith("image/") -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                mimeType.startsWith("video/") -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                mimeType.startsWith("audio/") -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                                else -> android.provider.MediaStore.Files.getContentUri("external")
                            }
                            val mediaCursor = resolver.query(
                                baseUri,
                                arrayOf(android.provider.MediaStore.MediaColumns._ID),
                                "${android.provider.MediaStore.MediaColumns.DATA} = ?",
                                arrayOf(originalPath),
                                null
                            )
                            mediaCursor?.use {
                                if (it.moveToFirst()) {
                                    val mediaId = it.getLong(it.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID))
                                    resolvedUri = android.content.ContentUris.withAppendedId(baseUri, mediaId)
                                }
                            }
                        }
                        if (!resolvedUri.toString().contains("media/external") && originalName != "unnamed_file" && size > 0L) {
                            val baseUri = when {
                                mimeType.startsWith("image/") -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                mimeType.startsWith("video/") -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                mimeType.startsWith("audio/") -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                                else -> android.provider.MediaStore.Files.getContentUri("external")
                            }
                            val mediaCursor = resolver.query(
                                baseUri,
                                arrayOf(android.provider.MediaStore.MediaColumns._ID),
                                "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${android.provider.MediaStore.MediaColumns.SIZE} = ?",
                                arrayOf(originalName, size.toString()),
                                null
                            )
                            mediaCursor?.use {
                                if (it.moveToFirst()) {
                                    val mediaId = it.getLong(it.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID))
                                    resolvedUri = android.content.ContentUris.withAppendedId(baseUri, mediaId)
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }

                val isMediaStoreMedia = resolvedUri.toString().let {
                    it.contains("media/external/images") || 
                    it.contains("media/external/video") || 
                    it.contains("media/external/audio")
                }

                if (isMediaStoreMedia) {
                    authoritativeUris.add(resolvedUri)
                } else {
                    documentUrisToDelete.add(uri)
                }
            }

            // Step 2: Handle SAF Document Uri direct deletion in background
            if (documentUrisToDelete.isNotEmpty()) {
                val deletionFailedReasons = mutableListOf<String>()
                for (docUri in documentUrisToDelete) {
                    try {
                        if (android.provider.DocumentsContract.isDocumentUri(context, docUri)) {
                            // Verify FLAG_SUPPORTS_DELETE
                            val supportsDelete = checkDocumentSupportsDelete(context, docUri)
                            if (!supportsDelete) {
                                android.util.Log.w("Vault", "Document does not declare FLAG_SUPPORTS_DELETE for $docUri. Trying direct deletion anyway...")
                            }
                            val deleted = android.provider.DocumentsContract.deleteDocument(resolver, docUri)
                            if (deleted) {
                                android.util.Log.d("Vault", "batchDeleteOriginalFiles direct SAF document deletion success: $docUri")
                            } else {
                                val reason = if (supportsDelete) "DocumentsContract.deleteDocument returned false" else "DocumentsContract.deleteDocument returned false (and FLAG_SUPPORTS_DELETE was not set)"
                                android.util.Log.e("Vault", "Failed to delete $docUri: $reason")
                                deletionFailedReasons.add("File ${getFileName(context, docUri)}: $reason")
                            }
                        } else {
                            val rows = resolver.delete(docUri, null, null)
                            if (rows > 0) {
                                android.util.Log.d("Vault", "batchDeleteOriginalFiles direct resolver deletion success: $docUri")
                            } else {
                                val reason = "resolver.delete returned 0 rows"
                                android.util.Log.e("Vault", "Failed to delete $docUri: $reason")
                                deletionFailedReasons.add("File ${getFileName(context, docUri)}: $reason")
                            }
                        }
                    } catch (se: SecurityException) {
                        val reason = "SecurityException - No delete permission (Is persistable permission taken?): ${se.localizedMessage}"
                        android.util.Log.e("Vault", "Permission denial deleting $docUri", se)
                        deletionFailedReasons.add("File ${getFileName(context, docUri)}: $reason")
                    } catch (e: Exception) {
                        val reason = "Exception: ${e.localizedMessage}"
                        android.util.Log.e("Vault", "Failed to delete document $docUri", e)
                        deletionFailedReasons.add("File ${getFileName(context, docUri)}: $reason")
                    }
                }

                if (deletionFailedReasons.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val errorMessage = "Failed to delete original files from device storage:\n" + deletionFailedReasons.joinToString("\n")
                        android.widget.Toast.makeText(context, errorMessage, android.widget.Toast.LENGTH_LONG).show()
                    }
                }

                withContext(Dispatchers.Main) {
                    onOriginalFileDeleted(context)
                }
            }

            // Step 3: Handle authoritative MediaStore URIs deletion using version logic
            if (authoritativeUris.isNotEmpty()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    try {
                        val pendingIntent = android.provider.MediaStore.createDeleteRequest(resolver, authoritativeUris)
                        withContext(Dispatchers.Main) {
                            _pendingDeleteSender.value = pendingIntent.intentSender
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Could not show delete prompt. Please delete the original file manually.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                } else if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.Q) {
                    try {
                        for (authUri in authoritativeUris) {
                            resolver.delete(authUri, null, null)
                        }
                        withContext(Dispatchers.Main) {
                            onOriginalFileDeleted(context)
                        }
                    } catch (securityException: SecurityException) {
                        val recoverableSecurityException = securityException as? android.app.RecoverableSecurityException
                        if (recoverableSecurityException != null) {
                            withContext(Dispatchers.Main) {
                                _pendingDeleteSender.value = recoverableSecurityException.userAction.actionIntent.intentSender
                            }
                        }
                    }
                } else {
                    for (authUri in authoritativeUris) {
                        resolver.delete(authUri, null, null)
                    }
                    withContext(Dispatchers.Main) {
                        onOriginalFileDeleted(context)
                    }
                }
            }

            // Step 4: Refresh MediaScanner database for deleted files
            if (pathsToScan.isNotEmpty()) {
                try {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        pathsToScan.toTypedArray(),
                        null
                    ) { path, scannedUri ->
                        android.util.Log.d("Vault", "Scanned deleted file: $path -> $scannedUri")
                    }
                } catch (e: Exception) {}
            }
        }
    }

    fun addVaultFile(context: Context, uri: Uri, skipDelete: Boolean = false): Boolean {
        return try {
            val contentResolver = context.contentResolver
            
            var originalName = "unnamed_file"
            var mimeType = "application/octet-stream"
            var size = 0L
            var originalPath = ""
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        val nameVal = it.getString(nameIdx)
                        if (!nameVal.isNullOrEmpty()) originalName = nameVal
                    }
                    val sizeIdx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIdx != -1) size = it.getLong(sizeIdx)
                    val dataIdx = it.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                    if (dataIdx != -1) originalPath = it.getString(dataIdx) ?: ""
                }
            }
            
            mimeType = contentResolver.getType(uri) ?: mimeType
            if (mimeType.isEmpty() || mimeType == "application/octet-stream") {
                val ext = java.io.File(originalName).extension.lowercase()
                mimeType = when (ext) {
                    "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp" -> "image/$ext"
                    "mp4", "mkv", "3gp", "avi", "mov", "webm" -> "video/$ext"
                    "mp3", "wav", "m4a", "ogg", "flac", "aac" -> "audio/$ext"
                    "pdf" -> "application/pdf"
                    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    "doc" -> "application/msword"
                    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    "xls" -> "application/vnd.ms-excel"
                    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    "ppt" -> "application/vnd.ms-powerpoint"
                    "txt", "csv", "log" -> "text/plain"
                    "zip" -> "application/zip"
                    else -> "application/octet-stream"
                }
            }
            
            val readableSize = formatFileSize(size)
            val id = System.currentTimeMillis().toString()
            val timestamp = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            
            val vaultDir = java.io.File(context.filesDir, "vault_files")
            if (!vaultDir.exists()) vaultDir.mkdirs()
            
            val extension = java.io.File(originalName).extension.ifEmpty {
                when {
                    mimeType.startsWith("image/") -> "jpg"
                    mimeType.startsWith("video/") -> "mp4"
                    mimeType.contains("pdf") -> "pdf"
                    mimeType.contains("wordprocessingml") || mimeType.contains("docx") -> "docx"
                    mimeType.contains("msword") || mimeType.contains("doc") -> "doc"
                    mimeType.contains("text") -> "txt"
                    else -> "dat"
                }
            }
            
            val destFileName = "$id.$extension"
            val destFile = java.io.File(vaultDir, destFileName)
            
            contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            if (!destFile.exists() || destFile.length() == 0L) return false
            
            var durationMs = 0L
            if (mimeType.startsWith("video/")) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(destFile.absolutePath)
                    val timeStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    durationMs = timeStr?.toLongOrNull() ?: 0L
                    retriever.release()
                } catch (e: Exception) {}
            }
            
            val fileSerialized = "$id|||$timestamp|||$originalName|||$mimeType|||${destFile.absolutePath}|||$readableSize|||$durationMs"
            stagedVaultFiles.add(fileSerialized)

            if (skipDelete) {
                return true
            }

            var resolvedUri = uri
            if (android.provider.DocumentsContract.isDocumentUri(context, uri) && uri.authority == "com.android.providers.media.documents") {
                try {
                    val docId = android.provider.DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":")
                    if (split.size >= 2) {
                        val type = split[0]
                        val mediaId = split[1].toLongOrNull()
                        if (mediaId != null) {
                            val baseUri = when (type) {
                                "image" -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                "video" -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                "audio" -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                                else -> android.provider.MediaStore.Files.getContentUri("external")
                            }
                            resolvedUri = android.content.ContentUris.withAppendedId(baseUri, mediaId)
                        }
                    }
                } catch (e: Exception) {}
            }
            
            if (!resolvedUri.toString().contains("media/external")) {
                try {
                    if (resolvedUri.toString().contains("photopicker")) {
                        val mediaId = resolvedUri.lastPathSegment?.toLongOrNull()
                        if (mediaId != null) {
                            val baseUri = when {
                                mimeType.startsWith("image/") -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                mimeType.startsWith("video/") -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                else -> android.provider.MediaStore.Files.getContentUri("external")
                            }
                            resolvedUri = android.content.ContentUris.withAppendedId(baseUri, mediaId)
                        }
                    }
                    if (!resolvedUri.toString().contains("media/external") && originalPath.isNotEmpty()) {
                        val baseUri = when {
                            mimeType.startsWith("image/") -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            mimeType.startsWith("video/") -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            mimeType.startsWith("audio/") -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                            else -> android.provider.MediaStore.Files.getContentUri("external")
                        }
                        val mediaCursor = contentResolver.query(
                            baseUri,
                            arrayOf(android.provider.MediaStore.MediaColumns._ID),
                            "${android.provider.MediaStore.MediaColumns.DATA} = ?",
                            arrayOf(originalPath),
                            null
                        )
                        mediaCursor?.use {
                            if (it.moveToFirst()) {
                                val mediaId = it.getLong(it.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID))
                                resolvedUri = android.content.ContentUris.withAppendedId(baseUri, mediaId)
                            }
                        }
                    }
                    if (!resolvedUri.toString().contains("media/external") && originalName != "unnamed_file" && size > 0L) {
                        val baseUri = when {
                            mimeType.startsWith("image/") -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            mimeType.startsWith("video/") -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            mimeType.startsWith("audio/") -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                            else -> android.provider.MediaStore.Files.getContentUri("external")
                        }
                        val mediaCursor = contentResolver.query(
                            baseUri,
                            arrayOf(android.provider.MediaStore.MediaColumns._ID),
                            "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${android.provider.MediaStore.MediaColumns.SIZE} = ?",
                            arrayOf(originalName, size.toString()),
                            null
                        )
                        mediaCursor?.use {
                            if (it.moveToFirst()) {
                                val mediaId = it.getLong(it.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID))
                                resolvedUri = android.content.ContentUris.withAppendedId(baseUri, mediaId)
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
            
            if (originalPath.isNotEmpty()) {
                try {
                    val f = java.io.File(originalPath)
                    if (f.exists() && f.delete()) {
                        android.util.Log.d("Vault", "addVaultFile direct file deletion success: $originalPath")
                    }
                } catch (e: Exception) {}
            }
            if (uri.scheme == "file") {
                try {
                    val f = java.io.File(uri.path ?: "")
                    if (f.exists() && f.delete()) {
                        android.util.Log.d("Vault", "addVaultFile direct file URI deletion success: ${uri.path}")
                    }
                } catch (e: Exception) {}
            }

            val isMediaStoreMedia = resolvedUri.toString().let {
                it.contains("media/external/images") || 
                it.contains("media/external/video") || 
                it.contains("media/external/audio")
            }

            if (!isMediaStoreMedia) {
                try {
                    if (android.provider.DocumentsContract.isDocumentUri(context, resolvedUri)) {
                        val supportsDelete = checkDocumentSupportsDelete(context, resolvedUri)
                        if (!supportsDelete) {
                            android.util.Log.w("Vault", "Document does not declare FLAG_SUPPORTS_DELETE for $resolvedUri. Trying direct deletion anyway...")
                        }
                        val deleted = android.provider.DocumentsContract.deleteDocument(contentResolver, resolvedUri)
                        if (deleted) {
                            android.util.Log.d("Vault", "addVaultFile direct SAF document deletion success: $resolvedUri")
                        } else {
                            val reason = if (supportsDelete) "DocumentsContract.deleteDocument returned false" else "DocumentsContract.deleteDocument returned false (and FLAG_SUPPORTS_DELETE was not set)"
                            android.util.Log.e("Vault", "Failed to delete $resolvedUri: $reason")
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                android.widget.Toast.makeText(context, "Could not delete original file:\n$reason", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        val rows = contentResolver.delete(resolvedUri, null, null)
                        if (rows > 0) {
                            android.util.Log.d("Vault", "addVaultFile direct content resolver deletion success: $resolvedUri")
                        } else {
                            val reason = "contentResolver.delete returned 0 rows"
                            android.util.Log.e("Vault", "Failed to delete $resolvedUri: $reason")
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                android.widget.Toast.makeText(context, "Could not delete original file:\n$reason", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } catch (se: SecurityException) {
                    val reason = "Permission denial - Is persistable permission taken?: ${se.localizedMessage}"
                    android.util.Log.e("Vault", "Permission denial deleting non-MediaStore URI: $resolvedUri", se)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(context, "Could not delete original file:\n$reason", android.widget.Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    val reason = "Exception: ${e.localizedMessage}"
                    android.util.Log.e("Vault", "addVaultFile failed to delete non-MediaStore URI: $resolvedUri", e)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(context, "Could not delete original file:\n$reason", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
                onOriginalFileDeleted(context)
                return true
            }

            val uriToPersist = resolvedUri.toString()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    val pendingIntent = android.provider.MediaStore.createDeleteRequest(contentResolver, listOf(resolvedUri))
                    pendingDeleteOriginalPaths = listOf(uriToPersist)
                    _pendingDeleteSender.value = pendingIntent.intentSender
                } catch (e: Exception) {
                    android.util.Log.e("Vault", "createDeleteRequest failed", e)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(context, "Could not show delete prompt. Please delete manually.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    contentResolver.delete(resolvedUri, null, null)
                    pendingDeleteOriginalPaths = listOf(uriToPersist)
                    onOriginalFileDeleted(context)
                } catch (securityException: SecurityException) {
                    val recoverableSecurityException = securityException as? android.app.RecoverableSecurityException
                        ?: throw securityException
                    pendingDeleteOriginalPaths = listOf(uriToPersist)
                    _pendingDeleteSender.value = recoverableSecurityException.userAction.actionIntent.intentSender
                }
            } else {
                contentResolver.delete(resolvedUri, null, null)
                pendingDeleteOriginalPaths = listOf(uriToPersist)
                onOriginalFileDeleted(context)
            }
            return true
        } catch (e: Exception) {
            android.util.Log.e("Vault", "Exception", e)
            return false
        }
    }
    fun deleteVaultFile(fileSerialized: String): Boolean {
        return try {
            val isDecoy = _decoyActive.value
            val filesKey = if (isDecoy) "decoy_files" else "vault_files"
            val recentKey = if (isDecoy) "recently_deleted_decoy_files" else "recently_deleted_files"

            // Remove from main vault
            val updatedFiles = _vaultFiles.value - fileSerialized
            _vaultFiles.value = updatedFiles
            prefs.edit().putStringSet(filesKey, updatedFiles.toSet()).apply()

            // Add to Recently Deleted with current timestamp
            val deletionTime = System.currentTimeMillis().toString()
            val recentSerialized = if (fileSerialized.split("|||").size >= 7) {
                fileSerialized
            } else {
                "$fileSerialized|||$deletionTime"
            }

            val savedRecent = prefs.getStringSet(recentKey, emptySet()) ?: emptySet()
            val updatedRecent = savedRecent + recentSerialized
            prefs.edit().putStringSet(recentKey, updatedRecent).apply()

            _recentlyDeletedFiles.value = updatedRecent.toList().sortedByDescending {
                val parts = it.split("|||")
                if (parts.size >= 7) parts[6].toLongOrNull() ?: 0L else 0L
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun restoreFromRecent(recentSerialized: String): Boolean {
        return try {
            val isDecoy = _decoyActive.value
            val filesKey = if (isDecoy) "decoy_files" else "vault_files"
            val recentKey = if (isDecoy) "recently_deleted_decoy_files" else "recently_deleted_files"

            // Remove from recently deleted
            val savedRecent = prefs.getStringSet(recentKey, emptySet()) ?: emptySet()
            val updatedRecent = savedRecent - recentSerialized
            prefs.edit().putStringSet(recentKey, updatedRecent).apply()

            _recentlyDeletedFiles.value = updatedRecent.toList().sortedByDescending {
                val parts = it.split("|||")
                if (parts.size >= 7) parts[6].toLongOrNull() ?: 0L else 0L
            }

            // Restore to main vault (strip the 7th part - deletion timestamp)
            val parts = recentSerialized.split("|||")
            val originalSerialized = parts.take(6).joinToString("|||")

            val updatedFiles = _vaultFiles.value + originalSerialized
            _vaultFiles.value = updatedFiles.sortedByDescending { it }
            prefs.edit().putStringSet(filesKey, updatedFiles.toSet()).apply()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deletePermanentlyFromRecent(recentSerialized: String): Boolean {
        return try {
            val isDecoy = _decoyActive.value
            val recentKey = if (isDecoy) "recently_deleted_decoy_files" else "recently_deleted_files"

            // Remove from recently deleted
            val savedRecent = prefs.getStringSet(recentKey, emptySet()) ?: emptySet()
            val updatedRecent = savedRecent - recentSerialized
            prefs.edit().putStringSet(recentKey, updatedRecent).apply()

            _recentlyDeletedFiles.value = updatedRecent.toList().sortedByDescending {
                val parts = it.split("|||")
                if (parts.size >= 7) parts[6].toLongOrNull() ?: 0L else 0L
            }

            // Delete physical file
            val parts = recentSerialized.split("|||")
            if (parts.size >= 5) {
                val absolutePath = parts[4]
                val file = File(absolutePath)
                if (file.exists()) {
                    file.delete()
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun emptyRecentlyDeleted(): Boolean {
        return try {
            val isDecoy = _decoyActive.value
            val recentKey = if (isDecoy) "recently_deleted_decoy_files" else "recently_deleted_files"
            val savedRecent = prefs.getStringSet(recentKey, emptySet()) ?: emptySet()
            for (recentSerialized in savedRecent) {
                val parts = recentSerialized.split("|||")
                if (parts.size >= 5) {
                    val absolutePath = parts[4]
                    val file = File(absolutePath)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
            prefs.edit().putStringSet(recentKey, emptySet()).apply()
            _recentlyDeletedFiles.value = emptyList()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun permanentlyDeleteVaultFile(fileSerialized: String): Boolean {
        return try {
            val isDecoy = _decoyActive.value
            val filesKey = if (isDecoy) "decoy_files" else "vault_files"

            // Remove from main vault
            val updatedFiles = _vaultFiles.value - fileSerialized
            _vaultFiles.value = updatedFiles
            prefs.edit().putStringSet(filesKey, updatedFiles.toSet()).apply()

            // Delete physical file
            val parts = fileSerialized.split("|||")
            if (parts.size >= 5) {
                val absolutePath = parts[4]
                val file = java.io.File(absolutePath)
                if (file.exists()) {
                    file.delete()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun shareVaultFile(
        context: Context,
        fileSerialized: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val parts = fileSerialized.split("|||")
                if (parts.size < 5) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onFailure("Invalid file information") }
                    return@launch
                }
                val originalName = parts[2]
                var mimeType = parts[3]
                val absolutePath = parts[4]
                val file = java.io.File(absolutePath)
                if (!file.exists()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onFailure("Source file does not exist") }
                    return@launch
                }

                val tempDir = java.io.File(context.cacheDir, "shared_temp")
                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }
                
                val nameWithoutExt = if (originalName.contains(".")) {
                    originalName.substringBeforeLast(".")
                } else {
                    originalName
                }

                val isImage = mimeType.startsWith("image/")
                val applyBranding = _secureShareBranding.value && isImage
                val finalExtension = if (isImage) (if (mimeType.endsWith("png")) "png" else "jpg") else java.io.File(originalName).extension.ifEmpty { "dat" }
                val tempFile = java.io.File(tempDir, "shared_${System.currentTimeMillis()}_$nameWithoutExt.$finalExtension")
                
                if (applyBranding) {
                    val options = android.graphics.BitmapFactory.Options()
                    var bitmap = android.graphics.BitmapFactory.decodeFile(absolutePath, options)
                    if (bitmap == null) {
                        file.inputStream().use { input ->
                            java.io.FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } else {
                        val mutableBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                        if (mutableBitmap != bitmap) {
                            bitmap.recycle()
                        }
                        bitmap = mutableBitmap

                        var rotationDegrees = 0
                        try {
                            val exif = androidx.exifinterface.media.ExifInterface(absolutePath)
                            val orientation = exif.getAttributeInt(
                                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                            )
                            rotationDegrees = when (orientation) {
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                                else -> 0
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        if (rotationDegrees != 0) {
                            val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                            val rotatedBitmap = android.graphics.Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                            )
                            if (rotatedBitmap != bitmap) {
                                bitmap.recycle()
                                bitmap = rotatedBitmap
                            }
                        }

                        val canvas = android.graphics.Canvas(bitmap)
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                        }

                        val baseDimension = minOf(bitmap.width, bitmap.height)
                        val fontSize1 = (baseDimension * 0.035f).coerceIn(12f, 44f)
                        val fontSize2 = (baseDimension * 0.028f).coerceIn(10f, 36f)
                        val margin = (baseDimension * 0.04f).coerceIn(12f, 48f)

                        paint.textSize = fontSize1
                        val text1 = "🔒 SECRET VAULT"
                        val text1Width = paint.measureText(text1)

                        paint.textSize = fontSize2
                        val text2 = "Secure Share"
                        val text2Width = paint.measureText(text2)

                        val maxTextWidth = maxOf(text1Width, text2Width)
                        val startX = bitmap.width - maxTextWidth - margin
                        
                        val lineSpacing = fontSize2 * 0.3f
                        val y2 = bitmap.height - margin
                        val y1 = y2 - fontSize2 - lineSpacing

                        // Draw a beautiful dark-tinted rounded card behind text so it's readable on any image background
                        val bgPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            alpha = (255 * 0.65f).toInt()
                            isAntiAlias = true
                            style = android.graphics.Paint.Style.FILL
                        }
                        val paddingH = margin * 0.5f
                        val paddingV = margin * 0.4f
                        val bgRect = android.graphics.RectF(
                            startX - paddingH,
                            y1 - fontSize1 - paddingV,
                            bitmap.width - margin + paddingH,
                            y2 + paddingV
                        )
                        canvas.drawRoundRect(bgRect, margin * 0.3f, margin * 0.3f, bgPaint)

                        paint.textSize = fontSize1
                        paint.color = android.graphics.Color.WHITE
                        paint.alpha = 255
                        canvas.drawText(text1, startX + (maxTextWidth - text1Width), y1, paint)

                        paint.textSize = fontSize2
                        paint.color = android.graphics.Color.YELLOW
                        paint.alpha = 220
                        canvas.drawText(text2, startX + (maxTextWidth - text2Width), y2, paint)

                        java.io.FileOutputStream(tempFile).use { out ->
                            val format = if (mimeType.endsWith("png")) {
                                android.graphics.Bitmap.CompressFormat.PNG
                            } else {
                                android.graphics.Bitmap.CompressFormat.JPEG
                            }
                            bitmap.compress(format, 90, out)
                        }
                        bitmap.recycle()
                        
                        // Update MIME type and format to JPEG/PNG since we compressed it
                        mimeType = if (mimeType.endsWith("png")) "image/png" else "image/jpeg"
                    }
                } else {
                    file.inputStream().use { input ->
                        java.io.FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                val shareUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    tempFile
                )

                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                    clipData = android.content.ClipData.newRawUri("Share File", shareUri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }

                val chooserIntent = android.content.Intent.createChooser(shareIntent, "Secure Share File")
                chooserIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                chooserIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val resInfoList = context.packageManager.queryIntentActivities(shareIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                for (resolveInfo in resInfoList) {
                    val packageName = resolveInfo.activityInfo.packageName
                    context.grantUriPermission(packageName, shareUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }

                context.startActivity(chooserIntent)

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess()
                }

                viewModelScope.launch {
                    kotlinx.coroutines.delay(300_000) // 5 minutes
                    try {
                        if (tempFile.exists()) {
                            tempFile.delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onFailure(e.message ?: "Failed to share file")
                }
            }
        }
    }

    fun shareMultipleVaultFiles(
        context: Context,
        filesSerialized: List<String>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val tempDir = java.io.File(context.cacheDir, "shared_temp")
                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }

                val shareUris = ArrayList<android.net.Uri>()
                var commonMimeType: String? = null
                val applyBranding = _secureShareBranding.value

                for (fileStr in filesSerialized) {
                    val parts = fileStr.split("|||")
                    if (parts.size < 5) continue
                    val originalName = parts[2]
                    var mimeType = parts[3]
                    val absolutePath = parts[4]
                    val file = java.io.File(absolutePath)
                    if (!file.exists()) continue

                    val nameWithoutExt = if (originalName.contains(".")) {
                        originalName.substringBeforeLast(".")
                    } else {
                        originalName
                    }

                    val isImage = mimeType.startsWith("image/")
                    val finalExtension = if (isImage) (if (mimeType.endsWith("png")) "png" else "jpg") else java.io.File(originalName).extension.ifEmpty { "dat" }
                    val tempFile = java.io.File(tempDir, "shared_${System.currentTimeMillis()}_$nameWithoutExt.$finalExtension")

                    if (applyBranding && isImage) {
                        val options = android.graphics.BitmapFactory.Options()
                        var bitmap = android.graphics.BitmapFactory.decodeFile(absolutePath, options)
                        if (bitmap != null) {
                            val mutableBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                            if (mutableBitmap != bitmap) {
                                bitmap.recycle()
                            }
                            bitmap = mutableBitmap

                            var rotationDegrees = 0
                            try {
                                val exif = androidx.exifinterface.media.ExifInterface(absolutePath)
                                val orientation = exif.getAttributeInt(
                                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                                )
                                rotationDegrees = when (orientation) {
                                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                                    else -> 0
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            if (rotationDegrees != 0) {
                                val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                val rotatedBitmap = android.graphics.Bitmap.createBitmap(
                                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                                )
                                if (rotatedBitmap != bitmap) {
                                    bitmap.recycle()
                                    bitmap = rotatedBitmap
                                }
                            }

                            val canvas = android.graphics.Canvas(bitmap)
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                isAntiAlias = true
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                            }

                            val baseDimension = minOf(bitmap.width, bitmap.height)
                            val fontSize1 = (baseDimension * 0.035f).coerceIn(12f, 44f)
                            val fontSize2 = (baseDimension * 0.028f).coerceIn(10f, 36f)
                            val margin = (baseDimension * 0.04f).coerceIn(12f, 48f)

                            paint.textSize = fontSize1
                            val text1 = "🔒 SECRET VAULT"
                            val text1Width = paint.measureText(text1)

                            paint.textSize = fontSize2
                            val text2 = "Secure Share"
                            val text2Width = paint.measureText(text2)

                            val maxTextWidth = maxOf(text1Width, text2Width)
                            val startX = bitmap.width - maxTextWidth - margin
                            
                            val lineSpacing = fontSize2 * 0.3f
                            val y2 = bitmap.height - margin
                            val y1 = y2 - fontSize2 - lineSpacing

                            val bgPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.BLACK
                                alpha = (255 * 0.65f).toInt()
                                isAntiAlias = true
                                style = android.graphics.Paint.Style.FILL
                            }
                            val paddingH = margin * 0.5f
                            val paddingV = margin * 0.4f
                            val bgRect = android.graphics.RectF(
                                startX - paddingH,
                                y1 - fontSize1 - paddingV,
                                bitmap.width - margin + paddingH,
                                y2 + paddingV
                            )
                            canvas.drawRoundRect(bgRect, margin * 0.3f, margin * 0.3f, bgPaint)

                            paint.textSize = fontSize1
                            paint.color = android.graphics.Color.WHITE
                            paint.alpha = 255
                            canvas.drawText(text1, startX + (maxTextWidth - text1Width), y1, paint)

                            paint.textSize = fontSize2
                            paint.color = android.graphics.Color.YELLOW
                            paint.alpha = 220
                            canvas.drawText(text2, startX + (maxTextWidth - text2Width), y2, paint)

                            java.io.FileOutputStream(tempFile).use { out ->
                                val format = if (mimeType.endsWith("png")) {
                                    android.graphics.Bitmap.CompressFormat.PNG
                                } else {
                                    android.graphics.Bitmap.CompressFormat.JPEG
                                }
                                bitmap.compress(format, 90, out)
                            }
                            bitmap.recycle()
                            
                            mimeType = if (mimeType.endsWith("png")) "image/png" else "image/jpeg"
                        } else {
                            file.inputStream().use { input ->
                                java.io.FileOutputStream(tempFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    } else {
                        file.inputStream().use { input ->
                            java.io.FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }

                    if (commonMimeType == null) {
                        commonMimeType = mimeType
                    } else if (commonMimeType != mimeType) {
                        if (commonMimeType.substringBefore("/") != mimeType.substringBefore("/")) {
                            commonMimeType = "*/*"
                        } else {
                            commonMimeType = commonMimeType.substringBefore("/") + "/*"
                        }
                    }

                    val shareUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        tempFile
                    )
                    shareUris.add(shareUri)

                    viewModelScope.launch {
                        kotlinx.coroutines.delay(300_000) // 5 minutes
                        try {
                            if (tempFile.exists()) {
                                tempFile.delete()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                if (shareUris.isEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onFailure("No valid files to share")
                    }
                    return@launch
                }

                val shareIntent = if (shareUris.size == 1) {
                    android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = commonMimeType ?: "*/*"
                        putExtra(android.content.Intent.EXTRA_STREAM, shareUris[0])
                        clipData = android.content.ClipData.newRawUri("Share File", shareUris[0])
                    }
                } else {
                    android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                        type = commonMimeType ?: "*/*"
                        putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, shareUris)
                        val clipData = android.content.ClipData.newRawUri("Share Files", shareUris[0])
                        for (i in 1 until shareUris.size) {
                            clipData.addItem(android.content.ClipData.Item(shareUris[i]))
                        }
                        this.clipData = clipData
                    }
                }
                shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

                val chooserIntent = android.content.Intent.createChooser(shareIntent, "Secure Share")
                chooserIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                chooserIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

                val resInfoList = context.packageManager.queryIntentActivities(shareIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                for (resolveInfo in resInfoList) {
                    val packageName = resolveInfo.activityInfo.packageName
                    for (uri in shareUris) {
                        context.grantUriPermission(packageName, uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }
                }

                context.startActivity(chooserIntent)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onFailure(e.message ?: "Failed to share files")
                }
            }
        }
    }

    @android.annotation.SuppressLint("NewApi")
    fun unhideVaultFile(
        context: Context,
        fileSerialized: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        try {
            val parts = fileSerialized.split("|||")
            if (parts.size < 5) {
                onFailure("Invalid file information")
                return
            }
            val originalName = parts[2]
            val mimeType = parts[3]
            val absolutePath = parts[4]
            val file = File(absolutePath)
            if (!file.exists()) {
                onFailure("Source file does not exist")
                return
            }

            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, originalName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
            }

            var uri: android.net.Uri? = null
            var writeSuccessful = false

            if (mimeType.startsWith("image/")) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/DCIM")
                }
                uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            } else if (mimeType.startsWith("video/")) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_MOVIES + "/DCIM")
                }
                uri = resolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                // Documents & others -> Downloads
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                } else {
                    // pre-Q: Save directly to the public Downloads folder via File API
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs()
                    }
                    var destFile = File(downloadsDir, originalName)
                    if (destFile.exists()) {
                        val baseName = File(originalName).nameWithoutExtension
                        val ext = File(originalName).extension
                        destFile = File(downloadsDir, "${baseName}_${System.currentTimeMillis()}.$ext")
                    }
                    try {
                        file.inputStream().use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        writeSuccessful = true
                        uri = android.net.Uri.fromFile(destFile)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (uri != null) {
                if (!writeSuccessful) {
                    resolver.openOutputStream(uri)?.use { output ->
                        file.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }

                // Refresh MediaStore immediately
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf(mimeType)
                ) { path, scannedUri ->
                    android.util.Log.d("Vault", "Scanned unhidden file: $path -> $scannedUri")
                }

                // Delete original encrypted file from app's private filesDir
                if (file.exists()) {
                    file.delete()
                }

                // Remove its metadata from main vault
                val updatedFiles = _vaultFiles.value - fileSerialized
                _vaultFiles.value = updatedFiles
                val isDecoy = _decoyActive.value
                val filesKey = if (isDecoy) "decoy_files" else "vault_files"
                prefs.edit().putStringSet(filesKey, updatedFiles.toSet()).apply()

                onSuccess("Media restored successfully.")
            } else {
                onFailure("Failed to create MediaStore entry")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onFailure("Error: ${e.localizedMessage ?: e.toString()}")
        }
    }

    @android.annotation.SuppressLint("NewApi")
    fun exportVaultFile(
        context: Context,
        fileSerialized: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        try {
            val parts = fileSerialized.split("|||")
            if (parts.size < 5) {
                onFailure("Invalid file information")
                return
            }
            val originalName = parts[2]
            val mimeType = parts[3]
            val absolutePath = parts[4]
            val file = File(absolutePath)
            if (!file.exists()) {
                onFailure("Source file does not exist")
                return
            }

            val resolver = context.contentResolver
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, originalName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output ->
                        file.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    onSuccess("File exported successfully to Downloads folder!")
                } else {
                    onFailure("Failed to create export file entry")
                }
            } else {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                var destFile = File(downloadsDir, originalName)
                if (destFile.exists()) {
                    val baseName = File(originalName).nameWithoutExtension
                    val ext = File(originalName).extension
                    destFile = File(downloadsDir, "${baseName}_${System.currentTimeMillis()}.$ext")
                }
                file.inputStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                onSuccess("File exported to Downloads: ${destFile.name}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onFailure("Error: ${e.localizedMessage ?: e.toString()}")
        }
    }

    // --- Advanced Stealth & Settings Updaters ---
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
        _biometricEnabled.value = enabled
        if (enabled && !prefs.getBoolean("security_status_improved", false)) {
            prefs.edit()
                .putBoolean("security_status_improved", true)
                .putLong("time_security_improved", System.currentTimeMillis())
                .apply()
        }
    }

    fun setBiometricMode(mode: String) {
        prefs.edit().putString("biometric_mode", mode).apply()
        _biometricMode.value = mode
    }

    fun setSecurityQuestionAndAnswer(question: String, answer: String) {
        val trimmedAnswer = answer.trim().lowercase()
        prefs.edit()
            .putString("security_question", question)
            .putString("security_answer", trimmedAnswer)
            .apply()
        _securityQuestion.value = question
        _securityAnswer.value = trimmedAnswer
    }

    fun generateRecoveryCodeIfNeeded(): String {
        val existing = _recoveryCode.value
        if (existing.isNotEmpty()) return existing
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val code = (1..4).map {
            (1..4).map { chars[(0 until chars.length).random()] }.joinToString("")
        }.joinToString("-")
        prefs.edit().putString("recovery_code", code).apply()
        _recoveryCode.value = code
        return code
    }

    fun exportRecoveryKeyFile(onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        try {
            val code = generateRecoveryCodeIfNeeded()
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val destFile = java.io.File(downloadsDir, "calculator_vault_recovery_key.txt")
            destFile.writeText(
                "--- CALCULATOR VAULT MASTER RECOVERY KEY ---\n" +
                "Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n" +
                "Recovery Key: $code\n\n" +
                "Keep this file safe. It can be used to unlock your private vault if you forget your access PIN.\n" +
                "To recover: Type 999999 and press '=' on the calculator, or long press the 'Calculator' header title, then choose option 4 and load this key.\n"
            )
            onSuccess("Recovery Key saved to Downloads as '${destFile.name}'")
        } catch (e: Exception) {
            e.printStackTrace()
            onFailure("Failed to export key: ${e.localizedMessage ?: e.toString()}")
        }
    }

    fun setPanicEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("panic_enabled", enabled).apply()
        _panicEnabled.value = enabled
        if (enabled && !prefs.getBoolean("security_status_improved", false)) {
            prefs.edit()
                .putBoolean("security_status_improved", true)
                .putLong("time_security_improved", System.currentTimeMillis())
                .apply()
        }
    }

    fun setPanicAction(action: String) {
        prefs.edit().putString("panic_action", action).apply()
        _panicAction.value = action
    }

    fun setPanicExitAction(action: String) {
        prefs.edit().putString("panic_exit_action", action).apply()
        _panicExitAction.value = action
    }



    fun toggleAppLock(appPackageName: String) {
        val current = _lockedApps.value.toMutableSet()
        if (current.contains(appPackageName)) {
            current.remove(appPackageName)
        } else {
            current.add(appPackageName)
        }
        prefs.edit().putStringSet("locked_apps", current).apply()
        _lockedApps.value = current
    }

    fun setPreventScreenshots(enabled: Boolean) {
        prefs.edit().putBoolean("prevent_screenshots", enabled).apply()
        _preventScreenshots.value = enabled
    }

    fun setActiveAppIcon(context: android.content.Context, iconId: String) {
        prefs.edit().putString("active_app_icon", "LauncherCalculator").apply()
        _activeAppIcon.value = "LauncherCalculator"

        try {
            val packageManager = context.packageManager
            val packageName = context.packageName

            val componentName = android.content.ComponentName(packageName, "com.example.LauncherDefault")
            packageManager.setComponentEnabledSetting(
                componentName,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setScreenDownLock(enabled: Boolean) {
        prefs.edit().putBoolean("screen_down_lock", enabled).apply()
        _screenDownLock.value = enabled
    }

    fun setIntruderDetectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("intruder_detection_enabled", enabled).apply()
        _intruderDetectionEnabled.value = enabled
        if (enabled && !prefs.getBoolean("security_status_improved", false)) {
            prefs.edit()
                .putBoolean("security_status_improved", true)
                .putLong("time_security_improved", System.currentTimeMillis())
                .apply()
        }
    }

    fun setIntruderSelfieEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("intruder_selfie_enabled", enabled).apply()
        _intruderSelfieEnabled.value = enabled
        if (enabled && !prefs.getBoolean("security_status_improved", false)) {
            prefs.edit()
                .putBoolean("security_status_improved", true)
                .putLong("time_security_improved", System.currentTimeMillis())
                .apply()
        }
    }

    fun setFailedAttemptsThreshold(count: Int) {
        prefs.edit().putInt("failed_attempts_threshold", count).apply()
        _failedAttemptsThreshold.value = count
    }

    fun setAutoLockDuration(seconds: Int) {
        prefs.edit().putInt("auto_lock_duration", seconds).apply()
        _autoLockDuration.value = seconds
    }

    fun setLockOnBackground(enabled: Boolean) {
        prefs.edit().putBoolean("lock_on_background", enabled).apply()
        _lockOnBackground.value = enabled
    }

    fun setHideNotifications(enabled: Boolean) {
        prefs.edit().putBoolean("hide_notifications", enabled).apply()
        _hideNotifications.value = enabled
    }

    fun setClipboardProtection(enabled: Boolean) {
        prefs.edit().putBoolean("clipboard_protection", enabled).apply()
        _clipboardProtection.value = enabled
    }

    fun copyToClipboard(context: Context, label: String, text: String, isSensitive: Boolean = true) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        if (isSensitive) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                clip.description.extras = android.os.PersistableBundle().apply {
                    putBoolean("android.content.ClipDescription.EXTRA_IS_SENSITIVE", true)
                }
            }
        }
        clipboard.setPrimaryClip(clip)

        if (_clipboardProtection.value) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(30000)
                try {
                    val currentClip = clipboard.primaryClip
                    if (currentClip != null && currentClip.itemCount > 0) {
                        val currentText = currentClip.getItemAt(0).text?.toString()
                        if (currentText == text) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                clipboard.clearPrimaryClip()
                            } else {
                                val emptyClip = android.content.ClipData.newPlainText("", "")
                                clipboard.setPrimaryClip(emptyClip)
                            }
                            android.widget.Toast.makeText(context, "Clipboard cleared for security!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun setStealthMode(enabled: Boolean) {
        prefs.edit().putBoolean("stealth_mode", enabled).apply()
        _stealthMode.value = enabled
    }

    fun setSecureShareBranding(enabled: Boolean) {
        prefs.edit().putBoolean("secure_share_branding", true).apply()
        _secureShareBranding.value = true
    }

    fun setOwnerName(name: String) {
        prefs.edit().putString("owner_name", name).apply()
        _ownerName.value = name
    }

    fun setOwnerAvatarUri(uri: String) {
        prefs.edit().putString("owner_avatar_uri", uri).apply()
        _ownerAvatarUri.value = uri
        _ownerAvatarScale.value = prefs.getFloat("owner_avatar_scale_$uri", 1.0f)
        _ownerAvatarOffsetX.value = prefs.getFloat("owner_avatar_offset_x_$uri", 0f)
        _ownerAvatarOffsetY.value = prefs.getFloat("owner_avatar_offset_y_$uri", 0f)
    }

    fun setOwnerAvatarScale(scale: Float) {
        val uri = _ownerAvatarUri.value
        prefs.edit().putFloat("owner_avatar_scale_$uri", scale).apply()
        _ownerAvatarScale.value = scale
    }

    fun setOwnerAvatarOffsetX(offset: Float) {
        val uri = _ownerAvatarUri.value
        prefs.edit().putFloat("owner_avatar_offset_x_$uri", offset).apply()
        _ownerAvatarOffsetX.value = offset
    }

    fun setOwnerAvatarOffsetY(offset: Float) {
        val uri = _ownerAvatarUri.value
        prefs.edit().putFloat("owner_avatar_offset_y_$uri", offset).apply()
        _ownerAvatarOffsetY.value = offset
    }

    fun setPremiumState(state: String) {
        prefs.edit().putString("premium_state", state).apply()
        _premiumState.value = state
    }

    fun setBlurThumbnails(enabled: Boolean) {
        prefs.edit().putBoolean("blur_thumbnails", enabled).apply()
        _blurThumbnails.value = enabled
    }

    fun toggleFolderLock(folderName: String) {
        val current = _lockedFolders.value.toMutableSet()
        if (current.contains(folderName)) {
            current.remove(folderName)
        } else {
            current.add(folderName)
        }
        prefs.edit().putStringSet("locked_folders", current).apply()
        _lockedFolders.value = current
    }

    fun tempUnlockFolder(folderName: String) {
        _tempUnlockedFolders.value = _tempUnlockedFolders.value + folderName
    }

    fun clearTempUnlockedFolders() {
        _tempUnlockedFolders.value = emptySet()
    }

    fun updateLastInteraction() {
        _lastInteractionTime.value = System.currentTimeMillis()
    }

    fun setFileLossProtection(enabled: Boolean) {
        prefs.edit().putBoolean("file_loss_protection", enabled).apply()
        _fileLossProtection.value = enabled
    }

    // --- Private Browser Downloads Engine ---
    private val _downloads = MutableStateFlow<List<DownloadTask>>(emptyList())
    
    private val _browserBookmarks = MutableStateFlow<List<BrowserBookmark>>(emptyList())
    val browserBookmarks: StateFlow<List<BrowserBookmark>> = _browserBookmarks.asStateFlow()

    private val _browserHistory = MutableStateFlow<List<BrowserHistory>>(emptyList())
    val browserHistory: StateFlow<List<BrowserHistory>> = _browserHistory.asStateFlow()

    private val _searchEngine = MutableStateFlow(prefs.getString("browser_search_engine", "DuckDuckGo") ?: "DuckDuckGo")
    val searchEngine: StateFlow<String> = _searchEngine.asStateFlow()

    private val _savePasswords = MutableStateFlow(prefs.getBoolean("browser_save_passwords", true))
    val savePasswords: StateFlow<Boolean> = _savePasswords.asStateFlow()

    private val _clearHistoryOnExit = MutableStateFlow(prefs.getBoolean("browser_clear_history", false))
    val clearHistoryOnExit: StateFlow<Boolean> = _clearHistoryOnExit.asStateFlow()

    private val _clearTempOnExit = MutableStateFlow(prefs.getBoolean("browser_clear_temp_on_exit", false))
    val clearTempOnExit: StateFlow<Boolean> = _clearTempOnExit.asStateFlow()

    private val _useGeckoView = MutableStateFlow(prefs.getBoolean("browser_use_geckoview", true))
    val useGeckoView: StateFlow<Boolean> = _useGeckoView.asStateFlow()

    private val _trackingProtectionEnabled = MutableStateFlow(prefs.getBoolean("browser_tracking_protection_enabled", true))
    val trackingProtectionEnabled: StateFlow<Boolean> = _trackingProtectionEnabled.asStateFlow()

    private val _trackingSiteEnabled = MutableStateFlow(prefs.getStringSet("browser_tracking_site_enabled", emptySet()) ?: emptySet())
    val trackingSiteEnabled: StateFlow<Set<String>> = _trackingSiteEnabled.asStateFlow()

    private val _trackingSiteDisabled = MutableStateFlow(prefs.getStringSet("browser_tracking_site_disabled", emptySet()) ?: emptySet())
    val trackingSiteDisabled: StateFlow<Set<String>> = _trackingSiteDisabled.asStateFlow()

    private val _totalTrackersBlocked = MutableStateFlow(prefs.getInt("browser_total_trackers_blocked", 0))
    val totalTrackersBlocked: StateFlow<Int> = _totalTrackersBlocked.asStateFlow()

    fun setTrackingProtectionEnabled(enabled: Boolean) {
        _trackingProtectionEnabled.value = enabled
        prefs.edit().putBoolean("browser_tracking_protection_enabled", enabled).apply()
    }

    fun setSiteTrackingProtection(domain: String, enabled: Boolean?) {
        val normDomain = domain.trim().lowercase()
        if (normDomain.isEmpty()) return

        val currentEnabled = _trackingSiteEnabled.value.toMutableSet()
        val currentDisabled = _trackingSiteDisabled.value.toMutableSet()

        when (enabled) {
            true -> {
                currentEnabled.add(normDomain)
                currentDisabled.remove(normDomain)
            }
            false -> {
                currentDisabled.add(normDomain)
                currentEnabled.remove(normDomain)
            }
            null -> {
                currentEnabled.remove(normDomain)
                currentDisabled.remove(normDomain)
            }
        }

        _trackingSiteEnabled.value = currentEnabled
        _trackingSiteDisabled.value = currentDisabled

        prefs.edit()
            .putStringSet("browser_tracking_site_enabled", currentEnabled)
            .putStringSet("browser_tracking_site_disabled", currentDisabled)
            .apply()
    }

    fun addBrowserBookmark(title: String, url: String) {
        if (url == "home" || url == "about:blank" || url.isBlank() ||
            url.startsWith("data:") || url.startsWith("file:") || url.startsWith("about:")) {
            return
        }
        val current = _browserBookmarks.value.toMutableList()
        if (!current.any { it.url == url }) {
            current.add(BrowserBookmark(title, url))
            _browserBookmarks.value = current
            saveBrowserBookmarks(current)
        }
    }

    fun removeBrowserBookmark(url: String) {
        val current = _browserBookmarks.value.toMutableList()
        current.removeAll { it.url == url }
        _browserBookmarks.value = current
        saveBrowserBookmarks(current)
    }

            fun addBrowserHistory(title: String, url: String) {
        val current = _browserHistory.value.toMutableList()
        if (current.isNotEmpty() && current.first().url == url) {
            current[0] = current[0].copy(title = title, timestamp = System.currentTimeMillis())
        } else {
            val existingIndex = current.indexOfFirst { it.url == url }
            if (existingIndex != -1) {
                current.removeAt(existingIndex)
            }
            current.add(0, BrowserHistory(title, url, System.currentTimeMillis()))
            if (current.size > 100) {
                current.removeAt(current.size - 1)
            }
        }
        _browserHistory.value = current
        saveBrowserHistory(current)
    }

    fun clearBrowserHistory() {
        _browserHistory.value = emptyList()
        saveBrowserHistory(emptyList())
    }

    fun deleteBrowserHistoryItem(item: BrowserHistory) {
        val current = _browserHistory.value.toMutableList()
        current.remove(item)
        _browserHistory.value = current
        saveBrowserHistory(current)
    }

    fun setSearchEngine(engine: String) {
        _searchEngine.value = engine
        prefs.edit().putString("browser_search_engine", engine).apply()
    }

    fun setSavePasswords(save: Boolean) {
        _savePasswords.value = save
        prefs.edit().putBoolean("browser_save_passwords", save).apply()
    }

    fun setClearHistoryOnExit(clear: Boolean) {
        _clearHistoryOnExit.value = clear
        prefs.edit().putBoolean("browser_clear_history", clear).apply()
    }

    fun setClearTempOnExit(clear: Boolean) {
        _clearTempOnExit.value = clear
        prefs.edit().putBoolean("browser_clear_temp_on_exit", clear).apply()
    }

    fun setUseGeckoView(use: Boolean) {
        _useGeckoView.value = use
        prefs.edit().putBoolean("browser_use_geckoview", use).apply()
    }

    private fun saveBrowserBookmarks(list: List<BrowserBookmark>) {
        val json = org.json.JSONArray()
        list.forEach { 
            val obj = org.json.JSONObject()
            obj.put("title", it.title)
            obj.put("url", it.url)
            json.put(obj)
        }
        prefs.edit().putString("browser_bookmarks", json.toString()).apply()
    }

    private fun loadBrowserBookmarks() {
        try {
            val jsonStr = prefs.getString("browser_bookmarks", "[]") ?: "[]"
            val json = org.json.JSONArray(jsonStr)
            val list = mutableListOf<BrowserBookmark>()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                list.add(BrowserBookmark(obj.getString("title"), obj.getString("url")))
            }
            _browserBookmarks.value = list
        } catch (e: Exception) {}
    }

    fun triggerSaveTabs() { saveBrowserTabs() }

    private fun saveBrowserTabs() {
        try {
            val json = org.json.JSONArray()
            for (tab in browserTabs) {
                val obj = org.json.JSONObject()
                obj.put("id", tab.id)
                obj.put("title", tab.title)
                obj.put("url", tab.url)
                obj.put("isDesktopMode", tab.isDesktopMode)
                if (tab.parentTabId != null) obj.put("parentTabId", tab.parentTabId)
                json.put(obj)
            }
            prefs.edit().putString("browser_tabs", json.toString())
                .putString("browser_active_tab_id", activeTabId)
                .apply()
        } catch (e: Exception) {}
    }

    private fun loadBrowserTabs() {
        try {
            val jsonStr = prefs.getString("browser_tabs", "[]") ?: "[]"
            val activeId = prefs.getString("browser_active_tab_id", null)
            val json = org.json.JSONArray(jsonStr)
            val list = mutableListOf<com.example.TabState>()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                list.add(com.example.TabState(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    url = obj.getString("url"),
                    isDesktopMode = obj.optBoolean("isDesktopMode", false),
                    parentTabId = if (obj.has("parentTabId")) obj.optString("parentTabId", null) else null
                ))
            }
            if (list.isNotEmpty()) {
                browserTabs.clear()
                browserTabs.addAll(list)
                activeTabId = if (activeId != null && list.any { it.id == activeId }) activeId else list.first().id
            }
            isBrowserTabsLoaded = true
        } catch (e: Exception) {
            isBrowserTabsLoaded = true
        }
    }

    private fun saveBrowserHistory(list: List<BrowserHistory>) {
        val json = org.json.JSONArray()
        list.forEach { 
            val obj = org.json.JSONObject()
            obj.put("title", it.title)
            obj.put("url", it.url)
            obj.put("timestamp", it.timestamp)
            json.put(obj)
        }
        prefs.edit().putString("browser_history", json.toString()).apply()
    }

    private fun loadBrowserHistory() {
        try {
            val jsonStr = prefs.getString("browser_history", "[]") ?: "[]"
            val json = org.json.JSONArray(jsonStr)
            val list = mutableListOf<BrowserHistory>()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                list.add(BrowserHistory(obj.getString("title"), obj.getString("url"), obj.getLong("timestamp")))
            }
            _browserHistory.value = list
        } catch (e: Exception) {}
    }

val downloads: StateFlow<List<DownloadTask>> = _downloads.asStateFlow()

    private val activeDownloadJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val activeDownloadConnections = java.util.concurrent.ConcurrentHashMap<String, java.net.HttpURLConnection>()

    private fun saveDownloads(list: List<DownloadTask>) {
        try {
            val json = org.json.JSONArray()
            for (item in list) {
                val obj = org.json.JSONObject()
                obj.put("id", item.id)
                obj.put("url", item.url)
                obj.put("filename", item.filename)
                obj.put("progress", item.progress.toDouble())
                obj.put("status", item.status)
                obj.put("sizeString", item.sizeString)
                obj.put("mimeType", item.mimeType)
                obj.put("filePath", item.filePath)
                obj.put("downloadedBytes", item.downloadedBytes)
                obj.put("totalBytes", item.totalBytes)
                obj.put("canResume", item.canResume)
                obj.put("userAgent", item.userAgent)
                obj.put("destination", item.destination)
                json.put(obj)
            }
            prefs.edit().putString("browser_downloads", json.toString()).apply()
        } catch (e: Exception) {}
    }

    private fun loadDownloads() {
        try {
            val jsonStr = prefs.getString("browser_downloads", "[]") ?: "[]"
            val json = org.json.JSONArray(jsonStr)
            val list = mutableListOf<DownloadTask>()
            val tempDir = java.io.File(getApplication<Application>().filesDir, "downloads_temp")
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val id = obj.getString("id")
                var status = obj.getString("status")
                val progress = obj.getDouble("progress").toFloat()
                val partFile = java.io.File(tempDir, "$id.part")
                var canResume = obj.optBoolean("canResume", false)
                // Interrupted transfer safety check: if process died during transfer, transition to Paused or Failed
                if (status == "Downloading") {
                    if (partFile.exists() && partFile.length() > 0) {
                        status = "Paused"
                        canResume = true
                    } else {
                        status = "Failed"
                        canResume = false
                    }
                }
                list.add(
                    DownloadTask(
                        id = id,
                        url = obj.getString("url"),
                        filename = obj.getString("filename"),
                        progress = progress,
                        status = status,
                        sizeString = obj.optString("sizeString", "0 B"),
                        mimeType = obj.optString("mimeType", ""),
                        filePath = obj.optString("filePath", ""),
                        downloadedBytes = obj.optLong("downloadedBytes", if (partFile.exists()) partFile.length() else 0L),
                        totalBytes = obj.optLong("totalBytes", 0L),
                        canResume = canResume,
                        userAgent = obj.optString("userAgent", "Mozilla/5.0"),
                        destination = obj.optString("destination", "SECRET_VAULT")
                    )
                )
            }
            _downloads.value = list
        } catch (e: Exception) {}
    }

    init {
        loadBrowserBookmarks()
        loadBrowserHistory()
        loadDownloads()
        loadBrowserTabs()
    }

    fun clearDownloads() {
        activeDownloadJobs.values.forEach { it.cancel() }
        activeDownloadJobs.clear()
        activeDownloadConnections.values.forEach { try { it.disconnect() } catch (e: Exception) {} }
        activeDownloadConnections.clear()
        _downloads.value = emptyList()
        saveDownloads(emptyList())
    }

    fun removeDownload(taskId: String) {
        activeDownloadConnections.remove(taskId)?.let { try { it.disconnect() } catch (e: Exception) {} }
        activeDownloadJobs.remove(taskId)?.cancel()
        val current = _downloads.value.filter { it.id != taskId }
        _downloads.value = current
        saveDownloads(current)
    }

    fun cancelDownload(task: DownloadTask) {
        activeDownloadConnections.remove(task.id)?.let { try { it.disconnect() } catch (e: Exception) {} }
        val job = activeDownloadJobs.remove(task.id)
        job?.cancel()
        val current = _downloads.value.map { t ->
            if (t.id == task.id) t.copy(status = "Cancelled", progress = 0f) else t
        }
        _downloads.value = current
        saveDownloads(current)
    }

    fun saveDownloadedFile(context: Context, filename: String, mimeType: String, bytes: ByteArray): String? {
        return try {
            val downloadsDir = File(context.filesDir, "downloads")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val destFile = File(downloadsDir, filename)
            var finalFile = destFile
            var count = 1
            while (finalFile.exists()) {
                val nameWithoutExt = finalFile.nameWithoutExtension
                val ext = finalFile.extension
                finalFile = File(downloadsDir, if (ext.isNotEmpty()) "${nameWithoutExt}_$count.$ext" else "${nameWithoutExt}_$count")
                count++
            }
            finalFile.writeBytes(bytes)
            finalFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("BrowserDownload", "Failed to save downloaded file", e)
            null
        }
    }

    fun saveDownloadedFile(context: Context, filename: String, mimeType: String, sourceFile: File): String? {
        return try {
            val downloadsDir = File(context.filesDir, "downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val destFile = File(downloadsDir, filename)
            var finalFile = destFile
            var count = 1
            while (finalFile.exists()) {
                val nameWithoutExt = finalFile.nameWithoutExtension
                val ext = finalFile.extension
                finalFile = File(downloadsDir, if (ext.isNotEmpty()) "${nameWithoutExt}_$count.$ext" else "${nameWithoutExt}_$count")
                count++
            }
            if (!sourceFile.renameTo(finalFile)) {
                sourceFile.inputStream().buffered().use { input ->
                    finalFile.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }
                sourceFile.delete()
            }
            finalFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("BrowserDownload", "Failed to save downloaded file", e)
            null
        }
    }

    fun addDownloadedFileToVault(context: Context, filename: String, mimeType: String, bytes: ByteArray): String? {
        return try {
            val size = bytes.size.toLong()
            val readableSize = formatFileSize(size)
            val id = System.currentTimeMillis().toString()
            val timestamp = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            
            val vaultDir = File(context.filesDir, "vault_files")
            if (!vaultDir.exists()) {
                vaultDir.mkdirs()
            }
            
            val lowerName = filename.lowercase()
            val isVideo = mimeType.startsWith("video/") ||
                lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".avi") ||
                lowerName.endsWith(".mov") || lowerName.endsWith(".webm") || lowerName.endsWith(".flv") ||
                lowerName.endsWith(".3gp") || lowerName.endsWith(".m4v") || lowerName.endsWith(".ts")
            val isImage = mimeType.startsWith("image/") ||
                lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") ||
                lowerName.endsWith(".webp") || lowerName.endsWith(".gif")
            val isAudio = mimeType.startsWith("audio/") ||
                lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") || lowerName.endsWith(".m4a") ||
                lowerName.endsWith(".ogg") || lowerName.endsWith(".flac") || lowerName.endsWith(".aac")
            val effectiveMime = when {
                isVideo && !mimeType.startsWith("video/") -> "video/mp4"
                isImage && !mimeType.startsWith("image/") -> "image/jpeg"
                isAudio && !mimeType.startsWith("audio/") -> "audio/mpeg"
                else -> mimeType
            }
            val ext = File(filename).extension.ifEmpty {
                when {
                    isVideo -> "mp4"
                    isImage -> "jpg"
                    isAudio -> "mp3"
                    mimeType.contains("pdf") -> "pdf"
                    mimeType.contains("text") -> "txt"
                    else -> "dat"
                }
            }
            
            val destFileName = "$id.$ext"
            val destFile = File(vaultDir, destFileName)
            destFile.writeBytes(bytes)
            
            val fileSerialized = "$id|||$timestamp|||$filename|||$effectiveMime|||${destFile.absolutePath}|||$readableSize"
            val updatedFiles = _vaultFiles.value + fileSerialized
            _vaultFiles.value = updatedFiles.sortedByDescending { it }
            
            val isDecoy = _decoyActive.value
            val filesKey = if (isDecoy) "decoy_files" else "vault_files"
            prefs.edit().putStringSet(filesKey, updatedFiles.toSet()).apply()
            
            destFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("Vault", "Failed to add downloaded file to vault", e)
            null
        }
    }

    fun addDownloadedFileToVault(context: Context, filename: String, mimeType: String, sourceFile: File): String? {
        return try {
            val vaultDir = File(context.filesDir, "vault_files")
            if (!vaultDir.exists()) vaultDir.mkdirs()
            val id = System.currentTimeMillis().toString()
            val lowerName = filename.lowercase()
            val isVideo = mimeType.startsWith("video/") ||
                lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".avi") ||
                lowerName.endsWith(".mov") || lowerName.endsWith(".webm") || lowerName.endsWith(".flv") ||
                lowerName.endsWith(".3gp") || lowerName.endsWith(".m4v") || lowerName.endsWith(".ts")
            val isImage = mimeType.startsWith("image/") ||
                lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") ||
                lowerName.endsWith(".webp") || lowerName.endsWith(".gif")
            val isAudio = mimeType.startsWith("audio/") ||
                lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") || lowerName.endsWith(".m4a") ||
                lowerName.endsWith(".ogg") || lowerName.endsWith(".flac") || lowerName.endsWith(".aac")
            val effectiveMime = when {
                isVideo && !mimeType.startsWith("video/") -> "video/mp4"
                isImage && !mimeType.startsWith("image/") -> "image/jpeg"
                isAudio && !mimeType.startsWith("audio/") -> "audio/mpeg"
                else -> mimeType
            }
            val ext = File(filename).extension.ifEmpty {
                when {
                    isVideo -> "mp4"
                    isImage -> "jpg"
                    isAudio -> "mp3"
                    mimeType.contains("pdf") -> "pdf"
                    mimeType.contains("text") -> "txt"
                    else -> "dat"
                }
            }
            val destFileName = "$id.$ext"
            val destFile = File(vaultDir, destFileName)
            if (!sourceFile.renameTo(destFile)) {
                sourceFile.inputStream().buffered().use { input ->
                    destFile.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }
                sourceFile.delete()
            }
            registerDirectVaultFile(context, destFile, filename, effectiveMime)
            destFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("Vault", "Failed to add downloaded file to vault", e)
            null
        }
    }

    fun deleteDownload(task: DownloadTask) {
        cancelDownload(task)
        if (task.filePath.isNotEmpty()) {
            val file = java.io.File(task.filePath)
            if (file.exists()) {
                file.delete()
            }
        }
        val tempDir = java.io.File(getApplication<Application>().filesDir, "downloads_temp")
        val partFile = java.io.File(tempDir, "${task.id}.part")
        if (partFile.exists()) {
            partFile.delete()
        }
        val current = _downloads.value.filter { it.id != task.id }
        _downloads.value = current
        saveDownloads(current)
    }

    fun pauseDownload(task: DownloadTask) {
        val conn = activeDownloadConnections.remove(task.id)
        try { conn?.disconnect() } catch (e: Exception) {}
        val job = activeDownloadJobs.remove(task.id)
        job?.cancel()
        val tempDir = java.io.File(getApplication<Application>().filesDir, "downloads_temp")
        val partFile = java.io.File(tempDir, "${task.id}.part")
        val existingBytes = if (partFile.exists()) partFile.length() else task.downloadedBytes
        val current = _downloads.value.map { t ->
            if (t.id == task.id) {
                t.copy(status = "Paused", canResume = true, downloadedBytes = existingBytes)
            } else t
        }
        _downloads.value = current
        saveDownloads(current)
        try {
            android.widget.Toast.makeText(getApplication(), "Download paused", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {}
    }

    fun resumeDownload(context: Context, task: DownloadTask) {
        try {
            android.widget.Toast.makeText(context, "Resuming download: ${task.filename}", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {}
        val dest = try {
            DownloadDestination.valueOf(task.destination)
        } catch (e: Exception) {
            DownloadDestination.SECRET_VAULT
        }
        startVaultDownload(
            context = context,
            url = task.url,
            userAgent = task.userAgent,
            contentDisposition = "",
            mimeType = task.mimeType,
            contentLength = task.totalBytes,
            destination = dest,
            existingTaskId = task.id,
            isResume = true
        )
    }

    fun openDownload(context: Context, task: DownloadTask) {
        if (task.filePath.isNotEmpty()) {
            val file = java.io.File(task.filePath)
            if (file.exists()) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, task.mimeType)
                    clipData = android.content.ClipData.newRawUri("Open File", uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "No app found to open this file.", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                android.widget.Toast.makeText(context, "File not found.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun retryDownload(context: Context, task: DownloadTask) {
        val job = activeDownloadJobs.remove(task.id)
        job?.cancel()
        val tempDir = java.io.File(context.filesDir, "downloads_temp")
        val partFile = java.io.File(tempDir, "${task.id}.part")
        if (partFile.exists()) {
            partFile.delete()
        }
        val dest = try {
            DownloadDestination.valueOf(task.destination)
        } catch (e: Exception) {
            DownloadDestination.SECRET_VAULT
        }
        startVaultDownload(
            context = context,
            url = task.url,
            userAgent = task.userAgent,
            contentDisposition = "",
            mimeType = task.mimeType,
            contentLength = task.totalBytes,
            destination = dest,
            existingTaskId = task.id,
            isResume = false
        )
    }

    fun startVaultDownload(
        context: Context,
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long,
        destination: DownloadDestination = DownloadDestination.SECRET_VAULT,
        existingTaskId: String? = null,
        isResume: Boolean = false,
        referrerUrl: String = ""
    ) {
        val taskId = existingTaskId ?: java.util.UUID.randomUUID().toString()
        val (resolvedInitialFilename, resolvedInitialMime) = SecretDownloadFilenameHelper.resolveFilenameAndMime(
            url = url,
            contentDisposition = contentDisposition,
            mimeType = mimeType
        )
        
        val tempDir = java.io.File(context.filesDir, "downloads_temp")
        if (!tempDir.exists()) tempDir.mkdirs()
        val partFile = java.io.File(tempDir, "$taskId.part")

        val existingTask = _downloads.value.find { it.id == taskId }
        val finalFilename = existingTask?.filename ?: resolvedInitialFilename
        val effectiveMimeType = if (resolvedInitialMime.isNotEmpty()) resolvedInitialMime else (existingTask?.mimeType ?: mimeType)
        val initialDownloaded = if (isResume && partFile.exists()) partFile.length() else 0L
        val initialTotal = if (contentLength > 0) contentLength else (existingTask?.totalBytes ?: 0L)
        val initialProgress = if (initialTotal > 0) (initialDownloaded.toFloat() / initialTotal.toFloat()).coerceIn(0f, 1f) else 0f

        val updatedTask = DownloadTask(
            id = taskId,
            url = url,
            filename = finalFilename,
            progress = initialProgress,
            status = "Downloading",
            sizeString = if (initialTotal > 0) "${formatFileSize(initialDownloaded)} / ${formatFileSize(initialTotal)}" else formatFileSize(initialTotal),
            mimeType = effectiveMimeType,
            filePath = "",
            downloadedBytes = initialDownloaded,
            totalBytes = initialTotal,
            canResume = true,
            userAgent = userAgent,
            destination = destination.name
        )

        val updatedDownloads = if (existingTask != null) {
            _downloads.value.map { if (it.id == taskId) updatedTask else it }
        } else {
            _downloads.value + updatedTask
        }
        _downloads.value = updatedDownloads
        saveDownloads(updatedDownloads)

        try {
            if (!isResume) {
                android.widget.Toast.makeText(context, "Downloading started: $finalFilename", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {}

        activeDownloadJobs.remove(taskId)?.cancel()
        activeDownloadConnections.remove(taskId)?.let { try { it.disconnect() } catch (e: Exception) {} }
        
        val job = viewModelScope.launch(Dispatchers.IO) {
            var fileOutputStream: java.io.OutputStream? = null
            var inputStream: java.io.InputStream? = null
            var connection: java.net.HttpURLConnection? = null
            try {
                var currentUrl = url
                var append = false
                var startByte = if (isResume && partFile.exists() && partFile.length() > 0) partFile.length() else 0L
                var responseCode = -1
                var connectAttempts = 0
                val defaultUa = "Mozilla/5.0 (Android 14; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0"
                val effectiveUa = if (userAgent.isNotBlank()) userAgent else defaultUa

                while (connectAttempts < 5) {
                    val urlObj = java.net.URL(currentUrl)
                    connection = urlObj.openConnection() as java.net.HttpURLConnection
                    activeDownloadConnections[taskId] = connection
                    connection.instanceFollowRedirects = true
                    connection.connectTimeout = 25000
                    connection.readTimeout = 40000
                    
                    // Maintain authentic GeckoView User-Agent across all connections and redirects
                    connection.setRequestProperty("User-Agent", effectiveUa)
                    connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,video/*,audio/*,*/*;q=0.8")
                    connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                    connection.setRequestProperty("Accept-Encoding", "identity")
                    connection.setRequestProperty("Sec-Fetch-Dest", "document")
                    connection.setRequestProperty("Sec-Fetch-Mode", "navigate")
                    connection.setRequestProperty("Sec-Fetch-Site", "same-origin")
                    connection.setRequestProperty("Sec-Fetch-User", "?1")
                    connection.setRequestProperty("Upgrade-Insecure-Requests", "1")
                    
                    try {
                        val parsedUri = Uri.parse(currentUrl)
                        val origin = "${parsedUri.scheme}://${parsedUri.host}"
                        val effectiveReferrer = if (referrerUrl.isNotBlank() && referrerUrl != "home" && !referrerUrl.startsWith("about:")) {
                            referrerUrl
                        } else {
                            origin
                        }
                        connection.setRequestProperty("Referer", effectiveReferrer)
                        connection.setRequestProperty("Origin", origin)
                    } catch (e: Exception) {}

                    if (startByte > 0) {
                        connection.setRequestProperty("Range", "bytes=$startByte-")
                    }

                    connection.connect()
                    responseCode = connection.responseCode

                    // Handle redirects (across protocols/domains)
                    if (responseCode in listOf(301, 302, 303, 307, 308)) {
                        val location = connection.getHeaderField("Location")
                        if (!location.isNullOrEmpty()) {
                            currentUrl = java.net.URL(urlObj, location).toString()
                            try { connection.disconnect() } catch (e: Exception) {}
                            connectAttempts++
                            continue
                        }
                    }

                    // If server rejects Range requests with 416 Range Not Satisfiable or 400, fall back to clean GET from byte 0
                    if ((responseCode == 416 || responseCode == 400) && startByte > 0) {
                        try { connection.disconnect() } catch (e: Exception) {}
                        try { partFile.delete() } catch (e: Exception) {}
                        startByte = 0L
                        connectAttempts++
                        continue
                    }

                    // If 403 Forbidden with Range: retry once from byte 0
                    if (responseCode == 403 && startByte > 0 && connectAttempts < 2) {
                        try { connection.disconnect() } catch (e: Exception) {}
                        startByte = 0L
                        connectAttempts++
                        continue
                    }

                    break
                }

                var totalLength: Long
                var currentDownloaded: Long

                if (responseCode == 206) {
                    append = true
                    currentDownloaded = startByte
                    val serverRemaining = connection?.contentLengthLong ?: 0L
                    totalLength = if (initialTotal > 0) initialTotal else (if (serverRemaining > 0) startByte + serverRemaining else 0L)
                } else if (responseCode in 200..299) {
                    append = false
                    currentDownloaded = 0L
                    val respLength = connection?.contentLengthLong ?: 0L
                    totalLength = if (contentLength > 0) contentLength else respLength
                } else {
                    val connMsg = connection?.responseMessage ?: ""
                    throw java.io.IOException("HTTP error: $responseCode $connMsg")
                }

                val safeConnection = connection ?: throw java.io.IOException("Connection is null")
                inputStream = safeConnection.inputStream.buffered()
                fileOutputStream = java.io.FileOutputStream(partFile, append).buffered()

                val buffer = ByteArray(131072) // 128 KB high-throughput memory-safe bounded buffer
                var lastUiUpdateTime = 0L
                var speedWindowStartTime = android.os.SystemClock.uptimeMillis()
                var speedWindowStartBytes = currentDownloaded
                var currentSpeedStr = ""

                while (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive == true) {
                    val currentTask = _downloads.value.find { it.id == taskId }
                    if (currentTask != null && currentTask.status != "Downloading") {
                        break
                    }
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    fileOutputStream.write(buffer, 0, bytesRead)
                    currentDownloaded += bytesRead

                    val now = android.os.SystemClock.uptimeMillis()
                    val timeDelta = now - speedWindowStartTime
                    if (timeDelta >= 1000) {
                        val bytesInDelta = currentDownloaded - speedWindowStartBytes
                        val bytesPerSec = (bytesInDelta * 1000L) / timeDelta.coerceAtLeast(1L)
                        currentSpeedStr = if (bytesPerSec > 0) "${formatFileSize(bytesPerSec)}/s" else ""
                        speedWindowStartTime = now
                        speedWindowStartBytes = currentDownloaded
                    }

                    if (now - lastUiUpdateTime > 400 || (totalLength > 0 && currentDownloaded >= totalLength)) {
                        lastUiUpdateTime = now
                        val progressValue = if (totalLength > 0) (currentDownloaded.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f) else 0f
                        val downloadedStr = formatFileSize(currentDownloaded)
                        val totalStr = if (totalLength > 0) formatFileSize(totalLength) else ""
                        var sizeDisplay = if (totalStr.isNotEmpty()) "$downloadedStr / $totalStr" else downloadedStr
                        if (currentSpeedStr.isNotEmpty()) {
                            sizeDisplay += " • $currentSpeedStr"
                        }

                        _downloads.value = _downloads.value.map { task ->
                            if (task.id == taskId) {
                                if (task.status == "Downloading") {
                                    task.copy(
                                        progress = progressValue,
                                        downloadedBytes = currentDownloaded,
                                        totalBytes = totalLength,
                                        sizeString = sizeDisplay
                                    )
                                } else task
                            } else task
                        }
                    }
                }

                fileOutputStream.flush()
                fileOutputStream.close()
                fileOutputStream = null

                inputStream.close()
                inputStream = null

                if (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive != true) {
                    return@launch
                }

                val respDisposition = safeConnection.getHeaderField("Content-Disposition") ?: contentDisposition
                val respContentType = safeConnection.contentType ?: safeConnection.getHeaderField("Content-Type")
                val redirectedUrl = safeConnection.url?.toString() ?: currentUrl
                val effectiveDownloadMime = if (!SecretDownloadFilenameHelper.isGenericMimeType(respContentType)) {
                    respContentType ?: resolvedInitialMime
                } else {
                    resolvedInitialMime
                }

                val (resolvedFinalFilename, resolvedFinalMime) = SecretDownloadFilenameHelper.resolveFilenameAndMime(
                    url = redirectedUrl,
                    contentDisposition = respDisposition,
                    mimeType = effectiveDownloadMime
                )

                val downloadHandler = VaultDownloadHandler(context)
                val targetFilePath = downloadHandler.saveDownloadedFile(
                    filename = resolvedFinalFilename,
                    mimeType = resolvedFinalMime,
                    sourceFile = partFile,
                    destination = destination,
                    deviceFileSaver = { fname, mime, src -> saveDownloadedFile(context, fname, mime, src) },
                    vaultFileSaver = { fname, mime, src -> addDownloadedFileToVault(context, fname, mime, src) }
                )
                val success = targetFilePath != null
                
                withContext(Dispatchers.Main) {
                    activeDownloadJobs.remove(taskId)
                    val finalDownloads = _downloads.value.map { task ->
                        if (task.id == taskId) {
                            task.copy(
                                filename = resolvedFinalFilename,
                                progress = 1f,
                                status = if (success) "Completed" else "Failed",
                                mimeType = resolvedFinalMime,
                                filePath = targetFilePath ?: "",
                                canResume = false,
                                sizeString = formatFileSize(currentDownloaded)
                            )
                        } else task
                    }
                    _downloads.value = finalDownloads
                    saveDownloads(finalDownloads)
                    if (success) {
                        android.widget.Toast.makeText(context, "$resolvedFinalFilename download completed!", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, "Failed to save downloaded file $resolvedFinalFilename", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    return@launch
                }
                val isPausedByUser = _downloads.value.find { it.id == taskId }?.status == "Paused"
                if (isPausedByUser) {
                    return@launch
                }
                android.util.Log.e("VaultDownload", "Error downloading file $finalFilename", e)
                withContext(Dispatchers.Main) {
                    activeDownloadJobs.remove(taskId)
                    activeDownloadConnections.remove(taskId)
                    val canResumeLater = partFile.exists() && partFile.length() > 0
                    val finalDownloads = _downloads.value.map { task ->
                        if (task.id == taskId) {
                            task.copy(
                                status = if (canResumeLater) "Paused" else "Failed",
                                canResume = canResumeLater
                            )
                        } else task
                    }
                    _downloads.value = finalDownloads
                    saveDownloads(finalDownloads)
                    val errorText = if (e.localizedMessage?.contains("403") == true) {
                        "Download expired or forbidden (403). Please click download again on the website for a fresh link."
                    } else {
                        "Download error: ${e.localizedMessage ?: "Network error"}"
                    }
                    android.widget.Toast.makeText(context, errorText, android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                try { fileOutputStream?.close() } catch (e: Exception) {}
                try { inputStream?.close() } catch (e: Exception) {}
                try { connection?.disconnect() } catch (e: Exception) {}
                activeDownloadJobs.remove(taskId)
                activeDownloadConnections.remove(taskId)
            }
        }
        activeDownloadJobs[taskId] = job
    }

    fun registerDirectVaultFile(context: Context, file: File, originalName: String, mimeType: String) {
        val size = file.length()
        val readableSize = formatFileSize(size)
        val id = System.currentTimeMillis().toString()
        val timestamp = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val lowerName = originalName.lowercase()
        val lowerFile = file.name.lowercase()
        val isVideo = mimeType.startsWith("video/") ||
            lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".avi") ||
            lowerName.endsWith(".mov") || lowerName.endsWith(".webm") || lowerName.endsWith(".flv") ||
            lowerName.endsWith(".3gp") || lowerName.endsWith(".m4v") || lowerName.endsWith(".ts") ||
            lowerFile.endsWith(".mp4") || lowerFile.endsWith(".mkv") || lowerFile.endsWith(".avi")
        val isImage = mimeType.startsWith("image/") ||
            lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") ||
            lowerName.endsWith(".webp") || lowerName.endsWith(".gif") ||
            lowerFile.endsWith(".jpg") || lowerFile.endsWith(".jpeg") || lowerFile.endsWith(".png")
        val isAudio = mimeType.startsWith("audio/") ||
            lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") || lowerName.endsWith(".m4a") ||
            lowerName.endsWith(".ogg") || lowerName.endsWith(".flac") || lowerName.endsWith(".aac") ||
            lowerFile.endsWith(".mp3") || lowerFile.endsWith(".wav")
        val effectiveMime = when {
            isVideo && !mimeType.startsWith("video/") -> "video/mp4"
            isImage && !mimeType.startsWith("image/") -> "image/jpeg"
            isAudio && !mimeType.startsWith("audio/") -> "audio/mpeg"
            else -> mimeType
        }
        
        val fileSerialized = "$id|||$timestamp|||$originalName|||$effectiveMime|||${file.absolutePath}|||$readableSize"
        val updatedFiles = _vaultFiles.value + fileSerialized
        _vaultFiles.value = updatedFiles.sortedByDescending { it }
        
        val isDecoy = _decoyActive.value
        val filesKey = if (isDecoy) "decoy_files" else "vault_files"
        prefs.edit().putStringSet(filesKey, updatedFiles.toSet()).apply()
    }

    fun createSamplePdfToVault(context: Context) {
        try {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            
            // Page 1: Welcome & Overview
            val pageInfo1 = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
            val page1 = pdfDocument.startPage(pageInfo1)
            val canvas1 = page1.canvas
            val paint = android.graphics.Paint()
            
            // Background
            canvas1.drawColor(android.graphics.Color.rgb(15, 19, 31)) // Deep dark brand background
            
            // Header box
            paint.color = android.graphics.Color.rgb(141, 110, 210) // ThemePurple color
            canvas1.drawRect(0f, 0f, 595f, 150f, paint)
            
            // Title
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 24f
            paint.isFakeBoldText = true
            paint.textAlign = android.graphics.Paint.Align.CENTER
            canvas1.drawText("SECURE CALCULATOR VAULT", 297f, 75f, paint)
            
            paint.textSize = 14f
            paint.isFakeBoldText = false
            canvas1.drawText("Official Welcome & User Guide", 297f, 110f, paint)
            
            // Content
            paint.textAlign = android.graphics.Paint.Align.LEFT
            paint.color = android.graphics.Color.rgb(230, 230, 230)
            paint.textSize = 16f
            paint.isFakeBoldText = true
            canvas1.drawText("1. Introduction", 50f, 200f, paint)
            
            paint.textSize = 12f
            paint.isFakeBoldText = false
            paint.color = android.graphics.Color.rgb(180, 185, 200)
            val lines1 = listOf(
                "Welcome to your ultra-secure offline vault! This space is hidden behind a fully",
                "functional calculator interface. Only you know the secret PIN combination that",
                "unlocks these hidden vaults.",
                "",
                "Your files are kept completely offline, stored safely within the application's",
                "isolated internal storage container. There are no background server connections,",
                "no external API leaks, and zero cloud uploads, ensuring absolute privacy."
            )
            var yPos = 230f
            for (line in lines1) {
                canvas1.drawText(line, 50f, yPos, paint)
                yPos += 20f
            }
            
            paint.textSize = 16f
            paint.isFakeBoldText = true
            paint.color = android.graphics.Color.rgb(230, 230, 230)
            canvas1.drawText("2. Security Protocols & Vault Privacy", 50f, yPos + 20f, paint)
            
            paint.textSize = 12f
            paint.isFakeBoldText = false
            paint.color = android.graphics.Color.rgb(180, 185, 200)
            yPos += 50f
            val lines2 = listOf(
                "- 100% Client-Side Isolation: Files reside purely in private internal directories.",
                "- Anti-Leak Prevention: External applications cannot browse or automatically scan",
                "  your documents, preventing metadata sniffing.",
                "- Automatic Temporary Decryption: Files are decrypted purely on-the-fly and",
                "  only in-memory during internal secure viewing. No temporary public files are",
                "  ever written to external or shared storage locations.",
                "- Decoy Safe Mode: Setup a secondary passcode to show a simulated, empty vault",
                "  to bypass lookers or forced disclosure seamlessly."
            )
            for (line in lines2) {
                canvas1.drawText(line, 50f, yPos, paint)
                yPos += 20f
            }
            
            // Footer page 1
            paint.color = android.graphics.Color.rgb(100, 105, 120)
            paint.textSize = 10f
            paint.textAlign = android.graphics.Paint.Align.CENTER
            canvas1.drawText("Page 1 of 2", 297f, 800f, paint)
            
            pdfDocument.finishPage(page1)
            
            // Page 2: Internal Document Viewer Guide
            val pageInfo2 = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 2).create()
            val page2 = pdfDocument.startPage(pageInfo2)
            val canvas2 = page2.canvas
            
            // Background
            canvas2.drawColor(android.graphics.Color.rgb(15, 19, 31))
            
            // Header box
            paint.color = android.graphics.Color.rgb(100, 80, 160)
            canvas2.drawRect(0f, 0f, 595f, 100f, paint)
            
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 18f
            paint.isFakeBoldText = true
            paint.textAlign = android.graphics.Paint.Align.CENTER
            canvas2.drawText("INTERNAL VIEWER MANUAL", 297f, 55f, paint)
            
            paint.textAlign = android.graphics.Paint.Align.LEFT
            paint.color = android.graphics.Color.rgb(230, 230, 230)
            paint.textSize = 16f
            paint.isFakeBoldText = true
            canvas2.drawText("3. Viewer Interaction Guide", 50f, 150f, paint)
            
            paint.textSize = 12f
            paint.isFakeBoldText = false
            paint.color = android.graphics.Color.rgb(180, 185, 200)
            yPos = 180f
            val lines3 = listOf(
                "Our built-in document viewer is optimized to handle high-resolution documents",
                "and heavy PDFs safely without leaking file references. Learn how to navigate:",
                "",
                "Pinch-to-zoom:",
                "   Pinch with two fingers on any image or PDF page to zoom in up to 5x. Drag with",
                "   a single finger to pan and inspect fine details easily.",
                "",
                "Double-tap:",
                "   Double-tap with a single finger on any page or image to instantly zoom in",
                "   by 2.5x, and double-tap again to reset back to normal scale.",
                "",
                "Fluid scrolling:",
                "   Scroll vertically through multi-page PDF documents. The system loads and",
                "   unloads pages dynamically in real-time to maintain extremely low memory usage",
                "   and ensure large PDF files open instantly without crashes."
            )
            for (line in lines3) {
                canvas2.drawText(line, 50f, yPos, paint)
                yPos += 20f
            }
            
            // Drawing a beautiful decorative lock icon on page 2 canvas using simple shapes
            paint.color = android.graphics.Color.rgb(141, 110, 210)
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 3f
            // Draw shackle
            canvas2.drawArc(267f, 550f, 327f, 610f, 180f, 180f, false, paint)
            // Draw body
            paint.style = android.graphics.Paint.Style.FILL
            canvas2.drawRoundRect(257f, 580f, 337f, 640f, 8f, 8f, paint)
            // Draw keyhole
            paint.color = android.graphics.Color.rgb(15, 19, 31)
            canvas2.drawCircle(297f, 600f, 6f, paint)
            canvas2.drawRect(294f, 600f, 300f, 615f, paint)
            
            // Text below icon
            paint.color = android.graphics.Color.rgb(141, 110, 210)
            paint.textSize = 12f
            paint.isFakeBoldText = true
            paint.textAlign = android.graphics.Paint.Align.CENTER
            canvas2.drawText("MAXIMUM PRIVACY GUARANTEED", 297f, 675f, paint)
            
            // Footer page 2
            paint.color = android.graphics.Color.rgb(100, 105, 120)
            paint.textSize = 10f
            paint.isFakeBoldText = false
            canvas2.drawText("Page 2 of 2", 297f, 800f, paint)
            
            pdfDocument.finishPage(page2)
            
            // Save to file
            val outputStream = java.io.ByteArrayOutputStream()
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            
            val bytes = outputStream.toByteArray()
            addDownloadedFileToVault(context, "Welcome Guide.pdf", "application/pdf", bytes)
            android.widget.Toast.makeText(context, "Welcome Guide.pdf generated successfully!", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Failed to generate Welcome Guide.pdf", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // --- Modern Backup & Restore Framework (Local Offline Support) ---

    private fun exportPrefsToJson(prefs: android.content.SharedPreferences): String {
        val json = org.json.JSONObject()
        val all = prefs.all
        for ((key, value) in all) {
            if (value == null) continue
            val item = org.json.JSONObject()
            when (value) {
                is String -> {
                    item.put("type", "string")
                    item.put("value", value)
                }
                is Boolean -> {
                    item.put("type", "boolean")
                    item.put("value", value)
                }
                is Int -> {
                    item.put("type", "int")
                    item.put("value", value)
                }
                is Long -> {
                    item.put("type", "long")
                    item.put("value", value)
                }
                is Float -> {
                    item.put("type", "float")
                    item.put("value", value.toDouble())
                }
                is Set<*> -> {
                    item.put("type", "string_set")
                    val arr = org.json.JSONArray()
                    for (s in value) {
                        if (s is String) {
                            arr.put(s)
                        }
                    }
                    item.put("value", arr)
                }
            }
            json.put(key, item)
        }
        return json.toString()
    }

    private fun importPrefsFromJson(prefs: android.content.SharedPreferences, jsonStr: String, context: android.content.Context) {
        val json = org.json.JSONObject(jsonStr)
        val editor = prefs.edit()
        editor.clear()
        
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = json.getJSONObject(key)
            val type = item.getString("type")
            
            when (type) {
                "string" -> {
                    val value = item.getString("value")
                    editor.putString(key, value)
                }
                "boolean" -> {
                    val value = item.getBoolean("value")
                    editor.putBoolean(key, value)
                }
                "int" -> {
                    val value = item.getInt("value")
                    editor.putInt(key, value)
                }
                "long" -> {
                    val value = item.getLong("value")
                    editor.putLong(key, value)
                }
                "float" -> {
                    val value = item.getDouble("value").toFloat()
                    editor.putFloat(key, value)
                }
                "string_set" -> {
                    val arr = item.getJSONArray("value")
                    val set = mutableSetOf<String>()
                    for (i in 0 until arr.length()) {
                        set.add(arr.getString(i))
                    }
                    
                    if (key == "vault_files" || key == "decoy_files" || key == "recently_deleted_files" || key == "recently_deleted_decoy_files") {
                        val updatedSet = set.map { fileStr ->
                            val parts = fileStr.split("|||")
                            if (parts.size >= 5) {
                                val originalPath = parts[4]
                                val filename = java.io.File(originalPath).name
                                val newPath = java.io.File(java.io.File(context.filesDir, "vault_files"), filename).absolutePath
                                
                                val newParts = parts.toMutableList()
                                newParts[4] = newPath
                                newParts.joinToString("|||")
                            } else {
                                fileStr
                            }
                        }.toSet()
                        editor.putStringSet(key, updatedSet)
                    } else {
                        editor.putStringSet(key, set)
                    }
                }
            }
        }
        editor.apply()
    }

    fun exportBackupToZip(context: android.content.Context, outputStream: java.io.OutputStream): Boolean {
        var zos: java.util.zip.ZipOutputStream? = null
        try {
            zos = java.util.zip.ZipOutputStream(outputStream)
            
            // 1. Export Shared Preferences
            val jsonStr = exportPrefsToJson(prefs)
            val prefsEntry = java.util.zip.ZipEntry("prefs.json")
            zos.putNextEntry(prefsEntry)
            zos.write(jsonStr.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            
            // 2. Export Vault Files
            val vaultDir = java.io.File(context.filesDir, "vault_files")
            if (vaultDir.exists() && vaultDir.isDirectory) {
                val files = vaultDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isFile) {
                            val entry = java.util.zip.ZipEntry("files/${file.name}")
                            zos.putNextEntry(entry)
                            file.inputStream().use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try {
                zos?.close()
            } catch (e: Exception) {}
        }
    }

    fun importBackupFromZip(context: android.content.Context, inputStream: java.io.InputStream): Boolean {
        var zis: java.util.zip.ZipInputStream? = null
        try {
            zis = java.util.zip.ZipInputStream(inputStream)
            
            val vaultDir = java.io.File(context.filesDir, "vault_files")
            if (!vaultDir.exists()) {
                vaultDir.mkdirs()
            }
            
            var entry = zis.nextEntry
            var prefsJsonStr: String? = null
            
            while (entry != null) {
                val name = entry.name
                if (name == "prefs.json") {
                    val baos = java.io.ByteArrayOutputStream()
                    zis.copyTo(baos)
                    prefsJsonStr = baos.toString("UTF-8")
                } else if (name.startsWith("files/")) {
                    val filename = java.io.File(name).name
                    if (filename.isNotEmpty()) {
                        val destFile = java.io.File(vaultDir, filename)
                        destFile.outputStream().use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            
            if (prefsJsonStr != null) {
                importPrefsFromJson(prefs, prefsJsonStr, context)
                
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    _soundEnabled.value = prefs.getBoolean("sound_enabled", true)
                    _hapticProfile.value = prefs.getString("haptic_profile", "Crisp") ?: "Crisp"
                    _lastUpdated.value = prefs.getString("last_rates_update", "Never") ?: "Never"
                    
                    _intruderDetectionEnabled.value = prefs.getBoolean("intruder_detection_enabled", true)
                    _intruderSelfieEnabled.value = prefs.getBoolean("intruder_selfie_enabled", true)
                    _failedAttemptsThreshold.value = prefs.getInt("failed_attempts_threshold", 1)
                    _autoLockDuration.value = prefs.getInt("auto_lock_duration", -1)
                    _blurThumbnails.value = prefs.getBoolean("blur_thumbnails", false)
                    _lockedFolders.value = prefs.getStringSet("locked_folders", emptySet()) ?: emptySet()
                    _biometricEnabled.value = prefs.getBoolean("biometric_enabled", false)
                    _panicEnabled.value = prefs.getBoolean("panic_enabled", false)
                    _panicAction.value = prefs.getString("panic_action", "lock") ?: "lock"
                    _activeAppIcon.value = prefs.getString("active_app_icon", "LauncherCalculator") ?: "LauncherCalculator"
                    _lockedApps.value = prefs.getStringSet("locked_apps", emptySet()) ?: emptySet()
                    _preventScreenshots.value = prefs.getBoolean("prevent_screenshots", false)
                    _screenDownLock.value = prefs.getBoolean("screen_down_lock", false)
                    _fileLossProtection.value = prefs.getBoolean("file_loss_protection", false)
                    _lockOnBackground.value = prefs.getBoolean("lock_on_background", true)
                    _hideNotifications.value = prefs.getBoolean("hide_notifications", false)
                    _clipboardProtection.value = prefs.getBoolean("clipboard_protection", true)
                    _stealthMode.value = prefs.getBoolean("stealth_mode", true)
                    _selectedLanguage.value = prefs.getString("selected_language", "en") ?: "en"
                    _searchEngine.value = prefs.getString("browser_search_engine", "DuckDuckGo") ?: "DuckDuckGo"
                    _savePasswords.value = prefs.getBoolean("browser_save_passwords", true)
                    _clearHistoryOnExit.value = prefs.getBoolean("browser_clear_history", false)
                    _clearTempOnExit.value = prefs.getBoolean("browser_clear_temp_on_exit", false)
                    _useGeckoView.value = prefs.getBoolean("browser_use_geckoview", true)
                    _vaultPin.value = prefs.getString("vault_pin", "7777") ?: "7777"
                    _decoyPin.value = prefs.getString("decoy_pin", "1111") ?: "1111"
                    _panicExitAction.value = prefs.getString("panic_exit_action", "close") ?: "close"
                    _ownerName.value = prefs.getString("owner_name", "Vault Owner") ?: "Vault Owner"
                    _ownerAvatarUri.value = prefs.getString("owner_avatar_uri", "") ?: ""
                    val loadedUri = _ownerAvatarUri.value
                    _ownerAvatarScale.value = prefs.getFloat("owner_avatar_scale_$loadedUri", 1.0f)
                    _ownerAvatarOffsetX.value = prefs.getFloat("owner_avatar_offset_x_$loadedUri", 0f)
                    _ownerAvatarOffsetY.value = prefs.getFloat("owner_avatar_offset_y_$loadedUri", 0f)
                    _premiumState.value = prefs.getString("premium_state", "Free") ?: "Free"
                    _vaultId.value = getOrCreateVaultId()
                    
                    loadFolders()
                    loadFolderAssociations()
                    loadFavorites()
                    loadRecentlyOpened()
                    loadVaultData()
                }
                return true
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try {
                zis?.close()
            } catch (e: Exception) {}
        }
    }
}

data class DownloadTask(
    val id: String,
    val url: String,
    val filename: String,
    val progress: Float, // 0.0f to 1.0f
    val status: String, // "Downloading", "Completed", "Failed", "Paused", "Cancelled"
    val sizeString: String = "0 B",
    val mimeType: String = "",
    val filePath: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val canResume: Boolean = false,
    val userAgent: String = "Mozilla/5.0",
    val destination: String = "SECRET_VAULT"
)

data class RecentItem(
    val id: String,
    val type: String,
    val name: String,
    val timestamp: Long,
    val extra: String = ""
)

data class StorageDetails(
    val photosSize: Long,
    val photosCount: Int,
    val videosSize: Long,
    val videosCount: Int,
    val documentsSize: Long,
    val documentsCount: Int,
    val notesSize: Long,
    val notesCount: Int,
    val totalSize: Long
)



data class VaultStorageInfo(
    val totalBytes: Long = 0,
    val photosBytes: Long = 0,
    val videosBytes: Long = 0,
    val docsBytes: Long = 0,
    val audioBytes: Long = 0,
    val notesBytes: Long = 0,
    val trashBytes: Long = 0
) {
    val totalUsedFormatted: String get() = formatSize(totalBytes)
    val photosFormatted: String get() = formatSize(photosBytes)
    val videosFormatted: String get() = formatSize(videosBytes)
    val docsFormatted: String get() = formatSize(docsBytes)
    val audioFormatted: String get() = formatSize(audioBytes)
    val notesFormatted: String get() = formatSize(notesBytes)
    val trashFormatted: String get() = formatSize(trashBytes)
    
    private fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = if (digitGroups > 4) 4 else digitGroups
        val num = bytes / Math.pow(1024.0, index.toDouble())
        return String.format(java.util.Locale.US, "%.1f %s", num, units[index])
    }
}

data class QrScanItem(
    val id: String,
    val rawValue: String,
    val type: String, // "TEXT", "URL", "WIFI", "CONTACT", "EMAIL", "PHONE"
    val timestamp: Long,
    val title: String,
    val formattedDetails: String
)

data class SecurityItemState(
    val id: String,
    val title: String,
    val status: String,
    val severity: String // "Safe", "Attention", "Warning"
)

data class JourneyTimelineItem(
    val id: String,
    val icon: String,
    val title: String,
    val date: String,
    val description: String,
    val timestamp: Long
)
