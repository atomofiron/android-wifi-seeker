package ru.raslav.wirelessscan.adapters

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import ru.raslav.wirelessscan.databinding.ItemSnapshotBinding
import ru.raslav.wirelessscan.inflater
import ru.raslav.wirelessscan.ui.renameSnapshot
import ru.raslav.wirelessscan.utils.setOnDoubleClickListener
import ru.raslav.wirelessscan.utils.SnapshotManager
import ru.raslav.wirelessscan.utils.SnapshotManager.Companion.shareSnapshot
import java.io.File

class SnapshotHolder(val binding: ItemSnapshotBinding) : RecyclerView.ViewHolder(binding.root)

class SnapshotsListAdapter(
    private val context: Context,
    private val onClick: (name: String) -> Unit,
) : RecyclerView.Adapter<SnapshotHolder>() {

    private val list = mutableListOf<String>()
    private val dir = context.filesDir

    init {
        dir.listFiles()
            ?.sortedBy { it.name }
            ?.filter { SnapshotManager.isSnapshotName(it.name) }
            ?.forEach { list.add(it.name) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SnapshotHolder {
        val binding = ItemSnapshotBinding.inflate(context.inflater(), parent, false)
        val holder = SnapshotHolder(binding)

        binding.root.setOnClickListener {
            holder.position()?.let { onClick(list[it]) }
        }
        binding.share.setOnClickListener {
            holder.position()?.let { parent.context.shareSnapshot(list[it]) }
        }
        binding.rename.setOnClickListener {
            holder.position()?.let { parent.context.renameSnapshot(list[it], ::onRenamed) }
        }
        binding.delete.setOnDoubleClickListener {
            holder.position()?.let { index ->
                File(dir, list[index]).delete()
                list.removeAt(index)
                notifyItemRemoved(index)
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: SnapshotHolder, position: Int) {
        holder.binding.title.text = list[position]
    }

    override fun getItemCount(): Int = list.size

    private fun SnapshotHolder.position(): Int? = bindingAdapterPosition.takeIf { it != NO_POSITION }

    private fun onRenamed(from: String, to: String) {
        val index = list.indexOf(from)
        list[index] = to
        notifyItemChanged(index)
    }
}
