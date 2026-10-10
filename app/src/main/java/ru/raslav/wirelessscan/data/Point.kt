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
    val bssidHex: String = "",
) {

    val outOfRange get() = level <= MIN_LEVEL

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

        const val MIN_LEVEL = -100 // WifiManager.MIN_LEVEL

        /*operator fun invoke(
            level: Int,
            frequency: Int,
            capabilities: String,
            essid: String,
            essidHex: String,
            bssid: String,
            ch: Int,
            bssidHex: String = "",
            manufacturer: String = "",
            manufacturerDesc: String = "",
        ): Point {
            val cip = if (capabilities.contains("CCMP")) "CCMP" else ""
            var enc = if (cip.isNotEmpty()) "?" else "OPN"
            if (capabilities.contains("SAE-")) {
                enc = if (capabilities.contains("WPA2")) "WPA2/3" else "WPA3"
            } else if (capabilities.contains("WPA")) {
                enc = if (capabilities.contains("WPA2")) "WPA2" else "WPA"
            } else if (capabilities.contains("WEP")) {
                enc = "WEP"
            }
            val wps = capabilities.contains("WPS")
            return Point(
                level = level,
                frequency = frequency,
                capabilities = capabilities,
                essid = essid,
                essidHex = essidHex,
                bssid = bssid,
                ch = ch,

                enc = enc,
                cip = cip,
                wps = wps,

                bssidHex = bssidHex,
                manufacturer = manufacturer,
                manufacturerDesc = manufacturerDesc,
            )
        }*/

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

        private fun getChanel(frequency: Int): Int {
            var fr = frequency
            var ans = 0
            if (fr in 2412..2484) {
                if (fr == 2484) return 14
                while (fr >= 2412) {
                    fr -= 5
                    ans++
                }
            } else if (fr in 3658..3692) {
                ans = 130
                while (fr >= 3655) {
                    fr -= 5
                    ans++
                }
            } else if (fr in 4940..4990 && fr % 5 != 0) {
                ans = 19
                while (fr >= 4940) {
                    fr -= 7
                    ans++
                }
            } else if (fr in 4915..4980) {
                ans = 182
                while (fr >= 4915) {
                    fr -= 5
                    ans++
                }
            } else if (fr in 5035..5825) {
                ans = 6
                while (fr >= 5035) {
                    fr -= 5
                    ans++
                }
            }
            return ans
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
