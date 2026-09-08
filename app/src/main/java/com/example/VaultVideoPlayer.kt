package com.example

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

/**
 * Supported video scaling modes.
 */
enum class VideoScaleMode {
    FIT,   // Letterbox to fit within screen bounds, maintain aspect ratio
    FILL,  // Zoom/crop to fill the entire screen with no black bars
    STRETCH // Stretch to fill exact container bounds
}

/**
 * Custom VideoView that properly centers and scales according to VideoScaleMode.
 */
class ScalableVideoView(context: Context) : VideoView(context) {
    var scaleMode: VideoScaleMode = VideoScaleMode.FIT
    var customVideoWidth: Int = 0
    var customVideoHeight: Int = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = getDefaultSize(0, widthMeasureSpec)
        val parentHeight = getDefaultSize(0, heightMeasureSpec)

        val vw = if (customVideoWidth > 0) customVideoWidth else 0
        val vh = if (customVideoHeight > 0) customVideoHeight else 0

        if (vw > 0 && vh > 0 && parentWidth > 0 && parentHeight > 0) {
            when (scaleMode) {
                VideoScaleMode.FIT -> {
                    val videoAspect = vw.toFloat() / vh.toFloat()
                    val parentAspect = parentWidth.toFloat() / parentHeight.toFloat()
                    if (videoAspect > parentAspect) {
                        // Video is wider than parent: match parent width
                        val w = parentWidth
                        val h = (parentWidth / videoAspect).toInt()
                        setMeasuredDimension(w, h)
                    } else {
                        // Video is taller than parent: match parent height
                        val h = parentHeight
                        val w = (parentHeight * videoAspect).toInt()
                        setMeasuredDimension(w, h)
                    }
                }
                VideoScaleMode.FILL -> {
                    val videoAspect = vw.toFloat() / vh.toFloat()
                    val parentAspect = parentWidth.toFloat() / parentHeight.toFloat()
                    if (videoAspect > parentAspect) {
                        // Video is wider: expand width so height covers parent completely
                        val h = parentHeight
                        val w = (parentHeight * videoAspect).toInt()
                        setMeasuredDimension(w, h)
                    } else {
                        // Video is taller: expand height so width covers parent completely
                        val w = parentWidth
                        val h = (parentWidth / videoAspect).toInt()
                        setMeasuredDimension(w, h)
                    }
                }
                VideoScaleMode.STRETCH -> {
                    setMeasuredDimension(parentWidth, parentHeight)
                }
            }
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}

/**
 * Finds the host Activity from any Context safely.
 */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Production-ready Vault Video Player with:
 * - Aspect-ratio aware Fullscreen
 * - Centered FrameLayout (eliminates top-left misalignment)
 * - Fit / Fill (Zoom) / Stretch scaling modes
 * - Screen orientation toggle
 * - Gesture-based Brightness, Volume, and Seeking
 */
@Composable
fun VaultVideoPlayer(
    path: String,
    isViewerUiVisible: Boolean,
    onToggleViewerUi: () -> Unit,
    formatDuration: (Int) -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // Read video metadata to detect orientation (portrait vs landscape)
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    LaunchedEffect(path) {
        try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(path)
            val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            mmr.release()
            if (rot == 90 || rot == 270) {
                videoWidth = h
                videoHeight = w
            } else {
                videoWidth = w
                videoHeight = h
            }
        } catch (e: Exception) {
            // Fallback handled via onPrepared
        }
    }

    val isPortraitVideo = remember(videoWidth, videoHeight) {
        videoHeight > 0 && videoWidth > 0 && videoHeight > videoWidth
    }

