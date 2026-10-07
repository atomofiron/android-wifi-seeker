package ru.raslav.wirelessscan.connection

import android.os.Handler

class ScanConnection(
    handler: Handler,
    onServiceConnected: () -> Unit,
) : Connection(onServiceConnected = onServiceConnected) {

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

    fun clearOutOfRangePoints() = send(newMessage(Event.CLEAR_OUT_OF_RANGE.ordinal))

    fun stopScanService() = send(newMessage(Event.STOP.ordinal))
}