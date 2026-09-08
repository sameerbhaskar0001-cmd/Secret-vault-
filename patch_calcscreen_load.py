import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

target1 = """    androidx.compose.runtime.LaunchedEffect(tabs.toList(), activeTabId) {
        viewModel.triggerSaveTabs()
    }"""
    
replace1 = """    androidx.compose.runtime.LaunchedEffect(tabs.toList(), activeTabId) {
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
    print("Patched target1")
else:
    print("Target1 not found")

if target2 in content:
    content = content.replace(target2, replace2)
    print("Patched target2")
else:
    print("Target2 not found")

with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
    f.write(content)

