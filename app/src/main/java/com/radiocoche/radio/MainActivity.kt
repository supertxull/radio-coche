package com.radiocoche.radio

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.radiocoche.radio.ui.RadioScreen
import com.radiocoche.radio.ui.RadioViewModel
import com.radiocoche.radio.ui.ScaledRoot
import com.radiocoche.radio.ui.Screen
import com.radiocoche.radio.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val vm: RadioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val s by vm.ui.collectAsState()
                ScaledRoot {
                    when (s.screen) {
                        Screen.RADIO -> RadioScreen(s, vm, onEqualizer = ::openEqualizer)
                        Screen.SETTINGS -> SettingsScreen(s, vm)
                    }
                }
                BackHandler(
                    enabled = s.screen == Screen.SETTINGS || s.ptyOpen || s.confirmScan ||
                        s.scanning || s.confirmDelete != null,
                ) { vm.onBack() }
            }
        }
    }

    /** Abre la app del ecualizador de la unidad. */
    private fun openEqualizer() {
        try {
            val intent = EQ_PACKAGE.takeIf { it.isNotEmpty() }?.let { packageManager.getLaunchIntentForPackage(it) }
                ?: Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                    .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            vm.toast("No se encuentra el ecualizador")
        }
    }

    companion object {
        /**
         * Paquete de la app del ecualizador de la unidad (p. ej. "com.xxx.eq").
         * Vacío = se abre el ecualizador estándar de Android, si la unidad tiene uno.
         */
        const val EQ_PACKAGE = ""
    }
}
