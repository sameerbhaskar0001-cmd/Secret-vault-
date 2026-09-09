package com.example

import android.content.Context
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebResponse
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager responsible for handling the lifecycle, instantiation, destruction,
 * and retrieval of GeckoView sessions. This prepares the architecture for
 * future multi-tab support by indexing sessions by tab ID.
 */
object GeckoSessionManager {
    // Thread-safe map holding the active GeckoSession for each tab ID
    private val activeSessions = ConcurrentHashMap<String, GeckoSession>()
    private val sessionCanGoBack = ConcurrentHashMap<String, Boolean>()
    
    // Thread-safe maps for update and download callbacks to prevent stale lambdas and memory leaks
    private val onUpdateCallbacks = ConcurrentHashMap<String, ((TabState) -> TabState) -> Unit>()
    private val onDownloadCallbacks = ConcurrentHashMap<String, ((String, String, String, String, Long, String) -> Unit)>()
    var globalDownloadCallback: ((String, String, String, String, Long, String) -> Unit)? = null
    var onOpenSecretRunner: (() -> Unit)? = null
    var onOpenNewTab: ((String, String?) -> Unit)? = null

    /**
     * Creates a new GeckoSession or returns an existing one for the specified tabId.
     */
    private val onCrashCallbacks = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()

