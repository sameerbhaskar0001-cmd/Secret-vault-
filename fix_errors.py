file_path = "app/src/main/java/com/example/GeckoSessionManager.kt"
with open(file_path, "r") as f:
    content = f.read()

import re

# Find the onLoadError method
match = re.search(r'override fun onLoadError\([\s\S]*?return org\.mozilla\.geckoview\.GeckoResult\.fromValue\("data:text/html;charset=utf-8;base64,\$base64"\)\s*\}', content)
if match:
    replacement = """            override fun onLoadError(
                s: GeckoSession,
                uri: String?,
                error: org.mozilla.geckoview.WebRequestError
            ): org.mozilla.geckoview.GeckoResult<String>? {
                onUpdate { tab ->
                    tab.copy(isLoading = false, progress = 0)
                }
                // Return null to allow GeckoView to display its native error page,
                // which includes SSL certificate warnings and "Accept the Risk" buttons.
                return org.mozilla.geckoview.GeckoResult.fromValue(null)
            }"""
    content = content.replace(match.group(0), replacement)
    
with open(file_path, "w") as f:
    f.write(content)
print("GeckoSessionManager updated.")
