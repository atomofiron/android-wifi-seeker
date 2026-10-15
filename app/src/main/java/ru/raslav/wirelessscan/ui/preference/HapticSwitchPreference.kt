package ru.raslav.wirelessscan.ui.preference

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.view.HapticFeedbackConstantsCompat.REJECT
import androidx.core.view.HapticFeedbackConstantsCompat.TOGGLE_OFF
import androidx.core.view.HapticFeedbackConstantsCompat.TOGGLE_ON
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.utils.performHapticEffect
import java.lang.ref.WeakReference

class HapticSwitchPreference : SwitchPreferenceCompat {

    private var view = WeakReference<View>(null)

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        widgetLayoutResource = R.layout.widget_switch
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        view = WeakReference(holder.itemView)
    }

    override fun callChangeListener(newValue: Any?): Boolean {
        return super.callChangeListener(newValue)
            .also { allowed ->
                newValue as Boolean?
                newValue ?: return@also
                when {
                    !allowed -> REJECT
                    newValue -> TOGGLE_ON
                    else -> TOGGLE_OFF
                }.let { view.get()?.performHapticEffect(it) }
            }
    }
}