import re

file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

box_start_str = "                    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {"
box_start_idx = content.find(box_start_str)

if box_start_idx == -1:
    print("Could not find box_start")
    exit(1)

box_end_str = "                    }"
# the box ends right before row ends, let's find the dropdown menu end
panic_end_str = "Spacer(modifier = Modifier.height(32.dp))"
panic_end_idx = content.find(panic_end_str, box_start_idx)
inner_content_end = panic_end_idx + len(panic_end_str)

# inner content starts at:
cat_browser = "                        // Category: BROWSER"
cat_browser_idx = content.find(cat_browser, box_start_idx)
inner_content = content[cat_browser_idx:inner_content_end]

dropdown_close = content.find("                        }", inner_content_end)
box_close = content.find("                    }", dropdown_close)

box_full_text = content[box_start_idx:box_close + len("                    }")]

icon_button_str = """                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, "More Options", tint = TextPrimary, modifier = Modifier.size(19.dp))
                    }"""

# Replace the box with the IconButton
content = content.replace(box_full_text, icon_button_str)

# Now we need to insert the AnimatedVisibility overlay at the bottom, just before `if (showFindInPage && !isHome)`
# Wait, the original overlay was placed near the bottom of SecretBrowserScreen.
# Let's see where SecretBrowserScreen ends.

target_insert = "            if (showFindInPage && !isHome) {"
target_insert_idx = content.find(target_insert)

overlay_str = f"""
        // BROWSER MENU OVERLAY (Command Center style)
        androidx.compose.animation.AnimatedVisibility(
            visible = showMenu,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = {{ it }}) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = {{ it }}) + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {{
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable {{ showMenu = false }}
            ) {{
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .background(LightBg, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .clickable(enabled = false) {{}}
                ) {{
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                    ) {{
                        Spacer(modifier = Modifier.height(12.dp))
                        // Drag Handle
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(BorderColor, RoundedCornerShape(2.dp))
                                .align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // Header Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {{
                            Column {{
                                Text("Command Center", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("Quick navigation & tools", color = TextSecondary, fontSize = 12.sp)
                            }}
                            IconButton(
                                onClick = {{ showMenu = false }},
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(LightCard, CircleShape)
                            ) {{
                                Icon(Icons.Default.Close, "Close Menu", tint = TextPrimary, modifier = Modifier.size(18.dp))
                            }}
                        }}
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {{
{inner_content}
                        }}
                    }}
                }}
            }}
        }}
"""

content = content[:target_insert_idx] + overlay_str + content[target_insert_idx:]

with open(file_path, "w") as f:
    f.write(content)

print("Restored bottom sheet layout")
