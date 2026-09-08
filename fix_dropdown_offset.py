import re
file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

bad_dropdown = """                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            offset = androidx.compose.ui.unit.DpOffset(0.dp, 12.dp),
                            modifier = Modifier
                                .background(LightBg)
                                .widthIn(min = 280.dp, max = 320.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {"""

good_dropdown = """                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            offset = androidx.compose.ui.unit.DpOffset(0.dp, 56.dp),
                            modifier = Modifier
                                .background(LightBg)
                                .widthIn(min = 280.dp, max = 320.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {"""

content = content.replace(bad_dropdown, good_dropdown)

with open(file_path, "w") as f:
    f.write(content)
print("Updated Dropdown spacing to 56dp")
