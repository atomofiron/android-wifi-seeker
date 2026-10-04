package ru.raslav.wirelessscan.fragments

import androidx.annotation.StringRes

interface Titled {
    val titleId: Int get() = 0
    val title: String? get() = null

    companion object {

        operator fun invoke(@StringRes titleId: Int): Titled = object : Titled {
            override val titleId = titleId
        }
    }
}