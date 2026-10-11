package ru.raslav.wirelessscan.fragments

import android.annotation.SuppressLint
import android.content.Context
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
import ru.raslav.wirelessscan.data.PointFilter
import ru.raslav.wirelessscan.databinding.FragmentSnapshotBinding
import ru.raslav.wirelessscan.fragments.delegates.FilterDelegate
import ru.raslav.wirelessscan.isWide
import ru.raslav.wirelessscan.ui.init
import ru.raslav.wirelessscan.ui.overscroll.setupSpringOverscroll
import ru.raslav.wirelessscan.ui.showError
import ru.raslav.wirelessscan.unsafeLazy
import ru.raslav.wirelessscan.utils.AlternatingDecoration
import ru.raslav.wirelessscan.utils.LayoutOrientation.Companion.layoutChanges
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
    private val filterDelegate by unsafeLazy { FilterDelegate(::onFilterChanged) }
    private lateinit var filterItem: MenuItem
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
                updateCounters(adapter.updateList(list))
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
        binding.list.addOnScrollListener(binding.filters.scrollListener)
        binding.list.addItemDecoration(AlternatingDecoration(adapter::backgroundAt))
        binding.list.setupSpringOverscroll()

        val padding = resources.getDimensionPixelSize(R.dimen.padding_half)
        val filters = binding.filters.withHorizontalLinearLayout(top = true, end = true, padding = padding)
        binding.filters.onAnim { offset ->
            binding.container.translationY = offset
        }
        filters.insetsPadding(horizontal = true)
        filterDelegate.init(filters)
        binding.listTitle.root.insetsPadding(start = true, end = true)
        binding.list.insetsPadding(start = true, end = true, bottom = true)
        binding.listTitle.bssid.isVisible = resources.configuration.isWide()
        binding.counters.root.insetsPadding(horizontal = true)
        binding.root.layoutChanges {
            @SuppressLint("NotifyDataSetChanged")
            adapter.notifyDataSetChanged() // wide item layout
        }

        viewJob.complete()
        binding.updateProgressVisibility()

        return binding.root
    }

    private fun FragmentSnapshotBinding.updateProgressVisibility() {
        list.isVisible = loaded
        progress.isVisible = !loaded
    }

    private fun onFilterChanged(filter: PointFilter) {
        adapter.updateFilter(filter)
        val counters = adapter.filter(adapter.hasFilters())
        updateCounters(counters)
        when (adapter.hasFilters()) {
            true -> R.drawable.ic_filter_active
            false -> R.drawable.ic_filter
        }.let { filterItem.setIcon(it) }
    }

    private fun updateCounters(counters: String) {
        binding.counters.root.text = counters
    }

    private inner class MainMenuProvider : MenuProvider {

        override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
            inflater.inflate(R.menu.snapshot, menu)
            filterItem = menu.findItem(R.id.filters)
        }

        override fun onMenuItemSelected(item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.filters -> binding.filters.toggle()
                R.id.share -> {
                    val manager = SnapshotManager(requireContext())
                    manager.put(adapter.points, snapshotName) // updated manufacturers
                        .handleError(requireContext())
                        ?.let { manager.put(adapter.currentList, name = null) }
                        ?.handleError(requireContext())
                        ?.let { requireContext().shareSnapshot(it) }
                }
                else -> return false
            }
            return true
        }
    }
}

private fun <T> Rslt<T>.handleError(context: Context): T? = when (this) {
    is Rslt.Ok -> value
    is Rslt.Err -> context.showError(message)
        .let { null }
}