import re
file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

# Remove Priority 2 block from goBack
old_goback = """    val goBack: () -> Unit = {
        if (activeGeckoSession != null && activeTab?.canGoBack == true) {
            // Priority 1: Navigate backward through GeckoView browser session history
            activeGeckoSession.goBack()
        } else if (activeTab != null && SecretBrowserNavigationCheckpointManager.hasValidCheckpoint(activeTab.id, activeTab.url)) {
            // Priority 2: Fallback to pre-redirect / same-tab replacement checkpoint if GeckoView history is lost
            stopLoading()
            val prevUrl = SecretBrowserNavigationCheckpointManager.popValidCheckpoint(activeTab.id, activeTab.url)
            if (prevUrl != null) {
                loadUrl(prevUrl)
            }
        } else if (activeTab?.parentTabId != null && tabs.any { it.id == activeTab.parentTabId }) {"""

new_goback = """    val goBack: () -> Unit = {
        if (activeGeckoSession != null && activeTab?.canGoBack == true) {
            // Priority 1: Navigate backward through GeckoView browser session history
            activeGeckoSession.goBack()
        } else if (activeTab?.parentTabId != null && tabs.any { it.id == activeTab.parentTabId }) {"""

content = content.replace(old_goback, new_goback)

# Remove Priority 2 block from goBackOrExit
old_goback_exit = """        } else if (activeGeckoSession != null && activeTab?.canGoBack == true) {
            // Priority 1: Navigate backward through the GeckoView browser session history
            activeGeckoSession.goBack()
        } else if (activeTab != null && SecretBrowserNavigationCheckpointManager.hasValidCheckpoint(activeTab.id, activeTab.url)) {
            // Priority 2: Fallback to pre-redirect/same-tab replacement checkpoint if GeckoView history is lost
            stopLoading()
            val prevUrl = SecretBrowserNavigationCheckpointManager.popValidCheckpoint(activeTab.id, activeTab.url)
            if (prevUrl != null) {
                loadUrl(prevUrl)
            }
        } else if (activeTab?.parentTabId != null && tabs.any { it.id == activeTab.parentTabId }) {"""

new_goback_exit = """        } else if (activeGeckoSession != null && activeTab?.canGoBack == true) {
            // Priority 1: Navigate backward through the GeckoView browser session history
            activeGeckoSession.goBack()
        } else if (activeTab?.parentTabId != null && tabs.any { it.id == activeTab.parentTabId }) {"""

content = content.replace(old_goback_exit, new_goback_exit)

with open(file_path, "w") as f:
    f.write(content)

