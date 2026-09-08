package com.example

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalAppThemeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val themeColors = LocalAppThemeColors.current
    val ThemePurple = themeColors.themePurple
    val TextMedium = themeColors.textMedium

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 800)),
        exit = fadeOut(animationSpec = tween(durationMillis = 300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header Row (Top App Bar: ← About)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF161B2B).copy(alpha = 0.95f))
                        .border(
                            width = 1.2.dp,
                            color = Color.White.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                        .testTag("about_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "About",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("about_screen_title")
                )

                Spacer(modifier = Modifier.weight(1f))

                // Security Shield Icon
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = "Verified Secure",
                    tint = ThemePurple.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // TOP HERO CARD
                UnifiedGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("about_hero_card"),
                    shape = RoundedCornerShape(24.dp),
                    bgColor = Color(0xFF1B2031).copy(alpha = 0.95f),
                    elevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Styled Premium Icon Stack
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(ThemePurple.copy(alpha = 0.08f))
                                    .border(1.dp, ThemePurple.copy(alpha = 0.2f), CircleShape)
                            )
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Security Shield",
                                tint = ThemePurple,
                                modifier = Modifier.size(44.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock overlay",
                                tint = Color.Black,
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(ThemePurple)
                                    .padding(3.dp)
                            )
                        }

                        // App Name and Tagline
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Secret Vault",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Private • Secure • Local",
                                color = TextMedium,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Version & Status Badge Layout
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "v1.0.0",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )

                            // Status Badge: Latest Version
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF4CAF50).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF81C784))
                                )
                                Text(
                                    text = "Latest Version",
                                    color = Color(0xFF81C784),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                // SECTION 1: Support & Feedback
                AboutSectionTitle(title = "Support & Feedback")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClickableAboutCard(
                        title = "Contact Support",
                        subtitle = "sameerbhaskar0001@gmail.com",
                        icon = Icons.Default.Mail,
                        testTag = "btn_contact_support",
                        onClick = {
                            Toast.makeText(context, "Support: sameerbhaskar0001@gmail.com", Toast.LENGTH_LONG).show()
                        }
                    )
                    ClickableAboutCard(
                        title = "Send Feedback & Suggestions",
                        subtitle = "Share your thoughts to help us improve",
                        icon = Icons.Default.Feedback,
                        testTag = "btn_send_feedback",
                        onClick = {
                            Toast.makeText(context, "Feedback: sameerbhaskar0001@gmail.com", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // SECTION 2: Legal & Privacy
                AboutSectionTitle(title = "Legal & Privacy")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClickableAboutCard(
                        title = "Privacy Policy",
                        subtitle = "How we protect and manage your data locally",
                        icon = Icons.Default.PrivacyTip,
                        testTag = "btn_privacy_policy",
                        onClick = {
                            Toast.makeText(context, "Your vault data is strictly offline and encrypted.", Toast.LENGTH_SHORT).show()
                        }
                    )
                    ClickableAboutCard(
                        title = "Terms & Conditions",
                        subtitle = "Rules for using Secret Vault",
                        icon = Icons.Default.Description,
                        testTag = "btn_terms_conditions",
                        onClick = {
                            Toast.makeText(context, "Secret Vault Terms of Service.", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // SECTION 3: Community
                AboutSectionTitle(title = "Community")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClickableAboutCard(
                        title = "Rate App",
                        subtitle = "Show your support with a 5-star rating",
                        icon = Icons.Default.Star,
                        testTag = "btn_rate_app",
                        onClick = {
                            Toast.makeText(context, "Opening Store Page", Toast.LENGTH_SHORT).show()
                        }
                    )
                    ClickableAboutCard(
                        title = "Share App",
                        subtitle = "Recommend Secret Vault to friends & family",
                        icon = Icons.Default.Share,
                        testTag = "btn_share_app",
                        onClick = {
                            Toast.makeText(context, "Share sheet initialized", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // FOOTER - Luxury minimal obsidian glass card (Apple / Notion style)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF0F1016)) // Solid deep obsidian dark
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Text(
                            text = "CRAFTED WITH PRECISION.",
                            color = Color(0xFF8E8E93), // subtle grey
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Text(
                            text = "Sam Unmatched",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                            textAlign = TextAlign.Center
                        )
                        
                        // Generous spacing around a minimal divider
                        Spacer(modifier = Modifier.height(36.dp))
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.12f))
                        )
                        Spacer(modifier = Modifier.height(36.dp))
                        
                        Text(
                            text = "Founder",
                            color = Color(0xFF8E8E93), // subtle grey
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 1.5.sp,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = "Sameer Bhaskar",
                            color = Color(0xFFD1D5DB), // soft silver
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}
}

@Composable
fun AboutSectionTitle(title: String) {
    val themeColors = LocalAppThemeColors.current
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = themeColors.textMedium,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
        letterSpacing = 1.sp
    )
}

@Composable
fun InfoItemRow(
    label: String,
    value: String,
    icon: ImageVector,
    valueColor: Color = Color.White
) {
    val themeColors = LocalAppThemeColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(themeColors.themePurple.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = themeColors.themePurple,
                modifier = Modifier.size(18.dp)
            )
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = themeColors.textMedium,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = valueColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ClickableAboutCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    val themeColors = LocalAppThemeColors.current
    val ThemePurple = themeColors.themePurple

    UnifiedGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(20.dp),
        bgColor = Color(0xFF1B2031).copy(alpha = 0.95f),
        elevation = 2.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ThemePurple.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = ThemePurple,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        lineHeight = 14.sp
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
