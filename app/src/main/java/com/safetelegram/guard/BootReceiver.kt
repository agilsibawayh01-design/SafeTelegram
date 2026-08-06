package com.safetelegram.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Android automatically re-binds any AccessibilityService the user has
 * enabled every time the device boots — no action is required from app code
 * for FR-13 ("aplikasi harus berjalan otomatis setelah HP dinyalakan").
 *
 * This receiver exists only as a hook for future roadmap items (v2+, e.g. a
 * lightweight watchdog) and currently just logs, keeping current RAM/CPU
 * usage at effectively zero on boot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("SafeTelegramBoot", "Boot completed — AccessibilityService (if enabled) will be reconnected by the system.")
    }
}
