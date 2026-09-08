import re

with open("app/src/main/java/com/example/CalculatorViewModel.kt", "r") as f:
    content = f.read()

# Add isBrowserTabsLoaded
if "var isBrowserTabsLoaded" not in content:
    content = content.replace("val browserTabs = androidx.compose.runtime.mutableStateListOf<com.example.TabState>()", 
                              "val browserTabs = androidx.compose.runtime.mutableStateListOf<com.example.TabState>()\n    var isBrowserTabsLoaded by androidx.compose.runtime.mutableStateOf(false)")

# Replace loadBrowserTabs
target = """    private fun loadBrowserTabs() {
        try {
            val jsonStr = prefs.getString("browser_tabs", "[]") ?: "[]"
            val activeId = prefs.getString("browser_active_tab_id", null)
            val json = org.json.JSONArray(jsonStr)
            val list = mutableListOf<com.example.TabState>()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                list.add(com.example.TabState(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    url = obj.getString("url"),
                    isDesktopMode = obj.optBoolean("isDesktopMode", false)
                ))
            }
            if (list.isNotEmpty()) {
                // Must be done on Main thread for SnapshotStateList
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    browserTabs.clear()
                    browserTabs.addAll(list)
                    activeTabId = activeId ?: list.first().id
                }
            }
        } catch (e: Exception) {}
    }"""

replace = """    private fun loadBrowserTabs() {
        try {
            val jsonStr = prefs.getString("browser_tabs", "[]") ?: "[]"
            val activeId = prefs.getString("browser_active_tab_id", null)
            val json = org.json.JSONArray(jsonStr)
            val list = mutableListOf<com.example.TabState>()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                list.add(com.example.TabState(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    url = obj.getString("url"),
                    isDesktopMode = obj.optBoolean("isDesktopMode", false)
                ))
            }
            // Must be done on Main thread for SnapshotStateList
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                if (list.isNotEmpty()) {
                    browserTabs.clear()
                    browserTabs.addAll(list)
                    activeTabId = activeId ?: list.first().id
                }
                isBrowserTabsLoaded = true
            }
        } catch (e: Exception) {
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                isBrowserTabsLoaded = true
            }
        }
    }"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/CalculatorViewModel.kt", "w") as f:
        f.write(content)
    print("Patched CalculatorViewModel.kt successfully")
else:
    print("Target not found in CalculatorViewModel.kt")
