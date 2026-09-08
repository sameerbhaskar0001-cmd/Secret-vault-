import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

target1 = """                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            setSystemBarsVisibility(activity, true)
                        }
                    ) { transform ->"""

replace1 = """                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            setSystemBarsVisibility(activity, true)
                        },
                        onCrash = {
                            webViews.remove(tab.id)
                        }
                    ) { transform ->"""

target2 = """                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    setSystemBarsVisibility(activity, true)
                }
            ) { transform ->"""

replace2 = """                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    setSystemBarsVisibility(activity, true)
                },
                onCrash = {
                    webViews.remove(tabId)
                }
            ) { transform ->"""

if target1 in content:
    content = content.replace(target1, replace1)
if target2 in content:
    content = content.replace(target2, replace2)

with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
    f.write(content)
print("Patched webView in calc successfully!")
