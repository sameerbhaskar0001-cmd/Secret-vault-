import re

with open("app/src/main/java/com/example/SecretBrowserViews.kt", "r") as f:
    content = f.read()

target = """    LaunchedEffect(tabs.toList(), useGeckoView) {
        if (useGeckoView) {
            // Safe clean up of fallback WebView resources on transition
            webViews.values.forEach { webView ->
                try {
                    webView.stopLoading()
                    (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                    webView.webViewClient = android.webkit.WebViewClient()
                    webView.webChromeClient = android.webkit.WebChromeClient()
                    webView.setDownloadListener(null)
                    webView.clearHistory()
                    webView.clearCache(true)
                    webView.loadUrl("about:blank")
                    webView.destroy()
                } catch (e: Exception) {}
            }
            webViews.clear()
        } else {
            // Safe clean up of primary GeckoView resources on transition
            geckoSessions.keys.forEach { tabId ->
                GeckoSessionManager.removeAndDestroySession(tabId)
            }
            geckoSessions.clear()
        }

        tabs.forEach { tab ->
            if (useGeckoView) {
                if (!geckoSessions.containsKey(tab.id)) {"""

replacement = """    LaunchedEffect(tabs.map { it.id }, activeTabId, useGeckoView) {
        val currentTabIds = tabs.map { it.id }.toSet()

        if (useGeckoView) {
            // Safe clean up of fallback WebView resources on transition
            webViews.values.forEach { webView ->
                try {
                    webView.stopLoading()
                    (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                    webView.webViewClient = android.webkit.WebViewClient()
                    webView.webChromeClient = android.webkit.WebChromeClient()
                    webView.setDownloadListener(null)
                    webView.clearHistory()
                    webView.clearCache(true)
                    webView.loadUrl("about:blank")
                    webView.destroy()
                } catch (e: Exception) {}
            }
            webViews.clear()
        } else {
            // Safe clean up of primary GeckoView resources on transition
            geckoSessions.keys.forEach { tabId ->
                GeckoSessionManager.removeAndDestroySession(tabId)
            }
            geckoSessions.clear()
        }

        // Clean up closed tabs
        val removedGecko = geckoSessions.keys.filter { it !in currentTabIds }
        removedGecko.forEach { GeckoSessionManager.removeAndDestroySession(it) }
        geckoSessions.keys.retainAll(currentTabIds)
        
        val removedWebViews = webViews.keys.filter { it !in currentTabIds }
        removedWebViews.forEach { webViews[it]?.destroy() }
        webViews.keys.retainAll(currentTabIds)

        // Only initialize the active tab to optimize startup and memory
        val activeTab = tabs.find { it.id == activeTabId }
        if (activeTab != null) {
            val tab = activeTab
            if (useGeckoView) {
                if (!geckoSessions.containsKey(tab.id)) {"""

if target in content:
    with open("app/src/main/java/com/example/SecretBrowserViews.kt", "w") as f:
        f.write(content.replace(target, replacement))
    print("Patched successfully!")
else:
    print("Target not found!")

