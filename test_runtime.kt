import org.mozilla.geckoview.GeckoRuntime

fun main() {
    val methods = GeckoRuntime.Delegate::class.java.methods
    for (m in methods) {
        println(m.name)
    }
}
