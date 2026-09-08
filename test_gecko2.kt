package com.example
import org.mozilla.geckoview.WebResponse
fun check(r: WebResponse) {
    val h = r.headers["Content-Type"]
}