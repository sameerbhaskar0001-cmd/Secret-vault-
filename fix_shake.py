import re
file_path = "app/src/main/java/com/example/CalculatorScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

old_block = """                                    "calculator" -> {
                                        Toast.makeText(context, "Shake to Exit: Vault locked!", Toast.LENGTH_SHORT).show()
                                    }
                                    "home" -> {
                                        Toast.makeText(context, "Shake to Exit: Returning to Home...", Toast.LENGTH_SHORT).show()
                                        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                                            addCategory(android.content.Intent.CATEGORY_HOME)
                                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    }
                                    "close" -> {
                                        Toast.makeText(context, "Shake to Exit: Closing Vault...", Toast.LENGTH_SHORT).show()
                                        (context as? android.app.Activity)?.finishAffinity()
                                    }"""

new_block = """                                    "calculator" -> {
                                        Toast.makeText(context, "Shake to Exit: Vault locked!", Toast.LENGTH_SHORT).show()
                                        // The lockVault() call above already changes the state to the calculator, but let's be explicit if needed
                                    }
                                    "home" -> {
                                        Toast.makeText(context, "Shake to Exit: Returning to Home...", Toast.LENGTH_SHORT).show()
                                        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                                            addCategory(android.content.Intent.CATEGORY_HOME)
                                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    }
                                    "close" -> {
                                        Toast.makeText(context, "Shake to Exit: Closing Vault...", Toast.LENGTH_SHORT).show()
                                        (context as? android.app.Activity)?.finishAffinity()
                                    }"""

if old_block in content:
    content = content.replace(old_block, new_block)
    print("replaced shake in CalculatorScreen")
else:
    print("shake replacement not found")

with open(file_path, "w") as f:
    f.write(content)
