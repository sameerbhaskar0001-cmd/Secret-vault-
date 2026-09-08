import re

file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

target_insert = "        if (showMenuClearBrowsingDataDialog) {"
target_insert_idx = content.find(target_insert)

if target_insert_idx == -1:
    print("Could not find target insert")
    exit(1)

missing_block = """            if (showFindInPage && !isHome) {
                FindInPageBar(
                    query = findInPageText,
                    onQueryChange = { text ->
                        findInPageText = text
                        performFindInPage(text, true)
                    },
                    currentMatch = findInPageMatchCurrent,
                    totalMatch = findInPageMatchTotal,
                    onPrev = { findPreviousMatch() },
                    onNext = { findNextMatch() },
                    onClose = { closeFindInPage() }
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isHome) {
                    SecretBrowserHome(
                        tabs = tabs,
                        activeTabId = activeTabId ?: "",
                        searchEngine = searchEngine,
                        browserBookmarks = browserBookmarks,
                        browserHistory = browserHistory,
                        onSearch = { query ->
                            var target = query.trim()
                            if (!target.startsWith("http://") && !target.startsWith("https://")) {
                                val encoded = java.net.URLEncoder.encode(target, "UTF-8")
                                target = if (searchEngine == "DuckDuckGo") {
                                    "https://duckduckgo.com/?q=$encoded"
                                } else {
                                    "https://www.google.com/search?q=$encoded"
                                }
                            }
                            loadUrl(target)
                        },
                        onOpenUrl = { url -> loadUrl(url) },
                        onShowBookmarks = { showBookmarks = true },
                        onShowHistory = { showHistory = true },
                        onShowDownloads = { showDownloads = true },
                        onShowSettings = { showSettings = true },
                        onClearData = { showMenuClearBrowsingDataDialog = true },
                        onClearTemp = { showMenuClearTempFilesDialog = true },
                        onShowSiteSecurity = { showSiteSecurityDialog = true },
                        onPanic = {
                            try {
                                SecretBrowserSecureDelete.cleanTemporaryUploadsDirectory(context, secure = true)
                                SecretBrowserSecureDelete.cleanStaleTemporaryRemnants(context, secure = true)
                            } catch (e: Exception) {
                                android.util.Log.e("SecureDelete", "Panic cleanup failed", e)
                            }
                            if (clearHistoryOnExit) {
                                viewModel.clearBrowserHistory()
                            }
                            clearAllBrowsingData(context, tabs)
                            geckoViews.values.forEach { try { (it.parent as? android.view.ViewGroup)?.removeView(it); it.releaseSession() } catch (e: Exception) {} }
                            geckoViews.clear()
                            geckoSessions.clear()
                            onPanic()
                        }
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = !isHome,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut()
                ) {
                    if (activeGeckoSession != null && currentActiveId != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AndroidView(
                                factory = { ctx ->
                                    val gv = org.mozilla.geckoview.GeckoView(ctx)
                                    geckoViews[currentActiveId] = gv
                                    try {
                                        activeGeckoSession.setActive(true)
                                        gv.setSession(activeGeckoSession)
                                    } catch (e: Exception) {
                                        android.util.Log.e("GeckoViewAttach", "Failed in factory", e)
                                    }
                                    gv
                                },
                                update = { geckoView ->
                                    geckoViews[currentActiveId] = geckoView
                                    try {
                                        activeGeckoSession.setActive(true)
                                        if (geckoView.session != activeGeckoSession) {
                                            geckoView.releaseSession()
                                            geckoView.setSession(activeGeckoSession)
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("GeckoViewAttach", "Failed in update", e)
                                    }
                                    geckoView.isSaveEnabled = false
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            androidx.compose.animation.AnimatedVisibility(
                                visible = activeTab?.isLoading == true,
                                enter = androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.fadeOut(),
                                modifier = Modifier.align(Alignment.TopCenter)
                            ) {
                                LinearProgressIndicator(
                                    progress = { (activeTab?.progress ?: 0) / 100f },
                                    modifier = Modifier.fillMaxWidth().height(2.dp),
                                    color = AccentColor,
                                    trackColor = Color.Transparent,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }
        }

"""

content = content[:target_insert_idx] + missing_block + content[target_insert_idx:]

with open(file_path, "w") as f:
    f.write(content)

print("Restored missing layout")
