package io.atomofiron.wirelessscan

import android.app.Application
import android.content.Context
import android.content.Intent
import io.atomofiron.wirelessscan.connection.Connection

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        Connection().bindService(baseContext)
    }
}