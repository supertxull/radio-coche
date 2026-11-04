package com.radiocoche.radio.data

import android.content.Context
import com.radiocoche.radio.model.Bank
import com.radiocoche.radio.model.RdsMemo
import com.radiocoche.radio.model.Region
import com.radiocoche.radio.model.Settings

/** Guarda presintonías, ajustes y la última emisora para que no se pierdan al apagar el coche. */
class Storage(context: Context) {

    private val p = context.getSharedPreferences("radio", Context.MODE_PRIVATE)

    fun presets(bank: Bank): List<Int?> {
        val raw = p.getString("presets_${bank.name}", null) ?: return defaultPresets(bank)
        val values = raw.split(",").map { it.toIntOrNull() }
        return List(PRESETS) { values.getOrNull(it) }
    }

    fun savePresets(bank: Bank, list: List<Int?>) {
        p.edit().putString("presets_${bank.name}", list.joinToString(",") { it?.toString() ?: "-" }).apply()
    }

    fun settings(): Settings {
        val d = Settings()
        return Settings(
            region = Region.entries.getOrElse(p.getInt("region", 0)) { Region.EUROPA },
            fmStep = p.getInt("fmStep", d.fmStep),
            amStep = p.getInt("amStep", d.amStep),
            loc = p.getBoolean("loc", d.loc),
            taDefault = p.getBoolean("taDefault", d.taDefault),
            afDefault = p.getBoolean("afDefault", d.afDefault),
            regDefault = p.getBoolean("regDefault", d.regDefault),
            taVolume = p.getInt("taVolume", d.taVolume),
        )
    }

    fun saveSettings(s: Settings) {
        p.edit()
            .putInt("region", s.region.ordinal)
            .putInt("fmStep", s.fmStep)
            .putInt("amStep", s.amStep)
            .putBoolean("loc", s.loc)
            .putBoolean("taDefault", s.taDefault)
            .putBoolean("afDefault", s.afDefault)
            .putBoolean("regDefault", s.regDefault)
            .putInt("taVolume", s.taVolume)
            .apply()
    }

    var bank: Bank
        get() = Bank.entries.getOrElse(p.getInt("bank", Bank.FM2.ordinal)) { Bank.FM1 }
        set(v) = p.edit().putInt("bank", v.ordinal).apply()

    var fm: Int
        get() = p.getInt("fm", 106300)
        set(v) = p.edit().putInt("fm", v).apply()

    var am: Int
        get() = p.getInt("am", 846)
        set(v) = p.edit().putInt("am", v).apply()

    fun rds(): Map<Int, RdsMemo> =
        (p.getStringSet("rds", null) ?: emptySet()).mapNotNull { entry ->
            val parts = entry.split("|")
            val f = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            f to RdsMemo(parts.getOrNull(1)?.ifEmpty { null }, parts.getOrNull(2)?.ifEmpty { null })
        }.toMap()

    fun saveRds(m: Map<Int, RdsMemo>) {
        p.edit().putStringSet("rds", m.map { (f, r) -> "$f|${r.ps.orEmpty()}|${r.pty.orEmpty()}" }.toSet()).apply()
    }

    companion object {
        const val PRESETS = 18

        /** Presintonías de ejemplo para la versión simulada. */
        private fun defaultPresets(bank: Bank): List<Int?> {
            val base = when (bank) {
                Bank.FM1 -> listOf(88200, 89900, 96900, 98600, 101300, 104900, 91000, 91700, 93500)
                Bank.FM2 -> listOf(106300, 94800, 99100, 103500, 102700, 92400, 91400)
                Bank.AM -> listOf(585, 639, 846, 954, 1026, 1287)
            }
            return List(PRESETS) { base.getOrNull(it) }
        }
    }
}
