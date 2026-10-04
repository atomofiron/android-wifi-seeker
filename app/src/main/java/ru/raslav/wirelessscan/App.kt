package ru.raslav.wirelessscan

import android.app.Application
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.PredictiveBackControl
import ru.raslav.wirelessscan.connection.Connection
import ru.raslav.wirelessscan.utils.OuiManager

class App : Application() {

    @OptIn(PredictiveBackControl::class)
    override fun onCreate() {
        super.onCreate()

        OuiManager.init(this)
        Connection().bindService(baseContext)
        FragmentManager.enablePredictiveBack(true)
    }
}