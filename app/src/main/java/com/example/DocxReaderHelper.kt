package com.example

import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

object DocxReaderHelper {
    /**
     * Extracts plain text from a .docx file without requiring heavy external dependencies.
     * DOCX files are zip archives containing word/document.xml with text in <w:t> tags.
     */
    fun extractDocxText(file: File): String {
        return try {
            if (!file.exists() || file.length() == 0L) return "Document is empty or not found."
            val stringBuilder = StringBuilder()
            ZipInputStream(FileInputStream(file)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val xmlContent = zis.bufferedReader(Charsets.UTF_8).readText()
                        parseWordXml(xmlContent, stringBuilder)
                        break
                    }
                    entry = zis.nextEntry
                }
            }
            val result = stringBuilder.toString().trim()
            if (result.isEmpty()) {
                "No readable text found in this Word document."
            } else {
                result
            }
        } catch (e: Exception) {
            "Unable to parse Word document: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    private fun parseWordXml(xml: String, out: StringBuilder) {
        val regex = Regex("<w:p[ >]|<w:t[ >]|</w:p>")
        val pEndRegex = Regex("</w:p>")
        val tTagRegex = Regex("<w:t(?: [^>]*)?>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)

        val paragraphs = xml.split("</w:p>")
        for (para in paragraphs) {
            val matches = tTagRegex.findAll(para)
            val paraText = StringBuilder()
            for (match in matches) {
                val rawText = match.groupValues[1]
                val unescaped = rawText
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                paraText.append(unescaped)
            }
            val trimmedPara = paraText.toString()
            if (trimmedPara.isNotBlank()) {
                out.append(trimmedPara).append("\n\n")
            }
        }
    }
}
