package ru.vinteno.finni.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.core.content.Category
import ru.vinteno.finni.core.content.Shelf
import ru.vinteno.finni.core.content.TaskTemplate
import ru.vinteno.finni.core.engine.Checkout
import ru.vinteno.finni.core.engine.Direction
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.ui.app
import ru.vinteno.finni.ui.components.Coin
import ru.vinteno.finni.ui.components.DirectionLabel
import ru.vinteno.finni.ui.components.ExplainPlate
import ru.vinteno.finni.ui.components.FinniDialog
import ru.vinteno.finni.ui.components.FinniIcons
import ru.vinteno.finni.ui.components.GameScreen
import ru.vinteno.finni.ui.components.Icon
import ru.vinteno.finni.ui.components.MainButton
import ru.vinteno.finni.ui.components.Picture
import ru.vinteno.finni.ui.components.PressCard
import ru.vinteno.finni.ui.components.SecondaryButton
import ru.vinteno.finni.ui.components.Txt
import ru.vinteno.finni.ui.components.bigFont
import ru.vinteno.finni.ui.components.directionStyle
import ru.vinteno.finni.ui.pet.Reaction
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniText

/** Какое окно открыто поверх корзины. Модальных окон в игре ровно два вида — нехватка и копилка. */
private enum class Ask { NONE, WANT, SAVINGS, NO_SAVINGS }

/**
 * Магазин — единственное место, где уходят деньги (сценарий главы 1, шаг 4). Полки со ступеньками,
 * хотелка главы, в неделю F5 — выбор «мячик или подарок». Корзина и есть подтверждение покупки:
 * список с ценой и меткой категории, сумма, «Купить». Кнопка не гаснет при нехватке — открывается окно.
 */
