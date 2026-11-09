package com.skyqlike.iptv.ui.common

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * True on a phone (a small, touch screen that is not a TV box). Phones get their own layout
 * with a bottom menu instead of the TV's side menu, and may be held upright.
 */
fun Context.isPhoneLike(): Boolean {
    val uiMode = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val tv = uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature("amazon.hardware.fire_tv")
    return !tv && resources.configuration.smallestScreenWidthDp < 600
}
