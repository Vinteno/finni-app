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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.core.engine.BallOffer
import ru.vinteno.finni.core.engine.Direction
import ru.vinteno.finni.core.model.EventOutcome
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.Phase
import ru.vinteno.finni.core.model.Plan
import ru.vinteno.finni.core.model.SummaryChoice
import ru.vinteno.finni.ui.app
import ru.vinteno.finni.ui.components.ChoiceButton
import ru.vinteno.finni.ui.components.Coin
import ru.vinteno.finni.ui.components.CoinRoll
import ru.vinteno.finni.ui.components.EqualColumn
import ru.vinteno.finni.ui.components.FitColumn
import ru.vinteno.finni.ui.components.Icon
import ru.vinteno.finni.ui.components.KiraFigure
import ru.vinteno.finni.ui.components.MainButton
import ru.vinteno.finni.ui.components.Picture
import ru.vinteno.finni.ui.components.ProgressCells
import ru.vinteno.finni.ui.components.ROOM_DEPTH
import ru.vinteno.finni.ui.components.ROOM_LIFT
import ru.vinteno.finni.ui.components.SavingsPlate
import ru.vinteno.finni.ui.components.SecondaryButton
import ru.vinteno.finni.ui.components.SoftExplain
import ru.vinteno.finni.ui.components.SoftScreen
import ru.vinteno.finni.ui.components.StandPicture
import ru.vinteno.finni.ui.components.TAIL
import ru.vinteno.finni.ui.components.TakeCells
import ru.vinteno.finni.ui.components.Thing
import ru.vinteno.finni.ui.components.Txt
import ru.vinteno.finni.ui.components.directionStyle
import ru.vinteno.finni.ui.components.drawRoom
import ru.vinteno.finni.ui.components.flex
import ru.vinteno.finni.ui.components.scrollHint
import ru.vinteno.finni.ui.components.softPlate
import ru.vinteno.finni.ui.components.textHeight
import ru.vinteno.finni.ui.components.textWidth
import ru.vinteno.finni.ui.components.thingRatio
import ru.vinteno.finni.ui.motion.Appear
import ru.vinteno.finni.ui.motion.CoinTarget
import ru.vinteno.finni.ui.motion.SlideUp
import ru.vinteno.finni.ui.motion.anchor
import ru.vinteno.finni.ui.pet.Finni
import ru.vinteno.finni.ui.pet.Reaction
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniText

/** Одна клетка копилки — пять монет (сценарий §5а, гайд §10.9). */
private const val COINS_PER_CELL = 5

/** Копилка на полу: не мельче 96 dp; крупнее 200 — не нужно. */
private val PIGGY_MIN = 96.dp
private val PIGGY_MAX = 200.dp

/** Картинки в карточках «Мячик или подарок»: не мельче 72 dp. */
private val CHOICE_MIN = 72.dp

/** Клетки копилки в плашке: одна строка, от ширины плашки, не меньше 24 dp. */
private val CELL_MIN = 24.dp
private val CELL_MAX = 36.dp
private val CELL_GAP = 6.dp

/**
 * Копилка — сценарий главы 1, шаг 7. Комната, копилка стоит на полу, над ней плашка с хвостиком:
 * цель, клетки по 5 монет и «Накопил N из M.». Строки с ценой подарка нет (I40): цена уже в «из M».
 * Сумма здесь не выбирается: кнопка откладывает ровно то, что стоит в плане, и при набранной цели тоже —
 * излишек остаётся в копилке (QA-M2). При нуле в плане кнопки нет, её место пустое: пол не прыгает.
 * Цель не покупается кнопкой: «Подарок готов» ничего не списывает (I4).
 * На неделе 2 после взноса здесь же открывается задание F5 (QA-M3).
 * До плана и после итога копилку только смотрят: кнопки «Отложить» нет, место её занято — до плана
 * строкой «Сначала разложи монеты.», после итога пустое. После взноса и после F5 на её месте — «Домой» (I45).
 */
