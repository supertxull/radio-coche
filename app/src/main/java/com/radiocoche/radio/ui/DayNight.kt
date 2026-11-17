package com.radiocoche.radio.ui

import android.content.Context
import android.content.SharedPreferences
import com.radiocoche.radio.tuner.CarLights

/**
 * Colores según las luces del coche (las avisa el microcontrolador con el mensaje 0x204):
 * luces apagadas = contraste alto; luces encendidas = los colores oscuros normales.
 * Mientras no llega el aviso se usa el último estado conocido; si no hay ninguno, contraste.
 */
object DayNight {
    private var lastKnown: Boolean? = null
    private var prefs: SharedPreferences? = null

    fun load(context: Context) {
        prefs = context.getSharedPreferences("radio", Context.MODE_PRIVATE)
        lastKnown = prefs?.takeIf { it.contains("lightsOn") }?.getBoolean("lightsOn", false)
        CarLights.setListener { onLights(it) }
    }

    fun isNight(): Boolean = CarLights.on ?: lastKnown ?: false

    private fun onLights(on: Boolean) {
        lastKnown = on
        prefs?.edit()?.putBoolean("lightsOn", on)?.apply()
        apply()
    }

    fun apply() {
        val next = if (isNight()) NightPalette else ContrastPalette
        if (C.palette !== next) C.palette = next
    }
}
