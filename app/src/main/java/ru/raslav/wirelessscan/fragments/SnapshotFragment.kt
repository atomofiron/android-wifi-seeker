package ru.raslav.wirelessscan.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.view.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import lib.atomofiron.insets.insetsPadding
import ru.raslav.wirelessscan.adapters.PointsListAdapter
import ru.raslav.wirelessscan.utils.Point
import ru.raslav.wirelessscan.utils.SnapshotManager
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.databinding.FragmentSnapshotBinding
import ru.raslav.wirelessscan.databinding.LayoutDescriptionBinding
import ru.raslav.wirelessscan.isWide

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

        val adapter = PointsListAdapter(requireContext(), binding.listView)
        binding.listView.adapter = adapter
        adapter.onPointClickListener = { point -> showDescription(binding.description, point) }

        adapter.updateList(SnapshotManager(requireContext()).get(requireArguments().getString(EXTRA_NAME)!!))

        binding.description.root.insetsPadding(start = true, end = true)
        binding.layoutItem.root.insetsPadding(start = true, end = true)
        binding.listView.insetsPadding(start = true, end = true, bottom = true)
        binding.layoutItem.bssid.isVisible = resources.configuration.isWide()

        return binding.root
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.layoutItem.bssid.isVisible = resources.configuration.isWide()
    }

    private fun showDescription(description: LayoutDescriptionBinding, point: Point?) {
        if (point != null) {
            description.root.visibility = View.VISIBLE

            description.tvEssid.text = getString(R.string.essid_format, point.getNotEmptyESSID())
            description.tvBssid.text = getString(R.string.bssid_format, point.bssid)
            description.tvCapab.text = getString(R.string.capab_format, point.capabilities)
            description.tvFrequ.text = getString(R.string.frequ_format, point.frequency, point.ch)
            description.tvManuf.text = getString(R.string.manuf_format, point.manufacturer)
            description.tvManufDesc.text =  point.manufacturerDesc
        } else
            description.root.visibility = View.GONE
    }
}