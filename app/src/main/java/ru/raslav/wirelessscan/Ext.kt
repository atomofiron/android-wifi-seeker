package ru.raslav.wirelessscan

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.util.LayoutDirection
import android.view.View
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceManager
import com.google.android.material.color.MaterialColors
import ru.raslav.wirelessscan.utils.Point


fun Context.sp(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

fun Context.shortToast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

fun Context.longToast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_LONG).show()

fun Context.granted(permission: String) = checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

fun <T> unsafeLazy(provider: () -> T) = lazy(LazyThreadSafetyMode.NONE, provider)

fun Boolean.toInt(): Int = if (this) 1 else 0

fun Int.toBoolean(): Boolean = this != 0

fun Configuration.isWide() = screenWidthDp > 640

fun Context.openPermissionSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    intent.setData(Uri.fromParts("package", packageName, null))
    startActivity(intent)
    shortToast(R.string.get_perm_by_settings)
}

fun View.isRtl() = layoutDirection == LayoutDirection.RTL

fun MutableList<Point>.clearOutOfRange() {
    val it = listIterator()
    while (it.hasNext()) {
        if (it.next().outOfRange) {
            it.remove()
        }
    }
}

fun Int.half() = this / 2

fun Int.sqr() = this * this

fun Context.colorAttr(@AttrRes attr: Int) = MaterialColors.getColor(this, attr, Color.MAGENTA)

fun Float.toIntAlpha(): Int = (this * 255).toInt().coerceIn(0, 255)

infix fun Int.withAlpha(alpha: Float): Int = this withAlpha alpha.toIntAlpha()

infix fun Int.withAlpha(alpha: Int): Int = ColorUtils.setAlphaComponent(this, alpha)

