import re
file_path = "app/src/main/java/com/example/CalculatorScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

old_block = """                transitionSpec = {
                    (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f, animationSpec = tween(300)))
                        .togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.95f, animationSpec = tween(300)))
                },"""
new_block = """                transitionSpec = {
                    if (targetState == "PrivateBrowser" || initialState == "PrivateBrowser" || targetState == "Private Browser" || initialState == "Private Browser") {
                        fadeIn(animationSpec = tween(150)).togetherWith(fadeOut(animationSpec = tween(150)))
                    } else {
                        (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f, animationSpec = tween(300)))
                            .togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.95f, animationSpec = tween(300)))
                    }
                },"""

if old_block in content:
    content = content.replace(old_block, new_block)
    print("replaced transition in CalculatorScreen")
else:
    print("could not find transition")

with open(file_path, "w") as f:
    f.write(content)
