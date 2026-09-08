import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

target1 = """                    val session = createPrivateGeckoSession(
                        ctx = context,
                        tabId = tab.id,
                        initialUrl = tab.url,
                        isDesktopMode = tab.isDesktopMode
                    ) { transform ->"""

replace1 = """                    val session = createPrivateGeckoSession(
                        ctx = context,
                        tabId = tab.id,
                        initialUrl = tab.url,
                        isDesktopMode = tab.isDesktopMode,
                        onCrash = {
                            geckoSessions.remove(tab.id)
                        }
                    ) { transform ->"""

target2 = """            val session = createPrivateGeckoSession(
                ctx = context,
                tabId = tabId,
                initialUrl = url,
                isDesktopMode = false
            ) { transform ->"""

replace2 = """            val session = createPrivateGeckoSession(
                ctx = context,
                tabId = tabId,
                initialUrl = url,
                isDesktopMode = false,
                onCrash = {
                    geckoSessions.remove(tabId)
                }
            ) { transform ->"""

target3 = """                                    val newSession = createPrivateGeckoSession(
                                        ctx = context,
                                        tabId = activeTab.id,
                                        initialUrl = activeTab.url,
                                        isDesktopMode = newMode
                                    ) { transform ->"""

replace3 = """                                    val newSession = createPrivateGeckoSession(
                                        ctx = context,
                                        tabId = activeTab.id,
                                        initialUrl = activeTab.url,
                                        isDesktopMode = newMode,
                                        onCrash = {
                                            geckoSessions.remove(activeTab.id)
                                        }
                                    ) { transform ->"""

if target1 in content:
    content = content.replace(target1, replace1)
if target2 in content:
    content = content.replace(target2, replace2)
if target3 in content:
    content = content.replace(target3, replace3)

with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
    f.write(content)
print("Patched successfully!")

