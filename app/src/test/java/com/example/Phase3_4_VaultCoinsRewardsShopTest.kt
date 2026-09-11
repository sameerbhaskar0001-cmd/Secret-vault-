package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.theme.AppTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Phase3_4_VaultCoinsRewardsShopTest {

    private lateinit var app: Application
    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        prefs = app.getSharedPreferences("exchange_calc_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testSuccessfulRedemptionDeductsCoins() {
        // Give the user 100 coins
        prefs.edit().putInt("vault_coins_balance", 100).commit()
        
        val viewModel = CalculatorViewModel(app)
        assertEquals(100, viewModel.vaultCoins.value)
        
        // Buy Premium Theme Pass for 25 coins
        val success = viewModel.redeemCoins(25, "Premium Theme Pass")
        assertTrue(success)
        assertEquals(75, viewModel.vaultCoins.value)
    }

    @Test
    fun testInsufficientBalanceFails() {
        // Give the user 20 coins
        prefs.edit().putInt("vault_coins_balance", 20).commit()
        
        val viewModel = CalculatorViewModel(app)
        assertEquals(20, viewModel.vaultCoins.value)
        
        // Try to buy Premium Theme Pass for 25 coins
        val success = viewModel.redeemCoins(25, "Premium Theme Pass")
        assertFalse(success)
        assertEquals(20, viewModel.vaultCoins.value) // no change
    }

    @Test
    fun testPassActivationAndDuration() {
        // Set coins to 50
        prefs.edit().putInt("vault_coins_balance", 50).commit()
        
        val viewModel = CalculatorViewModel(app)
        
        // Before purchase, pass is inactive
        assertFalse(viewModel.isThemePremiumActive())
        
        val now = System.currentTimeMillis()
        val success = viewModel.redeemCoins(25, "Premium Theme Pass")
        assertTrue(success)
        
        // Now it must be active
        assertTrue(viewModel.isThemePremiumActive())
        
        val expiry = prefs.getLong("pass_theme_expiry", 0L)
        assertTrue(expiry >= now + 12 * 60 * 60 * 1000 - 5000) // approx 12 hours
    }

    @Test
    fun testSamePassRepurchaseAddsDuration() {
        // Set coins to 100
        prefs.edit().putInt("vault_coins_balance", 100).commit()
        
        val viewModel = CalculatorViewModel(app)
        
        // First purchase
        val success1 = viewModel.redeemCoins(25, "Premium Theme Pass")
        assertTrue(success1)
        val expiry1 = prefs.getLong("pass_theme_expiry", 0L)
        
        // Second purchase
        val success2 = viewModel.redeemCoins(25, "Premium Theme Pass")
        assertTrue(success2)
        val expiry2 = prefs.getLong("pass_theme_expiry", 0L)
        
        // Expiry 2 must be exactly 12 hours after expiry 1
        assertEquals(expiry1 + 12 * 60 * 60 * 1000, expiry2)
    }

    @Test
    fun testPassExpiryBehaviour() {
        // Simulate expired Browser Pass
        val oneHourAgo = System.currentTimeMillis() - 60 * 60 * 1000
        prefs.edit().putLong("pass_browser_expiry", oneHourAgo).commit()
        
        val viewModel = CalculatorViewModel(app)
        
        // Browser pass is expired, should not be premium
        assertFalse(viewModel.isBrowserPremium())
    }

    @Test
    fun testThemeAccessPermissions() {
        val viewModel = CalculatorViewModel(app)
        
        // By default, free user
        assertFalse(viewModel.isPremiumUser())
        assertFalse(viewModel.isThemePremiumActive())
        
        // Free themes: GRAPHITE, CLASSIC, MIDNIGHT_BLUE, NORDIC_EMERALD
        assertFalse(viewModel.isThemePremium(AppTheme.GRAPHITE))
        assertFalse(viewModel.isThemePremium(AppTheme.CLASSIC))
        assertFalse(viewModel.isThemePremium(AppTheme.MIDNIGHT_BLUE))
        assertFalse(viewModel.isThemePremium(AppTheme.NORDIC_EMERALD))
        
        // Premium themes: OCEAN_BREEZE, SUNSET_ROSE, LAVENDER_MIST, QUANTUM_CYAN
        assertTrue(viewModel.isThemePremium(AppTheme.OCEAN_BREEZE))
        assertTrue(viewModel.isThemePremium(AppTheme.SUNSET_ROSE))
        assertTrue(viewModel.isThemePremium(AppTheme.LAVENDER_MIST))
        assertTrue(viewModel.isThemePremium(AppTheme.QUANTUM_CYAN))
        
        // Free user can select GRAPHITE
        viewModel.setSelectedTheme(AppTheme.GRAPHITE)
        assertEquals(AppTheme.GRAPHITE, viewModel.selectedTheme.value)
        
        // Free user cannot select OCEAN_BREEZE (should be blocked)
        viewModel.setSelectedTheme(AppTheme.OCEAN_BREEZE)
        // Should fallback or remain unchanged
        assertNotEquals(AppTheme.OCEAN_BREEZE, viewModel.selectedTheme.value)
        
        // Activate Theme Pass
        prefs.edit().putLong("pass_theme_expiry", System.currentTimeMillis() + 12 * 60 * 60 * 1000).commit()
        
        // Re-construct viewmodel to pick up updated premium/pass status from SharedPreferences
        val premiumViewModel = CalculatorViewModel(app)
        assertTrue(premiumViewModel.isThemePremiumActive())
        
        // Now selection works!
        premiumViewModel.setSelectedTheme(AppTheme.OCEAN_BREEZE)
        
        // Re-construct viewmodel to verify SharedPreferences was written and loaded successfully
        val verifiedViewModel = CalculatorViewModel(app)
        assertEquals(AppTheme.OCEAN_BREEZE, verifiedViewModel.selectedTheme.value)
    }
}
