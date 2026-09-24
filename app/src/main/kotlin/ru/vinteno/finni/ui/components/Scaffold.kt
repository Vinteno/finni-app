package ru.vinteno.finni.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.RiceBowl
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Soap
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.core.content.Impact
import ru.vinteno.finni.core.engine.Direction
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniText

/** Иконки — открытая библиотека Material Icons (Apache 2.0), решение H3. Иконка всегда с подписью. */
object FinniIcons {
    val Need: ImageVector = Icons.Outlined.Restaurant
    val Want: ImageVector = Icons.Outlined.StarOutline
    val Save: ImageVector = Icons.Outlined.Savings
    val Fed: ImageVector = Icons.Outlined.RiceBowl
    val Clean: ImageVector = Icons.Outlined.Soap
    val Warm: ImageVector = Icons.Outlined.Checkroom

    fun impact(i: Impact): ImageVector = when (i) {
        Impact.FED -> Fed
        Impact.CLEAN -> Clean
        Impact.WARM -> Warm
    }
}

data class DirectionStyle(val icon: ImageVector, val color: Color, val bg: Color, val labelKey: String)

fun directionStyle(d: Direction?) = when (d) {
    Direction.NEED -> DirectionStyle(FinniIcons.Need, FinniColors.Need, FinniColors.NeedBg, "plan.need")
    Direction.WANT -> DirectionStyle(FinniIcons.Want, FinniColors.Want, FinniColors.WantBg, "plan.want")
    null -> DirectionStyle(FinniIcons.Save, FinniColors.Save, FinniColors.SaveBg, "plan.save")
}

/**
 * Раскладка любого игрового экрана — гайд §7.4: «назад» всегда слева сверху (или пустое место
 * под него), кошелёк справа сверху, заголовок одной строкой под ними, главное действие внизу
 * во всю ширину. Содержимое прокручивается: вёрстка не ломается при шрифте ×2,0.
 */
@Composable
fun GameScreen(
    title: String?,
    wallet: Int?,
    onBack: (() -> Unit)?,
    backDescription: String,
    modifier: Modifier = Modifier,
    titleAside: (@Composable () -> Unit)? = null,
    scroll: ScrollState = rememberScrollState(),
    bottomInScroll: Boolean = false,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().padding(horizontal = FinniDimens.ScreenPadding)) {
        // Экраны первого запуска без «назад» и кошелька: пустая полоса сверху съедала бы 64 dp.
        if (onBack != null || wallet != null) {
            Row(Modifier.fillMaxWidth().padding(top = FinniDimens.ScreenPadding), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) BackButton(onBack, backDescription) else Box(Modifier.size(FinniDimens.BackButton))
                Box(Modifier.weight(1f))
                if (wallet != null) Wallet(wallet)
            }
        } else Box(Modifier.size(FinniDimens.ScreenPadding))
        // Низ в прокрутке — когда кнопки читаются после текста (итог): на низком экране сначала
        // объяснение, потом выбор, и ничего не прячется под кнопками. На высоком низ всё равно внизу.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val viewport = maxHeight
        Column(
            Modifier.fillMaxSize().scrollHint(scroll).verticalScroll(scroll)
                .then(if (bottomInScroll) Modifier.heightIn(min = viewport) else Modifier),
            verticalArrangement = Arrangement.spacedBy(FinniDimens.CardGap),
        ) {
            if (title != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = FinniDimens.TitleTop - FinniDimens.CardGap),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    titleAside?.invoke()
                    Txt(title, FinniText.Title, Modifier.weight(1f))
                }
            }
            content()
            Box(Modifier.size(8.dp))
            if (bottomInScroll && bottom != null) {
                Box(Modifier.weight(1f))
                Column(
                    Modifier.fillMaxWidth().padding(bottom = FinniDimens.BottomGap),
                    verticalArrangement = Arrangement.spacedBy(FinniDimens.CardGap),
                    content = bottom,
                )
            }
        }
        }
        if (bottom != null && !bottomInScroll) {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = FinniDimens.BottomGap),
                verticalArrangement = Arrangement.spacedBy(FinniDimens.CardGap),
                content = bottom,
            )
        }
    }
}

/**
 * Что экран длиннее окна, ребёнок видит без слов: справа в поле экрана — полоса прокрутки.
 * Черты у нижнего края нет: по срезанному тексту она читалась как зачёркивание. Без тени и градиента (гайд §8.2, §8.4), без движения.
 * Рисуется поверх содержимого, но в координатах окна, поэтому стоит до `verticalScroll`.
 */
fun Modifier.scrollHint(state: ScrollState): Modifier = drawWithContent {
    drawContent()
    val max = state.maxValue
    // Хвостовой отступ (8 dp и зазор 12 перед ним) — не «ещё содержимое»: из-за него полосы нет.
    if (max <= 24.dp.toPx() || max == Int.MAX_VALUE) return@drawWithContent
    val w = 4.dp.toPx()
    val h = size.height
    val thumb = (h * h / (h + max)).coerceAtLeast(32.dp.toPx())
    val y = (h - thumb) * state.value / max
    // Полоса — в поле экрана справа, между краем содержимого и краем телефона.
    drawRoundRect(FinniColors.StrokeStrong, Offset(size.width + 6.dp.toPx(), y), Size(w, thumb), CornerRadius(w / 2))
}

/**
 * Крупный системный шрифт (от ×1,3): карточки в ряд по 104 dp перестают вмещать слова, и
 * экраны переходят на раскладку строками — вёрстка не ломается и слова не рвутся (гайд §6.5).
 */
@Composable
fun bigFont(): Boolean = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
