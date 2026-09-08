import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for i, line in enumerate(lines):
    if "val clearHistoryOnExit by viewModel.clearHistoryOnExit.collectAsStateWithLifecycle()" in line and i > 11000:
        if i in [11105, 11106, 11115, 11116, 11302, 11303, 11312, 11313]:
            # It's a duplicate, we only want to keep the one that was ALREADY THERE?
            # Actually, I added them. Let's just remove MY additions.
            pass
        else:
            new_lines.append(line)
    elif "val clearTempOnExit by viewModel.clearTempOnExit.collectAsStateWithLifecycle()" in line and i > 11000:
        if i in [11106, 11107, 11116, 11117, 11303, 11304, 11313, 11314]:
            pass
        else:
            new_lines.append(line)
    else:
        new_lines.append(line)
        
with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
    f.writelines(new_lines)
