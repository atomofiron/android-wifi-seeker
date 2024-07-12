package ru.raslav.wirelessscan.fragments

import android.annotation.SuppressLint
import ru.raslav.wirelessscan.adapters.PointsListAdapter
import ru.raslav.wirelessscan.Const.WIDE_MODE

import ru.raslav.wirelessscan.utils.Point
import android.content.*
import android.content.Context.WIFI_SERVICE
import android.net.wifi.WifiManager
import android.os.*
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.M
import android.provider.Settings
import android.text.format.Formatter
import android.view.*
import ru.raslav.wirelessscan.utils.DoubleClickMaster
import ru.raslav.wirelessscan.connection.ScanConnection
import ru.raslav.wirelessscan.connection.Connection.Event
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import ru.raslav.wirelessscan.utils.FileNameInputText
import ru.raslav.wirelessscan.utils.SnapshotManager
import ru.raslav.wirelessscan.*
import ru.raslav.wirelessscan.databinding.FragmentMainBinding
import ru.raslav.wirelessscan.databinding.LayoutButtonsPaneBinding
import ru.raslav.wirelessscan.databinding.LayoutDescriptionBinding
import java.io.File

class MainFragment : Fragment() {
    companion object {
        private const val EXTRA_SERVICE_WAS_STARTED = "EXTRA_SERVICE_WAS_STARTED"
        private const val EXTRA_POINTS = "EXTRA_POINTS"
    }
    private val sp: SharedPreferences by unsafeLazy { requireContext().sp() }
    private val wifiManager by unsafeLazy { requireContext().applicationContext.getSystemService(WIFI_SERVICE) as WifiManager }
    private val scanConnection = ScanConnection(MessageHandler(), ::onServiceConnected)
    private val pointsListAdapter by unsafeLazy { PointsListAdapter(requireContext(), binding.listView) }
    private val connectionReceiver = ConnectionReceiver()

    private val flash: Animation by unsafeLazy { AnimationUtils.loadAnimation(requireContext(), R.anim.flash) }

    private lateinit var binding: FragmentMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // todo deprecation
        setHasOptionsMenu(true)

        flash.setAnimationListener(FlashAnimationListener())

        scanConnection.bindService(requireContext())

