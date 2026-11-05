package com.radiocoche.radio.tuner

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import com.radiocoche.radio.model.Band
import com.radiocoche.radio.model.BandConfig
import com.radiocoche.radio.model.PTY_TYPES
import com.radiocoche.radio.model.StationInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.lang.reflect.Method

/**
 * Sintonizador real de las unidades TopWay (TS10, como la Witson).
 *
 * El sintonizador TDA7708 lo maneja el microcontrolador de la unidad. Android habla con él
 * mediante la clase de sistema `android.tw.john.TWUtil`, que solo existe en estas unidades;
 * por eso se usa por reflexión. Los códigos de los mensajes se han sacado de la app de
 * radio de fábrica (`com.tw.radio`).
 *
 * Unidades: FM en pasos de 10 kHz (10630 = 106.30 MHz), AM en kHz.
 * Bandas del microcontrolador: 0 = FM1, 1 = FM2, 2 = AM.
 */
class TopWayTuner private constructor(
    private val tw: Any,
    private val write2: Method,
    private val write3: Method,
    private val closeFns: List<Method>,
) : Tuner {

    companion object {
        private const val TAG = "TopWayTuner"

        private const val MSG_RADIO = 0x401     // estado, frecuencia, banda, órdenes de sintonía
        private const val MSG_PRESET = 0x402    // presintonías / sintonizar una frecuencia
        private const val MSG_RDS = 0x404       // RDS: AF, TA, REG, PTY
        private const val MSG_RADIOTEXT = 0x405 // RadioText
        private const val MSG_ACTIVITY = 0x9E00 // aviso de app de radio en primer plano
        private const val MSG_KEYS = 0x9E01     // teclas del volante / frontal
        private const val MSG_SOURCE = 0x9E11   // fuente de audio

        private val LISTEN = intArrayOf(
            0x109, 0x10A, 0x112, 0x201, 0x203, 0x301,
            MSG_RADIO, MSG_PRESET, MSG_RDS, MSG_RADIOTEXT, 0x406, MSG_ACTIVITY, MSG_KEYS,
        )

        /** Devuelve el sintonizador real, o null si la app no está en una unidad TopWay. */
        fun create(): TopWayTuner? = try {
            val cls = Class.forName("android.tw.john.TWUtil")
            val tw = cls.getConstructor(Int::class.javaPrimitiveType).newInstance(1)
            val ids = ShortArray(LISTEN.size) { LISTEN[it].toShort() }
            val result = cls.getMethod("open", ShortArray::class.java).invoke(tw, ids) as? Int ?: 0
            if (result != 0) {
                Log.w(TAG, "TWUtil.open devolvió $result")
                null
            } else {
                cls.getMethod("start").invoke(tw)
                val int = Int::class.javaPrimitiveType
                TopWayTuner(
                    tw = tw,
                    write2 = cls.getMethod("write", int, int),
                    write3 = cls.getMethod("write", int, int, int),
                    closeFns = listOfNotNull(
                        runCatching { cls.getMethod("stop") }.getOrNull(),
                        runCatching { cls.getMethod("close") }.getOrNull(),
                    ),
                ).also { t ->
                    cls.getMethod("addHandler", String::class.java, Handler::class.java).invoke(tw, "RadioCoche", t.handler)
                    t.requestState()
                }
            }
        } catch (e: Throwable) {
            Log.i(TAG, "No es una unidad TopWay: ${e.message}")
            null
        }
    }

    override val isReal: Boolean get() = true

    private val _status = MutableStateFlow(TunerStatus())
    override val status: StateFlow<TunerStatus> = _status

    private var mcuBand = 0
    private var ps: String? = null
    private var radioText: String? = null
    private var pty: String? = null
    private var stereo = false
    private var hardKeys: ((HardKey) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper()) { msg -> onMessage(msg); true }

    private fun w(what: Int, arg: Int) {
        runCatching { write2.invoke(tw, what, arg) }.onFailure { Log.w(TAG, "write($what,$arg)", it) }
    }

    private fun w(what: Int, a1: Int, a2: Int) {
        runCatching { write3.invoke(tw, what, a1, a2) }.onFailure { Log.w(TAG, "write($what,$a1,$a2)", it) }
    }

    /** Pide al microcontrolador que envíe el estado actual (lo mismo que hace la app de fábrica al abrir). */
    private fun requestState() {
        w(0x109, 255); w(0x10A, 255); w(0x301, 255); w(0x112, 255)
        w(0x406, 0); w(MSG_RADIO, 255); w(MSG_RDS, 255); w(0x203, 255)
    }

    private fun toMcu(band: Band, freqKhz: Int) = if (band == Band.FM) freqKhz / 10 else freqKhz
    private fun fromMcu(band: Band, value: Int) = if (band == Band.FM) value * 10 else value
    private fun bandOf(mcu: Int) = if (mcu == 2) Band.AM else Band.FM

    private fun text(obj: Any?): String? = when (obj) {
        is String -> obj
        is ByteArray -> String(obj)
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

    private fun publish() {
        _status.update { s ->
            s.copy(info = StationInfo(ps = ps, radioText = radioText, pty = pty, stereo = stereo))
        }
    }

    private fun onMessage(msg: Message) {
        when (msg.what) {
            MSG_RADIO -> when (msg.arg1) {
                0 -> { // indicadores: bit 2 = estéreo, bit 4 = mono forzado, bit 8 = LOC
                    val f = msg.arg2
                    stereo = f and 0x2 != 0
                    _status.update { it.copy(mono = f and 0x4 != 0, loc = f and 0x8 != 0) }
                    publish()
                }
                1 -> { // banda
                    mcuBand = msg.arg2
                    _status.update { it.copy(band = bandOf(mcuBand)) }
                }
                2 -> { // frecuencia (+ nombre PS)
                    val band = bandOf(mcuBand)
                    val newFreq = fromMcu(band, msg.arg2)
                    if (newFreq != _status.value.freq) { radioText = null; pty = null }
                    ps = text(msg.obj)
                    _status.update { it.copy(band = band, freq = newFreq) }
                    publish()
                }
                3 -> { // tipo de programa (PTY)
                    pty = PTY_TYPES.getOrNull(msg.arg2 - 1)
                    publish()
                }
            }
            MSG_RDS -> { // bit 64 = AF, bit 32 = TA, bit 4 = REG
                val f = msg.arg1 and 0xFFFF
                _status.update { it.copy(af = f and 64 != 0, ta = f and 32 != 0, reg = f and 4 != 0) }
            }
            MSG_RADIOTEXT -> {
                radioText = text(msg.obj)
                publish()
            }
            MSG_KEYS -> when (msg.arg1) {
                3 -> hardKeys?.invoke(HardKey.NEXT_PRESET)
                4 -> hardKeys?.invoke(HardKey.PREV_PRESET)
                9 -> hardKeys?.invoke(HardKey.SEEK_DOWN)
                10 -> hardKeys?.invoke(HardKey.SEEK_UP)
            }
        }
    }

    override fun tune(band: Band, freq: Int) {
        val target = if (band == Band.AM) 2 else 0
        if (bandOf(mcuBand) != band) {
            w(MSG_RADIO, 5, target)
            mcuBand = target
        }
        w(MSG_PRESET, 255, toMcu(band, freq))
        w(MSG_SOURCE, 192, 1)
    }

    /** Espera a que la frecuencia deje de moverse tras una búsqueda. */
    private suspend fun waitSettled(timeoutMs: Long = 15000): Int {
        val start = System.currentTimeMillis()
        var last = _status.value.freq
        var lastChange = System.currentTimeMillis()
        var moved = false
        while (System.currentTimeMillis() - start < timeoutMs) {
            delay(100)
            val f = _status.value.freq
            if (f != last) { last = f; lastChange = System.currentTimeMillis(); moved = true }
            val quiet = System.currentTimeMillis() - lastChange
            if (moved && quiet > 900) break
            if (!moved && quiet > 2500) break
        }
        return last
    }

    override suspend fun seek(cfg: BandConfig, from: Int, up: Boolean): Int? {
        w(MSG_RADIO, 1, if (up) 1 else 0)
        val f = waitSettled()
        return f.takeIf { it in cfg.min..cfg.max }
    }

    override suspend fun scan(cfg: BandConfig, onProgress: (freq: Int, found: Int) -> Unit): List<Int> {
        val found = mutableListOf<Int>()
        tune(cfg.band, cfg.min)
        delay(400)
        var previous = cfg.min
        repeat(40) {
            w(MSG_RADIO, 1, 1)
            val f = waitSettled()
            if (f <= previous || f !in cfg.min..cfg.max) return found.sorted()
            if (found.none { kotlin.math.abs(it - f) <= cfg.step / 2 }) found += f
            onProgress(f, found.size)
            previous = f
        }
        return found.sorted()
    }

    override fun setMono(on: Boolean) = w(MSG_RADIO, 6, if (on) 1 else 0)
    override fun setLoc(on: Boolean) = w(MSG_RADIO, 4, if (on) 1 else 0)
    override fun setAf(on: Boolean) = w(MSG_RDS, 0, if (on) 1 else 0)
    override fun setTa(on: Boolean) = w(MSG_RDS, 1, if (on) 1 else 0)
    override fun setReg(on: Boolean) = w(MSG_RDS, 3, if (on) 1 else 0)

    override fun setForeground(on: Boolean) {
        if (on) {
            w(MSG_SOURCE, 192, 1) // pide el audio de la radio
            w(MSG_ACTIVITY, 1)
            requestState()
        } else {
            w(MSG_ACTIVITY, 129)
        }
    }

    override fun setHardKeyListener(listener: ((HardKey) -> Unit)?) {
        hardKeys = listener
    }

    override fun release() {
        runCatching {
            tw.javaClass.getMethod("removeHandler", String::class.java).invoke(tw, "RadioCoche")
        }
        closeFns.forEach { fn -> runCatching { fn.invoke(tw) } }
    }
}
