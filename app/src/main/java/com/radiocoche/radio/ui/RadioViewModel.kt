package com.radiocoche.radio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.radiocoche.radio.data.Storage
import com.radiocoche.radio.model.Band
import com.radiocoche.radio.model.BandConfig
import com.radiocoche.radio.model.Bank
import com.radiocoche.radio.model.RdsMemo
import com.radiocoche.radio.model.Region
import com.radiocoche.radio.model.Settings
import com.radiocoche.radio.model.StationInfo
import com.radiocoche.radio.model.configFor
import com.radiocoche.radio.tuner.HardKey
import com.radiocoche.radio.tuner.SimulatedTuner
import com.radiocoche.radio.tuner.TopWayTuner
import com.radiocoche.radio.tuner.Tuner
import com.radiocoche.radio.tuner.TunerStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen { RADIO, SETTINGS }

data class UiState(
    val screen: Screen = Screen.RADIO,
    val bank: Bank,
    val fm: Int,
    val am: Int,
    val presets: Map<Bank, List<Int?>>,
    val settings: Settings,
    val rds: Map<Int, RdsMemo> = emptyMap(),
    val info: StationInfo? = null,
    val mono: Boolean = false,
    val ta: Boolean = false,
    val af: Boolean = true,
    val reg: Boolean = false,
    val filter: String? = null,
    val ptyOpen: Boolean = false,
    val confirmScan: Boolean = false,
    val scanning: Boolean = false,
    val scanFound: Int = 0,
    val confirmDelete: Bank? = null,
    val toast: String? = null,
    val toastId: Long = 0,
    val pageRequest: Int = 0,
    val pageRequestId: Long = 0,
    /** Presintonía elegida en último lugar; manda si sigue coincidiendo con la frecuencia. */
    val selected: Int? = null,
) {
    val band: Band get() = bank.band
    val freq: Int get() = if (band == Band.FM) fm else am
    val cfg: BandConfig get() = configFor(band, settings)
    val list: List<Int?> get() = presets.getValue(bank)
    val currentIndex: Int
        get() = selected?.takeIf { cfg.same(list.getOrNull(it), freq) } ?: list.indexOfFirst { cfg.same(it, freq) }

    fun psOf(f: Int): String? = rds[f]?.ps
    fun ptyOf(f: Int): String? = rds[f]?.pty

    /** Posiciones de las presintonías que se muestran (todas, o solo las del tipo PTY elegido). */
    val visible: List<Int>
        get() = list.indices.filter { i -> filter == null || list[i]?.let { ptyOf(it) == filter } == true }
}

class RadioViewModel(app: Application) : AndroidViewModel(app) {

    private val storage = Storage(app)

    // El sintonizador real de la unidad TopWay; si no está (otro aparato), el simulado.
    private val tuner: Tuner = TopWayTuner.create() ?: SimulatedTuner()

    private val _ui: MutableStateFlow<UiState>
    val ui: StateFlow<UiState>

    private var dragJob: Job? = null
    private var seekJob: Job? = null
    private var scanJob: Job? = null

    init {
        val settings = storage.settings()
        var rds = storage.rds()
        val sim = tuner as? SimulatedTuner
        if (rds.isEmpty() && sim != null) {
            rds = sim.knownRds()
            storage.saveRds(rds)
        }
        // Primera vez con el sintonizador real: fuera los datos de ejemplo de la versión simulada.
        if (tuner.isReal && !storage.realTunerReady) {
            rds = emptyMap()
            storage.saveRds(rds)
            Bank.entries.forEach { storage.savePresets(it, List(Storage.PRESETS) { null }) }
            storage.realTunerReady = true
        }
        _ui = MutableStateFlow(
            UiState(
                bank = storage.bank,
                fm = storage.fm,
                am = storage.am,
                presets = Bank.entries.associateWith { storage.presets(it) },
                settings = settings,
                rds = rds,
                ta = settings.taDefault,
                af = settings.afDefault,
                reg = settings.regDefault,
            )
        )
        ui = _ui.asStateFlow()

        val s = _ui.value
        // Como la app de fábrica: al arrancar no tocamos LOC/DX, AF, TA ni REG del sintonizador;
        // leemos su estado y lo mostramos. Solo se cambian cuando el usuario pulsa el botón.
        if (!tuner.isReal) {
            tuner.setLoc(settings.loc)
            tuner.setTa(s.ta)
            tuner.setAf(s.af)
            tuner.setReg(s.reg)
        }
        // El sintonizador real ya sabe en qué emisora estaba: no se la cambiamos al arrancar.
        if (!tuner.isReal) tuner.tune(s.band, s.freq)

        tuner.setHardKeyListener { key ->
            when (key) {
                HardKey.NEXT_PRESET -> stepPreset(1)
                HardKey.PREV_PRESET -> stepPreset(-1)
                HardKey.SEEK_UP -> seek(true)
                HardKey.SEEK_DOWN -> seek(false)
            }
        }

        viewModelScope.launch { tuner.status.collect { onStatus(it) } }

        if (tuner.isReal && s.presets.values.all { l -> l.all { it == null } }) {
            toast("Pulsa la lupa para buscar las emisoras de tu zona")
        }
    }

