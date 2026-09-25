package ru.vinteno.finni.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.core.content.Impact
import ru.vinteno.finni.ui.art.Art
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniMotion
import ru.vinteno.finni.ui.theme.FinniText

/*
 * Детали экранов главы 1 поверх мира — мягкие, как на главном экране: кремовые плашки с тенью
 * вместо серой обводки, ценники, клетки копилки, раскладка «шапка — середина — низ».
 */

/** Линия пола — на столько выше нижнего блока, как на главном экране. */
val ROOM_LIFT = 12.dp

/** Насколько ниже стыка стены и пола стоят герои и вещи на полу — как Финни дома. */
val ROOM_DEPTH = 10.dp

/**
 * Раскладка экрана главы 1: сверху «назад» и кошелёк плашками, внизу кнопки, между ними — середина
 * высотой [content] `viewport`; её прокручивает сам экран, если она выше. [room] — фон комнаты во
 * весь экран, линия пола — над нижним блоком. Нижний блок меряется первым: пол и середина от него.
 */
@Composable
fun SoftScreen(
    onBack: (() -> Unit)?,
    backDescription: String,
    wallet: Int?,
    modifier: Modifier = Modifier,
    room: Boolean = false,
    bottomGap: Dp = FinniDimens.CardGap,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable (viewport: Dp) -> Unit,
) {
    SubcomposeLayout(modifier.fillMaxSize()) { c ->
        val w = c.maxWidth
        val h = c.maxHeight
        val top = subcompose(0) {
            if (onBack != null || wallet != null) {
                Row(
                    Modifier.fillMaxWidth().padding(start = FinniDimens.ScreenPadding, end = FinniDimens.ScreenPadding, top = FinniDimens.ScreenPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) BackButton(onBack, backDescription) else Box(Modifier.size(FinniDimens.BackButton))
                    Box(Modifier.weight(1f))
                    if (wallet != null) WalletPlate(wallet)
                }
            } else Box(Modifier.size(FinniDimens.ScreenPadding))
        }.map { it.measure(Constraints(maxWidth = w)) }
        val bot = subcompose(1) {
            if (bottom != null) Column(
                Modifier.fillMaxWidth().padding(horizontal = FinniDimens.ScreenPadding).padding(top = 8.dp, bottom = FinniDimens.BottomGap),
                verticalArrangement = Arrangement.spacedBy(bottomGap),
                content = bottom,
            )
        }.map { it.measure(Constraints(minWidth = w, maxWidth = w)) }
        val topH = top.maxOfOrNull { it.height } ?: 0
        val botH = bot.maxOfOrNull { it.height } ?: 0
        val floor = (h - botH - ROOM_LIFT.roundToPx()).toFloat()
        val back = if (room) subcompose(2) { Canvas(Modifier.fillMaxSize()) { drawRoom(floor) } }.map { it.measure(Constraints.fixed(w, h)) } else emptyList()
        val midH = (h - topH - botH).coerceAtLeast(0)
        val mid = subcompose(3) { content(midH.toDp()) }.map { it.measure(Constraints.fixed(w, midH)) }
        layout(w, h) {
            back.forEach { it.place(0, 0) }
            mid.forEach { it.place(0, topH) }
            top.forEach { it.place(0, 0) }
            bot.forEach { it.place(0, h - botH) }
        }
    }
}

/**
 * Нажимаемое с откликом, как кнопки игры (гайд §8.3): при нажатии лицо опускается на [lip] за 120 мс.
 * Губа [lipColor] под лицом; у мягкой плашки она прозрачная — губой служит её собственная тень.
 */
@Composable
fun PressBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lip: Dp = FinniDimens.LipSecondary,
    lipColor: Color = Color.Transparent,
    shape: Shape = RoundedCornerShape(FinniDimens.RadiusButton),
    description: String? = null,
    face: @Composable (Modifier) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val drop by animateDpAsState(if (pressed) lip else 0.dp, tween(FinniMotion.PRESS_MS), label = "press")
    Box(
        modifier.semantics { description?.let { contentDescription = it } }
            .clickable(source, indication = null, role = Role.Button, onClick = onClick),
    ) {
        if (lipColor != Color.Transparent) Box(Modifier.matchParentSize().padding(top = lip).background(lipColor, shape))
        face(Modifier.offset(y = drop).padding(bottom = lip))
    }
}

/** Выбранное читается формой, а не только цветом (гайд §10.3): рамка `action` 3 dp, подложка `action-soft`, галочка. */
fun Modifier.picked(selected: Boolean, radius: Dp): Modifier =
    if (selected) background(FinniColors.ActionSoft, RoundedCornerShape(radius)).border(3.dp, FinniColors.Action, RoundedCornerShape(radius))
    else this

