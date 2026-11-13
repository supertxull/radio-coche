package com.radiocoche.diagnostico

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Recoge la información de la unidad que hace falta para el launcher.
 * No incluye IMEI, número de serie, ICCID, direcciones MAC ni identificadores del dispositivo.
 */
class Collector(private val ctx: Context, private val progress: (String) -> Unit) {

    private val pm: PackageManager = ctx.packageManager
    private val out = StringBuilder()

    fun run(): String {
        line("DIAGNÓSTICO WITSON · launcher-coche")
        line("Fecha: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date()))
        line("Sin IMEI, número de serie, ICCID, MAC ni identificadores del dispositivo.")

        step("Unidad") { unit() }
        step("Pantalla de inicio") { home() }
        step("Apps instaladas") { apps() }
        step("Qué app responde a cada acción") { handlers() }
        step("Sistema de la unidad (TWUtil)") { twutil() }
        step("Ubicación / GPS") { gps() }
        step("Propiedades del sistema") { props() }
        step("Ajustes del sistema") { settings() }
        step("Manifiestos de las apps de fábrica") { manifests() }

        line("")
        line("FIN")
        return out.toString()
    }

    // ---------------------------------------------------------------- secciones

    private fun unit() {
        line("Fabricante: ${Build.MANUFACTURER} · Marca: ${Build.BRAND} · Modelo: ${Build.MODEL}")
        line("Dispositivo: ${Build.DEVICE} · Producto: ${Build.PRODUCT} · Hardware: ${Build.HARDWARE}")
        line("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · Compilación: ${Build.DISPLAY}")
        line("Huella: ${Build.FINGERPRINT}")
        val dm = DisplayMetrics()
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getRealMetrics(dm)
        line("Pantalla: ${dm.widthPixels}x${dm.heightPixels} px · densidad ${dm.densityDpi} dpi (${dm.density}x)")
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        line("Memoria: ${mi.totalMem / 1048576} MB")
    }

    private fun home() {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val def = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        line("Predeterminada: " + (def?.activityInfo?.let { "${it.packageName}/${it.name}" } ?: "ninguna"))
        line("Candidatas:")
        for (ri in pm.queryIntentActivities(homeIntent, allFlag())) {
            val a = ri.activityInfo
            line("  - ${label(a.applicationInfo)} · ${a.packageName}/${a.name} · prioridad ${ri.priority}")
        }
    }

    private fun apps() {
        @Suppress("DEPRECATION")
        val pkgs: List<PackageInfo> = pm.getInstalledPackages(0)
        val rows = pkgs.map { p ->
            val ai = p.applicationInfo
            val system = ai != null && (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val launch = pm.getLaunchIntentForPackage(p.packageName)?.component?.className
            val enabled = ai?.enabled ?: true
            Row(system, ai?.let { label(it) } ?: "", p.packageName, p.versionName ?: "", launch, enabled)
        }.sortedWith(compareBy({ it.system }, { it.pkg }))

        line("Formato: [S]istema/[U]suario · nombre · paquete · versión · actividad que abre · (desactivada)")
        line("Total: ${rows.size} (con icono: ${rows.count { it.launch != null }})")
        for (r in rows) {
            line(
                "  [${if (r.system) "S" else "U"}] ${r.label} · ${r.pkg} · ${r.version}" +
                    (r.launch?.let { " · $it" } ?: "") +
                    (if (r.enabled) "" else " · (desactivada)")
            )
        }
    }

    private data class Row(
        val system: Boolean, val label: String, val pkg: String,
        val version: String, val launch: String?, val enabled: Boolean
    )

    private fun handlers() {
        val acts = linkedMapOf(
            "Música (categoría APP_MUSIC)" to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC),
            "Reproductor de música" to Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER),
            "Abrir archivo de audio" to Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///sdcard/a.mp3"), "audio/*"),
            "Abrir vídeo" to Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///sdcard/a.mp4"), "video/*"),
            "Marcar teléfono" to Intent(Intent.ACTION_DIAL, Uri.parse("tel:0")),
            "Mapa (geo:)" to Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=madrid")),
            "Navegación (google.navigation:)" to Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=madrid")),
            "Ajustes" to Intent(Settings.ACTION_SETTINGS),
            "Ajustes de Bluetooth" to Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            "Ajustes de pantalla" to Intent(Settings.ACTION_DISPLAY_SETTINGS),
            "Apps predeterminadas" to Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"),
            "Ajustes de ubicación" to Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            "Navegador web" to Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            "Cámara" to Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        line("Actividades:")
        for ((title, intent) in acts) {
            val list = try { pm.queryIntentActivities(intent, allFlag()) } catch (e: Exception) { emptyList() }
            line("  $title:")
            if (list.isEmpty()) line("    (ninguna)")
            for (ri in list) line("    - ${ri.activityInfo.packageName}/${ri.activityInfo.name}")
        }

        val receivers = listOf(
            Intent.ACTION_MEDIA_BUTTON,
            "com.tw.theme.day.night.change",
            Intent.ACTION_BOOT_COMPLETED,
            "android.media.AUDIO_BECOMING_NOISY",
            "android.bluetooth.adapter.action.STATE_CHANGED",
        )
        line("Receptores de avisos (broadcast):")
        for (action in receivers) {
            val list = try { pm.queryBroadcastReceivers(Intent(action), allFlag()) } catch (e: Exception) { emptyList() }
            val vendor = list.filter { !isAosp(it.activityInfo.packageName) }
            line("  $action: ${list.size} en total, de fábrica:")
            if (vendor.isEmpty()) line("    (ninguno)")
            for (ri in vendor) line("    - ${ri.activityInfo.packageName}/${ri.activityInfo.name}")
        }
    }

    private fun twutil() {
        for (name in listOf("android.tw.john.TWUtil", "android.tw.john.TWClient", "android.tw.john.TWService")) {
            try {
                val c = Class.forName(name)
                line("$name: existe (superclase ${c.superclass?.name})")
                for (f in c.declaredFields.sortedBy { it.name }) {
                    val v = try {
                        if (java.lang.reflect.Modifier.isStatic(f.modifiers)) {
                            f.isAccessible = true; " = " + f.get(null)
                        } else ""
                    } catch (e: Throwable) { "" }
                    line("  campo ${f.type.simpleName} ${f.name}$v")
                }
                for (m in c.declaredMethods.sortedBy { it.name }) {
                    line("  método ${m.name}(${m.parameterTypes.joinToString { it.simpleName }}): ${m.returnType.simpleName}")
                }
            } catch (e: Throwable) {
                line("$name: no existe")
            }
        }
    }

    private fun gps() {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        line("Proveedores: " + lm.allProviders.joinToString())
        line("Activados: " + try { lm.getProviders(true).joinToString() } catch (e: Exception) { "?" })
    }

    private fun props() {
        val proc = Runtime.getRuntime().exec(arrayOf("getprop"))
        val lines = proc.inputStream.bufferedReader().readLines()
        proc.waitFor()
        var hidden = 0
        val re = Regex("""^\[(.*)]: \[(.*)]$""")
        for (l in lines) {
            val m = re.find(l) ?: continue
            val (k, v) = m.destructured
            if (sensitiveKey(k)) { hidden++; continue }
            line("  $k = ${redact(v)}")
        }
        line("($hidden propiedades ocultas por privacidad)")
    }

    private fun settings() {
        for ((title, uri) in listOf("System" to Settings.System.CONTENT_URI, "Global" to Settings.Global.CONTENT_URI)) {
            line("Tabla $title:")
            var hidden = 0
            try {
                ctx.contentResolver.query(uri, arrayOf("name", "value"), null, null, "name")?.use { c ->
                    while (c.moveToNext()) {
                        val k = c.getString(0) ?: continue
                        if (sensitiveKey(k)) { hidden++; continue }
                        line("  $k = ${redact(c.getString(1) ?: "")}")
                    }
                }
            } catch (e: Exception) {
                line("  (no se puede leer: ${e.javaClass.simpleName})")
            }
            line("  ($hidden ocultas por privacidad)")
        }
    }

    private fun manifests() {
        @Suppress("DEPRECATION")
        val apps = pm.getInstalledApplications(0)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && !isAosp(it.packageName) }
            .sortedBy { it.packageName }
        line("Apps de sistema que no son de Android/Google: ${apps.size}")
        for (ai in apps) {
            progress("Manifiesto de ${ai.packageName}")
            line("")
            line("## ${label(ai)} · ${ai.packageName}")
            line("  apk: ${ai.sourceDir}")
            try {
                out.append(Axml.dump(ai.sourceDir))
            } catch (e: Throwable) {
                line("  (no se pudo leer: ${e.javaClass.simpleName}: ${e.message})")
            }
        }
    }

