package io.atomofiron.wirelessscan.fragments

import android.app.Fragment
import android.os.Bundle
import android.view.*
import io.atomofiron.wirelessscan.R
import io.atomofiron.wirelessscan.adapters.ListAdapter
import io.atomofiron.wirelessscan.room.Node
import io.atomofiron.wirelessscan.utils.SnapshotManager
import kotlinx.android.synthetic.main.fragment_main.view.*
import kotlinx.android.synthetic.main.layout_description.view.*

class SnapshotFragment : Fragment() {
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

    private var fragmentView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        menu?.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        fragmentView = view
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (fragmentView != null)
            return fragmentView

        val view = inflater.inflate(R.layout.fragment_snapshot, container, false)

        val adapter = ListAdapter(activity, view.list_view)
        view.list_view.adapter = adapter
        adapter.onNodeClickListener = { node -> showDescription(view.layout_description, node) }

        adapter.updateList(SnapshotManager(activity).get(arguments.getString(EXTRA_NAME)))

        return view
    }

    private fun showDescription(description: View, node: Node?) {
        if (node != null) {
            description.visibility = View.VISIBLE

            description.tv_essid.text = getString(R.string.essid_format, node.getNotEmptyESSID())
            description.tv_bssid.text = getString(R.string.bssid_format, node.bssid)
            description.tv_capab.text = getString(R.string.capab_format, node.capabilities)
            description.tv_frequ.text = getString(R.string.frequ_format, node.frequency, node.ch)
            description.tv_manuf.text = getString(R.string.manuf_format, node.manufacturer)
        } else
            description.visibility = View.GONE
    }
}