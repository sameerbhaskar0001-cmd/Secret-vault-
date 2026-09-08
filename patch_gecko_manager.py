import re

with open("app/src/main/java/com/example/GeckoSessionManager.kt", "r") as f:
    content = f.read()

target1 = """    fun getOrCreateSession(
        context: Context,
        tabId: String,
        initialUrl: String,
        isDesktopMode: Boolean,
        onDownloadRequested: ((url: String, userAgent: String, contentDisposition: String, mimeType: String, contentLength: Long) -> Unit)? = null,
        onUpdateParam: ((TabState) -> TabState) -> Unit
    ): GeckoSession {"""

replace1 = """    private val onCrashCallbacks = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()

    fun getOrCreateSession(
        context: Context,
        tabId: String,
        initialUrl: String,
        isDesktopMode: Boolean,
        onDownloadRequested: ((url: String, userAgent: String, contentDisposition: String, mimeType: String, contentLength: Long) -> Unit)? = null,
        onCrash: (() -> Unit)? = null,
        onUpdateParam: ((TabState) -> TabState) -> Unit
    ): GeckoSession {
        if (onCrash != null) {
            onCrashCallbacks[tabId] = onCrash
        }"""

if target1 in content:
    content = content.replace(target1, replace1)
    
target2 = """            override fun onExternalResponse(s: GeckoSession, response: WebResponse) {"""

replace2 = """            override fun onCrash(s: GeckoSession) {
                // Safely detach/destroy the failed session
                try {
                    s.stop()
                } catch (e: Exception) {}
                try {
                    s.navigationDelegate = null
                    s.progressDelegate = null
                    s.contentDelegate = null
                    s.promptDelegate = null
                    s.close()
                } catch (e: Exception) {}
                activeSessions.remove(tabId)
                
                // Notify the UI to recreate the session
                onCrashCallbacks[tabId]?.invoke()
            }

            override fun onExternalResponse(s: GeckoSession, response: WebResponse) {"""

if target2 in content:
    content = content.replace(target2, replace2)
    with open("app/src/main/java/com/example/GeckoSessionManager.kt", "w") as f:
        f.write(content)
    print("Patched successfully!")
else:
    print("Target not found!")
