package com.radiocoche.radio.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.radiocoche.radio.tuner.CarLights
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Colores de día (contraste alto) o de noche (normales).
 *
 * En esta unidad no hemos encontrado ninguna señal de las luces del coche:
 * `persist.tw.theme.mode` vale siempre "night" (es el tema elegido en la app Tema de DoFun)
 * y el microcontrolador no avisa al encenderlas. Así que en modo Automático se decide por la
 * hora: noche desde 15 min antes de anochecer hasta 15 min después de amanecer (España).
 * El usuario puede fijar Día o Noche en Configuración.
 */
object DayNight {
    const val ACTION = "com.tw.theme.day.night.change"

    /** Cada cuánto se vuelve a mirar mientras la app está a la vista. */
    const val POLL_MS = 2000L

    enum class Mode(val label: String) { AUTO("Auto"), DAY("Día"), NIGHT("Noche") }

    var mode by mutableStateOf(Mode.AUTO)
        private set

    // Centro de la península: suficiente para calcular amanecer y anochecer con unos minutos de margen.
    private const val LAT = 40.42
    private const val LON = -3.70

    fun load(context: Context) {
        val name = context.getSharedPreferences("radio", Context.MODE_PRIVATE).getString("colorMode", Mode.AUTO.name)
        mode = Mode.entries.firstOrNull { it.name == name } ?: Mode.AUTO
        prefs = context.getSharedPreferences("radio", Context.MODE_PRIVATE)
        lastKnown = prefs?.takeIf { it.contains("lightsOn") }?.getBoolean("lightsOn", false)
        CarLights.setListener { onLights(it) }
    }

    fun setMode(context: Context, m: Mode) {
        mode = m
        context.getSharedPreferences("radio", Context.MODE_PRIVATE).edit().putString("colorMode", m.name).apply()
        apply()
    }

    /**
     * Decisión del usuario: con las luces apagadas, contraste alto; con las luces encendidas,
     * los colores oscuros normales. Las luces llegan del microcontrolador (mensaje 0x204).
     * Mientras no se sepa, se usa el último estado conocido; y si no hay ninguno, contraste.
     */
    private var lastKnown: Boolean? = null
    private var prefs: android.content.SharedPreferences? = null

    fun isNight(): Boolean = CarLights.on ?: lastKnown ?: false

    /** Se llama al recibir el estado de las luces. */
    fun onLights(on: Boolean) {
        lastKnown = on
        prefs?.edit()?.putBoolean("lightsOn", on)?.apply()
        apply()
    }

    fun apply() {
        val next = if (isNight()) NightPalette else ContrastPalette
        if (C.palette !== next) C.palette = next
    }

    /** Texto para la pantalla de diagnóstico. */
    fun describe(): String {
        val l = when (CarLights.on) { true -> "encendidas"; false -> "apagadas"; null -> "sin dato (último: $lastKnown)" }
        return "luces $l · ${if (isNight()) "colores normales" else "contraste alto"}"
    }

    private fun sunIsDown(): Boolean {
        val (rise, set) = sunTimesUtcMinutes()
        val now = (System.currentTimeMillis() / 60000.0) % 1440.0
        return now < rise + 15 || now > set - 15
    }

    /** Amanecer y anochecer de hoy, en minutos desde medianoche UTC (fórmula de la NOAA). */
    private fun sunTimesUtcMinutes(): Pair<Double, Double> {
        val day = Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.DAY_OF_YEAR)
        val g = 2 * Math.PI / 365.0 * (day - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val lat = Math.toRadians(LAT)
        val ha = Math.toDegrees(acos(cos(Math.toRadians(90.833)) / (cos(lat) * cos(decl)) - tan(lat) * tan(decl)))
        val rise = 720 - 4 * (LON + ha) - eqTime
        val set = 720 - 4 * (LON - ha) - eqTime
        return rise to set
    }

    private fun local(utcMinutes: Double): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        cal.add(Calendar.MINUTE, utcMinutes.toInt())
        val fmt = java.text.SimpleDateFormat("HH:mm", Locale.US)
        fmt.timeZone = TimeZone.getDefault()
        return fmt.format(cal.time)
    }
}
