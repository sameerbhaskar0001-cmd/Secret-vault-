import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

target = """    DisposableEffect(Unit) {
        onDispose {
            if (clearTempOnExit) {
                try {
                    SecretBrowserSecureDelete.cleanTemporaryUploadsDirectory(context, secure = true)
                    SecretBrowserSecureDelete.cleanStaleTemporaryRemnants(context, secure = true)
                } catch (e: Exception) {}
            }
            if (clearHistoryOnExit) {
                viewModel.clearBrowserHistory()
                clearAllBrowsingData(context, tabs, webViews)
                GeckoSessionManager.destroyAllSessions()
                geckoSessions.clear()
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

replace = """    DisposableEffect(Unit) {
        onDispose {
            if (clearTempOnExit) {
                try {
                    SecretBrowserSecureDelete.cleanTemporaryUploadsDirectory(context, secure = true)
                    SecretBrowserSecureDelete.cleanStaleTemporaryRemnants(context, secure = true)
                } catch (e: Exception) {}
            }
            if (clearHistoryOnExit) {
                viewModel.clearBrowserHistory()
                clearAllBrowsingData(context, tabs, webViews)
                GeckoSessionManager.destroyAllSessions()
                geckoSessions.clear()
            } else {
                // Do not destroy GeckoSessionManager sessions when leaving the screen to preserve state.
                geckoSessions.clear()
                // WebViews cannot be persisted outside of their rendering context easily, 
                // but we must clean up to avoid memory leaks. 
                webViews.values.forEach { webView ->
                    try {
                        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                        webView.destroy()
                    } catch (e: Exception) {}
                }
                webViews.clear()
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
