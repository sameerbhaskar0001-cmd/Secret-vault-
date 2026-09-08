import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

target = """    val clearHistoryOnExit by viewModel.clearHistoryOnExit.collectAsStateWithLifecycle()"""

replace = """    val clearHistoryOnExit by viewModel.clearHistoryOnExit.collectAsStateWithLifecycle()
    val clearTempOnExit by viewModel.clearTempOnExit.collectAsStateWithLifecycle()"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
        f.write(content)
    print("Patched CalculatorScreen.kt to add clearTempOnExit")
else:
    print("Target not found for clearTempOnExit")
