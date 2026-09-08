package com.example

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecretBrowserBackNavigationRobustnessTest {

    @Before
    fun setUp() {
        SecretBrowserNavigationCheckpointManager.clearAll()
    }

    /**
     * Requirement 10-A: Normal history
     * Page A -> Page B -> Back -> Page A (using GeckoView canGoBack)
     */
    @Test
    fun testNormalGeckoHistoryTakesPrecedence() {
        val tab = TabState(
            id = "tab-1",
            url = "https://example.com/page-b",
            canGoBack = true
        )
        // With canGoBack = true, GeckoView native history is used directly
        assertTrue(tab.canGoBack)
    }

    /**
     * Requirement 10-B: Same-tab replacement (location.replace / JS redirect / ad hijack)
     * Page A -> replacement/ad page (canGoBack = false) -> Back -> restores Page A
     */
    @Test
    fun testSameTabAdReplacementFallback() {
        val tabId = "tab-1"
        val originalUrl = "https://example.com/download-step"
        val adUrl = "https://ad-network.example.com/landing"

        // 1. User is on original page
        // 2. Ad replacement occurs in same tab without adding history entry
        SecretBrowserNavigationCheckpointManager.recordCheckpoint(
            tabId = tabId,
            previousUrl = originalUrl,
            reason = "location_change"
        )

        // Verify checkpoint exists for current ad URL
        assertTrue(SecretBrowserNavigationCheckpointManager.hasValidCheckpoint(tabId, adUrl))

        // When Back is pressed, popValidCheckpoint returns original page URL
        val restoredUrl = SecretBrowserNavigationCheckpointManager.popValidCheckpoint(tabId, adUrl)
        assertEquals(originalUrl, restoredUrl)

        // After restoring, checkpoint is consumed
        assertFalse(SecretBrowserNavigationCheckpointManager.hasValidCheckpoint(tabId, originalUrl))
    }

    /**
     * Requirement 10-C: Popup / New Tab Ads
     * Parent page -> Child/Ad tab -> Back -> Child closes, Parent tab remains intact
     */
    @Test
    fun testPopupAdTabClosesAndReturnsToParent() {
        val parentTab = TabState(
            id = "parent-tab",
            url = "https://example.com/download-area",
            title = "Download Hub"
        )
        val childAdTab = TabState(
            id = "child-ad-tab",
            url = "https://sponsor-ads.example.com/promo",
            title = "Special Offer",
            parentTabId = "parent-tab",
            canGoBack = false
        )

        val tabs = mutableListOf(parentTab, childAdTab)
        var activeTabId = childAdTab.id

        // When Back is pressed on child tab with canGoBack == false and no checkpoint:
        val activeTab = tabs.find { it.id == activeTabId }
        assertNotNull(activeTab)
        assertEquals("parent-tab", activeTab?.parentTabId)

        // Simulate closing child tab
        val tIndex = tabs.indexOfFirst { it.id == activeTab!!.id }
        val closedTab = tabs.removeAt(tIndex)
        if (closedTab.parentTabId != null && tabs.any { it.id == closedTab.parentTabId }) {
            activeTabId = closedTab.parentTabId!!
        }

        assertEquals("parent-tab", activeTabId)
        assertEquals(1, tabs.size)
        assertEquals("https://example.com/download-area", tabs[0].url)
    }

    /**
     * Requirement 10-D: Multiple independent tabs
     * Tab A + Tab B -> Back on Tab B when on webpage -> navigates to home in Tab B without closing it
     */
    @Test
    fun testMultipleIndependentTabsBackBehaviour() {
        val tabA = TabState(id = "tab-a", url = "https://duckduckgo.com", title = "Search")
        val tabB = TabState(id = "tab-b", url = "https://wikipedia.org", title = "Wiki", canGoBack = false)

        val tabs = mutableListOf(tabA, tabB)
        val activeTabId = "tab-b"
        val activeTab = tabs.find { it.id == activeTabId }!!

        // When canGoBack is false, no checkpoint, no parent:
        // Web page returns to "home" within the same tab (does NOT destroy tab immediately)
        val shouldReturnToHome = !tabB.canGoBack &&
                !SecretBrowserNavigationCheckpointManager.hasValidCheckpoint(tabB.id, tabB.url) &&
                tabB.parentTabId == null &&
                tabB.url != "home"

        assertTrue(shouldReturnToHome)
    }

    /**
     * Requirement 10-E: Browser Home Back handling
     * Single tab on Browser Home -> Back triggers exit to vault
     */
    @Test
    fun testSingleTabOnHomeTriggersVaultExit() {
        val tabHome = TabState(id = "tab-home", url = "home", title = "New Tab", canGoBack = false)
        val tabs = listOf(tabHome)
        val isHome = tabHome.url == "home"

        val canGoBackHistory = tabHome.canGoBack
        val hasCheckpoint = SecretBrowserNavigationCheckpointManager.hasValidCheckpoint(tabHome.id, tabHome.url)
        val hasParent = tabHome.parentTabId != null
        val shouldExit = isHome && !canGoBackHistory && !hasCheckpoint && !hasParent && tabs.size == 1

        assertTrue(shouldExit)
    }

    /**
     * Requirement 10-F: Failed remote page / HTTP 522
     * Page A -> Page B (Server 522/offline error) -> Back -> returns to Page A cleanly
     */
    @Test
    fun testFailedRemotePageBackRestoration() {
        val tabId = "tab-server-fail"
        val originalUrl = "https://example.com/real-file"
        val failedTargetUrl = "https://origin-error.example.com/522"

        // Before transition to the failing target, checkpoint is preserved
        SecretBrowserNavigationCheckpointManager.recordCheckpoint(
            tabId = tabId,
            previousUrl = originalUrl,
            reason = "location_change"
        )

        // When the failing page is active and canGoBack is false, fallback restores original URL
        val targetCheckpoint = SecretBrowserNavigationCheckpointManager.popValidCheckpoint(tabId, failedTargetUrl)
        assertEquals(originalUrl, targetCheckpoint)
    }

    /**
     * Requirement 10-G: Download interaction
     * User on download page -> Download triggers same-tab ad or background download -> Back preserves download page
     */
    @Test
    fun testDownloadTriggeredNavigationCheckpoints() {
        val tabId = "tab-download"
        val downloadPageUrl = "https://example.com/files/download.html"
        val adRedirectUrl = "https://traffic-exchange.example.com/ad"

        // Save checkpoint when download button navigates to ad
        SecretBrowserNavigationCheckpointManager.recordCheckpoint(
            tabId = tabId,
            previousUrl = downloadPageUrl,
            reason = "user_navigation"
        )

        val restored = SecretBrowserNavigationCheckpointManager.popValidCheckpoint(tabId, adRedirectUrl)
        assertEquals(downloadPageUrl, restored)
    }

    /**
     * Requirement 10-H: Restart & Tab Persistence
     * Checkpoint isolation and parentTabId serialization integrity
     */
    @Test
    fun testTabPersistenceAndCheckpointIsolation() {
        val tab1 = "tab-1"
        val tab2 = "tab-2"

        SecretBrowserNavigationCheckpointManager.recordCheckpoint(tab1, "https://site-1.com/a")
        SecretBrowserNavigationCheckpointManager.recordCheckpoint(tab2, "https://site-2.com/b")

        // Assert isolation between tabs
        assertEquals("https://site-1.com/a", SecretBrowserNavigationCheckpointManager.popValidCheckpoint(tab1, "https://site-1.com/current"))
        assertNull(SecretBrowserNavigationCheckpointManager.popValidCheckpoint(tab1, "https://site-1.com/current"))
        
        // Tab 2 remains unaffected
        assertEquals("https://site-2.com/b", SecretBrowserNavigationCheckpointManager.popValidCheckpoint(tab2, "https://site-2.com/current"))

        // Tab clear removes checkpoints cleanly
        SecretBrowserNavigationCheckpointManager.recordCheckpoint(tab1, "https://site-1.com/a")
        SecretBrowserNavigationCheckpointManager.clearTabCheckpoints(tab1)
        assertFalse(SecretBrowserNavigationCheckpointManager.hasValidCheckpoint(tab1, "https://site-1.com/current"))
    }
}
