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
                if (!url.isNullOrEmpty() && url != "about:blank" && !url.startsWith("data:")) {
                    val previous = currentMainUrl
                    if (previous.isNotBlank() && previous != "home" && previous != "about:blank" && !previous.startsWith("data:") && previous != url) {
                        SecretBrowserNavigationCheckpointManager.recordCheckpoint(
                            tabId = tabId,
                            previousUrl = previous,
                            reason = "location_change"
                        )
                    }
                    currentMainUrl = url
                    onUpdate { tab ->
                        tab.copy(url = url)
                    }
                }
            }

            override fun onCanGoBack(s: GeckoSession, canGoBack: Boolean) {
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
                val failingUrl = uri ?: ""
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>Secret Browser - Offline</title>
                        <style>
                            :root {
                                --bg: #f7f9fa;
                                --card: #ffffff;
                                --text-p: #1a1a1a;
                                --text-s: #757575;
                                --border: #e0e0e0;
                                --accent: #1E88E5;
                                --accent-hover: #1565C0;
                                --secondary-btn-bg: #f0f2f5;
                                --secondary-btn-text: #424242;
                                --secondary-btn-hover: #e4e6e9;
                                --ambient-bg: rgba(30, 136, 229, 0.08);
                                --icon-tint: #1E88E5;
                            }
                            @media (prefers-color-scheme: dark) {
                                :root {
                                    --bg: #121212;
                                    --card: #1e1e1e;
                                    --text-p: #f5f5f5;
                                    --text-s: #a0a0a0;
                                    --border: #333333;
                                    --accent: #2196F3;
                                    --accent-hover: #42A5F5;
                                    --secondary-btn-bg: #2a2a2a;
                                    --secondary-btn-text: #e0e0e0;
                                    --secondary-btn-hover: #333333;
                                    --ambient-bg: rgba(33, 150, 243, 0.12);
                                    --icon-tint: #42A5F5;
                                }
                            }
                            * { box-sizing: border-box; margin: 0; padding: 0; }
                            body {
                                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                                background-color: var(--bg);
                                color: var(--text-p);
                                display: flex;
                                align-items: center;
                                justify-content: center;
                                min-height: 100vh;
                                padding: 20px;
                            }
                            .card {
                                background: var(--card);
                                border: 1px solid var(--border);
                                border-radius: 24px;
                                padding: 32px 24px;
                                max-width: 390px;
                                width: 100%;
                                text-align: center;
                                box-shadow: 0 8px 30px rgba(0,0,0,0.06);
                            }
                            .icon-box {
                                width: 68px;
                                height: 68px;
                                background: var(--ambient-bg);
                                border-radius: 50%;
                                display: flex;
                                align-items: center;
                                justify-content: center;
                                margin: 0 auto 16px auto;
                            }
                            .icon-box svg {
                                width: 34px;
                                height: 34px;
                                fill: var(--icon-tint);
                            }
                            .greeting {
                                font-size: 20px;
                                font-weight: 800;
                                margin-bottom: 6px;
                                color: var(--text-p);
                            }
                            .title {
                                font-size: 15px;
                                font-weight: 600;
                                margin-bottom: 8px;
                                color: var(--text-p);
                            }
                            p.sub {
                                font-size: 13px;
                                color: var(--text-s);
                                line-height: 1.5;
                                margin-bottom: 20px;
                            }
                            details {
                                margin-bottom: 22px;
                                text-align: left;
                            }
                            summary {
                                font-size: 11.5px;
                                color: var(--text-s);
                                cursor: pointer;
                                user-select: none;
                                padding: 4px 0;
                            }
                            .error-badge {
                                background: var(--bg);
                                border: 1px solid var(--border);
                                border-radius: 12px;
                                padding: 10px 12px;
                                font-size: 11.5px;
                                color: var(--text-s);
                                word-break: break-all;
                                margin-top: 6px;
                            }
                            .btn-group {
                                display: flex;
                                flex-direction: column;
                                gap: 10px;
                            }
                            .btn {
                                display: inline-flex;
                                align-items: center;
                                justify-content: center;
                                font-size: 14px;
                                font-weight: 600;
                                text-decoration: none;
                                padding: 12px 20px;
                                border-radius: 14px;
                                border: none;
                                cursor: pointer;
                                transition: opacity 0.15s ease, background 0.15s ease;
                                width: 100%;
                            }
                            .btn-primary {
                                background: var(--accent);
                                color: #ffffff;
                            }
                            .btn-primary:active {
                                background: var(--accent-hover);
                            }
                            .btn-secondary {
                                background: var(--secondary-btn-bg);
                                color: var(--secondary-btn-text);
                                border: 1px solid var(--border);
                            }
                            .btn-secondary:active {
                                background: var(--secondary-btn-hover);
                            }
                            .btn-runner {
                                background: var(--ambient-bg);
                                color: var(--accent);
                                border: 1.5px solid var(--accent);
                            }
                            .btn-runner:active {
                                opacity: 0.8;
                                transform: scale(0.98);
                            }
                        </style>
                    </head>
                    <body>
                        <div class="card">
                            <div class="icon-box">
                                <svg viewBox="0 0 24 24"><path d="M23.64 7c-.45-.34-4.93-4-11.64-4-1.5 0-2.8.19-3.99.51L12 7.52 16.01 11.53c2.39.4 4.54 1.47 6.09 2.97L23.64 7zM1.41 1.6L0 3.01l2.45 2.45C1.56 5.86 1 6.34.36 7l11.63 14.49 5.3-6.6 3.7 3.7 1.41-1.41L1.41 1.6zM7.17 10.18L4.35 7.36c1.86-.68 4.39-1.07 7.65-1.07.72 0 1.41.03 2.07.08l-2.83 2.83c-.88-.06-1.84-.02-2.87.08l-1.2 1.2z"/></svg>
                            </div>
                            <div class="title">Connection Problem</div>
                            <p class="sub">This page couldn't be reached. Check your internet connection and try again.</p>
                            
                            <details>
                                <summary>Technical Details</summary>
                                <div class="error-badge">
                                    <strong>Status:</strong> ${error.localizedMessage ?: "Connection Refused / Offline"}
                                </div>
                            </details>

                            <div class="btn-group">
                                <a id="retryBtn" class="btn btn-primary" href="$failingUrl">Try Again</a>
                                <a id="homeBtn" class="btn btn-secondary" href="about:blank">Go Home</a>
                            </div>

                            <div style="margin-top: 20px; padding-top: 16px; border-top: 1px dashed var(--border);">
                                <div style="font-size: 12px; color: var(--text-s); margin-bottom: 8px;">Offline Game</div>
                                <a id="runnerBtn" class="btn btn-runner" href="secret://runner">Play Secret Runner</a>
                            </div>
                        </div>
                    </body>
                    </html>
                """.trimIndent()
                val base64 = android.util.Base64.encodeToString(html.toByteArray(Charsets.UTF_8), android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
                return org.mozilla.geckoview.GeckoResult.fromValue("data:text/html;charset=utf-8;base64,$base64")
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
