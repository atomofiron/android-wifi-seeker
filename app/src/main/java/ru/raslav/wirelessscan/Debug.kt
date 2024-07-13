package ru.raslav.wirelessscan

import android.util.Log

fun Any.report(s: String) {
    if (BuildConfig.DEBUG) Log.e("wifi-seeker", "[${this::class.simpleName}] $s")
}
