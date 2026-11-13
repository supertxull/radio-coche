package com.radiocoche.diagnostico

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Escucha TODOS los mensajes del microcontrolador (no solo los de la radio) para encontrar
 * cuál cambia al encender y apagar las luces. No envía ninguna orden: solo escucha.
 */
class ListenActivity : Activity() {

    private val fileName = "escucha-luces.txt"
    private val orange = Color.parseColor("#FF8A1E")
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val main = Handler(Looper.getMainLooper())

    private val lines = ArrayList<String>()
    private var lastKey: String? = null
    private var repeats = 0
    private val seen = HashSet<String>()
    private var marks = 0

    private lateinit var status: TextView
    private lateinit var view: TextView
    private val instances = mutableListOf<Pair<Any, Class<*>>>()
    private var receiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(Color.BLACK)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(10))
        }
        root.addView(TextView(this).apply {
            text = "Escuchar luces"
            setTextColor(Color.WHITE); textSize = 26f; typeface = Typeface.DEFAULT_BOLD
        })
        status = TextView(this).apply {
            setTextColor(Color.parseColor("#DCE2EA")); textSize = 17f
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(status)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(button("MARCA") { mark() })
        row.addView(button("Guardar") { save() })
        row.addView(button("Borrar") { lines.clear(); seen.clear(); lastKey = null; render() })
        root.addView(row)
        view = TextView(this).apply {
            setTextColor(Color.parseColor("#DCE2EA")); textSize = 14f; typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        root.addView(ScrollView(this).apply { setPadding(0, dp(10), 0, 0); addView(view) },
            LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)

        startListening()
        listenBroadcasts()
        add("Modo noche de Android: ${nightText(resources.configuration)}")
        status.text = "Escuchando ${instances.size} grupos. Pulsa MARCA, enciende las luces, espera 5 s, pulsa MARCA, apágalas, espera 5 s y pulsa MARCA. Repite 2 veces y pulsa Guardar."
    }

    // ---------- MCU ----------

    private fun ranges(): List<IntRange> = listOf(
        0x0000..0x00FF, 0x0100..0x01FF, 0x0200..0x02FF, 0x0300..0x03FF,
        0x0400..0x04FF, 0x0500..0x05FF, 0x0600..0x06FF, 0x0700..0x07FF,
        0x0800..0x0FFF, 0x9E00..0x9EFF, 0xA000..0xA1FF,
    )

    private fun startListening() {
        val cls = try { Class.forName("android.tw.john.TWUtil") } catch (e: Throwable) {
            add("No hay TWUtil en este equipo: ${e.message}"); return
        }
        for (r in ranges()) {
            try {
                val tw = try { cls.getConstructor(Int::class.javaPrimitiveType).newInstance(1) }
                         catch (e: Throwable) { cls.getConstructor().newInstance() }
                val ids = ShortArray(r.count()) { (r.first + it).toShort() }
                val res = cls.getMethod("open", ShortArray::class.java).invoke(tw, ids) as? Int ?: 0
                if (res != 0) { add("open ${hex(r.first)}-${hex(r.last)} → $res"); continue }
                cls.getMethod("start").invoke(tw)
                val h = Handler(Looper.getMainLooper()) { m ->
                    onMcu(m.what, m.arg1, m.arg2, m.obj); true
                }
                cls.getMethod("addHandler", String::class.java, Handler::class.java)
                    .invoke(tw, "EscuchaLuces${r.first}", h)
                instances += tw to cls
            } catch (e: Throwable) {
                add("Error ${hex(r.first)}: ${e.javaClass.simpleName} ${e.message}")
            }
        }
    }

    private fun onMcu(what: Int, a1: Int, a2: Int, obj: Any?) {
        val o = when (obj) {
            null -> ""
            is ByteArray -> " bytes=" + obj.joinToString(" ") { "%02X".format(it) }
            is IntArray -> " ints=" + obj.joinToString(",")
            is ShortArray -> " shorts=" + obj.joinToString(",")
            else -> " obj=" + obj.toString().take(80)
        }
        val text = "${hex(what)}  $a1  $a2$o"
        // Las líneas que se repiten seguidas se cuentan en vez de llenarlo todo.
        if (text == lastKey) {
            repeats++
            if (lines.isNotEmpty()) lines[lines.size - 1] = lines.last().substringBefore("   ×") + "   ×${repeats + 1}"
            render(); return
        }
        lastKey = text; repeats = 0
        val isNew = seen.add(text)
        add((if (isNew) "" else "  ") + text)
    }

    // ---------- Broadcasts y modo noche ----------

    private fun listenBroadcasts() {
        val actions = listOf(
            "com.tw.theme.day.night.change", "com.tw.day.night", "com.tw.light",
            "android.intent.action.CONFIGURATION_CHANGED", "android.intent.action.SCREEN_ON",
            "android.intent.action.SCREEN_OFF", "android.app.action.ENTER_CAR_MODE",
            "android.app.action.EXIT_CAR_MODE", "com.zjinnova.zlink.night",
            "com.android.server.am.backlight", "android.intent.action.ILL",
        )
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val extras = i.extras?.keySet()?.joinToString { k -> "$k=${i.extras?.get(k)}" } ?: ""
                add("BROADCAST ${i.action} $extras")
            }
        }
        val f = IntentFilter().apply { actions.forEach { addAction(it) } }
        runCatching { registerReceiver(receiver, f) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        add("Configuración cambiada · modo noche: ${nightText(newConfig)}")
    }

    private fun nightText(c: Configuration) =
        when (c.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> "sí"
            Configuration.UI_MODE_NIGHT_NO -> "no"
            else -> "?"
        }

    // ---------- Interfaz ----------

    private fun mark() {
        marks++
        lastKey = null
        add("======== MARCA $marks ========")
        Toast.makeText(this, "Marca $marks", Toast.LENGTH_SHORT).show()
    }

    private fun add(s: String) {
        lines += fmt.format(Date()) + "  " + s
        if (lines.size > 3000) lines.removeAt(0)
        render()
    }

    private var pending = false
    private fun render() {
        if (pending) return
        pending = true
        main.postDelayed({
            pending = false
            view.text = lines.takeLast(400).asReversed().joinToString("\n")
        }, 200)
    }

    private fun save() {
        val text = "Escucha de luces · ${Build.MODEL}\n\n" + lines.joinToString("\n")
        if (Build.VERSION.SDK_INT < 29) { Toast.makeText(this, "No se puede guardar aquí", Toast.LENGTH_LONG).show(); return }
        try {
            val col = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            contentResolver.delete(col, "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(fileName))
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(col, v)!!
            contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            Toast.makeText(this, "Guardado en Descargas/$fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo guardar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        runCatching { receiver?.let { unregisterReceiver(it) } }
        for ((tw, cls) in instances) {
            runCatching { cls.getMethod("removeHandler", String::class.java).invoke(tw, "EscuchaLuces") }
            runCatching { cls.getMethod("stop").invoke(tw) }
            runCatching { cls.getMethod("close").invoke(tw) }
        }
        super.onDestroy()
    }

    private fun hex(v: Int) = "0x" + Integer.toHexString(v and 0xFFFF).uppercase()

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; textSize = 20f; isAllCaps = false
        setTextColor(Color.BLACK)
        background = GradientDrawable().apply { setColor(orange); cornerRadius = dp(12).toFloat() }
        setPadding(dp(26), 0, dp(26), 0)
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, dp(60)).apply { marginEnd = dp(14) }
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
