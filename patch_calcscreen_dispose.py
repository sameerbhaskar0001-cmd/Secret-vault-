import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

target = """    val currentClearHistoryOnExit by androidx.compose.runtime.rememberUpdatedState(clearHistoryOnExit)
    DisposableEffect(Unit) {
        onDispose {
            if (currentClearHistoryOnExit) {
                viewModel.clearBrowserHistory()
                clearAllBrowsingData(context, tabs, webViews)
            }
            GeckoSessionManager.destroyAllSessions()
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
                } catch (e: Exception) {}
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
                geckoSessions.clear()
            }
        }
    }"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
        f.write(content)
    print("Patched CalculatorScreen.kt dispose successfully")
else:
    print("Target not found in CalculatorScreen.kt")
