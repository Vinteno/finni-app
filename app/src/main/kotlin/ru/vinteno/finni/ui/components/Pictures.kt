package ru.vinteno.finni.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/**
 * Картинки-заглушки до этапа 7 (build-plan.md §3): простые фигуры с тёплым контуром,
 * узнаются на 48 dp. Имена совпадают с `item_<id>` из animation-howto.md §12 — при подмене
 * на PNG меняется только эта функция. Трава и ягоды могут быть зелёными и красными: запрет
 * касается интерфейса, а не картинки мира (гайд §5.1).
 */
private val Line = Color(0xFF5A3A22)

@Composable
fun Picture(id: String, size: Dp, modifier: Modifier = Modifier, description: String? = null) {
    Canvas(modifier.size(size).semantics { description?.let { contentDescription = it } }) {
        scale(this.size.minDimension / 48f)
        when (id) {
            "kasha" -> { bowl(Color(0xFFF3E2C0)); porridge() }
            "krupa" -> sack()
            "kasha_yagody" -> { bowl(Color(0xFFF3E2C0)); porridge(); berries() }
            "yagody" -> berries(dy = -8f)
            "mylo" -> soap()
            "mylo_pena", "pena" -> { soap(); bubbles() }
            "myachik" -> ball()
            "kacheli" -> swing()
            "posylka" -> parcel()
            "miska" -> bowl(Color(0xFFE9D8B8))
            "kopilka" -> piggy()
            "podarok_myach" -> { gift(Color(0xFF7FA7D9)); ball(small = true) }
            "podarok_kniga" -> book()
            "podarok_samokat" -> scooter()
            "zapiska" -> note()
            "dver" -> door()
            "kira" -> hedgehog()
            else -> gift(Color(0xFFE8C9A0))
        }
    }
}

private fun DrawScope.scale(k: Float) = drawContext.transform.scale(k, k, Offset.Zero)

private fun DrawScope.outlined(path: Path, fill: Color, w: Float = 1.6f) {
    drawPath(path, fill)
    drawPath(path, Line, style = Stroke(w, cap = StrokeCap.Round))
}

private fun DrawScope.bowl(c: Color) {
    outlined(Path().apply { moveTo(6f, 24f); lineTo(42f, 24f); cubicTo(40f, 38f, 30f, 42f, 24f, 42f); cubicTo(18f, 42f, 8f, 38f, 6f, 24f); close() }, c)
}

private fun DrawScope.porridge() {
    outlined(Path().apply { moveTo(9f, 24f); cubicTo(12f, 16f, 36f, 16f, 39f, 24f); close() }, Color(0xFFF6D98E))
}

private fun DrawScope.berries(dy: Float = 0f) {
    for ((x, y) in listOf(18f to 19f, 24f to 17f, 30f to 19f)) {
        drawCircle(Color(0xFFB2334F), 3.6f, Offset(x, y + dy)); drawCircle(Line, 3.6f, Offset(x, y + dy), style = Stroke(1.2f))
    }
}

private fun DrawScope.sack() {
    outlined(Path().apply { moveTo(14f, 14f); lineTo(34f, 14f); cubicTo(42f, 26f, 42f, 42f, 24f, 42f); cubicTo(6f, 42f, 6f, 26f, 14f, 14f); close() }, Color(0xFFE2C28C))
    drawLine(Line, Offset(14f, 14f), Offset(34f, 14f), 3f, StrokeCap.Round)
    for ((x, y) in listOf(20f to 30f, 26f to 33f, 29f to 27f)) drawCircle(Color(0xFFF6E7B0), 2f, Offset(x, y))
}

private fun DrawScope.soap() {
    drawRoundRect(Color(0xFFBFD8EE), Offset(9f, 20f), Size(30f, 18f), CornerRadius(7f))
    drawRoundRect(Line, Offset(9f, 20f), Size(30f, 18f), CornerRadius(7f), style = Stroke(1.6f))
    drawLine(Color.White, Offset(15f, 25f), Offset(24f, 25f), 2.4f, StrokeCap.Round)
}

private fun DrawScope.bubbles() {
    for ((x, y, r) in listOf(Triple(14f, 13f, 5f), Triple(26f, 9f, 6.5f), Triple(36f, 15f, 4f))) {
        drawCircle(Color(0xFFEAF3FB), r, Offset(x, y)); drawCircle(Line, r, Offset(x, y), style = Stroke(1.2f))
    }
}

