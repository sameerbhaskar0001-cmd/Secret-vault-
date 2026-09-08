package com.example

import android.net.Uri
import android.util.Log
import java.net.URI

/**
 * Isolated architecture for Secret Browser Tracking Protection.
 * Performs entirely local database/rule-based lookup for tracker blocking.
 * Guaranteed local decisions without any external telemetry or DNS queries.
 */
object SecretBrowserTrackingProtection {
    private const val TAG = "TrackingProtection"
    
    // Dynamic preference hooks wired by ViewModel to isolate UI, state, and engine
    var isGlobalEnabled: () -> Boolean = { true }
    var getSiteOverride: (String) -> Boolean? = { null }
    var onTrackerBlocked: ((tabId: String?, currentSiteUrl: String?) -> Unit)? = null

    // 1. Advertising Trackers
    val ADVERTISING_TRACKERS = setOf(
        "doubleclick.net",
        "googleadservices.com",
        "adservice.google.com",
        "adnxs.com",
        "pubmatic.com",
        "rubiconproject.com",
        "adsrvr.org",
        "taboola.com",
        "outbrain.com",
        "adcolony.com",
        "applovin.com",
        "unityads.unity3d.com",
        "amazon-adsystem.com",
        "adsystem.com",
        "popads.net",
        "clickasegura.com",
        "quantserve.com",
        "exponential.com",
        "yieldmanager.com",
        "mynahsterfez.shop",
        "adsterra.com",
        "propellerads.com",
        "exoclick.com",
        "popcash.net",
        "mgid.com",
        "onclickalgo.com",
        "yllix.com",
        "adcash.com",
        "hilltopads.com",
        "ad-maven.com",
        "clickadu.com",
        "ero-advertising.com",
        "juicyads.com",
        "trafficjunky.com",
        "bet365.com",
        "1xbet.com",
        "parimatch.com",
        "vungle.com",
        "inmobi.com",
        "ironSource.com",
        "chartboost.com",
        "media.net",
        "monetag.com",
        "richads.com",
        "admob.com",
        "highcpmrevenuenetwork.com",
        "profitablecpmrate.com",
        "alwingulla.com",
        "poawooptugroo.com",
        "thubanoa.com",
        "whomeeno.com",
        "wherethef.com",
        "onclkds.com",
        "deloton.com",
        "cootlogu.net",
        "gloaphoo.net",
        "ouo.io",
        "ouo.press",
        "directrev.com",
        "ay267.com",
        "luugy.com",
        "adxvip.com",
        "shortenworld.com",
        "shrinkme.io",
        "gplinks.co",
        "droplink.co"
    )

    // 2. Analytics Trackers
    val ANALYTICS_TRACKERS = setOf(
        "google-analytics.com",
        "analytics.google.com",
        "hotjar.com",
        "mixpanel.com",
        "amplitude.com",
        "segment.io",
        "optimizely.com",
        "statcounter.com",
        "googletagmanager.com",
        "scorecardresearch.com",
        "chartbeat.com",
        "crazyegg.com",
        "newrelic.com",
        "intercom.io"
    )

    // 3. Social Trackers
    val SOCIAL_TRACKERS = setOf(
        "connect.facebook.net",
        "platform.twitter.com",
        "platform.instagram.com",
        "snapchat.com/sdk",
        "linkedin.com/count"
    )

    // 4. Known Tracking Domains/Scripts
    val KNOWN_TRACKING_DOMAINS = setOf(
        "criteo.com",
        "ads-twitter.com",
        "pixel.facebook.com",
        "ads.youtube.com"
    )

