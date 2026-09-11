package com.example

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.LoadAdError

object AdMobManager {
    private const val TAG = "AdMobManager"

    // Test Ad Unit IDs provided officially by Google AdMob
    const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"
    const val TEST_NATIVE_ID = "ca-app-pub-3940256099942544/2247696110"

    private var isInitialized = false
    private var lastAdShownTimeMillis: Long = 0L
    private var meaningfulActionsCompleted: Int = 0

    fun incrementMeaningfulAction() {
        meaningfulActionsCompleted++
        Log.d(TAG, "Meaningful action incremented. Total in current cooldown window: $meaningfulActionsCompleted")
    }

    fun isAdEligible(viewModel: CalculatorViewModel): Boolean {
        if (!isAdsEnabled(viewModel)) return false
        
        val now = System.currentTimeMillis()
        val timeSinceLastAd = now - lastAdShownTimeMillis
        val isCooldownPassed = timeSinceLastAd >= 5 * 60 * 1000 // 5 minutes
        val isActionsPassed = meaningfulActionsCompleted >= 6
        
        Log.d(TAG, "isAdEligible check - CooldownPassed: $isCooldownPassed (${timeSinceLastAd / 1000}s elapsed), ActionsPassed: $isActionsPassed ($meaningfulActionsCompleted completed)")
        
        return isCooldownPassed && isActionsPassed
    }

    fun recordAdShown() {
        lastAdShownTimeMillis = System.currentTimeMillis()
        meaningfulActionsCompleted = 0
        Log.d(TAG, "Ad shown recorded. 5-minute cooldown started. Actions reset to 0.")
    }

    fun initialize(context: Context) {
        if (isInitialized) return
        
        try {
            // Configure Google Mobile Ads SDK for test configuration
            val requestConfiguration = RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                .build()
            MobileAds.setRequestConfiguration(requestConfiguration)

            MobileAds.initialize(context) { status ->
                isInitialized = true
                Log.d(TAG, "AdMob MobileAds initialized successfully: $status")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AdMob MobileAds", e)
        }
    }

    fun isAdsEnabled(viewModel: CalculatorViewModel): Boolean {
        // Ads are completely disabled for Premium/Lifetime VIP users
        return !viewModel.isPremiumUser()
    }
}

/**
 * Isolated helper for Standard Normal Ads (Banners and Interstitials)
 */
object NormalAdHelper {
    private const val TAG = "NormalAdHelper"
    private var interstitialAd: InterstitialAd? = null
    private var isAdLoading = false

    /**
     * Loads a test interstitial ad.
     */
    fun loadInterstitialAd(context: Context, viewModel: CalculatorViewModel) {
        if (!AdMobManager.isAdsEnabled(viewModel)) {
            Log.d(TAG, "Interstitial load ignored: user is Premium")
            interstitialAd = null
            return
        }
        if (interstitialAd != null || isAdLoading) return

        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            AdMobManager.TEST_INTERSTITIAL_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isAdLoading = false
                    Log.d(TAG, "Test Interstitial ad loaded successfully")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isAdLoading = false
                    Log.e(TAG, "Test Interstitial ad failed to load: ${error.message}")
                }
            }
        )
    }

    /**
     * Shows the loaded test interstitial ad if available and eligible.
     */
    fun showInterstitialAd(activity: android.app.Activity, viewModel: CalculatorViewModel, onAdClosed: () -> Unit) {
        val ad = interstitialAd
        if (ad != null && AdMobManager.isAdsEnabled(viewModel)) {
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    onAdClosed()
                    // Pre-load next ad
                    loadInterstitialAd(activity, viewModel)
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                    interstitialAd = null
                    onAdClosed()
                }
            }
            ad.show(activity)
        } else {
            onAdClosed()
        }
    }
}

/**
 * Isolated helper for Rewarded Ads
 */
object RewardedAdHelper {
    private const val TAG = "RewardedAdHelper"
    private var rewardedAd: RewardedAd? = null
    private var isAdLoading = false

    /**
     * Loads a test rewarded ad.
     */
    fun loadRewardedAd(context: Context, viewModel: CalculatorViewModel) {
        if (!AdMobManager.isAdsEnabled(viewModel)) {
            Log.d(TAG, "Rewarded load ignored: user is Premium")
            rewardedAd = null
            return
        }
        if (rewardedAd != null || isAdLoading) return

        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            AdMobManager.TEST_REWARDED_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isAdLoading = false
                    Log.d(TAG, "Test Rewarded ad loaded successfully")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isAdLoading = false
                    Log.e(TAG, "Test Rewarded ad failed to load: ${error.message}")
                }
            }
        )
    }

    /**
     * Shows the loaded rewarded ad with success rewards callback.
     */
    fun showRewardedAd(
        activity: android.app.Activity,
        viewModel: CalculatorViewModel,
        onRewardEarned: (rewardAmount: Int) -> Unit,
        onAdClosed: () -> Unit
    ) {
        val ad = rewardedAd
        if (ad != null && AdMobManager.isAdsEnabled(viewModel)) {
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    onAdClosed()
                    // Preload next
                    loadRewardedAd(activity, viewModel)
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                    rewardedAd = null
                    onAdClosed()
                }
            }

            ad.show(activity, OnUserEarnedRewardListener { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onRewardEarned(rewardItem.amount)
            })
        } else {
            // Safe fallback if ad not ready/premium user
            onAdClosed()
        }
    }
}
