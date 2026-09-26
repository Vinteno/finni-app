package ru.vinteno.finni.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.core.engine.Direction
import ru.vinteno.finni.core.engine.Game
import ru.vinteno.finni.core.engine.SortCard
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.ui.app
import ru.vinteno.finni.ui.components.Coin
import ru.vinteno.finni.ui.components.DirectionLabel
import ru.vinteno.finni.ui.components.FitColumn
import ru.vinteno.finni.ui.components.Icon
import ru.vinteno.finni.ui.components.Jar
import ru.vinteno.finni.ui.components.MainButton
import ru.vinteno.finni.ui.components.Picture
import ru.vinteno.finni.ui.components.PlateText
import ru.vinteno.finni.ui.components.PressBox
import ru.vinteno.finni.ui.components.SecondaryButton
import ru.vinteno.finni.ui.components.SoftScreen
import ru.vinteno.finni.ui.components.Txt
import ru.vinteno.finni.ui.components.bigFont
import ru.vinteno.finni.ui.components.directionStyle
import ru.vinteno.finni.ui.components.flex
import ru.vinteno.finni.ui.components.picked
import ru.vinteno.finni.ui.components.scrollHint
import ru.vinteno.finni.ui.components.softPlate
import ru.vinteno.finni.ui.components.textHeight
import ru.vinteno.finni.ui.pet.Finni
import ru.vinteno.finni.ui.pet.Reaction
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniText

/** Картинки в карточках ситуации: не мельче 72 dp, крупнее 160 не нужно. */
private val SIT_MIN = 72.dp
private val SIT_MAX = 160.dp
private val CARD_PAD = 10.dp
private val CARD_GAP = 10.dp

/**
 * Экран ситуации недель глав 2 и 3 — final-plan §3, п. 4. Две одинаковые карточки рядом: базовая вещь и
 * та же вещь со слоем надбавки, у каждой — ценник; у второй цена разложена «6 + 4» с метками «Нужное» и
 * «Хочу» (гайд 10.7). Под обеими — подпись сценария: «Обе куртки одинаково тёплые». Карточки и есть
 * кнопки, одинаковые по виду, размеру и отклику; движения на экране нет (инвариант 8). Выбор кладёт
 * вариант в корзину и сразу ведёт в магазин — деньги уходят там.
 */
@Composable
fun SituationScreen(s: GameState, onBack: () -> Unit, onChosen: () -> Unit) {
    val a = app()
    val g = a.game
    val shelf = g.situationShelf(s) ?: return
    val caption = shelf.caption?.let(a::t)
    BackHandler { onBack() }
    SoftScreen(onBack = onBack, backDescription = a.t("common.back"), wallet = s.progress.wallet) { viewport ->
        val scroll = rememberScrollState()
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = FinniDimens.ScreenPadding).scrollHint(scroll).verticalScroll(scroll)) {
            val width = maxWidth
            val names = shelf.tiers.map { a.t("shop.tier." + it.joinToString("_")) }
            val choose: (Int) -> Unit = { i ->
                if (a.act { g.chooseSituation(it, i) }) {
                    // `доволен` одинаков для обоих вариантов — инвариант 8.
                    a.react(Reaction.HAPPY)
                    onChosen()
                }
            }
            if (bigFont()) {
                // Крупный шрифт: карточки столбиком во всю ширину, картинка слева — слова не рвутся.
                Column(verticalArrangement = Arrangement.spacedBy(FinniDimens.CardGap)) {
                    Txt(a.t("sit.title"), FinniText.Title)
                    shelf.tiers.forEachIndexed { i, tier -> SituationRowCard(tier, names[i]) { choose(i) } }
                    caption?.let { CaptionPlate(it) }
                    Box(Modifier.height(8.dp))
                }
                return@BoxWithConstraints
            }
            val cardW = (width - CARD_GAP) / 2
            val nameH = textHeight(names, SitName, cardW - CARD_PAD * 2)
            val labelH = textHeight(listOf(a.t("plan.need")), PlateText, cardW)
            val tagH = textHeight(listOf("6 + 4"), FinniText.Subtitle, cardW) + 8.dp + labelH * 2 + 8.dp
            val frame = CARD_PAD * 2 + 8.dp + nameH + 6.dp + tagH
            FitColumn(viewport) {
                Box(Modifier.height(FinniDimens.TitleTop - FinniDimens.ScreenPadding))
                Txt(a.t("sit.title"), FinniText.Title)
                Box(Modifier.height(FinniDimens.CardGap + 4.dp))
                Row(
                    Modifier.fillMaxWidth().flex(min = frame + SIT_MIN, max = frame + minOf(SIT_MAX, cardW - CARD_PAD * 2), order = 1),
                    horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
                ) {
                    shelf.tiers.forEachIndexed { i, tier ->
                        SituationCard(tier, names[i], nameH, tagH, Modifier.weight(1f).fillMaxHeight()) { choose(i) }
                    }
                }
                Box(Modifier.height(FinniDimens.CardGap))
                caption?.let { CaptionPlate(it) }
                Box(Modifier.flex(min = 8.dp))
            }
        }
    }
}