@Composable
fun PiggyScreen(s: GameState, onBack: () -> Unit) {
    val a = app()
    val g = a.game
    val goal = g.content.goal(s.chapter.goalId ?: return)
    val saved = s.progress.savings
    val reached = g.goalReached(s)
    var ballPlate by remember { mutableStateOf<List<String>?>(null) }
    // Задание F5 — на копилке сразу после взноса (QA-M3). Открывается, когда монеты взноса долетели:
    // ребёнок видит, как заполнились клетки, и выбирает уже с этими монетами в копилке.
    val taskOpen = g.choiceOpen(s) && a.flights.savingsPending == 0
    // Пока выбор не сделан, на экране только выбор; объяснение после выбора — там же, с «Понятно».
    val taskMode = taskOpen || ballPlate != null
    val offer = if (taskOpen) g.ballOffer(s) else null
    val noBall = offer != null && !offer.available
    fun leave() { a.act(g::leavePiggy); onBack() }
    // Системное «назад» закрывает шаг так же, как кнопка на экране; задание F5 остаётся ждать.
    BackHandler { leave() }
    fun choose(took: Boolean) {
        if (a.act { g.chooseBall(it, took) }) {
            // Одна и та же реакция при обоих решениях — самое опасное место главы для инварианта 8.
            a.react(Reaction.HAPPY)
            ballPlate = a.explain.afterBall(a.state.value, took)
        }
    }
    SoftScreen(
        onBack = ::leave,
        backDescription = a.t("common.back"),
        wallet = s.progress.wallet,
        room = !taskMode,
        bottom = {
            if (taskMode) {
                // Место — под две кнопки выбора всегда: после выбора карточки и плашка не прыгают.
                Box(contentAlignment = Alignment.BottomCenter) {
                    Column(Modifier.alpha(0f).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(FinniDimens.CardGap)) {
                        SecondaryButton(a.t("f5.take"), onClick = {})
                        SecondaryButton(a.t("f5.keep"), onClick = {})
                    }
                    when {
                        ballPlate != null -> MainButton(a.t("common.home"), onClick = ::leave)
                        // В копилке меньше цены мячика: «Понятно» засчитывает задание с наградой (I19).
                        noBall -> MainButton(a.t("common.ok"), onClick = { a.act(g::acknowledgeNoBall) })
                        else -> Column(verticalArrangement = Arrangement.spacedBy(FinniDimens.CardGap)) {
                            SecondaryButton(a.t("f5.take"), onClick = { choose(true) })
                            SecondaryButton(a.t("f5.keep"), onClick = { choose(false) })
                        }
                    }
                }
            } else {
                val w = s.week!!
                val save = w.plan.save
                val can = g.canDeposit(s)
                Box(contentAlignment = Alignment.Center) {
                    // Место кнопки занято всегда: пол не прыгает.
                    MainButton(a.f("piggy.deposit", "n" to save), onClick = {}, modifier = Modifier.alpha(0f).clearAndSetSemantics {})
                    when {
                        can -> MainButton(
                            a.f("piggy.deposit", "n" to save),
                            onClick = {
                                // `доволен` одинаков для любой суммы взноса — разная реакция была бы оценкой.
                                if (a.act(g::deposit)) {
                                    a.react(Reaction.HAPPY)
                                    // Монеты летят из кошелька в копилку; клетки заполняются по мере прилёта.
                                    a.flights.launch("wallet", "piggy", save, CoinTarget.SAVINGS, a.animationOn)
                                }
                            },
                        )
                        // До плана — только посмотреть: почему откладывать нельзя.
                        !w.planConfirmed -> Txt(a.t("home.say.planFirst"), FinniText.Subtitle)
                        // После итога — только посмотреть, место пустое.
                        s.phase != Phase.WEEK -> {}
                        else -> MainButton(a.t("common.home"), onClick = ::leave)
                    }
                }
            }
        },
    ) { viewport ->
        val scroll = rememberScrollState()
        val content: @Composable (Dp) -> Unit = { width ->
            if (taskMode) BallChoice(s, goal, offer, ballPlate, width, viewport)
            else PiggyRoom(s, goal, saved, reached, width, viewport)
        }
        BoxWithConstraints(
            Modifier.fillMaxSize().scrollHint(scroll).verticalScroll(scroll)
                // Комната выше окна (крупный шрифт) прокручивается вместе с копилкой: пол — у её низа.
                .then(if (!taskMode) Modifier.drawBehind { if (size.height > viewport.toPx() + 1f) drawRoom(size.height - ROOM_LIFT.toPx()) } else Modifier)
                .padding(horizontal = FinniDimens.ScreenPadding),
        ) { content(maxWidth) }
    }
}

