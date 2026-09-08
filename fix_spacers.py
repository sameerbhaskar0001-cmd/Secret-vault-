import re

files_to_fix = [
    "app/src/main/java/com/example/SecretBrowserViews.kt",
    "app/src/main/java/com/example/AboutScreen.kt",
    "app/src/main/java/com/example/CalculatorScreen.kt"
]

for file_path in files_to_fix:
    with open(file_path, "r") as f:
        content = f.read()
    
    # Replace Modifier.height(100.dp) with Modifier.windowInsetsBottomHeight(androidx.compose.foundation.layout.WindowInsets.safeDrawing) or similar
    # The user says: "Fixed Spacer/100dp/80dp workaround par depend mat karo. Command Centre, Browser Settings aur related bottom sheets system navigation bar ke upar properly visible hone chahiye."
    
    content = content.replace("Spacer(modifier = Modifier.height(100.dp)) // Safe clearance for floating dock bottom bar", "Spacer(modifier = Modifier.windowInsetsBottomHeight(androidx.compose.foundation.layout.WindowInsets.safeDrawing))")
    content = content.replace("Spacer(modifier = Modifier.height(100.dp))", "Spacer(modifier = Modifier.windowInsetsBottomHeight(androidx.compose.foundation.layout.WindowInsets.safeDrawing))")
    content = content.replace("Spacer(modifier = Modifier.height(100.dp).navigationBarsPadding())", "Spacer(modifier = Modifier.windowInsetsBottomHeight(androidx.compose.foundation.layout.WindowInsets.safeDrawing))")
    
    with open(file_path, "w") as f:
        f.write(content)
    
print("Spacers replaced.")
