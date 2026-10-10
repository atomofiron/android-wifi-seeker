package ru.raslav.wirelessscan

import android.annotation.SuppressLint
import android.app.IntentService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.net.wifi.WifiManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.raslav.wirelessscan.Const.DEFAULT_DURATION
import ru.raslav.wirelessscan.Const.DEFAULT_PERIOD
import ru.raslav.wirelessscan.Const.PREF_SCAN_DURATION
import ru.raslav.wirelessscan.connection.Connection.Event
import ru.raslav.wirelessscan.data.Point
import ru.raslav.wirelessscan.data.Point.Companion.toPoint
import ru.raslav.wirelessscan.utils.MutexLocker
import ru.raslav.wirelessscan.utils.OuiManager.Companion.oui
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.milliseconds

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION") // I don't care
class ScanService : IntentService("ScanService") {
    companion object {
        private const val ACTION_PAUSE = "ACTION_PAUSE"
        private const val ACTION_RESUME = "ACTION_RESUME"

        private const val SECOND = 1000L
        private const val WIFI_WAITING_PERIOD = 300L

        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "channel_id"

        private const val ACTION_CODE_SHOW = 2
        private const val ACTION_CODE_PAUSE = 3
        private const val ACTION_CODE_RESUME = 4

        private var boundCount = 0
        fun connected() = boundCount++
        fun disconnected() = boundCount--
    }
    private val mainPendingIntent: PendingIntent by unsafeLazy {
        PendingIntent.getActivity(
            this,
            ACTION_CODE_SHOW,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private val commandMessenger: Messenger = Messenger(MessageHandler(this))
    private var resultMessenger = WeakReference<Messenger>(null)

    private val wifiManager by unsafeLazy { getSystemService(WIFI_SERVICE) as WifiManager }
    private val notificationManager by unsafeLazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    private val durations by unsafeLazy { resources.getIntArray(R.array.duration_arr_int) }
    private val sp by unsafeLazy { sp() }
    private val points = MutexLocker(mutableListOf<Point>())
    private var period = DEFAULT_PERIOD
    private var process = false
    private var scanned = false
    private var job: Job = SupervisorJob()
    private val scope = CoroutineScope(job)

    override fun onCreate() {
        super.onCreate()

        if (SDK_INT >= O) {
            val name = getString(R.string.channel_name)
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = when {
        asNotificationAction(intent) || process -> START_NOT_STICKY
        else -> super.onStartCommand(intent, flags, startId)
    }

    override fun onHandleIntent(intent: Intent?) {
        dlog("ScanService: onHandleIntent()")
        showNotification(true)

        process = true
        sendStarted()
        try {
            runBlocking(job) {
                delay(100.milliseconds)
                while (process) scan()
            }
        } catch (e: CancellationException) {
            // cancelled with the service: nothing to unwind here
        } catch (e: Exception) {
            elog(e.toString())
        } finally {
            process = false
        }
    }

    override fun onBind(intent: Intent?): IBinder = commandMessenger.binder

    private fun asNotificationAction(intent: Intent?): Boolean {
        when (intent?.action) {
            ACTION_PAUSE -> stop()
            ACTION_RESUME -> startService(Intent(applicationContext, ScanService::class.java))
            else -> return false
        }
        return true
    }

    private suspend fun scan() {
        dlog("scan...")

        if (!waitForWifi()) {
            return
        }
        showNotification(true)
        sendStartScan()
        wifiManager.startScan()
        var seconds = 0
        while (process) {
            delay(SECOND.milliseconds)
            if (++seconds >= getDuration()) {
                break
            }
        }
        if (waitForWifi()) {
            scanned = true
            updatePoints()
            sendResults()
        }
        while (process && (seconds++ < period || !needScan())) {
            delay(SECOND.milliseconds)
        }
    }

    private fun getDuration() = sp.getString(PREF_SCAN_DURATION, null)
        ?.toIntOrNull()
        ?.let { durations.getOrNull(it) }
        ?: DEFAULT_DURATION

    private fun needScan(): Boolean = boundCount > 0

    private fun waitForWifi(): Boolean {
        while (!wifiManager.isWifiEnabled || !needScan()) {
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
    private suspend fun updatePoints() {
        val results = wifiManager.scanResults
            .map { it.toPoint() }
            .distinctBy { it.keyHash() }

        points {
            for (r in results) {
               val index = indexOfFirst { r.theSame(it) }
                if (index < 0) {
                    val info = oui { find(r.bssid) } ?: run {
                        add(0, r)
                        continue
                    }
                    val new = r.copy(
                        bssidGroup = info.bssidGroup,
                        manufacturer = info.label,
                        manufacturerDesc = info.description,
                    )
                    add(0, new)
                    continue
                }
                val old = get(index)
                val new = r.copy(
                    bssidGroup = old.bssidGroup,
                    manufacturer = old.manufacturer,
                    manufacturerDesc = old.manufacturerDesc,
                )
                removeAt(index)
                add(0, new)
            }
            for (i in results.size..<size) {
                val copy = get(i).copy(level = Point.range.first)
                set(i, copy)
            }
            sortBy { -it.level }
        }
    }

    private fun newMessage(what: Int): Message {
        val message = Message()
        message.what = what
        return message
    }

    private fun sendStartScan() = resultMessenger.get()?.send(newMessage(Event.START_SCAN.ordinal))

    private fun sendStarted() = resultMessenger.get()?.send(newMessage(Event.STARTED.ordinal))

    private fun sendStopped() = resultMessenger.get()?.send(newMessage(Event.STOPPED.ordinal))

    private suspend fun sendResults() {
        val message = newMessage(Event.RESULTS.ordinal)
        message.arg1 = process.toInt()
        message.obj = points { toMutableList() }
        resultMessenger.get()?.send(message)
    }

    fun handleMessage(message: Message) {
        val command = Event.entries[message.what]
        dlog("<- $command")
        resultMessenger = WeakReference(message.replyTo ?: resultMessenger.get())
        val arg1 = message.arg1
        when (command) {
            Event.STOP -> stop()
            Event.PERIOD -> period = arg1
            else -> scope.launch {
                when (command) {
                    Event.GET -> if (scanned) sendResults()
                    Event.CLEAR -> points { clear() }
                    Event.CLEAR_OUT_OF_RANGE -> points { clearOutOfRange() }
                    else -> Unit
                }
            }
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
        val builder = NotificationCompat.Builder(co, NOTIFICATION_CHANNEL_ID)
        builder.setContentText(getString(R.string.touch_to_look))
            .setContentIntent(mainPendingIntent)
            .setSmallIcon(R.drawable.ws)
            .setContentTitle(getString(if (foreground) R.string.scanning else R.string.scanning_was_paused))

        if (foreground || sp.getBoolean(Const.PREF_WORK_IN_BG, false)) builder.addAction(
            if (foreground) R.drawable.ic_pause else R.drawable.ic_resume,
            getString(if (foreground) R.string.pause else R.string.resume),
            PendingIntent.getService(
                co, if (foreground) ACTION_CODE_PAUSE else ACTION_CODE_RESUME,
                Intent(co, ScanService::class.java).setAction(if (foreground) ACTION_PAUSE else ACTION_RESUME),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        )
        val notification = builder.build()
        when {
            foreground -> ServiceCompat.startForeground(this, FOREGROUND_NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else -> notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private class MessageHandler(service: ScanService) : Handler() {

        private val service = WeakReference(service)

        override fun handleMessage(msg: Message) {
            service.get()?.handleMessage(msg)
        }
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