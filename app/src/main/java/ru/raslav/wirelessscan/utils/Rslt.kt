package ru.raslav.wirelessscan.utils

sealed class Rslt<T>(val isOk: Boolean) {

    abstract class Ok<T> : Rslt<T>(isOk = true) {
        companion object : Rslt.Ok<Unit>() {
            private data class Ok<T>(override val value: T) : Rslt.Ok<T>()
            override val value = Unit
            operator fun <T> invoke(value: T): Rslt.Ok<T> = Ok(value)
            override fun toString() = "Rslt.Ok"
        }
        abstract val value: T

        override fun toString(): String = "Rslt.Ok($value)"
    }

    open class Err<T>(open val message: String = "") : Rslt<T>(isOk = false) {
        companion object : Err<Unit>() {
            override fun toString() = "Rslt.Err"
        }
        val isEmpty: Boolean get() = message.isEmpty()

        override fun toString(): String = "Rslt.Err($message)"
    }

    fun ok(): T? = (this as? Ok<T>)?.value

    fun err(): String? = (this as? Err<T>)?.message
}

fun <T> T.toOk() = Rslt.Ok(this)

fun <T> String.toErr() = Rslt.Err<T>(this)

fun <T, E : Throwable> E.toRslt() = Rslt.Err<T>(forHumans())

fun <T> Rslt<T>.unwrapOrNull(): T? = when (this) {
    is Rslt.Ok -> value
    is Rslt.Err -> null
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T> Rslt<T>.unwrapOr(value: T): T = when (this) {
    is Rslt.Ok -> this@unwrapOr.value
    is Rslt.Err -> value
}

inline fun <T> Rslt<T>.unwrapOrElse(action: (message: String) -> T): T = when (this) {
    is Rslt.Ok -> value
    is Rslt.Err -> action(message)
}

inline fun <T, R> Rslt<T>.map(map: (T) -> R): Rslt<R> = when (this) {
    is Rslt.Ok -> Rslt.Ok(map(value))
    is Rslt.Err -> Rslt.Err(message)
}

inline fun <T> Rslt<T>.ifOk(action: (T) -> Unit): Rslt<T> {
    ok()?.let { action(it) }
    return this
}

fun Throwable.forHumans() = "${this::class.simpleName}: $message"
