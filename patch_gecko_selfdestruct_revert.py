import re

with open("app/src/main/java/com/example/GeckoSessionManager.kt", "r") as f:
    content = f.read()

target = """            iterator.remove()
        }
        try {
            runtime?.shutdown()
            runtime = null
        } catch (e: Exception) {}
    }"""
    
replace = """            iterator.remove()
        }
    }"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/GeckoSessionManager.kt", "w") as f:
        f.write(content)
    print("Reverted GeckoSessionManager destroyAllSessions")
else:
    print("Target not found in GeckoSessionManager")
