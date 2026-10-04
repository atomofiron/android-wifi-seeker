package ru.raslav.wirelessscan.fragments

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import androidx.recyclerview.widget.RecyclerView
import lib.atomofiron.insets.insetsPadding
import ru.raslav.wirelessscan.Const
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.openPermissionSettings
import ru.raslav.wirelessscan.sp
import ru.raslav.wirelessscan.tryStartActivity
import ru.raslav.wirelessscan.unsafeLazy
import ru.raslav.wirelessscan.utils.OuiManager

class PrefFragment : PreferenceFragmentCompat(), Titled by Titled(R.string.settings), Preference.OnPreferenceChangeListener {

    private val sp: SharedPreferences by unsafeLazy { requireContext().sp() }

    private lateinit var scanInBg: TwoStatePreference

    private val backgroundLocationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            granted -> scanInBg.isChecked = true
            else -> requireContext().openPermissionSettings()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)

        findPreference<Preference>(Const.PREF_MAIL)!!.setOnPreferenceClickListener { mailToDeveloper(); true }
        findPreference<Preference>(Const.PREF_OUI_SOURCE)!!.run {
            setOnPreferenceClickListener { openOuiSource(); true }
            summary = "$summary (${resources.getString(R.string.x_entries, OuiManager.entries())})"
        }
        findPreference<Preference>(Const.PREF_PRIVACY_POLICY)!!.setOnPreferenceClickListener { openPrivacyPolicy(); true }
        findPreference<Preference>(Const.PREF_SOURCE_CODE)!!.setOnPreferenceClickListener { openSourceCode(); true }
        scanInBg = findPreference(Const.PREF_WORK_IN_BG)!!
        setListeners(preferenceScreen)
    }

    override fun onCreateRecyclerView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?,
    ): RecyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState).apply {
        insetsPadding(start = true, end = true, bottom = true)
        if (SDK_INT >= Q) {
            verticalScrollbarThumbDrawable = ContextCompat.getDrawable(context, R.drawable.scroll_vertical)
        }
    }

    private fun setListeners(screen: PreferenceGroup) {
        (0 until screen.preferenceCount)
                .map { screen.getPreference(it) }
                .forEach {
                    when (it) {
                        is PreferenceScreen,
                        is PreferenceCategory -> setListeners(it)
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
            Const.PREF_WORK_IN_BG -> if (newValue == true && !backgroundLocationGranted()) {
                backgroundLocationLauncher.launch(ACCESS_BACKGROUND_LOCATION)
                return false
            }
        }
        return true
    }

    private fun mailToDeveloper() {
        Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "atomofiron@gmail.com", null))
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.dear_dev))
            .showChooser(R.string.send_email)
    }

    private fun openPrivacyPolicy() {
        Intent(Intent.ACTION_VIEW, "https://github.com/atomofiron/android-wifi-seeker/blob/master/privacy-policy.md".toUri())
            .showChooser(R.string.open_an_url)
    }

    private fun openOuiSource() {
        Intent(Intent.ACTION_VIEW, "https://www.wireshark.org/tools/oui-lookup".toUri())
            .showChooser(R.string.open_an_url)
    }

    private fun openSourceCode() {
        Intent(Intent.ACTION_VIEW, "https://github.com/atomofiron/android-wifi-seeker".toUri())
            .showChooser(R.string.open_an_url)
    }

    private fun Intent.showChooser(title: Int) {
        val chooser = Intent.createChooser(this, getString(title))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        requireContext().tryStartActivity(chooser)
    }

    private fun backgroundLocationGranted() = SDK_INT < Q || requireContext().checkSelfPermission(ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
}
