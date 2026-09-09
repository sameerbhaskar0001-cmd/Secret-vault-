import re
file_path = "app/src/main/java/com/example/GeckoSessionManager.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("return org.mozilla.geckoview.GeckoResult.fromValue(null)", "return null")

with open(file_path, "w") as f:
    f.write(content)
