package ru.raslav.wirelessscan.adapters

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.raslav.wirelessscan.Const
import ru.raslav.wirelessscan.clearOutOfRange
import ru.raslav.wirelessscan.clipboardManager
import ru.raslav.wirelessscan.data.CurrentConnection
import ru.raslav.wirelessscan.data.Point
import ru.raslav.wirelessscan.data.PointColors
import ru.raslav.wirelessscan.data.PointFilter
import ru.raslav.wirelessscan.databinding.ItemPointBinding
import ru.raslav.wirelessscan.utils.AlternatingDecoration.Colors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class AnimType {
    None, ScanStart, ScanEnd
}

class PointListAdapter(context: Context) : ListAdapter<Point, PointHolder>(PointItemCallback)
    , ValueAnimator.AnimatorUpdateListener
{

    private val colors = PointColors(context)
    private val filters = mutableListOf<PointFilter>()
    val points = mutableListOf<Point>()
    private var focused: Point? = null
    private var filtering = false
    private val closeDescription: (View) -> Unit = { resetFocus() }
    private val holders = mutableListOf<PointHolder>()
    private var current: CurrentConnection? = null

    private val animScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    private var animType = AnimType.None
    private val animator = ValueAnimator.ofFloat(Const.ALPHA_ZERO, Const.ALPHA_FULL)
    private val clipboard = context.clipboardManager()

    init {
        setHasStableIds(true)
    }

    fun isNotEmpty() = points.isNotEmpty()

    override fun getItemId(position: Int): Long = currentList[position].keyHash()

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

    override fun onBindViewHolder(holder: PointHolder, position: Int) = holder.bind(currentList[position], focused, current)

    /** Row background for RowBackgroundDecoration: alternating shades plus the out-of-range state. */
    fun backgroundAt(position: Int): Colors = when {
        currentList[position].outOfRange -> Colors(colors.blackLite, colors.redDarkLite)
        else -> Colors(colors.blackLite)
    }

    private fun onClick(position: Int) {
        val point = currentList[position]
        when (point.bssid) {
            focused?.bssid -> resetFocus()
            else -> setFocused(point)
        }
    }

    private fun setFocused(point: Point) {
        notifyChanged(keyHash = focused?.keyHash())
        focused = point
        notifyChanged(keyHash = point.keyHash())
    }

    fun resetFocus() {
        notifyChanged(keyHash = focused?.keyHash())
        focused = null
    }

    /** @return counters like '15 / 22' or '5 / 15 / 22' */
    private fun getCounters(filtered: Int): String {
        val count = points.count { it.level > Point.range.first }
        return "${if (filtering) "$filtered / " else ""}$count / ${points.size}"
    }

    fun updateList(list: List<Point>?) : String {
        points.clear()
        if (list != null) {
            points.addAll(list)
        }
        return getCounters(applyFilter())
    }

    fun filter(enable: Boolean) : String {
        filtering = enable
        return getCounters(applyFilter())
    }

    fun updateFilter(filter: PointFilter): String {
        val index = filters.indexOfFirst { it.label == filter.label }
        when {
            !filter.isEmpty && index < 0 -> filters.add(filter)
            !filter.isEmpty && index >= 0 -> filters[index] = filter
            filter.isEmpty && index >= 0 -> filters.removeAt(index)
        }
        return getCounters(applyFilter())
    }

    fun setCurrent(current: CurrentConnection?) {
        if (current != this.current) {
            notifyChanged(bssid = this.current?.bssid)
            this.current = current
            notifyChanged(bssid = current?.bssid)
        }
    }

    private fun notifyChanged(
        bssid: String? = null,
        keyHash: Long? = null,
    ) {
        currentList.forEachIndexed { index, point ->
            when {
                bssid != null && point.bssid == bssid -> Unit
                keyHash != null && point.keyHash() == keyHash -> Unit
                else -> return@forEachIndexed
            }
            notifyItemChanged(index)
        }
    }

    private fun applyFilter(): Int {
        val new = points.filter { point ->
            filters.all {
                when {
                    it.hidden && it.exclude && point.isHidden() -> false
                    it.capabilities && it.exclude && point.capabilities.contains(it.label) -> false
                    it.hidden && it.include -> point.isHidden()
                    it.capabilities && it.include -> point.capabilities.contains(it.label)
                    else -> true
                }
            }
        }
        submitList(new)
        return new.size
    }

    fun clear(): String {
        points.clear()
        submitList(emptyList())
        return getCounters(0)
    }

    fun clearOutOfRange(): String {
        points.clearOutOfRange()
        return getCounters(applyFilter())
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
