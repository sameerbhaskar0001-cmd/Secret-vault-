import re

file_path = "app/src/main/java/com/example/CalculatorViewModel.kt"

with open(file_path, "r") as f:
    content = f.read()

target = """                    } else {
                        android.widget.Toast.makeText(context, "Download Failed: $resolvedFinalFilename (Save error)", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        activeDownloadJobs[taskId] = job"""

replacement = """                    } else {
                        android.widget.Toast.makeText(context, "Download Failed: $resolvedFinalFilename (Save error)", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    return@launch
                }
                val isPausedByUser = _downloads.value.find { it.id == taskId }?.status == "Paused"
                if (isPausedByUser) {
                    return@launch
                }
                android.util.Log.e("VaultDownload", "Error downloading file $url", e)
                withContext(Dispatchers.Main) {
                    activeDownloadJobs.remove(taskId)
                    val canResumeLater = partFile.exists() && partFile.length() > 0
                    val finalDownloads = _downloads.value.map { task ->
                        if (task.id == taskId) {
                            task.copy(
                                status = if (canResumeLater) "Paused" else "Failed",
                                canResume = canResumeLater
                            )
                        } else task
                    }
                    _downloads.value = finalDownloads
                    saveDownloads(finalDownloads)
                    val rawMsg = e.localizedMessage ?: "Network error"
                    val displayError = if (rawMsg.contains("Verification/Ad page")) {
                        "Ad/Challenge page received instead of media"
                    } else if (rawMsg.contains("403")) {
                        "Download expired or forbidden (403)"
                    } else {
                        rawMsg
                    }
                    android.widget.Toast.makeText(context, "Download Failed: ${url.takeLast(20)} ($displayError)", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                activeDownloadJobs.remove(taskId)
            }
        }
        activeDownloadJobs[taskId] = job"""

if target in content:
    content = content.replace(target, replacement)
    with open(file_path, "w") as f:
        f.write(content)
    print("Fixed try-catch successfully")
else:
    print("Could not find target")
