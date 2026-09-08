import re

file_path = "app/src/main/java/com/example/SecretBrowserViews.kt"
with open(file_path, "r") as f:
    content = f.read()

# 1. Fix the extra brace
target_brace = """                            Text("Panic Mode", color = DangerColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                    }
                }
            }"""

replacement_brace = """                            Text("Panic Mode", color = DangerColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                    }
                }"""
content = content.replace(target_brace, replacement_brace)

# 2. Fix SecretBrowserHome invocation
bad_home_start = "                    SecretBrowserHome("
bad_home_end = "                    )"
start_idx = content.find(bad_home_start)
end_idx = content.find(bad_home_end, start_idx) + len(bad_home_end)

if start_idx == -1 or end_idx == -1:
    print("Could not find SecretBrowserHome block")
    exit(1)

correct_home = """                    SecretBrowserHome(
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
                        onOpenNewTab = { url -> openNewTab(url) },
                        onSelectActiveTab = { id -> activeTabId = id },
                        onCloseTab = { id -> closeTab(id) },
                        onShowBookmarks = { showBookmarks = true },
                        onShowHistory = { showHistory = true },
                        onShowDownloads = { showDownloads = true },
                        onShowSettings = { showSettings = true },
                        onShowSearchEngineDialog = { showSearchEngineDialog = true },
                        onClearAllData = { showMenuClearBrowsingDataDialog = true }
                    )"""

content = content[:start_idx] + correct_home + content[end_idx:]

with open(file_path, "w") as f:
    f.write(content)

print("Fixed SecretBrowserHome block")
