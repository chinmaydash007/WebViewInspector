package com.webviewinspector.sample

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator

internal fun Context.sampleMotionEnabled(): Boolean =
    if (Build.VERSION.SDK_INT >= 26) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }

internal fun View.resetSampleFade() {
    animate().cancel()
    alpha = 1f
}

internal fun View.fadeInSampleContent() {
    resetSampleFade()
    if (!isShown || !context.sampleMotionEnabled()) return
    // Keep the entering WebView discoverable while it fades in.
    alpha = 0.08f
    animate()
        .alpha(1f)
        .setDuration(180)
        .setInterpolator(DecelerateInterpolator())
        .start()
}
