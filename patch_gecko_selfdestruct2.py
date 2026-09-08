import re

with open("app/src/main/java/com/example/GeckoSessionManager.kt", "r") as f:
    content = f.read()

target = """            iterator.remove()
        }
    }"""
    
replace = """            iterator.remove()
        }
        try {
            runtime?.shutdown()
            runtime = null
        } catch (e: Exception) {}
    }"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/GeckoSessionManager.kt", "w") as f:
        f.write(content)
    print("Patched GeckoSessionManager destroyAllSessions")
else:
    print("Target not found in GeckoSessionManager")