/**
 * Заголовок с головой Финни: голова реагирует на взнос и выбор, холостого движения нет (U06). Не
 * помещается заголовок в строку рядом с головой — голова меньше, но не мельче 32 dp.
 */
@Composable
private fun HeadTitle(s: GameState, title: String, width: Dp) {
    val a = app()
    val head = (width - textWidth(title, FinniText.Title) - 12.dp - 2.dp).coerceIn(32.dp, FinniDimens.PetHead)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Finni(
            s.profile.fur, s.profile.accessory, Modifier.width(head),
            reaction = a.reaction, reactionKey = a.reactionKey, animate = a.animationOn, idle = false, headOnly = true,
        )
        Txt(title, FinniText.Title, Modifier.weight(1f))
    }
}

/** Комната копилки: плашка накоплений над копилкой, копилка стоит на полу. Уступает место копилка. */
@Composable
private fun PiggyRoom(s: GameState, goal: ru.vinteno.finni.core.content.Goal, saved: Int, reached: Boolean, width: Dp, viewport: Dp) {
    val a = app()
    val cells = (goal.price + COINS_PER_CELL - 1) / COINS_PER_CELL
    val arrived = (saved - a.flights.savingsPending).coerceAtLeast(0)
    val piggyW = minOf(PIGGY_MAX, width * 0.56f)
    val ratio = thingRatio("kopilka")
    FitColumn(viewport) {
        Box(Modifier.height(8.dp))
        HeadTitle(s, a.t("piggy.title"), width)
        Box(Modifier.flex(min = 12.dp))
        SavingsPlate(tailFromEnd = width / 2, maxWidth = width, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Picture(goal.id, 56.dp, description = goal.name)
                    Txt(goal.name, FinniText.Subtitle, Modifier.weight(1f))
                }
                // Клетки — во всю ширину плашки: главный предмет экрана, а не мелкая строка (гайд §10.9).
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cell = ((maxWidth - 4.dp * (cells - 1)) / cells).coerceIn(CELL_MIN, CELL_MAX)
                    ProgressCells(minOf(arrived, goal.price) / COINS_PER_CELL, cells, Modifier.anchor(a.flights, "piggy"), cell = cell)
                }
                Txt(a.f(if (reached) "piggy.ready" else "piggy.saved", "n" to saved, "goal" to goal.price), FinniText.Subtitle)
            }
        }
        Box(Modifier.height(TAIL + 6.dp))
        // Копилка стоит на полу по центру: низ — на глубине [ROOM_DEPTH] ниже стыка стены и пола.
        BoxWithConstraints(Modifier.fillMaxWidth().flex(min = PIGGY_MIN, max = piggyW * ratio, order = 1), contentAlignment = Alignment.BottomCenter) {
            Thing("kopilka", minOf(piggyW, maxHeight / ratio), description = a.t("piggy.title"))
        }
        Box(Modifier.height(ROOM_LIFT - ROOM_DEPTH))
    }
}

/**
 * Итог недели — заявленное ядро, сценарий главы 1, шаг 8, гайд §10.10. Всё на бумажном листе: три ряда
 * монет одного масштаба, все золотые, подписи над рядами, объяснение с головой Финни. Два действия,
 * оба законны, одного вида и размера, порядок не меняется, и оба всегда на виду внизу (I40): прокрутка,
 * прятавшая второй выбор, была скрытой рекомендацией. Анимаций нет вообще, Финни неподвижен.
 */
