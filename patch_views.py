import re

with open("app/src/main/java/com/example/SecretBrowserViews.kt", "r") as f:
    content = f.read()

target = """    LaunchedEffect(activeTabId) {
        viewModel.activeTabId = activeTabId
        // Clear Find in Page highlights and close search when tab switches
        showFindInPage = false
        findInPageText = ""
        findInPageMatchCurrent = 0
        findInPageMatchTotal = 0
    }"""

replace = """    LaunchedEffect(activeTabId) {
        viewModel.activeTabId = activeTabId
        // Clear Find in Page highlights and close search when tab switches
        showFindInPage = false
        findInPageText = ""
        findInPageMatchCurrent = 0
        findInPageMatchTotal = 0
    }
    
    LaunchedEffect(tabs.toList(), activeTabId) {
        viewModel.triggerSaveTabs()
    }"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/SecretBrowserViews.kt", "w") as f:
        f.write(content)
    print("Patched SecretBrowserViews.kt")
else:
    print("Target not found in SecretBrowserViews")
