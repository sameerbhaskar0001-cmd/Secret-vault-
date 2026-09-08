import re

with open("app/src/main/java/com/example/SecretBrowserViews.kt", "r") as f:
    content = f.read()

target = """                    val session = createPrivateGeckoSession(
                        ctx = context,
                        tabId = tab.id,
                        initialUrl = tab.url,
                        isDesktopMode = tab.isDesktopMode,
                        onDownloadRequested = { downloadUrl, userAgent, contentDisposition, mimeType, contentLength ->
                            pendingDownload = PendingDownloadData(downloadUrl, userAgent, contentDisposition, mimeType, contentLength)
                        }
                    ) { transform ->"""

replace = """                    val session = createPrivateGeckoSession(
                        ctx = context,
                        tabId = tab.id,
                        initialUrl = tab.url,
                        isDesktopMode = tab.isDesktopMode,
                        onDownloadRequested = { downloadUrl, userAgent, contentDisposition, mimeType, contentLength ->
                            pendingDownload = PendingDownloadData(downloadUrl, userAgent, contentDisposition, mimeType, contentLength)
                        },
                        onCrash = {
                            // Remove session from compose map to trigger recreation in next composition
                            geckoSessions.remove(tab.id)
                        }
                    ) { transform ->"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/SecretBrowserViews.kt", "w") as f:
        f.write(content)
    print("Patched successfully!")
else:
    print("Target not found!")