/** Галочка выбранного: белая на синем, не зелёная (§9.2). */
@Composable
fun CheckMark(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(FinniColors.Action, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Check, Color.White, size * 0.75f)
    }
}

/** Мягкая карточка выбора: плашка с тенью; выбранная — рамка и галочка в правом верхнем углу. */
@Composable
fun SoftCard(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val r = FinniDimens.RadiusCard - 4.dp
    PressBox(onClick, modifier, shape = RoundedCornerShape(r)) { m ->
        Box(m.softPlate(r).picked(selected, r)) {
            content()
            if (selected) CheckMark(24.dp, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
    }
}

/** Вторичная кнопка со своим содержимым: обводка 2 dp `action`, губа 2 dp, без заливки — §10.2. */
@Composable
fun ChoiceButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(FinniDimens.RadiusButton)
    PressBox(onClick, modifier.fillMaxWidth(), lipColor = FinniColors.Action, shape = shape) { m ->
        Box(
            m.fillMaxWidth().heightIn(min = FinniDimens.SecondaryButtonHeight)
                .background(FinniColors.Surface, shape).border(FinniDimens.Outline, FinniColors.Action, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) { content() }
    }
}

/**
 * Дети одной высоты — по самому высокому, по ширине столбца: две кнопки итога растут вместе,
 * когда при крупном шрифте в одной из них числа уходят под название.
 */
@Composable
fun EqualColumn(gap: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { ms, c ->
        val w = c.maxWidth
        val h = ms.maxOfOrNull { it.minIntrinsicHeight(w) } ?: 0
        val placed = ms.map { it.measure(Constraints(minWidth = w, maxWidth = w, minHeight = h, maxHeight = maxOf(h, c.minHeight))) }
        val g = gap.roundToPx()
        val total = placed.sumOf { it.height } + g * (placed.size - 1).coerceAtLeast(0)
        layout(w, total) {
            var y = 0
            placed.forEach { it.place(0, y); y += it.height + g }
        }
    }
}

/**
 * Плашка с хвостиком вверх — строка «хватит» под банкой «Копилка». Хвостик на [tailX] от левого края
 * плашки: сведения исходят от банки, как плашка накоплений дома исходит от копилки.
 */
@Composable
fun UpTailPlate(tailX: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .drawBehind {
                val t = TAIL.toPx()
                val x = tailX.toPx().coerceIn(t * 2, size.width - t * 2)
                val tail = Path().apply { moveTo(x - t, 1f); lineTo(x + t, 1f); lineTo(x, -t); close() }
                drawPath(tail, FinniColors.Surface.copy(alpha = 0.92f))
            }
            .softPlate(FinniDimens.RadiusSmall + 8.dp)
            .heightIn(min = FinniDimens.MinTouch)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

/** Картинка потребности для кольца — та же, что у значков дома: миска, мыло, шарф. */
fun needImage(i: Impact): String = when (i) {
    Impact.FED -> "item_miska"
    Impact.CLEAN -> "item_mylo"
    Impact.WARM -> "acc_scarf"
}

/**
 * Значок потребности без подписи — картинка в кольце, как у значка «закрыто» на главном экране,
 * только меньше и без галочки: ценник говорит, какую потребность закрывает вещь.
 */
@Composable
fun NeedRing(impact: Impact, size: Dp, modifier: Modifier = Modifier) {
    Art.init(LocalContext.current)
    val image = needImage(impact)
    Box(modifier.size(size).background(FinniColors.Surface, CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = maxOf(2.dp.toPx(), this.size.minDimension * 0.08f)
            drawCircle(FinniColors.Save, this.size.minDimension / 2 - w / 2, style = Stroke(w))
        }
        if (Art.missing(image)) Icon(FinniIcons.impact(impact), FinniColors.Ink, size * 0.6f)
        else Canvas(Modifier.size(size * 0.66f)) {
            val img = Art.image(image) ?: return@Canvas
            val bw = img.bitmap.width.toFloat()
            val bh = img.bitmap.height.toFloat()
            val k = minOf(this.size.width / bw, this.size.height / bh)
            translate((this.size.width - bw * k) / 2, (this.size.height - bh * k) / 2) {
                scale(k, k, Offset.Zero) { drawImage(img.bitmap) }
            }
        }
    }
}

/**
 * Ценник вещи на полке: монета и цена, рядом значок потребности или звезда и «Хочу». Не помещается
 * в строку (крупный шрифт) — «Хочу» уходит строкой ниже цены. Выбранный — рамкой и галочкой.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PriceTag(price: Int, impact: Impact?, want: String?, selected: Boolean, modifier: Modifier = Modifier) {
    val r = FinniDimens.RadiusSmall + 4.dp
    Box(modifier) {
        FlowRow(
            Modifier.softPlate(r).picked(selected, r).heightIn(min = 34.dp).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Coin(18.dp)
                Txt(price.toString(), FinniText.Button)
            }
            impact?.let { NeedRing(it, 26.dp) }
            want?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(FinniIcons.Want, FinniColors.Want, 18.dp)
                    Txt(it, PlateText)
                }
            }
        }
        if (selected) CheckMark(20.dp, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp))
    }
}

/**
 * Ряд итога — [CoinRow] с прежней геометрией и масштабом (гайд §10.5). До 20 — отдельные монеты,
 * как было. Больше — столбик монет на боку: тонкая насечка на каждую монету, заметная — на каждые десять.
 */
@Composable
fun CoinRoll(value: Int, scaleMax: Int, modifier: Modifier = Modifier) {
    if (scaleMax <= 20) {
        CoinRow(value, scaleMax, modifier)
        return
    }
    Canvas(modifier.fillMaxWidth().height(20.dp)) {
        val w = size.width
        val h = size.height
        val len = w * value / 100f
        if (len <= 0f) return@Canvas
        val r = CornerRadius(4.dp.toPx())
        drawRoundRect(
            Brush.verticalGradient(listOf(lerp(FinniColors.Coin, Color.White, 0.3f), FinniColors.Coin, lerp(FinniColors.Coin, FinniColors.CoinEdge, 0.35f))),
            size = Size(len, h), cornerRadius = r,
        )
        val step = w / 100f
        for (i in 1 until value) {
            val x = step * i
            val ten = i % 10 == 0
            drawLine(
                FinniColors.CoinEdge.copy(alpha = if (ten) 1f else 0.45f),
                Offset(x, if (ten) 3.dp.toPx() else 5.dp.toPx()), Offset(x, h - if (ten) 3.dp.toPx() else 5.dp.toPx()),
                (if (ten) 2.dp else 1.dp).toPx(),
            )
        }
        drawRoundRect(FinniColors.CoinEdge, size = Size(len, h), cornerRadius = r, style = Stroke(2.dp.toPx()))
    }
}

/** Плашка объяснения на бумажном листе: песочная, без обводки, слева голова Финни. */
@Composable
fun SoftExplain(lines: List<String>, modifier: Modifier = Modifier, pet: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().background(FinniColors.BgSand, RoundedCornerShape(FinniDimens.RadiusButton)).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(24.dp)) { pet() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { lines.forEach { Txt(it) } }
    }
}

/**
 * Где низ непрозрачной части картинки на канве 512: подарки и открытка стоят на полу низом, а не
 * прозрачным полем под ними. Числа — из самих PNG, нужны до декодирования.
 */
private val foot = mapOf(
    "podarok_kniga" to 493f, "podarok_myach" to 480f, "podarok_samokat" to 487f,
    "otkrytka" to 495f, "myachik" to 468f,
)

/** Картинка [size] × [size], опущенная так, что низ нарисованного — низ места: вещь стоит, а не висит. */
@Composable
fun StandPicture(id: String, size: Dp, modifier: Modifier = Modifier, description: String? = null) {
    val drop = foot[id]?.takeIf { !Art.missing("item_$id") }?.let { size * ((512f - it) / 512f) } ?: 0.dp
    Box(modifier.size(size)) { Picture(id, size, Modifier.offset(y = drop), description) }
}

/**
 * Клетки копилки для задания F5: по 5 монет в клетке. Полные — монетой; те, что уйдут на мячик, —
 * пунктирной рамкой и бледной заливкой: читается формой, не только цветом; пустые — белые.
 */
@Composable
fun TakeCells(filled: Int, taking: Int, total: Int, cell: Dp, modifier: Modifier = Modifier, gap: Dp = 4.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(total) { i ->
            val kind = when {
                i < filled - taking -> 0
                i < filled -> 1
                else -> 2
            }
            Canvas(Modifier.size(cell)) {
                val r = CornerRadius(minOf(FinniDimens.RadiusSmall, cell / 4).toPx())
                val sw = FinniDimens.Outline.toPx()
                val inset = Offset(sw / 2, sw / 2)
                val s = Size(size.width - sw, size.height - sw)
                when (kind) {
                    0 -> {
                        drawRoundRect(FinniColors.Coin, cornerRadius = r)
                        drawRoundRect(FinniColors.CoinEdge, inset, s, r, style = Stroke(sw))
                    }
                    1 -> {
                        drawRoundRect(lerp(FinniColors.Coin, Color.White, 0.6f), cornerRadius = r)
                        val dash = 4.dp.toPx()
                        drawRoundRect(FinniColors.CoinEdge, inset, s, r, style = Stroke(sw * 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.7f))))
                    }
                    else -> {
                        drawRoundRect(FinniColors.Surface, cornerRadius = r)
                        drawRoundRect(FinniColors.StrokeStrong, inset, s, r, style = Stroke(sw))
                    }
                }
            }
        }
    }
}
