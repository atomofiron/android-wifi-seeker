package ru.raslav.wirelessscan.fragments.delegates

import android.view.View
import android.view.View.OVER_SCROLL_NEVER
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import androidx.core.content.ContextCompat
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.absoluteElevation
import ru.raslav.wirelessscan.completeChildren
import ru.raslav.wirelessscan.data.PointFilter
import ru.raslav.wirelessscan.databinding.ChipBinding
import ru.raslav.wirelessscan.inflater
import ru.raslav.wirelessscan.updateMarginLayoutParams
import kotlin.math.max

class FilterDelegate(
    private val onFilterChanged: (PointFilter) -> Unit,
) {
    fun init(layout: ViewGroup) {
        (layout.parent as? HorizontalScrollView)?.overScrollMode = OVER_SCROLL_NEVER
        val inflater = layout.context.inflater()
        layout.completeChildren(
            count = PointFilter.labels.size,
            factory = { ChipBinding.inflate(inflater, this, false).root },
        ) { index ->
            elevation = max(0f, resources.getDimension(R.dimen.chip_elevation) - absoluteElevation())
            ensureAccessibleTouchTarget(0)
            setTextColor(ContextCompat.getColorStateList(context, R.color.filter_text))
            updateMarginLayoutParams {
                val margin = resources.getDimensionPixelSize(R.dimen.padding_mini)
                marginStart = margin
                marginEnd = margin
                topMargin = margin
                bottomMargin = margin
            }
            val label = PointFilter.labels[index]
            text = label
            setOnClickListener {
                onFilterClick(it, label)
            }
        }
    }

    private fun onFilterClick(view: View, label: String) {
        var include = false
        var exclude = false
        when {
            view.isActivated -> {
                view.isActivated = false
                view.isSelected = false
            }
            view.isSelected -> {
                view.isActivated = true
                exclude = true
            }
            else -> {
                view.isSelected = true
                include = true
            }
        }
        val filter = PointFilter.defaults
            .find { it.label == label }
            ?.copy(include = include, exclude = exclude)
            ?: return
        onFilterChanged(filter)
    }
}