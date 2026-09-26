package ru.vinteno.finni.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import ru.vinteno.finni.ui.art.Art

/**
 * Картинка предмета: PNG-слои из art/app, вписанные в квадрат с сохранением пропорций. Слои
 * лежат на общей канве основы, поэтому просто кладутся друг на друга. Нет хоть одного слоя —
 * заглушка целиком, без половинчатых картинок: простые фигуры с тёплым контуром, узнаются на
 * 48 dp (build-plan.md §3). Трава и ягоды могут быть зелёными и красными: запрет касается
 * интерфейса, а не картинки мира (гайд §5.1).
 */
private val Line = Color(0xFF5A3A22)

/** Из каких файлов собрана картинка, снизу вверх (art-brief §3: слои надбавок и еды — на канве основы). */
private fun layers(id: String): List<String> = when (id) {
    "kasha" -> listOf("item_miska", "item_kasha")
    "kasha_yagody" -> listOf("item_miska", "item_kasha", "item_yagody")
    "krupa" -> listOf("item_miska", "item_krupa")
    "mylo_pena", "pena" -> listOf("item_mylo", "item_pena")
    "kopilka" -> listOf("item_kopilka_obj")
    "kira" -> listOf("kira_birthday")
    // Только еда, без миски: миска стоит на полу всё время, меняется лишь этот слой (animation-howto §7.3).
    "food_kasha" -> listOf("item_kasha")
    "food_kasha_yagody" -> listOf("item_kasha", "item_yagody")
    "food_krupa" -> listOf("item_krupa")
    else -> artFiles(id)
}

/**
 * Файлы вещей глав 2 и 3: вещь с надбавкой — основа и слой надбавки на её канве (art-brief §3); цели
 * главы 3 без суффикса — пустые (выбор цели, копилка), с `_full` — с вещами после новоселья.
 */
fun artFiles(id: String): List<String> = when (id) {
    "kurtka_risunok" -> listOf("item_kurtka", "item_risunok")
    "lechenie_bint" -> listOf("item_lechenie", "item_bint")
    "korobka_nakleyki" -> listOf("item_korobka", "item_nakleyki")
    "lampa_abazhur" -> listOf("item_lampa", "item_abazhur")
    "ugoshchenie_glazur" -> listOf("item_ugoshchenie", "item_glazur")
    "polotence_vyshivka" -> listOf("item_polotence", "item_vyshivka")
    "polka_veshchey", "korzina", "sunduk" -> listOf("item_${id}_empty")
    "deposit" -> listOf("item_kopilka_obj")
    else -> listOf("item_$id")
}

/** Слой еды в миске для купленного на этой неделе: крупа, каша, каша с ягодами. Ягоды — только на каше (items.md §7). */
fun foodLayer(bought: Collection<String>): String? = when {
    "krupa" in bought -> "food_krupa"
    "kasha" in bought -> if ("yagody" in bought) "food_kasha_yagody" else "food_kasha"
    else -> null
}

