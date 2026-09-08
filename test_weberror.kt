import org.mozilla.geckoview.WebRequestError

fun test(error: WebRequestError) {
    println(error.category)
    println(error.code)
}
