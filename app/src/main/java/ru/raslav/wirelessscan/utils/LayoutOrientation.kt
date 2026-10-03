package ru.raslav.wirelessscan.utils

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.LayoutDirection
import android.view.Display
import android.view.Surface
import android.view.View
import ru.raslav.wirelessscan.R

class LayoutOrientation private constructor(
    private val view: View,
    private val range: IntRange,
    private val listener: ((Orientation) -> Unit)?,
) : View.OnLayoutChangeListener {
    companion object {

        fun View.layoutChanges(listener: (Orientation) -> Unit) {
            val range = resources.run { getDimensionPixelSize(R.dimen.compact_width)..getDimensionPixelSize(R.dimen.medium_width) }
            addOnLayoutChangeListener(LayoutOrientation(this, range, listener))
        }

        fun View.layoutOrientation(): LayoutOrientation {
            val range = resources.run { getDimensionPixelSize(R.dimen.compact_width)..getDimensionPixelSize(R.dimen.medium_width) }
            return LayoutOrientation(this, range, listener = null)
        }
    }

    private var orientation: Orientation? = null

    fun orientation(): Orientation {
        return view.calc(view.left, view.top, view.right, view.bottom)
            .also { orientation = it }
    }

    override fun onLayoutChange(layout: View, left: Int, top: Int, right: Int, bottom: Int, ol: Int, ot: Int, or: Int, ob: Int) {
        val orientation = layout.calc(left, top, right, bottom)
        if (orientation != this.orientation) {
            this.orientation = orientation
            listener?.invoke(orientation)
        }
    }

    private fun View.calc(left: Int, top: Int, right: Int, bottom: Int): Orientation {
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
        val display = context.getSystemService(Context.DISPLAY_SERVICE)
            .let { it as? DisplayManager }
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        val rtl = layoutDirection == LayoutDirection.RTL
        val orientation = when {
            vertical -> Orientation.Bottom
            display?.rotation == Surface.ROTATION_90 -> if (rtl) Orientation.Start(true) else Orientation.End(false)
            display?.rotation == Surface.ROTATION_270 -> if (rtl) Orientation.End(true) else Orientation.Start(false)
            else -> Orientation.Bottom
        }
        return orientation
    }
}