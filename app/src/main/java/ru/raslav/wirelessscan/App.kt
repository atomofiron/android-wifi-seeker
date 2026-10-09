package ru.raslav.wirelessscan

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.PredictiveBackControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import ru.raslav.wirelessscan.connection.Connection
import ru.raslav.wirelessscan.utils.OuiManager

class App : Application() {
    companion object {
        val scope = CoroutineScope(SupervisorJob())
        @SuppressLint("StaticFieldLeak") // application is singleton
        lateinit var context: Context
    }

    @OptIn(PredictiveBackControl::class)
    override fun onCreate() {
        super.onCreate()

        context = this
        OuiManager.init(this)
        Connection(silent = true).bindService(baseContext)
        FragmentManager.enablePredictiveBack(true)
    }
}