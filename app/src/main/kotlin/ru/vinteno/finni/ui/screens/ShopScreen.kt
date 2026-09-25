package ru.vinteno.finni.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.core.content.Category
import ru.vinteno.finni.core.content.Impact
import ru.vinteno.finni.core.engine.Checkout
import ru.vinteno.finni.core.engine.Direction
import ru.vinteno.finni.core.engine.requireWeek
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.ui.app
import ru.vinteno.finni.ui.components.Basket
import ru.vinteno.finni.ui.components.DirectionLabel
import ru.vinteno.finni.ui.components.FinniDialog
import ru.vinteno.finni.ui.components.FitColumn
import ru.vinteno.finni.ui.components.MainButton
import ru.vinteno.finni.ui.components.NeedRing
import ru.vinteno.finni.ui.components.PLANK
import ru.vinteno.finni.ui.components.Picture
import ru.vinteno.finni.ui.components.PlateText
import ru.vinteno.finni.ui.components.PriceTag
import ru.vinteno.finni.ui.components.ShelfPlank
import ru.vinteno.finni.ui.components.SoftScreen
import ru.vinteno.finni.ui.components.Thing
import ru.vinteno.finni.ui.components.Txt
import ru.vinteno.finni.ui.components.bigFont
import ru.vinteno.finni.ui.components.directionStyle
import ru.vinteno.finni.ui.components.flex
import ru.vinteno.finni.ui.components.scrollHint
import ru.vinteno.finni.ui.components.softPlate
import ru.vinteno.finni.ui.components.textHeight
import ru.vinteno.finni.ui.components.thingRatio
import ru.vinteno.finni.ui.pet.Reaction
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniText

/** Какое окно открыто поверх корзины. Модальных окон в игре ровно два вида — нехватка и копилка. */
private enum class Ask { NONE, WANT, SAVINGS, NO_SAVINGS }

/**
 * Магазин — единственное место, где уходят деньги за покупки (сценарий главы 1, шаг 4). Две полки:
 * еда, мыло и через разделитель хотелка главы. Мячика здесь нет: задание F5 живёт на копилке (QA-M3).
 * Корзина видна всегда и есть подтверждение покупки: картинки с ценой под меткой категории, сумма,
 * «Купить». Кнопка не гаснет при нехватке — открывается окно. Размеры — от свободной высоты: сначала
 * уменьшаются вещи на полках, ценники и кнопка — нет (I40).
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
    val boughtThisVisit = remember { mutableStateListOf<String>() }

    val wantId = g.content.chapter1.chapterWantId
    val cart = week.shelves.flatMap { sh -> tiers[sh.id]?.let { sh.tiers[it] } ?: emptyList() } +
        (if (wantPicked && !g.owns(s, wantId)) listOf(wantId) else emptyList())

    fun leave() {
        val firstVisit = !a.state.value.requireWeek().shopVisited
        a.act(g::leaveShop)
        // Плашка — о том, что сделано за этот заход. Повторный заход без покупок её не показывает:
        // «Ты ничего не купил» после утренней покупки было бы неправдой.
        if (firstVisit || boughtThisVisit.isNotEmpty()) {
            a.pendingPlate = a.explain.afterShop(a.state.value, boughtThisVisit.toList())
        }
        onLeave()
    }
    // Системное «назад» (жест, кнопка телефона) закрывает шаг так же, как кнопка на экране.
    BackHandler { leave() }

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

    // Полки сверху вниз: полка, с которой на этой неделе уже куплено, не показывается (QA-M1), остальные
    // поднимаются. Хотелка встаёт третьей на последнюю полку, если там есть место, — через разделитель;
    // места нет — на свою полку, на то же третье место.
    val wantAvailable = !g.owns(s, wantId)
    val rows = buildList {
        week.shelves.filter { it.id !in g.boughtShelves(s) }.forEach { shelf ->
            add(ShelfView(shelf.id, shelf.caption?.let(a::t), shelf.tiers.mapIndexed { i, tier -> tierSlot(tier, i) }))
        }
        if (wantAvailable) {
            val last = lastOrNull()
            if (last != null && last.slots.size < 3) set(lastIndex, last.copy(want = true))
            else add(ShelfView("want", null, emptyList(), want = true))
        }
    }
    // Корзина хранит место под самый полный набор этой недели: со второй строкой «Хочу», если хотелки
    // на неделе бывают. Так полки не прыгают, когда в корзину кладут первую вещь «Хочу».
    val fullest = week.shelves.flatMap { sh -> sh.tiers.maxBy { it.size } } + (if (wantAvailable) listOf(wantId) else emptyList())

    Box(Modifier.fillMaxSize()) {
        SoftScreen(
            onBack = ::leave,
            backDescription = a.t("common.back"),
            wallet = s.progress.wallet,
            bottomGap = 8.dp,
            bottom = {
                // Корзина видна всегда: пустая — пустая корзина без слов, место под «Купить» занято.
                Box {
                    CartBasket(s, fullest, Modifier.alpha(0f).clearAndSetSemantics {})
                    CartBasket(s, cart, Modifier.matchParentSize())
                }
                MainButton(
                    a.t("shop.buy"),
                    onClick = { if (cart.isNotEmpty()) proceed(g.quote(s, cart), false) },
                    modifier = if (cart.isEmpty()) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier,
                )
            },
        ) { viewport ->
            val scroll = rememberScrollState()
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = FinniDimens.ScreenPadding).scrollHint(scroll).verticalScroll(scroll)) {
                val width = maxWidth
                val slotW = (width - SLOT_GAP * 2) / 3
                val unitMax = minOf(slotW - 8.dp, UNIT_MAX)
                val tagH = tagHeight(slotW)
                FitColumn(viewport) {
                    Box(Modifier.height(4.dp))
                    // Заголовок — одной строкой, как в макете: двухстрочный на 360 × 600 выталкивал вторую полку.
                    Txt(a.t("shop.title"), FinniText.Subtitle)
                    Box(Modifier.height(4.dp))
                    Txt(a.f("shop.hint", "n" to g.needLeft(s).coerceAtLeast(0)), HintText)
                    rows.forEach { row ->
                        val signH = row.caption?.let { signHeight(it, width) } ?: 0.dp
                        val wantItem = if (row.want) g.content.item(wantId) else null
                        val factor = rowFactor(row, wantItem)
                        val fixed = SHELF_TOP + signH + PLANK + TAG_GAP + tagH
                        ShelfRow(
                            row, slotW, signH, tagH,
                            picked = tiers[row.id],
                            wantItem = wantItem, wantPicked = wantPicked,
                            onPick = { i -> if (tiers[row.id] == i) tiers.remove(row.id) else tiers[row.id] = i },
                            onWant = { wantPicked = !wantPicked },
                            modifier = Modifier.fillMaxWidth().flex(min = fixed + UNIT_MIN * factor, max = fixed + unitMax * factor, order = 1),
                        )
                    }
                    Box(Modifier.flex(min = 0.dp))
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

/** Полка на экране: подпись, ступеньки по местам слева направо и, если [want], хотелка на третьем месте. */
private data class ShelfView(val id: String, val caption: String?, val slots: List<TierSlot>, val want: Boolean = false)

