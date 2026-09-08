package com.example

import android.net.Uri
import android.webkit.MimeTypeMap
import java.net.URLDecoder
import java.util.Locale
import java.util.regex.Pattern

/**
 * Robust filename and MIME-type resolver for Secret Browser downloads.
 * Strictly preserves server-provided filenames and derives standard media/file extensions
 * when extensions are omitted or obscured by generic octet-stream MIME types.
 */
object SecretDownloadFilenameHelper {

    private val RFC5987_PATTERN = Pattern.compile("(?i)(?:^|;\\s*)filename\\*\\s*=\\s*([^']*?)'([^']*?)'([^;\\s]+)")
    private val STANDARD_FILENAME_PATTERN = Pattern.compile("(?i)(?:^|;\\s*)filename\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^;\\s]+))")

    /**
     * Extracts and cleans the MIME type, removing parameters such as charset, codecs, boundary.
     * e.g. "video/mp4; charset=UTF-8" -> "video/mp4"
     */
    fun cleanMimeType(rawMime: String?): String {
        if (rawMime.isNullOrBlank()) return ""
        val mime = rawMime.split(";")[0].trim().lowercase(Locale.ROOT)
        return if (mime == "null" || mime == "undefined") "" else mime
    }

    /**
     * Checks if a MIME type is a generic binary fallback rather than a real content type.
     */
    fun isGenericMimeType(mime: String?): Boolean {
        val clean = cleanMimeType(mime)
        return clean.isEmpty() ||
                clean == "application/octet-stream" ||
                clean == "binary/octet-stream" ||
                clean == "application/force-download" ||
                clean == "application/x-download" ||
                clean == "application/download" ||
                clean == "application/unknown" ||
                clean == "application/x-unknown"
    }

    /**
     * Parses Content-Disposition header with full RFC 6266 / RFC 5987 support (filename* and filename).
     */
    fun parseContentDispositionFilename(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrBlank()) return null

        // 1. Try RFC 5987 filename*=charset'lang'encoded_value
        try {
            val matcher5987 = RFC5987_PATTERN.matcher(contentDisposition)
            if (matcher5987.find()) {
                val charset = matcher5987.group(1)?.ifBlank { "UTF-8" } ?: "UTF-8"
                val encodedVal = matcher5987.group(3)
                if (!encodedVal.isNullOrBlank()) {
                    val decoded = URLDecoder.decode(encodedVal, charset)
                    val sanitized = sanitizeFilename(decoded)
                    if (sanitized.isNotBlank()) return sanitized
                }
            }
        } catch (e: Exception) {
            // fallback to standard matcher
        }

        // 2. Try standard filename="value" or filename=value
        try {
            val matcher = STANDARD_FILENAME_PATTERN.matcher(contentDisposition)
            if (matcher.find()) {
                val candidate = matcher.group(1) ?: matcher.group(2) ?: matcher.group(3)
                if (!candidate.isNullOrBlank()) {
                    val sanitized = sanitizeFilename(candidate)
                    if (sanitized.isNotBlank()) return sanitized
                }
            }
        } catch (e: Exception) {
            // fallback
        }

