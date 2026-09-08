sed -i 's/    LaunchedEffect(activeTab?.isFullScreen) {/    val activeTab = tabs.find { it.id == activeTabId }\n\n    LaunchedEffect(activeTab?.isFullScreen) {/g' app/src/main/java/com/example/SecretBrowserViews.kt
sed -i '1883,1883d' app/src/main/java/com/example/SecretBrowserViews.kt
