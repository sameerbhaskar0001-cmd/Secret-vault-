import re

file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

# I need to match the DropdownMenu block accurately
# Let's use a regex to find the start of the DropdownMenu and replace it.

old_menu_start = """                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            offset = androidx.compose.ui.unit.DpOffset(0.dp, 56.dp),
                            modifier = Modifier
                                .background(LightBg)
                                .widthIn(min = 280.dp, max = 320.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Command Center", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { showMenu = false }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Menu", tint = TextPrimary)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))"""

new_menu_start = """                        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                        if (showMenu) {
                            androidx.compose.material3.ModalBottomSheet(
                                onDismissRequest = { showMenu = false },
                                containerColor = LightBg,
                                dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle(color = TextMedium) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = screenHeight * 0.6f)
                                        .padding(horizontal = 16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Command Center", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        IconButton(onClick = { showMenu = false }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Close Menu", tint = TextPrimary)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f, fill = false)
                                            .verticalScroll(rememberScrollState())
                                    ) {"""

if old_menu_start in content:
    content = content.replace(old_menu_start, new_menu_start)
    print("Replaced menu start")
else:
    print("Could not find menu start")

old_menu_end = """                                Spacer(modifier = Modifier.height(32.dp))
                                Spacer(modifier = Modifier.fillMaxWidth().navigationBarsPadding())
                        }
                    }"""

new_menu_end = """                                Spacer(modifier = Modifier.height(32.dp))
                                Spacer(modifier = Modifier.fillMaxWidth().navigationBarsPadding())
                                    }
                                }
                            }
                        }
                    }"""

if old_menu_end in content:
    content = content.replace(old_menu_end, new_menu_end)
    print("Replaced menu end")
else:
    print("Could not find menu end")


with open(file_path, "w") as f:
    f.write(content)

