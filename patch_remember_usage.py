import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

target = "var activeSection by remember { rememberBackStack(\"Home\") }"
replace = "var activeSection by rememberBackStack(\"Home\")"

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
        f.write(content)
    print("Patched rememberBackStack usage")
else:
    print("Target not found")
