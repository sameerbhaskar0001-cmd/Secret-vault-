file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

bad_menu_start = """                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            offset = androidx.compose.ui.unit.DpOffset(0.dp, 56.dp),
                            modifier = Modifier
                                .background(LightBg)
                                .widthIn(min = 280.dp, max = 320.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                                Spacer(modifier = Modifier.height(8.dp))
                        // Category: BROWSER"""

good_menu_start = """                        androidx.compose.material3.DropdownMenu(
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
                            Spacer(modifier = Modifier.height(8.dp))
                        // Category: BROWSER"""

content = content.replace(bad_menu_start, good_menu_start)

bad_menu_end = """                        }
                                Spacer(modifier = Modifier.height(32.dp))
                                Spacer(modifier = Modifier.windowInsetsBottomHeight(androidx.compose.foundation.layout.WindowInsets.navigationBars))
                        }"""

good_menu_end = """                        }
                                Spacer(modifier = Modifier.height(80.dp))
                        }"""

content = content.replace(bad_menu_end, good_menu_end)

with open(file_path, "w") as f:
    f.write(content)
print("Applied menu fixes")