@Composable
fun SummaryScreen(s: GameState, onBack: () -> Unit, onDone: () -> Unit) {
    val a = app()
    val g = a.game
    val sum = g.summary(s)
    val actual = sum.fact.total
    val scale = maxOf(sum.planned, actual, sum.reward, 1)

    fun choose(c: SummaryChoice) {
        if (a.act { g.finishWeek(it, c) }) onDone()
    }

    SoftScreen(
        onBack = onBack,
        backDescription = a.t("common.back"),
        wallet = s.progress.wallet,
        bottom = {
            // В каждой кнопке — название и план, с которым откроется следующая неделя: три числа с иконками
            // направлений, без слов. Кнопки одного вида и одной высоты — ни одна не выделена (§10.10).
            val keep = Plan.DEFAULT
            EqualColumn(FinniDimens.CardGap) {
                ChoiceButton({ choose(SummaryChoice.KEEP_PLAN) }) { ChoiceFace(a.t("summary.keepPlan"), keep) }
                ChoiceButton({ choose(SummaryChoice.TAKE_ACTUAL) }) { ChoiceFace(a.t("summary.takeActual"), sum.fact) }
            }
        },
    ) { viewport ->
        val scroll = rememberScrollState()
        Box(Modifier.fillMaxSize().padding(horizontal = FinniDimens.ScreenPadding - 4.dp).scrollHint(scroll).verticalScroll(scroll)) {
            // Лист — светлая плашка на песке во всю середину экрана; выше окна — прокручивается лист.
            Column(
                Modifier.fillMaxWidth().heightIn(min = viewport).padding(vertical = 8.dp)
                    .softPlate(FinniDimens.RadiusCard - 6.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Txt(a.t("summary.title"), FinniText.Subtitle)
                FactRow(a.t("summary.planned"), sum.planned, scale)
                FactRow(a.t("summary.actual"), actual, scale)
                FactRow(a.t("summary.reward"), sum.reward, scale)
                Box(Modifier.height(2.dp))
                // Объяснение — откуда разница, словами (§10.11).
                SoftExplain(a.explain.summaryLines(s)) { PetIcon(s) }
            }
        }
    }
}

/** Название слева, три числа справа; не помещаются в строку — числа строкой ниже. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceFace(name: String, p: Plan) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(name, ChoiceText.copy(color = FinniColors.Action))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(Direction.NEED to p.need, Direction.WANT to p.want, null to p.save).forEach { (d, n) ->
                val st = directionStyle(d)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(st.icon, st.color, 18.dp)
                    Txt(n.toString(), ChoiceText)
                }
            }
        }
    }
}

private val ChoiceText = FinniText.Body.copy(fontWeight = FontWeight.Bold)

@Composable
private fun FactRow(label: String, value: Int, scale: Int) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(label, ChoiceText, Modifier.weight(1f))
            Txt(value.toString(), FinniText.Subtitle)
        }
        CoinRoll(value, scale)
    }
}

/**
 * Событие главы — день рождения Киры, сценарий §8, шаг 9. Играется всегда (I6). Комната; текст —
 * плашкой вверху, над героями; Финни и Кира стоят на полу в одном масштабе, подарок или открытка —
 * на полу между ними. Оба исхода наравне: одна реакция Финни, подарок и открытка одного размера, на
 * одном месте, появляются одинаково (инвариант 3). Кошелька нет (I40). Кнопки «назад» нет: событие
 * не отматывается.
 */
@Composable
fun EventScreen(s: GameState, onDone: () -> Unit) {
    val a = app()
    val g = a.game
    val given = s.progress.savings >= g.goalPrice(s) || s.eventOutcome == EventOutcome.GIFT_GIVEN
    val name = s.profile.petName
    val lines = listOf(a.t("event.1")) + if (given) {
        listOf(a.f("event.a.2", "what" to g.content.goal(s.chapter.goalId!!).accusative), a.t("event.a.3"))
    } else {
        listOf(a.t("event.b.2"), a.f("event.b.3", "name" to name), a.t("event.b.4"))
    }
    SoftScreen(
        onBack = null,
        backDescription = a.t("common.back"),
        wallet = null,
        room = true,
        bottom = { MainButton(a.t("event.next"), onClick = { a.act(g::playEvent); onDone() }) },
    ) { viewport ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = FinniDimens.ScreenPadding)) {
            val width = maxWidth
            // Герои не мельче 96 dp: если текст с ними не помещается (крупный шрифт), прокручивается плашка.
            val plateMax = viewport - EVENT_GAP - HERO_MIN - (ROOM_LIFT - ROOM_DEPTH)
            Column(Modifier.fillMaxSize()) {
                val scroll = rememberScrollState()
                Column(
                    Modifier.fillMaxWidth().heightIn(max = plateMax).softPlate(FinniDimens.RadiusCard - 4.dp)
                        .scrollHint(scroll).verticalScroll(scroll).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) { lines.forEach { Txt(it, FinniText.Subtitle) } }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(top = EVENT_GAP, bottom = ROOM_LIFT - ROOM_DEPTH)) {
                    Heroes(s, given, width, maxHeight)
                }
            }
        }
    }
}

