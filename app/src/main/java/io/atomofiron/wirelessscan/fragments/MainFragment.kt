package io.atomofiron.wirelessscan.fragments

import io.atomofiron.wirelessscan.adapters.ListAdapter
import io.atomofiron.wirelessscan.I.Companion.WIDE_MODE

import io.atomofiron.wirelessscan.R
import io.atomofiron.wirelessscan.ScanService
import io.atomofiron.wirelessscan.room.Node
import kotlinx.android.synthetic.main.fragment_main.view.*
import kotlinx.android.synthetic.main.layout_buttons_pane.view.*
import android.app.Fragment
import android.content.*
import android.content.Context.WIFI_SERVICE
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.*
import android.view.*
import io.atomofiron.wirelessscan.I
import io.atomofiron.wirelessscan.utils.OnDoubleClickListener
import io.atomofiron.wirelessscan.connection.ScanConnection
import io.atomofiron.wirelessscan.connection.Connection.WHAT.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.TextView
import io.atomofiron.wirelessscan.*
import io.atomofiron.wirelessscan.utils.SnapshotManager
import kotlinx.android.synthetic.main.layout_description.view.*
import kotlinx.android.synthetic.main.layout_filters_pane.view.*
import kotlinx.android.synthetic.main.layout_item.view.*

class MainFragment : Fragment() {
    companion object {
        private val EXTRA_SERVICE_WAS_STARTED = "EXTRA_SERVICE_WAS_STARTED"
        private val EXTRA_NODES = "EXTRA_NODES"
    }
    private lateinit var sp: SharedPreferences
    private lateinit var wifiManager: WifiManager
    private lateinit var scanConnection: ScanConnection
    private lateinit var listAdapter: ListAdapter
    private lateinit var connectionReceiver: BroadcastReceiver

    private lateinit var flash: Animation
    private var keepServiceStarted = false
    /** if onDestroyView() was invoked, but not onDestroy() */
    private var fragmentView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        sp = I.sp(activity)
        wifiManager = activity.getSystemService(WIFI_SERVICE) as WifiManager

