import re
file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

bad_dropdown = """                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier
                                .background(LightBg)
                                .widthIn(min = 280.dp, max = 320.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {"""

good_dropdown = """                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            offset = androidx.compose.ui.unit.DpOffset(0.dp, 12.dp),
                            modifier = Modifier
                                .background(LightBg)
                                .widthIn(min = 280.dp, max = 320.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {"""

content = content.replace(bad_dropdown, good_dropdown)

bad_bottom = """                            Text("Panic Mode", color = DangerColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }"""

good_bottom = """                            Text("Panic Mode", color = DangerColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                                Spacer(modifier = Modifier.height(32.dp))
                                Spacer(modifier = Modifier.windowInsetsBottomHeight(androidx.compose.foundation.layout.WindowInsets.navigationBars))
                            }
                        }"""

content = content.replace(bad_bottom, good_bottom)

with open(file_path, "w") as f:
    f.write(content)
print("Updated Dropdown spacing")
