package ru.raslav.wirelessscan

import android.app.Application
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.PredictiveBackControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import ru.raslav.wirelessscan.connection.Connection
import ru.raslav.wirelessscan.utils.OuiManager

class App : Application() {
    companion object {
        val scope = CoroutineScope(Job())
    }

    @OptIn(PredictiveBackControl::class)
    override fun onCreate() {
        super.onCreate()

        OuiManager.init(this)
        Connection(silent = true).bindService(baseContext)
        FragmentManager.enablePredictiveBack(true)
    }
}