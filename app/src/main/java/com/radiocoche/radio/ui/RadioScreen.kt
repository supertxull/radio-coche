package com.radiocoche.radio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radiocoche.radio.model.Band
import com.radiocoche.radio.model.BandConfig
import com.radiocoche.radio.model.PTY_TYPES

@Composable
fun RadioScreen(s: UiState, vm: RadioViewModel, onEqualizer: () -> Unit) {
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Dial(
                cfg = s.cfg,
                freq = s.freq,
                enabled = !s.scanning,
                onTap = vm::tuneTo,
                onDrag = vm::dragTo,
                onDragEnd = vm::dragEnd,
            )

            Row(
                Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RButton(Modifier.size(96.dp, 128.dp), radius = 22.dp, onClick = { vm.seek(false) }) {
                    GlyphIcon(Glyph.SEEK_PREV, 40.dp, C.Text)
                }
                RButton(Modifier.size(68.dp, 96.dp), radius = 18.dp, bg = Color.Transparent, onClick = { vm.stepFine(false) }) {
                    GlyphIcon(Glyph.STEP_DOWN, 26.dp, C.Muted)
                }
                StationBlock(s, vm, Modifier.weight(1f))
                RButton(Modifier.size(68.dp, 96.dp), radius = 18.dp, bg = Color.Transparent, onClick = { vm.stepFine(true) }) {
                    GlyphIcon(Glyph.STEP_UP, 26.dp, C.Muted)
                }
                RButton(Modifier.size(96.dp, 128.dp), radius = 22.dp, onClick = { vm.seek(true) }) {
                    GlyphIcon(Glyph.SEEK_NEXT, 40.dp, C.Text)
                }
            }

            Presets(s, vm)
            BottomBar(s, vm, onEqualizer)
        }

        ToastHost(s.toast, s.toastId, bottom = 104.dp, onTimeout = vm::clearToast)

        if (s.ptyOpen) PtyPanel(s, vm)
        if (s.confirmScan) {
            ConfirmDialog(
                title = "¿Buscar emisoras automáticamente?",
                message = "Se borrarán las 18 presintonías de ${s.bank.label} que tienes guardadas y se sustituirán por las emisoras que se encuentren.",
                confirmText = "Aceptar",
                danger = false,
                onConfirm = vm::startScan,
                onCancel = vm::cancelAskScan,
            )
        }
        if (s.scanning) ScanningPanel(s, vm)
    }
}

// ---------------- Dial ----------------

