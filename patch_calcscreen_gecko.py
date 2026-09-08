import re

file_path = "app/src/main/java/com/example/CalculatorViewModel.kt"

with open(file_path, "r") as f:
    content = f.read()

# Find the start of the job
start_str = 'val job = viewModelScope.launch(Dispatchers.IO) {'
start_idx = content.find(start_str)

if start_idx == -1:
    print("Could not find start_str")
    exit(1)

# Find the catch block of the launch
catch_str = '            } catch (e: Exception) {'
catch_idx = content.find(catch_str, start_idx)

if catch_idx == -1:
    print("Could not find catch_str")
    exit(1)

# We want to replace the try block inside the job.
# Actually we can replace from start_str up to catch_str.
replacement = """val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val append = isResume && partFile.exists() && partFile.length() > 0
                val result = GeckoDownloadEngine.executeDownload(
                    context = context,
                    url = url,
                    targetFile = partFile,
                    referrerUrl = referrerUrl,
                    append = append
                ) { currentDownloaded, totalLength, currentSpeedStr ->
                    val progressValue = if (totalLength > 0) (currentDownloaded.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f) else 0f
                    val downloadedStr = formatFileSize(currentDownloaded)
                    val totalStr = if (totalLength > 0) formatFileSize(totalLength) else ""
                    var sizeDisplay = if (totalStr.isNotEmpty()) "$downloadedStr / $totalStr" else downloadedStr
                    if (currentSpeedStr.isNotEmpty()) {
                        sizeDisplay += " • $currentSpeedStr"
                    }
                    _downloads.value = _downloads.value.map { task ->
                        if (task.id == taskId) {
                            if (task.status == "Downloading") {
                                task.copy(
                                    progress = progressValue,
                                    downloadedBytes = currentDownloaded,
                                    totalBytes = totalLength,
                                    sizeString = sizeDisplay
                                )
                            } else task
                        } else task
                    }
                }
                
                if (result is GeckoDownloadResult.HtmlChallengeResponse) {
                    throw java.io.IOException(result.message)
                } else if (result is GeckoDownloadResult.ServerError) {
                    throw java.io.IOException(result.message)
                } else if (result is GeckoDownloadResult.Error) {
                    throw java.io.IOException(result.message ?: "Unknown download error", result.cause)
                }
                
                val successResult = result as GeckoDownloadResult.Success
                
                if (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive != true) {
                    return@launch
                }
                val effectiveDownloadMime = if (!SecretDownloadFilenameHelper.isGenericMimeType(successResult.mimeType)) {
                    successResult.mimeType
                } else {
                    resolvedInitialMime
                }
                
                val (resolvedFinalFilename, resolvedFinalMime) = SecretDownloadFilenameHelper.resolveFilenameAndMime(
                    url = url,
                    contentDisposition = contentDisposition,
                    mimeType = effectiveDownloadMime
                )
                
                val downloadHandler = VaultDownloadHandler(context)
                val targetFilePath = downloadHandler.saveDownloadedFile(
                    filename = resolvedFinalFilename,
                    mimeType = resolvedFinalMime,
                    sourceFile = partFile,
                    destination = destination,
                    deviceFileSaver = { fname, mime, src -> saveDownloadedFile(context, fname, mime, src) },
                    vaultFileSaver = { fname, mime, src -> addDownloadedFileToVault(context, fname, mime, src) }
                )
                val success = targetFilePath != null
                
                withContext(Dispatchers.Main) {
                    activeDownloadJobs.remove(taskId)
                    val finalDownloads = _downloads.value.map { task ->
                        if (task.id == taskId) {
                            task.copy(
                                filename = resolvedFinalFilename,
                                progress = 1f,
                                status = if (success) "Completed" else "Failed",
                                mimeType = resolvedFinalMime,
                                filePath = targetFilePath ?: "",
                                canResume = false,
                                sizeString = formatFileSize(successResult.totalRead)
                            )
                        } else task
                    }
                    _downloads.value = finalDownloads
                    saveDownloads(finalDownloads)
                    if (success) {
                        android.widget.Toast.makeText(context, "Download Complete: $resolvedFinalFilename", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, "Download Failed: $resolvedFinalFilename (Save error)", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
"""

new_content = content[:start_idx] + replacement + content[catch_idx:]

with open(file_path, "w") as f:
    f.write(new_content)

print("Patched successfully")
