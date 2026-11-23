package com.radiocoche.radio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.radiocoche.radio.data.Storage
import com.radiocoche.radio.model.Band
import com.radiocoche.radio.model.Bank
import com.radiocoche.radio.model.configFor
import com.radiocoche.radio.tuner.TopWayTuner
import com.radiocoche.radio.ui.UiState

/**
 * Recibe las órdenes del launcher (aviso [LauncherBridge.CMD]).
 *
 * Está declarado en el manifiesto para que funcione también con la Radio cerrada:
 * en ese caso lee las presintonías guardadas, sintoniza directamente en el MCU y
 * contesta al launcher con el estado nuevo.
 */
class LauncherCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: "query"
        val index = intent.getIntExtra("index", -1)

        // Silencio durante los avisos de radar del launcher.
        if (cmd == "mute" || cmd == "unmute") {
            runCatching { RadioAudio.fromLauncher(context.applicationContext, cmd == "mute") }
            return
        }

        // La pantalla de la Radio existe: que lo haga ella (así también se actualiza).
        LauncherBridge.handler?.let { it(cmd, index); return }

        // Con la Radio cerrada hay que mantener viva la conexión unos segundos: el cambio de banda
        // y la frecuencia se mandan por separado (la frecuencia, 0,7 s después).
        val pending = goAsync()
        val ok = runCatching { handleClosed(context.applicationContext, cmd, index) { pending.finish() } }.isSuccess
        if (!ok) pending.finish()
    }

    private fun handleClosed(ctx: Context, cmd: String, index: Int, done: () -> Unit) {
        val storage = Storage(ctx)
        val bank = storage.bank
        val presets = Bank.entries.associateWith { storage.presets(it) }
        var state = UiState(
            bank = bank,
            fm = storage.fm,
            am = storage.am,
            presets = presets,
            settings = storage.settings(),
            rds = storage.rds(),
        )

        val target: Int? = when (cmd) {
            "next" -> step(state, 1)
            "prev" -> step(state, -1)
            "preset" -> index.takeIf { state.list.getOrNull(it) != null }
            else -> null
        }

        var keepOpen = false
        if (target != null) {
            val freq = state.list[target] ?: run { done(); return }
            val tuner = TopWayTuner.create()
            if (tuner != null) {
                tuner.tune(bank.band, freq)
                // La conexión se cierra cuando ya ha dado tiempo a mandar la frecuencia.
                keepOpen = true
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    runCatching { tuner.release() }
                    done()
                }, 3000)
            }
            if (bank.band == Band.FM) storage.fm = freq else storage.am = freq
            state = state.copy(
                fm = if (bank.band == Band.FM) freq else state.fm,
                am = if (bank.band == Band.AM) freq else state.am,
                selected = target,
            )
        }

        LauncherBridge.send(ctx, state, front = false, force = true)
        if (!keepOpen) done()
    }

    /** Presintonía siguiente o anterior entre las que tienen emisora. */
    private fun step(u: UiState, dir: Int): Int? {
        val filled = u.list.indices.filter { u.list[it] != null }
        if (filled.isEmpty()) return null
        val cfg = configFor(u.band, u.settings)
        val current = u.list.indexOfFirst { cfg.same(it, u.freq) }
        val pos = filled.indexOf(current)
        val next = when {
            pos >= 0 -> (pos + dir + filled.size) % filled.size
            dir > 0 -> 0
            else -> filled.size - 1
        }
        return filled[next]
    }
}
