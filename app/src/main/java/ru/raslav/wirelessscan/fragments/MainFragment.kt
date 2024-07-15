package ru.raslav.wirelessscan.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.M
import android.os.Build.VERSION_CODES.TIRAMISU as T
import android.annotation.SuppressLint
import android.app.BackgroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.wifi.WifiManager
import android.os.Build.VERSION_CODES.S
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.provider.Settings
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import lib.atomofiron.insets.ViewInsetsDelegate
import lib.atomofiron.insets.insetsDelegate
import ru.raslav.wirelessscan.Const
import ru.raslav.wirelessscan.MainActivity
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.ScanService
import ru.raslav.wirelessscan.adapters.PointListAdapter
import ru.raslav.wirelessscan.connection.Connection.Event
import ru.raslav.wirelessscan.connection.ScanConnection
import ru.raslav.wirelessscan.databinding.FragmentMainBinding
import ru.raslav.wirelessscan.databinding.LayoutButtonsPaneBinding
import ru.raslav.wirelessscan.databinding.LayoutDescriptionBinding
import ru.raslav.wirelessscan.granted
import ru.raslav.wirelessscan.isWide
import ru.raslav.wirelessscan.openPermissionSettings
import ru.raslav.wirelessscan.report
import ru.raslav.wirelessscan.shortToast
import ru.raslav.wirelessscan.sp
import ru.raslav.wirelessscan.toBoolean
import ru.raslav.wirelessscan.unsafeLazy
import ru.raslav.wirelessscan.utils.DoubleClickMaster
import ru.raslav.wirelessscan.utils.FileNameInputText
import ru.raslav.wirelessscan.utils.LayoutDelegate.Companion.layoutChanges
import ru.raslav.wirelessscan.utils.Orientation
import ru.raslav.wirelessscan.utils.Point
import ru.raslav.wirelessscan.utils.SnapshotManager
import java.io.File

class MainFragment : Fragment(), Titled {
    companion object {
        private const val EXTRA_SERVICE_WAS_STARTED = "EXTRA_SERVICE_WAS_STARTED"
        private const val EXTRA_POINTS = "EXTRA_POINTS"
    }
    private val sp: SharedPreferences by unsafeLazy { requireContext().sp() }
    private val wifiManager by unsafeLazy { requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    private val scanConnection = ScanConnection(MessageHandler(), ::onServiceConnected)
    private val adapter by unsafeLazy { PointListAdapter(requireContext()) }
    private val connectionReceiver = ConnectionReceiver()

    private val flashAnim: Animation by unsafeLazy { AnimationUtils.loadAnimation(requireContext(), R.anim.flash) }

    private lateinit var binding: FragmentMainBinding

    override val title: String get() = getString(R.string.app_name) + "   " + Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress) // todo deprecation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // todo deprecation
        setHasOptionsMenu(true)

        flashAnim.setAnimationListener(FlashAnimationListener())

        scanConnection.bindService(requireContext())

        val filter = IntentFilter()
        filter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        requireContext().registerReceiver(connectionReceiver, filter)

