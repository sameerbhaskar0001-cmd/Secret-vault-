import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

target = """            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1015))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .drawBehind {"""

replace = """            if (activeTab?.isFullScreen != true) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1015))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .drawBehind {"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
        f.write(content)
    print("Patched CalculatorScreen fullscreen")
else:
    print("Target not found in CalculatorScreen")
