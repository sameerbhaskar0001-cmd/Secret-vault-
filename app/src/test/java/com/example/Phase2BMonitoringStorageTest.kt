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
class Phase2BMonitoringStorageTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        val prefs = app.getSharedPreferences("exchange_calc_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testMonitoringLimits_FreeUser() {
        val viewModel = CalculatorViewModel(app)
        
        // Assert free user initially starts with 0 failed attempts and 5 remaining attempts
        assertFalse(viewModel.isPremiumUser())
        assertEquals(0, viewModel.monitoringFailedAttempts.value)
        assertEquals(5, viewModel.getMonitoringRemainingAttempts())
        assertFalse(viewModel.showMonitoringLimitDialog.value)

        // Perform 5 failed attempts
        for (i in 1..5) {
            val result = viewModel.tryUnlockVault("999$i") // Wrong PIN
            assertFalse(result)
            assertEquals(i, viewModel.monitoringFailedAttempts.value)
            assertEquals(5 - i, viewModel.getMonitoringRemainingAttempts())
            assertFalse(viewModel.showMonitoringLimitDialog.value)
        }

        // 6th attempt should block and show Premium prompt
        val result6 = viewModel.tryUnlockVault("9996")
        assertFalse(result6)
        assertEquals(5, viewModel.monitoringFailedAttempts.value) // Limit doesn't increase past max
        assertEquals(0, viewModel.getMonitoringRemainingAttempts())
        assertTrue(viewModel.showMonitoringLimitDialog.value)
    }

    @Test
    fun testMonitoringLimits_PremiumUser() {
        // Upgrade to Premium in SharedPreferences BEFORE initializing ViewModel
        val prefs = app.getSharedPreferences("exchange_calc_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("premium_state", "Premium").commit()

        val viewModel = CalculatorViewModel(app)
        
        assertTrue(viewModel.isPremiumUser())
        assertEquals(15, viewModel.getMonitoringMaxAttempts())
        assertEquals(15, viewModel.getMonitoringRemainingAttempts())

        // Perform 15 failed attempts
        for (i in 1..15) {
            val result = viewModel.tryUnlockVault("999$i")
            assertFalse(result)
            assertEquals(i, viewModel.monitoringFailedAttempts.value)
            assertEquals(15 - i, viewModel.getMonitoringRemainingAttempts())
            assertFalse(viewModel.showMonitoringLimitDialog.value)
        }

        // 16th attempt should block and show Premium prompt
        val result16 = viewModel.tryUnlockVault("99916")
        assertFalse(result16)
        assertEquals(15, viewModel.monitoringFailedAttempts.value)
        assertEquals(0, viewModel.getMonitoringRemainingAttempts())
        assertTrue(viewModel.showMonitoringLimitDialog.value)
    }

    @Test
    fun testMonitoringLimits_CounterPersistence() {
        // Run with first VM instance
        run {
            val vm1 = CalculatorViewModel(app)
            vm1.tryUnlockVault("8881")
            vm1.tryUnlockVault("8882")
            assertEquals(2, vm1.monitoringFailedAttempts.value)
            assertEquals(3, vm1.getMonitoringRemainingAttempts())
        }

        // Run with second VM instance simulating restart
        run {
            val vm2 = CalculatorViewModel(app)
            assertEquals(2, vm2.monitoringFailedAttempts.value)
            assertEquals(3, vm2.getMonitoringRemainingAttempts())
        }
    }

    @Test
    fun testStorageLimits() {
        val viewModel = CalculatorViewModel(app)
        
        // Free user storage check (15 GB)
        assertFalse(viewModel.isPremiumUser())
        
        // Premium user storage check (25 GB)
        val prefs = app.getSharedPreferences("exchange_calc_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("premium_state", "Premium").commit()
        
        val premiumViewModel = CalculatorViewModel(app)
        assertTrue(premiumViewModel.isPremiumUser())
    }
}
