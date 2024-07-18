package ru.raslav.wirelessscan.connection

import android.os.Handler

class ScanConnection(
    handler: Handler,
    onServiceConnectedListener: () -> Unit,
) : Connection(onServiceConnectedListener) {

    init {
        setDuplex(handler)
    }

    fun sendGetRequest() = send(newMessage(Event.GET.ordinal))

    fun sendScanPeriod(sec: Int) {
        val message = newMessage(Event.PERIOD.ordinal)
        message.arg1 = sec
        send(message)
    }

    fun clearPointsList() = send(newMessage(Event.CLEAR.ordinal))

    fun stopScanService() = send(newMessage(Event.STOP.ordinal))
}