        Point.initColors(requireContext())
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
        outState.putParcelableArrayList(EXTRA_POINTS, ArrayList(adapter.allPoints))
    }

    override fun onStart() {
        super.onStart()

        updateConnectionInfo()
        view?.let { binding.permissionDisclaimer.isVisible = !locationGranted() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        binding = FragmentMainBinding.inflate(inflater, container, false)
        adapter.initAnim()

        val counterInsets = binding.counter.insetsDelegate()
        val headerInsets = binding.layoutItem.root.insetsDelegate()
        val listInsets = binding.listView.insetsDelegate()
        val descriptionInsets = binding.layoutDescription.root.insetsDelegate()
        val filtersInsets = binding.filters.root.insetsDelegate()
        val buttonsInsets = binding.buttons.root.insetsDelegate()
        binding.root.layoutChanges {
            binding.onLayoutChanged(it, counterInsets, headerInsets, listInsets, descriptionInsets, filtersInsets, buttonsInsets)
        }
        binding.listView.setOnItemClickListener { _, _, position, _ ->
            val point = adapter[position]
            showDescription(binding.layoutDescription, point)
            adapter.setFocused(point)
        }
        binding.listView.adapter = adapter

        initFilters(binding.filters.layoutFilters.root)
        binding.initButtons(binding.counter)
        binding.layoutItem.bssid.isVisible = resources.configuration.isWide()
        binding.layoutDescription.cross.setOnClickListener {
            adapter.resetFocus()
            showDescription(binding.layoutDescription, null)
        }
        binding.permissionDisclaimer.isVisible = !locationGranted()
        binding.btnGrant.setOnClickListener { requireContext().openPermissionSettings() }

        if (savedInstanceState != null)
            adapter.updateList(savedInstanceState.getParcelableArrayList(EXTRA_POINTS)) // todo deprecation

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        when {
            savedInstanceState?.getBoolean(EXTRA_SERVICE_WAS_STARTED, true) == false -> Unit
            locationGranted() -> binding.buttons.tryStartScanServiceIfWifiEnabled()
            else -> requestPermissions(arrayOf(Const.LOCATION_PERMISSION), Const.LOCATION_REQUEST_CODE).also { report("onViewCreated requestPermissions") }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter.resetAnim()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.layoutItem.bssid.isVisible = newConfig.isWide()
    }

    private fun initFilters(filters: ViewGroup) {
        val listener = View.OnClickListener { v ->
            var state = PointListAdapter.FILTER_DEFAULT
            when {
                v.isSelected -> v.isSelected = false
                v.isActivated -> {
                    v.isActivated = false
                    v.isSelected = true
                    state = PointListAdapter.FILTER_EXCLUDE
                }
                else -> {
                    v.isActivated = true
                    state = PointListAdapter.FILTER_INCLUDE
                }
            }
            updateCounters(adapter.updateFilter(filters.indexOfChild(v), state))
        }
        for (i in 0 until filters.childCount)
            filters.getChildAt(i).setOnClickListener(listener)
    }

    private fun FragmentMainBinding.initButtons(label: TextView) {
        buttons.buttonFilter.setOnClickListener { v ->
            v.isActivated = !v.isActivated
            updateCounters(adapter.filter(v.isActivated))
            filters.root.isVisible = v.isActivated
        }
        var snapshotFileName: String? = null
        buttons.buttonSave.setOnClickListener(DoubleClickMaster(1000L).onClickListener {
            if (adapter.allPoints.size != 0) {
                binding.flash.startAnimation(flashAnim)

                snapshotFileName = SnapshotManager(requireContext()).put(adapter.allPoints)
            }
        }.onDoubleClickListener { renameSnapshot(snapshotFileName ?: return@onDoubleClickListener) })
        buttons.buttonResume.setOnClickListener { view ->
            if (view.isActivated)
                stopScanService()
            else
                checkPermissionAndStartScan()
        }
        buttons.spinnerDelay.setSelection(sp.getString(Const.PREF_DEFAULT_DELAY, 1.toString())!!.toInt())
        buttons.spinnerDelay.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) = sendScanDelay()
        }
        buttons.buttonClear.setOnClickListener(DoubleClickMaster {
            scanConnection.clearPointsList()
            label.text = adapter.clear()
        }.onClickListener {
            adapter.resetFocus()
            showDescription(binding.layoutDescription, null)
        })
        buttons.buttonList.setOnClickListener {
            val intent = Intent(activity, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_SNAPSHOTS_LIST)
            requireContext().startActivity(intent)
        }
    }

    private fun locationGranted() = SDK_INT < M || requireContext().checkSelfPermission(Const.LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun notificationsGranted() = SDK_INT < T || requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun checkPermissionAndStartScan() {
        if (!locationGranted())
            requestPermissions(arrayOf(Const.LOCATION_PERMISSION), Const.LOCATION_REQUEST_CODE)
        else if (!requireContext().granted(Manifest.permission.ACCESS_WIFI_STATE))
            requireContext().shortToast(R.string.no_perm)
        else
            startScanService()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (requestCode == Const.NOTIFICATIONS_REQUEST_CODE) {
            // do nothing
        } else if (requestCode == Const.LOCATION_REQUEST_CODE && granted) {
            binding.permissionDisclaimer.isVisible = false
            tryStartScanService()
        } else if (!shouldShowRequestPermissionRationale(Const.LOCATION_PERMISSION)) {
            requireContext().openPermissionSettings()
        }
    }

    private fun LayoutButtonsPaneBinding.tryStartScanServiceIfWifiEnabled() {
        if (wifiManager.isWifiEnabled) {
            if (!notificationsGranted()) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), Const.NOTIFICATIONS_REQUEST_CODE)
            }
            buttonResume.isActivated = true
            tryStartScanService()
        }
    }

    private fun tryStartScanService() {
        when {
            SDK_INT < S -> startScanService()
            else -> try {
                startScanService()
            } catch (e: BackgroundServiceStartNotAllowedException) {
                report(e.toString())
            }
        }
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
        report("-> ${mesaage.run { Event.entries[what] }}")
        if (view == null) return

        progress.isVisible = mesaage.what == Event.START_SCAN.ordinal
        when (mesaage.what) {
            Event.START_SCAN.ordinal -> adapter.animScanStart()
            Event.RESULTS.ordinal -> updateList(mesaage)
            Event.STARTED.ordinal -> buttons.buttonResume.isActivated = true
            Event.STOPPED.ordinal -> {
                buttons.buttonResume.isActivated = false
                adapter.animScanCancel()
            }
        }
    }

    private fun updateList(msg: Message) {
        if (msg.obj.javaClass == ArrayList<Point>().javaClass) {
            binding.buttons.buttonResume.isActivated = msg.arg1.toBoolean()

            updateCounters(adapter.updateList(msg.obj as ArrayList<Point>)) // todo wtf
            adapter.animScanEnd()
        }
    }

    private fun updateCounters(counters: String) {
        binding.counter.text = counters
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

    private fun showDescription(description: LayoutDescriptionBinding, point: Point?) {
        if (point != null && sp.getBoolean(Const.PREF_SHOW_DESCRIPTION, true)) {
            description.root.visibility = View.VISIBLE

            description.tvEssid.text = getString(R.string.essid_format, point.getNotEmptyESSID())
            description.tvBssid.text = getString(R.string.bssid_format, point.bssid)
            description.tvCapab.text = getString(R.string.capab_format, point.capabilities)
            description.tvFrequ.text = getString(R.string.frequ_format, point.frequency, point.ch, point.level)
            description.tvManuf.text = getString(R.string.manuf_format, point.manufacturer)
            description.tvManufDesc.text = point.manufacturerDesc
        } else
            description.root.visibility = View.GONE
    }

    private fun updateConnectionInfo() {
        adapter.connectionInfo = wifiManager.connectionInfo
        if (isResumed) {
            // trigger the back stack listeners
            parentFragmentManager.beginTransaction()
                .addToBackStack(null)
                .commit()
            parentFragmentManager.popBackStack()
        }
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

    private fun FragmentMainBinding.onLayoutChanged(
        orientation: Orientation,
        counterInsets: ViewInsetsDelegate,
        headerInsets: ViewInsetsDelegate,
        listInsets: ViewInsetsDelegate,
        descriptionInsets: ViewInsetsDelegate,
        filtersInsets: ViewInsetsDelegate,
        buttonsInsets: ViewInsetsDelegate,
    ) {
        root.removeAllViews()
        if (orientation == Orientation.Start) {
            root.addView(buttons.root)
            root.addView(filters.root)
            root.addView(container)
        } else {
            root.addView(container)
            root.addView(filters.root)
            root.addView(buttons.root)
        }
        filters.run {
            val parent = layoutFilters.root.parent as ViewGroup
            parent.removeView(layoutFilters.root)
            when (orientation) {
                Orientation.Bottom -> horizontalScrollView.addView(layoutFilters.root)
                else -> scrollView.addView(layoutFilters.root)
            }
        }
        counterInsets.changeInsets {
            if (orientation != Orientation.End) padding(end)
        }
        descriptionInsets.changeInsets {
            when (orientation) {
                Orientation.Start -> padding(end, bottom)
                Orientation.Bottom -> padding(start, end)
                Orientation.End -> padding(start, bottom)
            }
        }
        headerInsets.changeInsets {
            when (orientation) {
                Orientation.Start -> padding(end)
                Orientation.Bottom -> padding(start, end)
                Orientation.End -> padding(start)
            }
        }
        listInsets.changeInsets {
            when (orientation) {
                Orientation.Start -> padding(end, bottom)
                Orientation.Bottom -> padding(start, end)
                Orientation.End -> padding(start, bottom)
            }
        }
        filtersInsets.changeInsets {
            if (orientation == Orientation.Bottom) padding(start, end) else padding(bottom)
        }
        buttonsInsets.changeInsets {
            when (orientation) {
                Orientation.Start -> padding(start, bottom)
                Orientation.Bottom -> padding(start, bottom, end)
                Orientation.End -> padding(bottom, end)
            }
        }
        val vertical = orientation.vertical
        root.orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        container.updateLayoutParams {
            this.width = if (vertical) LayoutParams.MATCH_PARENT else 0
            this.height = if (vertical) 0 else LayoutParams.MATCH_PARENT
        }
        buttons.root.orientation = if (vertical) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        val lpWidth = if (vertical) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT
        val lpHeight = if (vertical) LayoutParams.WRAP_CONTENT else LayoutParams.MATCH_PARENT
        buttons.root.updateLayoutParams {
            this.width = lpWidth
            this.height = lpHeight
        }
        filters.root.updateLayoutParams {
            this.width = lpWidth
            this.height = lpHeight
        }
        filters.layoutFilters.root.orientation = if (vertical) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        filters.layoutFilters.root.updateLayoutParams {
            this.width = lpWidth
            this.height = lpHeight
        }
        adapter.notifyDataSetChanged()
    }
}
