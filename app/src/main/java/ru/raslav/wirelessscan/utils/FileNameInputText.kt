package ru.raslav.wirelessscan.utils

import android.content.Context
import androidx.appcompat.widget.AppCompatEditText
import java.util.regex.Pattern

class FileNameInputText(co: Context) : AppCompatEditText(co) {
    companion object { // necessary
        private val pattern = Pattern.compile("[^a-zA-Zа-яА-Я0-9.()_-]")
    }
    private var lastInput = ""

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)

        if (pattern.matcher(text).find()) {
            setText(lastInput)
            setSelection(Math.min(start, lastInput.length))
        } else
            lastInput = text.toString()
    }
}