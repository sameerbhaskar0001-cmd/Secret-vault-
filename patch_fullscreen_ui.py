import re

with open("app/src/main/java/com/example/SecretBrowserViews.kt", "r") as f:
    content = f.read()

# 1. Patch statusBarsPadding
target_root = """        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {"""
replace_root = """        Column(modifier = Modifier.fillMaxSize().let { if (activeTab?.isFullScreen == true) it else it.statusBarsPadding() }) {"""
content = content.replace(target_root, replace_root)

# 2. Patch Top Bar
target_top_bar = """            // TOP ADDRESS / BAR AREA
            Row("""
replace_top_bar = """            // TOP ADDRESS / BAR AREA
            if (activeTab?.isFullScreen != true) Row("""
content = content.replace(target_top_bar, replace_top_bar)

# 3. Patch Bottom Dock
target_bottom_dock = """            // BOTTOM DOCK NAVIGATION REBUILD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .navigationBarsPadding(),"""
replace_bottom_dock = """            // BOTTOM DOCK NAVIGATION REBUILD
            if (activeTab?.isFullScreen != true) Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .navigationBarsPadding(),"""
content = content.replace(target_bottom_dock, replace_bottom_dock)

with open("app/src/main/java/com/example/SecretBrowserViews.kt", "w") as f:
    f.write(content)
print("Patched fullscreen UI in SecretBrowserViews")
