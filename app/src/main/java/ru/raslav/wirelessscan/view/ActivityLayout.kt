package ru.raslav.wirelessscan.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import lib.atomofiron.insets.InsetsProvider
import lib.atomofiron.insets.InsetsProviderImpl

@Suppress("DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE")
class ActivityLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes), InsetsProvider by InsetsProviderImpl() {
    init {
        onInit()
    }
}