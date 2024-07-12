package ru.raslav.wirelessscan.connection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import ru.raslav.wirelessscan.ScanService
import ru.raslav.wirelessscan.report

open class Connection(
    private val onServiceConnectedListener: () -> Unit = {},
) : ServiceConnection {
    enum class Event { GET, CLEAR, STOP, START_SCAN, DELAY, RESULTS, STOPPED, STARTED }

    private var commandMessenger: Messenger? = null
    private var replyMessenger: Messenger? = null

    fun bindService(context: Context) =
        context.bindService(Intent(context, ScanService::class.java), this, Context.BIND_AUTO_CREATE)

    /* onServiceDisconnected() is only called in extreme situations (unbindService() is not) */
    fun unbindService(context: Context) {
        context.unbindService(this)
        commandMessenger = null
        ScanService.disconnected()
    }

    final override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        report("onServiceConnected()")
        commandMessenger = Messenger(service)
        onServiceConnectedListener()
        ScanService.connected()
    }

    final override fun onServiceDisconnected(name: ComponentName?) {}

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