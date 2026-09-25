package ru.vinteno.finni.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.core.engine.Direction
import ru.vinteno.finni.core.model.Fur
import ru.vinteno.finni.ui.art.Art
import ru.vinteno.finni.ui.art.FinniSpec
import ru.vinteno.finni.ui.motion.SlideUp
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniDimens

/*
 * Детали первых экранов и окон магазина в мягком стиле главы 1: метка направления вместо точек,
 * кнопка выбора в лотке, кружок настоящего меха, кремовый лоток и окно, выезжающее снизу.
 */

/**
 * Метка направления на знакомстве: иконка и слово. Текущая — подложка и рамка цвета направления,
 * остальные бледные. Не нажимается: без губы, без тени, без отклика — это не кнопка, а «одна из трёх».
 */
@Composable
fun DirectionTag(d: Direction?, text: String, current: Boolean, modifier: Modifier = Modifier) {
    val st = directionStyle(d)
    val shape = RoundedCornerShape(FinniDimens.RadiusButton - 2.dp)
    Row(
        modifier.heightIn(min = FinniDimens.MinTouch)
            .semantics { selected = current }
            .then(
                if (current) Modifier.background(st.bg, shape).border(FinniDimens.Outline, st.color, shape)
                else Modifier.alpha(0.5f).background(FinniColors.Surface.copy(alpha = 0.9f), shape),
            )
            .padding(horizontal = TAG_PAD, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TAG_GAP, Alignment.CenterHorizontally),
    ) {
        Icon(st.icon, st.color, TAG_ICON)
        Txt(text, PlateText)
    }
}

/** Ширина метки со словом [text] в одну строку: слово в метке не переносится. */
@Composable
fun directionTagWidth(text: String): Dp = textWidth(text, PlateText) + TAG_ICON + TAG_GAP + TAG_PAD * 2

private val TAG_PAD = 4.dp
private val TAG_GAP = 4.dp
private val TAG_ICON = 20.dp

/** Мягкая тень вещи на полу: овал под низом картинки, тёплый, не серый. */
@Composable
fun FloorShadow(width: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width, width * 0.1f)) { drawOval(FloorShade) }
}

private val FloorShade = Color(0x2E5A3A22)

/**
 * Кнопка выбора в лотке: мягкая плашка с тенью. Выбранная — `picked` и галочка на углу, снаружи:
 * внутри узкой кнопки с именем она легла бы на слово.
 */
@Composable
fun PickButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val r = FinniDimens.RadiusButton
    Box(modifier.semantics { this.selected = selected }) {
        PressBox(onClick, Modifier.matchParentSize(), shape = RoundedCornerShape(r), description = description) { m ->
            Box(m.fillMaxSize().softPlate(r).picked(selected, r), contentAlignment = Alignment.Center, content = content)
        }
        if (selected) CheckMark(20.dp, Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp))
    }
}

/** Где на канве Финни 640 × 960 лоб головы — ровный мех без глаз и контура. */
private val FOREHEAD = Rect(292f, 238f, 340f, 286f)

/** Цвет меха, пока картинка головы не прочитана, — тот же, что у заглушки Финни. */
fun furColor(f: Fur) = when (f) {
    Fur.GINGER -> Color(0xFFF8994E)
    Fur.BLUE -> Color(0xFF8FA8BE)
    Fur.BROWN -> Color(0xFFA9794F)
}

/**
 * Кружок меха: вырезан из картинки головы этого окраса, с тонкой тёмной обводкой, как контур Финни.
 * Ребёнок выбирает мех, а не краску. Пока картинка не готова — кружок его цвета.
 */
@Composable
fun FurSwatch(fur: Fur, size: Dp, modifier: Modifier = Modifier) {
    Art.init(LocalContext.current)
    val name = FinniSpec.body(fur, "head")
    Canvas(modifier.size(size)) {
        val circle = Path().apply { addOval(Rect(Offset.Zero, this@Canvas.size)) }
        drawCircle(furColor(fur))
        Art.image(name)?.let { img ->
            clipPath(circle) {
                drawImage(
                    img.bitmap,
                    srcOffset = IntOffset((FOREHEAD.left - img.left).toInt(), (FOREHEAD.top - img.top).toInt()),
                    srcSize = IntSize(FOREHEAD.width.toInt(), FOREHEAD.height.toInt()),
                    dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
        val w = 1.5f.dp.toPx()
        drawCircle(FurOutline, this.size.minDimension / 2 - w / 2, style = Stroke(w))
    }
}

private val FurOutline = Color(0xFF5A3A22)

/** Кремовый лоток снизу во всю ширину: скругление сверху, мягкая тень вверх на мир над ним. */
fun Modifier.tray(): Modifier = drawBehind {
    val r = CornerRadius(TRAY_RADIUS.toPx())
    drawRoundRect(FinniColors.Ink.copy(alpha = 0.05f), Offset(0f, -4.dp.toPx()), Size(size.width, size.height), r)
    drawRoundRect(FinniColors.Ink.copy(alpha = 0.07f), Offset(0f, -2.dp.toPx()), Size(size.width, size.height), r)
}.background(FinniColors.BgSand, RoundedCornerShape(topStart = TRAY_RADIUS, topEnd = TRAY_RADIUS))

val TRAY_RADIUS = 24.dp

/**
 * Окно поверх экрана (гайд §10.8, решение I44): кремовое, выезжает снизу, как плашка (§7.4), во всю
 * ширину, скругление сверху 24 dp. Фон под ним затемнён тёплым тоном и плотно: экран под окном не
 * читается, окно — отдельный экран для потолка 25 слов (QA-M6). Касание мимо окна ничего не делает.
 * Окно выше экрана (шрифт ×2,0) — прокручивается содержимое, кнопки внизу окна видны всегда.
 */
@Composable
fun FinniSheet(buttons: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().testTag("dialog").background(SheetScrim)
            // Касания не уходят на экран под окном; щелчка у фона нет — он не кнопка.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        SlideUp(true, Modifier.padding(top = SHEET_TOP)) {
            Column(
                Modifier.fillMaxWidth().background(FinniColors.BgSand, RoundedCornerShape(topStart = TRAY_RADIUS, topEnd = TRAY_RADIUS))
                    .padding(top = 20.dp, bottom = FinniDimens.ScreenPadding),
            ) {
                val scroll = rememberScrollState()
                Column(
                    Modifier.weight(1f, fill = false).padding(horizontal = SHEET_SIDE).scrollHint(scroll).verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )
                Column(
                    Modifier.padding(horizontal = SHEET_SIDE).padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = buttons,
                )
            }
        }
    }
}

/** Затемнение под окном: тёплый тёмно-коричневый, плотный — мир под окном угадывается, текст не читается. */
private val SheetScrim = Color(0xEB3D2A1B)

private val SHEET_TOP = 24.dp
val SHEET_SIDE = 18.dp
