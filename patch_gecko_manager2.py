import re

with open("app/src/main/java/com/example/GeckoSessionManager.kt", "r") as f:
    content = f.read()

target = """            override fun onExternalResponse(s: GeckoSession, response: WebResponse) {"""

replace = """            override fun onFullScreen(s: GeckoSession, fullScreen: Boolean) {
                onUpdate { tab ->
                    tab.copy(isFullScreen = fullScreen)
                }
            }

            override fun onExternalResponse(s: GeckoSession, response: WebResponse) {"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/GeckoSessionManager.kt", "w") as f:
        f.write(content)
    print("Patched GeckoSessionManager for onFullScreen")
else:
    print("Target not found in GeckoSessionManager")
