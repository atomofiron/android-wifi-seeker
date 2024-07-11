package ru.raslav.wirelessscan

import android.util.Log

fun report(s: String) {
    if (BuildConfig.DEBUG) Log.e("wifi-seeker", s)
}
