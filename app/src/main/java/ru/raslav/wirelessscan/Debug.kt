package ru.raslav.wirelessscan

import android.util.Log

fun Any.report(s: String) {
    if (BuildConfig.DEBUG) Log.e("wirelessscan", "[${this::class.simpleName}] $s")
}
