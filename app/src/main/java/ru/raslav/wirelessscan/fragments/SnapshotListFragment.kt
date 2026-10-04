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
import ru.raslav.wirelessscan.MainActivity
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.adapters.SnapshotsListAdapter
import ru.raslav.wirelessscan.databinding.LayoutListBinding
import ru.raslav.wirelessscan.tryStartActivity

class SnapshotListFragment : Fragment(), Titled by Titled(R.string.title_snapshots) {

    private lateinit var binding: LayoutListBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = LayoutListBinding.inflate(inflater, container, false)

        val adapter = SnapshotsListAdapter(requireContext())
        adapter.onSnapshotShareListener = { name -> share(name) }
        binding.listView.adapter = adapter
        binding.listView.setOnItemClickListener { _, _, position, _ ->
            requireContext().startActivity(
                    Intent(activity, MainActivity::class.java)
                            .setAction(MainActivity.ACTION_OPEN_SNAPSHOT)
                            .putExtra(MainActivity.EXTRA_SNAPSHOT_NAME, adapter.getItem(position))
            )
        }
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