        return null
    }

    /**
     * Cleans dangerous characters and directory separators from a filename.
     */
    fun sanitizeFilename(raw: String): String {
        var clean = raw.trim().replace("\\", "/").substringAfterLast("/")
        clean = clean.replace("\"", "").replace("'", "").trim()
        try {
            if (clean.contains("%")) {
                clean = URLDecoder.decode(clean, "UTF-8")
            }
        } catch (e: Exception) {}
        clean = clean.replace(Regex("[\\r\\n\\t\\\\/:*?\"<>|]"), "_")
        return clean.trim()
    }

    /**
     * Determines the most appropriate file extension for a given MIME type.
     */
    fun getExtensionForMimeType(mimeType: String?): String? {
        val clean = cleanMimeType(mimeType)
        if (clean.isEmpty() || isGenericMimeType(clean)) return null

        return when (clean) {
            // Video MIME types
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/x-matroska", "video/mkv" -> "mkv"
            "video/quicktime" -> "mov"
            "video/x-msvideo", "video/avi" -> "avi"
            "video/3gpp", "video/3gpp2" -> "3gp"
            "video/x-flv" -> "flv"
            "video/x-m4v" -> "m4v"
            "video/mp2t" -> "ts"
            "video/ogg" -> "ogv"
            "video/x-ms-wmv" -> "wmv"

            // Audio MIME types
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
            "audio/aac", "audio/x-aac" -> "aac"
            "audio/ogg", "audio/vorbis" -> "ogg"
            "audio/opus" -> "opus"
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/midi", "audio/x-midi" -> "mid"
            "audio/webm" -> "weba"
            "audio/3gpp" -> "3ga"

            // Image MIME types
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/svg+xml" -> "svg"
            "image/bmp", "image/x-ms-bmp" -> "bmp"
            "image/x-icon", "image/vnd.microsoft.icon" -> "ico"
            "image/heic", "image/heif" -> "heic"
            "image/avif" -> "avif"

            // Documents / Archives / Binaries
            "application/pdf" -> "pdf"
            "application/zip", "application/x-zip-compressed" -> "zip"
            "application/x-rar-compressed", "application/vnd.rar" -> "rar"
            "application/x-7z-compressed" -> "7z"
            "application/x-tar", "application/tar" -> "tar"
            "application/gzip", "application/x-gzip" -> "gz"
            "application/vnd.android.package-archive" -> "apk"
            "text/plain" -> "txt"
            "text/html" -> "html"
            "text/css" -> "css"
            "text/csv" -> "csv"
            "application/json" -> "json"
            "application/msword" -> "doc"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            "application/vnd.ms-excel" -> "xls"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"

            else -> try {
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(clean)
                if (!ext.isNullOrBlank()) ext.lowercase(Locale.ROOT) else null
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Determines the MIME type from a file extension when server returns generic/missing MIME type.
     */
    fun getMimeTypeForExtension(extension: String?): String? {
        if (extension.isNullOrBlank()) return null
        val ext = extension.trim().lowercase(Locale.ROOT).removePrefix(".")
        return when (ext) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            "flv" -> "video/x-flv"
            "m4v" -> "video/x-m4v"
            "ts" -> "video/mp2t"
            "wmv" -> "video/x-ms-wmv"
            "ogv" -> "video/ogg"

            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "weba" -> "audio/webm"

            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "bmp" -> "image/bmp"
            "ico" -> "image/x-icon"
            "heic" -> "image/heic"
            "avif" -> "image/avif"

            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "apk" -> "application/vnd.android.package-archive"
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> try {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Resolves the complete file name and ensures proper extension preservation.
     * Following principle:
     * SERVER-PROVIDED FILENAME
     *         ↓
     * preserve filename
     *         ↓
     * if extension missing
     *         ↓
     * derive extension from reliable MIME type
     *         ↓
     * save with correct filename
     */
    fun resolveFilenameAndMime(
        url: String?,
        contentDisposition: String?,
        mimeType: String?
    ): Pair<String, String> {
        val cleanMime = cleanMimeType(mimeType)

        // 1. Try to get filename from Content-Disposition
        var rawName = parseContentDispositionFilename(contentDisposition)

        // 2. If not in disposition, extract from URL path
        if (rawName.isNullOrBlank() && !url.isNullOrBlank()) {
            try {
                val uri = Uri.parse(url)
                val path = uri.path
                if (!path.isNullOrBlank()) {
                    val lastSegment = path.substringAfterLast("/")
                    if (lastSegment.isNotBlank() && lastSegment != "/" && !lastSegment.contains("=")) {
                        rawName = sanitizeFilename(URLDecoder.decode(lastSegment, "UTF-8"))
                    }
                }
            } catch (e: Exception) {}
        }

        // 3. Fallback to default name if still empty or generic placeholder
        if (rawName.isNullOrBlank() || rawName == "downloadfile" || rawName == "downloadfile.bin" || rawName == "downloaded_file") {
            rawName = "downloaded_file"
        }

        var baseName = rawName
        var currentExt = ""

        val lastDot = rawName.lastIndexOf('.')
        if (lastDot > 0 && lastDot < rawName.length - 1) {
            val candidateExt = rawName.substring(lastDot + 1).lowercase(Locale.ROOT)
            // If the extension is a dummy placeholder (".bin", ".dat", ".tmp" or all digits) and we have a valid MIME type, strip dummy ext
            if ((candidateExt == "bin" || candidateExt == "dat" || candidateExt == "tmp" || candidateExt.all { it.isDigit() }) && !isGenericMimeType(cleanMime)) {
                baseName = rawName.substring(0, lastDot)
                currentExt = ""
            } else {
                baseName = rawName.substring(0, lastDot)
                currentExt = candidateExt
            }
        }

        // 4. If extension is missing, derive it from reliable MIME type
        val finalExt: String = if (currentExt.isNotEmpty()) {
            currentExt
        } else {
            val derived = getExtensionForMimeType(cleanMime)
            derived ?: ""
        }

        val finalFilename = if (finalExt.isNotEmpty()) {
            "$baseName.$finalExt"
        } else {
            baseName
        }

        // 5. Derive or preserve the effective MIME type
        val effectiveMime = if (!isGenericMimeType(cleanMime)) {
            cleanMime
        } else {
            val inferred = getMimeTypeForExtension(finalExt)
            inferred ?: (if (cleanMime.isNotBlank()) cleanMime else "application/octet-stream")
        }

        return Pair(finalFilename, effectiveMime)
    }
}
