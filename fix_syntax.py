file_path = "app/src/main/java/com/example/GeckoDownloadEngine.kt"
with open(file_path, "r") as f:
    content = f.read()

bad_block = """            val response = suspendCoroutine<WebResponse> { continuation ->
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
                }
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            val statusCode = response.statusCode"""

good_block = """            val response = suspendCoroutine<WebResponse> { continuation ->
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

content = content.replace(bad_block, good_block)
with open(file_path, "w") as f:
    f.write(content)
print("Syntax fixed")
