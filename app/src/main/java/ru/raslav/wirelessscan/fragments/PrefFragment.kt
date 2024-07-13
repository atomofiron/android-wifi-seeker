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
import ru.raslav.wirelessscan.Const
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.longToast
import ru.raslav.wirelessscan.sp
import ru.raslav.wirelessscan.unsafeLazy

class PrefFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {

    private val sp: SharedPreferences by unsafeLazy { requireContext().sp() }

    private lateinit var attackScreen: PreferenceScreen
    private lateinit var noScanInBg: TwoStatePreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // todo deprecation
        setHasOptionsMenu(true)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)

        val detectAttacks = findPreference<TwoStatePreference>(Const.PREF_DETECT_ATTACKS)!!
        val autoOff = findPreference<TwoStatePreference>(Const.PREF_AUTO_OFF_WIFI)!!
        attackScreen = findPreference(Const.PREF_ATTACK_SCREEN)!!
        attackScreen.isEnabled = detectAttacks.isChecked
        noScanInBg = findPreference(Const.PREF_NO_SCAN_IN_BG)!!
        noScanInBg.isEnabled = autoOff.isChecked
        setListeners(preferenceScreen)
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
            Const.PREF_DETECT_ATTACKS -> attackScreen.isEnabled = newValue as Boolean
            Const.PREF_AUTO_OFF_WIFI -> updateNoScanPreference(newValue as Boolean)
        }
        return true
    }

    private fun updateNoScanPreference(value: Boolean) {
        noScanInBg.isEnabled = value

        when (false) {
            value -> noScanInBg.isChecked = false
            sp.getBoolean(Const.PREF_WORK_IN_BG, false) -> requireContext().longToast(R.string.recom)
            else -> Unit
        }
    }
}
