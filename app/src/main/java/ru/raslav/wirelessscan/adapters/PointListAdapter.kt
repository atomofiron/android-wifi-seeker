package ru.raslav.wirelessscan.adapters

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.raslav.wirelessscan.Const
import ru.raslav.wirelessscan.clearOutOfRange
import ru.raslav.wirelessscan.clipboardManager
import ru.raslav.wirelessscan.data.CurrentConnection
import ru.raslav.wirelessscan.data.Point
import ru.raslav.wirelessscan.data.PointColors
import ru.raslav.wirelessscan.databinding.ItemPointBinding
import ru.raslav.wirelessscan.utils.AlternatingDecoration.Colors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class AnimType {
    None, ScanStart, ScanEnd
}

class PointListAdapter(context: Context) : RecyclerView.Adapter<PointHolder>(),
    ValueAnimator.AnimatorUpdateListener {
    companion object {
        const val FILTER_DEFAULT = 0
        const val FILTER_INCLUDE = 1
        const val FILTER_EXCLUDE = 2
    }

    private val colors = PointColors(context)
    private val filterValues = arrayOf("WPA", "PSK", "EAP", "CCMP", "TKIP", "WPS", "P2P", "WEP", "HIDDEN")
    private val filter: IntArray = IntArray(filterValues.size)
    val allPoints = mutableListOf<Point>()
    private val points = mutableListOf<Point>()
    private var focused: Point? = null
    private var filtering = false
    private val closeDescription: (View) -> Unit = { resetFocus() }
    private val holders = mutableListOf<PointHolder>()
    private var current: CurrentConnection? = null

    private val animScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    private var animType = AnimType.None
    private val animator = ValueAnimator.ofFloat(Const.ALPHA_ZERO, Const.ALPHA_FULL)
    private val clipboard = context.clipboardManager()

    fun isNotEmpty() = allPoints.isNotEmpty()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PointHolder {
        val binding = ItemPointBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val holder = PointHolder(binding, colors, clipboard, closeDescription)

        binding.pwr.text = Const.Dot
        binding.pwr.gravity = Gravity.END
        binding.root.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                onClick(position)
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: PointHolder, position: Int) = holder.bind(points[position], focused, current)

    override fun getItemCount(): Int = points.size

    /** Row background for RowBackgroundDecoration: alternating shades plus the out-of-range state. */
    fun backgroundAt(position: Int): Colors = when {
        points[position].outOfRange -> Colors(colors.blackLite, colors.redDarkLite)
        else -> Colors(colors.blackLite)
    }

    private fun onClick(position: Int) {
        val point = points[position]
        when (point.bssid) {
            focused?.bssid -> resetFocus()
            else -> setFocused(point)
        }
    }

    private fun setFocused(point: Point) {
        focused = point
        notifyChanged()
    }

    fun resetFocus() {
        focused = null
        notifyChanged()
    }

    /** @return counters like '15 / 22' or '5 / 15 / 22' */
    private fun getCounters(): String {
        var count = allPoints.size
        allPoints.forEach { if (it.level == Point.MIN_LEVEL) count-- }
        return "${if (filtering) "${points.size} / " else ""}$count / ${allPoints.size}"
    }

    fun updateList(list: List<Point>?) : String {
        allPoints.clear()
        points.clear()

        if (list != null)
            allPoints.addAll(list)

        applyFilter()
        notifyChanged()
        return getCounters()
    }

    fun filter(enable: Boolean) : String {
        filtering = enable
        applyFilter()
        return getCounters()
    }

    fun updateFilter(which: Int, state: Int) : String {
        filter[which] = state
        applyFilter()
        return getCounters()
    }

    fun setCurrent(current: CurrentConnection?) {
        this.current = current
        notifyChanged()
    }

    @SuppressLint("NotifyDataSetChanged") // todo make Point's fields immutable
    fun notifyChanged() = notifyDataSetChanged()

    private fun applyFilter() {
        val prevPoints = points.toMutableList()
        points.clear()
        points.addAll(allPoints)

        if (filtering) {
            var n = 0
            loop@ while (n < points.size) {
                for (i in filter.indices)
                    if (i == filter.size - 1 && filter[i] != FILTER_DEFAULT && (filter[i] == FILTER_INCLUDE) != points[n].essid.isEmpty() ||
                            filter[i] != 0 && (filter[i] == FILTER_INCLUDE) != points[n].capabilities.contains(filterValues[i])) {
                        points.removeAt(n)
                        continue@loop
                    }
                n++
            }
        }

        if (prevPoints != points)
            notifyChanged()
    }

    fun clear(): String {
        allPoints.clear()
        points.clear()

        notifyChanged()
        return getCounters()
    }

    fun clearOutOfRange(): String {
        allPoints.clearOutOfRange()
        points.clearOutOfRange()

        notifyChanged()
        return getCounters()
    }

    override fun onViewAttachedToWindow(holder: PointHolder) {
        holders.add(holder)
        holder.binding.pwr.alpha = Const.ALPHA_FULL
    }

    override fun onViewDetachedFromWindow(holder: PointHolder) {
        holders.remove(holder)
    }

    fun animScanStart() {
        if (animScale <= 0f) return
        animType = AnimType.ScanStart
        animator.cancel()
        animator.duration = (100 / animScale).roundToInt().toLong()
        animator.repeatMode = ValueAnimator.REVERSE
        animator.repeatCount = ValueAnimator.INFINITE
        animator.start()
    }

    fun animScanEnd() {
        if (animScale <= 0f) return
        animType = AnimType.ScanEnd
        animator.cancel()
        animator.duration = (500 / animScale).roundToInt().toLong()
        animator.repeatCount = 0
        animator.start()
    }

    fun animScanCancel() {
        if (animType == AnimType.ScanStart) {
            animator.cancel()
            animType = AnimType.None
            for (holder in holders) {
                holder.binding.pwr.alpha = Const.ALPHA_FULL
            }
        }
    }

    fun initAnim() = animator.addUpdateListener(this)

    fun resetAnim() = animator.removeUpdateListener(this)

    override fun onAnimationUpdate(animation: ValueAnimator) {
        if (holders.isEmpty()) {
            return
        }
        val value = animation.animatedValue as Float
        if (animType == AnimType.ScanStart) {
            for (holder in holders) {
                holder.binding.pwr.alpha = if (holder.bindingAdapterPosition % 2 == 0) value else (Const.ALPHA_FULL - value)
            }
        } else if (animType == AnimType.ScanEnd) {
            var min = Int.MAX_VALUE
            var max = Int.MIN_VALUE
            for (holder in holders) {
                min = min(min, holder.bindingAdapterPosition)
                max = max(max, holder.bindingAdapterPosition)
            }
            val threshold = (min + (max - min) * (Const.ALPHA_FULL - value)).roundToInt()
            for (holder in holders) {
                holder.binding.pwr.alpha = if (holder.bindingAdapterPosition >= threshold) Const.ALPHA_FULL else Const.ALPHA_ZERO
            }
            if (value == Const.ALPHA_FULL) {
                animType = AnimType.None
            }
        }
    }
}
