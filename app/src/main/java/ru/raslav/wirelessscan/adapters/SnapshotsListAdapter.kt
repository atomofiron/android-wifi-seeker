package ru.raslav.wirelessscan.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.utils.DoubleClickMaster
import kotlinx.android.synthetic.main.layout_item_snapshot.view.*
import java.io.File

class SnapshotsListAdapter(private val co: Context) : BaseAdapter(), View.OnClickListener {
    private val list = ArrayList<String>()
    private val dbDir = File(co.applicationInfo.dataDir + "/databases/")
    private val onDoubleClickListener: (v: View) -> Unit = { v ->
        File(dbDir.absolutePath, (v.parent as View).title.text.toString()).delete()
        update()
    }

    var onSnapshotShareListener: (name: String) -> Unit = {}

    init {
        update()
    }

    fun clear() {
        dbDir.listFiles()
                .filter { it.name.endsWith(".db") }
                .forEach { it.delete() }

        update()
    }

    private fun update() {
        list.clear()

        dbDir.listFiles()
                ?.filter { it.name.endsWith(".db") }
                ?.forEach { list.add(it.name) }
                ?: return

        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var view: View? = convertView
        val holder: ViewHolder

        if (view == null) {
            view = LayoutInflater.from(co).inflate(R.layout.layout_item_snapshot, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        } else
            holder = view.tag as ViewHolder

        holder.title.text = list[position]

        return view!!
    }

    override fun getItem(position: Int): String = list[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getCount(): Int = list.size

    inner class ViewHolder(view: View) {

        val title: TextView = view.title

        init {
            view.share.setOnClickListener(this@SnapshotsListAdapter)
            view.delete.setOnClickListener(DoubleClickMaster(onDoubleClickListener))
        }
    }

    override fun onClick(v: View) {
        onSnapshotShareListener((v.parent as View).title.text.toString())
    }
}