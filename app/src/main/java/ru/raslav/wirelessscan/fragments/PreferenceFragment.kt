package ru.raslav.wirelessscan.fragments

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.Q
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
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
import kotlinx.coroutines.launch
import lib.atomofiron.insets.insetsPadding
import ru.raslav.wirelessscan.Const
import ru.raslav.wirelessscan.Const.ONE_DAY
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.data.Loading
import ru.raslav.wirelessscan.databinding.FragmentPreferencesBinding
import ru.raslav.wirelessscan.databinding.WidgetRefreshAndOutsideBinding
import ru.raslav.wirelessscan.openPermissionSettings
import ru.raslav.wirelessscan.scope
import ru.raslav.wirelessscan.sp
import ru.raslav.wirelessscan.tryStartActivity
import ru.raslav.wirelessscan.ui.init
import ru.raslav.wirelessscan.ui.overscroll.setupSpringOverscroll
import ru.raslav.wirelessscan.unsafeLazy
import ru.raslav.wirelessscan.utils.LinearLayoutParams
import ru.raslav.wirelessscan.utils.OuiManager
import ru.raslav.wirelessscan.utils.PreferenceId

class PreferenceFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {

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
        updatePaddingRelative(top = resources.getDimensionPixelSize(R.dimen.padding_common))
        insetsPadding(start = true, end = true, bottom = true)
        setupSpringOverscroll()
        clipToPadding = false
        if (SDK_INT >= Q) {
            verticalScrollbarThumbDrawable = ContextCompat.getDrawable(context, R.drawable.scroll_vertical)
        }
        addOnChildAttachStateChangeListener(ChildAttachListener(sp, ouiPreference))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        val binding = FragmentPreferencesBinding.inflate(inflater)
        binding.appBar.init(this, R.string.settings)
        binding.root.addView(view)
        view.updateLayoutParams<LinearLayoutParams> {
            height = 0
            weight = 1f
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setDividerHeight(0)
        setDivider(null)
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
        when (preference) {
            is EditTextPreference -> preference.setSummary(value as String? ?: sp.getString(preference.key, ""))
            // preference.entry from entries, but newValue from entryValues
            is ListPreference -> preference.summary = if (value is String) preference.entries[value.toInt()] else preference.entry
        }
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
        private val sp: SharedPreferences,
        private val preference: Preference,
    ) : RecyclerView.OnChildAttachStateChangeListener {

        private val headFrameWidth = preference.context.resources.getDimensionPixelSize(R.dimen.preference_head_frame_width)
        private var buttonBackground: Drawable? = null
        private var button: View? = null
        private var tried = false

        override fun onChildViewAttachedToWindow(view: View) {
            view.findViewById<LinearLayout>(PreferenceId.icon_frame).run {
                minimumWidth = headFrameWidth
            }
            if (view.id == R.id.oui_source) {
                val new = view.findViewById<View>(R.id.widget)
                if (new !== button) {
                    button = new
                    WidgetRefreshAndOutsideBinding.bind(new).bindOuiWidget()
                }
            }
        }

        private fun WidgetRefreshAndOutsideBinding.bindOuiWidget() {
            val now = SystemClock.elapsedRealtime()
            if (now < ONE_DAY + sp.getLong(Const.PREF_LAST_OUI_REFRESH, 0)) {
                button.isEnabled = false
                return
            }
            buttonBackground = buttonBackground ?: button.background
            button.contentDescription = root.resources.getString(R.string.update_oui)
            if (SDK_INT >= O) button.tooltipText = button.contentDescription
            setClickListener()
            root.scope().launch {
                OuiManager.self.ouiLoading.collect { loading ->
                    loading ?: return@collect
                    button.isVisible = !loading.progress
                    progress.isVisible = loading.progress
                    when (loading) {
                        is Loading.Progress -> {
                            progress.isIndeterminate = loading.value == null
                            progress.progress = ((loading.value ?: 0f) * progress.max).toInt()
                        }
                        is Loading.Finished<Long> -> {
                            button.setImageResource(R.drawable.ic_circle_check)
                            preference.setOuiEntries(root.resources, loading.data)
                            sp.edit { putLong(Const.PREF_LAST_OUI_REFRESH, now) }
                        }
                        is Loading.Error if (tried) -> {
                            tried = false
                            button.setImageResource(R.drawable.ic_error)
                            root.context.showError(loading.message)
                        }
                        is Loading.Error -> return@collect
                    }
                    removeClickListener()
                }
            }
        }

        private fun WidgetRefreshAndOutsideBinding.setClickListener() {
            button.background = buttonBackground
            button.setOnClickListener {
                tried = true
                removeClickListener()
                OuiManager.self.update(it.context)
            }
        }

        private fun WidgetRefreshAndOutsideBinding.removeClickListener() {
            button.background = null
            button.setOnClickListener(null)
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