    /**
     * Evaluates whether a given request URL should be blocked as a tracker.
     * Implements strict fail-open behavior: any error during matching or parsing
     * will default to ALLOW (returning false).
     *
     * Rules:
     * - Main-frame navigation -> ALLOW
     * - First-party assets/APIs for the active site -> ALLOW
     * - Internal browser resources (about:, file:, data:, blob:) -> ALLOW
     * - Match known tracker -> BLOCK
     * - Normal request -> ALLOW
     */
    fun shouldBlock(url: String?, isMainFrame: Boolean, currentSiteUrl: String? = null): Boolean {
        if (url.isNullOrBlank()) {
            return false
        }

        // Check if tracking protection is active globally or via per-site override
        val currentHost = currentSiteUrl?.let { extractHost(it) }
        val isProtectionActive = if (currentHost != null) {
            val override = getSiteOverride(currentHost)
            if (override != null) {
                override
            } else {
                isGlobalEnabled()
            }
        } else {
            isGlobalEnabled()
        }

        if (!isProtectionActive) {
            return false
        }

        try {
            // Allow internal browser resources and local/secure assets
            val lowerUrl = url.trim().lowercase()
            if (lowerUrl.startsWith("about:") ||
                lowerUrl.startsWith("file:") ||
                lowerUrl.startsWith("data:") ||
                lowerUrl.startsWith("blob:") ||
                lowerUrl.startsWith("chrome:") ||
                lowerUrl.startsWith("resource:") ||
                lowerUrl.startsWith("android-app:")
            ) {
                return false
            }

            // Extract host for matching
            val host = extractHost(url) ?: return false

            // First-party immunity: requests belonging to the active site's organization must never be blocked
            if (currentHost != null && isSameOrganization(currentHost, host)) {
                return false
            }

            // Match host against categorized blocklists (Exact domain or subdomain matching)
            if (isTrackerHost(host)) {
                Log.d(TAG, "BLOCKED tracker request (host match): $host (URL: $url)")
                return true
            }

            // Main-frame navigations to normal sites are allowed, unless matching obvious ad footprints
            if (isMainFrame && !containsTrackingFootprint(lowerUrl)) {
                return false
            }

            // Additional fallback: path/substring checking for generic tracker footprints in third-party resource names
            if (containsTrackingFootprint(lowerUrl)) {
                Log.d(TAG, "BLOCKED tracker request (substring footprint match): $url")
                return true
            }

        } catch (e: Exception) {
            // Guarantee Fail-open: log error, do not crash, and allow request to continue
            Log.e(TAG, "Exception in shouldBlock evaluating $url. Defaulting to ALLOW.", e)
            return false
        }

        return false
    }

    /**
     * Checks whether two hosts belong to the same parent domain or first-party service.
     */
    private fun isSameOrganization(siteHost: String, requestHost: String): Boolean {
        if (siteHost == requestHost || requestHost.endsWith(".$siteHost")) return true
        val baseSite = getBaseDomain(siteHost)
        val baseReq = getBaseDomain(requestHost)
        if (baseSite.isNotEmpty() && baseSite == baseReq) return true

        // First-party cross-domain service mappings (e.g. pinimg <-> pinterest, ytimg <-> youtube)
        if ((baseSite.contains("pinterest") || baseSite.contains("pinimg")) && 
            (baseReq.contains("pinterest") || baseReq.contains("pinimg"))) return true
        if ((baseSite.contains("youtube") || baseSite.contains("googlevideo") || baseSite.contains("ytimg") || baseSite.contains("google")) &&
            (baseReq.contains("youtube") || baseReq.contains("googlevideo") || baseReq.contains("ytimg") || baseReq.contains("google"))) return true
        if ((baseSite.contains("telegram") || baseSite.contains("t.me")) &&
            (baseReq.contains("telegram") || baseReq.contains("t.me"))) return true

        return false
    }

    private fun getBaseDomain(host: String): String {
        val parts = host.split(".")
        return if (parts.size >= 2) {
            "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
        } else {
            host
        }
    }

    /**
     * Safely extracts the host/domain name from a URL string.
     */
    fun extractHost(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host
            if (!host.isNullOrBlank()) {
                host.lowercase()
            } else {
                // Try java.net.URI fallback
                val javaUri = URI(url)
                javaUri.host?.lowercase()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if the given host matches any of our categorized tracking domains (exact or subdomain).
     */
    private fun isTrackerHost(host: String): Boolean {
        // Iterate through all categories
        val allCategories = listOf(
            ADVERTISING_TRACKERS,
            ANALYTICS_TRACKERS,
            SOCIAL_TRACKERS,
            KNOWN_TRACKING_DOMAINS
        )

        for (category in allCategories) {
            for (blockedDomain in category) {
                if (host == blockedDomain || host.endsWith(".$blockedDomain")) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Fallback substring analysis to catch tracking scripts/pixels in URL structures.
     */
    private fun containsTrackingFootprint(lowerUrl: String): Boolean {
        // Look for common patterns specifically inside query parameters or path segments, avoiding main domain false positives
        if (lowerUrl.contains("/fbevents.js") ||
            lowerUrl.contains("/gtm.js") ||
            lowerUrl.contains("/ga.js") ||
            lowerUrl.contains("/analytics.js") ||
            lowerUrl.contains("ads.doubleclick.net") ||
            lowerUrl.contains("googleadservices.com/pagead") ||
            lowerUrl.contains("/popunder") ||
            lowerUrl.contains("popunder.js") ||
            lowerUrl.contains("adsterra") ||
            lowerUrl.contains("onclickalgo") ||
            lowerUrl.contains("directrev") ||
            lowerUrl.contains("ay267.com") ||
            lowerUrl.contains("luugy.com") ||
            lowerUrl.contains("/adx/") ||
            lowerUrl.contains("adx.") ||
            lowerUrl.contains("popads") ||
            lowerUrl.contains("clickadu")
        ) {
            return true
        }
        return false
    }
}
