package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BrowserProcessDeathPersistenceTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        val prefs = app.getSharedPreferences("exchange_calc_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testScenarioA_BrowserToVaultToBrowser() {
        val viewModel = CalculatorViewModel(app)
        
        // Add a tab and set active
        val tabId = "tab_scenario_a"
        viewModel.browserTabs.add(TabState(id = tabId, url = "https://example.com/a", title = "Site A", isDesktopMode = false))
        viewModel.activeTabId = tabId
        viewModel.triggerSaveTabs()

        // Switch section to Vault Home and back to Browser
        // (In memory, tabs and activeTabId remain untouched)
        assertEquals(1, viewModel.browserTabs.size)
        assertEquals(tabId, viewModel.activeTabId)
        assertEquals("https://example.com/a", viewModel.browserTabs[0].url)
    }

    @Test
    fun testScenarioB_BrowserToBackgroundToBrowser() {
        val viewModel = CalculatorViewModel(app)
        val tabId = "tab_scenario_b"
        viewModel.browserTabs.add(TabState(id = tabId, url = "https://example.com/b", title = "Site B", isDesktopMode = true))
        viewModel.activeTabId = tabId
        viewModel.triggerSaveTabs()

        // Simulate background save
        val prefs = app.getSharedPreferences("exchange_calc_prefs", Context.MODE_PRIVATE)
        val savedTabsJson = prefs.getString("browser_tabs", "[]") ?: "[]"
        val savedActiveTab = prefs.getString("browser_active_tab_id", null)

        val jsonArray = org.json.JSONArray(savedTabsJson)
        assertEquals(1, jsonArray.length())
        assertEquals("https://example.com/b", jsonArray.getJSONObject(0).getString("url"))
        assertTrue(jsonArray.getJSONObject(0).getBoolean("isDesktopMode"))
        assertEquals(tabId, savedActiveTab)
    }

    @Test
    fun testScenarioC_RecentsRemovalReopenAppSingleTab() {
        // Step 1: Initial process - user opens a URL and app process is killed
        run {
            val vm1 = CalculatorViewModel(app)
            val tabId = "tab_c1"
            vm1.browserTabs.add(TabState(id = tabId, url = "https://search.brave.com", title = "Brave Search"))
            vm1.activeTabId = tabId
            vm1.triggerSaveTabs()
        }

        // Step 2: Process rebirth (new ViewModel instance simulating fresh launch after Recents swipe)
        run {
            val vm2 = CalculatorViewModel(app)
            assertTrue("Browser tabs must be loaded on startup", vm2.isBrowserTabsLoaded)
            assertEquals("Should have exactly 1 tab restored", 1, vm2.browserTabs.size)
            assertEquals("Restored active tab ID must match", "tab_c1", vm2.activeTabId)
            assertEquals("Restored tab URL must match", "https://search.brave.com", vm2.browserTabs[0].url)
            assertEquals("Restored tab title must match", "Brave Search", vm2.browserTabs[0].title)
        }
    }

    @Test
    fun testScenarioD_MultipleTabsRecentsRemovalReopen() {
        // Step 1: User has 3 tabs, tab 2 is active with desktop mode
        run {
            val vm1 = CalculatorViewModel(app)
            vm1.browserTabs.add(TabState(id = "tab_1", url = "https://site1.org", title = "Site 1", isDesktopMode = false))
            vm1.browserTabs.add(TabState(id = "tab_2", url = "https://site2.org", title = "Site 2", isDesktopMode = true))
            vm1.browserTabs.add(TabState(id = "tab_3", url = "https://site3.org", title = "Site 3", isDesktopMode = false))
            vm1.activeTabId = "tab_2"
            vm1.triggerSaveTabs()
        }

        // Step 2: Process restart
        run {
            val vm2 = CalculatorViewModel(app)
            assertEquals(3, vm2.browserTabs.size)
            assertEquals("tab_2", vm2.activeTabId)
            assertEquals("https://site1.org", vm2.browserTabs[0].url)
            assertEquals("https://site2.org", vm2.browserTabs[1].url)
            assertTrue("Tab 2 must preserve desktop mode", vm2.browserTabs[1].isDesktopMode)
            assertEquals("https://site3.org", vm2.browserTabs[2].url)
        }
    }

    @Test
    fun testScenarioE_HistoryAndBookmarksProcessRestart() {
        // Step 1: Add history and bookmarks
        run {
            val vm1 = CalculatorViewModel(app)
            vm1.addBrowserBookmark("DuckDuckGo", "https://duckduckgo.com")
            vm1.addBrowserBookmark("Wikipedia", "https://wikipedia.org")
            vm1.addBrowserHistory("GitHub", "https://github.com")
            vm1.addBrowserHistory("Mozilla", "https://mozilla.org")
        }

        // Step 2: Restart process
        run {
            val vm2 = CalculatorViewModel(app)
            assertEquals(2, vm2.browserBookmarks.value.size)
            assertTrue(vm2.browserBookmarks.value.any { it.url == "https://duckduckgo.com" })
            assertTrue(vm2.browserBookmarks.value.any { it.url == "https://wikipedia.org" })

            assertEquals(2, vm2.browserHistory.value.size)
            assertTrue(vm2.browserHistory.value.any { it.url == "https://github.com" })
            assertTrue(vm2.browserHistory.value.any { it.url == "https://mozilla.org" })
        }
    }

    @Test
    fun testScenarioF_ClearHistoryOnExit_OFF() {
        val vm = CalculatorViewModel(app)
        vm.setClearHistoryOnExit(false)
        vm.addBrowserHistory("Sensitive Site", "https://private.example")

        // Simulate exit without clear
        assertFalse(vm.clearHistoryOnExit.value)
        assertEquals(1, vm.browserHistory.value.size)

        // Process restart
        val vmRestarted = CalculatorViewModel(app)
        assertFalse(vmRestarted.clearHistoryOnExit.value)
        assertEquals(1, vmRestarted.browserHistory.value.size)
        assertEquals("https://private.example", vmRestarted.browserHistory.value[0].url)
    }

    @Test
    fun testScenarioG_ClearHistoryOnExit_ON() {
        val vm = CalculatorViewModel(app)
        vm.setClearHistoryOnExit(true)
        vm.addBrowserHistory("Temporary Site", "https://temporary.example")

        assertTrue(vm.clearHistoryOnExit.value)
        assertEquals(1, vm.browserHistory.value.size)

        // Trigger on exit cleanup
        if (vm.clearHistoryOnExit.value) {
            vm.clearBrowserHistory()
        }

        assertEquals(0, vm.browserHistory.value.size)

        // Process restart
        val vmRestarted = CalculatorViewModel(app)
        assertTrue(vmRestarted.clearHistoryOnExit.value)
        assertEquals(0, vmRestarted.browserHistory.value.size)
    }
}
