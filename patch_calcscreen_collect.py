import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

target = """    val currentClearHistoryOnExit by androidx.compose.runtime.rememberUpdatedState(clearHistoryOnExit)
    val currentClearTempOnExit by androidx.compose.runtime.rememberUpdatedState(clearTempOnExit)"""
    
replace = """    val clearHistoryOnExit by viewModel.clearHistoryOnExit.collectAsStateWithLifecycle()
    val clearTempOnExit by viewModel.clearTempOnExit.collectAsStateWithLifecycle()
    val currentClearHistoryOnExit by androidx.compose.runtime.rememberUpdatedState(clearHistoryOnExit)
    val currentClearTempOnExit by androidx.compose.runtime.rememberUpdatedState(clearTempOnExit)"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
        f.write(content)
    print("Patched CalculatorScreen collections")
else:
    print("Target not found in CalculatorScreen")
