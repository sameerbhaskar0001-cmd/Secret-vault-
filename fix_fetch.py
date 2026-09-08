file_path = "app/src/main/java/com/example/GeckoDownloadEngine.kt"
with open(file_path, "r") as f:
    lines = f.readlines()

new_lines = []
for i, line in enumerate(lines):
    if i == 46:  # line 47
        new_lines.append(line)
        new_lines.append("                android.os.Handler(android.os.Looper.getMainLooper()).post {\n")
        new_lines.append("                    try {\n")
    elif i >= 47 and i <= 59:
        new_lines.append("    " + line) # indent 4 spaces
    elif i == 60: # line 61
        new_lines.append("    " + line)
        new_lines.append("                    } catch (e: Exception) {\n")
        new_lines.append("                        continuation.resumeWithException(e)\n")
        new_lines.append("                    }\n")
        new_lines.append("                }\n")
    else:
        new_lines.append(line)

with open(file_path, "w") as f:
    f.writelines(new_lines)

print("Fix applied to python list")
