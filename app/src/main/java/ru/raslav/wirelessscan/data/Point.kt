package ru.raslav.wirelessscan.data

import android.net.wifi.ScanResult
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import ru.raslav.wirelessscan.isReadable
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

@Serializable
@XmlSerialName("point")
data class Point(
    val level: Int,
    val frequency: Int,
    val capabilities: String,
    val essid: String,
    @XmlSerialName("essid_hex")
    val essidHex: String = "",
    val bssid: String,
    val manufacturer: String = "",
    @XmlSerialName("manufacturer_name", "", "")
    val manufacturerName: String = "",

    @Transient
    val wps: Boolean = capabilities.contains("WPS"),
    @Transient
    val cip: String = when {
        capabilities.contains("GCMP") -> "GCMP"
        capabilities.contains("CCMP") -> "CCMP"
        capabilities.contains("TKIP") -> "TKIP"
        capabilities.contains("WEP") -> "WEP"
        else -> ""
    }.let {
        when {
            capabilities.contains("+TKIP") -> "+TKIP"
            capabilities.contains("+CCMP") -> "+CCMP"
            else -> it
        }
    },
    @Transient
    val enc: String = run {
        when {
            capabilities.contains("SAE-") -> if (capabilities.contains("WPA2")) "WPA2/3" else "WPA3"
            capabilities.contains("WPA") -> if (capabilities.contains("WPA2")) "WPA2" else "WPA"
            capabilities.contains("WEP") -> "WEP"
            cip.isEmpty() || capabilities.contains("OWE") -> "OPN"
            else -> UNKNOWN
        }
    },

    @Transient
    val bssidGroup: String = "",
    @Transient
    private var fcb: FrequencyChannelBand = FrequencyChannelBand(frequency),
) {
    @XmlElement
    @XmlSerialName("channel")
    val channelNumber: Int = fcb.channel

    val channel: String get() = when (channelNumber) {
        0 -> UNKNOWN
        else -> channelNumber.toString()
    }

    val band: Band? get() = fcb.band

    val outOfRange get() = level <= range.first

    init {
        if (fcb.frequency != frequency) {
            fcb = FrequencyChannelBand(frequency)
        }
    }

    fun isHidden() = essid.all { it.code == 0 }

    fun withCip(): Boolean = cip.isNotEmpty()

    fun theSame(other: Point?): Boolean = when {
        other == null -> false
        other.bssid != bssid -> false
        other.essidHex.isEmpty() && essidHex.isEmpty() -> other.essid == essid
        else -> other.essidHex == essidHex
    }

    fun keyHash(): Long = when {
        essidHex.isEmpty() -> essid
        else -> essidHex
    }.hashCode().toLong().shl(32) + bssid.hashCode().toLong()

    companion object {

        const val UNKNOWN = "?"

        val range = -100..0

        fun ScanResult.toPoint(): Point {
            val essid = getSsid()
            return Point(
                level = level,
                frequency = frequency,
                capabilities = capabilities,
                essid = essid,
                essidHex = getSsidHexIfNeeded(essid),
                bssid = BSSID,
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
