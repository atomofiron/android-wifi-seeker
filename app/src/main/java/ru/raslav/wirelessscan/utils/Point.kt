package ru.raslav.wirelessscan.utils

import android.content.res.Resources
import android.graphics.Color
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Parcel
import android.os.Parcelable
import org.simpleframework.xml.Element
import org.simpleframework.xml.Root
import ru.raslav.wirelessscan.R

@Root(name = "point")
class Point private constructor(): Parcelable {
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(level)
        dest.writeInt(frequency)
        dest.writeString(capabilities)
        dest.writeString(essid)
        dest.writeString(bssid)
        dest.writeInt(ch)
        dest.writeString(manufacturer)
    }

    override fun describeContents(): Int = 0

    @get:Element(name = "level")
    @set:Element(name = "level")
    var level = 0
        set(value) { field = value; pwColor = getPowerColor(value)
        }
    @get:Element(name = "frequency")
    @set:Element(name = "frequency")
    var frequency = 0
        set(value) { field = value; parseFrequency(value) }
    @get:Element(name = "capabilities")
    @set:Element(name = "capabilities")
    var capabilities = ""
        set(value) { field = value; parseCapabilities(value) }
    @get:Element(name = "essid")
    @set:Element(name = "essid")
    var essid = ""
        set(value) { field = value; essidColor = if (value.isEmpty()) yellow else grey }
    @field:Element(name = "bssid")
    var bssid = ""

    @field:Element(name = "channel")
    var ch = 0
    lateinit var enc: String
        private set
    lateinit var chi: String
        private set
    lateinit var wps: String
        private set
    @field:Element(name = "manufacturer")
    var manufacturer = ""

    var pwColor = 0
        private set
    var chColor = 0
        private set
    var encColor = 0
        private set
    var chiColor = 0
        private set
    var wpsColor = 0
        private set
    var essidColor = 0
        private set
    val bssidColor = grey

    constructor(sr: ScanResult) : this() {
        level = sr.level
        frequency = sr.frequency
        ch = getChanel(frequency)
        capabilities = sr.capabilities
        essid = sr.SSID
        bssid = sr.BSSID
    }

    private fun is5G(): Boolean = frequency >= 4915

    private fun parseFrequency(frequency: Int) {
        ch = getChanel(frequency)
        chColor = if (is5G()) blue_light else grey
    }

    private fun parseCapabilities(capabilities: String) {
        specifyEnc(capabilities)
        specifyChi(capabilities)
        specifyWps(capabilities)
    }

    private fun specifyEnc(capabilities: String) {
        enc = "OPN"
        encColor = green
        if (capabilities.contains("WPA")) {
            enc = if (capabilities.contains("WPA2")) "WPA2" else "WPA"
            encColor = yellow_middle
        } else if (capabilities.contains("WEP")) {
            enc = "WEP"
            encColor = sky_light
        }
        if (capabilities.contains("EAP"))
            encColor = red_light
    }

    private fun specifyChi(capabilities: String) {
        chi = if (capabilities.contains("CCMP")) "CCMP" else ""
        chiColor = grey

        if (capabilities.contains("TKIP")) {
            chi = if (chi.isEmpty()) "  TKIP" else "+TKIP"
            chiColor = if (capabilities.contains("preauth")) sky else sky_white
        }
    }

    private fun specifyWps(capabilities: String) {
        val yes = capabilities.contains("WPS")

        wps = if (yes) "yes" else "no"
        wpsColor = if (yes) green_high else red_high
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other::class.java != Point::class.java)
            return false

        val o = other as Point
        return o.essid == essid && o.bssid == bssid
    }

    fun compare(bssid: String, essid: String, hidden: Boolean): Boolean =
            this.bssid == bssid && (this.essid == essid || this.level > MIN_LEVEL && this.essid.isEmpty() && hidden)

    fun isSimilar(point: Point, smart: Boolean): Boolean = this.essid == point.essid &&
            if (smart)
                point.bssid.length >= 8 && !this.bssid.startsWith(point.bssid.substring(0, 8))
            else
                this.bssid != point.bssid

    fun getNotEmptyESSID(): String = if (essid.isEmpty()) bssid else essid

/*    override fun describeContents(): Int {
        return 0
    }
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(level)
        dest.writeInt(frequency)
        dest.writeString(capabilities)
        dest.writeString(essid)
        dest.writeString(bssid)
    }*/

    companion object {
		private val MAX_INDICATOR_LEVEL = 512
        val MIN_LEVEL = -100 // WifiManager.MIN_LEVEL

        var transparent = 0
            private set
        var black_lite = 0
            private set
        var red_lite= 0
            private set

        private var red_middle = 0
        private var grey = 0
        private var blue_light = 0
        private var green = 0
        private var yellow_middle = 0
        private var sky_light = 0
        private var red_light = 0
        private var sky = 0
        private var sky_white = 0
        private var green_high = 0
        private var red_high = 0
        private var yellow = 0
        var green_light = 0; private set

        fun initColors(resources: Resources) {
            transparent = resources.getColor(R.color.transparent)
            black_lite = resources.getColor(R.color.black_lite)
            red_lite = resources.getColor(R.color.red_lite)
            red_middle = resources.getColor(R.color.red_middle)
            grey = resources.getColor(R.color.grey)
            blue_light = resources.getColor(R.color.blue_light)
            green = resources.getColor(R.color.green)
            yellow_middle = resources.getColor(R.color.yellow_middle)
            sky_light = resources.getColor(R.color.sky_light)
            red_light = resources.getColor(R.color.red_light)
            sky = resources.getColor(R.color.sky)
            sky_white = resources.getColor(R.color.sky_white)
            green_high = resources.getColor(R.color.green_high)
            red_high = resources.getColor(R.color.red_high)
            yellow = resources.getColor(R.color.yellow)
            green_light = resources.getColor(R.color.green_light)
        }

        private fun getPowerColor(level: Int): Int {
			/* не знаю в чём причина, но, начиная с Android 8,
			   функция WifiManager.calculateSignalLevel(int, int)
			   возвращает неадекватные значения */
            val pwr = when {
                SDK_INT >= O -> MAX_INDICATOR_LEVEL * (Math.min(level, -50) + 100) / 50
                else -> WifiManager.calculateSignalLevel(level, MAX_INDICATOR_LEVEL)
            }

            var red = if (pwr <= MAX_INDICATOR_LEVEL / 2) "ff" else Integer.toHexString(MAX_INDICATOR_LEVEL - pwr)
            var green = if (pwr >= MAX_INDICATOR_LEVEL / 2) "ff" else Integer.toHexString(pwr)

            if (red.length < 2)
                red = "0" + red

            if (green.length < 2)
                green = "0" + green

            return Color.parseColor("#ff$red${green}00")
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
            } else if (fr in 4940..4990
                    && fr % 5 != 0) {
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

        fun parseScanResults(list: List<ScanResult>) : ArrayList<Point> {
            val points = ArrayList<Point>()
            list.forEach { it -> points.add(Point(it)) }

            return points
        }

        @JvmField
        val CREATOR: Parcelable.Creator<Point> = object : Parcelable.Creator<Point> {
            override fun createFromParcel(parcel: Parcel): Point {
                val point = Point()
                point.level = parcel.readInt()
                point.frequency = parcel.readInt()
                point.capabilities = parcel.readString()!!
                point.essid = parcel.readString()!!
                point.bssid = parcel.readString()!!
                point.ch = parcel.readInt()
                point.manufacturer = parcel.readString()!!
                return point
            }

            override fun newArray(size: Int): Array<Point?> = arrayOfNulls(size)
        }
    }
}