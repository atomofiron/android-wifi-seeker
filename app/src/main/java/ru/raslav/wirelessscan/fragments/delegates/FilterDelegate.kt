package ru.raslav.wirelessscan.fragments.delegates

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ru.raslav.wirelessscan.completeChildren
import ru.raslav.wirelessscan.data.PointFilter
import ru.raslav.wirelessscan.databinding.FilterBinding

class FilterDelegate(
    private val onFilterChanged: (PointFilter) -> Unit,
) {
    fun init(layout: ViewGroup) {
        val inflater = LayoutInflater.from(layout.context)
        layout.completeChildren(
            count = PointFilter.labels.size,
            factory = { FilterBinding.inflate(inflater, this, false).root },
        ) { index ->
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
            view.isSelected -> view.isSelected = false
            view.isActivated -> {
                view.isActivated = false
                view.isSelected = true
                exclude = true
            }
            else -> {
                view.isActivated = true
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