package ru.raslav.wirelessscan.data;


data class PointFilter(
    val label: String,
    val include: Boolean = false,
    val exclude: Boolean = false,
) {
    val hidden get() = label == LabelHidden
    val capabilities get() = label != LabelHidden
    val isEmpty get() = include == exclude

    companion object {

        private const val LabelHidden = "HIDDEN"

        val labels = listOf("WPA", "PSK", "EAP", "CCMP", "TKIP", "WPS", "P2P", "WEP", LabelHidden)
        val defaults = labels.map { PointFilter(it) }
    }
}