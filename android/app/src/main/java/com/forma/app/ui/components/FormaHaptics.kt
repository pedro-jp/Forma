package com.forma.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Equivalente a `UINotificationFeedbackGenerator`, `UIImpactFeedbackGenerator` e
 * `UISelectionFeedbackGenerator` do iOS.
 *
 * ```
 * val haptics = rememberFormaHaptics()
 * haptics.success()
 * ```
 */
class FormaHaptics(private val view: View) {
    /** UINotificationFeedbackGenerator().notificationOccurred(.success) */
    fun success() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.VIRTUAL_KEY
    )

    /** UINotificationFeedbackGenerator().notificationOccurred(.warning) */
    fun warning() = perform(HapticFeedbackConstants.LONG_PRESS)

    /** UINotificationFeedbackGenerator().notificationOccurred(.error) */
    fun error() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS
    )

    /** UIImpactFeedbackGenerator(style: .light) */
    fun impactLight() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CLOCK_TICK
        else HapticFeedbackConstants.KEYBOARD_TAP
    )

    /** UIImpactFeedbackGenerator(style: .medium) */
    fun impactMedium() = perform(HapticFeedbackConstants.KEYBOARD_TAP)

    /** UIImpactFeedbackGenerator(style: .heavy) */
    fun impactHeavy() = perform(HapticFeedbackConstants.LONG_PRESS)

    /** UISelectionFeedbackGenerator().selectionChanged() */
    fun selection() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CLOCK_TICK
        else HapticFeedbackConstants.KEYBOARD_TAP
    )

    private fun perform(constant: Int) {
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberFormaHaptics(): FormaHaptics {
    val view = LocalView.current
    return remember(view) { FormaHaptics(view) }
}