/** Ступенька полки: картинка — ключ ступеньки, цена — сумма предметов, потребность — у основного предмета. */
private data class TierSlot(val index: Int, val key: String, val items: List<String>)

private fun tierSlot(tier: List<String>, i: Int) = TierSlot(i, tier.joinToString("_"), tier)

/** Ширина основы вещи на полке в долях единицы: миска — единица, мыло меньше, качели почти как миска. */
private fun itemWidth(key: String): Float = when {
    key == "kacheli" -> 0.95f
    key.startsWith("mylo") -> 0.7f
    else -> 1f
}

/** Высота вещи в долях единицы — по непрозрачной рамке основы. */
private fun itemHeight(key: String): Float = itemWidth(key) * thingRatio(base(key))

/** Основа картинки: миска под любой едой, мыло под пеной. */
private fun base(key: String) = when {
    key == "krupa" || key.startsWith("kasha") -> "miska"
    key.startsWith("mylo") -> "mylo"
    else -> key
}

/** Слой поверх основы — в её рамке: еда в миске, пена на мыле. */
private fun layer(key: String): String? = when (key) {
    "krupa" -> "food_krupa"
    "kasha" -> "food_kasha"
    "kasha_yagody" -> "food_kasha_yagody"
    "mylo_pena" -> "pena"
    else -> null
}

/** Единица вещей на полке: ширина миски. Миска не уже 56 dp; шире 96 — не нужно. */
private val UNIT_MIN = 56.dp
private val UNIT_MAX = 96.dp
private val SLOT_GAP = 8.dp
private val SHELF_TOP = 4.dp
private val HintText = FinniText.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
private val TAG_GAP = 4.dp
private val SIGN_RING = 24.dp

