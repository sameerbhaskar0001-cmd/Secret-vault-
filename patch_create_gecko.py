import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

target = """fun createPrivateGeckoSession(
    ctx: android.content.Context,
    tabId: String,
    initialUrl: String,
    isDesktopMode: Boolean = false,
    onDownloadRequested: ((url: String, userAgent: String, contentDisposition: String, mimeType: String, contentLength: Long) -> Unit)? = null,
    onUpdate: ((TabState) -> TabState) -> Unit
): org.mozilla.geckoview.GeckoSession {
    return GeckoSessionManager.getOrCreateSession(ctx, tabId, initialUrl, isDesktopMode, onDownloadRequested, onUpdate)
}"""

replace = """fun createPrivateGeckoSession(
    ctx: android.content.Context,
    tabId: String,
    initialUrl: String,
    isDesktopMode: Boolean = false,
    onDownloadRequested: ((url: String, userAgent: String, contentDisposition: String, mimeType: String, contentLength: Long) -> Unit)? = null,
    onCrash: (() -> Unit)? = null,
    onUpdate: ((TabState) -> TabState) -> Unit
): org.mozilla.geckoview.GeckoSession {
    return GeckoSessionManager.getOrCreateSession(ctx, tabId, initialUrl, isDesktopMode, onDownloadRequested, onCrash, onUpdate)
}"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
        f.write(content)
    print("Patched successfully!")
else:
    print("Target not found!")
