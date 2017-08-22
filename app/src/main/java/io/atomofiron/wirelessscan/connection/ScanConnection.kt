package io.atomofiron.wirelessscan.connection

import android.os.Handler

class ScanConnection(handler: Handler) : Connection() {

    init {
        setDuplex(handler)
    }

    fun sendGetRequest() = send(newMessage(WHAT.GET.ordinal))

    fun sendScanDelay(sec: Int) {
        val message = newMessage(WHAT.DELAY.ordinal)
        message.arg1 = sec
        send(message)
    }

    fun clearNodesList() = send(newMessage(WHAT.CLEAR.ordinal))

    fun stopScanService() = send(newMessage(WHAT.STOP.ordinal))
}