package ru.vinteno.finni.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.ui.art.Art
import ru.vinteno.finni.ui.motion.anchor
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniText

/*
 * Части главного экрана поверх комнаты: вещи по своим непрозрачным краям, мягкие плашки,
 * значки потребностей, плашка накоплений у копилки, записка на стене.
 */

/**
 * Где на канве 512 × 512 лежит непрозрачная часть картинки предмета. По ней вещь ставится на пол
 * или на полку краем, а не прозрачным полем вокруг. Числа — из самих PNG и нужны до того, как
 * картинка декодирована: разметка не прыгает, пока слои грузятся.
 */
private val opaque = mapOf(
    "dver" to Rect(112f, 32f, 400f, 481f),
    "polka" to Rect(36f, 210f, 476f, 344f),
    "okno" to Rect(43f, 52f, 470f, 458f),
    "zapiska" to Rect(72f, 47f, 441f, 466f),
    "kopilka" to Rect(63f, 61f, 460f, 474f),
    "mylo" to Rect(44f, 121f, 468f, 407f),
    "miska" to Rect(53f, 114f, 459f, 418f),
    "posylka" to Rect(34f, 50f, 479f, 484f),
    "kacheli" to Rect(45f, 82f, 462f, 446f),
    "myachik" to Rect(45f, 46f, 467f, 468f),
)

/** Высота к ширине у непрозрачной части предмета. */
fun thingRatio(id: String): Float = opaque.getValue(id).let { it.height / it.width }

/** Доля высоты полки от её верха, где стоят вещи: середина верхней доски. */
const val SHELF_SURFACE = 0.16f

/** Доля высоты двери от её верха, где ручка. */
const val DOOR_HANDLE = 0.55f

private fun files(id: String): List<String> = when (id) {
    "kopilka" -> listOf("item_kopilka_obj")
    "food_kasha" -> listOf("item_kasha")
    "food_kasha_yagody" -> listOf("item_kasha", "item_yagody")
    "food_krupa" -> listOf("item_krupa")
    else -> listOf("item_$id")
}

/**
 * Вещь комнаты шириной [width]: непрозрачная часть картинки [id] ровно заполняет место, низ картинки —
 * низ места. [box] — чья рамка: слой еды рисуется в рамке миски и ложится в неё. [height] задаётся
 * только листку записки, который вытягивается под текст. Нет PNG — заглушка из [Picture].
 */
@Composable
fun Thing(
    id: String,
    width: Dp,
    modifier: Modifier = Modifier,
    box: String = id,
    height: Dp = width * thingRatio(box),
    description: String? = null,
) {
    Art.init(LocalContext.current)
    val names = files(id)
    if (names.any(Art::missing)) {
        Box(modifier.size(width, height), contentAlignment = Alignment.BottomCenter) {
            Picture(id, minOf(width, height), description = description)
        }
        return
    }
    val r = opaque.getValue(box)
    Canvas(modifier.size(width, height).semantics { description?.let { contentDescription = it } }) {
        // Пока слои декодируются, не рисуем ничего — это доли секунды при первом показе.
        val img = names.map { Art.image(it) ?: return@Canvas }
        val kx = size.width / r.width
        val ky = size.height / r.height
        img.forEach {
            drawImage(
                it.bitmap,
                dstOffset = IntOffset(((it.left - r.left) * kx).toInt(), ((it.top - r.top) * ky).toInt()),
                dstSize = IntSize((it.bitmap.width * kx).toInt(), (it.bitmap.height * ky).toInt()),
            )
        }
    }
}

/**
 * Мягкая плашка поверх мира — кремовая, полупрозрачная, с тенью вместо обводки. Тень — двумя
 * смещёнными вниз подложками: так она одинакова на любом устройстве и в снимках.
 */
fun Modifier.softPlate(radius: Dp): Modifier = drawBehind {
    val r = CornerRadius(radius.toPx())
    val far = 3.dp.toPx()
    drawRoundRect(FinniColors.Ink.copy(alpha = 0.06f), Offset(-1.dp.toPx(), far), Size(size.width + 2.dp.toPx(), size.height + 1.dp.toPx()), r)
    drawRoundRect(FinniColors.Ink.copy(alpha = 0.12f), Offset(0f, 2.dp.toPx()), size, r)
}.background(FinniColors.Surface.copy(alpha = PLATE_ALPHA), RoundedCornerShape(radius))

