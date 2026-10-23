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
import androidx.core.view.children
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.colorAttr
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
        val previous = parent.preferenceOf(view, -1)
        val next = parent.preferenceOf(view, 1)
        val first = previous?.parent !== group
        val last = next?.parent !== group
        outRect.set(
            padding,
            offsetFor(first, preference.isMergedWith(previous)),
            padding,
            offsetFor(last, preference.isMergedWith(next)),
        )
        val drawable = view.setTranslucentDrawable()
        if (drawable != null && view.outlineProvider !is OutlineProvider) {
            view.outlineProvider = OutlineProvider(drawable)
            view.clipToOutline = true
        }
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        for (child in parent.children) {
            val item = child.tag as? Item ?: Item()
            item.preference = parent.preferenceOf(child)
            child.tag = item
        }
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            val item = child.tag as? Item ?: continue
            val group = item.group ?: continue
            val previousChild = parent.getChildAt(index.dec())
            val nextChild = parent.getChildAt(index.inc())
            val previous = (previousChild?.tag as? Item)?.preference
            val next = (nextChild?.tag as? Item)?.preference
            val first = previous?.parent !== group
            val last = next?.parent !== group
            val topDistance = distanceBetween(previousChild, child)
            val bottomDistance = distanceBetween(child, nextChild)
            val drawable = child.getTranslucentDrawable()
            if (drawable != null && child.outlineProvider !is OutlineProvider) {
                child.outlineProvider = OutlineProvider(drawable)
                child.clipToOutline = true
                drawable.colors = intArrayOf(paint.color, paint.color)
            }
            val changed = item.updateRadii(child, radiiFor(first, topDistance), radiiFor(last, bottomDistance))
            if (changed && drawable != null) {
                drawable.cornerRadii = item.radii
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

    private fun distanceBetween(above: View?, below: View?): Float = when {
        above == null || below == null -> edgeRadius
        else -> below.y - above.y - above.height
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
        if (position !in 0 until adapter.itemCount) {
            return null
        }
        val preference = adapter.getItem(position) ?: return null
        return when {
            !preference.isVisible -> null
            preference is PreferenceScreen -> preference
            preference is PreferenceGroup -> null
            else -> preference
        }
    }

    private fun View.setTranslucentDrawable(): TranslucentGradientDrawable? {
        return when (val drawable = background) {
            is RippleDrawable -> drawable.findDrawableByLayerId(android.R.id.mask) as? TranslucentGradientDrawable
                ?: TranslucentGradientDrawable(paint.color).also { new ->
                    var index = drawable.findIndexByLayerId(android.R.id.mask)
                    if (index < 0) {
                        index = drawable.addLayer(new)
                        drawable.setId(index, android.R.id.mask)
                    }
                    drawable.setDrawableByLayerId(android.R.id.mask, new)
                }
            is TranslucentGradientDrawable -> drawable
            is ColorDrawable -> TranslucentGradientDrawable(paint.color)
                .also { background = it }
            null -> TranslucentGradientDrawable(paint.color)
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

    private class Item {

        var preference: Preference? = null
        val group: PreferenceGroup? get() = preference?.parent
        val radii = FloatArray(8)
        val path = Path()
        private var size = Size()

        fun updateRadii(view: View, top: Float, bottom: Float): Boolean {
            val new = Size(view.width, view.height)
            val changed = radii[0] != top || radii[4] != bottom || new != size
            for (i in radii.indices) {
                radii[i] = if (i < 4) top else bottom
            }
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
