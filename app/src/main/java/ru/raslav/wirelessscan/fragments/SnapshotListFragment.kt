package ru.raslav.wirelessscan.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import lib.atomofiron.insets.insetsPadding
import ru.raslav.wirelessscan.adapters.SnapshotsListAdapter

import ru.raslav.wirelessscan.BuildConfig
import ru.raslav.wirelessscan.MainActivity
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.databinding.LayoutListBinding

class SnapshotListFragment : Fragment(), Titled by Titled(R.string.title_snapshots) {

    private lateinit var binding: LayoutListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // todo deprecation
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://${BuildConfig.AUTHORITY}/$name"))
            .setType("text/xml")

        if (intent.resolveActivity(requireContext().packageManager) != null)
            startActivity(intent)
        else
            Toast.makeText(activity, R.string.no_activity, Toast.LENGTH_SHORT).show()
    }
}