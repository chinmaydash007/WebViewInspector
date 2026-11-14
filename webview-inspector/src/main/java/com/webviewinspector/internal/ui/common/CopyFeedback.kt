package com.webviewinspector.internal.ui.common

import android.app.NotificationManager
import android.media.AudioManager
import android.os.Build
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import com.webviewinspector.R

private const val COPY_CONFIRMATION_MILLIS = 1000L

internal fun ImageButton.setCopyAction(copy: () -> Boolean) {
    bindCopyAction(copy) { copied -> setImageResource(if (copied) R.drawable.wvi_ic_check else R.drawable.wvi_ic_copy) }
}

internal fun MaterialButton.setCopyAction(copy: () -> Boolean) {
    val originalIcon = icon
    val copyIcon = originalIcon ?: AppCompatResources.getDrawable(context, R.drawable.wvi_ic_copy)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && originalIcon == null) {
        iconSize = context.dp(18)
        iconTint = textColors
        icon = copyIcon
    }
    bindCopyAction(copy) { copied ->
        icon = if (copied) AppCompatResources.getDrawable(context, R.drawable.wvi_ic_check) else copyIcon
    }
}

internal fun View.clearCopyFeedback() {
    (getTag(R.id.wvi_copy_confirmation) as? CopyConfirmation)?.clear()
}

private fun View.bindCopyAction(
    copy: () -> Boolean,
    showCheckmark: (Boolean) -> Unit,
) {
    val confirmation =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            CopyConfirmation(this, showCheckmark).also {
                setTag(R.id.wvi_copy_confirmation, it)
                addOnAttachStateChangeListener(it)
            }
        } else {
            null
        }
    setOnClickListener {
        if (copy() && isAttachedToWindow && isShown && isEnabled) {
            if (allowsCopyHaptic()) {
                ViewCompat.performHapticFeedback(this, HapticFeedbackConstantsCompat.CONFIRM)
            }
            confirmation?.show()
        } else {
            confirmation?.clear()
        }
    }
}

private fun View.allowsCopyHaptic(): Boolean =
    try {
        val ringerMode = context.getSystemService(AudioManager::class.java)?.ringerMode
        (ringerMode == AudioManager.RINGER_MODE_NORMAL || ringerMode == AudioManager.RINGER_MODE_VIBRATE) &&
            context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter ==
            NotificationManager.INTERRUPTION_FILTER_ALL
    } catch (_: RuntimeException) {
        false
    }

/** Main-thread checkmark state stays with the individual control and resets when detached. */
private class CopyConfirmation(
    private val view: View,
    private val showCheckmark: (Boolean) -> Unit,
) : View.OnAttachStateChangeListener {
    private var showing = false
    private val reset = Runnable { clear() }

    @Suppress("DEPRECATION") // This confirmation runs only on Android 12L and older.
    fun show() {
        view.removeCallbacks(reset)
        if (!showing) {
            showing = true
            showCheckmark(true)
        }
        view.announceForAccessibility(view.context.getString(R.string.wvi_copied_to_clipboard))
        view.postDelayed(reset, COPY_CONFIRMATION_MILLIS)
    }

    fun clear() {
        view.removeCallbacks(reset)
        if (showing) {
            showing = false
            showCheckmark(false)
        }
    }

    override fun onViewAttachedToWindow(view: View) = Unit

    override fun onViewDetachedFromWindow(view: View) = clear()
}