@Composable
fun ShopScreen(s: GameState, onLeave: () -> Unit) {
    val a = app()
    val g = a.game
    val w = s.week ?: return
    val week = g.weekContent(s)
    // Выбранная ступенька каждой полки; хотелка — отдельно. Ступеньки одной полки взаимоисключающие.
    val tiers = remember(w.number) { mutableStateMapOf<String, Int>() }
    var wantPicked by remember(w.number) { mutableStateOf(false) }
    var ask by remember { mutableStateOf(Ask.NONE) }
    var agreedWant by remember { mutableStateOf(false) }
    var ballPlate by remember { mutableStateOf<List<String>?>(null) }
    val boughtThisVisit = remember { mutableStateListOf<String>() }

    val wantId = g.content.chapter1.chapterWantId
    val cart = week.shelves.flatMap { sh -> tiers[sh.id]?.let { sh.tiers[it] } ?: emptyList() } +
        (if (wantPicked && !g.owns(s, wantId)) listOf(wantId) else emptyList())

    fun leave() {
        a.act(g::leaveShop)
        a.pendingPlate = a.explain.afterShop(a.state.value, boughtThisVisit.toList())
        onLeave()
    }

    fun buy(q: Checkout, withWant: Boolean, withSavings: Boolean) {
        if (a.act { g.buy(it, cart, agreedWant = withWant, agreedSavings = withSavings) }) {
            boughtThisVisit += q.items.map { it.id }
            tiers.clear(); wantPicked = false
            // `доволен` одинаков для любого набора — инвариант 8.
            a.react(Reaction.HAPPY)
        }
        ask = Ask.NONE; agreedWant = false
    }

    /** Порядок один для любой покупки: своё направление → «Хочу» → копилка с отдельным окном. */
    fun proceed(q: Checkout, wantOk: Boolean) {
        when {
            q.asksWant && !wantOk -> ask = Ask.WANT
            q.asksSavings && q.savingsAfter < 0 -> ask = Ask.NO_SAVINGS
            q.asksSavings -> ask = Ask.SAVINGS
            else -> buy(q, wantOk, false)
        }
    }

    Box(Modifier.fillMaxSize()) {
        GameScreen(
            title = a.t("shop.title"),
            wallet = s.progress.wallet,
            onBack = ::leave,
            backDescription = a.t("common.back"),
            titleAside = { PetHead(s, live = true) },
            bottom = if (cart.isEmpty()) null else ({
                CartBox(s, cart)
                MainButton(a.t("shop.buy"), onClick = { proceed(g.quote(s, cart), false) })
            }),
        ) {
            // Режим задания F5: выбор стоит первым — это задание недели (сценарий, неделя 2, шаг 4).
            val task = week.taskId?.let { g.content.chapter1.task(it) }
            if (task?.template == TaskTemplate.CHOICE && !w.taskDone) BallChoice(s) { took ->
                if (a.act { g.chooseBall(it, took) }) {
                    // Одна и та же реакция при обоих решениях — самое опасное место главы для инварианта 8.
                    a.react(Reaction.HAPPY)
                    ballPlate = a.explain.afterBall(a.state.value, took)
                }
            }
            ballPlate?.let { lines -> ExplainPlate(lines, onClose = { ballPlate = null }) { PetIcon(s) } }
            Txt(a.f("shop.hint", "n" to g.needLeft(s).coerceAtLeast(0)), FinniText.Subtitle)
            week.shelves.forEach { shelf ->
                ShelfRow(s, shelf, tiers[shelf.id]) { i -> if (tiers[shelf.id] == i) tiers.remove(shelf.id) else tiers[shelf.id] = i }
            }
            if (!g.owns(s, wantId)) {
                val item = g.content.item(wantId)
                if (bigFont()) {
                    ItemCard(wantId, item.name, item.price, null, wantPicked, Modifier.fillMaxWidth(), Direction.WANT, wide = true) { wantPicked = !wantPicked }
                } else Row(horizontalArrangement = Arrangement.spacedBy(FinniDimens.CardGap)) {
                    ItemCard(wantId, item.name, item.price, null, wantPicked, Modifier.weight(1f), labelDirection = Direction.WANT) { wantPicked = !wantPicked }
                    Box(Modifier.weight(2f))
                }
            }
        }

        val q = if (cart.isEmpty()) null else g.quote(s, cart)
        when (ask) {
            Ask.WANT -> q?.let {
                FinniDialog(
                    lines = listOf(
                        a.f("shortfall.want.1", "n" to it.needShortage),
                        a.f("shortfall.want.2", "n" to it.wantAvailableForNeed),
                    ),
                    action = a.t("shortfall.want.take"),
                    onAction = { agreedWant = true; proceed(it, true) },
                    cancel = a.t("shortfall.back"),
                    onCancel = { ask = Ask.NONE },
                )
            }
            Ask.SAVINGS -> q?.let {
                FinniDialog(
                    lines = listOf(
                        a.f("shortfall.savings.1", "n" to it.fromSavings),
                        a.t("shortfall.savings.2"),
                        a.f("shortfall.savings.3", "n" to it.savingsAfter, "goal" to g.goalPrice(s)),
                    ),
                    action = a.t("shortfall.savings.take"),
                    onAction = { buy(it, agreedWant, true) },
                    cancel = a.t("shortfall.back"),
                    onCancel = { ask = Ask.NONE; agreedWant = false },
                )
            }
            Ask.NO_SAVINGS -> q?.let {
                FinniDialog(
                    lines = listOf(a.f("shortfall.savings.1", "n" to it.fromSavings), a.f("shortfall.none.2", "s" to s.progress.savings)),
                    action = null, onAction = {},
                    cancel = a.t("shortfall.back"),
                    onCancel = { ask = Ask.NONE; agreedWant = false },
                )
            }
            Ask.NONE -> {}
        }
    }
}

