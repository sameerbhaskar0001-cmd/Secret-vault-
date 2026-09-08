file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

# Fix PrivateBrowserSection
content = content.replace(
    "Column(modifier = Modifier.fillMaxSize().let { if (activeTab?.isFullScreen == true) it else it.statusBarsPadding() }) {",
    "Column(modifier = Modifier.fillMaxSize().let { if (activeTab?.isFullScreen == true) it else it.statusBarsPadding().navigationBarsPadding() }) {"
)

# Fix SecretBrowserSettingsDashboard
# It already has statusBarsPadding and navigationBarsPadding, but let's add an explicit spacer if needed.
# Actually it already has Spacer(32.dp).

# Ensure the DropdownMenu has the safeDrawing spacer. (I already added it in the test_inset.py earlier).

with open(file_path, "w") as f:
    f.write(content)
print("Fix applied")
