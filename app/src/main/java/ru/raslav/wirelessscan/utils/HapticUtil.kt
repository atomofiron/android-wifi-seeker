package ru.raslav.wirelessscan.utils

import android.view.HapticFeedbackConstants
import android.view.View

const val HAPTIC_LITE = HapticFeedbackConstants.CLOCK_TICK
const val HAPTIC_HEAVY = HapticFeedbackConstants.KEYBOARD_TAP

fun View.setHapticEffect(enabled: Boolean) {
    rootView.isHapticFeedbackEnabled = enabled
}

fun View.performHapticEffect(effect: Int) {
    rootView.performHapticFeedback(effect)
}

fun View.performHapticLite() = performHapticEffect(HAPTIC_LITE)

fun View.performHapticHeavy() = performHapticEffect(HAPTIC_HEAVY)