    fun getOrCreateSession(
        context: Context,
        tabId: String,
        initialUrl: String,
        isDesktopMode: Boolean,
        onDownloadRequested: ((url: String, userAgent: String, contentDisposition: String, mimeType: String, contentLength: Long, referrerUrl: String) -> Unit)? = null,
        onCrash: (() -> Unit)? = null,
        onUpdateParam: ((TabState) -> TabState) -> Unit
    ): GeckoSession {
        if (onCrash != null) {
            onCrashCallbacks[tabId] = onCrash
        }
        // ALWAYS update/register the latest callbacks for this tab to avoid stale lambdas and memory leaks
        onUpdateCallbacks[tabId] = onUpdateParam
        if (onDownloadRequested != null) {
            onDownloadCallbacks[tabId] = onDownloadRequested
        }

        // Return existing session if already created and valid
        val existing = activeSessions[tabId]
        if (existing != null) {
            if (existing.isOpen) {
                return existing
            } else {
                removeAndDestroySession(tabId)
            }
        }

        val onUpdate: ((TabState) -> TabState) -> Unit = { transform ->
            if (activeSessions.containsKey(tabId)) {
                onUpdateCallbacks[tabId]?.invoke(transform)
            }
        }

        // Setup GeckoSession settings with native tracking protection + fail-open protection
        val isTrackingEnabled = SecretBrowserTrackingProtection.isGlobalEnabled()
        val settings = GeckoSessionSettings.Builder()
            .userAgentMode(
                if (isDesktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP 
                else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            )
            .viewportMode(
                if (isDesktopMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            )
            .useTrackingProtection(isTrackingEnabled)
            .suspendMediaWhenInactive(true)
            .build()
        
        val session = GeckoSession(settings)
        
        var currentMainUrl = initialUrl

        // Navigation delegate to track URL shifts, navigation state, and intercept tracking requests
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                s: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.AllowOrDeny>? {
                try {
                    val url = request.uri
                    if (url.startsWith("secret://runner") || url == "secret://runner") {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onOpenSecretRunner?.invoke()
                        }
                        return org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.DENY)
                    }
                    if (url == "about:blank" && currentMainUrl != "home" && currentMainUrl != "about:blank" && currentMainUrl.isNotBlank() && sessionCanGoBack[tabId] != true) {
                        return org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.DENY)
                    }
                    if (SecretBrowserTrackingProtection.shouldBlock(url, isMainFrame = true, currentSiteUrl = currentMainUrl)) {
                        SecretBrowserTrackingProtection.onTrackerBlocked?.invoke(tabId, currentMainUrl)
                        return org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.DENY)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GeckoTracking", "Fail-open exception in onLoadRequest", e)
                }
                return org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.ALLOW)
            }

            override fun onSubframeLoadRequest(
                s: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.AllowOrDeny>? {
                try {
                    val url = request.uri
                    if (SecretBrowserTrackingProtection.shouldBlock(url, isMainFrame = false, currentSiteUrl = currentMainUrl)) {
                        SecretBrowserTrackingProtection.onTrackerBlocked?.invoke(tabId, currentMainUrl)
                        return org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.DENY)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GeckoTracking", "Fail-open exception in onSubframeLoadRequest", e)
                }
                return org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.ALLOW)
            }

            override fun onLocationChange(
                s: GeckoSession, 
                url: String?, 
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>
            ) {
                if (!url.isNullOrBlank() && !url.startsWith("data:")) {
                    val previous = currentMainUrl
                    if (!previous.isNullOrBlank() && previous != "home" && previous != "about:blank" && previous != url && !previous.startsWith("data:") && url != "about:blank") {
                        SecretBrowserNavigationCheckpointManager.recordCheckpoint(
                            tabId = tabId,
                            previousUrl = previous,
                            previousTitle = null,
                            reason = "location_change"
                        )
                    }
                    currentMainUrl = url
                    onUpdate { tab ->
                        tab.copy(url = if (url == "about:blank") "home" else url)
                    }
                }
            }

            override fun onCanGoBack(s: GeckoSession, canGoBack: Boolean) {
                sessionCanGoBack[tabId] = canGoBack
                onUpdate { tab ->
                    tab.copy(canGoBack = canGoBack)
                }
            }

            override fun onCanGoForward(s: GeckoSession, canGoForward: Boolean) {
                onUpdate { tab ->
                    tab.copy(canGoForward = canGoForward)
                }
            }

            override fun onNewSession(s: GeckoSession, uri: String): org.mozilla.geckoview.GeckoResult<GeckoSession>? {
                try {
                    val cleanUri = uri.trim()
                    if (cleanUri.isNotBlank() && cleanUri != "about:blank" && !cleanUri.startsWith("javascript:")) {
                        val isBlocked = SecretBrowserTrackingProtection.shouldBlock(cleanUri, isMainFrame = true, currentSiteUrl = currentMainUrl)
                        if (isBlocked) {
                            SecretBrowserTrackingProtection.onTrackerBlocked?.invoke(tabId, currentMainUrl)
                        } else {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                onOpenNewTab?.invoke(cleanUri, tabId)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GeckoSession", "Failed to handle onNewSession", e)
                }
                return org.mozilla.geckoview.GeckoResult.fromValue(null)
            }

                        override fun onLoadError(
                s: GeckoSession,
                uri: String?,
                error: org.mozilla.geckoview.WebRequestError
            ): org.mozilla.geckoview.GeckoResult<String>? {
                onUpdate { tab ->
                    tab.copy(isLoading = false, progress = 0)
                }
                // Return null to allow GeckoView to display its native error page,
                // which includes SSL certificate warnings and "Accept the Risk" buttons.
                return null
            }
        }

        // Progress delegate to track page load loading and progress states
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(s: GeckoSession, progress: Int) {
                onUpdate { tab ->
                    tab.copy(progress = progress, isLoading = progress < 100)
                }
            }

            override fun onPageStart(s: GeckoSession, url: String) {
                onUpdate { tab ->
                    tab.copy(
                        url = if (url != "about:blank") url else tab.url,
                        isLoading = true,
                        progress = if (tab.progress > 0) tab.progress else 15,
                        blockedCount = 0
                    )
                }
            }

            override fun onPageStop(s: GeckoSession, success: Boolean) {
                onUpdate { tab ->
                    val finalTitle = if (tab.title == "New Tab" || tab.title.isEmpty() || tab.title == "about:blank") {
                        try {
                            val host = java.net.URL(tab.url).host.removePrefix("www.")
                            if (host.isNotEmpty()) host else "New Tab"
                        } catch (e: Exception) {
                            "New Tab"
                        }
                    } else {
                        tab.title
                    }
                    tab.copy(isLoading = false, progress = 100, title = finalTitle)
                }
            }
        }

        // Content delegate to handle page title updates
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(s: GeckoSession, title: String?) {
                onUpdate { tab ->
                    val cleanTitle = if (!title.isNullOrEmpty() && title != "about:blank") {
                        title
                    } else {
                        try {
                            val host = java.net.URL(tab.url).host.removePrefix("www.")
                            if (host.isNotEmpty()) host else "New Tab"
                        } catch (e: Exception) {
                            "New Tab"
                        }
                    }
                    tab.copy(title = cleanTitle)
                }
            }

            override fun onCrash(s: GeckoSession) {
                onUpdate { tab ->
                    tab.copy(isLoading = false, progress = 0)
                }
                // Safely detach/destroy the failed session
                try {
                    s.stop()
                } catch (e: Exception) {}
                try {
                    s.navigationDelegate = null
                    s.progressDelegate = null
                    s.contentDelegate = null
                    s.promptDelegate = null
                    s.close()
                } catch (e: Exception) {}
                activeSessions.remove(tabId)
                
                // Notify the UI to recreate the session
                onCrashCallbacks[tabId]?.invoke()
            }

            override fun onFullScreen(s: GeckoSession, fullScreen: Boolean) {
                onUpdate { tab ->
                    tab.copy(isFullScreen = fullScreen)
                }
            }

            override fun onExternalResponse(s: GeckoSession, response: WebResponse) {
                val url = response.uri ?: ""
                val headers = response.headers
                val contentDisposition = headers["Content-Disposition"] ?: headers["content-disposition"] ?: headers["Content-disposition"] ?: ""
                val mimeType = headers["Content-Type"] ?: headers["content-type"] ?: headers["Content-type"] ?: ""
                val contentLength = (headers["Content-Length"] ?: headers["content-length"] ?: headers["Content-length"])?.toLongOrNull() ?: 0L
                val isDesktop = s.settings.userAgentMode == GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                val userAgent = if (isDesktop) "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" else "Mozilla/5.0 (Android 14; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0"
                val referrerUrl = currentMainUrl
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onDownloadCallbacks[tabId]?.invoke(url, userAgent, contentDisposition, mimeType, contentLength, referrerUrl)
                    globalDownloadCallback?.invoke(url, userAgent, contentDisposition, mimeType, contentLength, referrerUrl)
                }
            }
        }

        session.promptDelegate = object : org.mozilla.geckoview.GeckoSession.PromptDelegate {
            override fun onFilePrompt(
                s: org.mozilla.geckoview.GeckoSession,
                prompt: org.mozilla.geckoview.GeckoSession.PromptDelegate.FilePrompt
            ): org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse>? {
                val mimes = prompt.mimeTypes?.toList() ?: emptyList()
                val isMultiple = prompt.type == org.mozilla.geckoview.GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
                val uploadType = VaultBrowserIntegration.determineUploadType(mimes)
                
                val result = org.mozilla.geckoview.GeckoResult<org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse>()
                val promptContext = context.applicationContext ?: context
                
                VaultBrowserIntegration.activeUploadRequest = BrowserUploadRequest(
                    mimeTypes = mimes,
                    isMultiple = isMultiple,
                    uploadType = uploadType,
                    onResult = { uris ->
                        try {
                            if (uris != null && uris.isNotEmpty()) {
                                if (isMultiple) {
                                    result.complete(prompt.confirm(promptContext, uris.toTypedArray()))
                                } else {
                                    result.complete(prompt.confirm(promptContext, uris[0]))
                                }
                            } else {
                                result.complete(prompt.dismiss())
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("GeckoSessionManager", "Error completing file prompt", e)
                            try {
                                result.complete(prompt.dismiss())
                            } catch (ex: Exception) {}
                        }
                    }
                )
                return result
            }
        }

        // Open the session within the global GeckoRuntime
        val runtime = GeckoEngine.getRuntime(context)
        session.open(runtime)

        // Load the initial URI if appropriate
        if (initialUrl != "home" && initialUrl != "about:blank" && initialUrl.isNotEmpty()) {
            session.loadUri(initialUrl)
        }

        // Track and cache the session
        activeSessions[tabId] = session
        return session
    }

    /**
     * Retrieve an active session for the specified tab ID.
     */
    fun getSession(tabId: String): GeckoSession? {
        return activeSessions[tabId]
    }

    /**
     * Check if a session exists for the specified tab ID.
     */
    fun hasSession(tabId: String): Boolean {
        return activeSessions.containsKey(tabId)
    }

    /**
     * Dynamically updates desktop/mobile mode on an active session without recreating or reloading blank pages.
     */
    fun setDesktopMode(tabId: String, isDesktop: Boolean) {
        val session = activeSessions[tabId] ?: return
        try {
            session.settings.userAgentMode = if (isDesktop) {
                GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            } else {
                GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            }
            session.settings.viewportMode = if (isDesktop) {
                GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
            } else {
                GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            }
            session.reload()
        } catch (e: Exception) {
            android.util.Log.e("GeckoSessionManager", "Error toggling desktop mode", e)
        }
    }

    /**
     * Safely closes and removes the session associated with the given tab ID.
     */
    fun removeAndDestroySession(tabId: String) {
        SecretBrowserNavigationCheckpointManager.clearTabCheckpoints(tabId)
        sessionCanGoBack.remove(tabId)
        val session = activeSessions.remove(tabId)
        onUpdateCallbacks.remove(tabId)
        onDownloadCallbacks.remove(tabId)
        if (session != null) {
            try {
                session.stop()
            } catch (e: Exception) {}
            try {
                session.navigationDelegate = null
                session.progressDelegate = null
                session.contentDelegate = null
                session.promptDelegate = null
            } catch (e: Exception) {}
            try {
                session.close()
            } catch (e: Exception) {
                // Ignore safe destruction errors
            }
        }
    }

    /**
     * Safely destroys and closes all active sessions (for clean shutdown on browser exit).
     */
    fun destroyAllSessions() {
        SecretBrowserNavigationCheckpointManager.clearAll()
        sessionCanGoBack.clear()
        onUpdateCallbacks.clear()
        onDownloadCallbacks.clear()
        onCrashCallbacks.clear()
        val iterator = activeSessions.keys.iterator()
        while (iterator.hasNext()) {
            val tabId = iterator.next()
            val session = activeSessions[tabId]
            if (session != null) {
                try {
                    session.stop()
                } catch (e: Exception) {}
                try {
                    session.navigationDelegate = null
                    session.progressDelegate = null
                    session.contentDelegate = null
                    session.promptDelegate = null
                } catch (e: Exception) {}
                try {
                    session.close()
                } catch (e: Exception) {
                    // Ignore safe destruction errors
                }
            }
            iterator.remove()
        }
    }

    /**
     * Returns an immutable copy of the active sessions (for suspension or tracking).
     */
    fun getActiveSessions(): Map<String, GeckoSession> {
        return activeSessions.toMap()
    }
}
