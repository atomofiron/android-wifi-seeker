package ru.raslav.wirelessscan.utils

import androidx.core.graphics.Insets
import lib.atomofiron.insets.ExtendedWindowInsets
import lib.atomofiron.insets.TypeSet

object ExtType : ExtendedWindowInsets.Type() {
    val bottomToolbar: TypeSet = define("bottom_toolbar")
    inline operator fun invoke(block: ExtType.() -> TypeSet): TypeSet = ExtType.block()
}

// associate your custom type with ExtendedWindowInsets
operator fun ExtendedWindowInsets.invoke(block: ExtType.() -> TypeSet): Insets = get(ExtType.block())