        flash = AnimationUtils.loadAnimation(activity, R.anim.flash)
        flash.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                view.flash.visibility = View.VISIBLE
            }
            override fun onAnimationEnd(animation: Animation) {
                view?.flash?.visibility = View.GONE
            }
            override fun onAnimationRepeat(animation: Animation) {}
        })

        scanConnection = ScanConnection(object : Handler() {
            override fun handleMessage(msg: Message?) {
                super.handleMessage(msg)
                this@MainFragment.handleMessage(msg)
            }
        })
        val appIsStarted = savedInstanceState == null
        scanConnection.onServiceConnectedListener = {
            if (appIsStarted)
                scanConnection.sendGetRequest()

            sendScanDelay()
        }
        scanConnection.bindService(activity)

        connectionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (view != null)
                    listAdapter.connectionInfo = wifiManager.connectionInfo
            }
        }
        activity.registerReceiver(connectionReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!keepServiceStarted && !sp.getBoolean(I.PREF_WORK_IN_BG, false))
            stopScanService()

        scanConnection.unbindService(activity)
        activity.unregisterReceiver(connectionReceiver)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        keepServiceStarted = true

        outState.putBoolean(EXTRA_SERVICE_WAS_STARTED, view?.button_resume?.isActivated ?: false)
        outState.putParcelableArrayList(EXTRA_NODES, listAdapter.allNodes)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fragmentView = view
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (fragmentView != null) {
            showDescriptionIfNecessary( // когда возвращаемся из настроек
                    fragmentView!!.layout_description,
                    if (sp.getBoolean(I.PREF_SHOW_DESCRIPTION, false)) listAdapter.focuse else null
            )

            fragmentView!!.flash.visibility = View.GONE

            return fragmentView
        }

        val view = inflater.inflate(R.layout.fragment_main, container, false)

        if (WIDE_MODE)
            view.layout_item.bssid.visibility = View.VISIBLE

        listAdapter = ListAdapter(activity, view.list_view)
        view.list_view.adapter = listAdapter
        listAdapter.onNodeClickListener = { node -> showDescriptionIfNecessary(getView().layout_description, node) }
        listAdapter.connectionInfo = wifiManager.connectionInfo

        initFilters(view.layout_filters)
        initButtons(view.layout_buttons, view.label)

        if (savedInstanceState?.getBoolean(EXTRA_SERVICE_WAS_STARTED, true) != false)
            startScanServiceIfWifiEnabled(view.button_resume)

        if (savedInstanceState != null)
            listAdapter.updateList(savedInstanceState.getParcelableArrayList(EXTRA_NODES))

        return view
    }

    private fun initFilters(filters: ViewGroup) {
        val listener = View.OnClickListener { v ->
            var state = ListAdapter.FILTER_DEFAULT
            when {
                v.isSelected -> v.isSelected = false
                v.isActivated -> {
                    v.isActivated = false
                    v.isSelected = true
                    state = ListAdapter.FILTER_EXCLUDE
                }
                else -> {
                    v.isActivated = true
                    state = ListAdapter.FILTER_INCLUDE
                }
            }
            updateCounters(listAdapter.updateFilter(filters.indexOfChild(v), state))
        }
        for (i in 0 until filters.childCount)
            filters.getChildAt(i).setOnClickListener(listener)
    }

    private fun initButtons(buttons: View, label: TextView) {
        buttons.button_filter.setOnClickListener { v ->
            v.isActivated = !v.isActivated
            updateCounters(listAdapter.filter(v.isActivated))
            view.layout_filters.visibility = if (v.isActivated) View.VISIBLE else View.GONE
        }
        buttons.button_save.setOnClickListener {
            if (listAdapter.allNodes.size != 0) {
                view.flash.startAnimation(flash)
                SnapshotManager(activity).put(listAdapter.allNodes)
            }
        }
        buttons.button_resume.setOnClickListener { v: View ->
            v.isActivated = !v.isActivated

            if (v.isActivated)
                startScanService()
            else
                stopScanService()
        }
        (buttons.spinner_delay as Spinner).setSelection(sp.getString(I.PREF_DEFAULT_DELAY, "1").toInt())
        buttons.spinner_delay.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sendScanDelay()
            }
        }
        buttons.button_clear.setOnClickListener(OnDoubleClickListener({
            scanConnection.clearNodesList()
            label.text = listAdapter.clear()
        }).onClickListener { listAdapter.resetFocus(); true })
        buttons.button_list.setOnClickListener {
            activity.startActivity(
                    Intent(activity, MainActivity::class.java)
                            .setAction(MainActivity.ACTION_OPEN_SNAPSHOTS_LIST)
            )
        }
    }

    private fun startScanServiceIfWifiEnabled(buttonResume: View) {
        buttonResume.isActivated = wifiManager.isWifiEnabled
        if (buttonResume.isActivated)
            startScanService()
    }

    private fun startScanService() {
        if (!wifiManager.isWifiEnabled)
            wifiManager.isWifiEnabled = true

        activity.startService(Intent(activity.applicationContext, ScanService::class.java))
    }

    private fun stopScanService() = scanConnection.stopScanService()

    private fun sendScanDelay() {
        scanConnection.sendScanDelay(resources.getIntArray(R.array.delay_arr_int)
                [view?.spinner_delay?.selectedItemPosition ?: return])
    }

    private fun handleMessage(msg: Message?) {
        I.log("R: what: ${msg?.what ?: "null"}")
        if (view == null) return

        when (msg?.what) {
            START_SCAN.ordinal -> listAdapter.animScan(true)
            RESULTS.ordinal -> { updateList(msg) }
            STOPPED.ordinal -> {
                view.button_resume.isActivated = false
                listAdapter.animScan(false)
            }
        }
    }

    private fun updateList(msg: Message) {
        if (msg.obj.javaClass == ArrayList<Node>().javaClass) {
            view.button_resume.isActivated = msg.arg1.toBoolean()

            updateCounters(listAdapter.updateList(msg.obj as ArrayList<Node>))
        }
    }

    private fun updateCounters(counters: String) {
        view.label.text = counters
    }

    private fun showDescriptionIfNecessary(description: View, node: Node?) {
        if (node != null && sp.getBoolean(I.PREF_SHOW_DESCRIPTION, false)) {
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
