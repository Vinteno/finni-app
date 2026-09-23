package ru.vinteno.finni.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniText

@Composable
fun Txt(text: String, style: TextStyle = FinniText.Body, modifier: Modifier = Modifier) =
    BasicText(text, modifier, style)

@Composable
fun Icon(vector: ImageVector, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    val painter = rememberVectorPainter(vector)
    Canvas(modifier.size(size)) {
        with(painter) { draw(this@Canvas.size, colorFilter = ColorFilter.tint(tint)) }
    }
}

/** Монета: круг, заливка `coin` с мягким вертикальным градиентом, обводка `coin-edge` обязательна — §8.4, §10.5. */
@Composable
fun Coin(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val edge = 2.dp.toPx().coerceAtMost(r / 3)
        drawCircle(
            Brush.verticalGradient(listOf(lerp(FinniColors.Coin, Color.White, 0.25f), FinniColors.Coin), startY = 0f, endY = this.size.height),
            radius = r - edge / 2,
        )
        drawCircle(FinniColors.CoinEdge, radius = r - edge / 2, style = Stroke(edge))
    }
}

/** Кошелёк на каждом игровом экране, правый верхний угол, 48 dp: монета и число — §7.4. */
@Composable
fun Wallet(amount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = FinniDimens.WalletHeight)
            .background(FinniColors.Surface, RoundedCornerShape(FinniDimens.RadiusButton))
            .border(FinniDimens.Outline, FinniColors.CoinEdge, RoundedCornerShape(FinniDimens.RadiusButton))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Coin(24.dp)
        Txt(amount.toString(), FinniText.Title)
    }
}

/**
 * Ряд монет — §10.5. Длина линейна по величине: до 20 — отдельные монеты 16 dp с зазором
 * после каждой пятой, от 21 до 100 — лента с насечками через 10. Все ряды одного экрана
 * рисуются одним масштабом, выбранным по большему числу, — поэтому `scaleMax` общий.
 */
@Composable
fun CoinRow(value: Int, scaleMax: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().height(20.dp)) {
        val full = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val w = full.toPx()
            if (scaleMax <= 20) {
                val gaps = (scaleMax - 1).coerceAtLeast(0) / 5
                val step = minOf(18.dp.toPx(), (w - 8.dp.toPx() * gaps) / scaleMax.coerceAtLeast(1))
                val d = minOf(16.dp.toPx(), step - 1.dp.toPx())
                for (i in 0 until value) {
                    val x = i * step + (i / 5) * 8.dp.toPx() + d / 2
                    val c = Offset(x, size.height / 2)
                    drawCircle(FinniColors.Coin, d / 2 - 1.dp.toPx(), c)
                    drawCircle(FinniColors.CoinEdge, d / 2 - 1.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                }
            } else {
                val len = w * value / 100f
                val h = size.height
                drawRoundRect(FinniColors.Coin, size = androidx.compose.ui.geometry.Size(len, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
                drawRoundRect(FinniColors.CoinEdge, size = androidx.compose.ui.geometry.Size(len, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2), style = Stroke(2.dp.toPx()))
                var t = 10
                while (t < value) {
                    val x = w * t / 100f
                    drawLine(FinniColors.CoinEdge, Offset(x, 3.dp.toPx()), Offset(x, h - 3.dp.toPx()), 2.dp.toPx())
                    t += 10
                }
            }
        }
    }
}

/** Прогресс к цели ячейками, одна ячейка — 5 монет; число — подписью рядом, один раз (§10.9). */
@Composable
fun ProgressCells(filled: Int, total: Int, modifier: Modifier = Modifier, cell: Dp = 24.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { i ->
            val shape = RoundedCornerShape(minOf(FinniDimens.RadiusSmall, cell / 4))
            Box(
                Modifier.size(cell)
                    .background(if (i < filled) FinniColors.Coin else FinniColors.Surface, shape)
                    .border(FinniDimens.Outline, if (i < filled) FinniColors.CoinEdge else FinniColors.StrokeStrong, shape),
            )
        }
    }
}

/**
 * Плашка объяснения — §10.11: `surface`, скругление 24, обводка `stroke-strong`, слева иконка Финни.
 * Три строки одинаковой структуры при любом исходе. Касание закрывает.
 */
@Composable
fun ExplainPlate(lines: List<String>, onClose: () -> Unit, modifier: Modifier = Modifier, petIcon: @Composable () -> Unit) {
    val shape = RoundedCornerShape(FinniDimens.RadiusCard)
    Row(
        modifier.fillMaxWidth()
            .background(FinniColors.Surface, shape)
            .border(FinniDimens.Outline, FinniColors.StrokeStrong, shape)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClose)
            .padding(FinniDimens.CardPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(24.dp)) { petIcon() }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lines.forEach { Txt(it) }
        }
    }
}

/**
 * Диалог — §10.8: затемнение 40%, `surface`, скругление 24. Последствие — до выбора.
 * Действие — вторичная кнопка, отмена — главная. Модальных окон в игре ровно два.
 */
@Composable
fun FinniDialog(lines: List<String>, action: String?, onAction: () -> Unit, cancel: String, onCancel: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(FinniColors.Scrim)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .padding(FinniDimens.ScreenPadding),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(FinniDimens.RadiusCard)
        Column(
            Modifier.fillMaxWidth().background(FinniColors.Surface, shape).padding(FinniDimens.CardPadding + 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            lines.forEachIndexed { i, l -> Txt(l, if (i == 0) FinniText.Subtitle else FinniText.Body) }
            Box(Modifier.height(4.dp))
            if (action != null) SecondaryButton(action, onAction)
            MainButton(cancel, onCancel)
        }
    }
}

/** Метка направления: иконка плюс слово — цвет никогда не единственный носитель (§5.2). */
@Composable
fun DirectionLabel(icon: ImageVector, color: Color, text: String, modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = text }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, color, 20.dp)
        Txt(text, FinniText.Caption)
    }
}

@Composable
fun Spacer(h: Dp) = Box(Modifier.height(h).width(1.dp))
