package com.example.cardscanner.security

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicLong

/**
 * App-lock session state (spec §5): locked at launch, unlocked by PIN or
 * biometrics, auto-locks after the configured inactivity window.
 *
 * The timeout is driven by [onUserActivity] pings from the UI (any touch or
 * navigation), which keeps the implementation simple and correct across
 * process death — the lock state itself is never persisted.
 */
class SessionManager(private val pinManager: PinManager) {

    var locked by mutableStateOf(true)
        private set

    var lockTimeoutMinutes by mutableStateOf(5)
        private set

    private val lastActivity = AtomicLong(System.currentTimeMillis())

    val needsSetup: Boolean
        get() = !pinManager.hasPin()

    fun unlock() {
        locked = false
        touch()
    }

    fun lock() {
        locked = true
    }

    fun setTimeout(minutes: Int) {
        lockTimeoutMinutes = minutes.coerceIn(1, 30)
    }

    /** UI calls this on every meaningful interaction. */
    fun touch() {
        lastActivity.set(System.currentTimeMillis())
        maybeAutoLock()
    }

    /** Called periodically (UI ticker) to enforce the inactivity window. */
    fun maybeAutoLock() {
        if (locked) return
        val idleMs = System.currentTimeMillis() - lastActivity.get()
        if (idleMs > lockTimeoutMinutes * 60_000L) {
            locked = true
        }
    }

    fun verifyPin(pin: CharArray): Boolean = pinManager.verify(pin)

    fun setupPin(pin: CharArray): Boolean = pinManager.setPin(pin)
}
