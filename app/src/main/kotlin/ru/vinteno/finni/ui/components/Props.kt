package ru.vinteno.finni.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import ru.vinteno.finni.core.engine.Direction
import ru.vinteno.finni.ui.art.Art
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniText

/*
 * Банка плана, полка-витрина, корзина магазина и календарь на стене дома. Правило то же, что у вещей комнаты: есть PNG в art/app —
 * рисуется картинка, нет — рисунок кодом. Место и размер задаёт раскладка, от варианта они не
 * зависят. Цвета дерева и стекла — цвета мира, как у заглушек в Pictures.kt, а не интерфейса.
 */

private const val JAR_NEED_PNG = "prop_jar_need"
private const val JAR_WANT_PNG = "prop_jar_want"
private const val JAR_SAVE_PNG = "prop_jar_save"
private const val JAR_COIN_PNG = "prop_coin_in_jar"
private const val SHELF_PNG = "prop_shelf"
private const val BASKET_PNG = "prop_basket"
private const val CALENDAR_PNG = "prop_calendar"

private val Wood = Color(0xFFD9A86C)
private val WoodEdge = Color(0xFFB98549)
private val Wicker = Color(0xFFE4BC85)
private val WickerDark = Color(0xFFD7A965)

/** Банка 96 × 150 в единицах рисунка: ширина к высоте. */
const val JAR_RATIO = 96f / 150f

/**
 * Банки одного размера и масштаба: высота россыпи показывает долю от доступных монет,
 * а подпись рядом — точное количество. Видимые грани не означают монеты поштучно.
 */
@Composable
fun Jar(value: Int, scaleMax: Int, direction: Direction?, height: Dp, modifier: Modifier = Modifier) {
    Art.init(LocalContext.current)
    val jarName = when (direction) {
        Direction.NEED -> JAR_NEED_PNG
        Direction.WANT -> JAR_WANT_PNG
        null -> JAR_SAVE_PNG
    }
    val jar = Art.image(jarName)
    val coin = Art.image(JAR_COIN_PNG)
    Canvas(modifier.size(height * JAR_RATIO, height)) {
        val k = size.height / 150f
        scale(k, k, Offset.Zero) {
            val body = jarBody()
            if (jar != null) drawFitted(jar, Size(96f, 150f))
            else drawPath(body, Color.White.copy(alpha = 0.62f))
            if (value > 0) clipPath(jarInterior()) { coinHeap(value, scaleMax, coin, height < 60.dp) }
            if (jar == null) {
                drawPath(body, FinniColors.StrokeStrong, style = Stroke(2f))
                drawLine(Color.White.copy(alpha = 0.8f), Offset(11f, 50f), Offset(11f, 80f), 4f, StrokeCap.Round)
                val lid = directionStyle(direction).bg
                val lidEdge = when (direction) {
                    Direction.NEED -> FinniColors.NeedDeep
                    Direction.WANT -> FinniColors.WantDeep
                    null -> FinniColors.SaveDeep
                }
                val r = CornerRadius(5f)
                drawRoundRect(lid, Offset(12f, 6f), Size(72f, 16f), r)
                drawRoundRect(lidEdge, Offset(12f, 6f), Size(72f, 16f), r, style = Stroke(2f))
            }
        }
    }
}

private fun jarBody() = Path().apply {
    moveTo(14f, 26f); quadraticTo(14f, 20f, 20f, 20f); lineTo(76f, 20f); quadraticTo(82f, 20f, 82f, 26f)
    lineTo(82f, 30f); quadraticTo(92f, 36f, 92f, 50f); lineTo(92f, 140f); quadraticTo(92f, 150f, 82f, 150f)
    lineTo(14f, 150f); quadraticTo(4f, 150f, 4f, 140f); lineTo(4f, 50f); quadraticTo(4f, 36f, 14f, 30f); close()
}

/** Уже нарисованные края и крышка остаются поверх пустого фона; россыпь не заходит на стенки. */
private fun jarInterior() = Path().apply {
    moveTo(18f, 43f); lineTo(78f, 43f); quadraticTo(85f, 48f, 85f, 58f)
    lineTo(85f, 130f); quadraticTo(85f, 139f, 77f, 139f)
    lineTo(19f, 139f); quadraticTo(11f, 139f, 11f, 130f)
    lineTo(11f, 58f); quadraticTo(11f, 48f, 18f, 43f); close()
}

internal fun jarFillFraction(value: Int, scaleMax: Int): Float =
    if (value <= 0) 0f else value.toFloat() / maxOf(value, scaleMax, 1)

