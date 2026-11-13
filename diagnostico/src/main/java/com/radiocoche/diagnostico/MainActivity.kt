package com.radiocoche.diagnostico

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val fileName = "diagnostico-witson.txt"
    private val orange = Color.parseColor("#F7931E")

    private lateinit var status: TextView
    private lateinit var preview: TextView
    private val buttons = mutableListOf<Button>()

    private var report: String? = null
    private var downloadsUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(Color.parseColor("#0B0E12"))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(12))
        }

        root.addView(TextView(this).apply {
            text = "Diagnóstico de la unidad"
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
        })

        status = TextView(this).apply {
            setTextColor(Color.parseColor("#C9D0D8"))
            textSize = 17f
            setPadding(0, dp(6), 0, dp(10))
        }
        root.addView(status)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(button("Guardar en…") { saveAs() })
        row.addView(button("Compartir") { share() })
        row.addView(button("Copiar") { copy() })
        root.addView(row)

        preview = TextView(this).apply {
            setTextColor(Color.parseColor("#A3ADBB"))
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        root.addView(ScrollView(this).apply {
            setPadding(0, dp(12), 0, 0)
            addView(preview)
        }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        setContentView(root)
        collect()
    }

    private fun collect() {
        setButtonsEnabled(false)
        status.text = "Recopilando información…"
        Thread {
            val text = try {
                Collector(this) { step -> runOnUiThread { status.text = "Recopilando: $step…" } }.run()
            } catch (e: Throwable) {
                "Error general: ${e.javaClass.simpleName}: ${e.message}"
            }
            val saved = saveToDownloads(text)
            runOnUiThread {
                report = text
                val kb = text.toByteArray().size / 1024
                status.text = if (saved != null) {
                    "Listo ($kb KB). Guardado en Descargas/$fileName. También puedes guardarlo en un pendrive con «Guardar en…»."
                } else {
                    "Listo ($kb KB). Usa «Guardar en…» para guardarlo en un pendrive o en la memoria."
                }
                preview.text = if (text.length > 30000) {
                    text.substring(0, 30000) + "\n\n… (el resto está en el archivo)"
                } else text
                setButtonsEnabled(true)
            }
        }.start()
    }

    /** Copia automática en la carpeta Descargas (Android 10 o superior). */
    private fun saveToDownloads(text: String): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            val resolver = contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            // Borra la copia anterior de esta app, si la hay.
            resolver.delete(collection, "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(fileName))
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            downloadsUri = uri
            uri
        } catch (e: Throwable) {
            null
        }
    }

    private fun saveAs() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_SAVE)
        } catch (e: Exception) {
            toast("Esta unidad no tiene selector de archivos")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == REQ_SAVE && resultCode == RESULT_OK && uri != null) {
            try {
                contentResolver.openOutputStream(uri, "wt")?.use { it.write((report ?: "").toByteArray()) }
                toast("Guardado")
            } catch (e: Exception) {
                toast("No se pudo guardar: ${e.message}")
            }
        }
    }

    private fun share() {
        val text = report ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico Witson")
            val uri = downloadsUri
            if (uri != null) {
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                putExtra(Intent.EXTRA_TEXT, text.take(200_000))
            }
        }
        try {
            startActivity(Intent.createChooser(intent, "Compartir diagnóstico"))
        } catch (e: Exception) {
            toast("No hay ninguna app para compartir")
        }
    }

    private fun copy() {
        val text = report ?: return
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("diagnóstico", text.take(200_000)))
            toast(if (text.length > 200_000) "Copiado (solo el principio: el texto es muy largo)" else "Copiado")
        } catch (e: Exception) {
            toast("No se pudo copiar: es demasiado largo. Usa «Guardar en…»")
        }
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 18f
        isAllCaps = false
        setTextColor(Color.parseColor("#111111"))
        background = GradientDrawable().apply {
            setColor(orange)
            cornerRadius = dp(12).toFloat()
        }
        minHeight = dp(56)
        setPadding(dp(22), 0, dp(22), 0)
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, dp(56)).apply { marginEnd = dp(12) }
        setOnClickListener { onClick() }
        buttons.add(this)
    }

    private fun setButtonsEnabled(on: Boolean) {
        for (b in buttons) {
            b.isEnabled = on
            b.alpha = if (on) 1f else 0.4f
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_SAVE = 1
    }
}
