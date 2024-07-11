package ru.raslav.wirelessscan.utils

data class Manufacturer(
    val label: String,
    val description: String,
) {
    companion object {
        val Unknown = Manufacturer("<unknown>", "")
        val NoBase = Manufacturer("<no OUI base>", "")
    }
}