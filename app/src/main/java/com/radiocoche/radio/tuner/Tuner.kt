package com.radiocoche.radio.tuner

import com.radiocoche.radio.model.Band
import com.radiocoche.radio.model.BandConfig
import com.radiocoche.radio.model.StationInfo
import kotlinx.coroutines.flow.StateFlow

data class TunerStatus(
    val band: Band = Band.FM,
    val freq: Int = 0,
    val info: StationInfo? = null,
    /** Estado de los ajustes que informa el propio sintonizador (null = no lo sabemos). */
    val loc: Boolean? = null,
    val mono: Boolean? = null,
    val af: Boolean? = null,
    val ta: Boolean? = null,
    val reg: Boolean? = null,
)

/** Botones físicos o del volante que llegan desde el microcontrolador. */
enum class HardKey { NEXT_PRESET, PREV_PRESET, SEEK_UP, SEEK_DOWN }

/**
 * Todo lo que la app necesita del sintonizador.
 *
 * - [TopWayTuner]: el sintonizador real de la unidad (plataforma TopWay TS10).
 * - [SimulatedTuner]: de prueba, cuando la app se ejecuta en otro aparato.
 */
interface Tuner {
    val status: StateFlow<TunerStatus>

    /** true si controla un sintonizador de verdad. */
    val isReal: Boolean get() = false

    fun tune(band: Band, freq: Int)

    /** Busca la siguiente emisora hacia arriba o hacia abajo. Devuelve su frecuencia o null. */
    suspend fun seek(cfg: BandConfig, from: Int, up: Boolean): Int?

    /** Recorre toda la banda y devuelve las emisoras encontradas, ordenadas. */
    suspend fun scan(cfg: BandConfig, onProgress: (freq: Int, found: Int) -> Unit): List<Int>

    fun setMono(on: Boolean)
    fun setLoc(on: Boolean)
    fun setAf(on: Boolean)
    fun setTa(on: Boolean)
    fun setReg(on: Boolean)

    /** La app pasa a primer plano (true) o deja de estarlo (false). */
    fun setForeground(on: Boolean) = Unit

    fun setHardKeyListener(listener: ((HardKey) -> Unit)?) = Unit

    /** Últimos mensajes intercambiados con el sintonizador (para diagnosticar). */
    fun debugLog(): List<String> = emptyList()

    fun release()
}
