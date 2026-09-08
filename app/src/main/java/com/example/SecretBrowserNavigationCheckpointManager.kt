package com.example

import java.util.concurrent.ConcurrentHashMap
import java.util.ArrayDeque

/**
 * Lightweight navigation checkpoint record for same-tab redirects, location.replace,
 * and ad replacement fallbacks when native GeckoView session history is exhausted.
 */
data class BrowserTabCheckpoint(
    val tabId: String,
    val previousUrl: String,
    val previousTitle: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String = "navigation"
)

object SecretBrowserNavigationCheckpointManager {
    private val tabCheckpoints = ConcurrentHashMap<String, ArrayDeque<BrowserTabCheckpoint>>()
    private const val MAX_CHECKPOINTS_PER_TAB = 5
    private const val CHECKPOINT_MAX_AGE_MS = 15 * 60 * 1000L // 15 minutes

    fun recordCheckpoint(
        tabId: String,
        previousUrl: String,
        previousTitle: String? = null,
        reason: String = "navigation"
    ) {
        val cleanUrl = previousUrl.trim()
        if (cleanUrl.isBlank() || cleanUrl == "home" || cleanUrl == "about:blank" || cleanUrl.startsWith("secret://") || cleanUrl.startsWith("data:")) {
            return
        }
        val deque = tabCheckpoints.getOrPut(tabId) { ArrayDeque() }
        synchronized(deque) {
            if (deque.isNotEmpty() && deque.peekFirst()?.previousUrl == cleanUrl) {
                return
            }
            deque.addFirst(
                BrowserTabCheckpoint(
                    tabId = tabId,
                    previousUrl = cleanUrl,
                    previousTitle = previousTitle,
                    timestamp = System.currentTimeMillis(),
                    reason = reason
                )
            )
            while (deque.size > MAX_CHECKPOINTS_PER_TAB) {
                deque.removeLast()
            }
        }
    }

    fun hasValidCheckpoint(tabId: String, currentUrl: String?): Boolean {
        val deque = tabCheckpoints[tabId] ?: return false
        synchronized(deque) {
            val now = System.currentTimeMillis()
            val valid = deque.firstOrNull { cp ->
                cp.previousUrl.isNotBlank() &&
                cp.previousUrl != "home" &&
                cp.previousUrl != "about:blank" &&
                !cp.previousUrl.startsWith("data:") &&
                cp.previousUrl != currentUrl &&
                (now - cp.timestamp) <= CHECKPOINT_MAX_AGE_MS
            }
            return valid != null
        }
    }

    fun popValidCheckpoint(tabId: String, currentUrl: String?): String? {
        val deque = tabCheckpoints[tabId] ?: return null
        synchronized(deque) {
            val now = System.currentTimeMillis()
            while (deque.isNotEmpty()) {
                val candidate = deque.removeFirst()
                if (candidate.previousUrl.isNotBlank() &&
                    candidate.previousUrl != "home" &&
                    candidate.previousUrl != "about:blank" &&
                    !candidate.previousUrl.startsWith("data:") &&
                    candidate.previousUrl != currentUrl &&
                    (now - candidate.timestamp) <= CHECKPOINT_MAX_AGE_MS
                ) {
                    return candidate.previousUrl
                }
            }
            return null
        }
    }

    fun peekValidCheckpoint(tabId: String, currentUrl: String?): String? {
        val deque = tabCheckpoints[tabId] ?: return null
        synchronized(deque) {
            val now = System.currentTimeMillis()
            val candidate = deque.firstOrNull { cp ->
                cp.previousUrl.isNotBlank() &&
                cp.previousUrl != "home" &&
                cp.previousUrl != "about:blank" &&
                !cp.previousUrl.startsWith("data:") &&
                cp.previousUrl != currentUrl &&
                (now - cp.timestamp) <= CHECKPOINT_MAX_AGE_MS
            }
            return candidate?.previousUrl
        }
    }

    fun getCheckpointsForTab(tabId: String): List<BrowserTabCheckpoint> {
        val deque = tabCheckpoints[tabId] ?: return emptyList()
        synchronized(deque) {
            return deque.toList()
        }
    }

    fun clearTabCheckpoints(tabId: String) {
        tabCheckpoints.remove(tabId)
    }

    fun clearAll() {
        tabCheckpoints.clear()
    }
}
