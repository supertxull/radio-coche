package com.radiocoche.radio.tuner

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Estado de las luces del coche. Lo envía el microcontrolador en el mensaje 0x204
 * (arg1 = 1 al encenderlas, 0 al apagarlas), encontrado con «Escuchar luces» el 2026-11-16.
 * null = todavía no se sabe.
 */
object CarLights {
    var on by mutableStateOf<Boolean?>(null)
        private set

    private var listener: ((Boolean) -> Unit)? = null

    /** Para reenviar el aviso al launcher y a Música (por si su propia conexión no les llega). */
    var app: Context? = null

    fun set(value: Boolean) {
        on = value
        listener?.invoke(value)
        app?.let { ctx ->
            for (p in listOf("com.radiocoche.launcher", "com.radiocoche.musica")) {
                runCatching { ctx.sendBroadcast(Intent("com.radiocoche.LIGHTS").setPackage(p).putExtra("on", value)) }
            }
        }
    }

    fun setListener(l: ((Boolean) -> Unit)?) { listener = l }
}
