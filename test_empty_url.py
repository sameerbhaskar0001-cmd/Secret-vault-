import re
file_path = "app/src/main/java/com/example/GeckoSessionManager.kt"
with open(file_path, "r") as f:
    content = f.read()

old_if = """                if (!url.isNullOrEmpty() && url != "about:blank" && !url.startsWith("data:")) {"""
new_if = """                if (!url.isNullOrBlank() && url != "about:blank" && !url.startsWith("data:")) {"""

if old_if in content:
    content = content.replace(old_if, new_if)
    print("replaced empty url check in manager")
else:
    print("no empty url check in manager")

with open(file_path, "w") as f:
    f.write(content)

