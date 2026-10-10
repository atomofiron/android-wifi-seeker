package ru.raslav.wirelessscan.adapters

import android.content.ClipboardManager
import android.content.res.Resources
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import ru.raslav.wirelessscan.Const.ALPHA_INT_HALF
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.copy
import ru.raslav.wirelessscan.data.CurrentConnection
import ru.raslav.wirelessscan.data.Point
import ru.raslav.wirelessscan.data.PointColors
import ru.raslav.wirelessscan.databinding.PointDescriptionBinding
import ru.raslav.wirelessscan.databinding.ItemPointBinding
import ru.raslav.wirelessscan.elog
import ru.raslav.wirelessscan.isRtl
import ru.raslav.wirelessscan.isVisible
import ru.raslav.wirelessscan.isWide
import ru.raslav.wirelessscan.textColor
import ru.raslav.wirelessscan.utils.SideDrawable
import kotlin.math.min
import kotlin.text.isEmpty


private const val MAX_INDICATOR_LEVEL = 512

class PointHolder(
    val binding: ItemPointBinding,
    private val colors: PointColors,
    private val clip: ClipboardManager,
    private val closeDescription: (View) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {

    private val focusedDrawable = SideDrawable(
        ContextCompat.getColor(binding.root.context, R.color.gray),
        binding.root.resources.getDimension(R.dimen.one),
    )

    fun bind(point: Point, focused: Point?, current: CurrentConnection?) = binding.bind(point, focused, current)

    fun ItemPointBinding.bind(
        point: Point,
        focused: Point?,
        current: CurrentConnection?,
    ) {
        itemColumns.bindBackground(point, focused)
        val connected = point.bssid == current?.bssid
        val theSame = point.theSame(focused)
        binding.updateDescription(
            point.takeIf { theSame },
            current?.takeIf { connected },
        )
        root.foreground = focusedDrawable.takeIf { theSame }
        focusedDrawable.setRtl(root.isRtl())

        if (point.level == -1) {
            elog("WOW: point.level == -1")
        }
        // point.level >= -1 experiment
        pwr.textColor = if (point.level >= -1) Color.MAGENTA else getPowerColor(point.level)

        ch.text = point.ch.toString()
        ch.textColor = if (point.is5Ghz()) colors.blueLight else colors.gray

        enc.text = point.enc
        enc.textColor = when {
            point.capabilities.contains("EAP") -> colors.redLight
            point.capabilities.contains("SAE-") -> colors.jinx
            point.capabilities.contains("WPA") -> colors.yellowMiddle
            point.capabilities.contains("WEP") -> colors.skyLight
            point.withCip() -> colors.gray
            else -> colors.green
        }
        cip.text = if (point.capabilities.contains("CCMP")) "CCMP" else ""
        cip.textColor = if (point.capabilities.contains("TKIP")) {
            cip.text = if (cip.text.isEmpty()) "  TKIP" else "+TKIP"
            when {
                point.capabilities.contains("preauth") -> colors.sky
                else -> colors.skyWhite
            }
        } else {
            colors.gray
        }

        wps.text = if (point.wps) "yes" else "no"
        wps.textColor = if (point.wps) colors.greenHigh else colors.redHigh

        essid.text = when {
            point.essid.isEmpty() -> point.bssid
            point.essid.isVisible() -> point.essid
            else -> point.essidHex
        }
        essid.textColor = when {
            connected -> colors.greenLight
            point.essid.isEmpty() -> colors.yellow
            point.essid.isVisible() -> colors.gray
            else -> colors.yellow
        }
        bssid.text = point.bssid
        bssid.setTextColor(if (connected) colors.greenLight else colors.gray)
        bssid.isVisible = root.resources.configuration.isWide()
    }


    @Suppress("DEPRECATION")
    private fun getPowerColor(level: Int): Int {
        val level = level.coerceIn(Point.range)
        /* starting Android 8 WifiManager.calculateSignalLevel(int, int) returns something looks wrong */
        val pwr = when {
            SDK_INT >= O -> MAX_INDICATOR_LEVEL * (min(level, -50) + 100) / 50
            else -> WifiManager.calculateSignalLevel(level, MAX_INDICATOR_LEVEL)
        }

        var red = if (pwr <= MAX_INDICATOR_LEVEL / 2) "ff" else Integer.toHexString(MAX_INDICATOR_LEVEL - pwr)
        var green = if (pwr >= MAX_INDICATOR_LEVEL / 2) "ff" else Integer.toHexString(pwr)

        if (red.length < 2)
            red = "0$red"

        if (green.length < 2)
            green = "0$green"

        return "#ff$red${green}00".toColorInt()
    }

    private fun View.bindBackground(point: Point, focused: Point?) {
        val focused = focused
        val associating = when {
            focused == null -> false
            focused.bssidGroup.isNotEmpty() && point.bssidGroup.isNotEmpty() -> focused.bssidGroup == point.bssidGroup
            else -> false
        }
        when {
            !associating -> background = null
            point.level <= Point.range.first -> setBackgroundResource(R.drawable.grille_red)
            else -> setBackgroundResource(R.drawable.grille)
        }
    }

    private fun ItemPointBinding.updateDescription(point: Point?, current: CurrentConnection?) {
        val description = root.findViewById<View>(R.id.layout_description)
            ?.let { PointDescriptionBinding.bind(it) }
        when {
            point != null && description != null -> description.bind(point, current, colors)
            point == null && description != null -> root.removeView(description.root)
            point != null && description == null -> LayoutInflater.from(root.context)
                .let { PointDescriptionBinding.inflate(it, root, true) }
                .init()
                .bind(point, current, colors)
        }
    }

    private fun PointDescriptionBinding.init(): PointDescriptionBinding {
        val copy = ContextCompat.getDrawable(root.context, R.drawable.ic_copy)!!
        val size = tvEssid.textSize.toInt()
        copy.setBounds(0, 0, size, size)
        copy.alpha = ALPHA_INT_HALF
        copy.setTintList(tvEssid.textColors)
        arrayOf(tvEssid, tvEssidHex, tvBssid, tvIp, tvGateway).forEach { tv ->
            tv.setCompoundDrawablesRelative(null, null, copy, null)
            tv.compoundDrawablePadding = size / 2
            tv.setOnClickListener { tv.onCopiableClick() }
        }
        return this
    }

    private fun TextView.onCopiableClick() {
        val parts = text.toString()
            .split(": ")
        val data = parts.getOrNull(1) ?: return
        val label = parts.first()
        clip.copy(context, label, data)
    }

    private fun PointDescriptionBinding.bind(
        point: Point,
        current: CurrentConnection?,
        colors: PointColors,
    ) {
        val resources = root.resources
        tvEssid.text = when {
            point.essid.isEmpty() -> resources.colored(resources.getString(R.string.essid_empty), colors.yellow)
            else -> resources.getString(R.string.essid_format, point.essid)
        }
        tvEssidHex.text = point.essidHex.takeIf { it.isNotEmpty() }
            ?.let { resources.getString(R.string.essid_hex_format, it) }
        tvEssidHex.isVisible = point.essidHex.isNotEmpty()
        tvBssid.text = resources.getString(R.string.bssid_format, point.bssid)
        tvIp.text = current?.address?.takeIf { it.isNotEmpty() }
            ?.let { resources.getString(R.string.ip_format, it) }
        tvIp.isVisible = !current?.address.isNullOrEmpty()
        tvGateway.text = current?.gateway?.takeIf { it.isNotEmpty() }
            ?.let { resources.getString(R.string.gateway_format, it) }
        tvGateway.isVisible = !current?.gateway.isNullOrEmpty()
        tvCapab.text = resources.getString(R.string.capab_format, point.capabilities)
        tvFrequ.text = resources.getString(R.string.frequ_format, point.frequency, point.ch, point.level)
        tvManuf.text = resources.getString(R.string.manuf_format, point.manufacturer)
        tvManuf.isVisible = point.manufacturer.isNotBlank()
        tvManufDesc.text = point.manufacturerDesc
        tvManufDesc.isVisible = point.manufacturerDesc.isNotBlank()
        cross.setOnClickListener(closeDescription)
    }

    private fun Resources.colored(text: String, color: Int): CharSequence {
        return SpannableStringBuilder(getString(R.string.essid_format, text)).apply {
            setSpan(ForegroundColorSpan(color), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

}
