package com.example.cardscanner.security

import android.view.Window

/**
 * Runtime guards applied to screens that display or collect card-derived data.
 */
object SensitiveDataGuard {

    /**
     * Blocks screenshots and screen recording (FLAG_SECURE) on the scanner,
     * confirmation and edit surfaces.
     */
    fun applySecureFlag(window: Window) {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }
}
