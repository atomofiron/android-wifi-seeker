package ru.raslav.wirelessscan.adapters

import androidx.recyclerview.widget.DiffUtil
import ru.raslav.wirelessscan.data.Point

object PointItemCallback : DiffUtil.ItemCallback<Point>() {

    override fun areItemsTheSame(old: Point, new: Point): Boolean = old.theSame(new)

    override fun areContentsTheSame(old: Point, new: Point): Boolean = old == new
}