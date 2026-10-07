package ru.raslav.wirelessscan.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MutexLocker<T>(private val target: T) {

    private val mutex = Mutex()

    suspend operator fun <R> invoke(action: suspend T.() -> R): R {
        return mutex.withLock {
            target.action()
        }
    }
}
