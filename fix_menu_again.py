import re

file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier
                                .background(LightBg)
                                .widthIn(min = 280.dp, max = 320.dp)
                                .heightIn(max = 500.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {"""

replacement = """                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            offset = androidx.compose.ui.unit.DpOffset(x = 0.dp, y = 4.dp),
                            modifier = Modifier
                                .background(LightBg)
                                .widthIn(min = 280.dp, max = 320.dp)
                                .heightIn(max = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.6f)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {"""

if target in content:
    content = content.replace(target, replacement)
    print("Replaced DropdownMenu")
else:
    print("Could not find target")

# Now let's fix the Panic Mode bottom spacing just to be absolutely sure it doesn't get clipped by the Scroll container.
target_panic = """                            Text("Panic Mode", color = DangerColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))"""

replacement_panic = """                            Text("Panic Mode", color = DangerColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(32.dp))"""

if target_panic in content:
    content = content.replace(target_panic, replacement_panic)
    print("Replaced Panic Mode spacing")
else:
    print("Could not find Panic Mode spacing")


with open(file_path, "w") as f:
    f.write(content)
