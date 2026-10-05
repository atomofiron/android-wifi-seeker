package ru.raslav.wirelessscan.adapters

import android.animation.ValueAnimator
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import ru.raslav.wirelessscan.Const
import ru.raslav.wirelessscan.Const.ALPHA_INT_HALF
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.clearOutOfRange
import ru.raslav.wirelessscan.copy
import ru.raslav.wirelessscan.data.CurrentConnection
import ru.raslav.wirelessscan.data.Point
import ru.raslav.wirelessscan.databinding.LayoutDescriptionBinding
import ru.raslav.wirelessscan.databinding.LayoutItemBinding
import ru.raslav.wirelessscan.elog
import ru.raslav.wirelessscan.isRtl
import ru.raslav.wirelessscan.isVisible
import ru.raslav.wirelessscan.isWide
import ru.raslav.wirelessscan.utils.SideDrawable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class AnimType {
    None, ScanStart, ScanEnd
}

class PointLHolder(val binding: LayoutItemBinding) : RecyclerView.ViewHolder(binding.root)

class PointListAdapter(context: Context) : RecyclerView.Adapter<PointLHolder>(),
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
    private val closeDescription: (View) -> Unit = { resetFocus() }
    private var filtering = false
    private val focusedDrawable = SideDrawable(
        ContextCompat.getColor(context, R.color.gray),
        context.resources.getDimension(R.dimen.one),
    )
    private val holders = mutableListOf<PointLHolder>()
    private var current: CurrentConnection? = null

    private val animScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    private var animType = AnimType.None
    private val animator = ValueAnimator.ofFloat(Const.ALPHA_ZERO, Const.ALPHA_FULL)
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PointLHolder {
        val binding = LayoutItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val holder = PointLHolder(binding)

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

    override fun onBindViewHolder(holder: PointLHolder, position: Int) {
        holder.binding.fillView(points[position])
    }

    override fun getItemCount(): Int = points.size

    /** Row background for RowBackgroundDecoration: alternating shades plus the out-of-range state. */
    fun backgroundAt(position: Int): Int {
        val point = points[position]
        val even = position % 2 == 0
        return when {
            point.outOfRange -> if (even) Point.red_lite else Point.red_dark_lite
            even -> Point.transparent
            else -> Point.black_lite
        }
    }

    private fun LayoutItemBinding.fillView(point: Point) {
        drawItemRoot(itemColumns, point)
        val connected = point.bssid == current?.bssid
        updateDescription(
            point.takeIf { it.bssid == focused?.bssid },
            current?.takeIf { connected },
        )
        root.foreground = if (point.bssid == focused?.bssid) focusedDrawable else null
        focusedDrawable.setRtl(root.isRtl())

        // point.level == -1 experiment
        pwr.setTextColor(if (point.level == -1) -65281 else point.pwColor)

        ch.text = point.ch.toString()
        ch.setTextColor(point.chColor)

        enc.text = point.enc
        enc.setTextColor(point.encColor)

        cip.text = point.cip
        cip.setTextColor(point.cipColor)

        wps.text = point.wps
        wps.setTextColor(point.wpsColor)

        essid.text = when {
            point.essid.isEmpty() -> point.bssid
            point.essid.isVisible() -> point.essid
            else -> point.essidHex
        }
        when {
            connected -> Point.green_light
            point.essid.isEmpty() -> Point.yellow
            point.essid.isVisible() -> Point.gray
            else -> Point.yellow
        }.let { essid.setTextColor(it) }
        bssid.text = point.bssid
        bssid.setTextColor(if (connected) Point.green_light else Point.gray)
        bssid.isVisible = root.resources.configuration.isWide()
    }

    private fun drawItemRoot(layout: LinearLayout, point: Point) {
        val focused = focused
        val associating =  when {
            focused == null -> false
            focused.bssidHex.isEmpty() && point.bssidHex.isEmpty() -> focused.bssid.startsWith(point.bssid.substring(0, 8))
            else -> focused.bssidHex == point.bssidHex
        }
        when {
            !associating -> layout.background = null
            point.level <= Point.MIN_LEVEL -> layout.setBackgroundResource(R.drawable.grille_red)
            else -> layout.setBackgroundResource(R.drawable.grille)
        }

        if (point.level == -1) {
            elog("WOW: point.level == -1")
        }
    }

    private fun LayoutItemBinding.updateDescription(point: Point?, current: CurrentConnection?) {
        val description = root.findViewById<View>(R.id.layout_description)
            ?.let { LayoutDescriptionBinding.bind(it) }
        when {
            point != null && description != null -> description.bind(point, current)
            point == null && description != null -> root.removeView(description.root)
            point != null && description == null -> LayoutInflater.from(root.context)
                .let { LayoutDescriptionBinding.inflate(it, root, true) }
                .init()
                .bind(point, current)
        }
    }

    private fun LayoutDescriptionBinding.init(): LayoutDescriptionBinding {
        val copy = ContextCompat.getDrawable(root.context, R.drawable.ic_copy)!!
        val size = tvEssid.textSize.toInt()
        copy.setBounds(0, 0, size, size)
        copy.alpha = ALPHA_INT_HALF
        copy.setTintList(tvEssid.textColors)
        arrayOf(tvEssid, tvEssidHex, tvBssid, tvIp, tvGateway).forEach { tv ->
            tv.setCompoundDrawablesRelative(null, null, copy, null)
            tv.compoundDrawablePadding = size / 2
            tv.setOnClickListener { tv.onCopiableClick() }
        }
        return this
    }

    private fun TextView.onCopiableClick() {
        val parts = text.toString()
            .split(": ")
        val data = parts.getOrNull(1) ?: return
        val label = parts.first()
        clipboard.copy(context, label, data)
    }

    private fun LayoutDescriptionBinding.bind(point: Point, current: CurrentConnection?) {
        val resources = root.resources
        tvEssid.text = when {
            point.essid.isEmpty() -> resources.yellow("")
            else -> resources.getString(R.string.essid_format, point.essid)
        }
        tvEssidHex.text = point.essidHex.takeIf { it.isNotEmpty() }
            ?.let { resources.getString(R.string.essid_hex_format, it) }
        tvEssidHex.isVisible = point.essidHex.isNotEmpty()
        tvBssid.text = resources.getString(R.string.bssid_format, point.bssid)
        tvIp.text = current?.address?.takeIf { it.isNotEmpty() }
            ?.let { resources.getString(R.string.ip_format, it) }
        tvIp.isVisible = !current?.address.isNullOrEmpty()
        tvGateway.text = current?.gateway?.takeIf { it.isNotEmpty() }
            ?.let { resources.getString(R.string.gateway_format, it) }
        tvGateway.isVisible = !current?.gateway.isNullOrEmpty()
        tvCapab.text = resources.getString(R.string.capab_format, point.capabilities)
        tvFrequ.text = resources.getString(R.string.frequ_format, point.frequency, point.ch, point.level)
        tvManuf.text = resources.getString(R.string.manuf_format, point.manufacturer)
        tvManufDesc.text = point.manufacturerDesc
        tvManufDesc.isVisible = point.manufacturerDesc.isNotBlank()
        cross.setOnClickListener(closeDescription)
    }

    private fun Resources.yellow(text: String): CharSequence {
        val text = text.takeIf { it.isNotEmpty() } ?: getString(R.string.essid_empty)
        return SpannableStringBuilder(getString(R.string.essid_format, text)).apply {
            setSpan(ForegroundColorSpan(Point.yellow), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
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

    fun setCurrent(current: CurrentConnection?) {
        this.current = current
        notifyDataSetChanged()
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

    fun clearOutOfRange(): String {
        allPoints.clearOutOfRange()
        points.clearOutOfRange()

        notifyDataSetChanged()
        return getCounters()
    }

    override fun onViewAttachedToWindow(holder: PointLHolder) {
        holders.add(holder)
        holder.binding.pwr.alpha = Const.ALPHA_FULL
    }

    override fun onViewDetachedFromWindow(holder: PointLHolder) {
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
