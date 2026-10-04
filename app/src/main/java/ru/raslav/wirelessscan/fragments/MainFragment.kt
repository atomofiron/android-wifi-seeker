package ru.raslav.wirelessscan.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.app.BackgroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.S
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.View.NO_ID
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
import androidx.core.graphics.Insets
import androidx.core.location.LocationManagerCompat
import androidx.core.os.BundleCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import lib.atomofiron.insets.InsetsSource
import lib.atomofiron.insets.ViewInsetsDelegate
import lib.atomofiron.insets.insetsDelegate
import lib.atomofiron.insets.insetsPadding
import lib.atomofiron.insets.insetsSource
import ru.raslav.wirelessscan.Const
import ru.raslav.wirelessscan.Const.DEFAULT_PERIOD
import ru.raslav.wirelessscan.Const.PREF_DEFAULT_PERIOD
import ru.raslav.wirelessscan.MainActivity
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.ScanService
import ru.raslav.wirelessscan.adapters.PointListAdapter
import ru.raslav.wirelessscan.colorAttr
import ru.raslav.wirelessscan.connection.Connection.Event
import ru.raslav.wirelessscan.connection.ScanConnection
import ru.raslav.wirelessscan.databinding.FragmentMainBinding
import ru.raslav.wirelessscan.databinding.LayoutButtonsPaneBinding
import ru.raslav.wirelessscan.databinding.LayoutFiltersPaneBinding
import ru.raslav.wirelessscan.dlog
import ru.raslav.wirelessscan.elog
import ru.raslav.wirelessscan.granted
import ru.raslav.wirelessscan.isWide
import ru.raslav.wirelessscan.longToast
import ru.raslav.wirelessscan.openPermissionSettings
import ru.raslav.wirelessscan.shortToast
import ru.raslav.wirelessscan.sp
import ru.raslav.wirelessscan.toBoolean
import ru.raslav.wirelessscan.tryStartActivity
import ru.raslav.wirelessscan.ui.drawable.ScanDrawable
import ru.raslav.wirelessscan.unsafeLazy
import ru.raslav.wirelessscan.utils.DoubleClickMaster
import ru.raslav.wirelessscan.utils.ExtType
import ru.raslav.wirelessscan.utils.FileNameInputText
import ru.raslav.wirelessscan.utils.LayoutOrientation.Companion.layoutChanges
import ru.raslav.wirelessscan.utils.LayoutOrientation.Companion.layoutOrientation
import ru.raslav.wirelessscan.utils.MaterialAttr
import ru.raslav.wirelessscan.utils.Orientation
import ru.raslav.wirelessscan.utils.Point
import ru.raslav.wirelessscan.utils.SnapshotManager
import ru.raslav.wirelessscan.withAlpha
import java.io.File
import java.net.Inet4Address
import android.os.Build.VERSION_CODES.TIRAMISU as T

class MainFragment : Fragment(), Titled {
    companion object {
        private const val EXTRA_SERVICE_WAS_STARTED = "EXTRA_SERVICE_WAS_STARTED"
        private const val EXTRA_POINTS = "EXTRA_POINTS"
    }
    private val sp: SharedPreferences by unsafeLazy { requireContext().sp() }
    private val wifiManager by unsafeLazy { requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    private val connectivityManager by unsafeLazy { requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    private val networkRequest by unsafeLazy { NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build() }
    private val scanConnection = ScanConnection(MessageHandler(), ::onServiceConnected)
    private val adapter by unsafeLazy { PointListAdapter(requireContext()) }
    private val menuProvider = MainMenuProvider()
    private val networkCallback = if (SDK_INT >= S) NewNetworkCallback() else NetworkCallback()
    private val mainHandler by unsafeLazy { Handler(Looper.getMainLooper()) }
    private val locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission(), LocationPermissionCallback())
    private val notificationsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private lateinit var scanDrawable: ScanDrawable
    private var scanPeriod = 0
    private var wifiInfo: WifiInfo? = null
    private lateinit var periodItem: MenuItem

    private val flashAnim: Animation by unsafeLazy { AnimationUtils.loadAnimation(requireContext(), R.anim.flash) }

    private lateinit var binding: FragmentMainBinding

    override val title: String get() = getString(R.string.app_name) + "   " + wifiIpAddress()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        flashAnim.setAnimationListener(FlashAnimationListener())
        scanConnection.bindService(requireContext())

        scanPeriod = requireContext().sp()
            .getString(PREF_DEFAULT_PERIOD, null)
            ?.toIntOrNull()
            ?.let { resources.getIntArray(R.array.period_arr_int)[it] }
            ?: DEFAULT_PERIOD

        Point.initColors(requireContext())
    }

