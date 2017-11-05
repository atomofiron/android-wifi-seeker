package ru.raslav.wirelessscan

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import ru.raslav.wirelessscan.BuildConfig

class I {

    companion object {
        var WIDE_MODE = false
        val SNAPSHOT_FORMAT = ".xml"

        val PREF_DEFAULT_DELAY = "default_delay"
        val PREF_SHOW_DESCRIPTION = "show_description"
        val PREF_WORK_IN_BG = "work_in_bg"
        val PREF_DETECT_ATTACKS = "detect_attacks"
        val PREF_ATTACK_SCREEN = "attack_screen"
        val PREF_SMART_DETECTION = "smart_detection"
        val PREF_EXTRAS = "extras"
        val PREF_AUTO_OFF_WIFI = "auto_off_wifi"
        val PREF_NO_SCAN_IN_BG = "no_scan_in_bg"

        fun log(log: String) = if (BuildConfig.DEBUG) Log.e("atomofiron", log) else Log.d("atomofiron", log)

        fun sp(context: Context): SharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context)

        fun shortToast(context: Context, resId: Int) =
                Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()

        fun longToast(context: Context, resId: Int) =
                Toast.makeText(context, resId, Toast.LENGTH_LONG).show()

        fun granted(context: Context, per: String): Boolean =
                context.checkCallingOrSelfPermission(per) == PackageManager.PERMISSION_GRANTED
    }
}

fun Boolean.toInt(): Int = if (this) 1 else 0
fun Int.toBoolean(): Boolean = this != 0