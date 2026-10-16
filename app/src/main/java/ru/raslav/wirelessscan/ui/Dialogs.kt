package ru.raslav.wirelessscan.ui

import android.content.Context
import android.content.DialogInterface.BUTTON_POSITIVE
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.raslav.wirelessscan.Const.SNAPSHOT_FORMAT
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.clipboardManager
import ru.raslav.wirelessscan.copy
import ru.raslav.wirelessscan.utils.FileNameInputText
import java.io.File

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

fun Context.renameSnapshot(
    name: String,
    onRenamed: ((from: String, to: String) -> Unit)? = null,
) {
    val file = File(filesDir, name)
    if (file.exists()) {
        val editText = FileNameInputText(this)
        editText.setText(name)
        editText.setSelection(0, name.length - SNAPSHOT_FORMAT.length)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename_to)
            .setView(editText)
            .setCancelable(false)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val dst = editText.dstSnapshotFile()
                    ?: return@setPositiveButton
                val success = file.renameTo(dst)
                val messageId = if (success) R.string.success else R.string.failure
                Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show()
                if (success) onRenamed?.invoke(name, dst.name)
            }.create()
        dialog.show()
        editText.requestFocus()
        editText.postDelayed({
            val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            manager.showSoftInput(editText, 0)
        }, 256)
        editText.addTextChangedListener {
            dialog.getButton(BUTTON_POSITIVE)
                .isEnabled = editText.dstSnapshotFile() != null
        }
    } else
        Toast.makeText(this, R.string.failure, Toast.LENGTH_SHORT).show()
}

private fun EditText.dstSnapshotFile(): File? {
    var name = text.toString()
        .takeIf { it.isNotEmpty() }
        ?: return null

    if (!name.endsWith(SNAPSHOT_FORMAT))
        name += SNAPSHOT_FORMAT

    return File(context.filesDir, name)
        .takeIf { !it.exists() }
}
