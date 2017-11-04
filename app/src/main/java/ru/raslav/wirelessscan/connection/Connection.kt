package ru.raslav.wirelessscan.connection

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import ru.raslav.wirelessscan.I
import ru.raslav.wirelessscan.ScanService

open class Connection(var onServiceConnectedListener: () -> Unit = {}) : ServiceConnection {
    enum class WHAT { GET, CLEAR, STOP, START_SCAN, DELAY, RESULTS, STOPPED, STARTED }

    private var commandMessenger: Messenger? = null
    private var replyMessenger: Messenger? = null

    fun bindService(context: Context) =
        context.bindService(Intent(context, ScanService::class.java), this, Context.BIND_AUTO_CREATE)

    /* onServiceDisconnected() is only called in extreme situations (unbindService() is not) */
    fun unbindService(activity: Activity) {
        activity.unbindService(this)
        commandMessenger = null
        ScanService.disconnected()
    }

    override final fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        I.log("onServiceConnected()")
        commandMessenger = Messenger(service)
        onServiceConnectedListener()
        ScanService.connected()
    }

    override final fun onServiceDisconnected(name: ComponentName?) {}

    protected fun setDuplex(handler: Handler) {
        replyMessenger = Messenger(handler)
    }

    protected fun newMessage(what: Int): Message {
        val message = Message()
        message.what = what
        message.replyTo = replyMessenger
        return message
    }

    protected fun send(message: Message) = commandMessenger?.send(message)
}