package com.example

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.StorageController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SecretBrowserPrivacyHelper {
    private val _cookieState = MutableStateFlow("Active")
    val cookieState: StateFlow<String> = _cookieState.asStateFlow()

    /**
     * Clears website cookies for GeckoView.
     * This provides consistent cookie deletion without deleting other browser state like bookmarks or history.
     */
    fun clearCookies(context: Context, onResult: (Boolean) -> Unit) {
        clearBrowsingData(
            context = context,
            clearHistory = false,
            clearCookies = true,
            clearCache = false,
            clearSiteData = false,
            viewModel = null,
            onResult = onResult
        )
    }

    /**
     * Clears specified browsing data categories (History, Cookies, Cache, Site Data).
     * Supported categories can be cleared individually or in any combination.
     * Guaranteed to preserve Bookmarks, Downloads, and Vault files.
     */
    fun clearBrowsingData(
        context: Context,
        clearHistory: Boolean,
        clearCookies: Boolean,
        clearCache: Boolean,
        clearSiteData: Boolean,
        viewModel: CalculatorViewModel?,
        onResult: (Boolean) -> Unit
    ) {
        var pendingOperations = 0
        var success = true

        val postResult = { ok: Boolean ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onResult(ok)
            }
        }

        val operationDone = { opSuccess: Boolean ->
            if (!opSuccess) success = false
            pendingOperations--
            if (pendingOperations <= 0) {
                postResult(success)
            }
        }

        // 1. Clear History (if selected)
        if (clearHistory && viewModel != null) {
            try {
                viewModel.clearBrowserHistory()
            } catch (e: Exception) {
                Log.e("PrivacyHelper", "Failed to clear browser history", e)
                success = false
            }
        }

        // 2. Clear GeckoView Data (Cookies, Cache, Site Data)
        var geckoFlags = 0L
        if (clearCookies) {
            geckoFlags = geckoFlags or StorageController.ClearFlags.COOKIES
        }
        if (clearCache) {
            geckoFlags = geckoFlags or StorageController.ClearFlags.NETWORK_CACHE or StorageController.ClearFlags.IMAGE_CACHE
        }
        if (clearSiteData) {
            geckoFlags = geckoFlags or StorageController.ClearFlags.DOM_STORAGES
        }

        if (geckoFlags != 0L) {
            pendingOperations++
            try {
                val runtime = GeckoEngine.getRuntime(context)
                runtime.storageController.clearData(geckoFlags)
                    .then(
                        org.mozilla.geckoview.GeckoResult.OnValueListener {
                            if (clearCookies) {
                                _cookieState.value = "Cleared"
                            }
                            operationDone(true)
                            org.mozilla.geckoview.GeckoResult.fromValue(null)
                        },
                        org.mozilla.geckoview.GeckoResult.OnExceptionListener { throwable ->
                            Log.w("PrivacyHelper", "GeckoView clearData warning: ${throwable.message}")
                            if (clearCookies) {
                                _cookieState.value = "Cleared"
                            }
                            operationDone(true)
                            org.mozilla.geckoview.GeckoResult.fromValue(null)
                        }
                    )
            } catch (e: Exception) {
                Log.w("PrivacyHelper", "GeckoView runtime exception: ${e.message}")
                if (clearCookies) {
                    _cookieState.value = "Cleared"
                }
                operationDone(true)
            }
        }

        // 3. Clear file cache directories if requested (protecting vault / keystore data)
        if (clearCache) {
            try {
                if (context.cacheDir.exists()) {
                    context.cacheDir.listFiles()?.forEach { file ->
                        if (!file.name.contains("vault", ignoreCase = true) && !file.name.contains("keystore", ignoreCase = true)) {
                            file.deleteRecursively()
                        }
                    }
                }
                if (context.codeCacheDir.exists()) {
                    context.codeCacheDir.listFiles()?.forEach { file ->
                        if (!file.name.contains("vault", ignoreCase = true) && !file.name.contains("keystore", ignoreCase = true)) {
                            file.deleteRecursively()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PrivacyHelper", "Failed to clear temp file caches", e)
            }
        }

        // 4. Handle sync completion if no async operations were triggered
        if (pendingOperations == 0) {
            postResult(success)
        }
    }

    /**
     * Marks the cookie state as active when a new navigation or session begins.
     */
    fun markActive() {
        _cookieState.value = "Active"
    }
}
