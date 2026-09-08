file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("Spacer(modifier = Modifier.height(80.dp))", "Spacer(modifier = Modifier.height(32.dp))\n                                Spacer(modifier = Modifier.windowInsetsBottomHeight(androidx.compose.foundation.layout.WindowInsets.safeDrawing))")

with open(file_path, "w") as f:
    f.write(content)
print("Applied safeDrawing test")
