import re

with open("app/src/main/java/com/example/GeckoSessionManager.kt", "r") as f:
    content = f.read()

target = """    fun removeAndDestroySession(tabId: String) {
        onUpdateCallbacks.remove(tabId)
        onDownloadCallbacks.remove(tabId)"""

replace = """    fun removeAndDestroySession(tabId: String) {
        onUpdateCallbacks.remove(tabId)
        onDownloadCallbacks.remove(tabId)
        onCrashCallbacks.remove(tabId)"""

if target in content:
    content = content.replace(target, replace)
    
target2 = """    fun destroyAllSessions() {
        onUpdateCallbacks.clear()
        onDownloadCallbacks.clear()"""

replace2 = """    fun destroyAllSessions() {
        onUpdateCallbacks.clear()
        onDownloadCallbacks.clear()
        onCrashCallbacks.clear()"""

if target2 in content:
    content = content.replace(target2, replace2)
    with open("app/src/main/java/com/example/GeckoSessionManager.kt", "w") as f:
        f.write(content)
    print("Patched successfully!")
else:
    print("Target not found!")