    private fun onServiceConnected() {
        scanConnection.sendGetRequest()
        sendScanPeriod()
    }

    override fun onStop() {
        super.onStop()
        connectivityManager.unregisterNetworkCallback(networkCallback)
        if (!sp.getBoolean(Const.PREF_WORK_IN_BG, false))
            stopScanService()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanConnection.unbindService(requireContext())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(EXTRA_SERVICE_WAS_STARTED, binding.bottomToolbar.buttonResume.isActivated)
        outState.putParcelableArrayList(EXTRA_POINTS, ArrayList(adapter.allPoints))
    }

    override fun onStart() {
        super.onStart()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        updateConnectionInfo()
        view?.let { binding.permissionDisclaimer.isVisible = !locationGranted() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        binding = FragmentMainBinding.inflate(inflater, container, false)
        adapter.initAnim()

        val insets = ExtType { barsWithCutout + bottomToolbar }
        binding.counter.insetsPadding(insets, horizontal = true)
        binding.listTitle.root.insetsPadding(insets, horizontal = true)
        val toolbarDelegate = binding.bottomToolbar.root.insetsDelegate()
        binding.listView.insetsPadding(insets, start = true, end = true, bottom = true)
        binding.root.layoutChanges {
            binding.onLayoutChanged(it, toolbarDelegate)
        }
        binding.listView.onItemClickListener = adapter
        binding.listView.adapter = adapter

        initFilters(binding.bottomToolbar.filters)
        binding.initButtons(binding.counter)
        binding.listTitle.bssid.isVisible = resources.configuration.isWide()
        scanDrawable = ScanDrawable(
            color = requireContext().colorAttr(MaterialAttr.colorSurfaceContainer),
            scanColor = requireContext().colorAttr(MaterialAttr.colorPrimaryInverse) withAlpha 0.1f,
            cornerRadius = resources.getDimension(R.dimen.toolbar_corner),
            anchor = resources.getDimensionPixelSize(R.dimen.toolbar_padding)
                    + resources.getDimensionPixelSize(R.dimen.toolbar_button_margin)
                    + resources.getDimensionPixelSize(R.dimen.toolbar_button_size) / 2
        )
        binding.bottomToolbar.root.background = scanDrawable
        binding.bottomToolbar.root.clipToOutline = true
        binding.permissionDisclaimer.isVisible = !locationGranted()
        binding.btnGrant.setOnClickListener { requireContext().openPermissionSettings() }

        if (savedInstanceState != null) {
            adapter.updateList(BundleCompat.getParcelableArrayList(savedInstanceState, EXTRA_POINTS, Point::class.java))
        }
        val layoutOrientation = binding.root.layoutOrientation()
        binding.bottomToolbar.root.insetsSource {
            val orientation = layoutOrientation.orientation()
            val insets = if (orientation is Orientation.Bottom) {
                Insets.of(0, 0, 0, it.height + it.marginBottom * 2)
            } else {
                val width = it.width + it.marginStart + it.marginEnd
                when {
                    orientation.left -> Insets.of(width + it.translationX.toInt(), 0, 0, 0)
                    else -> Insets.of(0, 0, width - it.translationX.toInt(), 0)
                }
            }
            InsetsSource.submit(ExtType.bottomToolbar, insets)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!isHidden) {
            requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }
        when {
            savedInstanceState?.getBoolean(EXTRA_SERVICE_WAS_STARTED, true) == false -> Unit
            locationGranted() -> binding.bottomToolbar.tryStartScanServiceIfWifiEnabled()
        }
    }

    /** A hidden fragment keeps the RESUMED state, so its view is not destroyed and the menu is handled manually */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        when {
            hidden -> activity?.removeMenuProvider(menuProvider)
            else -> activity?.addMenuProvider(menuProvider)
        }
    }

