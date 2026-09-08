import re

with open("app/src/main/java/com/example/CalculatorViewModel.kt", "r") as f:
    content = f.read()

target = """    init {
        loadBrowserBookmarks()
        loadBrowserHistory()
        loadDownloads()"""

replace = """    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            loadBrowserBookmarks()
            loadBrowserHistory()
            loadDownloads()
        }"""

if target in content:
    with open("app/src/main/java/com/example/CalculatorViewModel.kt", "w") as f:
        f.write(content.replace(target, replace))
    print("Patched successfully!")
else:
    print("Target not found!")
