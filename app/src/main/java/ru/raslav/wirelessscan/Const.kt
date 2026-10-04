package ru.raslav.wirelessscan

import android.Manifest
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q

object Const {
    const val SNAPSHOT_FORMAT = ".xml"

    const val PREF_MAIL = "mail_to_dev"
    const val PREF_OUI_SOURCE = "oui_source"
    const val PREF_PRIVACY_POLICY = "privacy_policy"
    const val PREF_SOURCE_CODE = "source_code"
    const val PREF_DEFAULT_PERIOD = "default_period"
    const val PREF_SCAN_DURATION = "scan_duration"
    const val PREF_WORK_IN_BG = "work_in_bg"
    const val PREF_OUI_TEXT_LENGTH = "oui_text_length"

    const val DEFAULT_PERIOD = 5
    const val DEFAULT_DURATION = 2

    const val OUI_TEXT_LENGTH = 3165976L

    const val ALPHA_ZERO = 0f
    const val ALPHA_FULL = 1f

    const val ALPHA_INT_HALF = 128

    const val Dot = "\u25CF " // ●

    val LOCATION_PERMISSION = when {
        SDK_INT >= Q -> Manifest.permission.ACCESS_FINE_LOCATION
        else -> Manifest.permission.ACCESS_COARSE_LOCATION
    }

    val InvisibleChars = buildString {
        for (c in '\u0000'..'\u001F') append(c)
        append('\u007F')
        for (c in '\u0080'..'\u009F') append(c)
        append("\u00AD") // Soft Hyphen
        append("\u200B") // Zero Width Space
        append("\u200C") // Zero Width Non-Joiner
        append("\u200D") // Zero Width Joiner
        append("\u2028") // Line Separator
        append("\u2029") // Paragraph Separator
        append("\u2060") // Word Joiner
        append("\uFEFF") // BOM / Zero Width No-Break Space
        append("\u00A0") // NBSP
        append("\u1680") // Ogham Space
        for (c in '\u2000'..'\u200A') append(c) // En/Em/Thin/Hair Spaces
        append("\u202F") // NNBSP
        append("\u205F") // MMSP
        append("\u3000") // Ideographic Space
    }
}