private const val PLATE_ALPHA = 0.92f

/** Подписи на плашках, листке и табличке — кегль подписи, жирнее для чтения поверх картинки. */
val PlateText = FinniText.Caption.copy(fontWeight = FontWeight.Bold)

/**
 * Значок потребности: круг с картинкой и слово под ним. Два состояния, и оба читаются без цвета:
 * закрыто — толстое кольцо `save`, картинка яркая, в углу кружок с галочкой; ещё нет — тонкое бледное
 * кольцо, картинка бледная, без галочки. Слово не меняется (ТЗ 3.6, инвариант 7).
 */
@Composable
fun NeedBadge(image: String, fallback: ImageVector, label: String, done: Boolean) {
    Art.init(LocalContext.current)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.size(NEED + 4.dp)) {
            Box(Modifier.size(NEED).softPlate(NEED / 2), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = (if (done) 4.dp else 2.dp).toPx()
                    drawCircle(if (done) FinniColors.Save else FinniColors.Stroke, size.minDimension / 2 - w / 2, style = Stroke(w))
                }
                val filter = if (done) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.3f) })
                val alpha = if (done) 1f else 0.45f
                if (Art.missing(image)) {
                    Icon(fallback, FinniColors.Ink.copy(alpha = alpha), 28.dp)
                } else Canvas(Modifier.size(34.dp)) {
                    val img = Art.image(image) ?: return@Canvas
                    val bw = img.bitmap.width.toFloat()
                    val bh = img.bitmap.height.toFloat()
                    val k = minOf(size.width / bw, size.height / bh)
                    translate((size.width - bw * k) / 2, (size.height - bh * k) / 2) {
                        scale(k, k, Offset.Zero) { drawImage(img.bitmap, alpha = alpha, colorFilter = filter) }
                    }
                }
            }
            if (done) Canvas(Modifier.align(Alignment.BottomEnd).size(20.dp)) {
                val r = size.minDimension / 2
                drawCircle(FinniColors.Surface, r)
                drawCircle(FinniColors.Save, r - 2.dp.toPx())
                val tick = Path().apply {
                    moveTo(size.width * 0.3f, size.height * 0.52f)
                    lineTo(size.width * 0.45f, size.height * 0.67f)
                    lineTo(size.width * 0.72f, size.height * 0.36f)
                }
                drawPath(tick, FinniColors.Surface, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Txt(label, PlateText)
    }
}

private val NEED = 52.dp

/** Кошелёк дома — плашкой с монетой. Число растёт вместе с прилётом монет, как у [Wallet]. */
@Composable
fun WalletPlate(amount: Int, modifier: Modifier = Modifier) {
    val flights = ru.vinteno.finni.ui.app().flights
    Row(
        modifier.anchor(flights, "wallet").heightIn(min = FinniDimens.WalletHeight).softPlate(FinniDimens.WalletHeight / 2)
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Coin(28.dp)
        Txt((amount - flights.walletPending).coerceAtLeast(0).toString(), FinniText.Subtitle)
    }
}

/**
 * Плашка накоплений над копилкой: хвостик смотрит вниз, на копилку, — [tailFromEnd] от правого края
 * плашки до середины копилки.
 */
