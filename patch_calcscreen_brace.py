import re

file_path = "app/src/main/java/com/example/CalculatorViewModel.kt"

with open(file_path, "r") as f:
    content = f.read()

target = """                    } else {
                        android.widget.Toast.makeText(context, "Download Failed: $resolvedFinalFilename (Save error)", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }"""

replacement = """                    } else {
                        android.widget.Toast.makeText(context, "Download Failed: $resolvedFinalFilename (Save error)", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }"""

if target in content:
    content = content.replace(target, replacement)
    with open(file_path, "w") as f:
        f.write(content)
    print("Fixed braces")
else:
    print("Could not find target")
