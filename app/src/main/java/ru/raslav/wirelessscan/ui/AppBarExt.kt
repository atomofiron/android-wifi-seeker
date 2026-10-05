package ru.raslav.wirelessscan.ui

import androidx.fragment.app.Fragment
import lib.atomofiron.insets.insetsPadding
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.databinding.AppBarBinding

fun AppBarBinding.init(fragment: Fragment, title: Int) {
    init(fragment, fragment.getString(title))
}

fun AppBarBinding.init(
    fragment: Fragment,
    title: String,
    backButton: Boolean = true,
) {
    root.insetsPadding(start = true, top = true, end = true)
    toolbar.title = title
    if (backButton) {
        toolbar.setNavigationIcon(R.drawable.ic_back)
        var clicked = false
        toolbar.setNavigationOnClickListener {
            if (clicked) return@setNavigationOnClickListener
            clicked = true
            fragment.parentFragmentManager.popBackStack()
        }
    }
}
