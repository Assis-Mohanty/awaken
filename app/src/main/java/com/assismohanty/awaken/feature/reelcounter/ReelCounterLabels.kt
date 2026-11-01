package com.assismohanty.awaken.feature.reelcounter

import androidx.annotation.StringRes
import com.assismohanty.awaken.R
import com.assismohanty.awaken.reelcounter.core.ShortVideoApp

/** The name Reel Counter shows for each supported app. */
@StringRes
fun ShortVideoApp.labelRes(): Int = when (this) {
    ShortVideoApp.INSTAGRAM -> R.string.reel_app_instagram
    ShortVideoApp.YOUTUBE -> R.string.reel_app_youtube
    ShortVideoApp.TIKTOK -> R.string.reel_app_tiktok
    ShortVideoApp.FACEBOOK -> R.string.reel_app_facebook
    ShortVideoApp.SNAPCHAT -> R.string.reel_app_snapchat
}
