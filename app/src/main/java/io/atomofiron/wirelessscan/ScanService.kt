package io.atomofiron.wirelessscan

import android.app.IntentService
import android.app.Notification
import android.net.wifi.WifiManager
import android.os.*
import io.atomofiron.wirelessscan.room.Point
import io.atomofiron.wirelessscan.connection.Connection.WHAT.*
import android.app.PendingIntent
import android.content.*
import android.os.Build
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import io.atomofiron.wirelessscan.utils.OuiManager
import java.util.*
import kotlin.collections.ArrayList

class ScanService : IntentService("ScanService") {
    companion object {
        private val ACTION_STOP = "ACTION_STOP"

        private val SECOND = 1000L
        private val SCAN_DELAY_OFFSET = 2
        private val SCAN_DELAY = SECOND * SCAN_DELAY_OFFSET
        private val WIFI_WAITING_PERIOD = 300L

        private val FOREGROUND_NOTIFICATION_ID = 1
        private val WARNING_NOTIFICATION_ID = 2
        private val REQUEST_NOTIFICATION_ID = 3

        private var boundCount = 0
        fun connected() = boundCount++
        fun disconnected() = boundCount--
    }
    private lateinit var mainPendingIntent: PendingIntent
    private lateinit var receiver: BroadcastReceiver
    private lateinit var wifiManager: WifiManager
    private lateinit var commandMessenger: Messenger
    private lateinit var sp: SharedPreferences
    private lateinit var ouiManager: OuiManager
    private var resultMessenger: Messenger? = null
    private val points = ArrayList<Point>()
    private var trustedPoints: ArrayList<Point> = ArrayList()
    private var delay = 10
    private var process = false
    private var code = 1

    override fun onCreate() {
        I.log("ScanService: onCreate()")
        super.onCreate()

        mainPendingIntent = PendingIntent.getActivity(baseContext, code++, Intent(baseContext, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT)
        receiver = Receiver()
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        filter.addAction(ACTION_STOP)
        registerReceiver(receiver, filter)

        wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        commandMessenger = Messenger(object : Handler() {
            override fun handleMessage(msg: Message?) {
                super.handleMessage(msg)
                this@ScanService.handleMessage(msg)
            }
        })
        sp = I.sp(baseContext)
        ouiManager = OuiManager(baseContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        I.log("ScanService: onDestroy()")
        unregisterReceiver(receiver)
        ouiManager.close()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
            if (process)
                START_NOT_STICKY
            else
                super.onStartCommand(intent, flags, startId)

    override fun onHandleIntent(intent: Intent) {
        I.log("ScanService: onHandleIntent()")
        startForeground()

        process = true
        while (process)
            scan()
    }

    override fun onBind(intent: Intent?): IBinder = commandMessenger.binder

    private fun scan() {
        I.log("scan()")

        if (!waitForWifi())
            return

        sendStartScan()
        wifiManager.startScan()
        Thread.sleep(SCAN_DELAY)

        if (waitForWifi()) {
            updatePoints()
            sendResults()
            detectAttacksIfNeeded()
        }

        var i = SCAN_DELAY_OFFSET
        while ((i++ < delay || noScan()) && process)
            Thread.sleep(SECOND)
    }

    private fun noScan(): Boolean =
            sp.getBoolean(I.PREF_AUTO_OFF_WIFI, false) && sp.getBoolean(I.PREF_NO_SCAN_IN_BG, false) && boundCount <= 1

    /** @return process */
    private fun waitForWifi(): Boolean {
        while (!wifiManager.isWifiEnabled || noScan()) {
            Thread.sleep(WIFI_WAITING_PERIOD)

            if (!process)
                return false
        }
        return process
    }

    private fun stop() {
        process = false
        sendStopped()
        stopForeground(true)
    }

    private fun updatePoints() {
        val currentPoints = Point.parseScanResults(wifiManager.scanResults)

        currentPoints.forEach { it.manufacturer = ouiManager.find(it.bssid) }

        points.removeAll(currentPoints)
        points.forEach { it -> it.level = Point.MIN_LEVEL }

        points.addAll(currentPoints)
        points.sortWith(Comparator { o1, o2 -> o2.level - o1.level })
    }

    private fun newMessage(what: Int): Message {
        val message = Message()
        message.what = what
        return message
    }

    private fun sendStartScan() = resultMessenger?.send(newMessage(START_SCAN.ordinal))

    private fun sendStopped() = resultMessenger?.send(newMessage(STOPPED.ordinal))

    private fun sendResults() {
        val message = newMessage(RESULTS.ordinal)
        message.arg1 = process.toInt()
        message.obj = points
        resultMessenger?.send(message)
    }

    fun handleMessage(msg: Message?) {
        I.log("CH: what: ${msg?.what ?: "null"}")
        if (msg != null) {
            //resultMessenger = msg.replyTo ?: resultMessenger
            if (msg.replyTo != null)
                resultMessenger = msg.replyTo

            when (msg.what) {
                GET.ordinal -> sendResults()
                CLEAR.ordinal-> points.clear()
                STOP.ordinal -> stop()
                DELAY.ordinal -> delay = msg.arg1
            }
        }
    }

    private fun detectAttacksIfNeeded() {
        val bssid = wifiManager.connectionInfo.bssid ?: ""
        val essid = wifiManager.connectionInfo.ssid ?: ""
        val hidden = wifiManager.connectionInfo.hiddenSSID

        val current = points.find { it.compare(bssid, essid, hidden) }
        if (sp.getBoolean(I.PREF_DETECT_ATTACKS, false) && current != null) {
            val smart = sp.getBoolean(I.PREF_SMART_DETECTION, false)

            if (sp.getBoolean(I.PREF_AUTO_OFF_WIFI, false) &&
                trustedPoints.find { !it.compare(current, smart) } != null) {
                wifiManager.isWifiEnabled = false
                // todo request notification
            } else {
                trustedPoints.add(current)
                points.filter { !it.compare(current, smart) }
                        .forEach { /* todo warning notification */ }
            }
        }
    }

    private fun startForeground() {
        val co = applicationContext
        val builder = Notification.Builder(co)
                .setContentTitle(getString(R.string.scanning))
                .setContentText(getString(R.string.touch_to_look))
                .setContentIntent(mainPendingIntent)
                .setSmallIcon(R.drawable.ws)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            builder.setLargeIcon(Icon.createWithResource(co, R.mipmap.ic_launcher))

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            builder.addAction(
                    R.drawable.ic_close,
                    getString(R.string.stop),
                    PendingIntent.getBroadcast(co, code++, Intent(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT)
            ).build()
        else
            builder.notification

        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }

    inner class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ConnectivityManager.CONNECTIVITY_ACTION -> detectAttacksIfNeeded()
                ACTION_STOP -> stop()
            }
        }
    }
}