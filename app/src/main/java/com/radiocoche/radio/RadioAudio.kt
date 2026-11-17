package com.radiocoche.radio

import android.content.Context
import com.radiocoche.radio.tuner.TopWayTuner

/**
 * Silencio de la radio durante los avisos de radar del launcher.
 *
 * El launcher envía "mute" al empezar los pitidos y "unmute" al acabar (solo si lo último que se usó
 * es la Radio). La Radio quita y devuelve su audio al chip con TWUtil.
 *
 * Importante (Radio 0.21): la Radio NO pide el foco de sonido de Android. En la 0.20 lo pedía y el
 * sistema de TopWay, al ver una app normal con el foco, cambiaba la entrada de sonido a «Android» y la
 * radio se quedaba muda hasta salir de la pantalla.
 */
object RadioAudio {

    /** Lo pone la pantalla de la Radio mientras existe: usa su propio sintonizador. */
    @Volatile
    var live: ((on: Boolean) -> Unit)? = null

    /** Orden "mute" / "unmute" del launcher. */
    fun fromLauncher(ctx: Context, mute: Boolean) {
        live?.let { it(!mute); return }
        val tuner = TopWayTuner.create() ?: return
        try {
            tuner.setRadioAudio(!mute)
        } finally {
            tuner.release()
        }
    }
}