private fun DrawScope.ball(small: Boolean = false) {
    val c = if (small) Offset(24f, 12f) else Offset(24f, 25f)
    val r = if (small) 7f else 15f
    drawCircle(Color(0xFFF2B01E), r, c)
    drawArc(Color(0xFF4F86C6), -60f, 120f, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
    drawCircle(Line, r, c, style = Stroke(1.6f))
}

private fun DrawScope.swing() {
    drawLine(Line, Offset(8f, 6f), Offset(40f, 6f), 3f, StrokeCap.Round)
    drawLine(Line, Offset(14f, 6f), Offset(14f, 32f), 1.6f)
    drawLine(Line, Offset(34f, 6f), Offset(34f, 32f), 1.6f)
    drawRoundRect(Color(0xFFC98E5A), Offset(9f, 31f), Size(30f, 6f), CornerRadius(3f))
    drawRoundRect(Line, Offset(9f, 31f), Size(30f, 6f), CornerRadius(3f), style = Stroke(1.6f))
}

private fun DrawScope.parcel() {
    drawRoundRect(Color(0xFFD9A86C), Offset(7f, 15f), Size(34f, 27f), CornerRadius(3f))
    drawRoundRect(Line, Offset(7f, 15f), Size(34f, 27f), CornerRadius(3f), style = Stroke(1.6f))
    drawLine(Color(0xFF9C6B3A), Offset(24f, 15f), Offset(24f, 42f), 4f)
    drawLine(Line, Offset(7f, 22f), Offset(41f, 22f), 1.2f)
}

private fun DrawScope.gift(c: Color) {
    drawRoundRect(c, Offset(9f, 20f), Size(30f, 22f), CornerRadius(3f))
    drawRoundRect(Line, Offset(9f, 20f), Size(30f, 22f), CornerRadius(3f), style = Stroke(1.6f))
    drawLine(Color(0xFFF7F0E3), Offset(24f, 20f), Offset(24f, 42f), 4f)
}

private fun DrawScope.book() {
    outlined(Path().apply { moveTo(8f, 14f); lineTo(24f, 18f); lineTo(24f, 40f); lineTo(8f, 36f); close() }, Color(0xFF9FC3E6))
    outlined(Path().apply { moveTo(40f, 14f); lineTo(24f, 18f); lineTo(24f, 40f); lineTo(40f, 36f); close() }, Color(0xFFB9D3EE))
}

private fun DrawScope.scooter() {
    drawLine(Line, Offset(12f, 36f), Offset(34f, 36f), 3f, StrokeCap.Round)
    drawLine(Line, Offset(34f, 36f), Offset(30f, 10f), 3f, StrokeCap.Round)
    drawLine(Line, Offset(25f, 10f), Offset(35f, 10f), 3f, StrokeCap.Round)
    for (x in listOf(12f, 34f)) { drawCircle(Color(0xFF7FA7D9), 4.5f, Offset(x, 39f)); drawCircle(Line, 4.5f, Offset(x, 39f), style = Stroke(1.4f)) }
}

private fun DrawScope.piggy() {
    drawOval(Color(0xFFF5B8C8), Offset(7f, 16f), Size(34f, 24f))
    drawOval(Line, Offset(7f, 16f), Size(34f, 24f), style = Stroke(1.6f))
    drawRoundRect(Line, Offset(20f, 17f), Size(9f, 2.5f), CornerRadius(1f))
    drawCircle(Line, 1.4f, Offset(33f, 25f))
    drawOval(Color(0xFFEFA0B5), Offset(37f, 25f), Size(7f, 6f)); drawOval(Line, Offset(37f, 25f), Size(7f, 6f), style = Stroke(1.2f))
    for (x in listOf(14f, 30f)) drawLine(Line, Offset(x, 38f), Offset(x, 43f), 3f, StrokeCap.Round)
}

private fun DrawScope.note() {
    drawRoundRect(Color(0xFFFFF6D6), Offset(8f, 8f), Size(32f, 34f), CornerRadius(3f))
    drawRoundRect(Line, Offset(8f, 8f), Size(32f, 34f), CornerRadius(3f), style = Stroke(1.6f))
    drawCircle(Color(0xFFC57B00), 2.5f, Offset(24f, 10f))
    for (y in listOf(20f, 26f, 32f)) drawLine(Color(0xFFBDAA86), Offset(13f, y), Offset(35f, y), 1.4f)
}

private fun DrawScope.door() {
    drawRoundRect(Color(0xFFC08A5B), Offset(10f, 4f), Size(28f, 44f), CornerRadius(4f))
    drawRoundRect(Line, Offset(10f, 4f), Size(28f, 44f), CornerRadius(4f), style = Stroke(1.6f))
    drawCircle(Color(0xFFF2B01E), 2.2f, Offset(33f, 28f))
}

/** Кира — ежонок (H1): силуэт не спутать с зайцем, окрас не из семей трёх окрасов Финни. */
private fun DrawScope.hedgehog() {
    val spikes = Path().apply {
        moveTo(6f, 40f)
        var x = 6f
        var up = true
        while (x < 40f) { x += 3.4f; lineTo(x, if (up) 10f + (x - 23f) * (x - 23f) / 30f else 18f + (x - 23f) * (x - 23f) / 30f); up = !up }
        lineTo(40f, 40f); close()
    }
    outlined(spikes, Color(0xFF6E6470))
    drawOval(Color(0xFFF1DCC8), Offset(14f, 22f), Size(22f, 20f)); drawOval(Line, Offset(14f, 22f), Size(22f, 20f), style = Stroke(1.6f))
    drawCircle(Line, 1.8f, Offset(21f, 30f)); drawCircle(Line, 1.8f, Offset(29f, 30f))
    drawCircle(Color(0xFF3A2A20), 2.2f, Offset(25f, 35f))
    drawArc(Line, 20f, 140f, false, Offset(21f, 34f), Size(8f, 5f), style = Stroke(1.2f))
}
