package ru.raslav.wirelessscan.ui

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.clipboardManager
import ru.raslav.wirelessscan.copy

fun Context.showError(message: String?) {
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.error)
        .setCancelable(false)
        .setPositiveButton(R.string.ok, null)
        .run {
            when {
                message.isNullOrBlank() -> this
                else -> setMessage(message)
                    .setNeutralButton(android.R.string.copy) { _, _ ->
                        context.clipboardManager()
                            .copy(this@showError, getString(R.string.error), message)
                    }
            }
        }.show()
}
