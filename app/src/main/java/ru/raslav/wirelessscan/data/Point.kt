package ru.raslav.wirelessscan.data

import android.net.wifi.ScanResult
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import ru.raslav.wirelessscan.isReadable
import ru.raslav.wirelessscan.utils.Manufacturer
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

@Serializable
@XmlSerialName("point")
data class Point(
    @XmlSerialName("level")
    val rssi: Int,
    val frequency: Int,
    val capabilities: String,
    val essid: String,
    @XmlSerialName("essid_hex")
    val essidHex: String = "",
    val bssid: String,

    @Transient
    val wps: Boolean = capabilities.contains("WPS"),
    @Transient
    val pmf: Feature = when {
        capabilities.contains("MFPR") -> Feature.Required
        capabilities.contains("MFPC") -> Feature.Supported
        else -> Feature.No
    },
    @Transient
    val cip: String = when {
        capabilities.contains("-GCMP") -> if (capabilities.contains("+TKIP")) "GCMP+" else "GCMP"
        capabilities.contains("-CCMP") -> if (capabilities.contains("+TKIP")) "CCMP+" else "CCMP"
        capabilities.contains("-TKIP+") -> "TKIP+"
        capabilities.contains("-TKIP") -> "TKIP"
        capabilities.contains("WEP") -> "WEP"
        else -> ""
    },
    @Transient
    val enc: String = run {
        when {
            capabilities.contains("SAE-") -> if (capabilities.contains("WPA2")) "WPA2/3" else "WPA3"
            capabilities.contains("WPA2") -> "WPA2"
            capabilities.contains("WPA") -> "WPA"
            capabilities.contains("WEP") -> "WEP"
            cip.isEmpty() || capabilities.contains("OWE") -> "OPN"
            else -> UNKNOWN
        }
    },

    @Transient
    val manufacturer: Manufacturer? = null,
    @Transient
    private var fcb: FrequencyChannelBand = FrequencyChannelBand(frequency),
) {
    init {
        if (fcb.frequency != frequency) {
            fcb = FrequencyChannelBand(frequency)
        }
    }

    @XmlElement
    @XmlSerialName("channel")
    val channelNumber: Int = fcb.channel

    val channel: String get() = when (channelNumber) {
        0 -> UNKNOWN
        else -> channelNumber.toString()
    }

    val band: Band? get() = fcb.band

    @XmlElement
    @XmlSerialName("manufacturer")
    val manufacturerLabel: String? = manufacturer?.label
    @XmlElement
    @XmlSerialName("manufacturer_name")
    val manufacturerName: String? = manufacturer?.name

    val bssidGroup: String? get() = manufacturer?.bssidGroup

    val outOfRange get() = rssi <= range.first

    fun isHidden() = essid.all { it.code == 0 }

    fun withCip(): Boolean = cip.isNotEmpty()

    fun theSame(other: Point?): Boolean = when {
        other == null -> false
        other.bssid != bssid -> false
        other.essidHex.isEmpty() && essidHex.isEmpty() -> other.essid == essid
        else -> other.essidHex == essidHex
    }

    fun related(other: Point?): Boolean = related(other?.bssid, other?.bssidGroup)

    fun related(bssid: String?, bssidGroup: String?): Boolean = when {
        bssidGroup != null && bssidGroup == this.bssidGroup -> return true
        bssid == null -> return false
        else -> for (i in 0..12) {
            if (this.bssid[i] != bssid[i]) {
                return false
            }
        }
    }.let { true }

    fun keyHash(): Long = when {
        essidHex.isEmpty() -> essid
        else -> essidHex
    }.hashCode().toLong().shl(32) + bssid.hashCode().toLong()

    /* starting Android 8 WifiManager.calculateSignalLevel(int, int) returns something looks wrong */
    fun calculateSignalLevel(numLevels: Int): Int = when {
        rssi <= MIN_RSSI -> 0
        rssi >= MAX_RSSI -> numLevels
        else -> {
            val inputRange = (MAX_RSSI - MIN_RSSI).toFloat()
            val outputRange = numLevels.toFloat()
            ((rssi - MIN_RSSI).toFloat() * outputRange / inputRange).toInt()
        }
    }

    companion object {

        const val UNKNOWN = "?"

        val range = -100..0

        private const val MIN_RSSI = -90
        private const val MAX_RSSI = -50

        fun ScanResult.toPoint(): Point {
            val essid = getSsid()
            return Point(
                rssi = level,
                frequency = frequency,
                capabilities = capabilities,
                essid = essid,
                essidHex = getSsidHexIfNeeded(essid),
                bssid = BSSID.uppercase(),
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun ScanResult.getSsid(): String = when {
    SDK_INT < TIRAMISU -> SSID
    else -> when (val bytes = wifiSsid?.bytes) {
        null -> ""
        else -> {
            val out = CharBuffer.allocate(32)
            Utf8decoder.decode(ByteBuffer.wrap(bytes), out, true)
            out.flip()
            out.toString()
        }
    }
}

private fun ScanResult.getSsidHexIfNeeded(ssid: String): String {
    return when {
        ssid.isNotEmpty() && ssid.all { it.isReadable() } -> return ""
        SDK_INT >= TIRAMISU -> wifiSsid?.bytes ?: ssid.toByteArray()
        else -> ssid.toByteArray()
    }.toHexString(SpacedHexFormat)
}

private val SpacedHexFormat = HexFormat { bytes.byteSeparator = " " }

private val Utf8decoder = StandardCharsets.UTF_8
    .newDecoder()
    .onMalformedInput(CodingErrorAction.REPLACE)
    .onUnmappableCharacter(CodingErrorAction.REPLACE)
