import re

with open("app/src/main/java/com/example/SecretBrowserViews.kt", "r") as f:
    content = f.read()

target1 = """    LaunchedEffect(tabs.toList(), activeTabId) {
        viewModel.triggerSaveTabs()
    }"""
    
replace1 = """    LaunchedEffect(tabs.toList(), activeTabId) {
        if (viewModel.isBrowserTabsLoaded) {
            viewModel.triggerSaveTabs()
        }
    }"""

target2 = """    LaunchedEffect(Unit) {
        if (tabs.isEmpty()) {
            openNewTab("home")
        }
    }"""

replace2 = """    LaunchedEffect(viewModel.isBrowserTabsLoaded) {
        if (viewModel.isBrowserTabsLoaded && tabs.isEmpty()) {
            openNewTab("home")
        }
    }"""

if target1 in content:
    content = content.replace(target1, replace1)
    print("Patched target1 in SecretBrowserViews")
else:
    print("Target1 not found in SecretBrowserViews")

if target2 in content:
    content = content.replace(target2, replace2)
    print("Patched target2 in SecretBrowserViews")
else:
    print("Target2 not found in SecretBrowserViews")

with open("app/src/main/java/com/example/SecretBrowserViews.kt", "w") as f:
    f.write(content)

