import re
file_path = "app/src/main/java/com/example/SecretBrowserTrackingProtection.kt"
with open(file_path, "r") as f:
    content = f.read()

old_block = """    fun shouldBlock(url: String?, isMainFrame: Boolean, currentSiteUrl: String? = null): Boolean {
        if (url.isNullOrBlank()) {
            return false
        }"""
new_block = """    fun shouldBlock(url: String?, isMainFrame: Boolean, currentSiteUrl: String? = null): Boolean {
        if (url.isNullOrBlank()) {
            return false
        }
        
        // Never block main frame navigation (ad redirects) to preserve back history and avoid dead ends
        if (isMainFrame) {
            return false
        }"""

if old_block in content:
    content = content.replace(old_block, new_block)
    print("replaced ad logic again")
else:
    print("could not find ad block")

with open(file_path, "w") as f:
    f.write(content)
