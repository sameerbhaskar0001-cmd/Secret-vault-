import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

target = """    val webViews = remember { mutableStateMapOf<String, android.webkit.WebView>() }
    val geckoSessions = remember { mutableStateMapOf<String, org.mozilla.geckoview.GeckoSession>() }"""

replace = """    val webViews = remember { mutableStateMapOf<String, android.webkit.WebView>() }
    val geckoSessions = remember { mutableStateMapOf<String, org.mozilla.geckoview.GeckoSession>() }
    val clearHistoryOnExit by viewModel.clearHistoryOnExit.collectAsStateWithLifecycle()
    val clearTempOnExit by viewModel.clearTempOnExit.collectAsStateWithLifecycle()"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
        f.write(content)
    print("Patched CalculatorScreen missing vars")
else:
    print("Target not found")
