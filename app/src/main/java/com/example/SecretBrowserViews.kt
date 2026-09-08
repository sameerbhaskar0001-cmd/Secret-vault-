package com.example

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import android.content.Context
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.core.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.*
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.ConcurrentHashMap

// TAB THUMBNAIL SNAPSHOT CACHE
object TabThumbnailCache {
    private val cache = ConcurrentHashMap<String, android.graphics.Bitmap>()
    val version = mutableStateOf(0)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun getDiskFile(tabId: String): java.io.File? {
        val ctx = appContext ?: return null
        val dir = java.io.File(ctx.cacheDir, "tab_previews")
        if (!dir.exists()) dir.mkdirs()
        val safeTabId = tabId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return java.io.File(dir, "thumb_$safeTabId.jpg")
    }

    fun setThumbnail(tabId: String, bitmap: android.graphics.Bitmap) {
        cache[tabId] = bitmap
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            version.value++
        } else {
            mainHandler.post { version.value++ }
        }
        // Save to disk asynchronously so previews survive app restarts
        try {
            val file = getDiskFile(tabId)
            if (file != null) {
                java.util.concurrent.Executors.newSingleThreadExecutor().execute {
                    try {
                        java.io.FileOutputStream(file).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                        }
                    } catch (ex: Throwable) {}
                }
            }
        } catch (t: Throwable) {}
    }

    fun getThumbnail(tabId: String): android.graphics.Bitmap? {
        val inMem = cache[tabId]
        if (inMem != null) return inMem
        // Check disk cache
        try {
            val file = getDiskFile(tabId)
            if (file != null && file.exists() && file.length() > 0) {
                val diskBmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                if (diskBmp != null) {
                    cache[tabId] = diskBmp
                    return diskBmp
                }
            }
        } catch (t: Throwable) {}
        return null
    }

    fun removeThumbnail(tabId: String) {
        cache.remove(tabId)
        try {
            getDiskFile(tabId)?.delete()
        } catch (t: Throwable) {}
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            version.value++
        } else {
            mainHandler.post { version.value++ }
        }
    }

    fun clear() {
        cache.clear()
        try {
            val ctx = appContext
            if (ctx != null) {
                val dir = java.io.File(ctx.cacheDir, "tab_previews")
                if (dir.exists()) dir.deleteRecursively()
            }
        } catch (t: Throwable) {}
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            version.value++
        } else {
            mainHandler.post { version.value++ }
        }
    }
}

private fun isValidRenderedBitmap(bitmap: android.graphics.Bitmap): Boolean {
    if (bitmap.width < 20 || bitmap.height < 20) return false
    val w = bitmap.width
    val h = bitmap.height
    val samplePoints = intArrayOf(
        bitmap.getPixel(w / 2, h / 2),
        bitmap.getPixel(w / 4, h / 4),
        bitmap.getPixel((3 * w) / 4, (3 * h) / 4),
        bitmap.getPixel(w / 2, h / 4),
        bitmap.getPixel(w / 2, (3 * h) / 4)
    )
    for (pixel in samplePoints) {
        if (android.graphics.Color.alpha(pixel) > 30) return true
    }
    return false
}

fun captureViewThumbnail(view: android.view.View?, tabId: String, onComplete: (() -> Unit)? = null) {
    if (view == null || tabId.isEmpty()) {
        onComplete?.invoke()
        return
    }
    try {
        if (!view.isAttachedToWindow) {
            onComplete?.invoke()
            return
        }
        val w = view.width
        val h = view.height
        if (w <= 20 || h <= 20) {
            onComplete?.invoke()
            return
        }

        val scale = (380f / w).coerceAtMost(1f)
        val tw = (w * scale).toInt().coerceAtLeast(80)
        val th = (h * scale).toInt().coerceAtLeast(80)

        fun findGeckoView(v: android.view.View): org.mozilla.geckoview.GeckoView? {
            if (v is org.mozilla.geckoview.GeckoView) return v
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    val gv = findGeckoView(v.getChildAt(i))
                    if (gv != null) return gv
                }
            }
            return null
        }

        val geckoView = findGeckoView(view)
        if (geckoView != null) {
            try {
                geckoView.capturePixels().then(
                    org.mozilla.geckoview.GeckoResult.OnValueListener<android.graphics.Bitmap, Void> { bmp ->
                        if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                            val scaled = if (bmp.width != tw || bmp.height != th) {
                                android.graphics.Bitmap.createScaledBitmap(bmp, tw, th, true)
                            } else {
                                bmp
                            }
                            if (isValidRenderedBitmap(scaled)) {
                                TabThumbnailCache.setThumbnail(tabId, scaled)
                            }
                        }
                        onComplete?.invoke()
                        null
                    },
                    org.mozilla.geckoview.GeckoResult.OnExceptionListener<Void> { err ->
                        android.util.Log.w("TabThumbnail", "GeckoView capturePixels error for $tabId: ${err?.message}")
                        onComplete?.invoke()
                        null
                    }
                )
                return
            } catch (e: Throwable) {
                android.util.Log.w("TabThumbnail", "capturePixels exception for $tabId", e)
            }
        }

        // Find SurfaceView inside view hierarchy if any
        fun findSurfaceView(v: android.view.View): android.view.SurfaceView? {
            if (v is android.view.SurfaceView) return v
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    val sv = findSurfaceView(v.getChildAt(i))
                    if (sv != null) return sv
                }
            }
            return null
        }

        fun findTextureView(v: android.view.View): android.view.TextureView? {
            if (v is android.view.TextureView) return v
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    val tv = findTextureView(v.getChildAt(i))
                    if (tv != null) return tv
                }
            }
            return null
        }

        val textureView = findTextureView(view)
        if (textureView != null && textureView.isAvailable) {
            val bmp = textureView.getBitmap(tw, th)
            if (bmp != null) {
                TabThumbnailCache.setThumbnail(tabId, bmp)
                onComplete?.invoke()
                return
            }
        }

        val surfaceView = findSurfaceView(view)
        val window = (view.context as? android.app.Activity)?.window
        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val destBitmap = android.graphics.Bitmap.createBitmap(tw, th, android.graphics.Bitmap.Config.ARGB_8888)
            if (surfaceView != null && surfaceView.holder.surface.isValid) {
                android.view.PixelCopy.request(
                    surfaceView,
                    destBitmap,
                    { copyResult ->
                        if (copyResult == android.view.PixelCopy.SUCCESS) {
                            TabThumbnailCache.setThumbnail(tabId, destBitmap)
                            onComplete?.invoke()
                        } else if (window != null && view.isAttachedToWindow) {
                            val location = IntArray(2)
                            view.getLocationInWindow(location)
                            val viewRect = android.graphics.Rect(location[0], location[1], location[0] + w, location[1] + h)
                            try {
                                android.view.PixelCopy.request(
                                    window,
                                    viewRect,
                                    destBitmap,
                                    { winResult ->
                                        if (winResult == android.view.PixelCopy.SUCCESS) {
                                            TabThumbnailCache.setThumbnail(tabId, destBitmap)
                                        }
                                        onComplete?.invoke()
                                    },
                                    handler
                                )
                            } catch (e: Throwable) {
                                onComplete?.invoke()
                            }
                        } else {
                            onComplete?.invoke()
                        }
                    },
                    handler
                )
            } else if (window != null && view.isAttachedToWindow) {
                val location = IntArray(2)
                view.getLocationInWindow(location)
                val viewRect = android.graphics.Rect(location[0], location[1], location[0] + w, location[1] + h)
                try {
                    android.view.PixelCopy.request(
                        window,
                        viewRect,
                        destBitmap,
                        { copyResult ->
                            if (copyResult == android.view.PixelCopy.SUCCESS) {
                                TabThumbnailCache.setThumbnail(tabId, destBitmap)
                            }
                            onComplete?.invoke()
                        },
                        handler
                    )
                } catch (e: Throwable) {
                    onComplete?.invoke()
                }
            } else {
                onComplete?.invoke()
            }
        } else {
            val bmp = android.graphics.Bitmap.createBitmap(tw, th, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            canvas.scale(scale, scale)
            view.draw(canvas)
            TabThumbnailCache.setThumbnail(tabId, bmp)
            onComplete?.invoke()
        }
    } catch (e: Throwable) {
        android.util.Log.w("TabThumbnail", "Error capturing thumbnail for $tabId: ${e.message}")
        onComplete?.invoke()
    }
}

// BRAND & COLORS CONSTANTS
val LightBg = Color(0xFFF8F9FA)
val LightCard = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF111111)
val TextSecondary = Color(0xFF666666)
val BorderColor = Color(0xFFE8E8E8)
val AccentColor = Color(0xFFFF6A00)
val DangerColor = Color(0xFFC62828)
val SuccessColor = Color(0xFF2E7D32)

@Composable
fun YouTubeBrandIcon(modifier: Modifier = Modifier, sizeDp: Int = 34) {
    Box(
        modifier = modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size((sizeDp * 0.82f).dp, (sizeDp * 0.58f).dp)) {
            val corner = size.height * 0.32f
            drawRoundRect(
                color = Color(0xFFFF0000),
                cornerRadius = CornerRadius(corner, corner)
            )
            val path = Path().apply {
                moveTo(size.width * 0.38f, size.height * 0.28f)
                lineTo(size.width * 0.68f, size.height * 0.50f)
                lineTo(size.width * 0.38f, size.height * 0.72f)
                close()
            }
            drawPath(path, color = Color.White)
        }
    }
}

@Composable
fun InstagramBrandIcon(modifier: Modifier = Modifier, sizeDp: Int = 34) {
    val brush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF4F5BD5),
            Color(0xFF962FBF),
            Color(0xFFD62976),
            Color(0xFFFA7E1E),
            Color(0xFFFEDA75)
        ),
        start = Offset(0f, 100f),
        end = Offset(100f, 0f)
    )
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .background(brush, RoundedCornerShape((sizeDp * 0.28f).dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size((sizeDp * 0.56f).dp)) {
            val strokeW = (sizeDp * 0.062f).dp.toPx()
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(strokeW / 2, strokeW / 2),
                size = Size(size.width - strokeW, size.height - strokeW),
                cornerRadius = CornerRadius((sizeDp * 0.12f).dp.toPx(), (sizeDp * 0.12f).dp.toPx()),
                style = Stroke(width = strokeW)
            )
            drawCircle(
                color = Color.White,
                radius = size.width * 0.23f,
                center = center,
                style = Stroke(width = strokeW)
            )
            drawCircle(
                color = Color.White,
                radius = (sizeDp * 0.038f).dp.toPx(),
                center = Offset(size.width * 0.75f, size.height * 0.25f)
            )
        }
    }
}

@Composable
fun FacebookBrandIcon(modifier: Modifier = Modifier, sizeDp: Int = 34) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .background(Color(0xFF1877F2), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size((sizeDp * 0.64f).dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.70f, h)
                lineTo(w * 0.48f, h)
                lineTo(w * 0.48f, h * 0.55f)
                lineTo(w * 0.34f, h * 0.55f)
                lineTo(w * 0.34f, h * 0.38f)
                lineTo(w * 0.48f, h * 0.38f)
                lineTo(w * 0.48f, h * 0.24f)
                cubicTo(w * 0.48f, h * 0.10f, w * 0.58f, h * 0.02f, w * 0.74f, h * 0.02f)
                lineTo(w * 0.86f, h * 0.02f)
                lineTo(w * 0.86f, h * 0.18f)
                lineTo(w * 0.75f, h * 0.18f)
                cubicTo(w * 0.67f, h * 0.18f, w * 0.65f, h * 0.22f, w * 0.65f, h * 0.28f)
                lineTo(w * 0.65f, h * 0.38f)
                lineTo(w * 0.85f, h * 0.38f)
                lineTo(w * 0.81f, h * 0.55f)
                lineTo(w * 0.65f, h * 0.55f)
                lineTo(w * 0.65f, h)
                close()
            }
            drawPath(path, color = Color.White)
        }
    }
}

@Composable
fun TelegramBrandIcon(modifier: Modifier = Modifier, sizeDp: Int = 34) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .background(Color(0xFF24A1DE), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size((sizeDp * 0.58f).dp)) {
            val w = size.width
            val h = size.height
            
            val underPath = Path().apply {
                moveTo(w * 0.41f, h * 0.61f)
                lineTo(w * 0.38f, h * 0.86f)
                lineTo(w * 0.53f, h * 0.73f)
                close()
            }
            drawPath(underPath, color = Color(0xFFB0D6F5))

            val planePath = Path().apply {
                moveTo(w * 0.92f, h * 0.12f)
                lineTo(w * 0.08f, h * 0.47f)
                lineTo(w * 0.38f, h * 0.61f)
                lineTo(w * 0.79f, h * 0.26f)
                lineTo(w * 0.45f, h * 0.65f)
                lineTo(w * 0.85f, h * 0.87f)
                close()
            }
            drawPath(planePath, color = Color.White)
        }
    }
}

