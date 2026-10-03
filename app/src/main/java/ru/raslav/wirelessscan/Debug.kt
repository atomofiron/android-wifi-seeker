package ru.raslav.wirelessscan

import android.util.Log

fun Any.dlog(s: String) {
    if (BuildConfig.DEBUG) Log.d("wirelessscan", "[${this::class.simpleName}] $s")
}

fun Any.elog(s: String) {
    if (BuildConfig.DEBUG) Log.e("wirelessscan", "[${this::class.simpleName}] $s")
}
