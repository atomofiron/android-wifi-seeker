package ru.raslav.wirelessscan.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.raslav.wirelessscan.utils.DoubleClickMaster
import ru.raslav.wirelessscan.Const
import ru.raslav.wirelessscan.databinding.ItemSnapshotBinding
import java.io.File

class SnapshotHolder(val binding: ItemSnapshotBinding) : RecyclerView.ViewHolder(binding.root)

class SnapshotsListAdapter(private val co: Context) : RecyclerView.Adapter<SnapshotHolder>() {

    private val list = mutableListOf<String>()
    private val dir = File(co.applicationInfo.dataDir, "files")

    var onSnapshotShareListener: (name: String) -> Unit = {}
    var onSnapshotClickListener: (name: String) -> Unit = {}

    init {
        dir.listFiles()
            ?.sortedBy { it.name }
            ?.filter { it.name.endsWith(Const.SNAPSHOT_FORMAT) }
            ?.forEach { list.add(it.name) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SnapshotHolder {
        val binding = ItemSnapshotBinding.inflate(LayoutInflater.from(co), parent, false)
        val holder = SnapshotHolder(binding)

        binding.root.setOnClickListener {
            positionOf(holder)?.let { onSnapshotClickListener(list[it]) }
        }
        binding.share.setOnClickListener {
            positionOf(holder)?.let { onSnapshotShareListener(list[it]) }
        }
        binding.delete.setOnClickListener(DoubleClickMaster {
            positionOf(holder)?.let { index ->
                File(dir.absolutePath, list[index]).delete()
                list.removeAt(index)
                notifyItemRemoved(index)
            }
        })
        return holder
    }

    override fun onBindViewHolder(holder: SnapshotHolder, position: Int) {
        holder.binding.title.text = list[position]
    }

    override fun getItemCount(): Int = list.size

    private fun positionOf(holder: SnapshotHolder): Int? = holder.bindingAdapterPosition
        .takeIf { it != RecyclerView.NO_POSITION }
}
