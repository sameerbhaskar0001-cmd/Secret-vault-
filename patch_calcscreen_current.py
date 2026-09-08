import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

content = content.replace("if (currentClearTempOnExit)", "if (clearTempOnExit)")
content = content.replace("if (currentClearHistoryOnExit)", "if (clearHistoryOnExit)")

with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
    f.write(content)
print("Patched CalculatorScreen current variables")