/** Полка: подпись над полкой видна без нажатия, ступеньки одного размера, одна иконка влияния на всю полку. */
@Composable
private fun ShelfRow(s: GameState, shelf: Shelf, picked: Int?, onPick: (Int) -> Unit) {
    val a = app()
    shelf.caption?.let { Txt(a.t(it)) }
    val big = bigFont()
    val card: @Composable (Int, List<String>, Modifier) -> Unit = { i, tier, m ->
        val key = tier.joinToString("_")
        val price = tier.sumOf { a.game.content.item(it).price }
        val impact = a.game.content.item(tier.first()).impact
        ItemCard(key, a.t("shop.tier.$key"), price, impact?.let(FinniIcons::impact), picked == i, m, wide = big) { onPick(i) }
    }
    // Ступеньки одной полки одного размера — разный размер карточек запрещён (сценарий, шаг 5).
    if (big) {
        Column(verticalArrangement = Arrangement.spacedBy(FinniDimens.CardGap)) {
            shelf.tiers.forEachIndexed { i, tier -> card(i, tier, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(FinniDimens.CardGap)) {
            shelf.tiers.forEachIndexed { i, tier -> card(i, tier, Modifier.weight(1f).fillMaxHeight()) }
            repeat(3 - shelf.tiers.size) { Box(Modifier.weight(1f)) }
        }
    }
}

/** Карточка товара — гайд §10.6: картинка, название, цена с монетой; иконка влияния или метка «Хочу». */
@Composable
private fun ItemCard(
    picture: String,
    name: String,
    price: Int,
    impactIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    selected: Boolean,
    modifier: Modifier,
    labelDirection: Direction? = null,
    wide: Boolean = false,
    onClick: () -> Unit,
) {
    val a = app()
    val priceRow: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Coin(16.dp)
            Txt(price.toString(), FinniText.Button)
            impactIcon?.let { Icon(it, FinniColors.Ink, 20.dp) }
        }
    }
    val label: @Composable () -> Unit = {
        labelDirection?.let {
            val st = directionStyle(it)
            DirectionLabel(st.icon, st.color, a.t(st.labelKey))
        }
    }
    PressCard(onClick, if (wide) modifier else modifier.heightIn(min = 128.dp), selected = selected, fillHeight = !wide) {
        if (wide) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Picture(picture, 48.dp, description = name)
                Column(Modifier.weight(1f)) {
                    Txt(name, FinniText.Caption)
                    priceRow()
                    label()
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Picture(picture, 48.dp, description = name)
                Txt(name, FinniText.Caption.copy(textAlign = TextAlign.Center), Modifier.fillMaxWidth())
                priceRow()
                label()
            }
        }
    }
}

/** Корзина: строка «Каша 5» и метка категории, итог «Всего N». У надбавки метка «Хочу» (I25). */
@Composable
private fun CartBox(s: GameState, cart: List<String>) {
    val a = app()
    val q = a.game.quote(s, cart)
    val shape = RoundedCornerShape(FinniDimens.RadiusCard)
    Column(
        Modifier.fillMaxWidth().background(FinniColors.Surface, shape).border(FinniDimens.Outline, FinniColors.StrokeStrong, shape)
            .padding(horizontal = FinniDimens.CardPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        q.items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("${item.name} ${item.price}", FinniText.Body, Modifier.weight(1f))
                val st = directionStyle(if (item.category == Category.NEED) Direction.NEED else Direction.WANT)
                DirectionLabel(st.icon, st.color, a.t(st.labelKey))
            }
        }
        Txt(a.f("shop.cart.total", "n" to q.total), FinniText.Subtitle)
    }
}

/**
 * Задание F5 — сценарий главы 1, неделя 2, шаг 4. Последствие показано до выбора, обе кнопки
 * одинаковые: ни одно решение не помечено верным. Мячик платится только из копилки.
 */
@Composable
private fun BallChoice(s: GameState, onChoose: (Boolean) -> Unit) {
    val a = app()
    val offer = a.game.ballOffer(s)
    val shape = RoundedCornerShape(FinniDimens.RadiusCard)
    Column(
        Modifier.fillMaxWidth().background(FinniColors.Surface, shape).border(FinniDimens.Outline, FinniColors.StrokeStrong, shape)
            .padding(FinniDimens.CardPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Picture("myachik", 48.dp)
            Txt(a.t("f5.title"), FinniText.Subtitle, Modifier.weight(1f))
        }
        if (offer.available) {
            Txt(a.f("f5.preview", "n" to offer.price))
            Txt(a.f("f5.left", "n" to offer.savingsAfter, "goal" to offer.goalPrice))
            Txt(a.t(if (offer.giftStillPossible) "f5.giftYes" else "f5.giftNo"))
            SecondaryButton(a.t("f5.take"), onClick = { onChoose(true) })
            SecondaryButton(a.t("f5.keep"), onClick = { onChoose(false) })
        } else {
            Txt(a.t("f5.notEnough"))
        }
    }
}
