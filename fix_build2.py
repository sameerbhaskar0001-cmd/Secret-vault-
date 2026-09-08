import re

files_to_fix = [
    "app/src/main/java/com/example/SecretBrowserViews.kt",
    "app/src/main/java/com/example/AboutScreen.kt",
    "app/src/main/java/com/example/CalculatorScreen.kt"
]

for file_path in files_to_fix:
    with open(file_path, "r") as f:
        content = f.read()
    
    content = content.replace("Spacer(modifier = Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars))", "Spacer(modifier = Modifier.fillMaxWidth().navigationBarsPadding())")
    
    with open(file_path, "w") as f:
        f.write(content)

print("Build fixed 2.")