    private fun updatePeriodIcon() {
        val index = resources.getIntArray(R.array.period_arr_int)
            .indexOf(scanPeriod)
        periodItem.setIcon(PeriodIcons[index])
    }

    private inner class MainMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
            inflater.inflate(R.menu.main, menu)
            periodItem = menu.findItem(R.id.period)
            updatePeriodIcon()
            val subMenu = periodItem.subMenu ?: return
            resources.getStringArray(R.array.period_arr).forEachIndexed { index, it ->
                subMenu.add(Menu.NONE, PeriodIds[index], Menu.NONE, it)
            }
        }

        override fun onMenuItemSelected(item: MenuItem): Boolean {
            val periods = resources.getIntArray(R.array.period_arr_int)
            when (item.itemId) {
                R.id.period_3s,
                R.id.period_5s,
                R.id.period_10s,
                R.id.period_30s,
                R.id.period_1m,
                R.id.period_3m,
                R.id.period_5m -> {
                    scanPeriod = periods[PeriodIds.indexOf(item.itemId)]
                    sendScanPeriod()
                    updatePeriodIcon()
                }
                else -> return false
            }
            return true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter.resetAnim()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.listTitle.bssid.isVisible = newConfig.isWide()
    }

    private fun initFilters(binding: LayoutFiltersPaneBinding) {
        val layout = binding.root
        val listener = View.OnClickListener { view ->
            var state = PointListAdapter.FILTER_DEFAULT
            when {
                view.isSelected -> view.isSelected = false
                view.isActivated -> {
                    view.isActivated = false
                    view.isSelected = true
                    state = PointListAdapter.FILTER_EXCLUDE
                }
                else -> {
                    view.isActivated = true
                    state = PointListAdapter.FILTER_INCLUDE
                }
            }
            updateCounters(adapter.updateFilter(layout.indexOfChild(view), state))
        }
        for (i in 0 until layout.childCount)
            layout.getChildAt(i).setOnClickListener(listener)
    }

    private fun FragmentMainBinding.initButtons(label: TextView) {
        bottomToolbar.buttonFilter.setOnClickListener { view ->
            view.isActivated = !view.isActivated
            updateCounters(adapter.filter(view.isActivated))
            bottomToolbar.verticalFilters.isVisible = view.isActivated && bottomToolbar.verticalFilters.isNotEmpty()
            bottomToolbar.horizontalFilters.isVisible = view.isActivated && bottomToolbar.horizontalFilters.isNotEmpty()
        }
        var snapshotFileName: String? = null
        bottomToolbar.buttonSave.setOnClickListener(DoubleClickMaster(1000L).onClickListener {
            if (adapter.allPoints.isNotEmpty()) {
                binding.flash.startAnimation(flashAnim)
                snapshotFileName = SnapshotManager(requireContext()).put(adapter.allPoints)
            }
        }.onDoubleClickListener { renameSnapshot(snapshotFileName ?: return@onDoubleClickListener) })
        bottomToolbar.buttonResume.setOnClickListener { view ->
            when {
                view.isActivated -> stopScanService()
                else -> checkPermissionAndStartScan()
            }
        }
        bottomToolbar.buttonClear.setOnClickListener(DoubleClickMaster {
            scanConnection.clearPointsList()
            label.text = adapter.clear()
        }.onClickListener {
            scanConnection.clearOutOfRangePoints()
            label.text = adapter.clearOutOfRange()
        })
        bottomToolbar.buttonList.setOnClickListener {
            val intent = Intent(activity, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_SNAPSHOTS_LIST)
            requireContext().startActivity(intent)
        }
    }

    private fun locationGranted() = requireContext().checkSelfPermission(Const.LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun notificationsGranted() = SDK_INT < T || requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun checkPermissionAndStartScan() {
        when {
            !locationGranted() -> locationPermissionLauncher.launch(Const.LOCATION_PERMISSION)
            !requireContext().granted(Manifest.permission.ACCESS_WIFI_STATE) -> requireContext().shortToast(R.string.no_perm)
            else -> startScanService()
        }
    }

    private fun LayoutButtonsPaneBinding.tryStartScanServiceIfWifiEnabled() {
        if (wifiManager.isWifiEnabled) {
            if (!notificationsGranted()) {
                notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            buttonResume.isActivated = true
            tryStartScanService()
        }
    }

    private fun tryStartScanService() = when {
        SDK_INT < S -> startScanService()
        else -> try {
            startScanService()
        } catch (e: BackgroundServiceStartNotAllowedException) {
            requireContext().longToast(e.toString())
            elog(e.toString())
        }
    }

    private fun startScanService() {
        if (!wifiManager.isWifiEnabled) {
            requestWifiEnabled()
        }
        requireContext().startService(Intent(requireContext(), ScanService::class.java))

        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(locationManager))
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.geolocation_need)
                .setPositiveButton(R.string.got_it, null)
                .setCancelable(false)
                .create().show()
    }

    /** Wi-Fi cannot be enabled programmatically starting with Android 10, the system panel is shown instead */
    @Suppress("DEPRECATION")
    private fun requestWifiEnabled() = when {
        SDK_INT >= Q -> requireContext().tryStartActivity(Intent(Settings.Panel.ACTION_WIFI))
        else -> wifiManager.isWifiEnabled = true
    }

    private fun stopScanService() = scanConnection.stopScanService()

    private fun sendScanPeriod() = scanConnection.sendScanPeriod(scanPeriod)

    private fun FragmentMainBinding.updateState(message: Message) {
        dlog("-> ${message.run { Event.entries[what] }}")
        view ?: return

        scanDrawable.showAnimation(message.what == Event.START_SCAN.ordinal)
        when (message.what) {
            Event.START_SCAN.ordinal -> adapter.animScanStart()
            Event.RESULTS.ordinal -> updateList(message)
            Event.STARTED.ordinal -> bottomToolbar.buttonResume.isActivated = true
            Event.STOPPED.ordinal -> {
                bottomToolbar.buttonResume.isActivated = false
                adapter.animScanCancel()
            }
        }
    }

    private fun updateList(msg: Message) {
        if (msg.obj.javaClass == ArrayList<Point>().javaClass) {
            binding.bottomToolbar.buttonResume.isActivated = msg.arg1.toBoolean()
            @Suppress("UNCHECKED_CAST")
            updateCounters(adapter.updateList(msg.obj as ArrayList<Point>))
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
            MaterialAlertDialogBuilder(requireContext())
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
                    val messageId = if (success) R.string.success else R.string.failure
                    Toast.makeText(context, messageId, Toast.LENGTH_SHORT).show()
                }.create().show()
        } else
            Toast.makeText(activity, R.string.failure, Toast.LENGTH_SHORT).show()
    }

    private fun updateConnectionInfo() {
        adapter.connectionInfo = currentWifiInfo()
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
    private inner class MessageHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) = binding.updateState(msg)
    }

    private fun notifyConnectionChanged() {
        mainHandler.post { updateConnectionInfo() }
    }

    @Suppress("DEPRECATION")
    private fun currentWifiInfo(): WifiInfo? = if (SDK_INT >= S) wifiInfo else wifiManager.connectionInfo

    private fun FragmentMainBinding.onLayoutChanged(
        orientation: Orientation,
        toolbarDelegate: ViewInsetsDelegate,
    ) {
        val vertical = orientation.vertical
        bottomToolbar.root.updateLayoutParams<FrameLayout.LayoutParams> {
            gravity = when (orientation) {
                is Orientation.Start -> Gravity.START or Gravity.CENTER_VERTICAL
                is Orientation.End -> Gravity.END or Gravity.CENTER_VERTICAL
                is Orientation.Bottom -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            val margin = resources.getDimensionPixelSize(R.dimen.padding_common)
            bottomMargin = if (orientation.vertical) margin else 0
        }
        bottomToolbar.filters.root.let { filters ->
            (filters.parent as ViewGroup).removeView(filters)
            if (orientation == Orientation.Bottom) {
                bottomToolbar.horizontalFilters.addView(filters)
                bottomToolbar.horizontalFilters.isVisible = bottomToolbar.buttonFilter.isActivated
                bottomToolbar.verticalFilters.isVisible = false
            } else {
                bottomToolbar.verticalFilters.addView(filters)
                bottomToolbar.verticalFilters.isVisible = bottomToolbar.buttonFilter.isActivated
                bottomToolbar.horizontalFilters.isVisible = false
            }
            filters.orientation = if (vertical) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        toolbarDelegate.changeInsets {
            when (orientation) {
                is Orientation.Start -> translation(start, bottom)
                is Orientation.End -> translation(bottom, end)
                is Orientation.Bottom -> translation(start, end, bottom)
            }
        }
        bottomToolbar.buttons.orientation = if (vertical) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        bottomToolbar.buttons.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = if (vertical) MATCH_PARENT else WRAP_CONTENT
            height = if (vertical) WRAP_CONTENT else MATCH_PARENT
            topToTop = if (vertical) NO_ID else PARENT_ID
            startToStart = if (orientation.start) PARENT_ID else NO_ID
            endToEnd = if (orientation.end) PARENT_ID else NO_ID
        }
        bottomToolbar.verticalFilters.updateLayoutParams<ConstraintLayout.LayoutParams> {
            startToStart = if (orientation.start) NO_ID else PARENT_ID
            endToEnd = if (orientation.start) PARENT_ID else NO_ID
            startToEnd = if (orientation.start) R.id.buttons else NO_ID
            endToStart = if (orientation.start) NO_ID else R.id.buttons
        }
        scanDrawable.showOrientation(orientation)
        adapter.notifyDataSetChanged()
    }

    private fun wifiIpAddress(): String {
        val network = connectivityManager.activeNetwork ?: return ""
        connectivityManager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            ?.takeIf { it }
            ?: return ""
        return connectivityManager.getLinkProperties(network)
            ?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }
            ?.address
            ?.hostAddress
            .orEmpty()
    }

    @RequiresApi(S)
    private inner class NewNetworkCallback : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            wifiInfo = capabilities.transportInfo as? WifiInfo
            notifyConnectionChanged()
        }

        override fun onLost(network: Network) {
            wifiInfo = null
            notifyConnectionChanged()
        }
    }

    private inner class NetworkCallback : ConnectivityManager.NetworkCallback() {

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = notifyConnectionChanged()

        override fun onLost(network: Network) = notifyConnectionChanged()
    }

    private inner class LocationPermissionCallback : ActivityResultCallback<Boolean> {

        override fun onActivityResult(result: Boolean) {
            when {
                result -> {
                    binding.permissionDisclaimer.isVisible = false
                    tryStartScanService()
                }
                !shouldShowRequestPermissionRationale(Const.LOCATION_PERMISSION) -> requireContext().openPermissionSettings()
            }
        }
    }
}

private val PeriodIds = intArrayOf(R.id.period_3s, R.id.period_5s, R.id.period_10s, R.id.period_30s, R.id.period_1m, R.id.period_3m, R.id.period_5m)
private val PeriodIcons = intArrayOf(R.drawable.ic_3_sec, R.drawable.ic_5_sec, R.drawable.ic_10_sec, R.drawable.ic_30_sec, R.drawable.ic_1_min, R.drawable.ic_3_min, R.drawable.ic_5_min)
