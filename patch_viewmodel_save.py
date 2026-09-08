import re

with open("app/src/main/java/com/example/CalculatorViewModel.kt", "r") as f:
    content = f.read()

content = content.replace("    private fun saveBrowserTabs() {", "    fun triggerSaveTabs() { saveBrowserTabs() }\n\n    private fun saveBrowserTabs() {")

with open("app/src/main/java/com/example/CalculatorViewModel.kt", "w") as f:
    f.write(content)
print("Added triggerSaveTabs to ViewModel")
