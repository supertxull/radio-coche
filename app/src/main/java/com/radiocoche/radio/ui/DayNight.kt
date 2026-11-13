package com.radiocoche.radio.ui

/**
 * Día / noche según las luces del coche.
 *
 * Las unidades TopWay guardan el modo en la propiedad del sistema
 * `persist.tw.theme.mode` o `persist.sys.tw.current.theme.type` ("day" o "night"); a veces avisan con
 * el aviso [ACTION]. Es lo mismo que usa el ecualizador de fábrica.
 *
 * - Luces apagadas (día): contraste alto.
 * - Luces encendidas (noche): colores normales.
 */
object DayNight {
    const val ACTION = "com.tw.theme.day.night.change"

    /** Propiedades donde la unidad guarda el modo, por orden de preferencia. */
    val PROPERTIES = listOf("persist.sys.tw.current.theme.type", "persist.tw.theme.mode")

    /** Cada cuánto se vuelve a mirar mientras la app está a la vista (no siempre hay aviso). */
    const val POLL_MS = 2000L

    /** Valor actual ("day", "night" o vacío si la unidad no lo guarda). */
    fun mode(): String = PROPERTIES.map { readProperty(it) }.firstOrNull { it.isNotEmpty() } ?: ""

    fun isNight(): Boolean = mode().equals("night", ignoreCase = true)

    /** Texto para la pantalla de diagnóstico. */
    fun describe(): String =
        PROPERTIES.joinToString("  ·  ") { p -> "$p = " + readProperty(p).ifEmpty { "(vacía)" } }

    fun apply() {
        val next = if (isNight()) NightPalette else ContrastPalette
        if (C.palette !== next) C.palette = next
    }

    private val getter by lazy {
        runCatching {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java)
        }.getOrNull()
    }

    private fun readProperty(name: String): String {
        val g = getter
        if (g != null) {
            val v = runCatching { g.invoke(null, name, "") as String }.getOrNull()
            if (v != null) return v.trim()
        }
        return runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("getprop", name))
            p.inputStream.bufferedReader().use { it.readText() }.trim()
        }.getOrDefault("")
    }
}
