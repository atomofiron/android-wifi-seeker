package ru.raslav.wirelessscan

import android.annotation.SuppressLint
import android.app.*
import android.net.wifi.WifiManager
import android.os.*
import android.app.PendingIntent
import android.content.*
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import ru.raslav.wirelessscan.connection.Connection.Event
import ru.raslav.wirelessscan.utils.OuiManager
import ru.raslav.wirelessscan.utils.Point

private const val ONLY_APP_IS_BOUND = 1

@Suppress("DEPRECATION") // I don't care
class ScanService : IntentService("ScanService") {
    companion object {
        private const val ACTION_PAUSE = "ACTION_PAUSE"
        private const val ACTION_RESUME = "ACTION_RESUME"

        private const val SECOND = 1000L
        private const val SCAN_DELAY_OFFSET = 2
        private const val SCAN_DELAY = SECOND * SCAN_DELAY_OFFSET
        private const val WIFI_WAITING_PERIOD = 300L

        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL = "channel_wtf"

        private var boundCount = 0
        fun connected() = boundCount++
        fun disconnected() = boundCount--
    }
    private val mainPendingIntent: PendingIntent by unsafeLazy {
        PendingIntent.getActivity(
            this,
            code++,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
    private lateinit var receiver: BroadcastReceiver
    private val wifiManager by unsafeLazy { getSystemService(Context.WIFI_SERVICE) as WifiManager }
    @SuppressLint("HandlerLeak")
    private val commandMessenger: Messenger = Messenger(object : Handler() {
        override fun handleMessage(msg: Message) = this@ScanService.handleMessage(msg)
    })
    private val notificationManager by unsafeLazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val sp by unsafeLazy { sp() }
    private var resultMessenger: Messenger? = null
    private val points = mutableListOf<Point>()
    private var period = 10
    private var process = false
    private var code = 1

    override fun onCreate() {
        report("ScanService: onCreate()")
        super.onCreate()

        if (SDK_INT >= Build.VERSION_CODES.O)
            notificationManager.createNotificationChannel(NotificationChannel(
                    NOTIFICATION_CHANNEL,
                    "channelName",
                    NotificationManager.IMPORTANCE_LOW
            ))
    }

    override fun onDestroy() {
        super.onDestroy()
        report("ScanService: onDestroy()")
        unregisterReceiver(receiver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
            if (isNotificationAction(intent) || process)
                START_NOT_STICKY
            else
                super.onStartCommand(intent, flags, startId)

    override fun onHandleIntent(intent: Intent?) {
        report("ScanService: onHandleIntent()")
        showNotification(true)

        // wait for the connection to the service to be established
        Thread.sleep(100)
        sendStarted()
        process = true
        while (process) scan()
    }

    override fun onBind(intent: Intent?): IBinder = commandMessenger.binder

    private fun isNotificationAction(intent: Intent?): Boolean {
        when (intent?.action) {
            ACTION_PAUSE -> stop() // немного не соответствует, но так надо, потому что сервис не знает что такое пауза и как продолжить
            ACTION_RESUME -> startService(Intent(applicationContext, ScanService::class.java))
            else -> return false
        }
        return true
    }

    private fun scan() {
        report("scan...")

        if (!waitForWifi())
            return

        showNotification(true)
        sendStartScan()
        wifiManager.startScan()
        Thread.sleep(SCAN_DELAY)

        if (waitForWifi()) {
            updatePoints()
            sendResults()
        }

        var i = SCAN_DELAY_OFFSET
        while ((i++ < period || scanningIsNotRequired()) && process)
            Thread.sleep(SECOND)
    }

    private fun scanningIsNotRequired(): Boolean = boundCount <= ONLY_APP_IS_BOUND

    /** @return process */
    private fun waitForWifi(): Boolean {
        while (!wifiManager.isWifiEnabled || scanningIsNotRequired()) {
            Thread.sleep(WIFI_WAITING_PERIOD)
            if (!process)
                return false
        }
        return process
    }

    private fun stop() {
        process = false
        sendStopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        showNotification(false)
    }

    @SuppressLint("MissingPermission") // ask permission before, on button click
    private fun updatePoints() {
        val currentPoints = wifiManager.scanResults.map { Point(it) }

        currentPoints.forEach { new ->
            points.find { it.bssid == new.bssid }
                ?.let {
                    new.hex = it.hex
                    new.manufacturer = it.manufacturer
                    new.manufacturerDesc = it.manufacturerDesc
                }
                ?: OuiManager.find(new.bssid).let {
                    new.hex = it.digits
                    new.manufacturer = it.label
                    new.manufacturerDesc = it.description
                }
        }

        points.removeAll(currentPoints)
        points.forEach { it.level = Point.MIN_LEVEL }

        points.addAll(currentPoints)
        points.sortWith { o1, o2 -> o2.level - o1.level }
    }

    private fun newMessage(what: Int): Message {
        val message = Message()
        message.what = what
        return message
    }

    private fun sendStartScan() = resultMessenger?.send(newMessage(Event.START_SCAN.ordinal))

    private fun sendStarted() = resultMessenger?.send(newMessage(Event.STARTED.ordinal))

    private fun sendStopped() = resultMessenger?.send(newMessage(Event.STOPPED.ordinal))

    private fun sendResults() {
        val message = newMessage(Event.RESULTS.ordinal)
        message.arg1 = process.toInt()
        message.obj = points
        resultMessenger?.send(message)
    }

    fun handleMessage(message: Message) {
        report("<- ${message.run { Event.entries[what] }}")
        resultMessenger = message.replyTo ?: resultMessenger

        when (message.what) {
            Event.GET.ordinal -> sendResults()
            Event.CLEAR.ordinal-> points.clear()
            Event.STOP.ordinal -> stop()
            Event.PERIOD.ordinal -> period = message.arg1
        }
    }

    /* I don't know how it should work, and looks like it is not so needed
    private fun detectAttacksIfNeeded() {
        if (!sp.getBoolean(Const.PREF_DETECT_ATTACKS, false))
            return
        private val trustedPoints = mutableListOf<Point>()
        val bssid = wifiManager.connectionInfo.bssid ?: ""
        var essid = wifiManager.connectionInfo.ssid
        val hidden = wifiManager.connectionInfo.hiddenSSID
        essid = essid.substring(1, essid.length - 1) // necessary

        val extras = sp.getString(Const.PREF_EXTRAS, "")!!.split("\n")
        val current = points.find { it.compare(bssid, essid, hidden) }
        if (current != null && !extras.contains(current.essid)) {
            val smart = sp.getBoolean(Const.PREF_SMART_DETECTION, false)

            when {
                !sp.getBoolean("Const.PREF_AUTO_OFF_WIFI", false) -> Unit
                trustedPoints.contains(current) -> Unit
                trustedPoints.any { it.isSimilar(current, smart) } -> trustedPoints.add(current)
                else -> {
                    wifiManager.isWifiEnabled = false
                    request(current)
                }
            }
            points.filter {
                it.level > Point.MIN_LEVEL
                        && !it.isSimilar(current, smart)
                        && !trustedPoints.contains(it)
            }.forEach { warning(it) }
        }
    }*/

    private fun showNotification(foreground: Boolean) {
        val co = applicationContext
        val builder = NotificationCompat.Builder(co, NOTIFICATION_CHANNEL)
        builder.setContentText(getString(R.string.touch_to_look))
                .setContentIntent(mainPendingIntent)
                .setSmallIcon(R.drawable.ws)
                .setContentTitle(getString(if (foreground) R.string.scanning else R.string.scanning_was_paused))

        if (foreground || sp.getBoolean(Const.PREF_WORK_IN_BG, false)) builder.addAction(
            if (foreground) R.drawable.ic_pause else R.drawable.ic_resume,
            getString(if (foreground) R.string.pause else R.string.resume),
            PendingIntent.getService(
                co, code++,
                Intent(co, ScanService::class.java).setAction(if (foreground) ACTION_PAUSE else ACTION_RESUME),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        )
        val notification = builder.build()
        if (foreground)
            ServiceCompat.startForeground(this, FOREGROUND_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else
            notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
    }

    /*private fun warning(point: Point) {
        val co = applicationContext
        val id = point.bssid.hashCode()

        val builder = NotificationCompat.Builder(co, NOTIFICATION_CHANNEL)
		builder.setTicker(getString(R.string.clone_detected))
                .setContentTitle(getString(R.string.clone_detected))
                .setContentText("${point.manufacturer} - ${point.bssid}")
                .setContentIntent(mainPendingIntent)
                .setSmallIcon(R.drawable.ws_yellow)

        val notification = builder.addAction(
            R.drawable.ic_check,
            getString(R.string.allow_point),
            PendingIntent.getService(co, code++,
                Intent(co, ScanService::class.java)
                    //.setAction(ACTION_ALLOW)
                    .putExtra(EXTRA_ID, id)
                    .putExtra(EXTRA_POINT, point),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        ).build()

        notificationManager.notify(id, notification)
    }

    private fun request(point: Point) {
        val co = applicationContext
        val id = point.bssid.hashCode() + 1

        val builder = NotificationCompat.Builder(co, NOTIFICATION_CHANNEL)
		builder.setTicker(getString(R.string.clone_detected))
                .setContentTitle(getString(R.string.wifi_was_disabled))
                .setContentText("${point.manufacturer} - ${point.bssid}")
                .setContentIntent(mainPendingIntent)
                .setSmallIcon(R.drawable.ws_red)

        val notification = builder
                .addAction(
                        R.drawable.ic_check,
                        getString(R.string.allow_point),
                        PendingIntent.getService(co, code++,
                                Intent(co, ScanService::class.java)
                                        //.setAction(ACTION_ALLOW)
                                        .putExtra(EXTRA_ID, id)
                                        .putExtra(EXTRA_POINT, point),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                ).addAction(
                R.drawable.ic_wifi,
                        getString(R.string.turn_wifi_on),
                        PendingIntent.getService(co, code++,
                                Intent(co, ScanService::class.java)
                                        //.setAction(ACTION_TURN_WIFI_ON)
                                        .putExtra(EXTRA_ID, id),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                ).build()

        notificationManager.notify(id, notification)
    }*/
}