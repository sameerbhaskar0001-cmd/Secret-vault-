import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

target = """        webViewClient = object : android.webkit.WebViewClient() {"""

replace = """        webViewClient = object : android.webkit.WebViewClient() {
            override fun onRenderProcessGone(view: android.webkit.WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                onCrash?.invoke()
                return true
            }"""

if target in content:
    content = content.replace(target, replace, 1) # Only the first one (the main one)
    with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
        f.write(content)
    print("Patched webViewClient successfully!")
else:
    print("Target not found!")
