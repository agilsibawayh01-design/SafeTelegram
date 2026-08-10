package com.safetelegram.guard.presentation

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.safetelegram.guard.R
import com.safetelegram.guard.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: AccessibilityStatusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.openSettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isServiceEnabled.collect { enabled -> render(enabled) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Accessibility enablement has no callback API — re-check on every
        // resume (e.g. returning from Settings) as documented in the ViewModel.
        viewModel.refresh(contentResolver, packageName)
    }

    private fun render(active: Boolean) {
        binding.statusBadge.text = getString(
            if (active) R.string.status_active else R.string.status_inactive
        )
        binding.statusBadge.setBackgroundColor(
            getColor(if (active) R.color.accent_ok else R.color.accent_warn)
        )
    }
}