private fun DrawScope.coinHeap(value: Int, scaleMax: Int, sprite: ru.vinteno.finni.ui.art.ArtImage?, small: Boolean) {
    val ratio = jarFillFraction(value, scaleMax)
    val bottom = 139f
    // У одной монеты есть минимальная видимая толщина; дальше рост линейный.
    val height = maxOf(5f, ratio * 95f)
    val top = bottom - height
    val wave = minOf(3.5f, height * 0.16f)
    val mound = Path().apply {
        moveTo(10f, top + 5f)
        cubicTo(22f, top + 1f, 27f, top - wave, 37f, top + 2f)
        cubicTo(48f, top + 5f, 56f, top - wave, 66f, top + 1f)
        cubicTo(75f, top - 1f, 82f, top + 2f, 86f, top + 5f)
        lineTo(86f, bottom + 2f); lineTo(10f, bottom + 2f); close()
    }
    drawPath(
        mound,
        Brush.verticalGradient(
            listOf(Color(0xFFFFDA67), Color(0xFFF2B01E), Color(0xFFD68A13)),
            startY = top, endY = bottom,
        ),
    )
    // Размытые крупные формы внутри кучи — фактура, не ряд монет для счёта.
    val texture = listOf(
        Offset(21f, top + 18f), Offset(60f, top + 25f), Offset(36f, top + 37f),
        Offset(72f, top + 48f), Offset(28f, top + 58f), Offset(52f, top + 70f),
    )
    texture.forEachIndexed { i, p ->
        if (p.y < bottom + 7f) drawOval(
            if (i % 2 == 0) Color(0xFFFFE38B).copy(alpha = 0.32f) else Color(0xFFAB6613).copy(alpha = 0.15f),
            Offset(p.x - 15f, p.y - 5f), Size(30f, 10f),
        )
    }
    val faces = if (value <= 2) value else if (small) 2 else 4
    val positions = listOf(
        Triple(20f, top + 3f, -14f), Triple(53f, top + 1f, 12f),
        Triple(35f, top + 12f, 16f), Triple(65f, top + 15f, -18f),
    )
    positions.take(faces).forEach { (x, y, angle) ->
        val center = Offset(x + 11f, y + 7f)
        rotate(angle, center) {
            if (sprite != null) translate(x, y) { drawFitted(sprite, Size(23f, 15f)) }
            else {
                drawOval(FinniColors.Coin, Offset(x, y), Size(23f, 15f))
                drawOval(FinniColors.CoinEdge, Offset(x, y), Size(23f, 15f), style = Stroke(1.4f))
            }
        }
    }
    // Передние блики снова видны поверх монет: содержимое находится за стеклом.
    drawLine(Color.White.copy(alpha = 0.64f), Offset(16f, 60f), Offset(16f, 125f), 3f, StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.48f), Offset(80f, 65f), Offset(80f, 119f), 2.5f, StrokeCap.Round)
    drawOval(Color(0xFFB4DCEB).copy(alpha = 0.58f), Offset(14f, 129f), Size(68f, 17f), style = Stroke(2.3f))
}

/** Картинка во весь [box] с сохранением пропорций, по низу и по центру. */
private fun DrawScope.drawFitted(img: ru.vinteno.finni.ui.art.ArtImage, box: Size) {
    val cw = img.canvasWidth.toFloat()
    val ch = img.canvasHeight.toFloat()
    val k = minOf(box.width / cw, box.height / ch)
    translate((box.width - cw * k) / 2, box.height - ch * k) {
        scale(k, k, Offset.Zero) { drawImage(img.bitmap, Offset(img.left.toFloat(), img.top.toFloat())) }
    }
}

/** Толщина доски полки. */
val PLANK = 12.dp

/**
 * Доска полки во всю ширину [modifier], [PLANK] толщиной: вещи стоят на её верхней кромке. Картинка —
 * во всю ширину по верху, пропорции не меняются.
 */
@Composable
fun ShelfPlank(modifier: Modifier = Modifier) {
    Art.init(LocalContext.current)
    val png = !Art.missing(SHELF_PNG)
    Canvas(modifier.height(PLANK)) {
        if (png) {
            val img = Art.image(SHELF_PNG) ?: return@Canvas
            val k = size.width / img.canvasWidth
            scale(k, k, Offset.Zero) { drawImage(img.bitmap, Offset(img.left.toFloat(), img.top.toFloat())) }
            return@Canvas
        }
        val r = CornerRadius(4.dp.toPx())
        drawRoundRect(FinniColors.Ink.copy(alpha = 0.08f), Offset(0f, 6.dp.toPx()), size, r)
        drawRoundRect(WoodEdge, Offset(0f, 3.dp.toPx()), Size(size.width, size.height - 3.dp.toPx()), r)
        drawRoundRect(Wood, size = Size(size.width, size.height - 3.dp.toPx()), cornerRadius = r)
    }
}