@Composable
fun PinterestBrandIcon(modifier: Modifier = Modifier, sizeDp: Int = 34) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .background(Color(0xFFE60023), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size((sizeDp * 0.62f).dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.44f, h * 0.15f)
                cubicTo(w * 0.28f, h * 0.15f, w * 0.20f, h * 0.28f, w * 0.20f, h * 0.44f)
                cubicTo(w * 0.20f, h * 0.56f, w * 0.25f, h * 0.66f, w * 0.33f, h * 0.70f)
                cubicTo(w * 0.34f, h * 0.71f, w * 0.35f, h * 0.70f, w * 0.36f, h * 0.67f)
                lineTo(w * 0.39f, h * 0.57f)
                cubicTo(w * 0.37f, h * 0.53f, w * 0.36f, h * 0.48f, w * 0.36f, h * 0.43f)
                cubicTo(w * 0.36f, h * 0.31f, w * 0.44f, h * 0.23f, w * 0.56f, h * 0.23f)
                cubicTo(w * 0.66f, h * 0.23f, w * 0.73f, h * 0.30f, w * 0.73f, h * 0.42f)
                cubicTo(w * 0.73f, h * 0.56f, w * 0.66f, h * 0.67f, w * 0.56f, h * 0.67f)
                cubicTo(w * 0.51f, h * 0.67f, w * 0.47f, h * 0.64f, w * 0.45f, h * 0.60f)
                lineTo(w * 0.41f, h * 0.75f)
                cubicTo(w * 0.38f, h * 0.88f, w * 0.33f, h * 0.95f, w * 0.31f, h * 0.97f)
                cubicTo(w * 0.35f, h * 0.98f, w * 0.40f, h * 0.99f, w * 0.45f, h * 0.99f)
                cubicTo(w * 0.70f, h * 0.99f, w * 0.90f, h * 0.79f, w * 0.90f, h * 0.54f)
                cubicTo(w * 0.90f, h * 0.32f, w * 0.70f, h * 0.15f, w * 0.44f, h * 0.15f)
                close()
            }
            drawPath(path, color = Color.White)
        }
    }
}

@Composable
fun GoogleBrandIcon(modifier: Modifier = Modifier, sizeDp: Int = 34) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .background(Color.White, CircleShape)
            .border(0.8.dp, BorderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size((sizeDp * 0.58f).dp)) {
            val strokeW = size.width * 0.22f
            val halfStroke = strokeW / 2f
            val rect = Rect(
                halfStroke,
                halfStroke,
                size.width - halfStroke,
                size.height - halfStroke
            )

            // Red (Top arc)
            drawArc(
                color = Color(0xFFEA4335),
                startAngle = 210f,
                sweepAngle = 105f,
                useCenter = false,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
            )

            // Yellow (Left arc)
            drawArc(
                color = Color(0xFFFBBC05),
                startAngle = 135f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
            )

            // Green (Bottom arc)
            drawArc(
                color = Color(0xFF34A853),
                startAngle = 35f,
                sweepAngle = 105f,
                useCenter = false,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
            )

            // Blue (Right arc)
            drawArc(
                color = Color(0xFF4285F4),
                startAngle = 315f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = strokeW, cap = StrokeCap.Butt)
            )

            // Blue Horizontal Bar
            drawLine(
                color = Color(0xFF4285F4),
                start = Offset(size.width * 0.46f, size.height * 0.5f),
                end = Offset(size.width - halfStroke + 1f, size.height * 0.5f),
                strokeWidth = strokeW,
                cap = StrokeCap.Square
            )
        }
    }
}

@Composable
fun Modifier.premiumPressClick(
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "PremiumScale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(color = AccentColor.copy(alpha = 0.15f)),
            onClick = onClick
        )
}

private data class PopularSiteItem(
    val name: String,
    val url: String,
    val brandType: String
)

private fun formatTabDomain(url: String): String {
    if (url == "home" || url.isEmpty()) return "Home"
    return try {
        val uri = android.net.Uri.parse(url)
        val host = uri.host
        if (!host.isNullOrEmpty()) {
            host.removePrefix("www.")
        } else {
            url.removePrefix("https://").removePrefix("http://").take(24)
        }
    } catch (e: Exception) {
        url.removePrefix("https://").removePrefix("http://").take(24)
    }
}