    var isLandscapeOrientation by remember {
        mutableStateOf(activity?.resources?.configuration?.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
    }

    var isImmersiveFullscreen by remember { mutableStateOf(false) }
    var scaleMode by remember { mutableStateOf(VideoScaleMode.FIT) }

    // Restore orientation and system bars on exit
    DisposableEffect(activity) {
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                val ic = WindowCompat.getInsetsController(act.window, act.window.decorView)
                ic.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var playbackPosition by rememberSaveable(path) { mutableIntStateOf(0) }
    var videoViewRef by remember { mutableStateOf<ScalableVideoView?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekSliderValue by remember { mutableFloatStateOf(0f) }
    var hudText by remember { mutableStateOf("") }
    var hudIcon by remember { mutableStateOf<ImageVector?>(null) }
    var hudVisible by remember { mutableStateOf(false) }

    LaunchedEffect(hudText, hudIcon) {
        if (hudText.isNotEmpty()) {
            hudVisible = true
            delay(1200)
            hudVisible = false
        }
    }

    LaunchedEffect(videoViewRef) {
        while (true) {
            delay(250)
            videoViewRef?.let { vv ->
                if (!isSeeking) {
                    currentPosMs = vv.currentPosition
                    durationMs = vv.duration
                    isPlaying = vv.isPlaying
                    playbackPosition = vv.currentPosition
                }
            }
        }
    }

    fun applySystemBars(hide: Boolean) {
        activity?.let { act ->
            val ic = WindowCompat.getInsetsController(act.window, act.window.decorView)
            ic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hide) {
                ic.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                ic.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Toggle full screen behavior smartly based on video dimensions
    fun toggleFullscreen() {
        activity?.let { act ->
            if (isLandscapeOrientation || isImmersiveFullscreen) {
                // Exit Fullscreen
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                applySystemBars(false)
                isLandscapeOrientation = false
                isImmersiveFullscreen = false
                scaleMode = VideoScaleMode.FIT
                videoViewRef?.let { vv ->
                    vv.scaleMode = VideoScaleMode.FIT
                    vv.requestLayout()
                }
                hudIcon = Icons.Default.FullscreenExit
                hudText = "Standard View"
            } else {
                // Enter Fullscreen
                if (isPortraitVideo) {
                    // For portrait video: stay in portrait, maximize height, hide system bars
                    applySystemBars(true)
                    isImmersiveFullscreen = true
                    scaleMode = VideoScaleMode.FILL
                    videoViewRef?.let { vv ->
                        vv.scaleMode = VideoScaleMode.FILL
                        vv.requestLayout()
                    }
                    hudIcon = Icons.Default.Fullscreen
                    hudText = "Fullscreen (Portrait)"
                } else {
                    // For landscape or standard video: rotate to landscape and hide system bars
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    applySystemBars(true)
                    isLandscapeOrientation = true
                    isImmersiveFullscreen = true
                    scaleMode = VideoScaleMode.FIT
                    videoViewRef?.let { vv ->
                        vv.scaleMode = VideoScaleMode.FIT
                        vv.requestLayout()
                    }
                    hudIcon = Icons.Default.Fullscreen
                    hudText = "Fullscreen (Landscape)"
                }
            }
        }
    }

    // Toggle Orientation manually (Landscape <-> Portrait)
    fun toggleOrientation() {
        activity?.let { act ->
            if (isLandscapeOrientation) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                applySystemBars(false)
                isLandscapeOrientation = false
                isImmersiveFullscreen = false
                hudIcon = Icons.Default.ScreenRotation
                hudText = "Portrait Mode"
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                applySystemBars(true)
                isLandscapeOrientation = true
                isImmersiveFullscreen = true
                hudIcon = Icons.Default.ScreenRotation
                hudText = "Landscape Mode"
            }
        }
    }

    // Cycle Video Scale Mode (Fit -> Fill -> Stretch)
    fun cycleScaleMode() {
        val nextMode = when (scaleMode) {
            VideoScaleMode.FIT -> VideoScaleMode.FILL
            VideoScaleMode.FILL -> VideoScaleMode.STRETCH
            VideoScaleMode.STRETCH -> VideoScaleMode.FIT
        }
        scaleMode = nextMode
        videoViewRef?.let { vv ->
            vv.scaleMode = nextMode
            vv.requestLayout()
        }
        hudIcon = when (nextMode) {
            VideoScaleMode.FIT -> Icons.Default.FitScreen
            VideoScaleMode.FILL -> Icons.Default.CropFree
            VideoScaleMode.STRETCH -> Icons.Default.AspectRatio
        }
        hudText = when (nextMode) {
            VideoScaleMode.FIT -> "Fit to Screen"
            VideoScaleMode.FILL -> "Fill Screen (Zoom)"
            VideoScaleMode.STRETCH -> "Stretch to Screen"
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Native AndroidView with centered ScalableVideoView inside FrameLayout
        AndroidView(
            factory = { ctx ->
                val rootFrame = FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                }

                val vv = ScalableVideoView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                    setVideoPath(path)
                    setMediaController(null)
                    scaleMode = scaleMode
                    if (videoWidth > 0 && videoHeight > 0) {
                        customVideoWidth = videoWidth
                        customVideoHeight = videoHeight
                    }
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        durationMs = mp.duration
                        if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                            customVideoWidth = mp.videoWidth
                            customVideoHeight = mp.videoHeight
                            videoWidth = mp.videoWidth
                            videoHeight = mp.videoHeight
                        }
                        seekTo(playbackPosition)
                        start()
                        isPlaying = true
                        requestLayout()
                    }
                }
                rootFrame.addView(vv)
                videoViewRef = vv
                rootFrame
            },
            update = {
                videoViewRef?.scaleMode = scaleMode
                if (videoWidth > 0 && videoHeight > 0) {
                    videoViewRef?.customVideoWidth = videoWidth
                    videoViewRef?.customVideoHeight = videoHeight
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = {
                videoViewRef?.let { vv ->
                    playbackPosition = vv.currentPosition
                }
                videoViewRef = null
            }
        )

        // Touch Gestures Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val width = size.width.toFloat()
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val isLeft = offset.x < width / 3f
                            val isRight = offset.x > width * 2f / 3f
                            if (isLeft || isRight) {
                                videoViewRef?.let { vv ->
                                    val delta = if (isLeft) -10000 else 10000
                                    val newPos = (vv.currentPosition + delta).coerceIn(0, vv.duration)
                                    vv.seekTo(newPos)
                                    playbackPosition = newPos
                                    currentPosMs = newPos
                                    hudIcon = if (isLeft) Icons.Default.FastRewind else Icons.Default.FastForward
                                    hudText = if (isLeft) "-10s" else "+10s"
                                }
                            } else {
                                // Double tap in center cycles aspect ratio (Fit <-> Fill)
                                cycleScaleMode()
                            }
                        },
                        onTap = {
                            onToggleViewerUi()
                        }
                    )
                }
                .pointerInput(Unit) {
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    var isLeft = false
                    var initialBrightness = 0.5f
                    var initialVolume = 0f
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    detectDragGestures(
                        onDragStart = { offset ->
                            isLeft = offset.x < width / 2f
                            if (isLeft) {
                                val act = context.findActivity()
                                val currentBrightness = act?.window?.attributes?.screenBrightness ?: -1f
                                initialBrightness = if (currentBrightness < 0f) 0.5f else currentBrightness
                            } else {
                                initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaY = -dragAmount.y
                            val scaleY = deltaY / height
                            if (isLeft) {
                                initialBrightness = (initialBrightness + scaleY * 1.5f).coerceIn(0f, 1f)
                                val act = context.findActivity()
                                act?.runOnUiThread {
                                    val lp = act.window.attributes
                                    lp.screenBrightness = initialBrightness
                                    act.window.attributes = lp
                                }
                                hudIcon = Icons.Default.Brightness5
                                hudText = "Brightness: ${Math.round(initialBrightness * 100f)}%"
                            } else {
                                val volumeDelta = scaleY * maxVolume.toFloat() * 1.5f
                                initialVolume = (initialVolume + volumeDelta).coerceIn(0f, maxVolume.toFloat())
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, initialVolume.toInt(), 0)
                                hudIcon = Icons.Default.VolumeUp
                                hudText = "Volume: ${Math.round((initialVolume / maxVolume.toFloat()) * 100f)}%"
                            }
                        }
                    )
                }
        )

