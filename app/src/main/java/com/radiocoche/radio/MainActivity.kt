package com.radiocoche.radio

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.radiocoche.radio.ui.DayNight
import com.radiocoche.radio.ui.RadioScreen
import com.radiocoche.radio.ui.RadioViewModel
import com.radiocoche.radio.ui.ScaledRoot
import com.radiocoche.radio.ui.Screen
import com.radiocoche.radio.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val vm: RadioViewModel by viewModels()
    private val mainHandler = Handler(Looper.getMainLooper())


    private var inFront = false

    /** Órdenes del launcher (llegan a LauncherCommandReceiver): reenviar el estado o cambiar de presintonía. */
    private fun onLauncherCommand(cmd: String, index: Int) {
        when (cmd) {
            "mute", "unmute" -> RadioAudio.fromLauncher(this, cmd == "mute")
            "prev" -> vm.stepPreset(-1)
            "next" -> vm.stepPreset(1)
            "preset" -> index.takeIf { it >= 0 }?.let { vm.selectPreset(it) }
            else -> LauncherBridge.send(this, vm.ui.value, inFront, force = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DayNight.load()
        DayNight.apply()
        LauncherBridge.handler = { cmd, index -> runOnUiThread { onLauncherCommand(cmd, index) } }
        RadioAudio.live = { on -> runOnUiThread { vm.setRadioAudio(on) } }
        lifecycleScope.launch {
            vm.ui.collect { LauncherBridge.send(this@MainActivity, it, inFront) }
        }
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

    override fun onResume() {
        super.onResume()
        DayNight.apply()
        vm.onForeground(true)
        RadioAudio.take(this)
        inFront = true
        LauncherBridge.send(this, vm.ui.value, front = true, force = true)
    }

    override fun onDestroy() {
        LauncherBridge.handler = null
        RadioAudio.live = null
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onPause() {
        inFront = false
        vm.onForeground(false)
        super.onPause()
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
        const val EQ_PACKAGE = "com.tw.eq"
    }
}
