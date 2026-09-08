import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

target = """    val overallSecurityRating by viewModel.overallSecurityRating.collectAsStateWithLifecycle()
    val securityItems by viewModel.securityItems.collectAsStateWithLifecycle()"""

replace = """    val overallSecurityRating by viewModel.overallSecurityRating.collectAsStateWithLifecycle()
    val securityItems by viewModel.securityItems.collectAsStateWithLifecycle()
    val clearHistoryOnExit by viewModel.clearHistoryOnExit.collectAsStateWithLifecycle()
    val clearTempOnExit by viewModel.clearTempOnExit.collectAsStateWithLifecycle()"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
        f.write(content)
    print("Patched CalculatorScreen top vars")
else:
    print("Target not found")
