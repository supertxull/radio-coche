package com.radiocoche.radio.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Banda del sintonizador. Las frecuencias se guardan siempre en kHz (FM 106.3 MHz = 106300). */
enum class Band { FM, AM }

/** Bancos de presintonías. FM1 y FM2 comparten la misma banda FM. */
enum class Bank(val label: String, val band: Band) {
    FM1("FM1", Band.FM),
    FM2("FM2", Band.FM),
    AM("AM", Band.AM);

    fun next(): Bank = entries[(ordinal + 1) % entries.size]
}

enum class Region(val label: String, val fmStep: Int, val amStep: Int, val range: String) {
    EUROPA("EUR", 100, 9, "FM 87,5–108 MHz · AM 531–1602 kHz"),
    AMERICA("USA", 200, 10, "FM 87,5–107,9 MHz · AM 530–1710 kHz"),
    JAPON("JAP", 100, 9, "FM 76–95 MHz · AM 522–1629 kHz"),
}

data class Settings(
    val region: Region = Region.EUROPA,
    val fmStep: Int = 100,
    val amStep: Int = 9,
    val loc: Boolean = true,
    val taDefault: Boolean = false,
    val afDefault: Boolean = true,
    val regDefault: Boolean = false,
    val taVolume: Int = 18,
)

data class BandConfig(
    val band: Band,
    val min: Int,
    val max: Int,
    val step: Int,
    /** Separación entre las marcas pequeñas del dial (y paso de la búsqueda automática). */
    val minor: Int,
) {
    val unit: String get() = if (band == Band.FM) "MHz" else "kHz"
    private val tolerance: Int get() = step / 2

    fun clamp(f: Int): Int {
        val c = f.coerceIn(min, max)
        val n = ((c - min).toDouble() / step).roundToInt()
        return (min + n * step).coerceIn(min, max)
    }

    fun fraction(f: Int): Float = ((f - min).toFloat() / (max - min)).coerceIn(0f, 1f)

    fun at(fraction: Float): Int = clamp((min + fraction.coerceIn(0f, 1f) * (max - min)).roundToInt())

    fun format(f: Int): String = formatFreq(band, f)

    /** Seis etiquetas repartidas por el dial, como en la radio original. */
    val labels: List<Int> get() = (0..5).map { i -> min + (max - min) * i / 5 }

    fun same(a: Int?, b: Int): Boolean = a != null && abs(a - b) <= tolerance
}

fun formatFreq(band: Band, f: Int): String =
    if (band == Band.FM) {
        if (f % 100 == 0) String.format(Locale.US, "%.1f", f / 1000.0)
        else String.format(Locale.US, "%.2f", f / 1000.0)
    } else {
        f.toString()
    }

fun configFor(band: Band, s: Settings): BandConfig = when (s.region) {
    Region.EUROPA ->
        if (band == Band.FM) BandConfig(band, 87500, 108000, s.fmStep, 500)
        else BandConfig(band, 531, 1602, s.amStep, 27)
    Region.AMERICA ->
        if (band == Band.FM) BandConfig(band, 87500, 107900, s.fmStep, 500)
        else BandConfig(band, 530, 1710, s.amStep, 30)
    Region.JAPON ->
        if (band == Band.FM) BandConfig(band, 76000, 95000, s.fmStep, 500)
        else BandConfig(band, 522, 1629, s.amStep, 27)
}

/** Datos que llegan de la emisora sintonizada (RDS). */
data class StationInfo(
    val ps: String?,        // Nombre corto (PS), p. ej. "COPE"
    val radioText: String?, // RadioText (RT)
    val pty: String?,       // Tipo de programa
    val stereo: Boolean,
)

/** Lo que recordamos de cada frecuencia para poner nombre y tipo a las presintonías. */
data class RdsMemo(val ps: String?, val pty: String?)

/** Tipos de programa RDS (Europa), sin los de alarma. */
val PTY_TYPES = listOf(
    "Noticias", "Actualidad", "Información", "Deportes", "Educación",
    "Teatro", "Cultura", "Ciencia", "Variedades", "Música pop",
    "Música rock", "Música ligera", "Clásica ligera", "Clásica seria", "Otra música",
    "Meteorología", "Economía", "Infantil", "Social", "Religión",
    "Participación", "Viajes", "Ocio", "Jazz", "Country",
    "Música nacional", "Oldies", "Folk", "Documentales",
)
