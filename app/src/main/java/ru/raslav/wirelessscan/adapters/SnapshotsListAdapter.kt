package ru.raslav.wirelessscan.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import ru.raslav.wirelessscan.utils.DoubleClickMaster
import ru.raslav.wirelessscan.Const
import ru.raslav.wirelessscan.databinding.LayoutItemSnapshotBinding
import java.io.File

class SnapshotsListAdapter(private val co: Context) : BaseAdapter() {
    private val list = ArrayList<String>()
    private val dbDir = File(co.applicationInfo.dataDir + "/files/")

    var onSnapshotShareListener: (name: String) -> Unit = {}

    init {
        update()
    }

    fun clear() {
        dbDir.listFiles()
                .filter { it.name.endsWith(Const.SNAPSHOT_FORMAT) }
                .forEach { it.delete() }

        update()
    }

    private fun update() {
        list.clear()

        dbDir.listFiles()
                ?.filter { it.name.endsWith(Const.SNAPSHOT_FORMAT) }
                ?.forEach { list.add(it.name) }
                ?: return

        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val binding = if (convertView == null) {
            LayoutItemSnapshotBinding.inflate(LayoutInflater.from(co), parent, false).apply {
                share.setOnClickListener {
                    onSnapshotShareListener(title.text.toString())
                }
                delete.setOnClickListener(DoubleClickMaster {
                    File(dbDir.absolutePath, title.text.toString()).delete()
                    update()
                })
            }
        } else
            convertView.tag as LayoutItemSnapshotBinding

        binding.title.text = list[position]

        return binding.root
    }

    override fun getItem(position: Int): String = list[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getCount(): Int = list.size
}