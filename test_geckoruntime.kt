import org.mozilla.geckoview.GeckoRuntime

fun checkMemoryMethods() {
    val runtime = GeckoRuntime.getDefault(null)
    // reflection check
    val methods = GeckoRuntime::class.java.methods
    methods.forEach {
        println(it.name)
    }
}
