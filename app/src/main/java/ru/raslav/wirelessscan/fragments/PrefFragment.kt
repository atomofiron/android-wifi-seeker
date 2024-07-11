package ru.raslav.wirelessscan.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import ru.raslav.wirelessscan.I
import ru.raslav.wirelessscan.R

class PrefFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
    private lateinit var sp: SharedPreferences

    private lateinit var attackScreen: PreferenceScreen
    private lateinit var noScanInBg: TwoStatePreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        addPreferencesFromResource(R.xml.preferences)

        sp = I.sp(requireContext())
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setListeners(preferenceScreen)

        val detectAttacks = findPreference<TwoStatePreference>(I.PREF_DETECT_ATTACKS)!!
        val autoOff = findPreference<TwoStatePreference>(I.PREF_AUTO_OFF_WIFI)!!
        attackScreen = findPreference(I.PREF_ATTACK_SCREEN)!!
        attackScreen.isEnabled = detectAttacks.isChecked
        noScanInBg = findPreference(I.PREF_NO_SCAN_IN_BG)!!
        noScanInBg.isEnabled = autoOff.isChecked
    }

    override fun onStart() {
        super.onStart()

        requireActivity().setTitle(R.string.title_preferences)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.removeItem(R.id.settings)
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
            I.PREF_DETECT_ATTACKS -> attackScreen.isEnabled = newValue as Boolean
            I.PREF_AUTO_OFF_WIFI -> updateNoScanPreference(newValue as Boolean)
        }
        return true
    }

    private fun updateNoScanPreference(value: Boolean) {
        noScanInBg.isEnabled = value

        if (!value)
            noScanInBg.isChecked = false
        else
            if (!sp.getBoolean(I.PREF_WORK_IN_BG, false))
                I.longToast(requireContext(), R.string.recom)
    }
}
