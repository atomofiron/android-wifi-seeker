package ru.raslav.wirelessscan.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.alpha
import androidx.recyclerview.widget.RecyclerView
import ru.raslav.wirelessscan.x

/**
 * Paints row backgrounds behind the item views, so rows can alternate shades and show state
 * (out of range) without each holder touching its own background. Fully transparent colors
 * are skipped, which is how an even row stays unpainted.
 */
class RowBackgroundDecoration(
    private val backgroundAt: (position: Int) -> Int,
) : RecyclerView.ItemDecoration() {

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
            val color = backgroundAt(position)
            if (color.alpha != 0) {
                paint.color = color
                canvas.drawRect(0f, child.x(), parent.width.toFloat(), child.x() + child.height, paint)
            }
        }
    }
}