/** Высота ценника: цена и кольцо в строку; «Хочу», не поместившийся рядом, — строкой ниже. */
@Composable
private fun tagHeight(slotW: Dp): Dp {
    val a = app()
    val price = textHeight(listOf("99"), FinniText.Button, slotW)
    val one = maxOf(34.dp, maxOf(price, 26.dp) + 8.dp)
    val tm = androidx.compose.ui.text.rememberTextMeasurer()
    val d = androidx.compose.ui.platform.LocalDensity.current
    val w = with(d) {
        tm.measure("15", FinniText.Button, density = d).size.width.toDp() + tm.measure(a.t("shop.label.want"), PlateText, density = d).size.width.toDp()
    }
    // Монета, цена, звезда и слово с промежутками и полями ценника.
    val fits = 18.dp + 4.dp + 6.dp + 18.dp + 2.dp + 16.dp + w <= slotW
    return if (fits) one else one + textHeight(listOf(a.t("shop.label.want")), PlateText, slotW) + 2.dp
}

@Composable
private fun signHeight(caption: String, width: Dp): Dp =
    maxOf(SIGN_RING, textHeight(listOf(caption), PlateText, width - SIGN_RING - 24.dp)) + 8.dp + 2.dp

/**
 * Полка: над ней табличка, на доске вещи одного ряда, под доской ценники. Вещь стоит низом на верхней
 * кромке доски, как вещи дома. Вещь и её ценник — одна зона нажатия; места одинаковые, одна или две
 * вещи стоят там же, где стояли бы три. Все ступеньки выделяются одинаково: ценник с рамкой и галочкой.
 */
@Composable
private fun ShelfRow(
    row: ShelfView,
    slotW: Dp,
    signH: Dp,
    tagH: Dp,
    picked: Int?,
    wantItem: ru.vinteno.finni.core.content.Item?,
    wantPicked: Boolean,
    onPick: (Int) -> Unit,
    onWant: () -> Unit,
    modifier: Modifier,
) {
    val a = app()
    val factor = rowFactor(row, wantItem)
    Box(modifier.padding(top = SHELF_TOP)) {
        // Доска — во всю ширину, под вещами, над ценниками; чуть шире полей экрана.
        ShelfPlank(Modifier.align(Alignment.BottomCenter).padding(bottom = TAG_GAP + tagH).fillMaxWidth().offsetWide())
        row.caption?.let { caption ->
            // Табличка — по центру над теми местами, где стоят ступеньки этой полки, не над хотелкой;
            // шире этих мест — сдвигается внутрь экрана, но не обрезается.
            val span = row.slots.size.coerceAtLeast(1)
            val center = (slotW * span + SLOT_GAP * (span - 1)) / 2
            Box(Modifier.fillMaxWidth().height(signH)) {
                Sign(caption, row.slots.firstOrNull()?.items?.firstOrNull()?.let { a.game.content.item(it).impact },
                    Modifier.centerAt(center))
            }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(SLOT_GAP)) {
                repeat(3) { i ->
                    val slot = row.slots.getOrNull(i)
                    val want = i == 2 && wantItem != null && row.slots.size < 3
                    // Над ступеньками — табличка; над хотелкой её нет, и качели могут стоять выше.
                    val band = if (want) 0.dp else signH
                    Box(Modifier.width(slotW).fillMaxHeight().padding(top = band)) {
                        // Тонкий разделитель между ступеньками полки и хотелкой.
                        if (want && row.slots.isNotEmpty()) {
                            Box(Modifier.align(Alignment.CenterStart).offset(x = -(SLOT_GAP / 2 + 1.dp)).padding(bottom = TAG_GAP + tagH + PLANK + 8.dp, top = 8.dp)
                                .width(2.dp).fillMaxHeight().background(FinniColors.Stroke))
                        }
                        when {
                            slot != null -> {
                                val price = slot.items.sumOf { a.game.content.item(it).price }
                                val impact = a.game.content.item(slot.items.first()).impact
                                ShelfSlot(slot.key, a.t("shop.tier.${slot.key}"), price, impact, null, picked == slot.index, tagH, factor, 0.dp) { onPick(slot.index) }
                            }
                            want -> ShelfSlot(wantItem!!.id, wantItem.name, wantItem.price, null, a.t("shop.label.want"), wantPicked, tagH, factor, signH, onWant)
                        }
                    }
                }
        }
    }
}

/**
 * Высота ряда вещей в единицах: по самой высокой вещи. Хотелка на полке с табличкой в счёт не идёт —
 * над ней свободная полоса таблички, туда она и дорастает.
 */
private fun rowFactor(row: ShelfView, wantItem: ru.vinteno.finni.core.content.Item?): Float =
    (row.slots.map { itemHeight(it.key) } + listOfNotNull(wantItem?.takeIf { row.caption == null || row.slots.isEmpty() }?.let { itemHeight(it.id) })).max()

