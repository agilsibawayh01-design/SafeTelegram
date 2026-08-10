package com.safetelegram.guard.presentation

import android.content.ContentResolver
import android.provider.Settings
import android.text.TextUtils
import androidx.lifecycle.ViewModel
import com.safetelegram.guard.service.TelegramGuardService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Presentation-layer state for the status screen. Holds no Android UI
 * references (MVVM) so it survives configuration changes and is unit
 * testable with a fake ContentResolver-backed check if desired.
 */
class AccessibilityStatusViewModel : ViewModel() {

    private val _isServiceEnabled = MutableStateFlow(false)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    /**
     * There is no push notification for "accessibility service was
     * enabled/disabled" — the only reliable API is reading
     * Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, which is why callers
     * re-invoke this from onResume() rather than observing a callback.
     */
    fun refresh(contentResolver: ContentResolver, packageName: String) {
        val expectedComponent = "$packageName/${TelegramGuardService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        val enabled = if (enabledServices.isNullOrEmpty()) {
            false
        } else {
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            var found = false
            while (splitter.hasNext()) {
                if (splitter.next().equals(expectedComponent, ignoreCase = true)) {
                    found = true
                    break
                }
            }
            found
        }

        _isServiceEnabled.value = enabled
    }
}
