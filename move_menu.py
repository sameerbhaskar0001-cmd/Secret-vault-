import re

with open("app/src/main/java/com/example/SecretBrowserViews.kt", "r") as f:
    content = f.read()

# 1. Find the 3 dot icon area
target_3dot = """                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, "More Options", tint = TextPrimary, modifier = Modifier.size(19.dp))
                    }"""

# 2. Extract the overlay content
overlay_start_str = "        // BROWSER MENU OVERLAY (Command Center style)\n"
overlay_start_idx = content.find(overlay_start_str)

if overlay_start_idx == -1:
    print("Could not find overlay start")
    exit(1)

# The end is right before "        if (showMenuClearBrowsingDataDialog) {"
overlay_end_str = "        if (showMenuClearBrowsingDataDialog) {"
overlay_end_idx = content.find(overlay_end_str, overlay_start_idx)

if overlay_end_idx == -1:
    print("Could not find overlay end")
    exit(1)

overlay_full = content[overlay_start_idx:overlay_end_idx]

# Remove the AnimatedVisibility and Card wrapping
# We can just extract everything between `Spacer(modifier = Modifier.height(16.dp))` after the Header Bar, 
# up to the end of Panic Mode Presentation.

# Actually, the user just wants the menu to open from the 3 dots.
# Why don't we put a DropdownMenu at the 3 dots, and place the content inside it?

inner_content_start = overlay_full.find('                        // Category: BROWSER')
inner_content_end = overlay_full.rfind('                        Spacer(modifier = Modifier.height(6.dp))')

if inner_content_start == -1 or inner_content_end == -1:
    print("Could not find inner content bounds")
    exit(1)

inner_content = overlay_full[inner_content_start:inner_content_end]

# Create the DropdownMenu replacement
replacement_3dot = f"""                    Box {{
                        IconButton(
                            onClick = {{ showMenu = true }},
                            modifier = Modifier.size(34.dp)
                        ) {{
                            Icon(Icons.Default.MoreVert, "More Options", tint = TextPrimary, modifier = Modifier.size(19.dp))
                        }}
                        
                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = {{ showMenu = false }},
                            modifier = Modifier
                                .background(LightBg)
                                .widthIn(min = 280.dp, max = 320.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {{
{inner_content}
                        }}
                    }}"""

# Remove the old overlay
content = content[:overlay_start_idx] + content[overlay_end_idx:]

# Insert the replacement at the 3 dot icon
if target_3dot not in content:
    print("Could not find target 3 dot icon")
    exit(1)

content = content.replace(target_3dot, replacement_3dot)

with open("app/src/main/java/com/example/SecretBrowserViews.kt", "w") as f:
    f.write(content)

print("Menu moved successfully")
