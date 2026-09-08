import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

target = """                    val popupCookieManager = android.webkit.CookieManager.getInstance()
                    popupCookieManager.setAcceptCookie(true)
                    popupCookieManager.setAcceptThirdPartyCookies(this, true)
                    
                    webViewClient = object : android.webkit.WebViewClient() {"""

replace = """                    val popupCookieManager = android.webkit.CookieManager.getInstance()
                    popupCookieManager.setAcceptCookie(true)
                    popupCookieManager.setAcceptThirdPartyCookies(this, true)
                    
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onRenderProcessGone(view: android.webkit.WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                            // If popup crashes, just close it
                            onCreatePopup(null)
                            return true
                        }"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
        f.write(content)
    print("Patched popup webViewClient successfully!")
else:
    print("Target not found!")
