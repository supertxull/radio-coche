package com.radiocoche.radio.tuner

import com.radiocoche.radio.model.Band
import com.radiocoche.radio.model.BandConfig
import com.radiocoche.radio.model.RdsMemo
import com.radiocoche.radio.model.StationInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * Sintonizador de prueba: no suena, pero se comporta como uno real
 * (emisoras, RDS, búsqueda) para poder probar la interfaz en la unidad.
 */
class SimulatedTuner : Tuner {

    private data class Sim(
        val freq: Int,
        val ps: String? = null,
        val rt: String? = null,
        val pty: String? = null,
        val weak: Boolean = false,
    )

    private val fm = listOf(
        Sim(88200, pty = "Música pop"),
        Sim(89900, pty = "Música pop"),
        Sim(91000, pty = "Música pop"),
        Sim(91400, pty = "Cultura"),
        Sim(91700, pty = "Música ligera"),
        Sim(92400, pty = "Deportes"),
        Sim(93500, pty = "Música rock"),
        Sim(94800, ps = "COPE", pty = "Noticias"),
        Sim(96900, pty = "Música rock"),
        Sim(97200, weak = true),
        Sim(98600, pty = "Clásica seria"),
        Sim(99100, ps = "ESRADIO2", pty = "Información"),
        Sim(101300, pty = "Música pop"),
        Sim(102700, pty = "Deportes"),
        Sim(103500, pty = "Noticias"),
        Sim(104900, pty = "Música ligera"),
        Sim(105500, weak = true),
        Sim(106300, ps = "COPE", rt = "Cope Madrid 106.3 · Y más Cope en 94.8 FM", pty = "Noticias"),
    )
    private val am = listOf(585, 639, 702, 846, 954, 1026, 1179, 1287).map { Sim(it) }

    private val _status = MutableStateFlow(TunerStatus())
    override val status: StateFlow<TunerStatus> = _status

    private var mono = false
    private var loc = true

    private fun all(band: Band) = if (band == Band.FM) fm else am
    private fun receivable(band: Band) = all(band).filter { !(loc && it.weak) }

    override fun tune(band: Band, freq: Int) {
        val tol = if (band == Band.FM) 40 else 4
        val s = all(band).firstOrNull { abs(it.freq - freq) <= tol }
        _status.value = TunerStatus(
            band = band,
            freq = freq,
            info = s?.let { StationInfo(it.ps, it.rt, it.pty, stereo = band == Band.FM && !mono && !it.weak) },
        )
    }

    override suspend fun seek(cfg: BandConfig, from: Int, up: Boolean): Int? {
        delay(200)
        val list = receivable(cfg.band).map { it.freq }.filter { it in cfg.min..cfg.max }.sorted()
        if (list.isEmpty()) return null
        val half = cfg.step / 2
        return if (up) list.firstOrNull { it > from + half } ?: list.first()
        else list.lastOrNull { it < from - half } ?: list.last()
    }

    override suspend fun scan(cfg: BandConfig, onProgress: (freq: Int, found: Int) -> Unit): List<Int> {
        val list = receivable(cfg.band).map { it.freq }.filter { it in cfg.min..cfg.max }.sorted()
        var f = cfg.min
        while (f <= cfg.max) {
            tune(cfg.band, f)
            onProgress(f, list.count { it <= f })
            delay(40)
            f += cfg.minor
        }
        return list
    }

    override fun setMono(on: Boolean) {
        mono = on
        val s = _status.value
        tune(s.band, s.freq)
    }

    override fun setLoc(on: Boolean) {
        loc = on
    }

    override fun setAf(on: Boolean) = Unit
    override fun setTa(on: Boolean) = Unit
    override fun setReg(on: Boolean) = Unit
    override fun release() = Unit

    /** Solo para la simulación: nombres y tipos para las presintonías de ejemplo. */
    fun knownRds(): Map<Int, RdsMemo> =
        fm.filter { it.ps != null || it.pty != null }.associate { it.freq to RdsMemo(it.ps, it.pty) }
}
