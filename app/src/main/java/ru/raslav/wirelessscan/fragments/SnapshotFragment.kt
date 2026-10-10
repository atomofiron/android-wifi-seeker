package ru.raslav.wirelessscan.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
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
import ru.raslav.wirelessscan.data.Point
import ru.raslav.wirelessscan.databinding.FragmentSnapshotBinding
import ru.raslav.wirelessscan.isWide
import ru.raslav.wirelessscan.ui.init
import ru.raslav.wirelessscan.ui.overscroll.setupSpringOverscroll
import ru.raslav.wirelessscan.ui.showError
import ru.raslav.wirelessscan.unsafeLazy
import ru.raslav.wirelessscan.utils.AlternatingDecoration
import ru.raslav.wirelessscan.utils.Rslt
import ru.raslav.wirelessscan.utils.SnapshotManager
import ru.raslav.wirelessscan.utils.SnapshotManager.Companion.shareSnapshot

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

    private val snapshotName by unsafeLazy { requireArguments().getString(EXTRA_NAME).toString() }
    private val menuProvider by unsafeLazy { MainMenuProvider() }
    private val adapter by unsafeLazy { PointListAdapter(requireContext()) }
    private lateinit var binding: FragmentSnapshotBinding
    private var viewJob = Job()
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch(IO) {
            val manager = SnapshotManager(requireContext())
            val list = requireArguments().getString(EXTRA_NAME)?.let {
                when (val rslt = manager.get(it)) {
                    is Rslt.Ok -> rslt.value
                    is Rslt.Err -> emptyList<Point>().also {
                        withContext(Main) {
                            context?.showError(rslt.message)
                        }
                    }
                }
            }
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

        binding.appBar.init(this, snapshotName)
        binding.appBar.toolbar.addMenuProvider(menuProvider)
        binding.listTitle.root.setBackgroundResource(R.color.black_lite)
        binding.list.adapter = adapter
        binding.list.addItemDecoration(AlternatingDecoration(adapter::backgroundAt))
        binding.list.setupSpringOverscroll()

        binding.listTitle.root.insetsPadding(start = true, end = true)
        binding.list.insetsPadding(start = true, end = true, bottom = true)
        binding.listTitle.bssid.isVisible = resources.configuration.isWide()

        viewJob.complete()
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

    private inner class MainMenuProvider : MenuProvider {

        override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
            inflater.inflate(R.menu.snapshot, menu)
        }

        override fun onMenuItemSelected(item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.share -> SnapshotManager(requireContext())
                    .put(adapter.points, snapshotName).let { // updated manufacturers
                        when (it) {
                            is Rslt.Ok -> requireContext().shareSnapshot(it.value)
                            is Rslt.Err -> requireContext().showError(it.message)
                        }
                    }
                else -> return false
            }
            return true
        }
    }
}