    // ---------------------------------------------------------------- utilidades

    private fun step(title: String, block: () -> Unit) {
        progress(title)
        line("")
        line("===== $title =====")
        try {
            block()
        } catch (e: Throwable) {
            line("(error: ${e.javaClass.simpleName}: ${e.message})")
        }
    }

    private fun line(s: String) { out.append(s).append('\n') }

    private fun label(ai: ApplicationInfo): String =
        try { pm.getApplicationLabel(ai).toString().replace('\n', ' ') } catch (e: Exception) { "" }

    private fun allFlag(): Int =
        if (Build.VERSION.SDK_INT >= 23) PackageManager.MATCH_ALL else 0

    private fun isAosp(pkg: String): Boolean =
        pkg == "android" || pkg.startsWith("com.android.") || pkg.startsWith("com.google.") ||
            (pkg.startsWith("android.") && !pkg.startsWith("android.tw"))

    private val sensitive = Regex(
        "serial|imei|meid|iccid|imsi|msisdn|mac_?addr|wifimac|btmac|bt_?addr|bdaddr|bluetooth_address|" +
            "wifi.?addr|device_?id|deviceid|android_id|ssid|passw|psk|phone.?num|line1|uuid|" +
            "device_name|bluetooth_name|(^|[._])sn($|[._])|(^|[._])sn\\d|ro\\.boot\\.hardware\\.sku|vbmeta\\.digest",
        RegexOption.IGNORE_CASE
    )

    private fun sensitiveKey(k: String) = sensitive.containsMatchIn(k)

    private fun redact(v: String): String = v
        .replace(Regex("""\b\d{14,17}\b"""), "[oculto]")
        .replace(Regex("""\b[0-9a-fA-F]{24,}\b"""), "[oculto]")
        .replace(Regex("""\b([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\b"""), "[oculto]")
}
