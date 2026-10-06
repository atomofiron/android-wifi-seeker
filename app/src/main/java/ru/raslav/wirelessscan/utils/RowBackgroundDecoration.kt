package ru.raslav.wirelessscan.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.annotation.ColorRes
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.alpha
import androidx.recyclerview.widget.RecyclerView
import ru.raslav.wirelessscan.y

/**
 * Paints row backgrounds behind the item views, so rows can alternate shades and show state
 * (out of range) without each holder touching its own background. Fully transparent colors
 * are skipped, which is how an even row stays unpainted.
 */
class RowBackgroundDecoration(
    private val backgroundAt: (position: Int) -> Colors,
) : RecyclerView.ItemDecoration() {

    @JvmInline
    value class Colors private constructor(private val value: ULong) {
        companion object {
            private const val MASK: ULong = 0xFFFF_FFFFuL
        }
        constructor(
            @ColorRes alternating: Int,
            @ColorRes overlay: Int = 0,
        ) : this(alternating.toULong().shl(32) + overlay.toULong())

        operator fun component1() = value.shr(32).toInt()
        operator fun component2() = value.and(MASK).toInt()
    }

    private val paint = Paint().apply {
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) {
                continue
            }
            val (alternating, overlay) = backgroundAt(position)
            val evenIndex = position and 1 == 0
            var color = if (evenIndex) Color.TRANSPARENT else alternating
            if (evenIndex) {
                val prev = parent.getChildAt(index.dec())
                val bottom = prev?.run { y() + height } ?: parent.paddingTop.toFloat()
                val top = child.y()
                if (top > bottom) {
                    val ratio = ((top - bottom) / child.height * 2)
                        .coerceIn(0f, 1f)
                    color = ColorUtils.blendARGB(Color.TRANSPARENT, alternating, ratio)
                }
            }
            if (overlay.alpha != 0) {
                color = ColorUtils.compositeColors(overlay, color)
            }
            if (color.alpha != 0) {
                paint.color = color
                canvas.drawRect(0f, child.y(), parent.width.toFloat(), child.y() + child.height, paint)
            }
        }
    }
}
