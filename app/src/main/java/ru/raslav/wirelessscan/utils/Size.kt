package ru.raslav.wirelessscan.utils

@JvmInline
value class Size private constructor(val value: Long) {

    val width get() = value shr 32
    val height get() = value and 0xFFFF

    constructor(width: Int = 0, height: Int = 0) : this((width.toLong() shl 32) + height.toLong())
}