/**
 * Корзина магазина под содержимым [content]: плетёная, во всю ширину. Размер — у раскладки; картинка
 * вписывается в него с сохранением пропорций.
 */
@Composable
fun Basket(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Art.init(LocalContext.current)
    val png = !Art.missing(BASKET_PNG)
    Box(
        modifier.drawBehind {
            if (png) {
                val img = Art.image(BASKET_PNG) ?: return@drawBehind
                val k = minOf(size.width / img.canvasWidth, size.height / img.canvasHeight)
                translate((size.width - img.canvasWidth * k) / 2, (size.height - img.canvasHeight * k) / 2) {
                    scale(k, k, Offset.Zero) { drawImage(img.bitmap, Offset(img.left.toFloat(), img.top.toFloat())) }
                }
                return@drawBehind
            }
            val top = 18.dp.toPx()
            val bottom = 26.dp.toPx()
            val shape = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(top), CornerRadius(top), CornerRadius(bottom), CornerRadius(bottom)))
            }
            translate(0f, 4.dp.toPx()) { drawPath(shape, FinniColors.Ink.copy(alpha = 0.08f)) }
            drawPath(shape, Wicker)
            clipPath(shape) {
                val step = 12.dp.toPx()
                var x = step - 2.dp.toPx()
                while (x < size.width) {
                    drawRect(WickerDark, Offset(x, 0f), Size(2.dp.toPx(), size.height))
                    x += step
                }
            }
            drawPath(shape, WoodEdge, style = Stroke(3.dp.toPx()))
        },
        content = content,
    )
}

/** Промежуток между тремя банками комнаты. */
private val JAR_GAP = 3.dp

/** Ширина трёх банок комнаты высотой [height]: стоят рядом, как один предмет. */
fun roomJarsWidth(height: Dp): Dp = height * JAR_RATIO * 3 + JAR_GAP * 2

/**
 * Три банки плана в комнате — те же банки, что на экране плана, только маленькие: Нужное, Хочу,
 * Копилка слева направо, крышка — цвет направления. В банках — [values], масштаб уровня общий по [scaleMax].
 */
@Composable
fun RoomJars(values: List<Int>, scaleMax: Int, height: Dp, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(JAR_GAP), verticalAlignment = Alignment.Bottom) {
        listOf(Direction.NEED, Direction.WANT, null).forEachIndexed { i, d ->
            Jar(values.getOrElse(i) { 0 }, scaleMax, d, height)
        }
    }
}

/** Календарь: высота к ширине. */
const val CALENDAR_RATIO = 1.18f

/**
 * Календарь — листок на стене, неделя обозначена цифрой [week]. Шапка листка тёплого дерева с двумя
 * кольцами, ниже — крупная цифра. Есть `prop_calendar.png` — картинка, цифра поверх неё; нет — рисунок
 * кодом. Место задаёт раскладка комнаты.
 */
@Composable
fun Calendar(week: Int, width: Dp, modifier: Modifier = Modifier) {
    Art.init(LocalContext.current)
    val png = !Art.missing(CALENDAR_PNG)
    val height = width * CALENDAR_RATIO
    Box(modifier.size(width, height)) {
        Canvas(Modifier.matchParentSize()) {
            if (png) {
                val img = Art.image(CALENDAR_PNG) ?: return@Canvas
                drawFitted(img, size)
                return@Canvas
            }
            val r = CornerRadius(6.dp.toPx())
            val head = size.height * 0.26f
            drawRoundRect(FinniColors.Ink.copy(alpha = 0.10f), Offset(0f, 3.dp.toPx()), size, r)
            drawRoundRect(Color(0xFFFFFBF2), size = size, cornerRadius = r)
            drawRoundRect(Wood, size = Size(size.width, head), cornerRadius = r)
            drawRect(Wood, Offset(0f, head / 2), Size(size.width, head / 2))
            drawRoundRect(FinniColors.StrokeStrong, size = size, cornerRadius = r, style = Stroke(1.5.dp.toPx()))
            // Кольца, на которых листок висит.
            listOf(0.3f, 0.7f).forEach { x ->
                drawLine(WoodEdge, Offset(size.width * x, -2.dp.toPx()), Offset(size.width * x, head * 0.55f), 3.dp.toPx(), StrokeCap.Round)
            }
        }
        // Цифра — часть картинки: диктор называет календарь по подписи предмета, «Неделя 1».
        Box(Modifier.matchParentSize().padding(top = height * 0.26f).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            Txt(week.toString(), FinniText.Title.copy(textAlign = TextAlign.Center))
        }
    }
}
