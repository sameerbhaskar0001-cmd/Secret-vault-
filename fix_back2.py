file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

old_goback_p2 = """        } else if (activeTab != null && SecretBrowserNavigationCheckpointManager.hasValidCheckpoint(activeTab.id, activeTab.url)) {
            // Priority 2: Fallback to pre-redirect / same-tab replacement checkpoint if GeckoView history is lost"""
new_goback_p2 = """        } else if (activeTab != null && SecretBrowserNavigationCheckpointManager.hasValidCheckpoint(activeTab.id, activeTab.url)) {
            // Priority 2: Fallback to pre-redirect / same-tab replacement checkpoint if GeckoView history is lost
            stopLoading()"""
content = content.replace(old_goback_p2, new_goback_p2, 1)

old_goback_p3 = """        } else if (activeTab?.parentTabId != null && tabs.any { it.id == activeTab.parentTabId }) {
            // Priority 3: If this was a popup/child tab with exhausted history, close it and return to parent tab
            closeTab(activeTab.id)"""
new_goback_p3 = """        } else if (activeTab?.parentTabId != null && tabs.any { it.id == activeTab.parentTabId }) {
            // Priority 3: If this was a popup/child tab with exhausted history, close it and return to parent tab
            stopLoading()
            closeTab(activeTab.id)"""
content = content.replace(old_goback_p3, new_goback_p3, 1)

old_goback_p4 = """        } else if (activeTab != null && !isHome && activeTab.url != "home" && activeTab.url.isNotEmpty()) {
            // Priority 4: Return from web page to browser home dashboard
            loadUrl("home")"""
new_goback_p4 = """        } else if (activeTab != null && !isHome && activeTab.url != "home" && activeTab.url.isNotEmpty()) {
            // Priority 4: Return from web page to browser home dashboard
            stopLoading()
            loadUrl("home")"""
content = content.replace(old_goback_p4, new_goback_p4, 1)

old_goback_p5 = """        } else if (isHome && tabs.size > 1 && activeTab != null) {
            // Priority 5: If on home and multiple tabs exist, close tab and switch to remaining tab
            closeTab(activeTab.id)"""
new_goback_p5 = """        } else if (isHome && tabs.size > 1 && activeTab != null) {
            // Priority 5: If on home and multiple tabs exist, close tab and switch to remaining tab
            stopLoading()
            closeTab(activeTab.id)"""
content = content.replace(old_goback_p5, new_goback_p5, 1)

with open(file_path, "w") as f:
    f.write(content)
print("SecretBrowserViews updated.")
