import re
file_path = "app/src/main/java/com/example/GeckoSessionManager.kt"
with open(file_path, "r") as f:
    content = f.read()

# find onNewSession block and restore the return
old_block = """                } catch (e: Exception) {
                    android.util.Log.e("GeckoSession", "Failed to handle onNewSession", e)
                }
                return null
            }"""
new_block = """                } catch (e: Exception) {
                    android.util.Log.e("GeckoSession", "Failed to handle onNewSession", e)
                }
                return org.mozilla.geckoview.GeckoResult.fromValue(null)
            }"""
if old_block in content:
    content = content.replace(old_block, new_block)
    print("restored onNewSession")
with open(file_path, "w") as f:
    f.write(content)
