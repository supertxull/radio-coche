package com.radiocoche.radio.tuner

import com.radiocoche.radio.model.Band
import com.radiocoche.radio.model.BandConfig
import com.radiocoche.radio.model.StationInfo
import kotlinx.coroutines.flow.StateFlow

data class TunerStatus(
    val band: Band = Band.FM,
    val freq: Int = 0,
    val info: StationInfo? = null,
)

/**
 * Todo lo que la app necesita del sintonizador.
 *
 * Ahora mismo se usa [SimulatedTuner]. Cuando sepamos cómo se controla el sintonizador
 * de la unidad Witson, se creará otra clase que implemente esta interfaz y la interfaz
 * gráfica no tendrá que cambiar.
 */
interface Tuner {
    val status: StateFlow<TunerStatus>

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

    fun release()
}
