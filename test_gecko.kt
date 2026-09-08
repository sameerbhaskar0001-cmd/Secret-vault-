import org.mozilla.geckoview.GeckoSession

fun main() {
    val methods = GeckoSession.ContentDelegate::class.java.methods
    for (m in methods) {
        println(m.name)
    }
}
