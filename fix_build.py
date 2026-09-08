import re

# Fix MainAppActivity.kt
file_path = "app/src/main/java/com/example/MainAppActivity.kt"
with open(file_path, "r") as f:
    content = f.read()

if "import androidx.activity.enableEdgeToEdge" not in content:
    content = content.replace("import androidx.activity.ComponentActivity", "import androidx.activity.ComponentActivity\nimport androidx.activity.enableEdgeToEdge")
    # if ComponentActivity is not imported, just add it at the top
    if "import androidx.activity.enableEdgeToEdge" not in content:
        content = content.replace("package com.example", "package com.example\n\nimport androidx.activity.enableEdgeToEdge")

content = content.replace("androidx.activity.enableEdgeToEdge()", "enableEdgeToEdge()")

with open(file_path, "w") as f:
    f.write(content)

# Fix Spacers
files_to_fix = [
    "app/src/main/java/com/example/SecretBrowserViews.kt",
    "app/src/main/java/com/example/AboutScreen.kt",
    "app/src/main/java/com/example/CalculatorScreen.kt"
]

for file_path in files_to_fix:
    with open(file_path, "r") as f:
        content = f.read()
    
    content = content.replace("Spacer(modifier = Modifier.windowInsetsBottomHeight(androidx.compose.foundation.layout.WindowInsets.safeDrawing))", "Spacer(modifier = Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars))")
    
    with open(file_path, "w") as f:
        f.write(content)

print("Build fixed.")
