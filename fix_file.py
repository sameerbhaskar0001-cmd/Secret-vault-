file_path = "app/src/main/java/com/example/GeckoDownloadEngine.kt"
with open(file_path, "r") as f:
    content = f.read()
import re
# We just need to replace exactly from `val response = suspendCoroutine` up to `val statusCode = response.statusCode`
pattern = re.compile(r'val response = suspendCoroutine.*?val statusCode = response\.statusCode', re.DOTALL)
replacement = """val response = suspendCoroutine<WebResponse> { continuation ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        val result = executor.fetch(request, GeckoWebExecutor.FETCH_FLAGS_NONE)
                        result.accept(
                            { res -> 
                                if (res != null) {
                                    continuation.resume(res)
                                } else {
                                    continuation.resumeWithException(Exception("Received null WebResponse"))
                                }
                            },
                            { ex ->
                                continuation.resumeWithException(ex ?: Exception("Unknown GeckoWebExecutor error"))
                            }
                        )
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            }

            val statusCode = response.statusCode"""
new_content = pattern.sub(replacement, content)
with open(file_path, "w") as f:
    f.write(new_content)
print("Regex replace applied")
