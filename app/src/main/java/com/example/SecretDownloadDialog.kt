package com.example

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * High-craft, ultra-premium download confirmation dialog for Secret Browser.
 * Accurately displays filename, size, MIME type badge, and encrypted sandbox guarantee.
 */
@Composable
fun SecretBrowserDownloadConfirmDialog(
    download: PendingDownloadData,
    viewModel: CalculatorViewModel,
    onDismiss: () -> Unit,
    onConfirm: (destination: DownloadDestination) -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    val (guessedFilename, resolvedMime) = SecretDownloadFilenameHelper.resolveFilenameAndMime(
        download.url,
        download.contentDisposition,
        download.mimeType
    )

    val sizeText = if (download.contentLength > 0) {
        viewModel.formatFileSize(download.contentLength)
    } else {
        "Unknown Size"
    }

    val lowerName = guessedFilename.lowercase()
    val isVideo = resolvedMime.startsWith("video/") ||
            lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") ||
            lowerName.endsWith(".webm") || lowerName.endsWith(".avi") ||
            lowerName.endsWith(".mov") || lowerName.endsWith(".m4v") ||
            lowerName.endsWith(".flv") || lowerName.endsWith(".3gp") ||
            lowerName.endsWith(".ts") || lowerName.endsWith(".wmv")

    val isImage = resolvedMime.startsWith("image/") ||
            lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
            lowerName.endsWith(".png") || lowerName.endsWith(".webp") ||
            lowerName.endsWith(".gif") || lowerName.endsWith(".bmp") ||
            lowerName.endsWith(".svg") || lowerName.endsWith(".ico")

    val isAudio = resolvedMime.startsWith("audio/") ||
            lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") ||
            lowerName.endsWith(".m4a") || lowerName.endsWith(".aac") ||
            lowerName.endsWith(".ogg") || lowerName.endsWith(".flac") ||
            lowerName.endsWith(".opus") || lowerName.endsWith(".weba")

    val (fileIcon: ImageVector, iconBg: Color, iconColor: Color, typeLabel: String) = when {
        isImage -> Tuple4(Icons.Default.Image, Color(0xFF1E3A8A).copy(alpha = 0.2f), Color(0xFF3B82F6), "IMAGE")
        isVideo -> Tuple4(Icons.Default.VideoLibrary, Color(0xFF581C87).copy(alpha = 0.2f), Color(0xFFA855F7), "VIDEO")
        isAudio -> Tuple4(Icons.Default.AudioFile, Color(0xFF7C2D12).copy(alpha = 0.2f), Color(0xFFF97316), "AUDIO")
        resolvedMime.contains("pdf") || lowerName.endsWith(".pdf") ->
            Tuple4(Icons.Default.Description, Color(0xFF7F1D1D).copy(alpha = 0.2f), Color(0xFFEF4444), "PDF DOC")
        resolvedMime.contains("zip") || resolvedMime.contains("rar") || resolvedMime.contains("archive") || lowerName.endsWith(".zip") || lowerName.endsWith(".tar") || lowerName.endsWith(".gz") || lowerName.endsWith(".7z") ->
            Tuple4(Icons.Default.FolderZip, Color(0xFF78350F).copy(alpha = 0.2f), Color(0xFFF59E0B), "ARCHIVE")
        lowerName.endsWith(".apk") || resolvedMime.contains("android.package-archive") ->
            Tuple4(Icons.Default.Android, Color(0xFF064E3B).copy(alpha = 0.2f), Color(0xFF10B981), "PACKAGE")
        else ->
            Tuple4(Icons.Default.InsertDriveFile, Color(0xFFFF6A00).copy(alpha = 0.15f), Color(0xFFFF6A00), "DOCUMENT")
    }

    // Modern glass-tinted styling
    val cardBg = if (isDark) Color(0xFF0F172A) else Color(0xFFFFFFFF)
    val innerCardBg = if (isDark) Color(0xFF1E293B).copy(alpha = 0.7f) else Color(0xFFF8FAFC)
    val strokeColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val textHead = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSub = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val orangeAccent = Color(0xFFFF6A00)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(
                1.2.dp,
                Brush.verticalGradient(
                    listOf(
                        orangeAccent.copy(alpha = 0.45f),
                        strokeColor.copy(alpha = 0.5f)
                    )
                )
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(orangeAccent.copy(alpha = 0.28f), orangeAccent.copy(alpha = 0.06f))
                                    ),
                                    CircleShape
                                )
                                .border(1.dp, orangeAccent.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = orangeAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Download File",
                                color = textHead,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "Secret Vault Encrypted",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Dismiss 'X' button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textSub,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // File Details Container
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = innerCardBg),
                    border = BorderStroke(1.dp, strokeColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // File Icon + Title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(iconBg, RoundedCornerShape(12.dp))
                                    .border(1.dp, iconColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = fileIcon,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = guessedFilename,
                                    color = textHead,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 19.sp
                                )
                            }
                        }

                        // Metadata Badge Pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Size Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = orangeAccent.copy(alpha = 0.10f),
                                border = BorderStroke(0.8.dp, orangeAccent.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DataUsage,
                                        contentDescription = null,
                                        tint = orangeAccent,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = sizeText,
                                        color = orangeAccent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Format Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = iconColor.copy(alpha = 0.12f),
                                border = BorderStroke(0.8.dp, iconColor.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = typeLabel,
                                    color = iconColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }

                            // Destination Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDark) Color(0xFF0F172A) else Color(0xFFE2E8F0),
                                border = BorderStroke(0.8.dp, strokeColor)
                            ) {
                                Text(
                                    text = "🔒 Secret Vault",
                                    color = textSub,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Vault Security Notice Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = (if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)).copy(alpha = 0.6f),
                    border = BorderStroke(0.8.dp, strokeColor.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Item will be hidden from Gallery and public storage, secured inside your private vault.",
                            color = textSub,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, strokeColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textSub)
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            onConfirm(DownloadDestination.SECRET_VAULT)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1.35f)
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = orangeAccent
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 1.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Download",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// Simple quad-tuple helper to avoid Any/reflection
private data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
