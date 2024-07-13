package ru.raslav.wirelessscan.utils

import android.content.Context
import android.util.LayoutDirection
import android.view.Surface
import android.view.View
import android.view.WindowManager
import ru.raslav.wirelessscan.R

class LayoutDelegate private constructor(
    private val range: IntRange,
    private val listener: (Orientation) -> Unit,
) : View.OnLayoutChangeListener {
    companion object {
        fun View.layoutChanges(listener: (Orientation) -> Unit) {
            val range = resources.run { getDimensionPixelSize(R.dimen.compact_width)..getDimensionPixelSize(R.dimen.medium_width) }
            addOnLayoutChangeListener(LayoutDelegate(range, listener))
        }
    }

    private var orientation: Orientation? = null

    override fun onLayoutChange(layout: View, left: Int, top: Int, right: Int, bottom: Int, ol: Int, ot: Int, or: Int, ob: Int) {
        val width = right - left
        val height = bottom - top
        val vertical = when {
            width in range -> true
            height in range -> false
            width > range.last && height < range.first -> true
            width < range.first && height > range.last -> false
            width < range.first -> width > height
            else -> width < height
        }
        val display = (layout.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager?)!!.defaultDisplay
        val rtl = layout.layoutDirection == LayoutDirection.RTL
        val orientation = when {
            vertical -> Orientation.Bottom
            display.rotation == Surface.ROTATION_90 -> if (rtl) Orientation.Start else Orientation.End
            display.rotation == Surface.ROTATION_270 -> if (rtl) Orientation.End else Orientation.Start
            else -> Orientation.Bottom
        }
        if (orientation == this.orientation) {
            return
        }
        this.orientation = orientation
        listener(orientation)
    }
}