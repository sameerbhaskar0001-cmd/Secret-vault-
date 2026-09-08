import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "val currentClearHistoryOnExit by androidx.compose.runtime.rememberUpdatedState(clearHistoryOnExit)" in line:
        continue
    if "val currentClearTempOnExit by androidx.compose.runtime.rememberUpdatedState(clearTempOnExit)" in line:
        continue
    new_lines.append(line)
        
with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
    f.writelines(new_lines)
