import re
file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

bad_start = """                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            offset = androidx.compose.ui.unit.DpOffset(0.dp, 12.dp),
                            modifier = Modifier
                                .background(LightBg)
                                .widthIn(min = 280.dp, max = 320.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {"""

good_start = """                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            offset = androidx.compose.ui.unit.DpOffset(0.dp, 12.dp),
                            modifier = Modifier
                                .background(LightBg)
                                .widthIn(min = 280.dp, max = 320.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {"""

content = content.replace(bad_start, good_start)

bad_end = """                                Spacer(modifier = Modifier.windowInsetsBottomHeight(androidx.compose.foundation.layout.WindowInsets.navigationBars))
                            }
                        }
                    }"""

good_end = """                                Spacer(modifier = Modifier.windowInsetsBottomHeight(androidx.compose.foundation.layout.WindowInsets.navigationBars))
                        }
                    }"""

content = content.replace(bad_end, good_end)

with open(file_path, "w") as f:
    f.write(content)
print("Removed inner Column wrapper")
