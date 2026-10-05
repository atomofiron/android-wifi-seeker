package ru.raslav.wirelessscan.data

data class CurrentConnection(
    val bssid: String,
    val address: String,
    val gateway: String,
) {
    constructor(bssid: String) : this(bssid, "", "")
}