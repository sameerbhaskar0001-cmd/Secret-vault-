import re

file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

# 1. Extract the overlay
overlay_start = "        // BROWSER MENU OVERLAY (Command Center style)"
overlay_start_idx = content.find(overlay_start)
if overlay_start_idx == -1:
    print("Could not find overlay start")
    exit(1)

# the overlay ends after Panic Mode
panic_end_str = "Spacer(modifier = Modifier.height(32.dp))\n                        }\n                    }\n                }\n            }\n        }"
# Wait, let's just find the exact closing braces of the overlay.
# Since it's inside AnimatedVisibility, it has:
# AnimatedVisibility { Box { Box { Column { ... } } } } -> 4 braces.

def find_matching_brace(text, start_idx):
    count = 1
    idx = start_idx
    while count > 0 and idx < len(text):
        if text[idx] == '{':
            count += 1
        elif text[idx] == '}':
            count -= 1
        idx += 1
    return idx

anim_vis_start = content.find("AnimatedVisibility(", overlay_start_idx)
anim_vis_brace = content.find("{", anim_vis_start) + 1
overlay_end_idx = find_matching_brace(content, anim_vis_brace)

overlay_full = content[overlay_start_idx:overlay_end_idx]

# Remove it from its current position
content = content[:overlay_start_idx] + content[overlay_end_idx:]

# Insert it before showMenuClearBrowsingDataDialog
target_insert = "        if (showMenuClearBrowsingDataDialog) {"
target_insert_idx = content.find(target_insert)

if target_insert_idx == -1:
    print("Could not find target insert point")
    exit(1)

content = content[:target_insert_idx] + overlay_full + "\n" + content[target_insert_idx:]

with open(file_path, "w") as f:
    f.write(content)

print("Moved overlay down to Box")
