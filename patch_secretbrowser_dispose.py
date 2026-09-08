import re

with open("app/src/main/java/com/example/SecretBrowserViews.kt", "r") as f:
    content = f.read()

target = """    val currentClearHistoryOnExit by androidx.compose.runtime.rememberUpdatedState(clearHistoryOnExit)
    val currentClearTempOnExit by androidx.compose.runtime.rememberUpdatedState(clearTempOnExit)
    DisposableEffect(Unit) {
        onDispose {
            if (currentClearTempOnExit) {
                try {
                    SecretBrowserSecureDelete.cleanTemporaryUploadsDirectory(context, secure = true)
                    SecretBrowserSecureDelete.cleanStaleTemporaryRemnants(context, secure = true)
                } catch (e: Exception) {
                    android.util.Log.e("SecureDelete", "Exit cleanup failed", e)
                }
            }
            if (currentClearHistoryOnExit) {
                viewModel.clearBrowserHistory()
                clearAllBrowsingData(context, tabs, webViews)
            } else {
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
                // Do NOT destroy GeckoSessions on screen exit to preserve tab state and history.
                // GeckoSessions will be reused via GeckoSessionManager when the user returns.
                // We only clear the local composition map.
                geckoSessions.values.forEach { it.setActive(false) }
            }
            if (currentClearHistoryOnExit) {
                GeckoSessionManager.destroyAllSessions()
            }
            geckoSessions.clear()
        }
    }"""

replace = """    val currentClearHistoryOnExit by androidx.compose.runtime.rememberUpdatedState(clearHistoryOnExit)
    val currentClearTempOnExit by androidx.compose.runtime.rememberUpdatedState(clearTempOnExit)
    DisposableEffect(Unit) {
        onDispose {
            if (currentClearTempOnExit) {
                try {
                    SecretBrowserSecureDelete.cleanTemporaryUploadsDirectory(context, secure = true)
                    SecretBrowserSecureDelete.cleanStaleTemporaryRemnants(context, secure = true)
                } catch (e: Exception) {
                    android.util.Log.e("SecureDelete", "Exit cleanup failed", e)
                }
            }
            if (currentClearHistoryOnExit) {
                viewModel.clearBrowserHistory()
                clearAllBrowsingData(context, tabs, webViews)
                GeckoSessionManager.destroyAllSessions()
            } else {
                webViews.values.forEach { webView ->
                    try {
                        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                        webView.destroy()
                    } catch (e: Exception) {}
                }
                webViews.clear()
                geckoSessions.values.forEach { it.setActive(false) }
            }
            geckoSessions.clear()
        }
    }"""

if target in content:
    content = content.replace(target, replace)
    print("Patched SecretBrowserViews dispose")
else:
    print("Target not found in SecretBrowserViews")

with open("app/src/main/java/com/example/SecretBrowserViews.kt", "w") as f:
    f.write(content)
