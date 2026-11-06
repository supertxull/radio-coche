package com.radiocoche.radio.ui

/**
 * Día / noche según las luces del coche.
 *
 * Las unidades TopWay guardan el modo en la propiedad del sistema
 * `persist.sys.tw.current.theme.type` ("day" o "night") y avisan de cada cambio con
 * el aviso [ACTION]. Es lo mismo que usa el ecualizador de fábrica.
 *
 * - Luces apagadas (día): contraste alto.
 * - Luces encendidas (noche): colores normales.
 */
object DayNight {
    const val ACTION = "com.tw.theme.day.night.change"
    private const val PROPERTY = "persist.sys.tw.current.theme.type"

    fun isNight(): Boolean = readProperty(PROPERTY) == "night"

    fun apply() {
        C.palette = if (isNight()) NightPalette else ContrastPalette
    }

    private fun readProperty(name: String): String {
        val viaApi = runCatching {
            val cls = Class.forName("android.os.SystemProperties")
            cls.getMethod("get", String::class.java, String::class.java).invoke(null, name, "") as String
        }.getOrNull()
        if (!viaApi.isNullOrEmpty()) return viaApi
        return runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("getprop", name))
            p.inputStream.bufferedReader().use { it.readText() }.trim()
        }.getOrDefault("")
    }
}
