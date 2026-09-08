package com.example

import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import org.junit.Test
import org.junit.Assert.*

class ToggleTest {
    @Test
    fun testToggle() {
        var tf = TextFieldValue("", TextRange(0))
        tf = toggleTag(tf, "<b>", "</b>")
        println("After Bold ON: '${tf.text}' cursor: ${tf.selection.start}")
        tf = toggleTag(tf, "<b>", "</b>")
        println("After Bold OFF: '${tf.text}' cursor: ${tf.selection.start}")
        tf = toggleTag(tf, "<i>", "</i>")
        println("After Italic ON: '${tf.text}' cursor: ${tf.selection.start}")
        tf = toggleTag(tf, "<i>", "</i>")
        println("After Italic OFF: '${tf.text}' cursor: ${tf.selection.start}")
        tf = toggleTag(tf, "<u>", "</u>")
        println("After Underline ON: '${tf.text}' cursor: ${tf.selection.start}")
        tf = toggleTag(tf, "<u>", "</u>")
        println("After Underline OFF: '${tf.text}' cursor: ${tf.selection.start}")
    }
}
