import re

with open("app/src/main/java/com/example/SecretBrowserViews.kt", "r") as f:
    content = f.read()

target = """                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            setSystemBarsVisibility(activity, true)
                        }
                    ) { transform ->"""

replace = """                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            setSystemBarsVisibility(activity, true)
                        },
                        onCrash = {
                            webViews.remove(tab.id)
                        }
                    ) { transform ->"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/SecretBrowserViews.kt", "w") as f:
        f.write(content)
    print("Patched webView successfully!")
else:
    print("Target not found!")
