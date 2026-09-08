import re

file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

# 1. Find the DropdownMenu block
start_str = "                        androidx.compose.material3.DropdownMenu("
start_idx = content.find(start_str)
if start_idx == -1:
    print("Could not find start of DropdownMenu")
    exit(1)

# Find the start of the DropdownMenu's content (after the { )
content_start_idx = content.find("{", start_idx) + 1
# Find Spacer(modifier = Modifier.height(8.dp)) which we added and can skip
spacer_idx = content.find("Spacer(modifier = Modifier.height(8.dp))", content_start_idx)
if spacer_idx != -1 and spacer_idx < content_start_idx + 100:
    content_start_idx = content.find("Spacer(modifier = Modifier.height(8.dp))", content_start_idx) + len("Spacer(modifier = Modifier.height(8.dp))")

# Find the end of the DropdownMenu block
# We know the Panic Mode ends with Spacer(modifier = Modifier.height(32.dp))
panic_end_str = "Spacer(modifier = Modifier.height(32.dp))"
panic_end_idx = content.find(panic_end_str, content_start_idx)
if panic_end_idx == -1:
    print("Could not find Panic Mode end")
    exit(1)

inner_end_idx = panic_end_idx + len(panic_end_str)

inner_content = content[content_start_idx:inner_end_idx]

# Find the end of the Box wrapping DropdownMenu
dropdown_close_brace = content.find("                        }", inner_end_idx)
box_close_brace = content.find("                    }", dropdown_close_brace + 1)

box_start_str = "                    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {"
box_start_idx = content.rfind(box_start_str, 0, start_idx)

# We will replace the entire Box (which wraps IconButton and DropdownMenu) with just the IconButton
icon_button_str = """                    IconButton(
                        onClick = { showMenu = !showMenu },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (showMenu) Icons.Default.Close else Icons.Default.MoreVert,
                            contentDescription = if (showMenu) "Close Menu" else "More Options",
                            tint = TextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }"""

content = content[:box_start_idx] + icon_button_str + content[box_close_brace + 22:] # 22 is len("                    }") plus newline roughly... wait, let's just do a string replace on the exact text.
