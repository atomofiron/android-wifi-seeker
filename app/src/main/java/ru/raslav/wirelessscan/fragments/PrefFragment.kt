package ru.raslav.wirelessscan.fragments

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.preference.*
import android.view.*
import ru.raslav.wirelessscan.I
import ru.raslav.wirelessscan.R

class PrefFragment : PreferenceFragment(), Preference.OnPreferenceChangeListener {
    private lateinit var sp: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        addPreferencesFromResource(R.xml.preferences)

        sp = I.sp(activity)

        setListeners(preferenceScreen)

        findPreference(I.PREF_ATTACK_SCREEN).isEnabled = (findPreference(I.PREF_DETECT_ATTACKS) as TwoStatePreference).isChecked
        findPreference(I.PREF_NO_SCAN_IN_BG).isEnabled = (findPreference(I.PREF_AUTO_OFF_WIFI) as TwoStatePreference).isChecked
        findPreference(I.PREF_AUTO_OFF_WIFI).isEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
    }

    override fun onStart() {
        super.onStart()

        activity.setTitle(R.string.title_preferences)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.removeItem(R.id.settings)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun setListeners(screen: PreferenceGroup) {
        (0 until screen.preferenceCount)
                .map { screen.getPreference(it) }
                .forEach {
                    when (it) {
                        is PreferenceScreen, is PreferenceCategory -> setListeners(it as PreferenceGroup)
                        else -> {
                            it.onPreferenceChangeListener = this
                            updateSummary(it, null)
                        }
                    }
                }
    }

    private fun updateSummary(preference: Preference, value: Any?) {
        if (preference is EditTextPreference)
            preference.setSummary(value as String? ?: sp.getString(preference.key, ""))
        else if (preference is ListPreference)
            // preference.entry from entries, but newValue from entryValues
            preference.summary = if (value is String) preference.entries[value.toInt()] else preference.entry
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        updateSummary(preference, newValue)
        when (preference.key) {
            I.PREF_DETECT_ATTACKS -> findPreference(I.PREF_ATTACK_SCREEN).isEnabled = newValue as Boolean
            I.PREF_AUTO_OFF_WIFI -> updateNoScanPreference(newValue as Boolean)
        }
        return true
    }

    private fun updateNoScanPreference(value: Boolean) {
        findPreference(I.PREF_NO_SCAN_IN_BG).isEnabled = value

        if (!value)
            (findPreference(I.PREF_NO_SCAN_IN_BG) as CheckBoxPreference).isChecked = false
        else
            if (!sp.getBoolean(I.PREF_WORK_IN_BG, false))
                I.longToast(activity, R.string.recom)
    }
}
