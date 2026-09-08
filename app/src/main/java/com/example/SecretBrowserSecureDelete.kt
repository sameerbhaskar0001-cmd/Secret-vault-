package com.example

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Phase 7.5A — Secret Browser Secure Delete Foundation
 * Safely identifies and removes browser-generated temporary/privacy-sensitive files
 * without deleting legitimate user data. Includes secure overwriting of contents
 * to prevent recovery, memory-safe chunked streaming operations, and strict safety guards.
 */
object SecretBrowserSecureDelete {

    /**
     * Safely overwrites file contents with zeros using a memory-safe, chunked stream
     * before deleting the file. This prevents recovery of sensitive temporary browser data.
     * Returns true if overwrite was attempted, even if the file is empty.
     */
    fun secureOverwriteAndClose(file: File): Boolean {
        if (!file.exists() || !file.isFile) return true
        var outStream: java.io.FileOutputStream? = null
        try {
            val length = file.length()
            if (length > 0) {
                outStream = java.io.FileOutputStream(file)
                val bufferSize = 4096
                val zeroBuffer = ByteArray(bufferSize) // Zero-filled buffer
                var bytesWritten = 0L
                while (bytesWritten < length) {
                    val toWrite = Math.min(bufferSize.toLong(), length - bytesWritten).toInt()
                    outStream.write(zeroBuffer, 0, toWrite)
                    bytesWritten += toWrite
                }
                outStream.flush()
            }
            return true
        } catch (e: Exception) {
            Log.e("SecureDelete", "Failed to secure-overwrite file: ${file.name}", e)
            return false
        } finally {
            try {
                outStream?.close()
            } catch (e: Exception) {}
        }
    }

    /**
     * Deletes a single temporary browser file, optionally overwriting its content
     * with zeroes first to prevent recovery. Ignores missing/already-deleted files safely.
     */
    fun deleteTemporaryFile(file: File, secure: Boolean = true): Boolean {
        return try {
            if (!file.exists()) {
                Log.d("SecureDelete", "File ${file.name} does not exist, ignoring.")
                return true
            }
            
            if (System.currentTimeMillis() - file.lastModified() < 15000) {
                Log.d("SecureDelete", "Skipping active/recently modified file: ${file.name}")
                return true
            }
            
            if (file.isDirectory) {
                Log.e("SecureDelete", "${file.name} is a directory. Use directory cleanups instead.")
                return false
            }

            if (secure) {
                secureOverwriteAndClose(file)
            }

            val result = file.delete()
            Log.d("SecureDelete", "Deleted temp file ${file.name}: $result")
            result
        } catch (e: Exception) {
            Log.e("SecureDelete", "Exception deleting temp file ${file.name}", e)
            false
        }
    }

    /**
     * Helper overload to delete a file by its absolute path.
     */
    fun deleteTemporaryFile(filePath: String, secure: Boolean = true): Boolean {
        if (filePath.isEmpty()) return true
        return deleteTemporaryFile(File(filePath), secure)
    }

