package com.example

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecretDownloadFilenameHelperTest {

    @Test
    fun testCase1_videoMp4WithoutExtension() {
        val (filename, mime) = SecretDownloadFilenameHelper.resolveFilenameAndMime(
            url = "https://example.com/stream/video?id=123",
            contentDisposition = "attachment; filename=movie",
            mimeType = "video/mp4"
        )
        assertEquals("movie.mp4", filename)
        assertEquals("video/mp4", mime)
    }

    @Test
    fun testCase2_videoMp4WithExtension() {
        val (filename, mime) = SecretDownloadFilenameHelper.resolveFilenameAndMime(
            url = "https://example.com/stream/video?id=123",
            contentDisposition = "attachment; filename=movie.mp4",
            mimeType = "video/mp4"
        )
        assertEquals("movie.mp4", filename)
        assertEquals("video/mp4", mime)
    }

    @Test
    fun testCase3_audioMpegWithoutExtension() {
        val (filename, mime) = SecretDownloadFilenameHelper.resolveFilenameAndMime(
            url = "https://example.com/stream/audio?track=456",
            contentDisposition = "attachment; filename=song",
            mimeType = "audio/mpeg"
        )
        assertEquals("song.mp3", filename)
        assertEquals("audio/mpeg", mime)
    }

    @Test
    fun testCase4_videoWebmWithoutExtension() {
        val (filename, mime) = SecretDownloadFilenameHelper.resolveFilenameAndMime(
            url = "https://example.com/videos/clip",
            contentDisposition = "attachment; filename=video",
            mimeType = "video/webm"
        )
        assertEquals("video.webm", filename)
        assertEquals("video/webm", mime)
    }

    @Test
    fun testCase5_normalPdfDownload() {
        val (filename, mime) = SecretDownloadFilenameHelper.resolveFilenameAndMime(
            url = "https://example.com/docs/annual_report",
            contentDisposition = "attachment; filename=annual_report.pdf",
            mimeType = "application/pdf"
        )
        assertEquals("annual_report.pdf", filename)
        assertEquals("application/pdf", mime)
    }

    @Test
    fun testCase6_normalZipDownload() {
        val (filename, mime) = SecretDownloadFilenameHelper.resolveFilenameAndMime(
            url = "https://example.com/downloads/archive.zip",
            contentDisposition = "attachment; filename=archive.zip",
            mimeType = "application/zip"
        )
        assertEquals("archive.zip", filename)
        assertEquals("application/zip", mime)
    }

    @Test
    fun testCase7_existingExtensionNoDuplicate() {
        val (filename, mime) = SecretDownloadFilenameHelper.resolveFilenameAndMime(
            url = "https://example.com/files/movie.mp4",
            contentDisposition = "",
            mimeType = "video/mp4"
        )
        assertEquals("movie.mp4", filename)
        assertEquals("video/mp4", mime)
    }

    @Test
    fun testCase8_matroskaVideoWithoutExtension() {
        val (filename, mime) = SecretDownloadFilenameHelper.resolveFilenameAndMime(
            url = "https://example.com/dl",
            contentDisposition = "attachment; filename=trailer",
            mimeType = "video/x-matroska"
        )
        assertEquals("trailer.mkv", filename)
        assertEquals("video/x-matroska", mime)
    }

    @Test
    fun testCase9_opusAudioWithoutExtension() {
        val (filename, mime) = SecretDownloadFilenameHelper.resolveFilenameAndMime(
            url = "https://example.com/dl",
            contentDisposition = "attachment; filename=voice",
            mimeType = "audio/opus"
        )
        assertEquals("voice.opus", filename)
        assertEquals("audio/opus", mime)
    }

    @Test
    fun testCase10_quicktimeVideoWithoutExtension() {
        val (filename, mime) = SecretDownloadFilenameHelper.resolveFilenameAndMime(
            url = "https://example.com/dl",
            contentDisposition = "attachment; filename=clip",
            mimeType = "video/quicktime"
        )
        assertEquals("clip.mov", filename)
        assertEquals("video/quicktime", mime)
    }

    @Test
    fun testCase11_rfc5987Utf8Filename() {
        val (filename, mime) = SecretDownloadFilenameHelper.resolveFilenameAndMime(
            url = "https://example.com/dl",
            contentDisposition = "attachment; filename*=UTF-8''encoded%20movie.mp4",
            mimeType = "video/mp4"
        )
        assertEquals("encoded movie.mp4", filename)
        assertEquals("video/mp4", mime)
    }

    @Test
    fun testCase12_urlWithExtensionAndOctetStream() {
        val (filename, mime) = SecretDownloadFilenameHelper.resolveFilenameAndMime(
            url = "https://example.com/files/lecture.mp4",
            contentDisposition = "",
            mimeType = "application/octet-stream"
        )
        assertEquals("lecture.mp4", filename)
        assertEquals("video/mp4", mime)
    }
}
