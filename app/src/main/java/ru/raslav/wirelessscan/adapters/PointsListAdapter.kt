package ru.raslav.wirelessscan.adapters

import android.content.Context
import android.net.wifi.WifiInfo
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import ru.raslav.wirelessscan.I
import ru.raslav.wirelessscan.I.Companion.WIDE_MODE
import kotlinx.android.synthetic.main.layout_item.view.*
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.room.Point


class PointsListAdapter(private val co: Context, private val listView: ListView) : BaseAdapter() {
    companion object {
        val FILTER_DEFAULT = 0
        val FILTER_INCLUDE = 1
        val FILTER_EXCLUDE = 2
    }
    private val filterValues = arrayOf("WPA", "PSK", "EAP", "CCMP", "TKIP", "WPS", "P2P", "WEP", "HIDDEN")
    private val filter: IntArray = IntArray(filterValues.size)
    val allPoints = ArrayList<Point>()
    private val points = ArrayList<Point>()
    var focuse: Point? = null
        private set
    private var transparent = 0
    private var filtering = false
    var connectionInfo: WifiInfo? = null
        set(value) { field = value; notifyDataSetChanged() }

    var onPointClickListener: (point: Point?) -> Unit = {}

    init {
        Point.initColors(co.resources)
        transparent = co.resources.getColor(R.color.transparent)
        listView.setOnItemClickListener { _, _, position, _ ->
            focuse = points[position]
            onPointClickListener(focuse)
            notifyDataSetChanged()
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var convertView = convertView
        val holder: ViewHolder
        if (convertView == null) {
            //android.view.InflateException: Binary XML file line #64: addView(View, LayoutParams) is not supported in AdapterView
            //Caused by: java.lang.UnsupportedOperationException: addView(View, LayoutParams) is not supported in AdapterView
            convertView = LayoutInflater.from(co).inflate(R.layout.layout_item, null)
            holder = ViewHolder(convertView as LinearLayout)
            convertView.tag = holder

            holder.pw.text = "\u25CF " // ●
            holder.pw.gravity = Gravity.RIGHT

            if (WIDE_MODE)
                holder.bssid.visibility = View.VISIBLE
        } else
            holder = convertView.tag as ViewHolder

        fillView(holder, points[position], position)

        return convertView
    }

    private fun fillView(holder: ViewHolder, point: Point, position: Int) {
        drawItemRoot(holder.root, point, position)

        // point.level == -1 experiment
        holder.pw.setTextColor(if (point.level == -1) -65281 else point.pwColor)

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
        holder.essid.setTextColor(if (connected) Point.green_light else point.essidColor)

        holder.bssid.text = point.bssid
        holder.bssid.setTextColor(if (connected) Point.green_light else point.bssidColor)
    }

    private fun drawItemRoot(layout: LinearLayout, point: Point, position: Int) {
        val associating = focuse?.bssid?.startsWith(point.bssid.substring(0, 8)) ?: false
        if (associating)
            layout.setBackgroundResource(if (point.level <= Point.MIN_LEVEL) R.drawable.grille_red else R.drawable.grille)
        else
            layout.setBackgroundColor(when {
                point.level <= Point.MIN_LEVEL -> Point.red_lite
                position % 2 == 0 -> Point.transparent
                else -> Point.black_lite
            })

        if (point.level == -1)
            I.log("WOW: point.level == -1")
    }

    fun resetFocus() {
        focuse = null
        onPointClickListener(null)
    }

    /** @return counters like '15/22' or '5/22   15/22' */
    private fun getCounters(): String {
        var count = allPoints.size
        allPoints.forEach { it -> if (it.level == Point.MIN_LEVEL) count-- }
        return "${if (filtering) "${points.size}/${allPoints.size}" else ""}   $count/${allPoints.size}"
    }

    fun updateList(list: ArrayList<Point>?) : String {
        allPoints.clear()
        points.clear()

        if (list != null)
            allPoints.addAll(list)

        applyFilter()
        notifyDataSetChanged()
        anim()
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
        val prevPoints = ArrayList(points)
        points.clear()
        points.addAll(allPoints)

        if (filtering) {
            var n = 0
            loop@ while (n < points.size) {
                for (i in 0 until filter.size)
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

    private fun anim() {
        val count = listView.childCount
        if (count > 0) {
            for (i in 0 until count) {
                val anim = AnimationUtils.loadAnimation(co, R.anim.show)
                anim.startOffset = 500L / count * (count - 1 - i)
                listView.getChildAt(i).pwr.startAnimation(anim)
            }
        }
    }

    fun animScan(start: Boolean) {
        val count = listView.childCount
        if (count > 0)
            for (i in 0 until count)
                if (start)
                    listView.getChildAt(i).pwr.startAnimation(AnimationUtils.loadAnimation(co, if (i % 2 == 0) R.anim.blink_0 else R.anim.blink_1))
                else
                    listView.getChildAt(i).pwr.animation = null
    }

    override fun getItem(position: Int): Point = points[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getCount(): Int = points.size

    class ViewHolder(layout: LinearLayout) {
        val root = layout
        val pw = layout.pwr
        val ch = layout.ch
        val enc = layout.enc
        val chi = layout.chi
        val wps = layout.wps
        val essid = layout.essid
        val bssid = layout.bssid
    }
}