    /**
     * Cleans all files in the browser temporary-upload directory (context.cacheDir/temp_browser_uploads).
     * It will overwrite each file before deleting. Missing or already-deleted files are safely ignored.
     */
    fun cleanTemporaryUploadsDirectory(context: Context, secure: Boolean = true): Boolean {
        return try {
            val tempDir = File(context.cacheDir, "temp_browser_uploads")
            if (tempDir.exists() && tempDir.isDirectory) {
                val files = tempDir.listFiles()
                var allSuccess = true
                files?.forEach { file ->
                    if (file.isFile) {
                        if (!deleteTemporaryFile(file, secure)) {
                            allSuccess = false
                        }
                    } else if (file.isDirectory) {
                        // Recursively delete subdirectories safely
                        val recurseSuccess = deleteDirectoryRecursively(file, secure)
                        if (!recurseSuccess) {
                            allSuccess = false
                        }
                    }
                }
                allSuccess
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e("SecureDelete", "Failed to clean temporary uploads directory", e)
            false
        }
    }

    /**
     * Recursively deletes a directory, optionally secure-overwriting all files first.
     */
    fun deleteDirectoryRecursively(dir: File, secure: Boolean = true): Boolean {
        if (!dir.exists()) return true
        if (!dir.isDirectory) return deleteTemporaryFile(dir, secure)
        
        var allSuccess = true
        try {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isDirectory) {
                        allSuccess = deleteDirectoryRecursively(file, secure) && allSuccess
                    } else {
                        allSuccess = deleteTemporaryFile(file, secure) && allSuccess
                    }
                }
            }
            allSuccess = dir.delete() && allSuccess
        } catch (e: Exception) {
            Log.e("SecureDelete", "Failed to recursively delete directory: ${dir.name}", e)
            allSuccess = false
        }
        return allSuccess
    }

    /**
     * Scans cacheDir and externalCacheDir for temporary browser files and stale remnants.
     * Remnants include:
     * - known photo/video capture files (upload_temp_capture.jpg, upload_temp_capture.mp4)
     * - files ending with .tmp, .temp, .part, .crdownload
     * - files starting with temp_upload_ or download_temp
     * Explicitly protects critical app/browser directories like webview, gecko, shared_prefs, databases.
     */
    fun cleanStaleTemporaryRemnants(context: Context, secure: Boolean = true): Boolean {
        var success = true
        
        // 1. Known upload capture files in cacheDir
        val captureFiles = listOf(
            File(context.cacheDir, "upload_temp_capture.jpg"),
            File(context.cacheDir, "upload_temp_capture.mp4")
        )
        for (file in captureFiles) {
            if (!deleteTemporaryFile(file, secure)) {
                success = false
            }
        }

        // 2. Scan context.cacheDir
        try {
            val cacheDir = context.cacheDir
            if (cacheDir.exists() && cacheDir.isDirectory) {
                success = cleanRemnantsInDirectory(cacheDir, secure) && success
            }
        } catch (e: Exception) {
            Log.e("SecureDelete", "Error cleaning remnants in cacheDir", e)
            success = false
        }

        // 3. Scan context.externalCacheDir if it exists
        try {
            val extCache = context.externalCacheDir
            if (extCache != null && extCache.exists() && extCache.isDirectory) {
                success = cleanRemnantsInDirectory(extCache, secure) && success
            }
        } catch (e: Exception) {
            Log.e("SecureDelete", "Error cleaning remnants in externalCacheDir", e)
            success = false
        }

        return success
    }

    private fun cleanRemnantsInDirectory(dir: File, secure: Boolean): Boolean {
        var success = true
        try {
            val files = dir.listFiles() ?: return true
            for (file in files) {
                if (file.isDirectory) {
                    val nameLower = file.name.lowercase()
                    // Explicit safety boundaries to protect critical components
                    if (nameLower == "webview" || 
                        nameLower.contains("gecko") || 
                        nameLower == "lib-main" || 
                        nameLower == "shared_prefs" || 
                        nameLower == "databases") {
                        continue
                    }
                    
                    // If it is our temp_browser_uploads, clean it fully
                    if (nameLower == "temp_browser_uploads") {
                        success = deleteDirectoryRecursively(file, secure) && success
                        continue
                    }
                    
                    // Recurse into other directories
                    success = cleanRemnantsInDirectory(file, secure) && success
                } else if (file.isFile) {
                    val nameLower = file.name.lowercase()
                    val isRemnant = nameLower.endsWith(".tmp") ||
                            nameLower.endsWith(".temp") ||
                            nameLower.endsWith(".part") ||
                            nameLower.endsWith(".crdownload") ||
                            nameLower.startsWith("temp_upload_") ||
                            nameLower.startsWith("download_temp")
                    
                    if (isRemnant) {
                        if (!deleteTemporaryFile(file, secure)) {
                            success = false
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SecureDelete", "Failed to scan remnants in directory: ${dir.absolutePath}", e)
            success = false
        }
        return success
    }
}
