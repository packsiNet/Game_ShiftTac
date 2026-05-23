package com.shifttac.ui

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.shifttac.BuildConfig

// MobileAds.initialize() is called once in App.onCreate() — not here.
class AdManager(context: Context) {

    private val appContext = context.applicationContext
    private var ad: InterstitialAd? = null
    private var loading = false

    init {
        preload()
    }

    fun preload() {
        if (loading || ad != null) return
        loading = true
        InterstitialAd.load(
            appContext,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    ad = loaded
                    loading = false
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null
                    loading = false
                }
            }
        )
    }

    // Show the interstitial if it is loaded; call onFinished when dismissed (or immediately if no ad).
    fun showIfReady(activity: Activity, onFinished: () -> Unit) {
        val current = ad ?: run {
            onFinished()
            preload()
            return
        }
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad = null
                onFinished()
                preload()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                ad = null
                onFinished()
                preload()
            }
        }
        current.show(activity)
    }
}
