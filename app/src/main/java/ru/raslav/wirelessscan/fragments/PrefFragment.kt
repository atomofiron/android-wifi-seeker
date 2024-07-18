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
import android.view.Menu
import android.view.MenuInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
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
import ru.raslav.wirelessscan.unsafeLazy

class PrefFragment : PreferenceFragmentCompat(), Titled by Titled(R.string.settings), Preference.OnPreferenceChangeListener {

    private val sp: SharedPreferences by unsafeLazy { requireContext().sp() }

    private lateinit var scanInBg: TwoStatePreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // todo deprecation
        setHasOptionsMenu(true)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)

        findPreference<Preference>(Const.PREF_MAIL)!!.setOnPreferenceClickListener { mailToDeveloper(); true }
        findPreference<Preference>(Const.PREF_OUI_SOURCE)!!.setOnPreferenceClickListener { openOuiSource(); true }
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
            Const.PREF_WORK_IN_BG -> if (newValue == true && !backgroundLocationGranted()) {
                requestPermissions(arrayOf(ACCESS_BACKGROUND_LOCATION), Const.BG_LOCATION_REQUEST_CODE)
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
        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/atomofiron/android-wifi-seeker/blob/master/privacy-policy.md"))
            .showChooser(R.string.open_an_url)
    }

    private fun openOuiSource() {
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.wireshark.org/tools/oui-lookup"))
            .showChooser(R.string.open_an_url)
    }

    private fun openSourceCode() {
        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/atomofiron/android-wifi-seeker"))
            .showChooser(R.string.open_an_url)
    }

    private fun Intent.showChooser(title: Int) {
        if (resolveActivity(requireContext().packageManager) != null) {
            val chooser = Intent.createChooser(this, getString(title))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(chooser)
        } else
            Toast.makeText(requireContext(), R.string.no_activity, Toast.LENGTH_SHORT).show()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when {
            requestCode != Const.BG_LOCATION_REQUEST_CODE -> Unit
            grantResults.first() == PackageManager.PERMISSION_GRANTED -> scanInBg.isChecked = true
            else -> requireContext().openPermissionSettings()
        }
    }

    private fun backgroundLocationGranted() = SDK_INT < Q || requireContext().checkSelfPermission(ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
}
