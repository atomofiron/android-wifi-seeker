package ru.raslav.wirelessscan.fragments

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import lib.atomofiron.insets.insetsPadding
import ru.raslav.wirelessscan.BuildConfig
import ru.raslav.wirelessscan.Const.MIME_TYPE_XML
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.adapters.SnapshotsListAdapter
import ru.raslav.wirelessscan.asMain
import ru.raslav.wirelessscan.databinding.ListBinding
import ru.raslav.wirelessscan.tryStartActivity
import ru.raslav.wirelessscan.ui.init
import ru.raslav.wirelessscan.ui.overscroll.setupSpringOverscroll
import java.io.File

class SnapshotListFragment : Fragment() {

    private lateinit var binding: ListBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = ListBinding.inflate(inflater, container, false)
        binding.appBar.init(this, R.string.title_snapshots)

        val adapter = SnapshotsListAdapter(requireContext())
        adapter.onSnapshotShareListener = { name -> share(name) }
        adapter.onSnapshotClickListener = { name -> requireActivity().asMain().showSnapshot(name) }
        binding.listView.adapter = adapter
        binding.listView.insetsPadding(start = true, end = true, bottom = true)
        binding.listView.setupSpringOverscroll()
        return binding.root
    }

    private fun share(name: String) {
        val context = requireContext()
        val file = File(context.filesDir, name)
        val uri = FileProvider.getUriForFile(context, BuildConfig.AUTHORITY, file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(MIME_TYPE_XML)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newUri(context.contentResolver, name, uri)
        context.tryStartActivity(intent)
    }
}