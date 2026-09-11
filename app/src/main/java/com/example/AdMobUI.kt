package com.example

import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.LocalAppThemeColors
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import kotlinx.coroutines.delay

@Composable
fun AdMobBannerAd(viewModel: CalculatorViewModel, modifier: Modifier = Modifier) {
    if (!AdMobManager.isAdsEnabled(viewModel)) return

    val context = LocalContext.current
    var isEligible by remember { mutableStateOf(AdMobManager.isAdEligible(viewModel)) }
    val themeColors = LocalAppThemeColors.current

    LaunchedEffect(Unit) {
        while (true) {
            isEligible = AdMobManager.isAdEligible(viewModel)
            delay(1000) // Poll eligibility every second
        }
    }

    if (isEligible) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(themeColors.brandBg)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = AdMobManager.TEST_BANNER_ID
                        adListener = object : AdListener() {
                            override fun onAdLoaded() {
                                super.onAdLoaded()
                                Log.d("AdMobBannerAd", "Banner ad loaded successfully")
                                AdMobManager.recordAdShown()
                                isEligible = false
                            }
                            override fun onAdFailedToLoad(error: LoadAdError) {
                                super.onAdFailedToLoad(error)
                                Log.e("AdMobBannerAd", "Banner ad failed to load: ${error.message}")
                            }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                },
                onRelease = { adView ->
                    adView.destroy()
                }
            )
        }
    }
}

@Composable
fun AdMobNativeAd(viewModel: CalculatorViewModel, modifier: Modifier = Modifier) {
    if (!AdMobManager.isAdsEnabled(viewModel)) return

    val context = LocalContext.current
    var isEligible by remember { mutableStateOf(AdMobManager.isAdEligible(viewModel)) }
    var loadedNativeAd by remember { mutableStateOf<NativeAd?>(null) }
    val themeColors = LocalAppThemeColors.current

    DisposableEffect(loadedNativeAd) {
        onDispose {
            loadedNativeAd?.destroy()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            isEligible = AdMobManager.isAdEligible(viewModel)
            delay(1000) // Poll eligibility every second
        }
    }

    if (isEligible) {
        LaunchedEffect(isEligible) {
            if (loadedNativeAd == null) {
                try {
                    val adLoader = AdLoader.Builder(context, AdMobManager.TEST_NATIVE_ID)
                        .forNativeAd { nativeAd ->
                            loadedNativeAd = nativeAd
                            AdMobManager.recordAdShown()
                            isEligible = false
                        }
                        .withAdListener(object : AdListener() {
                            override fun onAdFailedToLoad(error: LoadAdError) {
                                Log.e("AdMobNativeAd", "Native Ad failed to load: ${error.message}")
                            }
                        })
                        .build()
                    adLoader.loadAd(AdRequest.Builder().build())
                } catch (e: Exception) {
                    Log.e("AdMobNativeAd", "Error loading native ad", e)
                }
            }
        }

        val ad = loadedNativeAd
        if (ad != null) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1B2031).copy(alpha = 0.95f))
                    .border(1.dp, themeColors.themePurple.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    factory = { ctx ->
                        val adView = NativeAdView(ctx)
                        val container = LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        }

                        // Headline
                        val headlineView = TextView(ctx).apply {
                            text = ad.headline
                            textSize = 14f
                            setTextColor(android.graphics.Color.WHITE)
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setPadding(0, 0, 0, 4)
                        }
                        container.addView(headlineView)
                        adView.headlineView = headlineView

                        // Body Text
                        if (!ad.body.isNullOrBlank()) {
                            val bodyView = TextView(ctx).apply {
                                text = ad.body
                                textSize = 11f
                                setTextColor(android.graphics.Color.LTGRAY)
                                setPadding(0, 0, 0, 8)
                            }
                            container.addView(bodyView)
                            adView.bodyView = bodyView
                        }

                        // Call To Action Button
                        if (!ad.callToAction.isNullOrBlank()) {
                            val ctaButton = Button(ctx).apply {
                                text = ad.callToAction
                                setTextColor(android.graphics.Color.WHITE)
                                setBackgroundColor(android.graphics.Color.parseColor("#8E24AA")) // ThemePurple color matching the design
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    gravity = Gravity.CENTER_HORIZONTAL
                                }
                            }
                            container.addView(ctaButton)
                            adView.callToActionView = ctaButton
                        }

                        adView.addView(container)
                        adView.setNativeAd(ad)
                        adView
                    }
                )
            }
        }
    }
}
