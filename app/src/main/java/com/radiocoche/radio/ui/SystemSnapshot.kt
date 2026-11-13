package com.radiocoche.radio.ui

import android.content.Context
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Foto" del estado del sistema para encontrar qué cambia al encender las luces:
 * propiedades del sistema, ajustes de Android (system, global, secure) y brillo de la pantalla.
 * Se compara una foto con las luces apagadas y otra con las luces encendidas.
 */
object SystemSnapshot {
    private val propLine = Regex("""^\[(.*)]: \[(.*)]$""")

    suspend fun take(context: Context): Map<String, String> = withContext(Dispatchers.IO) {
        val m = sortedMapOf<String, String>()
        runCatching {
            val p = Runtime.getRuntime().exec("getprop")
            p.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val r = propLine.find(line.trim()) ?: return@forEach
                    m["prop " + r.groupValues[1]] = r.groupValues[2]
                }
            }
        }
        val tables: List<Pair<String, Uri>> = listOf(
            "system" to Settings.System.CONTENT_URI,
            "global" to Settings.Global.CONTENT_URI,
            "secure" to Settings.Secure.CONTENT_URI,
        )
        for ((label, uri) in tables) {
            runCatching {
                context.contentResolver.query(uri, arrayOf("name", "value"), null, null, null)?.use { c ->
                    while (c.moveToNext()) m["$label ${c.getString(0)}"] = c.getString(1) ?: ""
                }
            }
        }
        runCatching {
            File("/sys/class/backlight").listFiles()?.forEach { d ->
                listOf("brightness", "actual_brightness").forEach { f ->
                    runCatching { m["sys ${d.name}/$f"] = File(d, f).readText().trim() }
                }
            }
        }
        m
    }

    fun diff(before: Map<String, String>, after: Map<String, String>): List<String> =
        (before.keys + after.keys).toSortedSet().mapNotNull { k ->
            val a = before[k]
            val b = after[k]
            if (a != b) "$k:  ${a ?: "—"}  →  ${b ?: "—"}" else null
        }
}