        // Top HUD Pill Feedback
        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(250)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 56.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.82f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    hudIcon?.let { icon ->
                        Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF9C27B0), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text = hudText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Bottom Playback Control Bar
        AnimatedVisibility(
            visible = isViewerUiVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Scrubber Seekbar
                val effectivePos = if (isSeeking) seekSliderValue else currentPosMs.toFloat()
                val effectiveDur = if (durationMs > 0) durationMs.toFloat() else 1f
                Slider(
                    value = effectivePos.coerceIn(0f, effectiveDur),
                    onValueChange = {
                        isSeeking = true
                        seekSliderValue = it
                    },
                    onValueChangeFinished = {
                        videoViewRef?.let { vv ->
                            val targetMs = seekSliderValue.toInt().coerceIn(0, vv.duration)
                            vv.seekTo(targetMs)
                            currentPosMs = targetMs
                            playbackPosition = targetMs
                        }
                        isSeeking = false
                    },
                    valueRange = 0f..effectiveDur,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF9C27B0),
                        activeTrackColor = Color(0xFF9C27B0),
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(28.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Play / Pause
                        IconButton(
                            onClick = {
                                videoViewRef?.let { vv ->
                                    if (vv.isPlaying) {
                                        vv.pause()
                                        isPlaying = false
                                    } else {
                                        vv.start()
                                        isPlaying = true
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // -10s Rewind
                        IconButton(
                            onClick = {
                                videoViewRef?.let { vv ->
                                    val newPos = (vv.currentPosition - 10000).coerceAtLeast(0)
                                    vv.seekTo(newPos)
                                    currentPosMs = newPos
                                    playbackPosition = newPos
                                    hudIcon = Icons.Default.FastRewind
                                    hudText = "-10s"
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Rewind 10s",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // +10s Forward
                        IconButton(
                            onClick = {
                                videoViewRef?.let { vv ->
                                    val newPos = (vv.currentPosition + 10000).coerceAtMost(vv.duration)
                                    vv.seekTo(newPos)
                                    currentPosMs = newPos
                                    playbackPosition = newPos
                                    hudIcon = Icons.Default.FastForward
                                    hudText = "+10s"
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Forward 10s",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Time display
                        Text(
                            text = "${formatDuration(currentPosMs)} / ${formatDuration(durationMs)}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Scale Mode (Fit / Fill / Stretch)
                        IconButton(
                            onClick = { cycleScaleMode() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = when (scaleMode) {
                                    VideoScaleMode.FIT -> Icons.Default.FitScreen
                                    VideoScaleMode.FILL -> Icons.Default.CropFree
                                    VideoScaleMode.STRETCH -> Icons.Default.AspectRatio
                                },
                                contentDescription = "Aspect Ratio Mode",
                                tint = if (scaleMode != VideoScaleMode.FIT) Color(0xFF9C27B0) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Orientation Switcher (Landscape / Portrait)
                        IconButton(
                            onClick = { toggleOrientation() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = "Rotate Screen",
                                tint = if (isLandscapeOrientation) Color(0xFF9C27B0) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Fullscreen Toggle
                        val isCurrentlyFullscreen = isLandscapeOrientation || isImmersiveFullscreen
                        IconButton(
                            onClick = { toggleFullscreen() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isCurrentlyFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isCurrentlyFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                                tint = if (isCurrentlyFullscreen) Color(0xFF9C27B0) else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
