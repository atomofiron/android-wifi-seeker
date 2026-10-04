package ru.raslav.wirelessscan.fragments

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.Q
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import lib.atomofiron.insets.insetsPadding
import ru.raslav.wirelessscan.Const
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.data.Loading
import ru.raslav.wirelessscan.databinding.WidgetRefreshAndOutsideBinding
import ru.raslav.wirelessscan.openPermissionSettings
import ru.raslav.wirelessscan.scope
import ru.raslav.wirelessscan.sp
import ru.raslav.wirelessscan.tryStartActivity
import ru.raslav.wirelessscan.unsafeLazy
import ru.raslav.wirelessscan.utils.OuiManager

class PrefFragment : PreferenceFragmentCompat(), Titled by Titled(R.string.settings), Preference.OnPreferenceChangeListener {

    private val sp: SharedPreferences by unsafeLazy { requireContext().sp() }

    private lateinit var scanInBg: TwoStatePreference
    private lateinit var ouiPreference: Preference

    private val backgroundLocationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            granted -> scanInBg.isChecked = true
            else -> requireContext().openPermissionSettings()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)

        findPreference<Preference>(Const.PREF_MAIL)!!.setOnPreferenceClickListener { mailToDeveloper(); true }
        ouiPreference = findPreference<Preference>(Const.PREF_OUI_SOURCE)!!.apply {
            setViewId(R.id.oui_source)
            setOnPreferenceClickListener { openOuiSource(); true }
            setOuiEntries(resources, OuiManager.self.entries())
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
        addOnChildAttachStateChangeListener(ChildAttachListener(ouiPreference))
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

    private class ChildAttachListener(
        private val preference: Preference,
    ) : RecyclerView.OnChildAttachStateChangeListener {

        private var tried = false

        override fun onChildViewAttachedToWindow(view: View) {
            if (view.id == R.id.oui_source) {
                WidgetRefreshAndOutsideBinding.bind(view.findViewById(R.id.widget)).run {
                    button.contentDescription = view.resources.getString(R.string.update_oui)
                    if (SDK_INT >= O) button.tooltipText = button.contentDescription
                    root.scope().launch(Main) {
                    OuiManager.self.ouiLoading.collect { loading ->
                        loading ?: return@collect
                            button.isEnabled = false
                            button.isVisible = !loading.progress
                            progress.isVisible = loading.progress
                            when (loading) {
                                is Loading.Progress -> {
                                    progress.isIndeterminate = loading.value == null
                                    progress.progress = ((loading.value ?: 0f) * progress.max).toInt()
                                }
                                is Loading.Finished<Long> -> {
                                    button.setImageResource(R.drawable.ic_circle_check)
                                    preference.setOuiEntries(view.resources, loading.data)
                                    button.setOnClickListener(null)
                                    button.background = null
                                }
                                is Loading.Error if (tried) -> {
                                    tried = false
                                    button.setImageResource(R.drawable.ic_error)
                                    view.context.showError(loading.message)
                                }
                                is Loading.Error -> {
                                    button.setImageResource(R.drawable.ic_refresh)
                                    button.isEnabled = true
                                }
                            }
                        }
                    }
                    button.setOnClickListener {
                        tried = true
                        it.isEnabled = false
                        OuiManager.self.update(view.context)
                    }
                }
            }
        }

        override fun onChildViewDetachedFromWindow(view: View) = Unit
    }
}

private fun Preference.setOuiEntries(resources: Resources, entries: Long) {
    summary = "${resources.getString(R.string.oui_lookup_tool)} (${resources.getString(R.string.x_entries, entries)})"
}

private fun Context.showError(message: String) = MaterialAlertDialogBuilder(this)
    .setTitle(R.string.error)
    .setMessage(message)
    .setCancelable(false)
    .setPositiveButton(R.string.ok, null)
    .show()
