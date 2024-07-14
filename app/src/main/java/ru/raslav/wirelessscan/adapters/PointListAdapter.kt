package ru.raslav.wirelessscan.adapters

import android.animation.ValueAnimator
import android.content.Context
import android.net.wifi.WifiInfo
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.M
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import ru.raslav.wirelessscan.Const
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.databinding.LayoutItemBinding
import ru.raslav.wirelessscan.isWide
import ru.raslav.wirelessscan.report
import ru.raslav.wirelessscan.utils.Point
import ru.raslav.wirelessscan.utils.SideDrawable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class AnimType {
    None, ScanStart, ScanEnd
}

private class Holder(
    var index: Int = -1,
    val binding: LayoutItemBinding,
) {
    override fun equals(other: Any?): Boolean = other is Holder && other.binding === binding
    override fun hashCode(): Int = binding.hashCode()
}

class PointListAdapter(context: Context) : BaseAdapter(), View.OnAttachStateChangeListener,
    ValueAnimator.AnimatorUpdateListener {
    companion object {
        const val FILTER_DEFAULT = 0
        const val FILTER_INCLUDE = 1
        const val FILTER_EXCLUDE = 2
    }
    private val filterValues = arrayOf("WPA", "PSK", "EAP", "CCMP", "TKIP", "WPS", "P2P", "WEP", "HIDDEN")
    private val filter: IntArray = IntArray(filterValues.size)
    val allPoints = mutableListOf<Point>()
    private val points = mutableListOf<Point>()
    private var focused: Point? = null
    private var filtering = false
    private val focusedDrawable = SideDrawable(
        ContextCompat.getColor(context, R.color.grey),
        context.resources.getDimension(R.dimen.one),
    )
    private val holders = mutableListOf<Holder>()
    var connectionInfo: WifiInfo? = null
        set(value) { field = value; notifyDataSetChanged() }

    private val animScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    private var animType = AnimType.None
    private val animator = ValueAnimator.ofFloat(Const.ALPHA_ZERO, Const.ALPHA_FULL)

    operator fun get(index: Int): Point = points[index]

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder: Holder = if (convertView == null) {
            //android.view.InflateException: Binary XML file line #64: addView(View, LayoutParams) is not supported in AdapterView
            //Caused by: java.lang.UnsupportedOperationException: addView(View, LayoutParams) is not supported in AdapterView
            val itemView = LayoutInflater.from(parent.context).inflate(R.layout.layout_item, parent, false)
            itemView.addOnAttachStateChangeListener(this)
            val binding = LayoutItemBinding.bind(itemView)
            val holder = Holder(binding = binding)
            itemView.tag = holder

            binding.pwr.text = "\u25CF " // ●
            binding.pwr.gravity = Gravity.END
            holder
        } else
            convertView.tag as Holder

        holder.index = position
        fillView(holder.binding, points[position], position)

        return holder.binding.root
    }

    private fun fillView(holder: LayoutItemBinding, point: Point, position: Int) {
        drawItemRoot(holder.root, point, position)

        // point.level == -1 experiment
        holder.pwr.setTextColor(if (point.level == -1) -65281 else point.pwColor)

        holder.ch.text = point.ch.toString()
        holder.ch.setTextColor(point.chColor)

        holder.enc.text = point.enc
        holder.enc.setTextColor(point.encColor)

        holder.chi.text = point.chi
        holder.chi.setTextColor(point.chiColor)

        holder.wps.text = point.wps
        holder.wps.setTextColor(point.wpsColor)

        val connected = connectionInfo?.bssid == point.bssid
        holder.essid.text = if (point.essid.isEmpty()) point.bssid else point.essid
        holder.essid.setTextColor(when {
            connected -> Point.green_light
            point.essid.isEmpty() -> Point.yellow
            else -> Point.grey
        })

        holder.bssid.text = point.bssid
        holder.bssid.setTextColor(if (connected) Point.green_light else Point.grey)
        holder.bssid.isVisible = holder.root.resources.configuration.isWide()
    }

    private fun drawItemRoot(layout: LinearLayout, point: Point, position: Int) {
        val focused = focused
        val associating =  when {
            focused == null -> false
            focused.hex.isEmpty() && point.hex.isEmpty() -> focused.bssid.startsWith(point.bssid.substring(0, 8))
            else -> focused.hex == point.hex
        }
        if (SDK_INT >= M) layout.foreground = if (point.bssid == focused?.bssid) focusedDrawable else null
        if (associating)
            layout.setBackgroundResource(if (point.level <= Point.MIN_LEVEL) R.drawable.grille_red else R.drawable.grille)
        else
            layout.setBackgroundColor(when {
                point.level <= Point.MIN_LEVEL -> Point.red_lite
                position % 2 == 0 -> Point.transparent
                else -> Point.black_lite
            })

        if (point.level == -1)
            report("WOW: point.level == -1")
    }

    fun setFocused(point: Point) {
        focused = point
        notifyDataSetChanged()
    }

    fun resetFocus() {
        focused = null
        notifyDataSetChanged()
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
        notifyDataSetChanged()
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
            notifyDataSetChanged()
    }

    fun clear(): String {
        allPoints.clear()
        points.clear()

        notifyDataSetChanged()
        return getCounters()
    }

    override fun getItem(position: Int): Point = points[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getCount(): Int = points.size

    override fun onViewAttachedToWindow(view: View) {
        val holder = view.tag as Holder
        holders.add(holder)
        holder.binding.pwr.alpha = Const.ALPHA_FULL
    }

    override fun onViewDetachedFromWindow(view: View) {
        holders.remove(view.tag as Holder)
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
                holder.binding.pwr.alpha = if (holder.index % 2 == 0) value else (Const.ALPHA_FULL - value)
            }
        } else if (animType == AnimType.ScanEnd) {
            var min = Int.MAX_VALUE
            var max = Int.MIN_VALUE
            for (holder in holders) {
                min = min(min, holder.index)
                max = max(max, holder.index)
            }
            val threshold = (min + (max - min) * (Const.ALPHA_FULL - value)).roundToInt()
            for (holder in holders) {
                holder.binding.pwr.alpha = if (holder.index >= threshold) Const.ALPHA_FULL else Const.ALPHA_ZERO
            }
            if (value == Const.ALPHA_FULL) {
                animType = AnimType.None
            }
        }
    }
}
