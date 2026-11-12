package com.radiocoche.radio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radiocoche.radio.model.Bank
import com.radiocoche.radio.model.Region

@Composable
fun SettingsScreen(s: UiState, vm: RadioViewModel) {
    val st = s.settings
    var showDiag by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.height(60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RButton(Modifier.size(60.dp), onClick = vm::onBack) {
                    GlyphIcon(Glyph.BACK, 28.dp, C.Text)
                }
                Text("Configuración", color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                RButton(Modifier.height(52.dp), radius = 14.dp, bg = C.Panel, onClick = { showDiag = true }) {
                    Text("Diagnóstico", color = C.Muted, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp))
                }
            }

            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().panel().padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SectionTitle("SINTONIZADOR")
                    Field("Región", hint = st.region.range) {
                        Segmented(Region.entries.map { it to it.label }, st.region, vm::setRegion)
                    }
                    Field("Ajustes de sintonía FM") {
                        Segmented(listOf(50 to "0,05 MHz", 100 to "0,1 MHz", 200 to "0,2 MHz"), st.fmStep, vm::setFmStep)
                    }
                    Field("Ajustes de sintonía AM") {
                        Segmented(listOf(9 to "9 kHz", 10 to "10 kHz"), st.amStep, vm::setAmStep)
                    }
                    Field("Sensibilidad", hint = if (st.loc) "Solo emisoras con señal fuerte" else "También lejanas o débiles") {
                        Segmented(listOf(true to "LOC · Local", false to "DX · Lejana"), st.loc) { vm.setLoc(it) }
                    }
                }

                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        Modifier.fillMaxWidth().panel().padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SectionTitle("INICIO")
                        SwitchRow("TA · Avisos de tráfico", "Activado al encender la radio", st.taDefault, vm::setTaDefault)
                        SwitchRow("AF · Frecuencias alternativas", "Cambia de frecuencia si se pierde la señal", st.afDefault, vm::setAfDefault)
                        SwitchRow("REG · Regional", "No cambia a desconexiones regionales", st.regDefault, vm::setRegDefault)
                        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Border))
                        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Volumen de avisos de tráfico", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                Text("Se aplica cuando entra un aviso TA", color = C.Muted, fontSize = 16.sp)
                            }
                            RButton(Modifier.size(48.dp), radius = 12.dp, bg = C.Raised, onClick = { vm.changeTaVolume(-1) }) {
                                Text("−", color = C.Text, fontSize = 26.sp)
                            }
                            Text(
                                "${st.taVolume}",
                                color = C.Text,
                                fontFamily = Condensed,
                                fontSize = 30.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(min = 44.dp),
                            )
                            RButton(Modifier.size(48.dp), radius = 12.dp, bg = C.Raised, onClick = { vm.changeTaVolume(1) }) {
                                Text("+", color = C.Text, fontSize = 26.sp)
                            }
                        }
                    }

                    Column(
                        Modifier.fillMaxWidth().weight(1f).panel().padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SectionTitle("PRESINTONÍAS")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Bank.entries.forEach { b ->
                                RButton(
                                    Modifier.weight(1f).height(56.dp),
                                    radius = 14.dp,
                                    bg = C.DangerBg,
                                    border = C.DangerBorder,
                                    onClick = { vm.askDelete(b) },
                                ) {
                                    Text("Borrar ${b.label}", color = C.Danger, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        ToastHost(s.toast, s.toastId, bottom = 32.dp, onTimeout = vm::clearToast)

        if (showDiag) DiagnosticPanel(vm) { showDiag = false }

        val del = s.confirmDelete
        if (del != null) {
            ConfirmDialog(
                title = "¿Borrar las presintonías de ${del.label}?",
                message = "Se vaciarán las 18 presintonías de ${del.label}. Esta acción no se puede deshacer.",
                confirmText = "Borrar",
                danger = true,
                onConfirm = vm::confirmDelete,
                onCancel = vm::cancelDelete,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = C.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
}

@Composable
private fun Field(label: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(label, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (hint != null) Text(hint, color = C.Muted, fontSize = 16.sp, textAlign = TextAlign.End)
        }
        content()
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Deep).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) C.Accent else Color.Transparent)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) C.OnAccent else C.Muted,
                    fontSize = 19.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, color = C.Muted, fontSize = 16.sp)
        }
        val shape = RoundedCornerShape(18.dp)
        Box(
            Modifier
                .size(64.dp, 36.dp)
                .clip(shape)
                .background(if (checked) C.Accent else C.Deep)
                .border(1.dp, if (checked) C.Accent else C.Border2, shape)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(if (checked) C.OnAccent else C.Dim))
        }
    }
}

/** Lista en directo de los mensajes con el sintonizador, para enviar capturas al depurar. */
@Composable
private fun DiagnosticPanel(vm: RadioViewModel, onClose: () -> Unit) {
    var lines by remember { mutableStateOf(vm.debugLog()) }
    LaunchedEffect(Unit) {
        while (true) {
            lines = vm.debugLog()
            delay(500)
        }
    }
    Scrim(onDismiss = onClose) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp)
                .panel(radius = 20.dp, bg = C.Bg, border = C.Border2)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Diagnóstico del sintonizador", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                RButton(Modifier.height(48.dp), radius = 12.dp, bg = C.Raised, onClick = onClose) {
                    Text("Cerrar", color = C.Text, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 18.dp))
                }
            }
            if (lines.isEmpty()) {
                Text("Sin mensajes (sintonizador simulado o sin actividad).", color = C.Muted, fontSize = 18.sp)
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(lines) { line ->
                    Text(line, color = C.Muted, fontSize = 15.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                }
            }
        }
    }
}
