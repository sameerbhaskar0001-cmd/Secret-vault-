import re

with open("app/src/main/java/com/example/CalculatorViewModel.kt", "r") as f:
    content = f.read()

# Add loadBrowserTabs to init
init_target = """    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            loadBrowserBookmarks()
            loadBrowserHistory()
            loadDownloads()
        }"""
        
init_replace = """    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            loadBrowserBookmarks()
            loadBrowserHistory()
            loadDownloads()
            loadBrowserTabs()
        }"""
if init_target in content:
    content = content.replace(init_target, init_replace)

save_tabs_method = """    private fun saveBrowserTabs() {
        try {
            val json = org.json.JSONArray()
            for (tab in browserTabs) {
                val obj = org.json.JSONObject()
                obj.put("id", tab.id)
                obj.put("title", tab.title)
                obj.put("url", tab.url)
                obj.put("isDesktopMode", tab.isDesktopMode)
                json.put(obj)
            }
            prefs.edit().putString("browser_tabs", json.toString())
                .putString("browser_active_tab_id", activeTabId)
                .apply()
        } catch (e: Exception) {}
    }

    private fun loadBrowserTabs() {
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
    }

    private fun saveBrowserHistory(list: List<BrowserHistory>) {"""

content = content.replace("    private fun saveBrowserHistory(list: List<BrowserHistory>) {", save_tabs_method)

with open("app/src/main/java/com/example/CalculatorViewModel.kt", "w") as f:
    f.write(content)
print("Added saveBrowserTabs to ViewModel")

