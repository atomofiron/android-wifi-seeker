package ru.raslav.wirelessscan.data

enum class Feature(val label: String) {
    No("no"),
    Supported("yes"),
    Required("req"),
    ;
    companion object {
        fun supported(supported: Boolean) = if (supported) Supported else No
    }
}