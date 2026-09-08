import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

target = """    // Sync local activeTabId back to viewModel
    androidx.compose.runtime.LaunchedEffect(activeTabId) {
        viewModel.activeTabId = activeTabId
    }"""

replace = """    // Sync local activeTabId back to viewModel
    androidx.compose.runtime.LaunchedEffect(activeTabId) {
        viewModel.activeTabId = activeTabId
    }
    
    androidx.compose.runtime.LaunchedEffect(tabs.toList(), activeTabId) {
        viewModel.triggerSaveTabs()
    }"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
        f.write(content)
    print("Patched CalculatorScreen.kt")
else:
    print("Target not found in CalculatorScreen")
