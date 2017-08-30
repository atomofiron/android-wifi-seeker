package io.atomofiron.wirelessscan.fragments

import android.app.Fragment
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import io.atomofiron.wirelessscan.R
import io.atomofiron.wirelessscan.adapters.ListAdapter
import io.atomofiron.wirelessscan.utils.SnapshotManager
import kotlinx.android.synthetic.main.fragment_main.view.*

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

        val view = inflater.inflate(R.layout.layout_list, container, false)
        (view as LinearLayout).addView(inflater.inflate(R.layout.layout_item, null, false), 0)

        val adapter = ListAdapter(activity, view.list_view)
        view.list_view.adapter = adapter

        SnapshotManager(activity).get(arguments.getString(EXTRA_NAME), { list ->
            adapter.updateList(list)
        })

        return view
    }
}