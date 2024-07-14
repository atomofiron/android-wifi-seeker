package ru.raslav.wirelessscan

import android.Manifest
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q

object Const {
    const val SNAPSHOT_FORMAT = ".xml"

    const val PREF_MAIL = "mail_to_dev"
    const val PREF_OUI_SOURCE = "oui_source"
    const val PREF_SOURCE_CODE = "source_code"
    const val PREF_DEFAULT_DELAY = "default_delay"
    const val PREF_SHOW_DESCRIPTION = "show_description"
    const val PREF_WORK_IN_BG = "work_in_bg"
    const val PREF_DETECT_ATTACKS = "detect_attacks"
    const val PREF_ATTACK_SCREEN = "attack_screen"
    const val PREF_SMART_DETECTION = "smart_detection"
    const val PREF_EXTRAS = "extras"
    const val PREF_AUTO_OFF_WIFI = "auto_off_wifi"
    const val PREF_NO_SCAN_IN_BG = "no_scan_in_bg"

    const val ALPHA_ZERO = 0f
    const val ALPHA_FULL = 1f

    const val LOCATION_REQUEST_CODE = 7
    const val NOTIFICATIONS_REQUEST_CODE = 8
    val LOCATION_PERMISSION = when {
        SDK_INT >= Q -> Manifest.permission.ACCESS_FINE_LOCATION
        else -> Manifest.permission.ACCESS_COARSE_LOCATION
    }
}
