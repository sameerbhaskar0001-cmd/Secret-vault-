package com.example

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.LocalAppThemeColors
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@Composable
fun VaultRewardsScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val themeColors = LocalAppThemeColors.current
    val ThemePurple = themeColors.themePurple
    val TextDark = themeColors.textDark
    val TextMedium = themeColors.textMedium
    val ButtonContentColor = if (ThemePurple == Color(0xFFFFFFFF)) Color(0xFF131726) else Color.White

    val coins by viewModel.vaultCoins.collectAsStateWithLifecycle()
    val premiumState by viewModel.premiumState.collectAsStateWithLifecycle()
    val isPermanentPremium = premiumState == "Premium" || premiumState == "Lifetime"
    val dailyLastClaimed by viewModel.dailyRewardLastClaimed.collectAsStateWithLifecycle()
    val luckyLastOpened by viewModel.luckyChestLastOpened.collectAsStateWithLifecycle()
    val adsCount by viewModel.rewardedAdsTodayCount.collectAsStateWithLifecycle()

    var showChestRewardDialog by remember { mutableStateOf<String?>(null) }
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Keep timers updated
    LaunchedEffect(Unit) {
        // Preload rewarded ad
        RewardedAdHelper.loadRewardedAd(context, viewModel)
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    // Helper to calculate countdown
    fun getCountdownString(lastActionTime: Long): String {
        val remaining = 24 * 60 * 60 * 1000 - (currentTime - lastActionTime)
        if (remaining <= 0) return ""
        val hours = TimeUnit.MILLISECONDS.toHours(remaining)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
        return String.format("%02dh %02dm %02ds", hours, minutes, seconds)
    }

    val dailyClaimable = currentTime - dailyLastClaimed >= 24 * 60 * 60 * 1000
    val luckyOpenable = currentTime - luckyLastOpened >= 24 * 60 * 60 * 1000

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = themeColors.brandBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextDark
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Vault Rewards",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Coin Balance Header Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF2E1A47), Color(0xFF130E22))))
                    .border(1.dp, ThemePurple.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "VAULT COIN BALANCE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ThemePurple,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🪙 $coins",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Coins never expire. Complete tasks below to earn more!",
                            fontSize = 11.sp,
                            color = TextMedium.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // 2. Daily Reward Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B2B).copy(alpha = 0.95f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🎁",
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Daily Reward",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Claim once every 24 hours for free coins",
                                    fontSize = 11.sp,
                                    color = TextMedium
                                )
                            }
                        }
                        if (dailyClaimable) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ThemePurple.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "NEW",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThemePurple
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    if (dailyClaimable) {
                        Button(
                            onClick = {
                                viewModel.triggerKeypressEffects(context)
                                val reward = viewModel.claimDailyReward()
                                if (reward > 0) {
                                    Toast.makeText(context, "Daily Reward Claimed: +$reward Coins! 🪙", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = ThemePurple),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Claim Reward (2-10 Coins)", fontWeight = FontWeight.Bold, color = ButtonContentColor)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = TextMedium.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Next Reward in: ${getCountdownString(dailyLastClaimed)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextMedium.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Lucky Chest Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B2B).copy(alpha = 0.95f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🗝️",
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Lucky Vault Chest",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Open daily: 70% Coins, 30% Rare 12-hr Pass",
                                    fontSize = 11.sp,
                                    color = TextMedium
                                )
                            }
                        }
                        if (luckyOpenable) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ThemePurple.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "OPEN",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThemePurple
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    if (luckyOpenable) {
                        Button(
                            onClick = {
                                viewModel.triggerKeypressEffects(context)
                                val outcome = viewModel.openLuckyChest()
                                if (outcome.first == "Coins") {
                                    showChestRewardDialog = "Congratulations! You opened the Lucky Chest and found 🪙 ${outcome.second} Coins!"
                                } else if (outcome.first == "Pass") {
                                    showChestRewardDialog = "Spectacular! You found a Rare 12-Hour Pass: ${outcome.second}! All Premium options unlocked for 12 hours."
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = ThemePurple),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Open Lucky Chest", fontWeight = FontWeight.Bold, color = ButtonContentColor)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = TextMedium.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Next Chest in: ${getCountdownString(luckyLastOpened)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextMedium.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Watch & Earn Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B2B).copy(alpha = 0.95f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📺",
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Watch & Earn",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isPermanentPremium) "Premium Ad-Free experience active" else "Watch a video to earn +10 Coins",
                                    fontSize = 11.sp,
                                    color = TextMedium
                                )
                            }
                        }
                        if (!isPermanentPremium) {
                            Text(
                                text = "$adsCount / 8 today",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThemePurple
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ThemePurple.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "VIP ACTIVE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThemePurple
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    if (isPermanentPremium) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.04f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Ads are disabled for VIP members.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextMedium.copy(alpha = 0.7f)
                            )
                        }
                    } else if (adsCount < 8) {
                        Button(
                            onClick = {
                                viewModel.triggerKeypressEffects(context)
                                if (activity != null) {
                                    Toast.makeText(context, "Loading video...", Toast.LENGTH_SHORT).show()
                                    RewardedAdHelper.showRewardedAd(
                                        activity = activity,
                                        viewModel = viewModel,
                                        onRewardEarned = {
                                            viewModel.completeRewardedAd()
                                            Toast.makeText(context, "Video Completed! Earned +10 Coins 🪙", Toast.LENGTH_LONG).show()
                                        },
                                        onAdClosed = {
                                            // Pre-load next
                                            RewardedAdHelper.loadRewardedAd(context, viewModel)
                                        }
                                    )
                                } else {
                                    Toast.makeText(context, "Error: Activity not found", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = ThemePurple),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Watch Video (+10 Coins)", fontWeight = FontWeight.Bold, color = ButtonContentColor)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = TextMedium.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Daily limit reached for today",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextMedium.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // 5. Redeem Rewards Section
            Text(
                text = "REDEEM REWARDS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMedium,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )

            val browserExpiry by viewModel.passBrowserExpiry.collectAsStateWithLifecycle()
            val cameraExpiry by viewModel.passCameraExpiry.collectAsStateWithLifecycle()
            val themeExpiry by viewModel.passThemeExpiry.collectAsStateWithLifecycle()
            val premiumExpiry by viewModel.passPremiumExpiry.collectAsStateWithLifecycle()

            fun getPassRemainingText(expiry: Long): String? {
                if (expiry <= currentTime) return null
                val diffMs = expiry - currentTime
                val diffHours = diffMs / (60 * 60 * 1000)
                val diffMins = (diffMs % (60 * 60 * 1000)) / (60 * 1000)
                val diffSecs = (diffMs % (60 * 1000)) / 1000
                return if (diffHours > 0) "${diffHours}h ${diffMins}m left" else "${diffMins}m ${diffSecs}s left"
            }

            val redeemItems = listOf(
                RedeemItem("Browser Pass", "Unlock tracking blocker & private incognito browser", "12 Hours", 40),
                RedeemItem("Camera Pass", "Bypass limitations on secure private camera", "12 Hours", 50),
                RedeemItem("Premium Theme Pass", "Access all beautiful premium themes safely", "12 Hours", 25),
                RedeemItem("1-Day Premium Pass", "Access complete ad-free premium system", "24 Hours", 50)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                redeemItems.forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131726)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = item.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(ThemePurple.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = item.duration,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ThemePurple
                                        )
                                    }
                                }
                                
                                val expiry = if (isPermanentPremium) 0L else when (item.title) {
                                    "Browser Pass" -> browserExpiry
                                    "Camera Pass" -> cameraExpiry
                                    "Premium Theme Pass" -> themeExpiry
                                    "1-Day Premium Pass" -> premiumExpiry
                                    else -> 0L
                                }
                                val remainingText = getPassRemainingText(expiry)
                                if (remainingText != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "ACTIVE: $remainingText",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = item.description,
                                    fontSize = 11.sp,
                                    color = TextMedium
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            if (isPermanentPremium) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ThemePurple.copy(alpha = 0.15f))
                                        .border(1.dp, ThemePurple.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "UNLOCKED",
                                        color = ThemePurple,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        viewModel.triggerKeypressEffects(context)
                                        val success = viewModel.redeemCoins(item.cost, item.title)
                                        if (success) {
                                            Toast.makeText(context, "${item.title} Redeemed successfully!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Not enough coins! Earn more coins by watching videos or daily claims.", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ThemePurple),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "🪙 ${item.cost}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ButtonContentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Chest Opening dialog representation
    if (showChestRewardDialog != null) {
        AlertDialog(
            onDismissRequest = { showChestRewardDialog = null },
            title = {
                Text(
                    text = "Lucky Vault Chest",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = showChestRewardDialog!!,
                    fontSize = 14.sp,
                    color = TextMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showChestRewardDialog = null }
                ) {
                    Text("Superb!", color = ThemePurple, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF0F1322),
            titleContentColor = Color.White,
            textContentColor = TextMedium
        )
    }
}

data class RedeemItem(
    val title: String,
    val description: String,
    val duration: String,
    val cost: Int
)
