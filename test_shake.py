import re

file_path = "app/src/main/java/com/example/CalculatorScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

old_shake = """                            if (acceleration > 25.0f) { // Intentional shake
                                viewModel.triggerKeypressEffects(context)
                                viewModel.lockVault()
                                Toast.makeText(context, "Shake to Exit: Vault locked!", Toast.LENGTH_SHORT).show()
                            }"""

new_shake = """                            if (acceleration > 25.0f) { // Intentional shake
                                viewModel.triggerKeypressEffects(context)
                                viewModel.lockVault()
                                when (panicExitActionVal) {
                                    "calculator" -> {
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
                                    }
                                    else -> {
                                        (context as? android.app.Activity)?.finishAffinity()
                                    }
                                }
                            }"""

if old_shake in content:
    content = content.replace(old_shake, new_shake)
    with open(file_path, "w") as f:
        f.write(content)
    print("Shake logic updated.")
else:
    print("Could not find shake logic.")