@Composable
fun SavingsPlate(tailFromEnd: Dp, maxWidth: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.widthIn(max = maxWidth)
            .drawBehind {
                val t = TAIL.toPx()
                val x = (size.width - tailFromEnd.toPx()).coerceIn(t * 2, size.width - t * 2)
                val tail = Path().apply {
                    moveTo(x - t, size.height - 1f); lineTo(x + t, size.height - 1f); lineTo(x, size.height + t); close()
                }
                translate(0f, 2.dp.toPx()) { drawPath(tail, FinniColors.Ink.copy(alpha = 0.12f)) }
                drawPath(tail, FinniColors.Surface.copy(alpha = PLATE_ALPHA))
            }
            .softPlate(FinniDimens.RadiusSmall + 4.dp)
            .heightIn(min = FinniDimens.MinTouch)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

/** Высота хвостика плашки накоплений. */
val TAIL = 7.dp

/**
 * Записка, приколотая к стене: текущий шаг недели крупно — [step], под ним мельче задание недели
 * [task], пока оно не выполнено. Текст — в поле листка ниже кнопки-булавки: [TEXT_TOP] сверху,
 * [TEXT_SIDE] с боков, [TEXT_BOTTOM] снизу.
 */
@Composable
fun WallNote(step: String, task: String?, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(width, height)) {
        Thing("zapiska", width, height = height)
        Column(
            Modifier.fillMaxSize().padding(start = width * TEXT_SIDE, end = width * TEXT_SIDE, top = height * TEXT_TOP, bottom = height * TEXT_BOTTOM),
            verticalArrangement = Arrangement.spacedBy(NOTE_GAP, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Txt(step, NoteStep.copy(textAlign = TextAlign.Center))
            task?.let { Txt(it, NoteTask.copy(textAlign = TextAlign.Center)) }
        }
    }
}

/** Шаг на записке — кеглем кнопки: это и есть прежняя кнопка. Задание под ним — подписью. */
val NoteStep = FinniText.Button
val NoteTask = FinniText.Caption.copy(color = FinniColors.InkMute)
val NOTE_GAP = 4.dp

/** Высота полосы над предметом, где стоит значок шага, вместе с зазором до предмета. */
val MARK_BAND = 28.dp
private val MARK_W = 30.dp
private val MARK_H = 22.dp

/**
 * Значок текущего шага над предметом: маленькая мягкая плашка со стрелкой вниз. Неподвижный — ни
 * мигания, ни пульсации, ни покачивания (инвариант 4). Не нажимается и диктором не читается: шаг
 * читает записка. Стоит серединой на [centerX] от левого края родителя, верхом на [top].
 */
@Composable
fun StepMark(centerX: Dp, top: Dp, modifier: Modifier = Modifier) {
    Canvas(
        modifier.offset(x = centerX - MARK_W / 2, y = top).size(MARK_W, MARK_H)
            .softPlate(FinniDimens.RadiusSmall).clearAndSetSemantics { testTag = "mark" },
    ) {
        val w = size.width
        val h = size.height
        val arrow = Path().apply {
            moveTo(w * 0.5f, h * 0.78f)
            lineTo(w * 0.28f, h * 0.44f)
            moveTo(w * 0.5f, h * 0.78f)
            lineTo(w * 0.72f, h * 0.44f)
            moveTo(w * 0.5f, h * 0.78f)
            lineTo(w * 0.5f, h * 0.2f)
        }
        drawPath(arrow, FinniColors.Ink, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Реплика Финни — короткий пузырь с хвостиком вниз, к нему. Пузырь стоит серединой над [anchorX] от
 * левого края родителя, но не выходит за его края; хвостик всегда смотрит на [anchorX]. Без звука;
 * уходит при следующем касании.
 */
@Composable
fun SayBubble(text: String, anchorX: Dp, maxWidth: Dp, modifier: Modifier = Modifier) {
    var left by remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .layout { m, c ->
                val pl = m.measure(c.copy(minWidth = 0, maxWidth = minOf(c.maxWidth, maxWidth.roundToPx())))
                val x = (anchorX.roundToPx() - pl.width / 2).coerceIn(0, maxOf(0, c.maxWidth - pl.width))
                left = x.toFloat()
                layout(c.maxWidth, pl.height) { pl.place(x, 0) }
            }
            .drawBehind {
                val t = TAIL.toPx()
                val x = (anchorX.toPx() - left).coerceIn(t * 2, size.width - t * 2)
                val tail = Path().apply {
                    moveTo(x - t, size.height - 1f); lineTo(x + t, size.height - 1f); lineTo(x, size.height + t); close()
                }
                translate(0f, 2.dp.toPx()) { drawPath(tail, FinniColors.Ink.copy(alpha = 0.12f)) }
                drawPath(tail, FinniColors.Surface.copy(alpha = PLATE_ALPHA))
            }
            .softPlate(FinniDimens.RadiusSmall + 4.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Txt(text, PlateText) }
}

const val TEXT_SIDE = 0.1f
const val TEXT_TOP = 0.2f
const val TEXT_BOTTOM = 0.08f
