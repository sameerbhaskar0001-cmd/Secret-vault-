package com.example

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.junit.Before

@RunWith(AndroidJUnit4::class)
@Config(instrumentedPackages = ["androidx.loader.content"])
class CrashTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    @Throws(Exception::class)
    fun setUp() {
        ShadowLog.stream = System.out
    }

    @Test
    fun testCrash() {
        try {
            composeTestRule.setContent {
                val app = ApplicationProvider.getApplicationContext<Application>()
                val viewModel = CalculatorViewModel(app)
                // We bypass auth by calling setAuth(true) or similar? 
                // Let's just render the NoteDetailScreen directly!
                
                NoteDetailScreen(
                    noteId = null,
                    viewModel = viewModel,
                    onBack = {}
                )
            }
            
            composeTestRule.waitForIdle()
            
            // Enter title
            composeTestRule.onNodeWithText("Title", substring = true, ignoreCase = true).performTextInput("Test Title")
            composeTestRule.waitForIdle()
            
            // Click Bold
            composeTestRule.onNodeWithText("B").performClick()
            composeTestRule.waitForIdle()
            
            // Click Bold again
            composeTestRule.onNodeWithText("B").performClick()
            composeTestRule.waitForIdle()
            
            // Click Italic
            composeTestRule.onNodeWithText("I").performClick()
            composeTestRule.waitForIdle()
            // Click Italic again
            composeTestRule.onNodeWithText("I").performClick()
            composeTestRule.waitForIdle()
            
            // Click Underline
            composeTestRule.onNodeWithText("U").performClick()
            composeTestRule.waitForIdle()
            // Click Underline again
            composeTestRule.onNodeWithText("U").performClick()
            composeTestRule.waitForIdle()
            
            // Focus and Press Enter on the note content
            composeTestRule.onNodeWithText("Start typing", substring = true, ignoreCase = true).performClick()
            composeTestRule.onNodeWithText("Start typing", substring = true, ignoreCase = true).performTextInput("\n")
            
            composeTestRule.waitForIdle()
            
            println("Test finished without crashing!")
        } catch (e: Throwable) {
            println("CRASH CAUGHT: ${e.javaClass.name}")
            e.printStackTrace()
            throw e
        }
    }
}
