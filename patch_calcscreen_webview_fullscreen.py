import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

target1 = """                                    viewModel.browserCustomViewCallback = callback
                                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                                    setSystemBarsVisibility(activity, false)"""

replace1 = """                                    viewModel.browserCustomViewCallback = callback
                                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                                    setSystemBarsVisibility(activity, false)
                                    val index = tabs.indexOfFirst { it.id == tab.id }
                                    if (index != -1) tabs[index] = tabs[index].copy(isFullScreen = true)"""

target2 = """                            viewModel.browserCustomViewCallback = null
                            val activity = context as? android.app.Activity
                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            setSystemBarsVisibility(activity, true)"""

replace2 = """                            viewModel.browserCustomViewCallback = null
                            val activity = context as? android.app.Activity
                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            setSystemBarsVisibility(activity, true)
                            val index = tabs.indexOfFirst { it.id == tab.id }
                            if (index != -1) tabs[index] = tabs[index].copy(isFullScreen = false)"""

if target1 in content:
    content = content.replace(target1, replace1)
if target2 in content:
    content = content.replace(target2, replace2)

with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
    f.write(content)
print("Patched CalculatorScreen WebView fullscreen")
