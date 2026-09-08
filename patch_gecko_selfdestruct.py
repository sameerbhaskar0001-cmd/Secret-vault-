import re

with open("app/src/main/java/com/example/GeckoSessionManager.kt", "r") as f:
    content = f.read()

target = """    fun destroyAllSessions() {
        onUpdateCallbacks.clear()
        onDownloadCallbacks.clear()
        onCrashCallbacks.clear()
        val iterator = activeSessions.keys.iterator()
        while (iterator.hasNext()) {
            val tabId = iterator.next()
            val session = activeSessions[tabId]
            if (session != null) {
                try {
                    session.stop()
                    session.navigationDelegate = null
                    session.progressDelegate = null
                    session.contentDelegate = null
                    session.promptDelegate = null
                    session.close()
                } catch (e: Exception) {}
            }
            iterator.remove()
        }
    }"""
    
replace = """    fun destroyAllSessions() {
        onUpdateCallbacks.clear()
        onDownloadCallbacks.clear()
        onCrashCallbacks.clear()
        val iterator = activeSessions.keys.iterator()
        while (iterator.hasNext()) {
            val tabId = iterator.next()
            val session = activeSessions[tabId]
            if (session != null) {
                try {
                    session.stop()
                    session.navigationDelegate = null
                    session.progressDelegate = null
                    session.contentDelegate = null
                    session.promptDelegate = null
                    session.close()
                } catch (e: Exception) {}
            }
            iterator.remove()
        }
        try {
            runtime?.shutdown()
            runtime = null
        } catch (e: Exception) {}
    }"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/GeckoSessionManager.kt", "w") as f:
        f.write(content)
    print("Patched GeckoSessionManager destroyAllSessions")
else:
    print("Target not found in GeckoSessionManager")
