package com.radiocoche.radio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.min

/** Colores del diseño. */
object C {
    // Tema "Contraste alto": fondo negro y botones bien marcados para que se vea con sol.
    val Bg = Color(0xFF000000)
    val Panel = Color(0xFF1C2230)
    val Raised = Color(0xFF2A3242)
    val Deep = Color(0xFF0E1218)
    val Border = Color(0xFF56627A)
    val Border2 = Color(0xFF6E7B94)
    val Text = Color(0xFFFFFFFF)
    val Muted = Color(0xFFDCE2EA)
    val Dim = Color(0xFFA9B3C2)
    val Accent = Color(0xFFFF8A1E)
    val OnAccent = Color(0xFF140A00)
    val AccentBg = Color(0xFF4A2A08)
    val Tick = Color(0xFF6E7B94)
    val TickMajor = Color(0xFFC3CBD6)
    val Danger = Color(0xFFFF8A7E)
    val DangerBg = Color(0xFF2A1416)
    val DangerBorder = Color(0xFF8A3A34)
    val Scrim = Color(0xCC000000)
}

/** Letra estrecha del sistema para el nombre de la emisora y las frecuencias. */
val Condensed: FontFamily = FontFamily(
    Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))
)

/**
 * El diseño está hecho para una pantalla de 1024 × 600. Esto lo escala para que
 * ocupe la pantalla de la unidad sea cual sea su densidad.
 */
@Composable
fun ScaledRoot(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(C.Bg)) {
        val scale = min(maxWidth.value / 1024f, maxHeight.value / 600f)
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density * scale, 1f)) {
            content()
        }
    }
}

fun Modifier.panel(radius: Dp = 20.dp, bg: Color = C.Panel, border: Color = C.Border): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(bg).border(1.dp, border, shape)
}

@Composable
fun RButton(
    modifier: Modifier,
    radius: Dp = 16.dp,
    bg: Color = C.Panel,
    border: Color? = C.Border2,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    var m = modifier.clip(shape).background(bg)
    if (border != null) m = m.border(1.dp, border, shape)
    Box(m.clickable(onClick = onClick), contentAlignment = Alignment.Center, content = content)
}

enum class Glyph { SEEK_PREV, SEEK_NEXT, STEP_DOWN, STEP_UP, PRESET_PREV, PRESET_NEXT, EQ, BACK, CLOSE, CHEVRON_DOWN }

/** Iconos dibujados a mano, iguales a los del diseño. */
@Composable
fun GlyphIcon(g: Glyph, size: Dp, color: Color, modifier: Modifier = Modifier) {
    val wide = g == Glyph.SEEK_PREV || g == Glyph.SEEK_NEXT
    Canvas(modifier.size(width = if (wide) size * (32f / 24f) else size, height = size)) {
        val s = this.size.height / 24f
        fun pt(x: Float, y: Float) = Offset(x * s, y * s)
        fun tri(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) {
            val path = Path().apply {
                moveTo(ax * s, ay * s); lineTo(bx * s, by * s); lineTo(cx * s, cy * s); close()
            }
            drawPath(path, color)
            drawPath(path, color, style = Stroke(width = 1.6f * s, join = StrokeJoin.Round))
        }
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = 2.2f) {
            drawLine(color, pt(x1, y1), pt(x2, y2), strokeWidth = w * s, cap = StrokeCap.Round)
        }
        when (g) {
            Glyph.SEEK_PREV -> { tri(16f, 5f, 4f, 12f, 16f, 19f); tri(29f, 5f, 17f, 12f, 29f, 19f) }
            Glyph.SEEK_NEXT -> { tri(3f, 5f, 15f, 12f, 3f, 19f); tri(16f, 5f, 28f, 12f, 16f, 19f) }
            Glyph.STEP_DOWN -> tri(18f, 5f, 6f, 12f, 18f, 19f)
            Glyph.STEP_UP -> tri(6f, 5f, 18f, 12f, 6f, 19f)
            Glyph.PRESET_PREV -> { line(5f, 5f, 5f, 19f, 2.4f); tri(19f, 5f, 9f, 12f, 19f, 19f) }
            Glyph.PRESET_NEXT -> { tri(5f, 5f, 15f, 12f, 5f, 19f); line(19f, 5f, 19f, 19f, 2.4f) }
            Glyph.EQ -> {
                line(6f, 3f, 6f, 21f, 2f); line(12f, 3f, 12f, 21f, 2f); line(18f, 3f, 18f, 21f, 2f)
                line(3.5f, 15f, 8.5f, 15f, 3f); line(9.5f, 8f, 14.5f, 8f, 3f); line(15.5f, 13f, 20.5f, 13f, 3f)
            }
            Glyph.BACK -> { line(15f, 5f, 8f, 12f); line(8f, 12f, 15f, 19f) }
            Glyph.CLOSE -> { line(6f, 6f, 18f, 18f, 2.6f); line(18f, 6f, 6f, 18f, 2.6f) }
            Glyph.CHEVRON_DOWN -> { line(6f, 9f, 12f, 15f, 2.6f); line(12f, 15f, 18f, 9f, 2.6f) }
        }
    }
}

/** Aviso corto que desaparece solo. */
@Composable
fun ToastHost(message: String?, id: Long, bottom: Dp, onTimeout: (Long) -> Unit) {
    LaunchedEffect(id) {
        if (message != null) {
            delay(2200)
            onTimeout(id)
        }
    }
    if (message != null) {
        Box(Modifier.fillMaxSize().padding(bottom = bottom), contentAlignment = Alignment.BottomCenter) {
            Text(
                message,
                color = C.Bg,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(C.Text)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

/** Fondo oscuro para los diálogos; bloquea los toques a lo que hay detrás. */
@Composable
fun Scrim(onDismiss: (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(C.Scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss?.invoke() },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun DialogCard(width: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .width(width)
            .panel(radius = 22.dp, border = C.Border2)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .padding(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    danger: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Scrim(onDismiss = onCancel) {
        DialogCard(540.dp) {
            Text(title, color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text(message, color = C.Muted, fontSize = 21.sp, lineHeight = 26.sp)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RButton(Modifier.weight(1f).height(64.dp), bg = C.Raised, onClick = onCancel) {
                    Text("Cancelar", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                RButton(
                    Modifier.weight(1f).height(64.dp),
                    bg = if (danger) C.Danger else C.Accent,
                    border = null,
                    onClick = onConfirm,
                ) {
                    Text(
                        confirmText,
                        color = if (danger) Color(0xFF1F0B09) else C.OnAccent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
