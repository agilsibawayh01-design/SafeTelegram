package com.safetelegram.guard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.appcompat.app.AppCompatActivity
import com.safetelegram.guard.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.openSettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val active = isAccessibilityServiceEnabled()
        binding.statusBadge.text = if (active) {
            getString(R.string.status_active)
        } else {
            getString(R.string.status_inactive)
        }
        binding.statusBadge.setBackgroundColor(
            resources.getColor(
                if (active) R.color.accent_ok else R.color.accent_warn,
                theme
            )
        )
    }

    /**
     * Checks Settings.Secure ENABLED_ACCESSIBILITY_SERVICES for our service's
     * component name, which is the reliable way to know whether the user has
     * granted the accessibility permission (there is no other API for this).
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = "$packageName/${TelegramGuardService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedComponent, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
