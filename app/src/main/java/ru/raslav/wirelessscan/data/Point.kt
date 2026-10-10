package ru.raslav.wirelessscan.data

import android.net.wifi.ScanResult
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import ru.raslav.wirelessscan.isReadable
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.serialization.Transient
import nl.adaptivity.xmlutil.serialization.XmlDefault

@Serializable
@XmlSerialName("point", "", "")
data class Point(
    val level: Int,
    val frequency: Int,
    val capabilities: String,
    val essid: String,
    @XmlSerialName("essid_hex", "", "")
    val essidHex: String = "",
    val bssid: String,
    @XmlSerialName("channel", "", "")
    val ch: Int,
    val manufacturer: String = "",
    @XmlDefault("")
    @XmlSerialName("manufacturer_description", "", "")
    val manufacturerDesc: String = "",

    @Transient
    val wps: Boolean = capabilities.contains("WPS"),
    @Transient
    val cip: String = if (capabilities.contains("CCMP")) "CCMP" else "",
    @Transient
    val enc: String = run {
        when {
            capabilities.contains("SAE-") -> if (capabilities.contains("WPA2")) "WPA2/3" else "WPA3"
            capabilities.contains("WPA") -> if (capabilities.contains("WPA2")) "WPA2" else "WPA"
            capabilities.contains("WEP") -> "WEP"
            cip.isNotEmpty() -> "?"
            else -> "OPN"
        }
    },

    @Transient
    val bssidGroup: String = "",
) {

    val outOfRange get() = level <= range.first

    fun is5Ghz(): Boolean = frequency >= 4915

    fun isHidden() = essid.isEmpty() || essid.length == 1 && essid[0].code == 0

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
                ch = getChanel(frequency),
            )
        }

        /**
         * Maps a center frequency in MHz to its Wi-Fi channel number, 0 when no channel uses it.
         * Follows [android.net.wifi.ScanResult.convertFrequencyMhzToChannelIfSupported] for the bands it
         * knows, plus the 3.6 GHz and the 4.9 GHz (Japan) bands the framework leaves unmapped:
         * 2.4 GHz 1..13 (2412..2472) and 14 (2484); 3.6 GHz 131..139; 4.9 GHz 182..196;
         * 5 GHz 7..181; 6 GHz 1..233 and the operating class 136 channel 2 (5935); 60 GHz 1..6.
         */
        private fun getChanel(frequency: Int): Int = when (frequency) {
            2484 -> 14
            in 2412..2472 -> (frequency - 2412) / 5 + 1
            in 3655..3695 -> 131 + (frequency - 3655) / 5
            in 4910..4980 -> (frequency - 4000) / 5
            in 5035..5905 -> (frequency - 5000) / 5
            5935 -> 2
            in 5955..7115 -> (frequency - 5955) / 5 + 1
            in 58320..69120 -> (frequency - 58320) / 2160 + 1
            else -> 0
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
