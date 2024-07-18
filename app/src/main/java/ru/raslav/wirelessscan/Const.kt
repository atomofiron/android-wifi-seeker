package ru.raslav.wirelessscan

import android.Manifest
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q

object Const {
    const val SNAPSHOT_FORMAT = ".xml"

    const val PREF_MAIL = "mail_to_dev"
    const val PREF_OUI_SOURCE = "oui_source"
    const val PREF_PRIVACY_POLICY = "privacy_policy"
    const val PREF_SOURCE_CODE = "source_code"
    const val PREF_DEFAULT_PERIOD = "default_period"
    const val PREF_WORK_IN_BG = "work_in_bg"

    const val ALPHA_ZERO = 0f
    const val ALPHA_FULL = 1f

    const val LOCATION_REQUEST_CODE = 7
    const val BG_LOCATION_REQUEST_CODE = 8
    const val NOTIFICATIONS_REQUEST_CODE = 9
    val LOCATION_PERMISSION = when {
        SDK_INT >= Q -> Manifest.permission.ACCESS_FINE_LOCATION
        else -> Manifest.permission.ACCESS_COARSE_LOCATION
    }
}