private val EVENT_GAP = 12.dp
private val HERO_MIN = FinniDimens.PetFull

/** Финни: ширина к росту по канве рига; Кира рисуется в той же единице (KiraFigure). */
private const val FINNI_W = 100f / 212f
private const val FIGURE_W = 427f
private const val KIRA_CANVAS = 640f
private const val KIRA_LEFT = 43f
private const val KIRA_RIGHT = 590f

/** Подарок и открытка — одного размера, в доле ширины Финни, не мельче 40 dp. */
private const val PRESENT_K = 0.62f
private val PRESENT_MIN = 40.dp

/**
 * Финни слева, Кира справа, в одном масштабе, стоят на полу; подарок или открытка — на полу между ними.
 * Рост — от свободной высоты между плашкой и полом и от ширины: в ряд встают Финни, подарок и Кира.
 */
@Composable
private fun Heroes(s: GameState, given: Boolean, width: Dp, height: Dp) {
    val a = app()
    val g = a.game
    val kiraVisible = (KIRA_RIGHT - KIRA_LEFT) / FIGURE_W
    val byWidth = (width - EVENT_GAP * 2 - PRESENT_MIN) / (1f + kiraVisible)
    val fw = minOf(byWidth, maxOf(height, HERO_MIN) * FINNI_W)
    val unit = fw / FIGURE_W
    val present = (fw * PRESENT_K).coerceIn(PRESENT_MIN, 88.dp)
    val kiraLeft = width - unit * (KIRA_RIGHT - KIRA_LEFT)
    Box(Modifier.fillMaxSize()) {
        Finni(
            s.profile.fur, s.profile.accessory, Modifier.align(Alignment.BottomStart).width(fw),
            reaction = Reaction.HAPPY, reactionKey = 1, animate = a.animationOn, description = s.profile.petName,
        )
        // Подарок или открытка — на одном месте, одного размера и появляются одинаково: оба исхода
        // выглядят наравне (инвариант 3).
        val id = if (given) s.chapter.goalId else "otkrytka"
        val label = if (given) s.chapter.goalId?.let { g.content.goal(it).name } else a.t("a11y.card")
        id?.let {
            Box(Modifier.align(Alignment.BottomStart).offset(x = (fw + kiraLeft) / 2 - present / 2)) {
                Appear("event:$it") { StandPicture(it, present, description = label) }
            }
        }
        // Кира появляется здесь впервые — по правилу появления §7.1; видимый край — у края поля экрана.
        Box(Modifier.align(Alignment.BottomStart).offset(x = kiraLeft - unit * KIRA_LEFT)) {
            Appear("kira") { KiraFigure(fw, description = a.t("a11y.kira")) }
        }
    }
}

/**
 * Задание F5 — сценарий главы 1, неделя 2, шаг 7 (QA-M3). Мячик и подарок рядом, одного размера, у
 * каждого ценник; последствие показано до выбора: какие клетки копилки уйдут — пунктиром. Обе кнопки
 * внизу одинаковые: ни одно решение не помечено верным. Мячик платится только из копилки. Вопрос
 * задания — заголовок экрана (QA-M9). «Мало монет» и объяснение после выбора — в той же плашке.
 */