/** Подпись под обеими карточками: «Обе куртки одинаково тёплые». */
@Composable
private fun CaptionPlate(text: String) {
    Box(Modifier.fillMaxWidth().softPlate(FinniDimens.RadiusCard - 6.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Txt(text, FinniText.Subtitle.copy(textAlign = TextAlign.Center), Modifier.fillMaxWidth())
    }
}

/** Ценник варианта: цена «6 + 4» и под ней метки направлений — база «Нужное», надбавка «Хочу». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SituationPrice(tier: List<String>, modifier: Modifier = Modifier) {
    val a = app()
    val items = tier.map(a.game.content::item)
    Column(
        modifier.background(FinniColors.BgSand, RoundedCornerShape(FinniDimens.RadiusSmall + 4.dp)).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Coin(22.dp)
            Txt(items.joinToString(" + ") { it.price.toString() }, FinniText.Subtitle)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEach { item ->
                val st = directionStyle(a.game.direction(item))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(st.icon, st.color, 16.dp)
                    Txt(a.t(st.labelKey), PlateText)
                }
            }
        }
    }
}

/** Карточка варианта на крупном шрифте: строкой, картинка слева. Та же кнопка, тот же отклик. */
@Composable
private fun SituationRowCard(tier: List<String>, name: String, onClick: () -> Unit) {
    val r = FinniDimens.RadiusCard - 4.dp
    PressBox(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(r), description = name) { m ->
        Row(m.fillMaxWidth().softPlate(r).padding(CARD_PAD), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Picture(tier.joinToString("_"), SIT_MIN)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(name, FinniText.Button)
                SituationPrice(tier, Modifier.fillMaxWidth())
            }
        }
    }
}

private val SitName = FinniText.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, textAlign = TextAlign.Center)

/** Карточка варианта — кнопка: картинка, название, ценник. Разложенная цена — у варианта с надбавкой. */
@Composable
private fun SituationCard(tier: List<String>, name: String, nameH: Dp, tagH: Dp, modifier: Modifier, onClick: () -> Unit) {
    val r = FinniDimens.RadiusCard - 4.dp
    PressBox(onClick, modifier, shape = RoundedCornerShape(r), description = name) { m ->
        Column(m.fillMaxSize().softPlate(r).padding(CARD_PAD), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Picture(tier.joinToString("_"), minOf(maxWidth, maxHeight))
            }
            Box(Modifier.height(8.dp))
            Box(Modifier.height(nameH).fillMaxWidth(), contentAlignment = Alignment.TopCenter) { Txt(name, SitName, Modifier.fillMaxWidth()) }
            Box(Modifier.height(6.dp))
            SituationPrice(tier, Modifier.height(tagH).fillMaxWidth())
        }
    }
}

