import re

file_path = "app/src/main/java/com/example/CalculatorViewModel.kt"

with open(file_path, "r") as f:
    content = f.read()

old_toast = """                    val errorText = if (e.localizedMessage?.contains("403") == true) {
                        "Download expired or forbidden (403). Please click download again on the website for a fresh link."
                    } else {
                        "Download error: ${e.localizedMessage ?: "Network error"}"
                    }
                    android.widget.Toast.makeText(context, errorText, android.widget.Toast.LENGTH_LONG).show()"""

new_toast = """                    val rawMsg = e.localizedMessage ?: "Network error"
                    val displayError = if (rawMsg.contains("Verification/Ad page")) {
                        "Ad/Challenge page received instead of media"
                    } else if (rawMsg.contains("403")) {
                        "Download expired or forbidden (403)"
                    } else {
                        rawMsg
                    }
                    android.widget.Toast.makeText(context, "Download Failed: $finalFilename ($displayError)", android.widget.Toast.LENGTH_LONG).show()"""

if old_toast in content:
    content = content.replace(old_toast, new_toast)
    with open(file_path, "w") as f:
        f.write(content)
    print("Patched catch block successfully")
else:
    print("Could not find old_toast")
