package ru.raslav.wirelessscan

import android.app.Application
import ru.raslav.wirelessscan.connection.Connection
import ru.raslav.wirelessscan.utils.OuiManager

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        OuiManager.init(this)
        Connection().bindService(baseContext)
    }
}