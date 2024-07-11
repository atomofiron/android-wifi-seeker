package ru.raslav.wirelessscan

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.preference.PreferenceManager


fun Context.sp(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

fun Context.shortToast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

fun Context.longToast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_LONG).show()

fun Context.granted(permission: String) = checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

fun <T> unsafeLazy(provider: () -> T) = lazy(LazyThreadSafetyMode.NONE, provider)

fun Boolean.toInt(): Int = if (this) 1 else 0

fun Int.toBoolean(): Boolean = this != 0
