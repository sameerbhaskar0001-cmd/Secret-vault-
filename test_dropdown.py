import re
file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

bad_column = """                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 500.dp) // Adjust height to prevent clipping
                                    .verticalScroll(rememberScrollState())
                            ) {"""

good_column = """                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {"""

content = content.replace(bad_column, good_column)

with open(file_path, "w") as f:
    f.write(content)
print("Updated Column")
