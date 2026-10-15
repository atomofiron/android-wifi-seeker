package ru.raslav.wirelessscan.utils

import android.view.View

private const val DOUBLE_CLICK_DELAY = 500L

/**
 * Tells single clicks from double ones: [onClick] runs immediately on the first click,
 * [onDoubleClick] runs on a second click within [DOUBLE_CLICK_DELAY]. While the second click is
 * pending the view stays activated, so its state drawables can hint at the pending action.
 */
fun View.setOnDoubleClickListener(
    onClick: ((View) -> Unit)? = null,
    onDoubleClick: (View) -> Unit,
) {
    var millisClickedFirst = 0L
    setOnClickListener { view ->
        if (millisClickedFirst == 0L) {
            millisClickedFirst = System.currentTimeMillis()
            view.isActivated = true
            val mcf = millisClickedFirst
            view.postDelayed({
                if (mcf == millisClickedFirst) {
                    millisClickedFirst = 0L
                    view.isActivated = false
                }
            }, DOUBLE_CLICK_DELAY)
            onClick?.invoke(view)
        } else {
            millisClickedFirst = 0L
            view.isActivated = false
            onDoubleClick(view)
        }
    }
}
