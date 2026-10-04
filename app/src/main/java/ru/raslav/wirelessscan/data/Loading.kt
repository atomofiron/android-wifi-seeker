package ru.raslav.wirelessscan.data

sealed class Loading<T>(
    val progress: Boolean = false,
) {
    data class Progress<T>(val value: Float?) : Loading<T>(progress = true)
    data class Finished<T>(val data: T) : Loading<T>()
    data class Error<T>(val message: String) : Loading<T>()

    companion object {
        operator fun <T> invoke(progress: Float? = null) = Progress<T>(progress)
        operator fun <T> invoke(message: String) = Error<T>(message)
    }
}