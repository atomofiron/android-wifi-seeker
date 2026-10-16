package ru.raslav.wirelessscan.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import lib.atomofiron.insets.insetsPadding
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.adapters.SnapshotsListAdapter
import ru.raslav.wirelessscan.asMain
import ru.raslav.wirelessscan.databinding.ListBinding
import ru.raslav.wirelessscan.ui.init
import ru.raslav.wirelessscan.ui.overscroll.setupSpringOverscroll

class SnapshotListFragment : Fragment() {

    private lateinit var binding: ListBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = ListBinding.inflate(inflater, container, false)
        binding.appBar.init(this, R.string.title_snapshots)

        binding.listView.adapter = SnapshotsListAdapter(requireContext()) { name ->
            requireActivity().asMain().showSnapshot(name)
        }
        binding.listView.insetsPadding(start = true, end = true, bottom = true)
        binding.listView.setupSpringOverscroll()
        return binding.root
    }
}