@Composable
private fun Dial(
    cfg: BandConfig,
    freq: Int,
    enabled: Boolean,
    onTap: (Int) -> Unit,
    onDrag: (Int) -> Unit,
    onDragEnd: () -> Unit,
) {
    val tap by rememberUpdatedState(onTap)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val active by rememberUpdatedState(enabled)
    val measurer = rememberTextMeasurer()

    Box(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .panel(radius = 16.dp)
            .padding(horizontal = 36.dp, vertical = 6.dp),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(cfg) {
                    detectTapGestures { o -> if (active) tap(cfg.at(o.x / size.width)) }
                }
                .pointerInput(cfg) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> if (active) drag(cfg.at(o.x / size.width)) },
                        onDragEnd = { if (active) dragEnd() },
                        onDragCancel = { if (active) dragEnd() },
                    ) { change, _ ->
                        change.consume()
                        if (active) drag(cfg.at(change.position.x / size.width))
                    }
                },
        ) {
            val w = size.width
            var f = cfg.min
            while (f <= cfg.max) {
                val x = cfg.fraction(f) * w
                drawLine(C.Tick, Offset(x, 4.dp.toPx()), Offset(x, 14.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                f += cfg.minor
            }
            val labelStyle = TextStyle(color = C.Muted, fontSize = 13.sp)
            for (l in cfg.labels) {
                val x = cfg.fraction(l) * w
                drawLine(C.TickMajor, Offset(x, 4.dp.toPx()), Offset(x, 24.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                val layout = measurer.measure(text = cfg.format(l), style = labelStyle)
                drawText(layout, topLeft = Offset(x - layout.size.width / 2f, 26.dp.toPx()))
            }
            val nx = cfg.fraction(freq) * w
            drawRoundRect(
                C.Accent.copy(alpha = 0.25f),
                topLeft = Offset(nx - 6.dp.toPx(), 0f),
                size = Size(12.dp.toPx(), 34.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            drawRoundRect(
                C.Accent,
                topLeft = Offset(nx - 3.dp.toPx(), 2.dp.toPx()),
                size = Size(6.dp.toPx(), 30.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
    }
}

// ---------------- Emisora ----------------

@Composable
private fun StationBlock(s: UiState, vm: RadioViewModel, modifier: Modifier) {
    val fm = s.band == Band.FM
    val info = s.info
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                s.bank.label,
                color = C.OnAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(C.Accent).padding(horizontal = 9.dp, vertical = 3.dp),
            )
            if (fm) {
                StatusChip("RDS", on = info?.ps != null)
                val pty = info?.pty
                PtyButton(
                    text = if (pty != null) "PTY · $pty" else "PTY",
                    lit = pty != null,
                    filled = s.filter != null,
                    onClick = vm::openPty,
                )
                val filter = s.filter
                if (filter != null) FilterChip(filter) { vm.setFilter(null) }
            }
        }

        val name = info?.ps
        if (name != null) {
            Text(name, color = C.Text, fontFamily = Condensed, fontSize = 84.sp, lineHeight = 86.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${s.cfg.format(s.freq)} ${s.cfg.unit}", color = C.Muted, fontSize = 24.sp)
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.cfg.format(s.freq), color = C.Text, fontFamily = Condensed, fontSize = 84.sp, lineHeight = 86.sp)
                Text(s.cfg.unit, color = C.Muted, fontSize = 28.sp, modifier = Modifier.padding(bottom = 12.dp))
            }
            Text("Sin datos RDS", color = C.Muted, fontSize = 24.sp)
        }

        Text(
            info?.radioText.orEmpty(),
            color = C.Muted,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(22.dp),
        )

        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (fm) {
                Toggle("TA", s.ta, vm::toggleTa)
                Toggle("AF", s.af, vm::toggleAf)
                Toggle("REG", s.reg, vm::toggleReg)
                Toggle(if (s.mono) "MONO" else "ST", s.mono || info?.stereo == true, vm::toggleMono)
            }
            Toggle(if (s.settings.loc) "LOC" else "DX", true, vm::toggleLoc)
        }
    }
}

@Composable
private fun StatusChip(text: String, on: Boolean) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        text,
        color = if (on) C.Accent else C.Dim,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.border(1.dp, if (on) C.Accent else C.Border2, shape).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun PtyButton(text: String, lit: Boolean, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val fg = when {
        filled -> C.OnAccent
        lit -> C.Accent
        else -> C.Muted
    }
    Row(
        Modifier
            .height(34.dp)
            .clip(shape)
            .background(if (filled) C.Accent else Color.Transparent)
            .border(1.dp, if (filled || lit) C.Accent else C.Border2, shape)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
        GlyphIcon(Glyph.CHEVRON_DOWN, 14.dp, fg)
    }
}

@Composable
private fun FilterChip(filter: String, onClear: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .height(34.dp)
            .clip(shape)
            .background(C.AccentBg)
            .border(1.dp, C.Accent, shape)
            .clickable(onClick = onClear)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Filtro: $filter", color = C.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        GlyphIcon(Glyph.CLOSE, 14.dp, C.Accent)
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .height(44.dp)
            .widthIn(min = 60.dp)
            .clip(shape)
            .background(if (on) C.AccentBg else Color.Transparent)
            .border(1.dp, if (on) C.Accent else C.Border2, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) C.Accent else C.Dim, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

// ---------------- Presintonías ----------------

/** Pulsación larga de 1 segundo para guardar una presintonía. */
private class OneSecondLongPress(base: ViewConfiguration) : ViewConfiguration by base {
    override val longPressTimeoutMillis: Long get() = 1000L
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Presets(s: UiState, vm: RadioViewModel) {
    val visible = s.visible
    if (visible.isEmpty()) {
        Box(
            Modifier.fillMaxWidth().height(132.dp).border(1.dp, C.Border2, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("No tienes presintonías de tipo «${s.filter.orEmpty()}»", color = C.Muted, fontSize = 18.sp)
        }
        return
    }

    val pages = (visible.size + 5) / 6
    val startPage = (visible.indexOf(s.currentIndex).coerceAtLeast(0) / 6).coerceIn(0, pages - 1)
    val pager = rememberPagerState(initialPage = startPage) { pages }

    LaunchedEffect(s.pageRequestId) {
        if (s.pageRequestId > 0) pager.animateScrollToPage(s.pageRequest.coerceIn(0, pages - 1))
    }

    val base = LocalViewConfiguration.current
    val longPress = remember(base) { OneSecondLongPress(base) }
    CompositionLocalProvider(LocalViewConfiguration provides longPress) {
        HorizontalPager(
            state = pager,
            pageSpacing = 8.dp,
            modifier = Modifier.fillMaxWidth().height(132.dp),
        ) { page ->
            val items = visible.drop(page * 6).take(6)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in 0 until 3) {
                            val i = items.getOrNull(r * 3 + c)
                            if (i != null) PresetButton(s, i, vm, Modifier.weight(1f))
                            else Spacer(Modifier.weight(1f).height(62.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetButton(s: UiState, i: Int, vm: RadioViewModel, modifier: Modifier) {
    val f = s.list[i]
    val on = i == s.currentIndex
    val haptic = LocalHapticFeedback.current
    val name = f?.let { s.psOf(it) }
    val label = when {
        f == null -> "Vacía"
        name != null -> name
        else -> s.cfg.format(f)
    }
    val sub = when {
        f == null -> ""
        name != null -> "${s.cfg.format(f)} ${s.cfg.unit}"
        else -> s.cfg.unit
    }
    val shape = RoundedCornerShape(14.dp)
    val mute = if (on) C.OnAccent else C.Muted

    Row(
        modifier
            .height(62.dp)
            .clip(shape)
            .background(if (on) C.Accent else C.Panel)
            .border(1.dp, if (on) C.Accent else C.Border, shape)
            .combinedClickable(
                onClick = { vm.selectPreset(i) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.savePreset(i)
                },
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("${i + 1}", color = mute, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 22.dp))
        Text(
            label,
            color = when {
                f == null -> C.Dim
                on -> C.OnAccent
                else -> C.Text
            },
            fontFamily = Condensed,
            fontSize = 26.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (sub.isNotEmpty()) Text(sub, color = mute, fontSize = 14.sp, maxLines = 1)
    }
}

// ---------------- Barra inferior ----------------

@Composable
private fun BottomBar(s: UiState, vm: RadioViewModel, onEqualizer: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val m = Modifier.fillMaxHeight()
        RButton(m.weight(1f), bg = C.Panel, border = C.Border, onClick = vm::askScan) {
            Icon(Icons.Filled.Search, contentDescription = "Búsqueda automática", tint = C.Text, modifier = Modifier.size(32.dp))
        }
        RButton(m.weight(1f), bg = C.Panel, border = C.Border, onClick = { vm.stepPreset(-1) }) {
            GlyphIcon(Glyph.PRESET_PREV, 32.dp, C.Text)
        }
        RButton(m.weight(1.4f), bg = C.Raised, border = C.Border2, onClick = vm::toggleBank) {
            Text(s.bank.label, color = C.Text, fontFamily = Condensed, fontSize = 34.sp)
        }
        RButton(m.weight(1f), bg = C.Panel, border = C.Border, onClick = { vm.stepPreset(1) }) {
            GlyphIcon(Glyph.PRESET_NEXT, 32.dp, C.Text)
        }
        RButton(m.weight(1f), bg = C.Panel, border = C.Border, onClick = onEqualizer) {
            GlyphIcon(Glyph.EQ, 30.dp, C.Text)
        }
        RButton(m.weight(1f), bg = C.Panel, border = C.Border, onClick = vm::openSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Configuración", tint = C.Text, modifier = Modifier.size(32.dp))
        }
    }
}

// ---------------- Paneles ----------------

@Composable
private fun PtyPanel(s: UiState, vm: RadioViewModel) {
    Scrim(onDismiss = vm::closePty) {
        DialogCard(920.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Tipo de programa (PTY)", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Text("Elige un tipo para ver solo esas presintonías. El número indica cuántas tienes.", color = C.Muted, fontSize = 15.sp)
                }
                RButton(Modifier.height(52.dp).padding(0.dp), bg = C.Raised, onClick = { vm.setFilter(null) }) {
                    Text("Mostrar todas", color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp))
                }
                RButton(Modifier.size(52.dp), bg = C.Raised, onClick = vm::closePty) {
                    GlyphIcon(Glyph.CLOSE, 22.dp, C.Text)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PTY_TYPES.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { type ->
                            val n = s.list.count { f -> f != null && s.ptyOf(f) == type }
                            PtyCell(Modifier.weight(1f), type, n, on = s.filter == type) { vm.setFilter(type) }
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PtyCell(modifier: Modifier, type: String, count: Int, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        on -> C.Accent
        count > 0 -> C.Raised
        else -> Color.Transparent
    }
    val border = when {
        on -> C.Accent
        count > 0 -> C.Border2
        else -> C.Border
    }
    val fg = when {
        on -> C.OnAccent
        count > 0 -> C.Text
        else -> C.Dim
    }
    Row(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(type, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            "$count",
            color = when {
                on -> C.OnAccent
                count > 0 -> C.Accent
                else -> C.Dim
            },
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun ScanningPanel(s: UiState, vm: RadioViewModel) {
    Scrim {
        DialogCard(440.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Buscando emisoras…", color = C.Muted, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.cfg.format(s.freq), color = C.Text, fontFamily = Condensed, fontSize = 64.sp, lineHeight = 66.sp)
                    Text(s.cfg.unit, color = C.Muted, fontSize = 22.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
                Text(
                    if (s.scanFound == 1) "1 emisora encontrada" else "${s.scanFound} emisoras encontradas",
                    color = C.Muted,
                    fontSize = 17.sp,
                )
                RButton(Modifier.fillMaxWidth().height(60.dp).padding(top = 0.dp), bg = C.Raised, onClick = vm::cancelScan) {
                    Text("Cancelar", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
