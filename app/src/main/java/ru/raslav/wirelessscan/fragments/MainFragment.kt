package ru.raslav.wirelessscan.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.app.BackgroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.wifi.WifiManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.provider.Settings
import android.text.format.Formatter
import android.view.Gravity
import android.view.LayoutInflater
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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
import androidx.core.graphics.Insets
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import lib.atomofiron.insets.InsetsSource
import lib.atomofiron.insets.insetsPadding
import lib.atomofiron.insets.insetsSource
import ru.raslav.wirelessscan.Const
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
import ru.raslav.wirelessscan.granted
import ru.raslav.wirelessscan.isWide
import ru.raslav.wirelessscan.openPermissionSettings
import ru.raslav.wirelessscan.report
import ru.raslav.wirelessscan.shortToast
import ru.raslav.wirelessscan.sp
import ru.raslav.wirelessscan.toBoolean
import ru.raslav.wirelessscan.unsafeLazy
import ru.raslav.wirelessscan.utils.AppCompatAttr
import ru.raslav.wirelessscan.utils.DoubleClickMaster
import ru.raslav.wirelessscan.utils.ExtType
import ru.raslav.wirelessscan.utils.FileNameInputText
import ru.raslav.wirelessscan.utils.LayoutOrientation.Companion.layoutChanges
import ru.raslav.wirelessscan.utils.LayoutOrientation.Companion.layoutOrientation
import ru.raslav.wirelessscan.utils.MaterialAttr
import ru.raslav.wirelessscan.utils.Orientation
import ru.raslav.wirelessscan.utils.Point
import ru.raslav.wirelessscan.utils.SnapshotManager
import ru.raslav.wirelessscan.ui.drawable.ScanDrawable
import ru.raslav.wirelessscan.withAlpha
import java.io.File
import android.os.Build.VERSION_CODES.TIRAMISU as T

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
    private lateinit var scanDrawable: ScanDrawable

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
        sendScanPeriod()
    }

    override fun onStop() {
        super.onStop()
        if (!sp.getBoolean(Const.PREF_WORK_IN_BG, false))
            stopScanService()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanConnection.unbindService(requireContext())
        requireContext().unregisterReceiver(connectionReceiver)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(EXTRA_SERVICE_WAS_STARTED, binding.bottomToolbar.buttonResume.isActivated)
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

        val insets = ExtType { barsWithCutout + bottomToolbar }
        binding.counter.insetsPadding(insets, horizontal = true)
        binding.listTitle.root.insetsPadding(insets, horizontal = true)
        binding.listView.insetsPadding(insets, start = true, end = true, bottom = true)
        binding.root.layoutChanges {
            binding.onLayoutChanged(it)
        }
        binding.listView.onItemClickListener = adapter
        binding.listView.adapter = adapter

        initFilters(binding.bottomToolbar.filters)
        binding.initButtons(binding.counter)
        binding.listTitle.bssid.isVisible = resources.configuration.isWide()
        scanDrawable = ScanDrawable(
            color = requireContext().colorAttr(MaterialAttr.colorSurface),
            scanColor = requireContext().colorAttr(AppCompatAttr.colorPrimary) withAlpha 0.1f,
            cornerRadius = resources.getDimension(R.dimen.toolbar_corner),
            bottom = resources.getDimensionPixelSize(R.dimen.toolbar_padding)
                    + resources.getDimensionPixelSize(R.dimen.toolbar_button_margin)
                    + resources.getDimensionPixelSize(R.dimen.toolbar_button_size) / 2
        )
        binding.bottomToolbar.root.background = scanDrawable
        binding.bottomToolbar.root.clipToOutline = true
        binding.permissionDisclaimer.isVisible = !locationGranted()
        binding.btnGrant.setOnClickListener { requireContext().openPermissionSettings() }

        if (savedInstanceState != null) {
            adapter.updateList(savedInstanceState.getParcelableArrayList(EXTRA_POINTS)) // todo deprecation
        }
        val layoutOrientation = binding.root.layoutOrientation()
        binding.bottomToolbar.root.insetsSource {
            val orientation = layoutOrientation.orientation()
            val insets = if (orientation is Orientation.Bottom) {
                Insets.of(0, 0, 0, it.height + it.marginBottom * 2)
            } else {
                val width = it.width + it.marginStart + it.marginEnd
                when {
                    orientation.right -> Insets.of(0, 0, width, 0)
                    else -> Insets.of(width, 0, 0, 0)
                }
            }
            InsetsSource.submit(ExtType.bottomToolbar, insets)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        when {
            savedInstanceState?.getBoolean(EXTRA_SERVICE_WAS_STARTED, true) == false -> Unit
            locationGranted() -> binding.bottomToolbar.tryStartScanServiceIfWifiEnabled()
            else -> requestPermissions(arrayOf(Const.LOCATION_PERMISSION), Const.LOCATION_REQUEST_CODE).also { report("onViewCreated requestPermissions") }
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
            if (view.isActivated)
                stopScanService()
            else
                checkPermissionAndStartScan()
        }
        /*bottomToolbar.spinnerPeriod.setSelection(sp.getString(Const.PREF_DEFAULT_PERIOD, 1.toString())!!.toInt())
        bottomToolbar.spinnerPeriod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) = sendScanPeriod()
        }*/
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
            scanDrawable.showAnimation(true)
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

        if (Settings.Secure.getInt(requireContext().contentResolver, Settings.Secure.LOCATION_MODE) == 0)
            MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.geolocation_need)
                    .setPositiveButton(R.string.got_it, null)
                    .setCancelable(false)
                    .create().show()
    }

    private fun stopScanService() = scanConnection.stopScanService()

    private fun sendScanPeriod() {
        /*val selected = binding.bottomToolbar.spinnerPeriod.selectedItemPosition
        val period = resources.getIntArray(R.array.period_arr_int)[selected]
        scanConnection.sendScanPeriod(period)*/
    }

    private fun FragmentMainBinding.updateState(message: Message) {
        report("-> ${message.run { Event.entries[what] }}")
        if (view == null) return

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
                        Toast.makeText(
                            activity,
                            if (success) R.string.success else R.string.failure,
                            Toast.LENGTH_SHORT
                        ).show()
                    }.create().show()
        } else
            Toast.makeText(activity, R.string.failure, Toast.LENGTH_SHORT).show()
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

    private fun FragmentMainBinding.onLayoutChanged(orientation: Orientation) {
        val vertical = orientation.vertical
        root.removeAllViews()
        if (orientation is Orientation.Start) {
            root.addView(bottomToolbar.root)
            root.addView(container)
        } else {
            root.addView(container)
            root.addView(bottomToolbar.root)
        }
        bottomToolbar.root.updateLayoutParams<FrameLayout.LayoutParams> {
            gravity = when (orientation) {
                is Orientation.Start -> Gravity.START or Gravity.CENTER_VERTICAL
                is Orientation.End -> Gravity.END or Gravity.CENTER_VERTICAL
                is Orientation.Bottom -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            bottomMargin = if (orientation.vertical) resources.getDimensionPixelSize(R.dimen.padding_common) else 0
        }
        bottomToolbar.filters.root.let { filters ->
            (filters.parent as ViewGroup).removeView(filters)
            if (orientation == Orientation.Bottom) {
                bottomToolbar.horizontalFilters.addView(filters)
                bottomToolbar.horizontalFilters.isVisible = bottomToolbar.verticalFilters.isVisible
                bottomToolbar.verticalFilters.isVisible = false
            } else {
                bottomToolbar.verticalFilters.addView(filters)
                bottomToolbar.verticalFilters.isVisible = bottomToolbar.horizontalFilters.isVisible
                bottomToolbar.horizontalFilters.isVisible = false
            }
            filters.orientation = if (vertical) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
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
        adapter.notifyDataSetChanged()
    }
}
