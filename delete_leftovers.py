import re

file_path = "app/src/main/java/com/example/CalculatorViewModel.kt"

with open(file_path, "r") as f:
    content = f.read()

# I will find the exact string to start deleting:
start_del = """                    } else {
                        android.widget.Toast.makeText(context, "Download Failed: $resolvedFinalFilename (Save error)", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }"""

end_del = """        activeDownloadJobs[taskId] = job
    }"""

start_idx = content.find(start_del)
if start_idx == -1:
    print("Could not find start_del")
    exit(1)

# Advance start_idx to the end of start_del
start_idx += len(start_del)

end_idx = content.find(end_del, start_idx)
if end_idx == -1:
    print("Could not find end_del")
    exit(1)

# we just delete between start_idx and end_idx
new_content = content[:start_idx] + "\n        }\n" + content[end_idx:]

with open(file_path, "w") as f:
    f.write(new_content)

print("Deleted leftovers")
