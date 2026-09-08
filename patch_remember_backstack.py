import re

with open("app/src/main/java/com/example/CalculatorScreen.kt", "r") as f:
    content = f.read()

target = """fun rememberBackStack(initial: String): androidx.compose.runtime.MutableState<String> {
    return object : androidx.compose.runtime.MutableState<String> {
        var backStack by androidx.compose.runtime.mutableStateOf(listOf(initial))
        override var value: String
            get() = backStack.last()
            set(v) {
                if (v == "Home") {
                    backStack = listOf("Home")
                } else if (v == "__BACK__") {
                    if (backStack.size > 1) {
                        backStack = backStack.dropLast(1)
                    }
                } else if (v != backStack.last()) {
                    backStack = backStack + v
                }
            }
        override fun component1() = value
        override fun component2(): (String) -> Unit = { value = it }
    }
}"""

replace = """@androidx.compose.runtime.Composable
fun rememberBackStack(initial: String): androidx.compose.runtime.MutableState<String> {
    val backStackState = androidx.compose.runtime.saveable.rememberSaveable(
        saver = androidx.compose.runtime.saveable.listSaver(
            save = { it.toList() },
            restore = { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(it) } }
        )
    ) {
        androidx.compose.runtime.mutableStateListOf(initial)
    }
    
    return object : androidx.compose.runtime.MutableState<String> {
        override var value: String
            get() = backStackState.lastOrNull() ?: initial
            set(v) {
                if (v == "Home") {
                    backStackState.clear()
                    backStackState.add("Home")
                } else if (v == "__BACK__") {
                    if (backStackState.size > 1) {
                        backStackState.removeAt(backStackState.lastIndex)
                    }
                } else if (v != backStackState.lastOrNull()) {
                    backStackState.add(v)
                }
            }
        override fun component1() = value
        override fun component2(): (String) -> Unit = { value = it }
    }
}"""

if target in content:
    content = content.replace(target, replace)
    with open("app/src/main/java/com/example/CalculatorScreen.kt", "w") as f:
        f.write(content)
    print("Patched rememberBackStack")
else:
    print("Target not found in CalculatorScreen")