@Composable
private fun BallChoice(s: GameState, goal: ru.vinteno.finni.core.content.Goal, offer: BallOffer?, after: List<String>?, width: Dp, viewport: Dp) {
    val a = app()
    val ball = a.game.content.item("myachik")
    val cells = (goal.price + COINS_PER_CELL - 1) / COINS_PER_CELL
    val orW = textWidth(a.t("f5.or"), FinniText.Subtitle)
    val cardW = (width - orW - CARD_GAP * 2) / 2
    // Ценник не уменьшается: его высота — от шрифта.
    val tagH = maxOf(36.dp, textHeight(listOf("99"), FinniText.Subtitle, width) + 4.dp)
    val frame = CARD_PAD * 2 + 4.dp + tagH
    val cell = ((width - PLATE_PAD * 2 - CELL_GAP * (cells - 1)) / cells).coerceIn(CELL_MIN, CELL_MAX)
    FitColumn(viewport) {
        Box(Modifier.height(4.dp))
        HeadTitle(s, a.t("f5.title"), width)
        Box(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().flex(min = frame + CHOICE_MIN, max = frame + cardW - CARD_PAD * 2, order = 1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
        ) {
            ChoiceCard(ball.id, ball.name, ball.price, tagH, Modifier.width(cardW))
            Txt(a.t("f5.or"), FinniText.Subtitle.copy(color = FinniColors.InkMute))
            ChoiceCard(goal.id, goal.name, goal.price, tagH, Modifier.width(cardW))
        }
        Box(Modifier.height(8.dp))
        // Плашка держит место под самое длинное своё состояние — последствие с клетками: после выбора
        // и при «мало монет» карточки над ней не прыгают.
        Box(Modifier.fillMaxWidth().softPlate(FinniDimens.RadiusCard - 4.dp).padding(PLATE_PAD)) {
            Column(Modifier.alpha(0f).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(a.f("f5.preview", "n" to ball.price))
                TakeCells(0, 0, cells, cell, gap = CELL_GAP)
                Txt(a.f("f5.left", "n" to goal.price, "goal" to goal.price))
                Txt(listOf(a.t("f5.giftYes"), a.t("f5.giftNo")).maxBy { it.length })
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    after != null -> SlideUp(true) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { after.forEach { Txt(it) } }
                    }
                    offer == null || !offer.available -> Txt(a.t("f5.notEnough"))
                    else -> {
                        Txt(a.f("f5.preview", "n" to offer.price))
                        val filled = minOf(s.progress.savings, goal.price) / COINS_PER_CELL
                        val left = minOf(offer.savingsAfter, goal.price) / COINS_PER_CELL
                        TakeCells(filled, filled - left, cells, cell, gap = CELL_GAP)
                        Txt(a.f("f5.left", "n" to offer.savingsAfter, "goal" to offer.goalPrice))
                        Txt(a.t(if (offer.giftStillPossible) "f5.giftYes" else "f5.giftNo"))
                    }
                }
            }
        }
        Box(Modifier.flex(min = 0.dp))
    }
}

private val PLATE_PAD = 12.dp
private val CARD_GAP = 8.dp
private val CARD_PAD = 8.dp

/** Карточка «мячик» или «подарок»: одинаковые размер, фон, рамка и появление; не нажимается — выбор кнопками. */
@Composable
private fun ChoiceCard(id: String, name: String, price: Int, tagH: Dp, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight().softPlate(FinniDimens.RadiusCard - 4.dp).padding(CARD_PAD),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Picture(id, minOf(maxWidth, maxHeight), description = name)
        }
        Box(Modifier.height(4.dp))
        Box(Modifier.height(tagH), contentAlignment = Alignment.Center) {
            Row(
                Modifier.background(FinniColors.BgSand, RoundedCornerShape(FinniDimens.RadiusSmall + 4.dp)).padding(start = 6.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Coin(24.dp)
                Txt(price.toString(), FinniText.Subtitle)
            }
        }
    }
}