    fun debugLog(): List<String> = tuner.debugLog()

    // ---------- Exportar / importar (emisoras.txt) ----------

    /**
     * Texto con las presintonías, fácil de leer y de editar:
     *   [FM1]
     *   1;COPE;106.3
     * Una línea por presintonía ocupada: número;nombre;frecuencia (MHz en FM, kHz en AM).
     */
    fun exportText(): String {
        val u = _ui.value
        val sb = StringBuilder("# Radio Coche · presintonías\n# número;nombre;frecuencia\n")
        Bank.entries.forEach { bank ->
            val cfg = configFor(bank.band, u.settings)
            sb.append("\n[").append(bank.label).append("]\n")
            u.presets.getValue(bank).forEachIndexed { i, f ->
                if (f != null) sb.append(i + 1).append(';').append(u.psOf(f).orEmpty()).append(';').append(cfg.format(f)).append('\n')
            }
        }
        return sb.toString()
    }

    fun importText(text: String) {
        val u = _ui.value
        val presets = u.presets.toMutableMap()
        var rds = u.rds
        val touched = mutableSetOf<Bank>()
        var bank: Bank? = null
        var count = 0
        text.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.isEmpty() || line.startsWith("#") -> Unit
                line.startsWith("[") && line.endsWith("]") -> {
                    bank = Bank.entries.firstOrNull { it.label.equals(line.trim('[', ']').trim(), ignoreCase = true) }
                    bank?.let { b ->
                        if (touched.add(b)) presets[b] = List<Int?>(Storage.PRESETS) { null }
                    }
                }
                else -> {
                    val b = bank ?: return@forEach
                    val parts = line.split(';')
                    val n = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return@forEach
                    val raw = parts.getOrNull(2)?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: return@forEach
                    if (n !in 1..Storage.PRESETS) return@forEach
                    val cfg = configFor(b.band, u.settings)
                    val f = cfg.clamp(if (b.band == Band.FM) (raw * 1000).toInt() else raw.toInt())
                    presets[b] = presets.getValue(b).toMutableList().also { it[n - 1] = f }
                    val name = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
                    if (name != null) rds = rds + (f to RdsMemo(name, rds[f]?.pty))
                    count++
                }
            }
        }
        if (touched.isEmpty()) {
            toast("El archivo no tiene presintonías válidas")
            return
        }
        touched.forEach { storage.savePresets(it, presets.getValue(it)) }
        storage.saveRds(rds)
        _ui.update { it.copy(presets = presets, rds = rds, filter = null, selected = null) }
        toast("$count presintonías importadas")
    }

    /** La app vuelve a primer plano (o se va): recupera el sonido de la radio. */
    fun onForeground(on: Boolean) = tuner.setForeground(on)

    private fun onStatus(status: TunerStatus) {
        val info = status.info
        val current = _ui.value
        var rds = current.rds
        if (info != null && (info.ps != null || info.pty != null) && status.freq > 0) {
            val memo = RdsMemo(info.ps, info.pty)
            if (rds[status.freq] != memo) {
                rds = rds + (status.freq to memo)
                storage.saveRds(rds)
            }
        }
        // Si la emisora ha cambiado desde fuera (volante, AF, otra app), la pantalla la sigue.
        val follow = tuner.isReal && status.freq > 0 && status.band == current.band &&
            !current.scanning && dragJob?.isActive != true && status.freq != current.freq
        if (follow) {
            setFreqState(status.freq)
            if (status.band == Band.FM) storage.fm = status.freq else storage.am = status.freq
        }
        _ui.update {
            it.copy(
                info = if (status.band == it.band) info else null,
                rds = rds,
                mono = status.mono ?: it.mono,
                ta = status.ta ?: it.ta,
                af = status.af ?: it.af,
                reg = status.reg ?: it.reg,
                settings = if (status.loc != null && status.loc != it.settings.loc) it.settings.copy(loc = status.loc) else it.settings,
            )
        }
    }

    // ---------- Sintonía ----------

    private fun setFreqState(f: Int) {
        _ui.update { if (it.band == Band.FM) it.copy(fm = f) else it.copy(am = f) }
    }

    fun tuneTo(f: Int) {
        val u = _ui.value
        val v = u.cfg.clamp(f)
        setFreqState(v)
        tuner.tune(u.band, v)
        if (u.band == Band.FM) storage.fm = v else storage.am = v
    }

    /** Mientras se arrastra el dial solo se mueve la aguja; el sintonizador se mueve al parar. */
    fun dragTo(f: Int) {
        val v = _ui.value.cfg.clamp(f)
        setFreqState(v)
        dragJob?.cancel()
        dragJob = viewModelScope.launch {
            delay(250)
            tuner.tune(_ui.value.band, v)
        }
    }

    fun dragEnd() {
        dragJob?.cancel()
        tuneTo(_ui.value.freq)
    }

    fun stepFine(up: Boolean) {
        val u = _ui.value
        tuneTo(u.freq + if (up) u.cfg.step else -u.cfg.step)
    }

    fun seek(up: Boolean) {
        if (seekJob?.isActive == true && !tuner.isReal) return
        seekJob = viewModelScope.launch {
            val u = _ui.value
            tuner.seek(u.cfg, u.freq, up)?.let { tuneTo(it) }
        }
    }

    // ---------- Presintonías ----------

    fun selectPreset(i: Int) {
        val f = _ui.value.list.getOrNull(i)
        if (f == null) toast("Mantén pulsado para guardar aquí") else { _ui.update { it.copy(selected = i) }; tuneTo(f) }
    }

    fun savePreset(i: Int) {
        val u = _ui.value
        val l = u.list.toMutableList()
        l[i] = u.freq
        _ui.update { it.copy(presets = it.presets + (u.bank to l), selected = i) }
        storage.savePresets(u.bank, l)
        toast("Guardada en P${i + 1}: ${u.cfg.format(u.freq)} ${u.cfg.unit}")
    }

    fun stepPreset(dir: Int) {
        val u = _ui.value
        val filled = u.visible.filter { u.list[it] != null }
        if (filled.isEmpty()) return
        val pos = filled.indexOf(u.currentIndex)
        val nextPos = when {
            pos >= 0 -> (pos + dir + filled.size) % filled.size
            dir > 0 -> 0
            else -> filled.size - 1
        }
        val idx = filled[nextPos]
        _ui.update { it.copy(selected = idx) }
        tuneTo(u.list[idx] ?: return)
        requestPage(u.visible.indexOf(idx) / 6)
    }

    private fun requestPage(page: Int) {
        _ui.update { it.copy(pageRequest = page, pageRequestId = it.pageRequestId + 1) }
    }

    fun toggleBank() {
        val u = _ui.value
        val nb = u.bank.next()
        storage.bank = nb
        _ui.update { it.copy(bank = nb, filter = null, selected = null, info = if (nb.band == u.band) it.info else null) }
        val n = _ui.value
        if (nb.band != u.band) tuner.tune(n.band, n.freq)
        requestPage(n.currentIndex.coerceAtLeast(0) / 6)
    }

    // ---------- Opciones de recepción ----------

    fun toggleMono() {
        val v = !_ui.value.mono
        _ui.update { it.copy(mono = v) }
        tuner.setMono(v)
    }

    fun toggleTa() {
        val v = !_ui.value.ta
        _ui.update { it.copy(ta = v) }
        tuner.setTa(v)
        toast(if (v) "Avisos de tráfico activados" else "Avisos de tráfico desactivados")
    }

    fun toggleAf() {
        val v = !_ui.value.af
        _ui.update { it.copy(af = v) }
        tuner.setAf(v)
        toast(if (v) "AF activado: cambia de frecuencia si se pierde la señal" else "AF desactivado")
    }

    fun toggleReg() {
        val v = !_ui.value.reg
        _ui.update { it.copy(reg = v) }
        tuner.setReg(v)
        toast(if (v) "REG activado: no cambia a desconexiones regionales" else "REG desactivado")
    }

    fun toggleLoc() = setLoc(!_ui.value.settings.loc, withToast = true)

    fun setLoc(v: Boolean, withToast: Boolean = false) {
        updateSettings { it.copy(loc = v) }
        tuner.setLoc(v)
        if (withToast) toast(if (v) "LOC: solo emisoras con señal fuerte" else "DX: también emisoras lejanas o débiles")
    }

    // ---------- Búsqueda automática ----------

    fun askScan() = _ui.update { it.copy(confirmScan = true) }
    fun cancelAskScan() = _ui.update { it.copy(confirmScan = false) }

    fun startScan() {
        val u = _ui.value
        val from = u.freq
        val bank = u.bank
        val cfg = u.cfg
        _ui.update { it.copy(confirmScan = false, scanning = true, scanFound = 0) }
        scanJob = viewModelScope.launch {
            try {
                val found = tuner.scan(cfg) { f, n ->
                    setFreqState(f)
                    _ui.update { it.copy(scanFound = n) }
                }
                val l = List(Storage.PRESETS) { found.getOrNull(it) }
                _ui.update { it.copy(presets = it.presets + (bank to l), filter = null) }
                storage.savePresets(bank, l)
                tuneTo(found.firstOrNull() ?: from)
                requestPage(0)
                toast(if (found.isEmpty()) "No se han encontrado emisoras" else "${minOf(found.size, Storage.PRESETS)} emisoras guardadas")
            } catch (e: CancellationException) {
                tuneTo(from)
                toast("Búsqueda cancelada. Presintonías sin cambios")
                throw e
            } finally {
                _ui.update { it.copy(scanning = false) }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    // ---------- PTY ----------

    fun openPty() = _ui.update { it.copy(ptyOpen = true) }
    fun closePty() = _ui.update { it.copy(ptyOpen = false) }

    fun setFilter(type: String?) {
        _ui.update { it.copy(filter = type, ptyOpen = false) }
        requestPage(0)
    }

    // ---------- Avisos ----------

    fun toast(msg: String) = _ui.update { it.copy(toast = msg, toastId = it.toastId + 1) }
    fun clearToast(id: Long) = _ui.update { if (it.toastId == id) it.copy(toast = null) else it }

    // ---------- Navegación ----------

    fun openSettings() = _ui.update { it.copy(screen = Screen.SETTINGS) }

    /** Botón atrás: primero cierra lo que esté abierto, después vuelve a la radio. */
    fun onBack() {
        val u = _ui.value
        when {
            u.scanning -> cancelScan()
            u.confirmScan -> cancelAskScan()
            u.ptyOpen -> closePty()
            u.confirmDelete != null -> cancelDelete()
            u.screen == Screen.SETTINGS -> _ui.update { it.copy(screen = Screen.RADIO) }
        }
    }

    // ---------- Configuración ----------

    private fun updateSettings(f: (Settings) -> Settings) {
        _ui.update { it.copy(settings = f(it.settings)) }
        storage.saveSettings(_ui.value.settings)
    }

    fun setRegion(r: Region) {
        updateSettings { it.copy(region = r, fmStep = r.fmStep, amStep = r.amStep) }
        reclamp()
    }

    fun setFmStep(v: Int) {
        updateSettings { it.copy(fmStep = v) }
        reclamp()
    }

    fun setAmStep(v: Int) {
        updateSettings { it.copy(amStep = v) }
        reclamp()
    }

    private fun reclamp() {
        val u = _ui.value
        val fm = configFor(Band.FM, u.settings).clamp(u.fm)
        val am = configFor(Band.AM, u.settings).clamp(u.am)
        _ui.update { it.copy(fm = fm, am = am) }
        storage.fm = fm
        storage.am = am
        tuner.tune(u.band, if (u.band == Band.FM) fm else am)
    }

    fun setTaDefault(v: Boolean) = updateSettings { it.copy(taDefault = v) }
    fun setAfDefault(v: Boolean) = updateSettings { it.copy(afDefault = v) }
    fun setRegDefault(v: Boolean) = updateSettings { it.copy(regDefault = v) }
    fun changeTaVolume(delta: Int) = updateSettings { it.copy(taVolume = (it.taVolume + delta).coerceIn(0, 40)) }

    fun askDelete(b: Bank) = _ui.update { it.copy(confirmDelete = b) }
    fun cancelDelete() = _ui.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val b = _ui.value.confirmDelete ?: return
        val empty = List<Int?>(Storage.PRESETS) { null }
        _ui.update { it.copy(presets = it.presets + (b to empty), confirmDelete = null) }
        storage.savePresets(b, empty)
        toast("Presintonías de ${b.label} borradas")
    }

    override fun onCleared() {
        tuner.release()
    }
}
