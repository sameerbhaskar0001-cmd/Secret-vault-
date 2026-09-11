package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AdMobFoundationTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        val prefs = app.getSharedPreferences("exchange_calc_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testTestAdUnitIds() {
        // Verify that the official Google AdMob test ad configurations are defined correctly
        assertEquals("ca-app-pub-3940256099942544/6300978111", AdMobManager.TEST_BANNER_ID)
        assertEquals("ca-app-pub-3940256099942544/1033173712", AdMobManager.TEST_INTERSTITIAL_ID)
        assertEquals("ca-app-pub-3940256099942544/5224354917", AdMobManager.TEST_REWARDED_ID)
    }

    @Test
    fun testIsAdsEnabled_FreeUser() {
        val viewModel = CalculatorViewModel(app)
        assertFalse(viewModel.isPremiumUser())
        
        // Ads should be enabled for standard free users
        assertTrue(AdMobManager.isAdsEnabled(viewModel))
    }

    @Test
    fun testIsAdsEnabled_PremiumUser() {
        val prefs = app.getSharedPreferences("exchange_calc_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("premium_state", "Premium").commit()

        val viewModel = CalculatorViewModel(app)
        assertTrue(viewModel.isPremiumUser())
        
        // Ads must be completely disabled for premium users
        assertFalse(AdMobManager.isAdsEnabled(viewModel))
    }

    @Test
    fun testIsAdsEnabled_LifetimeUser() {
        val prefs = app.getSharedPreferences("exchange_calc_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("premium_state", "Lifetime").commit()

        val viewModel = CalculatorViewModel(app)
        assertTrue(viewModel.isPremiumUser())
        
        // Ads must be completely disabled for lifetime VIP users
        assertFalse(AdMobManager.isAdsEnabled(viewModel))
    }

    @Test
    fun testAdMobEligibilityAndFrequencyRules() {
        val viewModel = CalculatorViewModel(app)
        assertFalse(viewModel.isPremiumUser())

        // Ad is eligible initially as lastAdShownTimeMillis is 0 (cooldown passed) 
        // but meaningful actions are 0, so ineligible until 4 actions are completed
        assertFalse(AdMobManager.isAdEligible(viewModel))

        // Perform 3 actions
        for (i in 1..3) {
            AdMobManager.incrementMeaningfulAction()
        }
        assertFalse(AdMobManager.isAdEligible(viewModel))

        // Perform 4th action
        AdMobManager.incrementMeaningfulAction()
        assertTrue(AdMobManager.isAdEligible(viewModel))

        // Show ad -> this should reset action count and start cooldown
        AdMobManager.recordAdShown()
        assertFalse(AdMobManager.isAdEligible(viewModel))

        // Even with 4 more actions, eligibility should remain false because of the 4-minute cooldown
        for (i in 1..4) {
            AdMobManager.incrementMeaningfulAction()
        }
        assertFalse(AdMobManager.isAdEligible(viewModel))
    }
}
