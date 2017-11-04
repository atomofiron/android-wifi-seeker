package ru.raslav.wirelessscan.fragments

import android.app.AlertDialog
import ru.raslav.wirelessscan.adapters.PointsListAdapter
import ru.raslav.wirelessscan.I.Companion.WIDE_MODE

import ru.raslav.wirelessscan.room.Point
import kotlinx.android.synthetic.main.fragment_main.view.*
import kotlinx.android.synthetic.main.layout_buttons_pane.view.*
import android.app.Fragment
import android.content.*
import android.content.Context.WIFI_SERVICE
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.provider.Settings.Secure.LOCATION_MODE
import android.text.format.Formatter
import android.view.*
import ru.raslav.wirelessscan.utils.DoubleClickMaster
import ru.raslav.wirelessscan.connection.ScanConnection
import ru.raslav.wirelessscan.connection.Connection.WHAT.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import ru.raslav.wirelessscan.utils.FileNameInputText
import ru.raslav.wirelessscan.utils.SnapshotManager
import kotlinx.android.synthetic.main.layout_description.view.*
import kotlinx.android.synthetic.main.layout_filters_pane.view.*
import kotlinx.android.synthetic.main.layout_item.view.*
import ru.raslav.wirelessscan.*
import java.io.File

class MainFragment : Fragment() {
    companion object {
        private val EXTRA_SERVICE_WAS_STARTED = "EXTRA_SERVICE_WAS_STARTED"
        private val EXTRA_POINTS = "EXTRA_POINTS"
    }
    private lateinit var sp: SharedPreferences
    private lateinit var wifiManager: WifiManager
    private lateinit var scanConnection: ScanConnection
    private lateinit var pointsListAdapter: PointsListAdapter
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
            override fun onReceive(context: Context?, intent: Intent?) = updateConnectionInfo()
        }
        val filter = IntentFilter()
        filter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        activity.registerReceiver(connectionReceiver, filter)
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
        outState.putParcelableArrayList(EXTRA_POINTS, pointsListAdapter.allPoints)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fragmentView = view
    }

    override fun onStart() {
        super.onStart()

        updateConnectionInfo()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (fragmentView != null) {
            showDescriptionIfNecessary( // когда возвращаемся из настроек
                    fragmentView!!.layout_description,
                    if (sp.getBoolean(I.PREF_SHOW_DESCRIPTION, false)) pointsListAdapter.focuse else null
            )

            fragmentView!!.flash.visibility = View.GONE

            return fragmentView
        }

        val view = inflater.inflate(R.layout.fragment_main, container, false)

        if (WIDE_MODE)
            view.layout_item.bssid.visibility = View.VISIBLE

        pointsListAdapter = PointsListAdapter(activity, view.list_view)
        view.list_view.adapter = pointsListAdapter
        pointsListAdapter.onPointClickListener = { point -> showDescriptionIfNecessary(getView().layout_description, point) }

        initFilters(view.layout_filters)
        initButtons(view.layout_buttons, view.label)

        if (savedInstanceState?.getBoolean(EXTRA_SERVICE_WAS_STARTED, true) != false)
            startScanServiceIfWifiEnabled(view.button_resume)

        if (savedInstanceState != null)
            pointsListAdapter.updateList(savedInstanceState.getParcelableArrayList(EXTRA_POINTS))

        return view
    }

    private fun initFilters(filters: ViewGroup) {
        val listener = View.OnClickListener { v ->
            var state = PointsListAdapter.FILTER_DEFAULT
            when {
                v.isSelected -> v.isSelected = false
                v.isActivated -> {
                    v.isActivated = false
                    v.isSelected = true
                    state = PointsListAdapter.FILTER_EXCLUDE
                }
                else -> {
                    v.isActivated = true
                    state = PointsListAdapter.FILTER_INCLUDE
                }
            }
            updateCounters(pointsListAdapter.updateFilter(filters.indexOfChild(v), state))
        }
        for (i in 0 until filters.childCount)
            filters.getChildAt(i).setOnClickListener(listener)
    }

    private fun initButtons(buttons: View, label: TextView) {
        buttons.button_filter.setOnClickListener { v ->
            v.isActivated = !v.isActivated
            updateCounters(pointsListAdapter.filter(v.isActivated))
            view.layout_filters.visibility = if (v.isActivated) View.VISIBLE else View.GONE
        }
        var snapshotFileName = ""
        buttons.button_save.setOnClickListener(DoubleClickMaster(1000L).onClickListener {
            if (pointsListAdapter.allPoints.size != 0) {
                view.flash.startAnimation(flash)

                snapshotFileName = SnapshotManager(activity).put(pointsListAdapter.allPoints)
            }
        }.onDoubleClickListener { renameSnapshot(snapshotFileName) })
        buttons.button_resume.setOnClickListener { v: View ->
            if (v.isActivated)
                stopScanService()
            else
                startScanService()
        }
        (buttons.spinner_delay as Spinner).setSelection(sp.getString(I.PREF_DEFAULT_DELAY, "1").toInt())
        buttons.spinner_delay.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sendScanDelay()
            }
        }
        buttons.button_clear.setOnClickListener(DoubleClickMaster({
            scanConnection.clearPointsList()
            label.text = pointsListAdapter.clear()
        }).onClickListener { pointsListAdapter.resetFocus() })
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.Secure.getInt(activity.contentResolver, LOCATION_MODE) == 0)
            AlertDialog.Builder(activity)
                    .setMessage(R.string.geolocation_need)
                    .setPositiveButton(R.string.got_it, null)
                    .setCancelable(false)
                    .create().show()
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
            START_SCAN.ordinal -> pointsListAdapter.animScan(true)
            RESULTS.ordinal -> updateList(msg)
            STARTED.ordinal -> view.button_resume.isActivated = true
            STOPPED.ordinal -> {
                view.button_resume.isActivated = false
                pointsListAdapter.animScan(false)
            }
        }
    }

    private fun updateList(msg: Message) {
        if (msg.obj.javaClass == ArrayList<Point>().javaClass) {
            view.button_resume.isActivated = msg.arg1.toBoolean()

            updateCounters(pointsListAdapter.updateList(msg.obj as ArrayList<Point>))
        }
    }

    private fun updateCounters(counters: String) {
        view.label.text = counters
    }

    private fun renameSnapshot(lastName: String) {
        val file = activity.getDatabasePath(lastName)
        if (file.exists()) {
            val editText = FileNameInputText(activity)
            editText.setText(lastName)
            AlertDialog.Builder(activity)
                    .setTitle(R.string.rename_to)
                    .setView(editText)
                    .setCancelable(false)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok, { _, _ ->
                        var text = editText.text.toString()

                        if (text.isEmpty())
                            return@setPositiveButton

                        if (!text.endsWith(".db"))
                            text += ".db"

                        val success = file.renameTo(File(file.parent, text))
                        Toast.makeText(activity, if (success) R.string.success else R.string.failure, Toast.LENGTH_SHORT).show()
                    }).create().show()
        } else
            Toast.makeText(activity, R.string.failure, Toast.LENGTH_SHORT).show()
    }

    private fun showDescriptionIfNecessary(description: View, point: Point?) {
        if (point != null && sp.getBoolean(I.PREF_SHOW_DESCRIPTION, false)) {
            description.visibility = View.VISIBLE

            description.tv_essid.text = getString(R.string.essid_format, point.getNotEmptyESSID())
            description.tv_bssid.text = getString(R.string.bssid_format, point.bssid)
            description.tv_capab.text = getString(R.string.capab_format, point.capabilities)
            description.tv_frequ.text = getString(R.string.frequ_format, point.frequency, point.ch)
            description.tv_manuf.text = getString(R.string.manuf_format, point.manufacturer)
        } else
            description.visibility = View.GONE
    }

    private fun updateConnectionInfo() {
        if (view != null)
            pointsListAdapter.connectionInfo = wifiManager.connectionInfo

        activity.title = getString(R.string.app_name) + "   " + Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
    }
}
