package ru.vinteno.finni.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.ui.art.Art
import ru.vinteno.finni.ui.theme.FinniColors

/*
 * Банка плана, полка-витрина и корзина магазина. Картинки для них закажем художнику; пока их нет,
 * они рисуются кодом по макету. Правило то же, что у вещей комнаты: есть PNG в art/app —
 * рисуется картинка, нет — рисунок кодом. Место и размер задаёт раскладка, от варианта они не
 * зависят. Цвета дерева и стекла — цвета мира, как у заглушек в Pictures.kt, а не интерфейса.
 */

private const val JAR_PNG = "prop_jar"
private const val SHELF_PNG = "prop_shelf"
private const val BASKET_PNG = "prop_basket"

private val Wood = Color(0xFFD9A86C)
private val WoodEdge = Color(0xFFB98549)
private val Wicker = Color(0xFFE4BC85)
private val WickerDark = Color(0xFFD7A965)

/** Банка 96 × 150 в единицах рисунка: ширина к высоте. */
const val JAR_RATIO = 96f / 150f

/**
 * Банка направления высотой [height]: крышка — цвет направления [lid] с обводкой [lidEdge], внутри
 * монеты рядами по 5, снизу вверх. Одна монета — одна нарисованная. Шаг рядов — один на все банки
 * экрана: по [scaleMax] — большему из дохода недели и самого большого числа в банках. Пока ряды
 * помещаются, шаг естественный; больше — ряды сжимаются, и столбик не выходит за горло банки.
 */
@Composable
fun Jar(value: Int, scaleMax: Int, lid: Color, lidEdge: Color, height: Dp, modifier: Modifier = Modifier) {
    Art.init(LocalContext.current)
    val png = !Art.missing(JAR_PNG)
    Canvas(modifier.size(height * JAR_RATIO, height)) {
        val k = size.height / 150f
        scale(k, k, Offset.Zero) {
            val body = jarBody()
            if (png) {
                val img = Art.image(JAR_PNG)
                if (img != null) drawFitted(img, Size(96f, 150f))
            } else drawPath(body, Color.White.copy(alpha = 0.62f))
            // Монеты — внутри стекла: не выходят за стенки.
            clipPath(body) { coins(value, scaleMax) }
            if (!png) {
                drawPath(body, FinniColors.StrokeStrong, style = Stroke(2f))
                drawLine(Color.White.copy(alpha = 0.8f), Offset(11f, 50f), Offset(11f, 80f), 4f, StrokeCap.Round)
            }
            // Крышка — цвет направления и в картинке, и в рисунке: по ней банку узнают.
            val r = CornerRadius(5f)
            drawRoundRect(lid, Offset(12f, 6f), Size(72f, 16f), r)
            drawRoundRect(lidEdge, Offset(12f, 6f), Size(72f, 16f), r, style = Stroke(2f))
        }
    }
}

private fun jarBody() = Path().apply {
    moveTo(14f, 26f); quadraticTo(14f, 20f, 20f, 20f); lineTo(76f, 20f); quadraticTo(82f, 20f, 82f, 26f)
    lineTo(82f, 30f); quadraticTo(92f, 36f, 92f, 50f); lineTo(92f, 140f); quadraticTo(92f, 150f, 82f, 150f)
    lineTo(14f, 150f); quadraticTo(4f, 150f, 4f, 140f); lineTo(4f, 50f); quadraticTo(4f, 36f, 14f, 30f); close()
}

/** Самый нижний ряд и горло банки — где центр монеты может быть. */
private const val COIN_BASE = 136f
private const val COIN_TOP = 38f
private const val COIN_PITCH = 12.5f

private fun DrawScope.coins(value: Int, scaleMax: Int) {
    val rows = (maxOf(scaleMax, value, 1) + 4) / 5
    val pitch = if (rows <= 1) COIN_PITCH else minOf(COIN_PITCH, (COIN_BASE - COIN_TOP) / (rows - 1))
    val rx = 7.8f
    val ry = rx * 0.62f
    val step = 16.4f
    val x0 = (96f - 5 * step) / 2 + step / 2
    for (i in 0 until value) {
        val c = Offset(x0 + (i % 5) * step, COIN_BASE - (i / 5) * pitch)
        drawOval(FinniColors.Coin, Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2))
        drawOval(FinniColors.CoinEdge, Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2), style = Stroke(1.6f))
    }
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
