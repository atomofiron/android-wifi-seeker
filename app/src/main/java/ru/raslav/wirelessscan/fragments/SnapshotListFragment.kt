package ru.raslav.wirelessscan.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import lib.atomofiron.insets.insetsPadding
import ru.raslav.wirelessscan.BuildConfig
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.adapters.SnapshotsListAdapter
import ru.raslav.wirelessscan.asMain
import ru.raslav.wirelessscan.databinding.LayoutListBinding
import ru.raslav.wirelessscan.tryStartActivity
import ru.raslav.wirelessscan.ui.init

class SnapshotListFragment : Fragment() {

    private lateinit var binding: LayoutListBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = LayoutListBinding.inflate(inflater, container, false)
        binding.appBar.init(this, R.string.title_snapshots)

        val adapter = SnapshotsListAdapter(requireContext())
        adapter.onSnapshotShareListener = { name -> share(name) }
        adapter.onSnapshotClickListener = { name -> requireActivity().asMain().showSnapshot(name) }
        binding.listView.adapter = adapter
        binding.listView.insetsPadding(start = true, end = true, bottom = true)
        return binding.root
    }

    private fun share(name: String) {
        val intent = Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_STREAM, "content://${BuildConfig.AUTHORITY}/$name".toUri())
            .setType("text/xml")
        requireContext().tryStartActivity(intent)
    }
}