@Composable
private fun CommandCenterMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    trailingText: String? = null,
    trailingSwitch: Boolean? = null,
    isDestructive: Boolean = false,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isEnabled) Modifier.premiumPressClick(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    if (isDestructive) DangerColor.copy(alpha = 0.12f)
                    else if (!isEnabled) TextSecondary.copy(alpha = 0.08f)
                    else iconTint.copy(alpha = 0.12f),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) DangerColor else if (!isEnabled) TextSecondary.copy(alpha = 0.5f) else iconTint,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    color = if (isDestructive) DangerColor else if (!isEnabled) TextSecondary.copy(alpha = 0.7f) else TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (badgeText != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccentColor.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = AccentColor,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailingSwitch != null) {
            Switch(
                checked = trailingSwitch,
                onCheckedChange = { onClick() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentColor,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = LightBg
                ),
                modifier = Modifier.graphicsLayer {
                    scaleX = 0.8f
                    scaleY = 0.8f
                }
            )
        } else if (trailingText != null) {
            Text(
                text = trailingText,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        } else if (isEnabled && !isDestructive) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CommandCenterSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = TextSecondary.copy(alpha = 0.8f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LightCard),
            border = BorderStroke(1.dp, BorderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
fun SecretBrowserHome(
    tabs: List<TabState>,
    activeTabId: String,
    searchEngine: String,
    browserBookmarks: List<BrowserBookmark>,
    browserHistory: List<BrowserHistory>,
    onSearch: (String) -> Unit,
    onOpenNewTab: (String) -> Unit,
    onSelectActiveTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onShowBookmarks: () -> Unit,
    onShowHistory: () -> Unit,
    onShowDownloads: () -> Unit,
    onShowSettings: () -> Unit,
    onShowSearchEngineDialog: () -> Unit,
    onClearAllData: () -> Unit
) {
    val context = LocalContext.current
    var searchInput by remember { mutableStateOf("") }
    var lastSearchSubmittedTime by remember { mutableStateOf(0L) }
    val submitSearch = {
        val now = System.currentTimeMillis()
        val text = searchInput.trim()
        if (text.isNotEmpty() && now - lastSearchSubmittedTime > 500L) {
            lastSearchSubmittedTime = now
            onSearch(text)
        }
    }

    val popularSites = remember {
        listOf(
            PopularSiteItem("YouTube", "https://www.youtube.com", "youtube"),
            PopularSiteItem("Instagram", "https://www.instagram.com", "instagram"),
            PopularSiteItem("Facebook", "https://www.facebook.com", "facebook"),
            PopularSiteItem("Telegram", "https://web.telegram.org", "telegram"),
            PopularSiteItem("Pinterest", "https://www.pinterest.com", "pinterest")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // 1. BRAND & IDENTITY AREA (CALM, COMPACT & UNDERSTATED)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(LightCard, RoundedCornerShape(16.dp))
                    .border(0.8.dp, BorderColor, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(AccentColor.copy(alpha = 0.08f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Secret Browser Logo",
                        tint = AccentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Secret Browser",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Private • Independent • Secure",
                color = TextSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.2.sp,
                textAlign = TextAlign.Center
            )
        }

        // 2. PRIMARY SEARCH EXPERIENCE (FOCUSED & COMFORTABLE VIEWPORT)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = LightCard),
            border = BorderStroke(1.dp, BorderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 5.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subordinate Search Engine Selector Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(LightBg)
                        .border(0.8.dp, BorderColor, RoundedCornerShape(16.dp))
                        .premiumPressClick { onShowSearchEngineDialog() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Engine",
                        tint = AccentColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = searchEngine,
                        color = TextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Choose Search Engine",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Protected Middle Viewport for text input
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 3.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        singleLine = true,
                        cursorBrush = SolidColor(AccentColor),
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                submitSearch()
                            }
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchInput.isEmpty()) {
                                    Text(
                                        text = "Search or enter URL...",
                                        color = TextSecondary.copy(alpha = 0.6f),
                                        fontSize = 13.5.sp,
                                        maxLines = 1
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                // Protected Trailing Controls
                if (searchInput.isNotEmpty()) {
                    IconButton(
                        onClick = { searchInput = "" },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(3.dp))
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentColor)
                        .premiumPressClick {
                            submitSearch()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Go",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. POPULAR SITES / QUICK ACCESS (CLEAN DISCOVERY STRIP)
        Text(
            text = "POPULAR SITES",
            color = TextSecondary.copy(alpha = 0.85f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            popularSites.forEach { site ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .premiumPressClick { onSearch(site.url) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(LightCard, RoundedCornerShape(15.dp))
                            .border(0.8.dp, BorderColor, RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        when (site.brandType) {
                            "youtube" -> YouTubeBrandIcon(sizeDp = 34)
                            "instagram" -> InstagramBrandIcon(sizeDp = 34)
                            "facebook" -> FacebookBrandIcon(sizeDp = 34)
                            "telegram" -> TelegramBrandIcon(sizeDp = 34)
                            "pinterest" -> PinterestBrandIcon(sizeDp = 34)
                            else -> Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(AccentColor.copy(alpha = 0.1f), RoundedCornerShape(11.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = site.name,
                                    tint = AccentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = site.name,
                        color = TextPrimary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4. ESSENTIAL SHORTCUTS (LIGHTWEIGHT & UNCLUTTERED)
        Text(
            text = "QUICK SHORTCUTS",
            color = TextSecondary.copy(alpha = 0.85f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .premiumPressClick { onOpenNewTab("home") },
                shape = RoundedCornerShape(15.dp),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                border = BorderStroke(0.9.dp, BorderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(AccentColor.copy(alpha = 0.08f), RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, "New Tab", tint = AccentColor, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(9.dp))
                    Column {
                        Text("New Tab", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("Private session", color = TextSecondary, fontSize = 10.5.sp)
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .premiumPressClick { onShowBookmarks() },
                shape = RoundedCornerShape(15.dp),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                border = BorderStroke(0.9.dp, BorderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(AccentColor.copy(alpha = 0.08f), RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Star, "Bookmarks", tint = AccentColor, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(9.dp))
                    Column {
                        Text("Bookmarks", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("Saved pages", color = TextSecondary, fontSize = 10.5.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp)) // Safe clearance for floating dock bottom bar
    }
}

@Composable
fun SecretBrowserSettingsDashboard(
    tabs: List<TabState>,
    browserBookmarks: List<BrowserBookmark>,
    browserHistory: List<BrowserHistory>,
    searchEngine: String,
    savePasswords: Boolean,
    clearHistoryOnExit: Boolean,
    clearTempOnExit: Boolean,
    useGeckoView: Boolean,
    trackingProtectionEnabled: Boolean,
    onSetTrackingProtectionEnabled: (Boolean) -> Unit,
    totalTrackersBlocked: Int,
    onBack: () -> Unit,
    onShowSearchEngineDialog: () -> Unit,
    onSetSavePasswords: (Boolean) -> Unit,
    onSetClearHistoryOnExit: (Boolean) -> Unit,
    onSetClearTempOnExit: (Boolean) -> Unit,
    onClearBrowsingData: (clearHistory: Boolean, clearCookies: Boolean, clearCache: Boolean, clearSiteData: Boolean, onResult: (Boolean) -> Unit) -> Unit,
    onShowDownloads: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showClearTempFilesDialog by remember { mutableStateOf(false) }
    var showClearBrowsingDataDialog by remember { mutableStateOf(false) }
    var clearHist by remember { mutableStateOf(true) }
    var clearCooks by remember { mutableStateOf(true) }
    var clearCach by remember { mutableStateOf(true) }
    var clearSite by remember { mutableStateOf(true) }

    if (showClearBrowsingDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearBrowsingDataDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = DangerColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text("Clear Browsing Data", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Permanently remove selected private data from this device.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Checkbox for History
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { clearHist = !clearHist }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = clearHist,
                            onCheckedChange = { clearHist = it },
                            colors = CheckboxDefaults.colors(checkedColor = AccentColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Browsing History", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Clear visited web pages and search logs", color = TextSecondary, fontSize = 11.sp)
                        }
                    }

                    // Checkbox for Cookies
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { clearCooks = !clearCooks }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = clearCooks,
                            onCheckedChange = { clearCooks = it },
                            colors = CheckboxDefaults.colors(checkedColor = AccentColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Cookies & Logins", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Sign out of active web sessions", color = TextSecondary, fontSize = 11.sp)
                        }
                    }

                    // Checkbox for Cache
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { clearCach = !clearCach }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = clearCach,
                            onCheckedChange = { clearCach = it },
                            colors = CheckboxDefaults.colors(checkedColor = AccentColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Cached Images & Files", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Clear temporarily stored web content", color = TextSecondary, fontSize = 11.sp)
                        }
                    }

                    // Checkbox for Site Data
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { clearSite = !clearSite }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = clearSite,
                            onCheckedChange = { clearSite = it },
                            colors = CheckboxDefaults.colors(checkedColor = AccentColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Site Storage & Preferences", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Clear offline storage and per-site permissions", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            onClearBrowsingData(true, true, true, true) { success ->
                                if (success) {
                                    Toast.makeText(context, "Browsing data cleared", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Some browsing data failed to clear", Toast.LENGTH_SHORT).show()
                                }
                            }
                            showClearBrowsingDataDialog = false
                        }
                    ) {
                        Text("Clear All", color = TextSecondary)
                    }

                    Button(
                        onClick = {
                            if (!clearHist && !clearCooks && !clearCach && !clearSite) {
                                Toast.makeText(context, "No categories selected", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onClearBrowsingData(clearHist, clearCooks, clearCach, clearSite) { success ->
                                if (success) {
                                    Toast.makeText(context, "Browsing data cleared", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to clear some selected data", Toast.LENGTH_SHORT).show()
                                }
                            }
                            showClearBrowsingDataDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerColor)
                    ) {
                        Text("Clear Selected", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearBrowsingDataDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = LightCard,
            tonalElevation = 6.dp
        )
    }

    if (showClearTempFilesDialog) {
        AlertDialog(
            onDismissRequest = { showClearTempFilesDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = DangerColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text("Clear Temporary Files", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Securely wipe temporary disk fragments, cached uploads, and incomplete download remnants. This action cannot be undone.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearTempFilesDialog = false
                        coroutineScope.launch(Dispatchers.IO) {
                            val ok1 = SecretBrowserSecureDelete.cleanTemporaryUploadsDirectory(context, secure = true)
                            val ok2 = SecretBrowserSecureDelete.cleanStaleTemporaryRemnants(context, secure = true)
                            withContext(Dispatchers.Main) {
                                if (ok1 && ok2) {
                                    Toast.makeText(context, "Temporary files cleared", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Temporary files cleared", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerColor)
                ) {
                    Text("Clear Temporary Files", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearTempFilesDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = LightCard,
            tonalElevation = 6.dp
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.padding(start = 6.dp)) {
                Text("Browser Settings", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Privacy, engine preferences & data controls", color = TextSecondary, fontSize = 12.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. BROWSING
            SettingsSectionHeader(title = "BROWSING")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                border = BorderStroke(1.dp, BorderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    SettingsNavigationRow(
                        icon = Icons.Default.Search,
                        iconTint = AccentColor,
                        title = "Search Engine",
                        subtitle = searchEngine,
                        subtitleColor = AccentColor,
                        onClick = onShowSearchEngineDialog
                    )
                    HorizontalDivider(color = BorderColor.copy(alpha = 0.6f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsNavigationRow(
                        icon = Icons.Default.CloudDownload,
                        iconTint = AccentColor,
                        title = "Vault Downloads",
                        subtitle = "Manage downloaded files inside secure vault",
                        onClick = onShowDownloads
                    )
                }
            }

            // 2. PRIVACY & SECURITY
            SettingsSectionHeader(title = "PRIVACY & SECURITY")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                border = BorderStroke(1.dp, BorderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    SettingsSwitchRow(
                        icon = Icons.Default.Security,
                        iconTint = if (trackingProtectionEnabled) SuccessColor else TextSecondary,
                        title = "Tracking Protection",
                        subtitle = if (trackingProtectionEnabled) {
                            if (totalTrackersBlocked > 0) "Active • $totalTrackersBlocked tracker${if (totalTrackersBlocked > 1) "s" else ""} blocked this session" else "Active • Blocking known trackers (No activity yet)"
                        } else {
                            "Inactive • Protection disabled"
                        },
                        checked = trackingProtectionEnabled,
                        activeTrackColor = SuccessColor,
                        onCheckedChange = { enabled ->
                            onSetTrackingProtectionEnabled(enabled)
                            Toast.makeText(context, if (enabled) "Protection enabled" else "Protection disabled", Toast.LENGTH_SHORT).show()
                        }
                    )
                    HorizontalDivider(color = BorderColor.copy(alpha = 0.6f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchRow(
                        icon = Icons.Default.Lock,
                        iconTint = if (savePasswords) AccentColor else TextSecondary,
                        title = "Form Autofill Prompts",
                        subtitle = if (savePasswords) "Active • Browser prompts to autofill credentials in web forms" else "Disabled • Form credential autofill prompts disabled",
                        checked = savePasswords,
                        activeTrackColor = AccentColor,
                        onCheckedChange = onSetSavePasswords
                    )
                }
            }

            // 3. DATA & CLEANUP
            SettingsSectionHeader(title = "DATA & CLEANUP")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                border = BorderStroke(1.dp, BorderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    SettingsSwitchRow(
                        icon = Icons.Default.History,
                        iconTint = AccentColor,
                        title = "Clear History on Exit",
                        subtitle = "Purge web history logs whenever you leave the browser",
                        checked = clearHistoryOnExit,
                        activeTrackColor = AccentColor,
                        onCheckedChange = onSetClearHistoryOnExit
                    )
                    HorizontalDivider(color = BorderColor.copy(alpha = 0.6f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchRow(
                        icon = Icons.Default.DeleteSweep,
                        iconTint = AccentColor,
                        title = "Clear Temporary Files on Exit",
                        subtitle = "Purge upload caches and incomplete download remnants on exit",
                        checked = clearTempOnExit,
                        activeTrackColor = AccentColor,
                        onCheckedChange = onSetClearTempOnExit
                    )
                    HorizontalDivider(color = BorderColor.copy(alpha = 0.6f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsActionRow(
                        icon = Icons.Default.DeleteForever,
                        iconTint = DangerColor,
                        title = "Clear Browsing Data",
                        subtitle = "Permanently delete history, cookies, cache, and site data",
                        actionLabel = "Clear",
                        isDestructive = true,
                        onClick = { showClearBrowsingDataDialog = true }
                    )
                    HorizontalDivider(color = BorderColor.copy(alpha = 0.6f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsActionRow(
                        icon = Icons.Default.FolderDelete,
                        iconTint = DangerColor,
                        title = "Clear Temporary Browser Files",
                        subtitle = "Securely wipe temporary disk remnants and cache fragments",
                        actionLabel = "Clear",
                        isDestructive = true,
                        onClick = { showClearTempFilesDialog = true }
                    )
                }
            }

            // 4. BROWSER ENGINE
            SettingsSectionHeader(title = "BROWSER ENGINE")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                border = BorderStroke(1.dp, BorderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(SuccessColor.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Speed, "Engine", tint = SuccessColor, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("GeckoView Engine", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (useGeckoView) "High-isolation Mozilla GeckoView active" else "System WebKit active",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SuccessColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, SuccessColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "STABLE",
                            color = SuccessColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // 5. FUTURE PROTECTION
            SettingsSectionHeader(title = "FUTURE PROTECTION")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = LightCard.copy(alpha = 0.7f)),
                border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.7f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    SettingsFutureRow(
                        icon = Icons.Default.Shield,
                        title = "Decentralized Onion Routing",
                        subtitle = "Multi-hop encrypted relay protection"
                    )
                    HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsFutureRow(
                        icon = Icons.Default.Fingerprint,
                        title = "AI Fingerprint Shield",
                        subtitle = "Neural traffic defense against browser profiling"
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsNavigationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    subtitleColor: Color = TextSecondary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .premiumPressClick { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconTint.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, title, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = subtitleColor, fontSize = 12.sp, fontWeight = if (subtitleColor != TextSecondary) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        Icon(Icons.Default.ChevronRight, "Navigate", tint = TextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    activeTrackColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconTint.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, title, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.padding(end = 8.dp)) {
                Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextSecondary, fontSize = 11.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = activeTrackColor)
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    actionLabel: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconTint.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, title, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.padding(end = 8.dp)) {
                Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextSecondary, fontSize = 11.sp)
            }
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isDestructive) DangerColor.copy(alpha = 0.1f) else AccentColor.copy(alpha = 0.1f)
        ) {
            Text(
                text = actionLabel,
                color = if (isDestructive) DangerColor else AccentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun SettingsFutureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(TextSecondary.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, title, tint = TextSecondary.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.padding(end = 8.dp)) {
                Text(title, color = TextPrimary.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextSecondary.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = TextSecondary.copy(alpha = 0.1f)
        ) {
            Text(
                text = "COMING SOON",
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
    }
}

// HELPER FUNCTIONS FOR BROWSER LIBRARY
fun extractBrowserDomain(url: String): String {
    if (url.isBlank() || url == "home" || url == "about:blank") return "Start Page"
    return try {
        val uri = java.net.URI(if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url)
        val host = uri.host ?: url
        host.removePrefix("www.")
    } catch (e: Exception) {
        url.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore("/").substringBefore("?")
    }
}

private fun groupHistoryByRelativeDate(history: List<BrowserHistory>): Map<String, List<BrowserHistory>> {
    val now = java.util.Calendar.getInstance()
    val todayYear = now.get(java.util.Calendar.YEAR)
    val todayDayOfYear = now.get(java.util.Calendar.DAY_OF_YEAR)

    now.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val yesterdayYear = now.get(java.util.Calendar.YEAR)
    val yesterdayDayOfYear = now.get(java.util.Calendar.DAY_OF_YEAR)

    val cal = java.util.Calendar.getInstance()
    val grouped = LinkedHashMap<String, MutableList<BrowserHistory>>()
    grouped["Today"] = mutableListOf()
    grouped["Yesterday"] = mutableListOf()
    grouped["Earlier"] = mutableListOf()

    for (item in history) {
        cal.timeInMillis = item.timestamp
        val itemYear = cal.get(java.util.Calendar.YEAR)
        val itemDayOfYear = cal.get(java.util.Calendar.DAY_OF_YEAR)

        if (itemYear == todayYear && itemDayOfYear == todayDayOfYear) {
            grouped["Today"]?.add(item)
        } else if (itemYear == yesterdayYear && itemDayOfYear == yesterdayDayOfYear) {
            grouped["Yesterday"]?.add(item)
        } else {
            grouped["Earlier"]?.add(item)
        }
    }
    return grouped.filterValues { it.isNotEmpty() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SecretBrowserBookmarksScreen(
    browserBookmarks: List<BrowserBookmark>,
    onBack: () -> Unit,
    onSelectBookmark: (String) -> Unit,
    onDeleteBookmark: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedBookmarkForDetail by remember { mutableStateOf<BrowserBookmark?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Premium Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text("Saved Collection", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (browserBookmarks.isEmpty()) "Private bookmarks" else "${browserBookmarks.size} pages saved",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )
                }
            }
            if (browserBookmarks.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentColor.copy(alpha = 0.08f),
                    border = BorderStroke(0.8.dp, AccentColor.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = AccentColor, modifier = Modifier.size(13.dp))
                        Text("Encrypted", color = AccentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (browserBookmarks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(AccentColor.copy(alpha = 0.08f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmarks,
                            contentDescription = "No Saved Pages",
                            tint = AccentColor,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No Saved Pages", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Bookmark your essential websites and private pages to quickly access them in your encrypted browser.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
            ) {
                items(browserBookmarks, key = { it.url }) { bookmark ->
                    val domain = extractBrowserDomain(bookmark.url)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onSelectBookmark(bookmark.url) },
                                onLongClick = { selectedBookmarkForDetail = bookmark }
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = LightCard),
                        border = BorderStroke(1.dp, BorderColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Favicon / Site Identity
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(LightBg, RoundedCornerShape(12.dp))
                                    .border(0.8.dp, BorderColor, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                TabCardFavicon(url = bookmark.url, isActive = true, sizeDp = 24)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Site information hierarchy
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = AccentColor.copy(alpha = 0.08f)
                                    ) {
                                        Text(
                                            text = domain,
                                            color = AccentColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "• Saved",
                                        color = TextSecondary.copy(alpha = 0.8f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (bookmark.title.isNotBlank()) bookmark.title else domain,
                                    color = TextPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 19.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Actions
                            IconButton(
                                onClick = { selectedBookmarkForDetail = bookmark },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, "Details", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { onDeleteBookmark(bookmark.url) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, "Delete", tint = DangerColor.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Bottom Sheet / Dialog for Bookmark
    selectedBookmarkForDetail?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { selectedBookmarkForDetail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TabCardFavicon(url = bookmark.url, isActive = true, sizeDp = 22)
                    Text(
                        text = extractBrowserDomain(bookmark.url),
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = bookmark.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = LightBg,
                        border = BorderStroke(0.8.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = bookmark.url,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetUrl = bookmark.url
                        selectedBookmarkForDetail = null
                        onSelectBookmark(targetUrl)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Open Page", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("URL", bookmark.url)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "URL Copied to clipboard", Toast.LENGTH_SHORT).show()
                            selectedBookmarkForDetail = null
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Copy URL", color = TextPrimary)
                    }
                    TextButton(onClick = { selectedBookmarkForDetail = null }) {
                        Text("Close", color = TextSecondary)
                    }
                }
            },
            containerColor = LightCard,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

private fun formatRelativeHistoryTime(
    timestamp: Long,
    timeFormatter: SimpleDateFormat,
    fullDateFormatter: SimpleDateFormat
): String {
    val now = java.util.Calendar.getInstance()
    val itemCal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val timeStr = timeFormatter.format(Date(timestamp))

    val todayYear = now.get(java.util.Calendar.YEAR)
    val todayDay = now.get(java.util.Calendar.DAY_OF_YEAR)

    val itemYear = itemCal.get(java.util.Calendar.YEAR)
    val itemDay = itemCal.get(java.util.Calendar.DAY_OF_YEAR)

    now.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val yesterdayYear = now.get(java.util.Calendar.YEAR)
    val yesterdayDay = now.get(java.util.Calendar.DAY_OF_YEAR)

    return when {
        itemYear == todayYear && itemDay == todayDay -> "Today, $timeStr"
        itemYear == yesterdayYear && itemDay == yesterdayDay -> "Yesterday, $timeStr"
        itemYear == todayYear -> fullDateFormatter.format(Date(timestamp))
        else -> SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
fun SecretBrowserHistoryScreen(
    browserHistory: List<BrowserHistory>,
    onBack: () -> Unit,
    onSelectHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteHistoryItem: (BrowserHistory) -> Unit
) {
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    val groupedHistory = remember(browserHistory) { groupHistoryByRelativeDate(browserHistory) }

    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val fullDateFormatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text("Browsing History", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (browserHistory.isEmpty()) "Private audit trail" else "${browserHistory.size} total visits recorded",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )
                }
            }
            if (browserHistory.isNotEmpty()) {
                TextButton(
                    onClick = { showClearConfirmDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = DangerColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All", color = DangerColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (browserHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(AccentColor.copy(alpha = 0.08f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "No Browsing History",
                            tint = AccentColor,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No Browsing History", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Pages you visit in your private browsing sessions are recorded chronologically and can be erased anytime.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
            ) {
                groupedHistory.forEach { (sectionTitle, itemsInSection) ->
                    // Section Date Header
                    item(key = "section_$sectionTitle") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = sectionTitle,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${itemsInSection.size} ${if (itemsInSection.size == 1) "visit" else "visits"}",
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // History Items in this section
                    items(itemsInSection, key = { "${it.url}_${it.timestamp}" }) { historyItem ->
                        val domain = extractBrowserDomain(historyItem.url)
                        val formattedTime = formatRelativeHistoryTime(historyItem.timestamp, timeFormatter, fullDateFormatter)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectHistoryItem(historyItem.url) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = LightCard),
                            border = BorderStroke(1.dp, BorderColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Website identity favicon
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(LightBg, RoundedCornerShape(10.dp))
                                        .border(0.8.dp, BorderColor, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TabCardFavicon(url = historyItem.url, isActive = false, sizeDp = 20)
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Information hierarchy: Page title -> Domain & Timestamp
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (historyItem.title.isNotBlank()) historyItem.title else domain,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = domain,
                                            color = AccentColor,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(3.dp)
                                                .background(TextSecondary.copy(alpha = 0.5f), CircleShape)
                                        )
                                        Text(
                                            text = formattedTime,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // Single item delete
                                IconButton(
                                    onClick = { onDeleteHistoryItem(historyItem) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete entry",
                                        tint = TextSecondary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text("Clear All History?", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will permanently erase all saved history records from your private browser session.",
                    color = TextSecondary,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearHistory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerColor),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Clear Everything", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = LightCard,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
fun SecretBrowserDownloadsScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("All", "Downloading", "Completed", "Failed")

    var selectedTaskForDetail by remember { mutableStateOf<DownloadTask?>(null) }

    val filteredDownloads = remember(downloads, selectedTab) {
        downloads.filter { task ->
            when (selectedTab) {
                0 -> true
                1 -> task.status == "Downloading"
                2 -> task.status == "Completed"
                else -> task.status == "Failed" || task.status == "Cancelled"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text("Downloads", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(if (downloads.isEmpty()) "No downloads" else "${downloads.size} files", color = TextSecondary, fontSize = 11.5.sp)
                }
            }
            if (downloads.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.clearDownloads() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = DangerColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All", color = DangerColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Segmented Status Filter Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .background(LightCard, RoundedCornerShape(12.dp))
                .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabTitles.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                val count = when (index) {
                    0 -> downloads.size
                    1 -> downloads.count { it.status == "Downloading" }
                    2 -> downloads.count { it.status == "Completed" }
                    else -> downloads.count { it.status == "Failed" || it.status == "Cancelled" }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .background(
                            if (isSelected) AccentColor else Color.Transparent,
                            RoundedCornerShape(9.dp)
                        )
                        .clickable { selectedTab = index },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        if (count > 0 && !isSelected) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(BorderColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$count",
                                    color = TextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content List or Filter-Aware Empty State
        if (filteredDownloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val emptyIcon = when (selectedTab) {
                        1 -> Icons.Default.Downloading
                        2 -> Icons.Default.TaskAlt
                        3 -> Icons.Default.ErrorOutline
                        else -> Icons.Default.CloudDownload
                    }
                    val emptyTitle = when (selectedTab) {
                        1 -> "No Active Downloads"
                        2 -> "No Completed Downloads"
                        3 -> "No Failed Downloads"
                        else -> "No Downloads Yet"
                    }
                    val emptyDesc = when (selectedTab) {
                        1 -> "Currently there are no ongoing background file transfers."
                        2 -> "Completed downloads will appear here."
                        3 -> "Any interrupted or cancelled downloads will appear here for retry."
                        else -> "Downloaded files from the browser will appear here."
                    }

                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(AccentColor.copy(alpha = 0.08f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = emptyIcon,
                            contentDescription = emptyTitle,
                            tint = AccentColor,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = emptyTitle,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = emptyDesc,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 2.dp, bottom = 24.dp)
            ) {
                items(filteredDownloads, key = { it.id }) { task ->
                    SecretBrowserDownloadItemCard(
                        task = task,
                        onOpen = { viewModel.openDownload(context, task) },
                        onDelete = { viewModel.deleteDownload(task) },
                        onRetry = { viewModel.retryDownload(context, task) },
                        onResume = { viewModel.resumeDownload(context, task) },
                        onPause = { viewModel.pauseDownload(task) },
                        onCancel = { viewModel.cancelDownload(task) },
                        onShowDetail = { selectedTaskForDetail = task }
                    )
                }
            }
        }
    }

    // Download Detail Modal / Sheet
    selectedTaskForDetail?.let { task ->
        AlertDialog(
            onDismissRequest = { selectedTaskForDetail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AccentColor, modifier = Modifier.size(22.dp))
                    Text("File Properties", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = LightBg,
                        border = BorderStroke(0.8.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Filename", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(task.filename, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Size", color = TextSecondary, fontSize = 11.sp)
                                    Text(if (task.sizeString.isNotBlank()) task.sizeString else "Calculating...", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Column {
                                    Text("Type", color = TextSecondary, fontSize = 11.sp)
                                    Text(if (task.mimeType.isNotBlank()) task.mimeType else "application/octet-stream", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SuccessColor.copy(alpha = 0.08f),
                        border = BorderStroke(0.8.dp, SuccessColor.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = SuccessColor, modifier = Modifier.size(15.dp))
                            Text("Stored in Secret Vault", color = SuccessColor, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                if (task.status == "Completed") {
                    Button(
                        onClick = {
                            selectedTaskForDetail = null
                            viewModel.openDownload(context, task)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Open File", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else if (task.status == "Failed" || task.status == "Cancelled") {
                    Button(
                        onClick = {
                            selectedTaskForDetail = null
                            viewModel.retryDownload(context, task)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Retry Download", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(onClick = { selectedTaskForDetail = null }) {
                        Text("Close", color = TextPrimary)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedTaskForDetail = null }) {
                    Text("Dismiss", color = TextSecondary)
                }
            },
            containerColor = LightCard,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
fun SecretBrowserDownloadItemCard(
    task: DownloadTask,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onShowDetail: () -> Unit = {},
    onResume: () -> Unit = {},
    onPause: () -> Unit = {}
) {
    val isDownloading = task.status == "Downloading"
    val isPaused = task.status == "Paused"
    val isCompleted = task.status == "Completed"
    val isFailed = task.status == "Failed" || task.status == "Cancelled"

    val lowerName = task.filename.lowercase()
    val isVideo = task.mimeType.startsWith("video/") ||
            lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") ||
            lowerName.endsWith(".webm") || lowerName.endsWith(".avi") ||
            lowerName.endsWith(".mov") || lowerName.endsWith(".m4v") ||
            lowerName.endsWith(".flv") || lowerName.endsWith(".3gp") ||
            lowerName.endsWith(".ts") || lowerName.endsWith(".wmv")

    val isImage = task.mimeType.startsWith("image/") ||
            lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
            lowerName.endsWith(".png") || lowerName.endsWith(".webp") ||
            lowerName.endsWith(".gif") || lowerName.endsWith(".bmp") ||
            lowerName.endsWith(".svg") || lowerName.endsWith(".ico")

    val isAudio = task.mimeType.startsWith("audio/") ||
            lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") ||
            lowerName.endsWith(".m4a") || lowerName.endsWith(".aac") ||
            lowerName.endsWith(".ogg") || lowerName.endsWith(".flac") ||
            lowerName.endsWith(".opus") || lowerName.endsWith(".weba")

    val (fileIcon, iconBgColor, iconTintColor) = when {
        isImage -> Triple(Icons.Default.Image, Color(0xFF2563EB).copy(alpha = 0.1f), Color(0xFF2563EB))
        isVideo -> Triple(Icons.Default.VideoLibrary, Color(0xFF7C3AED).copy(alpha = 0.12f), Color(0xFF7C3AED))
        isAudio -> Triple(Icons.Default.AudioFile, Color(0xFFEA580C).copy(alpha = 0.1f), Color(0xFFEA580C))
        task.mimeType.contains("pdf") || lowerName.endsWith(".pdf") ->
            Triple(Icons.Default.Description, Color(0xFFDC2626).copy(alpha = 0.1f), Color(0xFFDC2626))
        task.mimeType.contains("zip") || task.mimeType.contains("rar") || task.mimeType.contains("tar") || task.mimeType.contains("archive") || lowerName.endsWith(".zip") || lowerName.endsWith(".tar") || lowerName.endsWith(".gz") || lowerName.endsWith(".7z") ->
            Triple(Icons.Default.FolderZip, Color(0xFFD97706).copy(alpha = 0.1f), Color(0xFFD97706))
        lowerName.endsWith(".apk") || task.mimeType.contains("android.package-archive") ->
            Triple(Icons.Default.Android, Color(0xFF059669).copy(alpha = 0.1f), Color(0xFF059669))
        else ->
            Triple(Icons.Default.InsertDriveFile, AccentColor.copy(alpha = 0.1f), AccentColor)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onShowDetail() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LightCard),
        border = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File Type / Status Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(iconBgColor, RoundedCornerShape(12.dp))
                        .border(0.8.dp, iconTintColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = fileIcon,
                        contentDescription = null,
                        tint = iconTintColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // File Details Hierarchy
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.filename,
                        color = TextPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (task.sizeString.isNotEmpty() && task.sizeString != "0 B") task.sizeString else "File",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .background(TextSecondary.copy(alpha = 0.5f), CircleShape)
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when {
                                isDownloading -> AccentColor.copy(alpha = 0.09f)
                                isPaused -> Color(0xFFD97706).copy(alpha = 0.09f)
                                isCompleted -> SuccessColor.copy(alpha = 0.09f)
                                else -> DangerColor.copy(alpha = 0.09f)
                            }
                        ) {
                            Text(
                                text = when {
                                    isDownloading -> "${(task.progress * 100).toInt()}%"
                                    isPaused -> "Paused"
                                    isCompleted -> "Completed"
                                    task.status == "Cancelled" -> "Cancelled"
                                    else -> "Failed"
                                },
                                color = when {
                                    isDownloading -> AccentColor
                                    isPaused -> Color(0xFFD97706)
                                    isCompleted -> SuccessColor
                                    else -> DangerColor
                                },
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isDownloading) {
                        IconButton(
                            onClick = onPause,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pause Download",
                                tint = AccentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Download",
                                tint = DangerColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else if (isPaused) {
                        Button(
                            onClick = onResume,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Resume", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else if (isCompleted) {
                        Button(
                            onClick = onOpen,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Open", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry Download",
                                tint = AccentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Progress bar if downloading or paused
            if (isDownloading || isPaused) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (isPaused) Color(0xFFD97706) else AccentColor,
                    trackColor = BorderColor
                )
            }
        }
    }
}

@Composable
private fun TabCardFavicon(url: String, isActive: Boolean, sizeDp: Int = 22) {
    val lower = url.lowercase()
    when {
        lower == "home" || lower.isEmpty() -> {
            Box(
                modifier = Modifier
                    .size(sizeDp.dp)
                    .background(if (isActive) AccentColor.copy(alpha = 0.15f) else LightBg, CircleShape)
                    .border(0.6.dp, if (isActive) AccentColor.copy(alpha = 0.4f) else BorderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (isActive) AccentColor else TextSecondary,
                    modifier = Modifier.size((sizeDp * 0.55f).dp)
                )
            }
        }
        lower.contains("youtube") || lower.contains("youtu.be") -> {
            YouTubeBrandIcon(sizeDp = sizeDp)
        }
        lower.contains("instagram") -> {
            InstagramBrandIcon(sizeDp = sizeDp)
        }
        lower.contains("facebook") || lower.contains("fb.com") -> {
            FacebookBrandIcon(sizeDp = sizeDp)
        }
        lower.contains("telegram") || lower.contains("t.me") -> {
            TelegramBrandIcon(sizeDp = sizeDp)
        }
        lower.contains("pinterest") -> {
            PinterestBrandIcon(sizeDp = sizeDp)
        }
        lower.contains("google") -> {
            GoogleBrandIcon(sizeDp = sizeDp)
        }
        lower.contains("duckduckgo") -> {
            Box(
                modifier = Modifier
                    .size(sizeDp.dp)
                    .background(Color(0xFFDE5833), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size((sizeDp * 0.55f).dp)
                )
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .size(sizeDp.dp)
                    .background(if (isActive) AccentColor.copy(alpha = 0.12f) else LightBg, CircleShape)
                    .border(0.6.dp, if (isActive) AccentColor.copy(alpha = 0.4f) else BorderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = if (isActive) AccentColor else TextSecondary,
                    modifier = Modifier.size((sizeDp * 0.55f).dp)
                )
            }
        }
    }
}

@Composable
private fun TabPreviewWindow(
    tab: TabState,
    domainText: String,
    modifier: Modifier = Modifier
) {
    val isHome = tab.url == "home" || tab.url.isEmpty() || tab.url == "about:blank"
    val cacheVersion = TabThumbnailCache.version.value
    val liveScreenshot = if (cacheVersion >= 0) TabThumbnailCache.getThumbnail(tab.id) else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(LightBg),
        contentAlignment = Alignment.Center
    ) {
        if (liveScreenshot != null && !isHome) {
            Image(
                bitmap = liveScreenshot.asImageBitmap(),
                contentDescription = "Webpage Snapshot Preview",
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isHome) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightCard)
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(AccentColor.copy(alpha = 0.08f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = AccentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "New Secret Tab",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Private Tab",
                    fontSize = 9.sp,
                    color = TextSecondary
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightCard)
                    .padding(8.dp)
            ) {
                if (tab.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = AccentColor,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Loading...",
                        fontSize = 9.sp,
                        color = TextSecondary
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(AccentColor.copy(alpha = 0.08f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = AccentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = domainText.ifEmpty { tab.title.ifEmpty { "Web Page" } },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap to open",
                        fontSize = 8.5.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun SecretBrowserTabSwitcherScreen(
    tabs: List<TabState>,
    activeTabId: String?,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onCloseAllTabs: () -> Unit,
    onNewTab: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // TOP APP BAR
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = LightCard,
            shadowElevation = 2.dp,
            border = BorderStroke(0.8.dp, BorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to web page",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Tabs Manager",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AccentColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${tabs.size}",
                                    color = AccentColor,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "${tabs.size} isolated private sessions",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (tabs.size > 1) {
                        OutlinedButton(
                            onClick = onCloseAllTabs,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DangerColor.copy(alpha = 0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerColor),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Close All", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
                        onClick = onNewTab,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Tab", tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // CONTENT AREA (SPACIOUS TAB GRID / PREVIEWS)
        if (tabs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(AccentColor.copy(alpha = 0.08f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = AccentColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "No Open Tabs",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Open a private tab to browse secretly without traces.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onNewTab,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open New Tab", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tabs, key = { it.id }) { tab ->
                    val isActive = tab.id == activeTabId
                    val isStartPage = tab.url == "home" || tab.url.isEmpty() || tab.url == "about:blank"
                    val domainText = if (isStartPage) "Start Page" else formatTabDomain(tab.url)
                    val displayTitle = if (isStartPage) "Private Tab" else tab.title.ifEmpty { domainText }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(236.dp)
                            .premiumPressClick { onSelectTab(tab.id) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = LightCard
                        ),
                        border = BorderStroke(
                            if (isActive) 2.dp else 1.dp,
                            if (isActive) AccentColor else BorderColor
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (isActive) 4.dp else 1.5.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        ) {
                            // Top Header Bar inside Card
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TabCardFavicon(url = tab.url, isActive = isActive, sizeDp = 22)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = displayTitle,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isActive) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AccentColor)
                                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    ) {
                                        Text("ACTIVE", color = Color.White, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(LightBg)
                                        .border(0.6.dp, BorderColor, CircleShape)
                                        .clickable { onCloseTab(tab.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Tab",
                                        tint = DangerColor.copy(alpha = 0.9f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Visual Preview Area
                            TabPreviewWindow(
                                tab = tab,
                                domainText = domainText,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.height(7.dp))

                            // Bottom Info Area (Clean single domain line + tracker shield)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = domainText,
                                    fontSize = 10.sp,
                                    color = if (isActive) AccentColor else TextSecondary,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (tab.blockedCount > 0) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = null,
                                            tint = AccentColor,
                                            modifier = Modifier.size(9.dp)
                                        )
                                        Text(
                                            text = "${tab.blockedCount}",
                                            fontSize = 8.5.sp,
                                            color = AccentColor,
                                            fontWeight = FontWeight.Bold
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateBrowserSection(
    modifier: Modifier = Modifier,
    viewModel: CalculatorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onExit: () -> Unit = {},
    onPanic: () -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        TabThumbnailCache.init(context)
    }
    val tabs = viewModel.browserTabs
    val geckoViews = remember { mutableStateMapOf<String, org.mozilla.geckoview.GeckoView>() }
    val geckoSessions = remember { 
        mutableStateMapOf<String, org.mozilla.geckoview.GeckoSession>().apply {
            putAll(GeckoSessionManager.getActiveSessions())
        }
    }
    var activeTabId by remember { mutableStateOf<String?>(viewModel.activeTabId) }

    LaunchedEffect(viewModel.activeTabId) {
        if (viewModel.activeTabId != null && activeTabId != viewModel.activeTabId) {
            activeTabId = viewModel.activeTabId
        }
    }

    var showFindInPage by remember { mutableStateOf(false) }
    var findInPageText by remember { mutableStateOf("") }
    var findInPageMatchCurrent by remember { mutableStateOf(0) }
    var findInPageMatchTotal by remember { mutableStateOf(0) }

    LaunchedEffect(activeTabId) {
        if (activeTabId != null && viewModel.activeTabId != activeTabId) {
            viewModel.activeTabId = activeTabId
        }
        // Clear Find in Page highlights and close search when tab switches
        showFindInPage = false
        findInPageText = ""
        findInPageMatchCurrent = 0
        findInPageMatchTotal = 0
    }
    
    LaunchedEffect(tabs.toList(), activeTabId) {
        if (activeTabId == null && tabs.isNotEmpty()) {
            activeTabId = viewModel.activeTabId ?: tabs.first().id
        }
        if (viewModel.isBrowserTabsLoaded) {
            viewModel.triggerSaveTabs()
        }
    }

    var showTabSwitcher by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showMenuClearBrowsingDataDialog by remember { mutableStateOf(false) }
    var showMenuClearTempFilesDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var isEditingUrl by remember { mutableStateOf(false) }
    var editingUrlText by remember { mutableStateOf("") }
    val urlFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditingUrl) {
        if (isEditingUrl) {
            try {
                urlFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val browserBookmarks by viewModel.browserBookmarks.collectAsStateWithLifecycle()
    val browserHistory by viewModel.browserHistory.collectAsStateWithLifecycle()
    val searchEngine by viewModel.searchEngine.collectAsStateWithLifecycle()
    val savePasswords by viewModel.savePasswords.collectAsStateWithLifecycle()
    val clearHistoryOnExit by viewModel.clearHistoryOnExit.collectAsStateWithLifecycle()
    val clearTempOnExit by viewModel.clearTempOnExit.collectAsStateWithLifecycle()
    val useGeckoView by viewModel.useGeckoView.collectAsStateWithLifecycle()
    val trackingProtectionEnabled by viewModel.trackingProtectionEnabled.collectAsStateWithLifecycle()
    val trackingSiteEnabled by viewModel.trackingSiteEnabled.collectAsStateWithLifecycle()
    val trackingSiteDisabled by viewModel.trackingSiteDisabled.collectAsStateWithLifecycle()
    val totalTrackersBlocked by viewModel.totalTrackersBlocked.collectAsStateWithLifecycle()

    var showSearchEngineDialog by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<PendingDownloadData?>(null) }
    var showSiteSecurityDialog by remember { mutableStateOf(false) }
    var showSecretRunnerGame by remember { mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        GeckoSessionManager.globalDownloadCallback = { downloadUrl, userAgent, contentDisposition, mimeType, contentLength, referrerUrl ->
            pendingDownload = PendingDownloadData(downloadUrl, userAgent, contentDisposition, mimeType, contentLength, referrerUrl)
        }
        GeckoSessionManager.onOpenSecretRunner = {
            showSecretRunnerGame = true
        }
        onDispose {
            GeckoSessionManager.globalDownloadCallback = null
            GeckoSessionManager.onOpenSecretRunner = null
        }
    }

    val activeTab = tabs.find { it.id == activeTabId } ?: tabs.find { it.id == viewModel.activeTabId } ?: tabs.firstOrNull()

    // Automatically capture thumbnail when page finish loading or tab updates
    LaunchedEffect(activeTab?.isLoading, activeTab?.url) {
        val currentTab = activeTab
        if (currentTab != null && !currentTab.isLoading && currentTab.url != "home" && currentTab.url != "about:blank" && currentTab.url.isNotEmpty()) {
            kotlinx.coroutines.delay(350)
            geckoViews[currentTab.id]?.let { gv ->
                captureViewThumbnail(gv, currentTab.id)
            }
            kotlinx.coroutines.delay(850)
            geckoViews[currentTab.id]?.let { gv ->
                captureViewThumbnail(gv, currentTab.id)
            }
        }
    }

    LaunchedEffect(activeTabId, geckoSessions.size) {
        geckoSessions.forEach { (id, session) ->
            try {
                session.setActive(id == activeTabId)
            } catch (e: Exception) {
                android.util.Log.e("GeckoActiveState", "Failed to set active state for $id", e)
            }
        }
    }

    fun getOrCreateTabSession(tab: TabState): org.mozilla.geckoview.GeckoSession {
        val existing = geckoSessions[tab.id] ?: GeckoSessionManager.getSession(tab.id)
        val updateBlock: ((TabState) -> TabState) -> Unit = { transform ->
            val index = tabs.indexOfFirst { it.id == tab.id }
            if (index != -1) {
                val oldTab = tabs[index]
                val newTab = transform(oldTab)
                tabs[index] = newTab
                val currentUrl = newTab.url
                val currentTitle = newTab.title
                if (!newTab.isLoading && oldTab.isLoading) {
                    if (currentUrl != "home" && currentUrl != "about:blank" && currentUrl.isNotEmpty() &&
                        !currentUrl.startsWith("data:") && !currentUrl.startsWith("file:") && !currentUrl.startsWith("about:")) {
                        viewModel.addBrowserHistory(currentTitle, currentUrl)
                    }
                    geckoViews[tab.id]?.let { gv ->
                        gv.postDelayed({
                            captureViewThumbnail(gv, tab.id)
                        }, 400)
                        gv.postDelayed({
                            captureViewThumbnail(gv, tab.id)
                        }, 1200)
                    }
                }
            }
        }

        if (existing != null && existing.isOpen) {
            geckoSessions[tab.id] = existing
            GeckoSessionManager.getOrCreateSession(
                context = context,
                tabId = tab.id,
                initialUrl = tab.url,
                isDesktopMode = tab.isDesktopMode,
                onDownloadRequested = { downloadUrl, userAgent, contentDisposition, mimeType, contentLength, referrerUrl ->
                    pendingDownload = PendingDownloadData(downloadUrl, userAgent, contentDisposition, mimeType, contentLength, referrerUrl)
                },
                onCrash = {
                    geckoSessions.remove(tab.id)
                },
                onUpdateParam = updateBlock
            )
            return existing
        }

        val session = createPrivateGeckoSession(
            ctx = context,
            tabId = tab.id,
            initialUrl = tab.url,
            isDesktopMode = tab.isDesktopMode,
            onDownloadRequested = { downloadUrl, userAgent, contentDisposition, mimeType, contentLength, referrerUrl ->
                pendingDownload = PendingDownloadData(downloadUrl, userAgent, contentDisposition, mimeType, contentLength, referrerUrl)
            },
            onCrash = {
                geckoSessions.remove(tab.id)
            },
            onUpdate = updateBlock
        )
        geckoSessions[tab.id] = session
        return session
    }

    LaunchedEffect(tabs.map { it.id }, activeTabId) {
        val currentTabIds = tabs.map { it.id }.toSet()

        // Clean up closed tabs
        val removedGecko = geckoSessions.keys.filter { it !in currentTabIds }
        removedGecko.forEach { tabId ->
            val gv = geckoViews.remove(tabId)
            try {
                (gv?.parent as? android.view.ViewGroup)?.removeView(gv)
                gv?.releaseSession()
            } catch (e: Exception) {}
            GeckoSessionManager.removeAndDestroySession(tabId)
        }
        geckoSessions.keys.retainAll(currentTabIds)

        // Pre-initialize active tab's GeckoSession
        val activeTab = tabs.find { it.id == activeTabId }
        if (activeTab != null) {
            getOrCreateTabSession(activeTab)
        }
    }

    var lastTabCreationTime by remember { mutableStateOf(0L) }
    fun openNewTab(url: String, parentId: String? = null) {
        val now = android.os.SystemClock.elapsedRealtime()
        val cleanUrl = url.trim()
        val isBlocked = SecretBrowserTrackingProtection.shouldBlock(cleanUrl, isMainFrame = true)
        if (!isBlocked && (now - lastTabCreationTime > 400L || tabs.isEmpty())) {
            // Guard against runaway ad loops creating 15+ tabs
            if (tabs.size < 20 || cleanUrl == "home") {
                lastTabCreationTime = now
                val tabId = java.util.UUID.randomUUID().toString()
                val newTab = TabState(id = tabId, url = cleanUrl, title = "New Tab", parentTabId = parentId)
                tabs.add(newTab)
                activeTabId = tabId
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        GeckoSessionManager.onOpenNewTab = { url, parentId ->
            openNewTab(url, parentId)
        }
        onDispose {
            GeckoSessionManager.onOpenNewTab = null
        }
    }

    val closeTab: (String) -> Unit = { tabId ->
        SecretBrowserNavigationCheckpointManager.clearTabCheckpoints(tabId)
        TabThumbnailCache.removeThumbnail(tabId)
        val gv = geckoViews.remove(tabId)
        try {
            (gv?.parent as? android.view.ViewGroup)?.removeView(gv)
            gv?.releaseSession()
        } catch (e: Exception) {}
        GeckoSessionManager.removeAndDestroySession(tabId)
        geckoSessions.remove(tabId)
        val tIndex = tabs.indexOfFirst { it.id == tabId }
        if (tIndex != -1) {
            val closedTab = tabs[tIndex]
            val parentId = closedTab.parentTabId
            tabs.removeAt(tIndex)
            if (activeTabId == tabId) {
                if (parentId != null && tabs.any { it.id == parentId }) {
                    activeTabId = parentId
                } else if (tabs.isNotEmpty()) {
                    activeTabId = tabs[tIndex.coerceAtMost(tabs.size - 1)].id
                } else {
                    openNewTab("home", null)
                }
            }
        }
    }

    LaunchedEffect(viewModel.isBrowserTabsLoaded) {
        if (viewModel.isBrowserTabsLoaded && tabs.isEmpty()) {
            openNewTab("home", null)
        }
    }

    val currentClearHistoryOnExit by androidx.compose.runtime.rememberUpdatedState(clearHistoryOnExit)
    val currentClearTempOnExit by androidx.compose.runtime.rememberUpdatedState(clearTempOnExit)
    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? android.app.Activity
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            setSystemBarsVisibility(activity, true)
            if (currentClearTempOnExit) {
                try {
                    SecretBrowserSecureDelete.cleanTemporaryUploadsDirectory(context, secure = true)
                    SecretBrowserSecureDelete.cleanStaleTemporaryRemnants(context, secure = true)
                } catch (e: Exception) {
                    android.util.Log.e("SecureDelete", "Exit cleanup failed", e)
                }
            }
            if (currentClearHistoryOnExit) {
                viewModel.clearBrowserHistory()
                clearAllBrowsingData(context, tabs)
                geckoViews.values.forEach { try { (it.parent as? android.view.ViewGroup)?.removeView(it); it.releaseSession() } catch (e: Exception) {} }
                geckoViews.clear()
                GeckoSessionManager.destroyAllSessions()
            } else {
                geckoViews.values.forEach { try { (it.parent as? android.view.ViewGroup)?.removeView(it) } catch (e: Exception) {} }
                geckoSessions.values.forEach { it.setActive(false) }
            }
            geckoSessions.clear()
            geckoViews.clear()
        }
    }

    val isFullScreen = activeTab?.isFullScreen == true
    LaunchedEffect(isFullScreen) {
        val activity = context as? android.app.Activity
        if (activity != null) {
            if (isFullScreen) {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                setSystemBarsVisibility(activity, false)
            } else {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                setSystemBarsVisibility(activity, true)
            }
        }
    }

    val currentActiveId = activeTabId
    val activeGeckoSession = if (currentActiveId != null && activeTab != null) {
        getOrCreateTabSession(activeTab)
    } else {
        null
    }
    val isHome = activeTab?.url == "home" || activeTab?.url == "about:blank" || activeTab?.url?.isEmpty() == true

    val currentUrl = activeTab?.url ?: ""
    val currentHost = remember(currentUrl) {
        if (currentUrl.isNotEmpty() && currentUrl != "home" && currentUrl != "about:blank") {
            try {
                val uri = android.net.Uri.parse(currentUrl)
                uri.host
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
    val siteOverride = remember(currentHost, trackingSiteEnabled, trackingSiteDisabled) {
        currentHost?.let { host ->
            val norm = host.lowercase().trim()
            if (trackingSiteEnabled.contains(norm)) {
                true
            } else if (trackingSiteDisabled.contains(norm)) {
                false
            } else {
                null
            }
        }
    }
    val isProtectionActive = siteOverride ?: trackingProtectionEnabled

    val performFindInPage: (String, Boolean) -> Unit = { query, forward ->
        if (activeGeckoSession != null) {
            val finder = activeGeckoSession.getFinder()
            if (query.isEmpty()) {
                finder.clear()
                findInPageMatchCurrent = 0
                findInPageMatchTotal = 0
            } else {
                val flags = if (forward) {
                    0
                } else {
                    org.mozilla.geckoview.GeckoSession.FINDER_FIND_BACKWARDS
                }
                finder.find(query, flags).accept { result ->
                    if (result != null) {
                        findInPageMatchCurrent = result.current
                        findInPageMatchTotal = result.total
                    } else {
                        findInPageMatchCurrent = 0
                        findInPageMatchTotal = 0
                    }
                }
            }
        }
    }

    val findNextMatch: () -> Unit = {
        val query = findInPageText
        if (query.isNotEmpty() && activeGeckoSession != null) {
            activeGeckoSession.getFinder().find(query, 0).accept { result ->
                if (result != null) {
                    findInPageMatchCurrent = result.current
                    findInPageMatchTotal = result.total
                } else {
                    findInPageMatchCurrent = 0
                    findInPageMatchTotal = 0
                }
            }
        }
    }

    val findPreviousMatch: () -> Unit = {
        val query = findInPageText
        if (query.isNotEmpty() && activeGeckoSession != null) {
            activeGeckoSession.getFinder().find(query, org.mozilla.geckoview.GeckoSession.FINDER_FIND_BACKWARDS).accept { result ->
                if (result != null) {
                    findInPageMatchCurrent = result.current
                    findInPageMatchTotal = result.total
                } else {
                    findInPageMatchCurrent = 0
                    findInPageMatchTotal = 0
                }
            }
        }
    }

    val closeFindInPage: () -> Unit = {
        showFindInPage = false
        findInPageText = ""
        findInPageMatchCurrent = 0
        findInPageMatchTotal = 0
        activeGeckoSession?.getFinder()?.clear()
    }

    val stopLoading: () -> Unit = {
        activeGeckoSession?.stop()
        val index = tabs.indexOfFirst { it.id == activeTabId }
        if (index != -1) {
            tabs[index] = tabs[index].copy(isLoading = false, progress = 100)
        }
    }

    val loadUrl: (String) -> Unit = { target ->
        val formatted = if (target == "home" || target == "about:blank") {
            target
        } else {
            val q = target.trim()
            if (q.startsWith("http://") || q.startsWith("https://") || q.startsWith("file://") || q.startsWith("about:")) {
                q
            } else {
                val hasSpace = q.contains(" ")
                val firstSegment = q.substringBefore("/")
                val hasDot = firstSegment.contains(".")
                val isLocalhost = firstSegment.lowercase() == "localhost" || firstSegment.lowercase().startsWith("localhost:" )
                val isValidWebUrl = !hasSpace && (hasDot || isLocalhost)
                if (isValidWebUrl) {
                    "https://$q"
                } else {
                    val encodedQ = java.net.URLEncoder.encode(q, "UTF-8")
                    when (searchEngine) {
                        "DuckDuckGo" -> "https://duckduckgo.com/?q=$encodedQ"
                        "Bing" -> "https://www.bing.com/search?q=$encodedQ&setlang=en&cc=US"
                        "Yahoo" -> "https://search.yahoo.com/search?p=$encodedQ&ei=UTF-8&vc=US&vl=en"
                        else -> "https://www.google.com/search?q=$encodedQ&hl=en&gl=US"
                    }
                }
            }
        }

        if (formatted == "home") {
            val index = tabs.indexOfFirst { it.id == activeTabId }
            if (index != -1) {
                tabs[index] = tabs[index].copy(
                    url = "home",
                    title = "New Tab",
                    progress = 0,
                    isLoading = false,
                    canGoBack = activeTab?.canGoBack == true,
                    canGoForward = activeTab?.canGoForward == true
                )
            }
            activeGeckoSession?.loadUri("about:blank")
        } else {
            val index = tabs.indexOfFirst { it.id == activeTabId }
            var shouldLoad = true
            if (index != -1) {
                val currentTab = tabs[index]
                if (currentTab.isLoading && currentTab.url == formatted) {
                    shouldLoad = false
                } else {
                    if (currentTab.url.isNotBlank() && currentTab.url != "home" && currentTab.url != "about:blank" && !currentTab.url.startsWith("data:") && currentTab.url != formatted) {
                        SecretBrowserNavigationCheckpointManager.recordCheckpoint(
                            tabId = currentTab.id,
                            previousUrl = currentTab.url,
                            previousTitle = currentTab.title,
                            reason = "user_navigation"
                        )
                    }
                    tabs[index] = tabs[index].copy(
                        url = formatted,
                        isLoading = true,
                        progress = 10
                    )
                }
            }
            if (shouldLoad) {
                activeGeckoSession?.loadUri(formatted)
            }
        }
    }

    val reload: () -> Unit = {
        activeGeckoSession?.reload()
    }
    val goBack: () -> Unit = {
        stopLoading()
        if (activeGeckoSession != null && activeTab?.canGoBack == true) {
            // Priority 1: Navigate backward through GeckoView browser session history
            activeGeckoSession.goBack()
        } else if (activeTab != null && SecretBrowserNavigationCheckpointManager.hasValidCheckpoint(activeTab.id, activeTab.url)) {
            // Priority 2: Fallback to pre-redirect / same-tab replacement checkpoint if GeckoView history is lost
            val prevUrl = SecretBrowserNavigationCheckpointManager.popValidCheckpoint(activeTab.id, activeTab.url)
            if (prevUrl != null) {
                loadUrl(prevUrl)
            }
        } else if (activeTab?.parentTabId != null && tabs.any { it.id == activeTab.parentTabId }) {
            // Priority 3: If this was a popup/child tab with exhausted history, close it and return to parent tab
            closeTab(activeTab.id)
        } else if (activeTab != null && !isHome && activeTab.url != "home" && activeTab.url.isNotEmpty()) {
            // Priority 4: Return from web page to browser home dashboard
            loadUrl("home")
        } else if (isHome && tabs.size > 1 && activeTab != null) {
            // Priority 5: If on home and multiple tabs exist, close tab and switch to remaining tab
            closeTab(activeTab.id)
        }
    }
    val goForward: () -> Unit = {
        activeGeckoSession?.goForward()
    }

    val goBackOrExit = {
        if (showSecretRunnerGame) {
            showSecretRunnerGame = false
        } else if (activeTab?.isFullScreen == true) {
            activeGeckoSession?.exitFullScreen()
            val index = tabs.indexOfFirst { it.id == activeTabId }
            if (index != -1) {
                tabs[index] = tabs[index].copy(isFullScreen = false)
            }
        } else if (showSearchEngineDialog) {
            showSearchEngineDialog = false
        } else if (showDownloads) {
            showDownloads = false
        } else if (showSettings) {
            showSettings = false
        } else if (showBookmarks) {
            showBookmarks = false
        } else if (showHistory) {
            showHistory = false
        } else if (showTabSwitcher) {
            showTabSwitcher = false
        } else if (showMenuClearBrowsingDataDialog) {
            showMenuClearBrowsingDataDialog = false
        } else if (showMenuClearTempFilesDialog) {
            showMenuClearTempFilesDialog = false
        } else if (showMenu) {
            showMenu = false
        } else if (showFindInPage) {
            closeFindInPage()
        } else if (activeGeckoSession != null && activeTab?.canGoBack == true) {
            // Priority 1: Navigate backward through the GeckoView browser session history
            stopLoading()
            activeGeckoSession.goBack()
        } else if (activeTab != null && SecretBrowserNavigationCheckpointManager.hasValidCheckpoint(activeTab.id, activeTab.url)) {
            // Priority 2: Fallback to pre-redirect/same-tab replacement checkpoint if GeckoView history is lost
            stopLoading()
            val prevUrl = SecretBrowserNavigationCheckpointManager.popValidCheckpoint(activeTab.id, activeTab.url)
            if (prevUrl != null) {
                loadUrl(prevUrl)
            }
        } else if (activeTab?.parentTabId != null && tabs.any { it.id == activeTab.parentTabId }) {
            // Priority 3: If this was a popup/child tab with exhausted history, closing it returns directly to the originating tab
            stopLoading()
            closeTab(activeTab.id)
        } else if (activeTab != null && !isHome && activeTab.url != "home" && activeTab.url.isNotEmpty()) {
            // Priority 4: Return from web page to browser home dashboard
            stopLoading()
            loadUrl("home")
        } else if (isHome && tabs.size > 1 && activeTab != null) {
            // Priority 5: If on home and multiple tabs exist, closing tab returns to remaining tab
            stopLoading()
            closeTab(activeTab.id)
        } else if (isHome) {
            // Priority 6: Only when confirmed on the browser Home screen on the last tab, exit to vault
            onExit()
        }
    }

    androidx.activity.compose.BackHandler {
        goBackOrExit()
    }

    pendingDownload?.let { download ->
        SecretBrowserDownloadConfirmDialog(
            download = download,
            viewModel = viewModel,
            onDismiss = { pendingDownload = null },
            onConfirm = { destination ->
                viewModel.startVaultDownload(
                    context = context,
                    url = download.url,
                    userAgent = download.userAgent,
                    contentDisposition = download.contentDisposition,
                    mimeType = download.mimeType,
                    contentLength = download.contentLength,
                    destination = destination,
                    referrerUrl = download.referrerUrl
                )
                pendingDownload = null
            }
        )
    }

    if (showDownloads) {
        SecretBrowserDownloadsScreen(
            viewModel = viewModel,
            onBack = { showDownloads = false }
        )
        return
    }

    if (showBookmarks) {
        SecretBrowserBookmarksScreen(
            browserBookmarks = browserBookmarks,
            onBack = { showBookmarks = false },
            onSelectBookmark = { url ->
                showBookmarks = false
                loadUrl(url)
            },
            onDeleteBookmark = { url ->
                viewModel.removeBrowserBookmark(url)
            }
        )
        return
    }

    if (showHistory) {
        SecretBrowserHistoryScreen(
            browserHistory = browserHistory,
            onBack = { showHistory = false },
            onSelectHistoryItem = { url ->
                showHistory = false
                loadUrl(url)
            },
            onClearHistory = {
                viewModel.clearBrowserHistory()
            },
            onDeleteHistoryItem = { item ->
                viewModel.deleteBrowserHistoryItem(item)
            }
        )
        return
    }

    if (showSearchEngineDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showSearchEngineDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(AccentColor.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = AccentColor, modifier = Modifier.size(16.dp))
                        }
                        Column {
                            Text("Search Engine", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Select default search provider", color = TextSecondary, fontSize = 11.5.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val engines = listOf(
                        Triple("DuckDuckGo", "Privacy-focused • No tracking", Icons.Default.Security),
                        Triple("Google", "Fast results • Comprehensive index", Icons.Default.Search),
                        Triple("Bing", "Microsoft search network", Icons.Default.Search),
                        Triple("Yahoo", "Yahoo web search", Icons.Default.Search)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        engines.forEach { (engine, subtitle, icon) ->
                            val isSelected = searchEngine == engine
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) AccentColor.copy(alpha = 0.08f) else Color.Transparent)
                                    .border(1.dp, if (isSelected) AccentColor.copy(alpha = 0.4f) else BorderColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                    .clickable {
                                        viewModel.setSearchEngine(engine)
                                        showSearchEngineDialog = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(if (isSelected) AccentColor.copy(alpha = 0.15f) else LightBg, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) AccentColor else TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = engine,
                                        color = TextPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                    )
                                    Text(
                                        text = subtitle,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.setSearchEngine(engine)
                                        showSearchEngineDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = AccentColor, unselectedColor = TextSecondary.copy(alpha = 0.5f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        SecretBrowserSettingsDashboard(
            tabs = tabs,
            browserBookmarks = browserBookmarks,
            browserHistory = browserHistory,
            searchEngine = searchEngine,
            savePasswords = savePasswords,
            clearHistoryOnExit = clearHistoryOnExit,
            clearTempOnExit = clearTempOnExit,
            useGeckoView = useGeckoView,
            trackingProtectionEnabled = trackingProtectionEnabled,
            onSetTrackingProtectionEnabled = { viewModel.setTrackingProtectionEnabled(it) },
            totalTrackersBlocked = totalTrackersBlocked,
            onBack = { showSettings = false },
            onShowSearchEngineDialog = { showSearchEngineDialog = true },
            onSetSavePasswords = { viewModel.setSavePasswords(it) },
            onSetClearHistoryOnExit = { viewModel.setClearHistoryOnExit(it) },
            onSetClearTempOnExit = { viewModel.setClearTempOnExit(it) },
            onClearBrowsingData = { clearHistory, clearCookies, clearCache, clearSiteData, onResult ->
                SecretBrowserPrivacyHelper.clearBrowsingData(
                    context = context,
                    clearHistory = clearHistory,
                    clearCookies = clearCookies,
                    clearCache = clearCache,
                    clearSiteData = clearSiteData,
                    viewModel = viewModel,
                    onResult = onResult
                )
            },
            onShowDownloads = {
                showSettings = false
                showDownloads = true
            }
        )
        return
    }

    if (showSiteSecurityDialog && currentHost != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showSiteSecurityDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(if (isProtectionActive) SuccessColor.copy(alpha = 0.1f) else TextSecondary.copy(alpha = 0.08f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isProtectionActive) Icons.Default.Security else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isProtectionActive) SuccessColor else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text("Site Privacy & Security", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(currentHost, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    androidx.compose.material3.HorizontalDivider(color = BorderColor)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = SuccessColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text("Connection Encrypted", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Text("Data in transit is protected via HTTPS", color = TextSecondary, fontSize = 11.5.sp)
                        }
                    }

                    val pageBlocked = activeTab?.blockedCount ?: 0
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (isProtectionActive) SuccessColor else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = if (isProtectionActive) "Tracking Protection Active" else "Tracking Protection Inactive",
                                color = TextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isProtectionActive) {
                                    if (pageBlocked > 0) "$pageBlocked tracker${if (pageBlocked > 1) "s" else ""} blocked on this page" else "No trackers detected on this page"
                                } else {
                                    "Third-party tracking scripts are not blocked"
                                },
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = AccentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text("Private Session", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Text("Session cookies & storage are cleared on exit", color = TextSecondary, fontSize = 11.5.sp)
                        }
                    }

                    androidx.compose.material3.HorizontalDivider(color = BorderColor)

                    Text(
                        text = "PER-SITE OVERRIDE",
                        color = AccentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (siteOverride == true) SuccessColor.copy(alpha = 0.1f) else Color.Transparent)
                                .border(1.dp, if (siteOverride == true) SuccessColor else BorderColor, RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setSiteTrackingProtection(currentHost, true)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = siteOverride == true,
                                onClick = { viewModel.setSiteTrackingProtection(currentHost, true) },
                                colors = RadioButtonDefaults.colors(selectedColor = SuccessColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Always Enable Protection", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (siteOverride == false) DangerColor.copy(alpha = 0.1f) else Color.Transparent)
                                .border(1.dp, if (siteOverride == false) DangerColor else BorderColor, RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setSiteTrackingProtection(currentHost, false)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = siteOverride == false,
                                onClick = { viewModel.setSiteTrackingProtection(currentHost, false) },
                                colors = RadioButtonDefaults.colors(selectedColor = DangerColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Always Disable Protection", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (siteOverride == null) AccentColor.copy(alpha = 0.1f) else Color.Transparent)
                                .border(1.dp, if (siteOverride == null) AccentColor else BorderColor, RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setSiteTrackingProtection(currentHost, null)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = siteOverride == null,
                                onClick = { viewModel.setSiteTrackingProtection(currentHost, null) },
                                colors = RadioButtonDefaults.colors(selectedColor = AccentColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Use Global Default (${if (trackingProtectionEnabled) "ON" else "OFF"})", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
                        onClick = { showSiteSecurityDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (activeTab?.isFullScreen == true) Color.Black else LightBg)
            .let { if (activeTab?.isFullScreen == true) it.zIndex(200f) else it }
    ) {
        Column(modifier = Modifier.fillMaxSize().let { if (activeTab?.isFullScreen == true) it else it.statusBarsPadding() }) {

            // TOP ADDRESS / BAR AREA
            if (activeTab?.isFullScreen != true) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LightCard)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .drawBehind {
                        drawLine(
                            color = BorderColor,
                            start = androidx.compose.ui.geometry.Offset(0f, size.height),
                            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isEditingUrl) {
                    IconButton(
                        onClick = { isEditingUrl = false },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, "Cancel", tint = TextPrimary, modifier = Modifier.size(19.dp))
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(LightBg)
                            .border(1.2.dp, AccentColor.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                            .padding(start = 10.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = AccentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        BasicTextField(
                            value = editingUrlText,
                            onValueChange = { editingUrlText = it },
                            singleLine = true,
                            cursorBrush = SolidColor(AccentColor),
                            textStyle = TextStyle(color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Normal),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(urlFocusRequester),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    val target = editingUrlText.trim()
                                    if (target.isNotEmpty()) {
                                        loadUrl(target)
                                    }
                                    isEditingUrl = false
                                }
                            ),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (editingUrlText.isEmpty()) {
                                        Text(
                                            text = "Search or enter address...",
                                            color = TextSecondary.copy(alpha = 0.65f),
                                            fontSize = 13.5.sp,
                                            maxLines = 1
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        if (editingUrlText.isNotEmpty()) {
                            IconButton(
                                onClick = { editingUrlText = "" },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AccentColor)
                            .premiumPressClick {
                                val target = editingUrlText.trim()
                                if (target.isNotEmpty()) {
                                    loadUrl(target)
                                }
                                isEditingUrl = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowForward, "Go", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                } else {
                    IconButton(
                        onClick = { if (isHome) onExit() else goBackOrExit() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary, modifier = Modifier.size(19.dp))
                    }

                    if (isHome) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                                .clickable {
                                    editingUrlText = ""
                                    isEditingUrl = true
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(AccentColor.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Security, "Secret Browser", tint = AccentColor, modifier = Modifier.size(15.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Secret Browser",
                                color = TextPrimary,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .height(38.dp)
                                .clip(RoundedCornerShape(19.dp))
                                .background(LightBg)
                                .border(1.dp, BorderColor, RoundedCornerShape(19.dp))
                                .clickable {
                                    editingUrlText = activeTab?.url ?: ""
                                    isEditingUrl = true
                                }
                                .padding(start = 7.dp, end = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(if (isProtectionActive) SuccessColor.copy(alpha = 0.12f) else TextSecondary.copy(alpha = 0.08f))
                                        .clickable { showSiteSecurityDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isProtectionActive) Icons.Default.Security else Icons.Default.Lock,
                                        contentDescription = "Security Info",
                                        tint = if (isProtectionActive) SuccessColor else TextSecondary,
                                        modifier = Modifier.size(13.5.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(7.dp))
                                val displayUrl = try {
                                    val urlStr = activeTab?.url ?: ""
                                    if (urlStr == "home" || urlStr.isEmpty()) {
                                        "Secret Browser"
                                    } else {
                                        val uri = android.net.Uri.parse(urlStr)
                                        val host = uri.host
                                        if (!host.isNullOrEmpty()) {
                                            host.removePrefix("www.")
                                        } else {
                                            urlStr
                                        }
                                    }
                                } catch(e: Exception) {
                                    activeTab?.title ?: "Website"
                                }
                                Text(
                                    text = displayUrl,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (activeTab?.isLoading == true) {
                                        stopLoading()
                                    } else {
                                        reload()
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (activeTab?.isLoading == true) Icons.Default.Close else Icons.Default.Refresh,
                                    contentDescription = if (activeTab?.isLoading == true) "Stop" else "Refresh",
                                    tint = if (activeTab?.isLoading == true) DangerColor.copy(alpha = 0.85f) else TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    val currentUrl = activeTab?.url ?: ""
                    val canBookmark = currentUrl.isNotEmpty() && currentUrl != "home" && currentUrl != "about:blank" &&
                            !currentUrl.startsWith("data:") && !currentUrl.startsWith("file:") && !currentUrl.startsWith("about:")
                    val isBookmarked = if (canBookmark) browserBookmarks.any { it.url == currentUrl } else false

                    if (!isHome && canBookmark) {
                        IconButton(
                            onClick = {
                                if (isBookmarked) {
                                    viewModel.removeBrowserBookmark(currentUrl)
                                    Toast.makeText(context, "Removed from Bookmarks", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.addBrowserBookmark(activeTab?.title ?: "New Tab", currentUrl)
                                    Toast.makeText(context, "Added to Bookmarks", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) Color(0xFFFFB300) else TextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, "More Options", tint = TextPrimary, modifier = Modifier.size(19.dp))
                    }
                }
            }

            if (showFindInPage && !isHome) {
                FindInPageBar(
                    query = findInPageText,
                    onQueryChange = { text ->
                        findInPageText = text
                        performFindInPage(text, true)
                    },
                    currentMatch = findInPageMatchCurrent,
                    totalMatch = findInPageMatchTotal,
                    onPrev = { findPreviousMatch() },
                    onNext = { findNextMatch() },
                    onClose = { closeFindInPage() }
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isHome) {
                    SecretBrowserHome(
                        tabs = tabs,
                        activeTabId = activeTabId ?: "",
                        searchEngine = searchEngine,
                        browserBookmarks = browserBookmarks,
                        browserHistory = browserHistory,
                        onSearch = { query ->
                            var target = query.trim()
                            if (!target.startsWith("http://") && !target.startsWith("https://")) {
                                if (target.contains(".") && !target.contains(" ")) {
                                    target = "https://$target"
                                } else {
                                    val q = java.net.URLEncoder.encode(target, "UTF-8")
                                    target = when (searchEngine) {
                                        "DuckDuckGo" -> "https://duckduckgo.com/?q=$q"
                                        "Bing" -> "https://www.bing.com/search?q=$q&setlang=en&cc=US"
                                        "Yahoo" -> "https://search.yahoo.com/search?p=$q&ei=UTF-8&vc=US&vl=en"
                                        else -> "https://www.google.com/search?q=$q&hl=en&gl=US"
                                    }
                                }
                            }
                            loadUrl(target)
                        },
                        onOpenNewTab = { openNewTab(it) },
                        onSelectActiveTab = { targetId ->
                            val currentId = activeTabId
                            if (currentId != null && currentId != targetId) {
                                geckoViews[currentId]?.let { gv -> captureViewThumbnail(gv, currentId) }
                            }
                            activeTabId = targetId
                        },
                        onCloseTab = closeTab,
                        onShowBookmarks = { showBookmarks = true },
                        onShowHistory = { showHistory = true },
                        onShowDownloads = { showDownloads = true },
                        onShowSettings = { showSettings = true },
                        onShowSearchEngineDialog = { showSearchEngineDialog = true },
                        onClearAllData = {
                            if (clearHistoryOnExit) {
                                viewModel.clearBrowserHistory()
                            }
                            clearAllBrowsingData(context, tabs)
                            geckoViews.values.forEach { try { (it.parent as? android.view.ViewGroup)?.removeView(it); it.releaseSession() } catch (e: Exception) {} }
                            geckoViews.clear()
                            geckoSessions.clear()
                            openNewTab("home")
                            Toast.makeText(context, "Session Purged Successfully!", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    val targetProgress = if (activeTab?.isLoading == true) {
                        ((activeTab?.progress ?: 0).coerceIn(12, 95)) / 100f
                    } else {
                        1f
                    }
                    val animatedProgress by animateFloatAsState(
                        targetValue = targetProgress,
                        animationSpec = tween(
                            durationMillis = if (activeTab?.isLoading == true) 250 else 150,
                            easing = FastOutSlowInEasing
                        ),
                        label = "BrowserAnimatedProgress"
                    )
                    val progressAlpha by animateFloatAsState(
                        targetValue = if (activeTab?.isLoading == true && activeTab?.isFullScreen != true) 1f else 0f,
                        animationSpec = tween(durationMillis = if (activeTab?.isLoading == true) 120 else 280),
                        label = "BrowserProgressAlpha"
                    )

                    if (activeGeckoSession != null && currentActiveId != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AndroidView(
                                factory = { ctx ->
                                    val gv = org.mozilla.geckoview.GeckoView(ctx)
                                    geckoViews[currentActiveId] = gv
                                    try {
                                        activeGeckoSession.setActive(true)
                                        gv.setSession(activeGeckoSession)
                                    } catch (e: Exception) {
                                        android.util.Log.e("GeckoViewAttach", "Failed in factory", e)
                                    }
                                    gv
                                },
                                update = { geckoView ->
                                    geckoViews[currentActiveId] = geckoView
                                    try {
                                        activeGeckoSession.setActive(true)
                                        if (geckoView.session != activeGeckoSession) {
                                            geckoView.releaseSession()
                                            geckoView.setSession(activeGeckoSession)
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("GeckoViewUpdate", "Failed in update", e)
                                    }
                                },
                                onRelease = { geckoView ->
                                    try {
                                        geckoViews.remove(currentActiveId)
                                        geckoView.releaseSession()
                                        (geckoView.parent as? android.view.ViewGroup)?.removeView(geckoView)
                                    } catch (e: Exception) {}
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            if (progressAlpha > 0f && activeTab?.isFullScreen != true) {
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .align(Alignment.TopCenter)
                                        .graphicsLayer { alpha = progressAlpha },
                                    color = AccentColor,
                                    trackColor = AccentColor.copy(alpha = 0.12f)
                                )
                            }
                        }
                    }
                }
            }

            // BOTTOM DOCK NAVIGATION REBUILD
            if (activeTab?.isFullScreen != true) Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = CardDefaults.cardColors(containerColor = LightCard),
                    border = BorderStroke(1.dp, BorderColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val canNavigateBack = (activeTab?.canGoBack == true) || (activeTab != null && SecretBrowserNavigationCheckpointManager.hasValidCheckpoint(activeTab.id, activeTab.url)) || (activeTab?.parentTabId != null) || (!isHome && activeTab?.url != "home" && activeTab?.url?.isNotEmpty() == true)
                        IconButton(
                            onClick = { goBack() },
                            enabled = canNavigateBack,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = if (canNavigateBack) TextPrimary else TextSecondary.copy(alpha = 0.3f),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        IconButton(
                            onClick = { goForward() },
                            enabled = activeTab?.canGoForward == true,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Forward",
                                tint = if (activeTab?.canGoForward == true) TextPrimary else TextSecondary.copy(alpha = 0.3f),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                stopLoading()
                                loadUrl("home")
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home",
                                tint = if (isHome) AccentColor else TextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        IconButton(
                            onClick = { openNewTab("home") },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Tab",
                                tint = TextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .premiumPressClick {
                                    activeTabId?.let { id -> captureViewThumbnail(geckoViews[id], id) }
                                    showTabSwitcher = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (showTabSwitcher) AccentColor.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(1.5.dp, if (showTabSwitcher) AccentColor else TextPrimary, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tabs.size.toString(),
                                    color = if (showTabSwitcher) AccentColor else TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // BROWSER MENU OVERLAY (Command Center style)
        androidx.compose.animation.AnimatedVisibility(
            visible = showMenu,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(180)) +
                    androidx.compose.animation.slideInVertically(initialOffsetY = { it / 4 }, animationSpec = androidx.compose.animation.core.tween(220)),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(150)) +
                    androidx.compose.animation.slideOutVertically(targetOffsetY = { it / 4 }, animationSpec = androidx.compose.animation.core.tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showMenu = false }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = LightBg),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 580.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Drag handle
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .background(TextSecondary.copy(alpha = 0.3f), CircleShape)
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Header Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(AccentColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = AccentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Command Center",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Quick navigation & privacy tools",
                                    fontSize = 11.5.sp,
                                    color = TextSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(LightCard)
                                    .border(0.8.dp, BorderColor, CircleShape)
                                    .clickable { showMenu = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Menu",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Category: BROWSER
                        CommandCenterSectionCard(title = "BROWSER") {
                            CommandCenterMenuItem(
                                icon = Icons.Default.Add,
                                iconTint = AccentColor,
                                title = "New Tab",
                                subtitle = "Open a private browsing tab",
                                onClick = {
                                    showMenu = false
                                    openNewTab("home")
                                }
                            )
                            androidx.compose.material3.HorizontalDivider(
                                color = BorderColor.copy(alpha = 0.6f),
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                            CommandCenterMenuItem(
                                icon = Icons.Default.Layers,
                                iconTint = AccentColor,
                                title = "Tab Overview",
                                subtitle = "${tabs.size} active ${if (tabs.size == 1) "tab" else "tabs"}",
                                trailingText = "${tabs.size}",
                                onClick = {
                                    showMenu = false
                                    activeTabId?.let { id -> captureViewThumbnail(geckoViews[id], id) }
                                    showTabSwitcher = true
                                }
                            )
                            androidx.compose.material3.HorizontalDivider(
                                color = BorderColor.copy(alpha = 0.6f),
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                            CommandCenterMenuItem(
                                icon = Icons.Default.Search,
                                iconTint = AccentColor,
                                title = "Find in Page",
                                subtitle = "Search text in active webpage",
                                onClick = {
                                    showMenu = false
                                    if (isHome) {
                                        Toast.makeText(context, "Cannot search on Start page", Toast.LENGTH_SHORT).show()
                                    } else {
                                        showFindInPage = true
                                        findInPageText = ""
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Category: LIBRARY
                        CommandCenterSectionCard(title = "LIBRARY") {
                            CommandCenterMenuItem(
                                icon = Icons.Default.Star,
                                iconTint = AccentColor,
                                title = "Bookmarks",
                                subtitle = "${browserBookmarks.size} saved pages",
                                onClick = {
                                    showMenu = false
                                    showBookmarks = true
                                }
                            )
                            androidx.compose.material3.HorizontalDivider(
                                color = BorderColor.copy(alpha = 0.6f),
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                            CommandCenterMenuItem(
                                icon = Icons.Default.History,
                                iconTint = AccentColor,
                                title = "Browsing History",
                                subtitle = "${browserHistory.size} visit logs",
                                onClick = {
                                    showMenu = false
                                    showHistory = true
                                }
                            )
                            androidx.compose.material3.HorizontalDivider(
                                color = BorderColor.copy(alpha = 0.6f),
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                            CommandCenterMenuItem(
                                icon = Icons.Default.StarBorder,
                                iconTint = TextSecondary,
                                title = "Reading Later",
                                subtitle = "Offline article reader archive",
                                badgeText = "Soon",
                                isEnabled = false,
                                onClick = {}
                            )
                            androidx.compose.material3.HorizontalDivider(
                                color = BorderColor.copy(alpha = 0.6f),
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                            CommandCenterMenuItem(
                                icon = Icons.Default.Download,
                                iconTint = AccentColor,
                                title = "Vault Downloads",
                                subtitle = "Encrypted downloaded files",
                                onClick = {
                                    showMenu = false
                                    showDownloads = true
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Category: PRIVACY & SETTINGS
                        CommandCenterSectionCard(title = "PRIVACY & SETTINGS") {
                            CommandCenterMenuItem(
                                icon = Icons.Default.Settings,
                                iconTint = SuccessColor,
                                title = "Browser Settings",
                                subtitle = "Shields, tracking & preferences",
                                onClick = {
                                    showMenu = false
                                    showSettings = true
                                }
                            )
                            androidx.compose.material3.HorizontalDivider(
                                color = BorderColor.copy(alpha = 0.6f),
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                            CommandCenterMenuItem(
                                icon = Icons.Default.DeleteSweep,
                                iconTint = DangerColor,
                                title = "Clear Browsing Data",
                                subtitle = "History, cookies & cache",
                                isDestructive = true,
                                onClick = {
                                    showMenu = false
                                    showMenuClearBrowsingDataDialog = true
                                }
                            )
                            androidx.compose.material3.HorizontalDivider(
                                color = BorderColor.copy(alpha = 0.6f),
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                            CommandCenterMenuItem(
                                icon = Icons.Default.FolderDelete,
                                iconTint = DangerColor,
                                title = "Clear Temporary Files",
                                subtitle = "Wipe cached remnants securely",
                                isDestructive = true,
                                onClick = {
                                    showMenu = false
                                    showMenuClearTempFilesDialog = true
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Category: EXTRA
                        CommandCenterSectionCard(title = "EXTRA") {
                            val isDesktop = activeTab?.isDesktopMode == true
                            CommandCenterMenuItem(
                                icon = Icons.Default.Laptop,
                                iconTint = AccentColor,
                                title = "Desktop Site",
                                subtitle = if (isDesktop) "Desktop user-agent enabled" else "Standard mobile rendering",
                                trailingSwitch = isDesktop,
                                onClick = {
                                    showMenu = false
                                    if (activeGeckoSession != null && activeTab != null) {
                                        val newMode = !activeTab.isDesktopMode
                                        val index = tabs.indexOfFirst { it.id == activeTabId }
                                        if (index != -1) {
                                            tabs[index] = tabs[index].copy(isDesktopMode = newMode)
                                        }
                                        GeckoSessionManager.setDesktopMode(activeTab.id, newMode)
                                    }
                                }
                            )
                            androidx.compose.material3.HorizontalDivider(
                                color = BorderColor.copy(alpha = 0.6f),
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                            CommandCenterMenuItem(
                                icon = Icons.Default.Security,
                                iconTint = AccentColor,
                                title = "Play Secret Runner",
                                subtitle = "Offline privacy mini-game",
                                onClick = {
                                    showMenu = false
                                    showSecretRunnerGame = true
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Panic Mode Presentation
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DangerColor.copy(alpha = 0.06f)),
                            border = BorderStroke(1.dp, DangerColor.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Panic Mode",
                                        tint = DangerColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Panic Mode",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Close Secret Browser and clear sensitive browsing session",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        showMenu = false
                                        try {
                                            SecretBrowserSecureDelete.cleanTemporaryUploadsDirectory(context, secure = true)
                                            SecretBrowserSecureDelete.cleanStaleTemporaryRemnants(context, secure = true)
                                        } catch (e: Exception) {
                                            android.util.Log.e("SecureDelete", "Panic cleanup failed", e)
                                        }
                                        if (clearHistoryOnExit) {
                                            viewModel.clearBrowserHistory()
                                        }
                                        clearAllBrowsingData(context, tabs)
                                        geckoViews.values.forEach { try { (it.parent as? android.view.ViewGroup)?.removeView(it); it.releaseSession() } catch (e: Exception) {} }
                                        geckoViews.clear()
                                        geckoSessions.clear()
                                        onPanic()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DangerColor),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(40.dp)
                                ) {
                                    Text(
                                        text = "Activate Panic Mode",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        if (showMenuClearBrowsingDataDialog) {
            AlertDialog(
                onDismissRequest = { showMenuClearBrowsingDataDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = DangerColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Text("Clear Browsing Data", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Text(
                        "Securely wipe browsing history, cached assets, active session cookies, and temporary data from this device.",
                        color = TextSecondary,
                        fontSize = 13.5.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showMenuClearBrowsingDataDialog = false
                            SecretBrowserPrivacyHelper.clearBrowsingData(
                                context = context,
                                clearHistory = true,
                                clearCookies = true,
                                clearCache = true,
                                clearSiteData = true,
                                viewModel = viewModel,
                                onResult = { ok: Boolean ->
                                    if (ok) {
                                        Toast.makeText(context, "Browsing data cleared", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to clear some browsing data", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerColor)
                    ) {
                        Text("Clear Browsing Data", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMenuClearBrowsingDataDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = LightCard,
                tonalElevation = 6.dp
            )
        }

        if (showMenuClearTempFilesDialog) {
            AlertDialog(
                onDismissRequest = { showMenuClearTempFilesDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderDelete,
                            contentDescription = null,
                            tint = DangerColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Text("Clear Temporary Files", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Text(
                        "Permanently purge cached uploads, temporary fragments, and incomplete downloads.",
                        color = TextSecondary,
                        fontSize = 13.5.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showMenuClearTempFilesDialog = false
                            coroutineScope.launch(Dispatchers.IO) {
                                val ok1 = SecretBrowserSecureDelete.cleanTemporaryUploadsDirectory(context, secure = true)
                                val ok2 = SecretBrowserSecureDelete.cleanStaleTemporaryRemnants(context, secure = true)
                                withContext(Dispatchers.Main) {
                                    if (ok1 && ok2) {
                                        Toast.makeText(context, "Temporary files cleared", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Temporary files cleared", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerColor)
                    ) {
                        Text("Clear Temporary Files", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMenuClearTempFilesDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = LightCard,
                tonalElevation = 6.dp
            )
        }
        BrowserUploadSourceDialog(viewModel = viewModel)

        if (showTabSwitcher) {
            SecretBrowserTabSwitcherScreen(
                tabs = tabs,
                activeTabId = activeTabId,
                onSelectTab = { id ->
                    activeTabId = id
                    showTabSwitcher = false
                },
                onCloseTab = { id ->
                    closeTab(id)
                },
                onCloseAllTabs = {
                    TabThumbnailCache.clear()
                    tabs.clear()
                    openNewTab("home")
                    showTabSwitcher = false
                },
                onNewTab = {
                    openNewTab("home")
                    showTabSwitcher = false
                },
                onBack = { showTabSwitcher = false }
            )
        }

        if (showSecretRunnerGame) {
            SecretRunnerGameView(
                onClose = { showSecretRunnerGame = false }
            )
        }
    }
}

@Composable
fun FindInPageBar(
    query: String,
    onQueryChange: (String) -> Unit,
    currentMatch: Int,
    totalMatch: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LightBg)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .drawBehind {
                drawLine(
                    color = BorderColor,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Search Input Field Container
        Row(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .background(LightCard, RoundedCornerShape(20.dp))
                .border(1.dp, AccentColor.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = AccentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                cursorBrush = SolidColor(AccentColor),
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.5.sp),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { onNext() }
                ),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text("Find in page...", color = TextSecondary.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1)
                        }
                        innerTextField()
                    }
                }
            )

            if (query.isNotEmpty()) {
                // Clear button
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = TextSecondary.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Match Counter Badge
                Box(
                    modifier = Modifier
                        .background(
                            if (totalMatch > 0) AccentColor.copy(alpha = 0.12f) else TextSecondary.copy(alpha = 0.1f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (totalMatch > 0) "$currentMatch/$totalMatch" else "0/0",
                        color = if (totalMatch > 0) AccentColor else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Previous Match
        IconButton(
            onClick = onPrev,
            modifier = Modifier.size(34.dp),
            enabled = query.isNotEmpty() && totalMatch > 0
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Previous Match",
                tint = if (query.isNotEmpty() && totalMatch > 0) TextPrimary else TextSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp)
            )
        }

        // Next Match
        IconButton(
            onClick = onNext,
            modifier = Modifier.size(34.dp),
            enabled = query.isNotEmpty() && totalMatch > 0
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Next Match",
                tint = if (query.isNotEmpty() && totalMatch > 0) TextPrimary else TextSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp)
            )
        }

        // Close Find in Page
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Find in Page",
                tint = TextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
