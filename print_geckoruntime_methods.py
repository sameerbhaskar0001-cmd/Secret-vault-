import subprocess
try:
    with open("test_gecko2.kt", "w") as f:
        f.write("""package com.example
import org.mozilla.geckoview.WebResponse
fun check(r: WebResponse) {
    val h = r.headers["Content-Type"]
}""")
    subprocess.run(["/opt/gradle/gradle-9.3.1/bin/gradle", ":app:compileDebugKotlin"])
except Exception as e:
    print(e)
