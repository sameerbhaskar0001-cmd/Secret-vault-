with open("app/src/main/java/com/example/MainAppActivity.kt", "r") as f:
    c = f.read()
if "androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)" in c:
    c = c.replace("androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)", "androidx.activity.enableEdgeToEdge()")
    with open("app/src/main/java/com/example/MainAppActivity.kt", "w") as f:
        f.write(c)
    print("Replaced setDecorFitsSystemWindows")
