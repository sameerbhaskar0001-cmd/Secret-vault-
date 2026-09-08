import re

with open("app/src/main/java/com/example/SecretBrowserViews.kt", "r") as f:
    content = f.read()

target_remember = """    val geckoSessions = remember { mutableStateMapOf<String, org.mozilla.geckoview.GeckoSession>() }"""
replace_remember = """    val geckoSessions = remember { 
        mutableStateMapOf<String, org.mozilla.geckoview.GeckoSession>().apply {
            putAll(GeckoSessionManager.getActiveSessions())
        }
    }"""

target_dispose = """            } else {
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
            }
            GeckoSessionManager.destroyAllSessions()
            geckoSessions.clear()
        }
    }"""

replace_dispose = """            } else {
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

if target_remember in content and target_dispose in content:
    content = content.replace(target_remember, replace_remember)
    content = content.replace(target_dispose, replace_dispose)
    with open("app/src/main/java/com/example/SecretBrowserViews.kt", "w") as f:
        f.write(content)
    print("Patched successfully!")
else:
    print("Target not found!")
