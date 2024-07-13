package ru.raslav.wirelessscan.fragments

interface Titled {
    val titleId: Int get() = 0
    val title: String? get() = null

    companion object {

        operator fun invoke(titleId: Int): Titled = object : Titled {
            override val titleId = titleId
        }
    }
}