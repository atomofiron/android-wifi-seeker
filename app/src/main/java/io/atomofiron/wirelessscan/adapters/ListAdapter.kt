package io.atomofiron.wirelessscan.adapters

import android.content.Context
import android.net.NetworkInfo
import android.net.wifi.WifiInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import io.atomofiron.wirelessscan.I
import io.atomofiron.wirelessscan.I.Companion.WIDE_MODE
import kotlinx.android.synthetic.main.layout_item.view.*
import io.atomofiron.wirelessscan.R
import io.atomofiron.wirelessscan.room.Node


class ListAdapter(private val co: Context, private val listView: ListView) : BaseAdapter() {
    companion object {
        val FILTER_DEFAULT = 0
        val FILTER_INCLUDE = 1
        val FILTER_EXCLUDE = 2
    }
    private val filterValues = arrayOf("WPA", "PSK", "EAP", "CCMP", "TKIP", "WPS", "P2P", "WEP", "HIDDEN")
    private val filter: IntArray = IntArray(filterValues.size)
    val allNodes = ArrayList<Node>()
    private val nodes = ArrayList<Node>()
    var focuse: Node? = null
        private set
    private var transparent = 0
    private var filtering = false
    var connectionInfo: WifiInfo? = null
        set(value) { field = value; notifyDataSetChanged() }

    var onNodeClickListener: (node: Node?) -> Unit = {}

    init {
        Node.initColors(co.resources)
        transparent = co.resources.getColor(R.color.transparent)
        listView.setOnItemClickListener { _, _, position, _ ->
            focuse = nodes[position]
            onNodeClickListener(focuse)
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
            convertView.pwr.text = "  ●" //█●
            holder = ViewHolder(convertView as LinearLayout)
            convertView.tag = holder

            if (WIDE_MODE)
                holder.bssid.visibility = View.VISIBLE
        } else
            holder = convertView.tag as ViewHolder

        fillView(holder, nodes[position], position)

        return convertView
    }

    private fun fillView(holder: ViewHolder, node: Node, position: Int) {
        drawItemRoot(holder.root, node, position)

        // node.level == -1 experiment
        holder.pw.setTextColor(if (node.level == -1) -65281 else node.pwColor)

        holder.ch.text = node.ch.toString()
        holder.ch.setTextColor(node.chColor)

        holder.enc.text = node.enc
        holder.enc.setTextColor(node.encColor)

        holder.chi.text = node.chi
        holder.chi.setTextColor(node.chiColor)

        holder.wps.text = node.wps
        holder.wps.setTextColor(node.wpsColor)

        val connected = connectionInfo?.bssid == node.bssid
        holder.essid.text = if (node.essid.isEmpty()) node.bssid else node.essid
        holder.essid.setTextColor(if (connected) Node.green_light else node.essidColor)

        holder.bssid.text = node.bssid
        holder.bssid.setTextColor(if (connected) Node.green_light else node.bssidColor)
    }

    private fun drawItemRoot(layout: LinearLayout, node: Node, position: Int) {
        val associating = focuse?.bssid?.startsWith(node.bssid.substring(0, 8)) ?: false
        if (associating)
            layout.setBackgroundResource(if (node.level <= Node.MIN_LEVEL) R.drawable.grille_red else R.drawable.grille)
        else
            layout.setBackgroundColor(when {
                node.level <= Node.MIN_LEVEL -> Node.red_lite
                position % 2 == 0 -> Node.transparent
                else -> Node.black_lite
            })

        if (node.level == -1)
            I.log("WOW: node.level == -1")
    }

    fun resetFocus() {
        focuse = null
        onNodeClickListener(null)
    }

    /** @return counters like '15/22' or '5/22   15/22' */
    private fun getCounters(): String {
        var count = allNodes.size
        allNodes.forEach { it -> if (it.level == Node.MIN_LEVEL) count-- }
        return "${if (filtering) "${nodes.size}/${allNodes.size}" else ""}   $count/${allNodes.size}"
    }

    fun updateList(list: ArrayList<Node>?) : String {
        allNodes.clear()
        nodes.clear()

        if (list != null)
            allNodes.addAll(list)

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
        val prevNodes = ArrayList(nodes)
        nodes.clear()
        nodes.addAll(allNodes)

        if (filtering) {
            var n = 0
            loop@ while (n < nodes.size) {
                for (i in 0 until filter.size)
                    if (i == filter.size - 1 && filter[i] != FILTER_DEFAULT && (filter[i] == FILTER_INCLUDE) != nodes[n].essid.isEmpty() ||
                            filter[i] != 0 && (filter[i] == FILTER_INCLUDE) != nodes[n].capabilities.contains(filterValues[i])) {
                        nodes.removeAt(n)
                        continue@loop
                    }
                n++
            }
        }

        if (prevNodes != nodes)
            notifyDataSetChanged()
    }

    fun clear(): String {
        allNodes.clear()
        nodes.clear()

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

    override fun getItem(position: Int): Node = nodes[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getCount(): Int = nodes.size

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
