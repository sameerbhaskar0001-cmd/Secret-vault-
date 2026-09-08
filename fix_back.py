file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

# Fix goBack
# We need to remove `stopLoading()` from `goBack` when we are doing `activeGeckoSession.goBack()`
# We should probably remove it from Priority 1.
# Wait, let's look at `goBack`
old_goback = """    val goBack: () -> Unit = {
        stopLoading()
        if (activeGeckoSession != null && activeTab?.canGoBack == true) {
            // Priority 1: Navigate backward through GeckoView browser session history
            activeGeckoSession.goBack()
        }"""
new_goback = """    val goBack: () -> Unit = {
        if (activeGeckoSession != null && activeTab?.canGoBack == true) {
            // Priority 1: Navigate backward through GeckoView browser session history
            activeGeckoSession.goBack()
        }"""
content = content.replace(old_goback, new_goback)

# Also fix `goBackOrExit` Priority 1
old_goback_exit = """        } else if (activeGeckoSession != null && activeTab?.canGoBack == true) {
            // Priority 1: Navigate backward through the GeckoView browser session history
            stopLoading()
            activeGeckoSession.goBack()
        }"""
new_goback_exit = """        } else if (activeGeckoSession != null && activeTab?.canGoBack == true) {
            // Priority 1: Navigate backward through the GeckoView browser session history
            activeGeckoSession.goBack()
        }"""
content = content.replace(old_goback_exit, new_goback_exit)

with open(file_path, "w") as f:
    f.write(content)
print("SecretBrowserViews updated.")
