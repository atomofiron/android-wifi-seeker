package ru.raslav.wirelessscan.utils

enum class Orientation(val vertical: Boolean = false) {
    Start,
    Bottom(vertical = true),
    End,
}
