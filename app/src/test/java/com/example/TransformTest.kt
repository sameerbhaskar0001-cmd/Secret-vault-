package com.example

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import org.junit.Test
import org.junit.Assert.*

class TransformTest {
    @Test
    fun testEnterCrash() {
        val transformation = RichTextVisualTransformation(Color.Magenta)
        
        // Simulating the user's steps:
        val inputs = listOf(
            "",
            "\n",
            "<b>hello</b>\n",
            "<b></b><i></i><u></u>\n",
            "<b></b>\n"
        )
        
        for (input in inputs) {
            val annotatedInput = AnnotatedString(input)
            val transformed = transformation.filter(annotatedInput)
            val mapping = transformed.offsetMapping
            
            val tLen = transformed.text.length
            val oLen = input.length
            
            println("Input: '${input.replace("\n", "\\n")}'")
            println("OrigToTrans:")
            for (i in 0..oLen) {
                print("  $i->${mapping.originalToTransformed(i)}")
            }
            println("\nTransToOrig:")
            for (i in 0..tLen) {
                print("  $i->${mapping.transformedToOriginal(i)}")
            }
            println("\n")
        }
    }
}
