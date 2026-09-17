package com.example

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

sealed class GeckoDownloadResult {
    data class Success(val file: File, val totalRead: Long, val mimeType: String, val contentLength: Long) : GeckoDownloadResult()
    data class HtmlChallengeResponse(val message: String) : GeckoDownloadResult()
    data class ServerError(val statusCode: Int, val message: String) : GeckoDownloadResult()
    data class Error(val message: String?, val cause: Throwable? = null) : GeckoDownloadResult()
}

object GeckoDownloadEngine {

    suspend fun executeDownload(
        context: Context,
        url: String,
        targetFile: File,
        referrerUrl: String = "",
        append: Boolean = false,
        onProgress: (current: Long, total: Long, speedStr: String) -> Unit
    ): GeckoDownloadResult = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            val runtime = GeckoEngine.getRuntime(context)
            val executor = GeckoWebExecutor(runtime)
            
            val builder = WebRequest.Builder(url)
            if (referrerUrl.isNotBlank()) {
                builder.header("Referer", referrerUrl)
            }
            builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,video/*,audio/*,*/*;q=0.8")
            
            val request = builder.build()

            val response = suspendCoroutine<WebResponse> { continuation ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        val result = executor.fetch(request, GeckoWebExecutor.FETCH_FLAGS_NONE)
                        result.accept(
                            { res -> 
                                if (res != null) {
                                    continuation.resume(res)
                                } else {
                                    continuation.resumeWithException(Exception("Received null WebResponse"))
                                }
                            },
                            { ex ->
                                continuation.resumeWithException(ex ?: Exception("Unknown GeckoWebExecutor error"))
                            }
                        )
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            }

            val statusCode = response.statusCode
            if (statusCode !in 200..299) {
                throw java.io.IOException("HTTP Error $statusCode from GeckoWebExecutor")
            } else {
                val headers = response.headers
                val contentType = headers["Content-Type"] ?: headers["content-type"] ?: ""
                if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
                    GeckoDownloadResult.HtmlChallengeResponse("Verification/Ad page detected instead of media file")
                } else {
                    val body = response.body
                    if (body == null) {
                        throw java.io.IOException("Null response body from GeckoWebExecutor")
                    } else {
                        inputStream = body
                        outputStream = FileOutputStream(targetFile, append)

                        val contentLengthStr = headers["Content-Length"] ?: headers["content-length"]
                        val contentLength = contentLengthStr?.toLongOrNull() ?: 0L

                        val buffer = ByteArray(131072) // 128KB buffer
                        var totalRead = if (append) targetFile.length() else 0L
                        val expectedTotal = if (contentLength > 0) totalRead + contentLength else 0L
                        
                        var lastUiUpdateTime = 0L
                        var speedWindowStartTime = android.os.SystemClock.uptimeMillis()
                        var speedWindowStartBytes = totalRead
                        var currentSpeedStr = ""

                        while (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive == true) {
                            val bytesRead = body.read(buffer)
                            if (bytesRead == -1) break
                            outputStream?.write(buffer, 0, bytesRead)
                            totalRead += bytesRead

                            val now = android.os.SystemClock.uptimeMillis()
                            val timeDelta = now - speedWindowStartTime
                            if (timeDelta >= 1000) {
                                val bytesInDelta = totalRead - speedWindowStartBytes
                                val bytesPerSec = (bytesInDelta * 1000L) / timeDelta.coerceAtLeast(1L)
                                currentSpeedStr = if (bytesPerSec > 0) formatFileSize(bytesPerSec) + "/s" else ""
                                speedWindowStartTime = now
                                speedWindowStartBytes = totalRead
                            }
                            
                            if (now - lastUiUpdateTime > 400 || (expectedTotal > 0 && totalRead >= expectedTotal)) {
                                lastUiUpdateTime = now
                                onProgress(totalRead, expectedTotal, currentSpeedStr)
                            }
                        }
                        
                        GeckoDownloadResult.Success(targetFile, totalRead, contentType, expectedTotal)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("GeckoDownloadEngine", "Primary GeckoWebExecutor failed: ${e.message}. Falling back to HttpURLConnection.", e)
            downloadWithHttpURLConnection(url, targetFile, referrerUrl, append, onProgress)
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
            try { outputStream?.close() } catch (e: Exception) {}
        }
    }

    private suspend fun downloadWithHttpURLConnection(
        url: String,
        targetFile: File,
        referrerUrl: String,
        append: Boolean,
        onProgress: (current: Long, total: Long, speedStr: String) -> Unit
    ): GeckoDownloadResult = withContext(Dispatchers.IO) {
        var connection: java.net.HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            val connectionUrl = java.net.URL(url)
            connection = connectionUrl.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true
            
            // Set standard browser-like headers to avoid getting blocked or encountering HTTPS handshake blocks
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Mobile Safari/537.36")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,video/*,audio/*,*/*;q=0.8")
            if (referrerUrl.isNotBlank()) {
                connection.setRequestProperty("Referer", referrerUrl)
            }
            
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext GeckoDownloadResult.ServerError(responseCode, "HTTP Error: $responseCode")
            }
            
            val contentType = connection.contentType ?: ""
            if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
                return@withContext GeckoDownloadResult.HtmlChallengeResponse("Verification/Ad page detected instead of media file")
            }
            
            val contentLength = connection.contentLength.toLong()
            inputStream = connection.inputStream
            outputStream = FileOutputStream(targetFile, append)
            
            val buffer = ByteArray(131072) // 128KB buffer
            var totalRead = if (append) targetFile.length() else 0L
            val expectedTotal = if (contentLength > 0) totalRead + contentLength else 0L
            
            var lastUiUpdateTime = 0L
            var speedWindowStartTime = android.os.SystemClock.uptimeMillis()
            var speedWindowStartBytes = totalRead
            var currentSpeedStr = ""
            
            while (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive == true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                
                val now = android.os.SystemClock.uptimeMillis()
                val timeDelta = now - speedWindowStartTime
                if (timeDelta >= 1000) {
                    val bytesInDelta = totalRead - speedWindowStartBytes
                    val bytesPerSec = (bytesInDelta * 1000L) / timeDelta.coerceAtLeast(1L)
                    currentSpeedStr = if (bytesPerSec > 0) formatFileSize(bytesPerSec) + "/s" else ""
                    speedWindowStartTime = now
                    speedWindowStartBytes = totalRead
                }
                
                if (now - lastUiUpdateTime > 400 || (expectedTotal > 0 && totalRead >= expectedTotal)) {
                    lastUiUpdateTime = now
                    onProgress(totalRead, expectedTotal, currentSpeedStr)
                }
            }
            
            GeckoDownloadResult.Success(targetFile, totalRead, contentType, expectedTotal)
        } catch (e: Exception) {
            GeckoDownloadResult.Error(e.message, e)
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
            try { outputStream?.close() } catch (e: Exception) {}
            try { connection?.disconnect() } catch (e: Exception) {}
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
