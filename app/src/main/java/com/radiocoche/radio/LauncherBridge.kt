package com.radiocoche.radio

import android.content.Context
import android.content.Intent
import com.radiocoche.radio.ui.UiState

/**
 * Enlace con el launcher del coche (com.radiocoche.launcher).
 *
 * La Radio le envía su estado con el aviso [STATE] cada vez que cambia, y obedece las
 * órdenes que llegan con [CMD]: "query" (reenviar el estado), "prev" / "next"
 * (presintonía anterior / siguiente) y "preset" + "index" (elegir una presintonía).
 */
object LauncherBridge {
    const val STATE = "com.radiocoche.radio.STATE"
    const val CMD = "com.radiocoche.radio.CMD"
    private const val LAUNCHER = "com.radiocoche.launcher"

    /** Lo que ve el launcher; si no cambia, no se vuelve a enviar. */
    private var last: List<Any?>? = null

    fun send(ctx: Context, u: UiState, front: Boolean, force: Boolean = false) {
        val cfg = u.cfg
        val name = u.info?.ps?.takeIf { it.isNotBlank() } ?: u.psOf(u.freq) ?: ""
        val text = u.info?.radioText ?: ""
        val pFreq = u.list.map { f -> f?.let { cfg.format(it) } ?: "" }
        val pName = u.list.map { f -> f?.let { u.psOf(it) } ?: "" }
        val key = listOf(u.bank, u.freq, name, text, u.currentIndex, pFreq, pName, front)
        if (!force && key == last) return
        last = key

        val intent = Intent(STATE).setPackage(LAUNCHER)
            .putExtra("bank", u.bank.label)
            .putExtra("freq", cfg.format(u.freq))
            .putExtra("unit", cfg.unit)
            .putExtra("name", name)
            .putExtra("text", text)
            .putExtra("pos", cfg.fraction(u.freq))
            .putExtra("labels", cfg.labels.map { cfg.format(it) }.toTypedArray())
            .putExtra("preset", u.currentIndex)
            .putExtra("pFreq", pFreq.toTypedArray())
            .putExtra("pName", pName.toTypedArray())
            .putExtra("front", front)
        runCatching { ctx.sendBroadcast(intent) }
    }
}