/**
 * Задание F6 «Что задумали и что вышло» — final-plan §3, п. 8. Траты недели карточками с картинкой и
 * ценой; ребёнок раскладывает их касанием: касание карточки, затем касание банки (перетаскивания нет).
 * На карточке — значок направления, как на банке: так карточка находит свою банку. Не та банка —
 * карточка остаётся на месте, реплика «Посмотри на значок» — без оценки. Готово, когда разложены все;
 * вывод говорит объяснение после, а не подсказка до.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortScreen(s: GameState, onBack: () -> Unit, onDone: () -> Unit) {
    val a = app()
    val g = a.game
    val cards = remember(s.week?.number) { g.sortCards(s) }
    val placed = remember(s.week?.number) { mutableStateListOf<Int>() }
    var picked by remember { mutableStateOf<Int?>(null) }
    var hint by remember { mutableStateOf(false) }
    var after by remember { mutableStateOf<List<String>?>(null) }
    val all = placed.size == cards.size
    BackHandler { onBack() }

    fun finish() {
        val lines = a.explain.afterSort(s)
        if (a.act(g::finishSort)) {
            a.react(Reaction.HAPPY)
            after = lines
        }
    }

    fun drop(jar: Direction?) {
        val i = picked ?: run { hint = false; return }
        if (cards[i].direction == jar) {
            placed += i
            picked = null
            hint = false
        } else hint = true
    }

    SoftScreen(
        onBack = onBack,
        backDescription = a.t("common.back"),
        wallet = s.progress.wallet,
        bottom = {
            Box(contentAlignment = Alignment.BottomCenter) {
                when {
                    after != null -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(
                            Modifier.fillMaxWidth().softPlate(FinniDimens.RadiusCard - 6.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) { after!!.forEachIndexed { i, l -> Txt(l, if (i == 0) FinniText.Button else FinniText.Body) } }
                        MainButton(a.t("common.home"), onClick = onDone)
                    }
                    all -> MainButton(a.t("common.ok"), onClick = ::finish)
                    else -> Box(Modifier.fillMaxWidth().heightIn(min = FinniDimens.MainButtonHeight), contentAlignment = Alignment.Center) {
                        if (hint) Txt(a.t("f6.look"), FinniText.Subtitle)
                    }
                }
            }
        },
    ) { viewport ->
        val scroll = rememberScrollState()
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = FinniDimens.ScreenPadding).scrollHint(scroll).verticalScroll(scroll)) {
            val width = maxWidth
            val colW = (width - 16.dp) / 3
            FitColumn(viewport) {
                Box(Modifier.height(4.dp))
                Txt(a.t("f6.title"), FinniText.Title)
                Box(Modifier.height(8.dp))
                // Три банки — цели касания; под банкой — сколько в неё уже разложено. Крупный шрифт — строками.
                val big = bigFont()
                val jarRow: @Composable (Direction?, Modifier) -> Unit = { d, m0 ->
                    val st = directionStyle(d)
                    val sum = placed.filter { cards[it].direction == d }.sumOf { cards[it].price }
                    val scale = maxOf(cards.sumOf { it.price }, 1)
                    PressBox({ drop(d) }, m0, description = a.t(st.labelKey)) { m ->
                        if (big) Row(
                            m.fillMaxWidth().softPlate(FinniDimens.RadiusCard - 6.dp).padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Jar(sum, scale, st.bg, st.color, 48.dp)
                            DirectionLabel(st.icon, st.color, a.t(st.labelKey), Modifier.weight(1f))
                            Txt(sum.toString(), FinniText.Subtitle)
                        } else Column(m.fillMaxWidth().softPlate(FinniDimens.RadiusCard - 6.dp).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            DirectionLabel(st.icon, st.color, a.t(st.labelKey))
                            Jar(sum, scale, st.bg, st.color, 72.dp)
                            Txt(sum.toString(), FinniText.Subtitle)
                        }
                    }
                }
                val jarsOrder = listOf(Direction.NEED, Direction.WANT, null)
                if (big) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { jarsOrder.forEach { jarRow(it, Modifier.fillMaxWidth()) } }
                else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { jarsOrder.forEach { jarRow(it, Modifier.width(colW)) } }
                Box(Modifier.height(12.dp))
                if (cards.isEmpty()) Txt(a.t("f6.empty"), FinniText.Subtitle)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cards.forEachIndexed { i, card ->
                        if (i in placed) return@forEachIndexed
                        SortCardView(card, picked == i) { picked = if (picked == i) null else i; hint = false }
                    }
                }
                Box(Modifier.flex(min = 8.dp))
            }
        }
    }
}

/** Карточка траты: картинка, цена и значок направления в кольце — тот же, что над банкой. */
@Composable
private fun SortCardView(card: SortCard, selected: Boolean, onClick: () -> Unit) {
    val a = app()
    val st = directionStyle(card.direction)
    val name = if (card.id == Game.DEPOSIT) a.t("f6.deposit") else a.game.content.item(card.id).name
    val r = FinniDimens.RadiusSmall + 6.dp
    PressBox(onClick, Modifier.semantics { this.selected = selected }, shape = RoundedCornerShape(r), description = name) { m ->
        Column(
            m.softPlate(r).picked(selected, r).padding(8.dp).width(76.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Picture(card.id, 56.dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Coin(18.dp)
                Txt(card.price.toString(), FinniText.Button)
                Icon(st.icon, st.color, 18.dp)
            }
        }
    }
}

/**
 * Конец игры — сценарий главы 3, §8: обжитая комната, одна строка «Мы обжились» (без рода, I48) и два
 * равных действия: «Играть дальше» и «Начать сначала». Ни очков, ни оценок, ни процентов.
 */
@Composable
fun EndScreen(s: GameState) {
    val a = app()
    val g = a.game
    val storage = listOf("polka_veshchey", "korzina", "sunduk").firstOrNull { it in s.progress.inventory }
    SoftScreen(
        onBack = null,
        backDescription = a.t("common.back"),
        wallet = null,
        room = true,
        bottom = {
            SecondaryButton(a.t("end.more"), onClick = { a.act(g::keepPlaying) })
            SecondaryButton(a.t("end.again"), onClick = { a.act(g::restart) })
        },
    ) { viewport ->
        Column(Modifier.fillMaxSize().padding(horizontal = FinniDimens.ScreenPadding)) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).softPlate(FinniDimens.RadiusCard - 4.dp).padding(horizontal = 18.dp, vertical = 14.dp)) {
                Txt(a.t("end.title"), FinniText.Title)
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp, bottom = 2.dp)) {
                val fw = minOf(maxHeight * (100f / 212f), maxWidth * 0.4f).coerceAtLeast(48.dp)
                val side = maxWidth * 0.4f
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceEvenly) {
                    Finni(
                        s.profile.fur, s.profile.accessory, Modifier.width(fw),
                        reaction = Reaction.HAPPY, reactionKey = 1, animate = a.animationOn,
                        description = s.profile.petName, wear = g.worn(s),
                    )
                    storage?.let { Picture("${it}_full", minOf(fw * 1.1f, side), description = g.content.goal(it).name) }
                }
            }
        }
    }
}
