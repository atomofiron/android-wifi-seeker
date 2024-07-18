package ru.raslav.wirelessscan.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.view.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import lib.atomofiron.insets.insetsPadding
import ru.raslav.wirelessscan.adapters.PointListAdapter
import ru.raslav.wirelessscan.utils.SnapshotManager
import ru.raslav.wirelessscan.databinding.FragmentSnapshotBinding
import ru.raslav.wirelessscan.isWide
import ru.raslav.wirelessscan.unsafeLazy

class SnapshotFragment : Fragment(), Titled {
    companion object {
        private val EXTRA_NAME = "EXTRA_NAME"

        fun newInstance(name: String): SnapshotFragment {
            val bundle = Bundle()
            bundle.putString(EXTRA_NAME, name)
            val fragment = SnapshotFragment()
            fragment.arguments = bundle

            return fragment
        }
    }

    override val title: String get() = requireArguments().getString(EXTRA_NAME).toString()

    private val adapter by unsafeLazy { PointListAdapter(requireContext()) }
    private lateinit var binding: FragmentSnapshotBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // todo deprecation
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear()
    }

    override fun onStart() {
        super.onStart()

        requireActivity().title = requireArguments().getString(EXTRA_NAME)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentSnapshotBinding.inflate(inflater, container, false)

        binding.listView.adapter = adapter
        adapter.updateList(SnapshotManager(requireContext()).get(requireArguments().getString(EXTRA_NAME)!!))

        binding.description.root.insetsPadding(start = true, end = true, bottom = true)
        binding.layoutItem.root.insetsPadding(start = true, end = true)
        binding.listView.insetsPadding(start = true, end = true, bottom = true)
        binding.layoutItem.bssid.isVisible = resources.configuration.isWide()
        binding.listView.onItemClickListener = adapter
        binding.description.cross.setOnClickListener {
            adapter.resetFocus()
        }

        return binding.root
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.layoutItem.bssid.isVisible = resources.configuration.isWide()
    }
}