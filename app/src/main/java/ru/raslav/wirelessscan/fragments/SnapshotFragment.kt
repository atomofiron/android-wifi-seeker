package ru.raslav.wirelessscan.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lib.atomofiron.insets.insetsPadding
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.adapters.PointListAdapter
import ru.raslav.wirelessscan.databinding.FragmentSnapshotBinding
import ru.raslav.wirelessscan.isWide
import ru.raslav.wirelessscan.ui.init
import ru.raslav.wirelessscan.ui.overscroll.setupSpringOverscroll
import ru.raslav.wirelessscan.unsafeLazy
import ru.raslav.wirelessscan.utils.RowBackgroundDecoration
import ru.raslav.wirelessscan.utils.SnapshotManager

class SnapshotFragment : Fragment() {
    companion object {
        private const val EXTRA_NAME = "EXTRA_NAME"

        operator fun invoke(name: String): SnapshotFragment {
            val bundle = Bundle()
            bundle.putString(EXTRA_NAME, name)
            val fragment = SnapshotFragment()
            fragment.arguments = bundle
            return fragment
        }
    }

    private val adapter by unsafeLazy { PointListAdapter(requireContext()) }
    private lateinit var binding: FragmentSnapshotBinding
    private var viewJob = Job()
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch(IO) {
            val manager = SnapshotManager(requireContext())
            val list = requireArguments().getString(EXTRA_NAME)
                ?.let { manager.get(it) }
            withContext(Main) {
                adapter.updateList(list)
                loaded = true
                viewJob.join()
                binding.updateProgressVisibility()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSnapshotBinding.inflate(inflater, container, false)

        binding.appBar.init(this, requireArguments().getString(EXTRA_NAME).toString())
        binding.listTitle.root.setBackgroundResource(R.color.black_lite)
        binding.list.adapter = adapter
        binding.list.addItemDecoration(RowBackgroundDecoration(adapter::backgroundAt))
        binding.list.setupSpringOverscroll()

        binding.listTitle.root.insetsPadding(start = true, end = true)
        binding.list.insetsPadding(start = true, end = true, bottom = true)
        binding.listTitle.bssid.isVisible = resources.configuration.isWide()

        viewJob.cancel()
        binding.updateProgressVisibility()

        return binding.root
    }

    private fun FragmentSnapshotBinding.updateProgressVisibility() {
        list.isVisible = loaded
        progress.isVisible = !loaded
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.listTitle.bssid.isVisible = resources.configuration.isWide()
    }
}