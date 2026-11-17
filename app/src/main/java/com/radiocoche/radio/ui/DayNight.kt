package com.radiocoche.radio.ui

import com.radiocoche.radio.tuner.CarLights

/**
 * Colores según las luces del coche (las avisa el microcontrolador con el mensaje 0x204):
 * luces apagadas = contraste alto; luces encendidas = los colores oscuros normales.
 * Al arrancar siempre contraste alto, hasta que la unidad diga que las luces están encendidas.
 */
object DayNight {
    fun load() {
        CarLights.setListener { apply() }
    }

    fun isNight(): Boolean = CarLights.on == true

    fun apply() {
        val next = if (isNight()) NightPalette else ContrastPalette
        if (C.palette !== next) C.palette = next
    }
}