@Composable
fun Picture(id: String, size: Dp, modifier: Modifier = Modifier, description: String? = null) {
    Art.init(LocalContext.current)
    val files = layers(id)
    val png = files.none(Art::missing)
    Canvas(modifier.size(size).semantics { description?.let { contentDescription = it } }) {
        if (png) {
            // Пока слои декодируются, не рисуем ничего — это доли секунды при первом показе.
            val img = files.map { Art.image(it) ?: return@Canvas }
            val base = img.first()
            val k = minOf(this.size.width / base.canvasWidth, this.size.height / base.canvasHeight)
            translate((this.size.width - base.canvasWidth * k) / 2, (this.size.height - base.canvasHeight * k) / 2) {
                scale(k, k, Offset.Zero) {
                    img.forEach { drawImage(it.bitmap, Offset(it.left.toFloat(), it.top.toFloat())) }
                }
            }
            return@Canvas
        }
        scale(this.size.minDimension / 48f)
        when (id) {
            "kasha" -> { bowl(Color(0xFFF3E2C0)); porridge() }
            "krupa" -> sack()
            "kasha_yagody" -> { bowl(Color(0xFFF3E2C0)); porridge(); berries() }
            "food_kasha" -> porridge()
            "food_kasha_yagody" -> { porridge(); berries() }
            "food_krupa" -> porridge()
            "polka" -> drawRoundRect(Line, Offset(4f, 20f), Size(40f, 5f), CornerRadius(2.5f))
            "okno" -> { drawRoundRect(Color(0xFFD6ECF8), Offset(8f, 8f), Size(32f, 32f)); drawRoundRect(Line, Offset(8f, 8f), Size(32f, 32f), style = Stroke(2f)) }
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

/**
 * Кира в масштабе Финни: `kira_birthday` нарисована на канве 640 × 960 в том же масштабе, что слои
 * Финни (art-brief §6), поэтому рисуется тем же числом dp на пиксель канвы, что Финни шириной
 * [finniWidth], и по той же рамке по высоте — стоят на одной земле. Рост у неё свой, без вписывания
 * в квадрат. Нет PNG — заглушка.
 */
@Composable
fun KiraFigure(finniWidth: Dp, modifier: Modifier = Modifier, description: String? = null, image: String = KIRA) {
    Art.init(LocalContext.current)
    val spec = Art.finni
    if (spec == null || Art.missing(image)) {
        Picture("kira", finniWidth, modifier, description)
        return
    }
    val box = spec.figureBox
    val unit = finniWidth / box.width
    Canvas(modifier.size(unit * KIRA_CANVAS_W, unit * box.height).semantics { description?.let { contentDescription = it } }) {
        val img = Art.image(image) ?: return@Canvas
        val k = size.height / box.height
        scale(k, k, Offset.Zero) { drawImage(img.bitmap, Offset(img.left.toFloat(), img.top - box.top)) }
    }
}

private const val KIRA = "kira_birthday"
private const val KIRA_CANVAS_W = 640f

/** Линия пола на `room_*`: доля высоты картинки от верха. По ТЗ художнику — 82%, по пикселям — 81,3%. */
private const val ROOM_FLOOR = 1952f / 2400f

/**
 * Какая комната сейчас: `room_sand` в главе 1, `room_cold` в главе 2, `room_home` в главе 3, со светом
 * лампы — `room_home_light` (целая комната на замену). Ставит навигация по состоянию игры; чтение в
 * рисовании подписывает фон на смену.
 */
object RoomArt {
    var name by mutableStateOf("room_sand")

    fun of(chapter: Int, lamp: Boolean): String = when (chapter) {
        1 -> "room_sand"
        2 -> "room_cold"
        else -> if (lamp) "room_home_light" else "room_home"
    }
}

/**
 * Фон комнаты под всем экраном: линия пола картинки ложится на [floorY] — пол комнаты
 * в коде. Пропорции не меняются: картинка шире экрана или равна ему, лишнее срезается сверху и по
 * краям. Стена и пол однородны, поэтому срез краёв не виден. Пока картинка не готова — фон экрана.
 */
fun DrawScope.drawRoom(floorY: Float, name: String = RoomArt.name) {
    val img = Art.image(name) ?: return
    val w = img.canvasWidth.toFloat()
    val h = img.canvasHeight.toFloat()
    // Масштаб: во всю ширину, и чтобы пол доставал до низа экрана, а стена — до верха.
    val k = maxOf(size.width / w, (size.height - floorY) / (h * (1f - ROOM_FLOOR)), floorY / (h * ROOM_FLOOR))
    val left = (size.width - w * k) / 2
    val top = floorY - h * ROOM_FLOOR * k
    translate(left, top) { scale(k, k, Offset.Zero) { drawImage(img.bitmap, Offset(img.left.toFloat(), img.top.toFloat())) } }
}
