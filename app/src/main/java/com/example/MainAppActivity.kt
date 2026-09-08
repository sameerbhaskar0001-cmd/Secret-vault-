package com.example

// Triggering platform hot reload check for emulator container connection
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.ui.theme.MyApplicationTheme
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.view.WindowManager
import android.util.Log

class MainAppActivity : FragmentActivity() {
  private var viewModel: CalculatorViewModel? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d("ActivityLifecycle", "onCreate called. savedInstanceState is null: ${savedInstanceState == null}")
    Log.d("StartupAuthCheck", "Application starting up. Verifying Google OAuth Configuration...")
    val oAuthClientId = com.example.BuildConfig.GOOGLE_OAUTH_CLIENT_ID
    when {
        oAuthClientId.isEmpty() -> {
            Log.e("StartupAuthCheck", "GOOGLE_OAUTH_CLIENT_ID is empty!")
        }
        oAuthClientId == "ADD_YOUR_CLIENT_ID_HERE" -> {
            Log.e("StartupAuthCheck", "GOOGLE_OAUTH_CLIENT_ID is set to the default placeholder: 'ADD_YOUR_CLIENT_ID_HERE'")
        }
        else -> {
            val length = oAuthClientId.length
            val isWebClient = oAuthClientId.endsWith(".apps.googleusercontent.com")
            val firstPart = if (oAuthClientId.contains("-")) oAuthClientId.split("-").firstOrNull() ?: "" else ""
            Log.i("StartupAuthCheck", "GOOGLE_OAUTH_CLIENT_ID is successfully loaded at runtime.")
            Log.i("StartupAuthCheck", "Length: $length characters.")
            Log.i("StartupAuthCheck", "Is valid Web Client ID format (.apps.googleusercontent.com suffix): $isWebClient")
            if (firstPart.isNotEmpty()) {
                Log.i("StartupAuthCheck", "Project Number Prefix: $firstPart")
            }
            // For diagnostic verification without exposing the entire secret middle part in one continuous string,
            // we show the prefix and suffix cleanly so the user can easily verify they matching console.
            val masked = if (oAuthClientId.length > 35) {
                "${oAuthClientId.take(15)}...${oAuthClientId.takeLast(30)}"
            } else {
                oAuthClientId
            }
            Log.i("StartupAuthCheck", "Masked client ID: $masked")
        }
    }

    // Force the window to fit system windows (status and navigation bars) to avoid overlaps globally
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)

    try {
        viewModel = ViewModelProvider(this)[CalculatorViewModel::class.java]

        lifecycleScope.launch {
          viewModel?.preventScreenshots?.collectLatest { prevent ->
            if (prevent) {
              window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
              window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
          }
        }

        setContent {
          val selectedTheme by (viewModel?.selectedTheme ?: kotlinx.coroutines.flow.MutableStateFlow(com.example.ui.theme.AppTheme.GRAPHITE)).collectAsState()
          MyApplicationTheme(theme = selectedTheme) {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
              if (viewModel != null) {
                  CalculatorScreen(
                    viewModel = viewModel!!,
                    modifier = Modifier.fillMaxSize()
                  )
              }
            }
          }
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        setContent {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "CRASH DETECTED:\n${e.message}\n\n${e.stackTraceToString()}",
                    color = Color.Red
                )
            }
        }
    }
  }

  override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
      if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
          if (viewModel?.isCameraActive?.value == true) {
              viewModel?.cameraTriggerFlow?.tryEmit(Unit)
              return true
          }
      }
      return super.onKeyDown(keyCode, event)
  }

  override fun onPause() {
      super.onPause()
      if (!isChangingConfigurations && viewModel?.isPickingFile != true && VaultBrowserIntegration.activeUploadRequest == null) {
          viewModel?.onAppBackgrounded()
      }
  }
  
  override fun onStop() {
    super.onStop()
    val isStealth = viewModel?.stealthMode?.value == true
    if (!isChangingConfigurations && viewModel?.isPickingFile != true && VaultBrowserIntegration.activeUploadRequest == null) {
      try {
          if (viewModel?.lockOnBackground?.value == true) {
              viewModel?.lockVault()
          }
          if (isStealth) {
              finishAndRemoveTask()
          }
      } catch (e: Exception) {
          e.printStackTrace()
      }
    }
  }

  override fun onDestroy() {
    Log.d("ActivityLifecycle", "onDestroy called - isChangingConfigurations: $isChangingConfigurations, isFinishing: $isFinishing")
    super.onDestroy()
  }

  override fun onTrimMemory(level: Int) {
      super.onTrimMemory(level)
      Log.d("ActivityLifecycle", "onTrimMemory called with level: $level")
      // Broadcast memory pressure to view model or handle here.
      // E.g., if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) { ... }
      viewModel?.handleMemoryPressure(level)
  }

  override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
    super.onConfigurationChanged(newConfig)
    Log.d("ActivityLifecycle", "onConfigurationChanged called - orientation: ${newConfig.orientation}")
    val customView = viewModel?.browserCustomView
    if (customView != null) {
        customView.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        customView.requestLayout()
    }
  }
}