        val filter = IntentFilter()
        filter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        requireContext().registerReceiver(connectionReceiver, filter)
    }

    private fun onServiceConnected() {
        scanConnection.sendGetRequest()
        sendScanDelay()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRemoving && !sp.getBoolean(Const.PREF_WORK_IN_BG, false))
            stopScanService()

        scanConnection.unbindService(requireContext())
        requireContext().unregisterReceiver(connectionReceiver)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(EXTRA_SERVICE_WAS_STARTED, binding.buttons.buttonResume.isActivated)
        outState.putParcelableArrayList(EXTRA_POINTS, ArrayList(pointsListAdapter.allPoints))
    }

    override fun onStart() {
        super.onStart()

        updateConnectionInfo()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        binding = FragmentMainBinding.inflate(inflater, container, false)

        if (WIDE_MODE)
            binding.layoutDescription.tvBssid.visibility = View.VISIBLE

        binding.listView.adapter = pointsListAdapter
        pointsListAdapter.onPointClickListener = { point -> showDescriptionIfNecessary(binding.layoutDescription, point) }

        initFilters(binding.filters.root as ViewGroup)
        initButtons(binding.buttons, binding.label)

        if (savedInstanceState?.getBoolean(EXTRA_SERVICE_WAS_STARTED, true) != false)
            startScanServiceIfWifiEnabled(binding.buttons.buttonResume)

        if (savedInstanceState != null)
            pointsListAdapter.updateList(savedInstanceState.getParcelableArrayList(EXTRA_POINTS)) // todo deprecation

        return binding.root
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

    private fun initButtons(buttons: LayoutButtonsPaneBinding, label: TextView) {
        buttons.buttonFilter.setOnClickListener { v ->
            v.isActivated = !v.isActivated
            updateCounters(pointsListAdapter.filter(v.isActivated))
            buttons.buttonFilter.visibility = if (v.isActivated) View.VISIBLE else View.GONE
        }
        var snapshotFileName: String? = null
        buttons.buttonSave.setOnClickListener(DoubleClickMaster(1000L).onClickListener {
            if (pointsListAdapter.allPoints.size != 0) {
                binding.flash.startAnimation(flash)

                snapshotFileName = SnapshotManager(requireContext()).put(pointsListAdapter.allPoints)
            }
        }.onDoubleClickListener { renameSnapshot(snapshotFileName ?: return@onDoubleClickListener) })
        buttons.buttonResume.setOnClickListener { v: View ->
            if (v.isActivated)
                stopScanService()
            else
                startScanService()
        }
        buttons.spinnerDelay.setSelection(sp.getString(Const.PREF_DEFAULT_DELAY, 1.toString())!!.toInt())
        buttons.spinnerDelay.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) = sendScanDelay()
        }
        buttons.buttonClear.setOnClickListener(DoubleClickMaster {
            scanConnection.clearPointsList()
            label.text = pointsListAdapter.clear()
        }.onClickListener { pointsListAdapter.resetFocus() })
        buttons.buttonList.setOnClickListener {
            val intent = Intent(activity, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_SNAPSHOTS_LIST)
            requireContext().startActivity(intent)
        }
    }

    private fun startScanServiceIfWifiEnabled(buttonResume: View) {
        buttonResume.isActivated = wifiManager.isWifiEnabled
        if (buttonResume.isActivated)
            startScanService()
    }

    private fun startScanService() {
        if (!wifiManager.isWifiEnabled)
            // todo deprecation
            wifiManager.isWifiEnabled = true

        requireContext().startService(Intent(requireContext(), ScanService::class.java))

        if (SDK_INT >= M && Settings.Secure.getInt(requireContext().contentResolver, Settings.Secure.LOCATION_MODE) == 0)
            AlertDialog.Builder(requireContext())
                    .setMessage(R.string.geolocation_need)
                    .setPositiveButton(R.string.got_it, null)
                    .setCancelable(false)
                    .create().show()
    }

    private fun stopScanService() = scanConnection.stopScanService()

    private fun sendScanDelay() {
        val selected = binding.buttons.spinnerDelay.selectedItemPosition
        val delay = resources.getIntArray(R.array.delay_arr_int)[selected]
        scanConnection.sendScanDelay(delay)
    }

    private fun FragmentMainBinding.updateState(mesaage: Message) {
        report("event: ${mesaage.run { Event.entries[what] }}")
        if (view == null) return

        progress.isVisible = mesaage.what == Event.START_SCAN.ordinal
        when (mesaage.what) {
            Event.START_SCAN.ordinal -> pointsListAdapter.animScan(true)
            Event.RESULTS.ordinal -> updateList(mesaage)
            Event.STARTED.ordinal -> buttons.buttonResume.isActivated = true
            Event.STOPPED.ordinal -> {
                buttons.buttonResume.isActivated = false
                pointsListAdapter.animScan(false)
            }
        }
    }

    private fun updateList(msg: Message) {
        if (msg.obj.javaClass == ArrayList<Point>().javaClass) {
            binding.buttons.buttonResume.isActivated = msg.arg1.toBoolean()

            updateCounters(pointsListAdapter.updateList(msg.obj as ArrayList<Point>)) // todo wtf
        }
    }

    private fun updateCounters(counters: String) {
        binding.label.text = counters
    }

    private fun renameSnapshot(lastName: String) {
        val file = File(requireContext().filesDir, lastName)
        if (file.exists()) {
            val editText = FileNameInputText(requireContext())
            editText.setText(lastName)
            AlertDialog.Builder(requireContext())
                    .setTitle(R.string.rename_to)
                    .setView(editText)
                    .setCancelable(false)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        var text = editText.text.toString()

                        if (text.isEmpty())
                            return@setPositiveButton

                        if (!text.endsWith(Const.SNAPSHOT_FORMAT))
                            text += Const.SNAPSHOT_FORMAT

                        val success = file.renameTo(File(file.parent, text))
                        Toast.makeText(
                            activity,
                            if (success) R.string.success else R.string.failure,
                            Toast.LENGTH_SHORT
                        ).show()
                    }.create().show()
        } else
            Toast.makeText(activity, R.string.failure, Toast.LENGTH_SHORT).show()
    }

    private fun showDescriptionIfNecessary(description: LayoutDescriptionBinding, point: Point?) {
        if (point != null && sp.getBoolean(Const.PREF_SHOW_DESCRIPTION, false)) {
            description.root.visibility = View.VISIBLE

            description.tvEssid.text = getString(R.string.essid_format, point.getNotEmptyESSID())
            description.tvBssid.text = getString(R.string.bssid_format, point.bssid)
            description.tvCapab.text = getString(R.string.capab_format, point.capabilities)
            description.tvFrequ.text = getString(R.string.frequ_format, point.frequency, point.ch)
            description.tvManuf.text = getString(R.string.manuf_format, point.manufacturer)
        } else
            description.root.visibility = View.GONE
    }

    private fun updateConnectionInfo() {
        pointsListAdapter.connectionInfo = wifiManager.connectionInfo

        requireActivity().title = getString(R.string.app_name) + "   " + Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
    }

    private inner class FlashAnimationListener : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            binding.flash.isVisible = true
        }
        override fun onAnimationEnd(animation: Animation) {
            binding.flash.isVisible = false
        }
        override fun onAnimationRepeat(animation: Animation) {}
    }

    @SuppressLint("HandlerLeak")
    private inner class MessageHandler : Handler() {
        override fun handleMessage(msg: Message) = binding.updateState(msg)
    }

    private inner class ConnectionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateConnectionInfo()
    }
}
