import re
file_path = "app/src/main/java/com/example/GeckoSessionManager.kt"
with open(file_path, "r") as f:
    content = f.read()

old_block = """                    val previous = currentMainUrl
                    if (previous.isNotBlank() && previous != "home" && previous != "about:blank" && !previous.startsWith("data:") && previous != url) {
                        SecretBrowserNavigationCheckpointManager.recordCheckpoint(
                            tabId = tabId,
                            previousUrl = previous,
                            reason = "location_change"
                        )
                    }"""
new_block = """                    val previous = currentMainUrl"""

if old_block in content:
    content = content.replace(old_block, new_block)
    print("replaced checkpoint in GeckoSessionManager")

with open(file_path, "w") as f:
    f.write(content)
