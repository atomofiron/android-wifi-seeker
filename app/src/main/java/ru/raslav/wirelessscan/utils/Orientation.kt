package ru.raslav.wirelessscan.utils

sealed class Orientation(
    val vertical: Boolean = false,
    val start: Boolean = false,
    val left: Boolean = false,
    val end: Boolean = false,
    val right: Boolean = false,
) {
    data class Start(val rtl: Boolean) : Orientation(start = true, left = !rtl, right = rtl)
    data object Bottom : Orientation(vertical = true)
    data class End(val rtl: Boolean) : Orientation(end = true, left = rtl, right = !rtl)
}
