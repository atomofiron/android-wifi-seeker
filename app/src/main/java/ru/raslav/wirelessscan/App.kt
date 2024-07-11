package ru.raslav.wirelessscan

import android.app.Application
import ru.raslav.wirelessscan.connection.Connection

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        Connection().bindService(baseContext)
    }
}