/** Доска полки выходит за поля экрана на 8 dp с каждой стороны — как в макете, полка шире вещей. */
private fun Modifier.offsetWide(): Modifier = layout { m, c ->
    val extra = 8.dp.roundToPx()
    val p = m.measure(c.copy(minWidth = c.maxWidth + extra * 2, maxWidth = c.maxWidth + extra * 2))
    layout(c.maxWidth, p.height) { p.place(-extra, 0) }
}

/** Место на полке: вещь на доске и ценник под доской — одна зона нажатия во всю высоту. */
@Composable
private fun ShelfSlot(key: String, name: String, price: Int, impact: Impact?, want: String?, selected: Boolean, tagH: Dp, rowFactor: Float, band: Dp, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().semantics(mergeDescendants = true) { contentDescription = name; this.selected = selected }
            .clickable(remember { MutableInteractionSource() }, null, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
            // Вещи одного ряда — одной единицы: высота ряда делится на самую высокую вещь.
            // [band] — свободная полоса над хотелкой: единица от ряда, рост — до верха полосы.
            val unit = (maxHeight - band) / rowFactor
            val w = minOf(unit * itemWidth(key), maxHeight / thingRatio(base(key)), maxWidth)
            Box(Modifier.offset(y = SEAT)) {
                Thing(base(key), w)
                layer(key)?.let { Thing(it, w, box = base(key)) }
            }
        }
        Box(Modifier.height(PLANK + TAG_GAP))
        Box(Modifier.height(tagH), contentAlignment = Alignment.TopCenter) {
            PriceTag(price, impact, want, selected)
        }
    }
}

/** Поставить серединой на [center] от левого края, не выходя за края родителя. */
private fun Modifier.centerAt(center: Dp): Modifier = layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0))
    val x = (center.roundToPx() - p.width / 2).coerceIn(0, maxOf(0, c.maxWidth - p.width))
    layout(c.maxWidth, p.height) { p.place(x, 0) }
}

/** Вещь чуть утоплена в доску, чтобы стояла, а не висела над кромкой. */
private val SEAT = 2.dp

/** Табличка над полкой: значок потребности и подпись — к чему она относится, видно. */
@Composable
private fun Sign(caption: String, impact: Impact?, modifier: Modifier) {
    Row(
        modifier.softPlate(FinniDimens.RadiusSmall + 2.dp).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        impact?.let { NeedRing(it, SIGN_RING) }
        Txt(caption, PlateText)
    }
}

/**
 * Корзина — подтверждение покупки (F2): у каждой позиции цена и категория, справа сумма, внизу
 * «Купить». Позиции — картинкой с ценой под меткой категории, без названия — гайд §12.2 «сначала
 * показать, потом написать»; название — в подписи картинки для экранного диктора. Каждая метка —
 * один раз (QA-M11), у надбавки метка «Хочу» (I25). Пустая корзина — без слов.
 */
@Composable
private fun CartBasket(s: GameState, cart: List<String>, modifier: Modifier) {
    val a = app()
    val q = if (cart.isEmpty()) null else a.game.quote(s, cart)
    Basket(modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        if (q == null) return@Basket
        val groups: @Composable (Modifier) -> Unit = { m ->
            // Строка на категорию: метка, за ней картинки с ценой.
            Column(
                m.softPlate(FinniDimens.RadiusSmall + 4.dp).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                listOf(Category.NEED, Category.WANT).forEach { cat ->
                    val items = q.items.filter { it.category == cat }
                    if (items.isEmpty()) return@forEach
                    val st = directionStyle(if (cat == Category.NEED) Direction.NEED else Direction.WANT)
                    FlowRowGroup {
                        DirectionLabel(st.icon, st.color, a.t(st.labelKey))
                        items.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Picture(item.id, THUMB, description = item.name)
                                Txt(item.price.toString(), FinniText.Button)
                            }
                        }
                    }
                }
            }
        }
        val total: @Composable () -> Unit = {
            Box(Modifier.softPlate(FinniDimens.RadiusSmall + 4.dp).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Txt(a.f("shop.cart.total", "n" to q.total), FinniText.Button)
            }
        }
        // Крупный шрифт: сумма — под картинками, иначе она забирает ширину и картинки встают столбиком.
        if (bigFont()) Column(Modifier.fillMaxWidth().padding(4.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            groups(Modifier.fillMaxWidth())
            total()
        } else Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            groups(Modifier.weight(1f))
            total()
        }
    }
}

private val THUMB = 26.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowGroup(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
