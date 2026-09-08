import re

file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

# 1. Extract the inner content of the Command Center
start_marker = "                        // Category: BROWSER"
end_marker = "                        // Panic Mode Presentation"

start_idx = content.find(start_marker)
end_idx = content.find(end_marker, start_idx)

if start_idx == -1 or end_idx == -1:
    print("Could not find inner content bounds")
    exit(1)

inner_content = content[start_idx:end_idx]

# 2. Extract Panic Mode click logic and convert it
panic_logic_start = content.find("try {", end_idx)
panic_logic_end = content.find("onPanic()", panic_logic_start) + len("onPanic()")

panic_logic = content[panic_logic_start:panic_logic_end]

panic_mode_ui = f"""                        // Panic Mode Presentation
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {{
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(DangerColor, CircleShape)
                                    .clickable {{
                                        showMenu = false
                                        {panic_logic}
                                    }},
                                contentAlignment = Alignment.Center
                            ) {{
                                Icon(Icons.Default.Warning, contentDescription = "Panic Mode", tint = Color.White, modifier = Modifier.size(24.dp))
                            }}
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Panic Mode", color = DangerColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }}"""

# 3. Create the new DropdownMenu to replace the 3-dot icon
dropdown_menu_code = f"""                    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {{
                        IconButton(
                            onClick = {{ showMenu = !showMenu }},
                            modifier = Modifier.size(34.dp)
                        ) {{
                            Icon(
                                imageVector = if (showMenu) Icons.Default.Close else Icons.Default.MoreVert,
                                contentDescription = if (showMenu) "Close Menu" else "More Options",
                                tint = TextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }}
                        
                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = {{ showMenu = false }},
                            modifier = Modifier
                                .background(LightBg)
                                .widthIn(min = 280.dp, max = 320.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {{
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 500.dp) // Adjust height to prevent clipping
                                    .verticalScroll(rememberScrollState())
                            ) {{
                                Spacer(modifier = Modifier.height(8.dp))
{inner_content}
{panic_mode_ui}
                                Spacer(modifier = Modifier.height(8.dp))
                            }}
                        }}
                    }}"""

# 4. Replace the old 3-dot icon with the new Box containing IconButton + DropdownMenu
old_icon_button = """                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, "More Options", tint = TextPrimary, modifier = Modifier.size(19.dp))
                    }"""

if old_icon_button not in content:
    print("Could not find old_icon_button")
    exit(1)

content = content.replace(old_icon_button, dropdown_menu_code)

# 5. Remove the old AnimatedVisibility overlay
# It starts at:
overlay_start_marker = "        // BROWSER MENU OVERLAY (Command Center style)"
overlay_start_idx = content.find(overlay_start_marker)
if overlay_start_idx == -1:
    print("Could not find overlay start")
    exit(1)

# We know the overlay ends just before:
overlay_end_marker = "        if (showMenuClearBrowsingDataDialog) {"
overlay_end_idx = content.find(overlay_end_marker, overlay_start_idx)

if overlay_end_idx == -1:
    print("Could not find overlay end")
    exit(1)

content = content[:overlay_start_idx] + content[overlay_end_idx:]

with open(file_path, "w") as f:
    f.write(content)

print("Conversion complete")
