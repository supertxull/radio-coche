package com.radiocoche.radio.tuner

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

    fun set(value: Boolean) {
        on = value
        listener?.invoke(value)
    }

    fun setListener(l: ((Boolean) -> Unit)?) { listener = l }
}
