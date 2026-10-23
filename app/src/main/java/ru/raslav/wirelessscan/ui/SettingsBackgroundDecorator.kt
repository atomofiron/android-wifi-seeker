package ru.raslav.wirelessscan.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.graphics.withTranslation
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.colorAttr
import ru.raslav.wirelessscan.plus
import ru.raslav.wirelessscan.utils.MaterialAttr
import ru.raslav.wirelessscan.utils.Size
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class SettingsBackgroundDecorator(
    context: Context,
    private val mergedKeys: List<List<String>> = emptyList(),
) : RecyclerView.ItemDecoration() {

    private val paint = Paint()
    private val padding = context.resources.getDimensionPixelSize(R.dimen.padding_semi)
    private val edgeRadius = context.resources.getDimension(R.dimen.corner_extra)
    private val gap = context.resources.getDimension(R.dimen.padding_micro)

    init {
        paint.style = Paint.Style.FILL
        paint.color = context.colorAttr(MaterialAttr.colorSurfaceContainer)
        paint.isAntiAlias = true
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val preference = parent.preferenceOf(view) ?: return
        val group = preference.parent ?: return

        val item = view.tag as? Item ?: Item(preference)
        if (item.preference.key != preference.key) {
            item.previous = null
            item.next = null
        }
        item.preference = preference
        item.previous = parent.preferenceOf(view, -1) ?: item.previous
        item.next = parent.preferenceOf(view, 1) ?: item.next
        view.tag = item

        val first = item.previous?.parent !== group
        val last = item.next?.parent !== group
        outRect.set(
            padding,
            offsetFor(first, preference.isMergedWith(item.previous)),
            padding,
            offsetFor(last, preference.isMergedWith(item.next)),
        )
        val drawable = view.setTranslucentDrawable(paint.color)
        if (drawable != null && view.outlineProvider !is OutlineProvider) {
            view.outlineProvider = OutlineProvider(drawable)
            view.clipToOutline = true
        }
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        for (index in 0 until parent.childCount) {
            val child: View = parent.getChildAt(index)
            val item = child.tag as? Item ?: continue
            val group = item.group ?: continue
            val previousChild: View? = parent.getChildAt(index.dec())
            val nextChild: View? = parent.getChildAt(index.inc())
            val first = group !== item.previous?.parent
            val last = group !== item.next?.parent
            val topDistance = distanceBetween(previousChild, child, edge = first, current = item.preference, other = item.previous)
            val bottomDistance = distanceBetween(child, nextChild, edge = last, current = item.preference, other = item.next)
            val drawable = child.getTranslucentDrawable()
            val changed = item.updateRadii(child, radiiFor(first, topDistance), radiiFor(last, bottomDistance))
            if (changed && drawable != null) {
                drawable.cornerRadii = item.radii
                drawable.setBounds(0, 0, child.width, child.height)
            } else if (changed) {
                item.path.reset()
                item.path.addRoundRect(0f, 0f, child.width.toFloat(), child.height.toFloat(), item.radii, Path.Direction.CW)
            }
            canvas.withTranslation(child.left.toFloat(), child.y) {
                when (drawable) {
                    null -> drawPath(item.path, paint)
                    else -> drawable.draw(this)
                }
            }
        }
    }

    private fun offsetFor(edge: Boolean, merged: Boolean): Int = when {
        edge || merged -> 0
        else -> (gap / 2).roundToInt()
    }

    private fun radiiFor(edge: Boolean, distance: Float): Float = when {
        edge -> edgeRadius
        else -> max(min(distance, edgeRadius), 0f)
    }

    private fun distanceBetween(
        upper: View?,
        lower: View?,
        edge: Boolean,
        current: Preference,
        other: Preference?,
    ): Float = when {
        edge -> edgeRadius
        upper != null && lower != null -> lower.y - upper.y - upper.height
        current.isMergedWith(other) -> 0f
        else -> gap
    }

    private fun Preference.isMergedWith(other: Preference?): Boolean {
        if (other == null || other.parent !== parent) {
            return false
        }
        val key = key ?: return false
        val otherKey = other.key ?: return false
        return mergedKeys.any { key in it && otherKey in it }
    }

    @SuppressLint("RestrictedApi")
    private fun RecyclerView.preferenceOf(view: View, step: Int = 0): Preference? {
        val adapter = adapter as? PreferenceGroupAdapter ?: return null
        val position = getChildAdapterPosition(view) + step
        val preference = adapter.getItem(position)
        return when {
            preference == null -> null
            !preference.isVisible -> null
            preference is PreferenceScreen -> preference
            preference is PreferenceGroup -> null
            else -> preference
        }
    }

    private fun View.setTranslucentDrawable(color: Int): TranslucentGradientDrawable? {
        return when (val drawable = background) {
            is RippleDrawable -> drawable.findDrawableByLayerId(android.R.id.mask) as? TranslucentGradientDrawable
                ?: TranslucentGradientDrawable(color).also { new ->
                    var index = drawable.findIndexByLayerId(android.R.id.mask)
                    if (index < 0) {
                        index = drawable.addLayer(new)
                        drawable.setId(index, android.R.id.mask)
                    }
                    drawable.setDrawableByLayerId(android.R.id.mask, new)
                }
            is TranslucentGradientDrawable -> drawable
            is ColorDrawable -> TranslucentGradientDrawable(color)
                .also { background = it }
            null -> TranslucentGradientDrawable(color)
                .also { background = it }
            else -> null
        }
    }

    private fun View.getTranslucentDrawable(): TranslucentGradientDrawable? {
        return when (val drawable = background) {
            is RippleDrawable -> drawable.findDrawableByLayerId(android.R.id.mask) as? TranslucentGradientDrawable
            is TranslucentGradientDrawable -> drawable
            else -> null
        }
    }

    private class Item(var preference: Preference) {

        val group: PreferenceGroup? get() = preference.parent
        var previous: Preference? = null
        var next: Preference? = null
        val radii = FloatArray(8)
        val path = Path()
        private var size = Size()

        fun updateRadii(view: View, top: Float, bottom: Float): Boolean {
            val new = Size(view.width, view.height)
            var changed = radii[0] != top || radii[4] != bottom
            if (changed) for (i in radii.indices) {
                radii[i] = if (i < 4) top else bottom
            }
            changed += new != size
            size = new
            return changed
        }
    }

    private class OutlineProvider(private val drawable: Drawable) : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) = drawable.getOutline(outline)
    }

    private class TranslucentGradientDrawable(color: Int) : GradientDrawable(BOTTOM_TOP, intArrayOf(color, color)) {
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT // to dodge the bug in android 5.0-8.1
    }
}
