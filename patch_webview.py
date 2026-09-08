import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

target = """fun createPrivateWebView(
    ctx: android.content.Context,
    tabId: String,
    initialUrl: String,
    savePasswords: Boolean,
    isDesktopMode: Boolean = false,
    onDownloadRequested: (url: String, userAgent: String, contentDisposition: String, mimeType: String, contentLength: Long) -> Unit,
    onCreatePopup: (android.webkit.WebView?) -> Unit,
    onShowCustomView: (android.view.View, android.webkit.WebChromeClient.CustomViewCallback) -> Unit,
    onHideCustomView: () -> Unit,
    onUpdate: ((TabState) -> TabState) -> Unit
): android.webkit.WebView {"""

replace = """fun createPrivateWebView(
    ctx: android.content.Context,
    tabId: String,
    initialUrl: String,
    savePasswords: Boolean,
    isDesktopMode: Boolean = false,
    onDownloadRequested: (url: String, userAgent: String, contentDisposition: String, mimeType: String, contentLength: Long) -> Unit,
    onCreatePopup: (android.webkit.WebView?) -> Unit,
    onShowCustomView: (android.view.View, android.webkit.WebChromeClient.CustomViewCallback) -> Unit,
    onHideCustomView: () -> Unit,
    onCrash: (() -> Unit)? = null,
    onUpdate: ((TabState) -> TabState) -> Unit
): android.webkit.WebView {"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
        f.write(content)
    print("Patched createPrivateWebView successfully!")
else:
    print("Target not found!")
