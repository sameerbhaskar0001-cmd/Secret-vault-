import re

with open("app/src/main/java/com/example/SecretBrowserViews.kt", "r") as f:
    content = f.read()

target = """                                                val newSession = createPrivateGeckoSession(
                                                    ctx = context,
                                                    tabId = activeTab.id,
                                                    initialUrl = activeTab.url,
                                                    isDesktopMode = newMode,
                                                    onDownloadRequested = { downloadUrl, userAgent, contentDisposition, mimeType, contentLength ->
                                                        pendingDownload = PendingDownloadData(downloadUrl, userAgent, contentDisposition, mimeType, contentLength)
                                                    }
                                                ) { transform ->"""

replace = """                                                val newSession = createPrivateGeckoSession(
                                                    ctx = context,
                                                    tabId = activeTab.id,
                                                    initialUrl = activeTab.url,
                                                    isDesktopMode = newMode,
                                                    onDownloadRequested = { downloadUrl, userAgent, contentDisposition, mimeType, contentLength ->
                                                        pendingDownload = PendingDownloadData(downloadUrl, userAgent, contentDisposition, mimeType, contentLength)
                                                    },
                                                    onCrash = {
                                                        geckoSessions.remove(activeTab.id)
                                                    }
                                                ) { transform ->"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/SecretBrowserViews.kt", "w") as f:
        f.write(content)
    print("Patched successfully!")
else:
    print("Target not found!")
