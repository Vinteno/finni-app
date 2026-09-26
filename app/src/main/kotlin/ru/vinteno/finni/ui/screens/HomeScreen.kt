package ru.vinteno.finni.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.vinteno.finni.core.engine.Step
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.ParcelResult
import ru.vinteno.finni.core.model.Phase
import ru.vinteno.finni.ui.app
import ru.vinteno.finni.ui.components.AdultGate
import ru.vinteno.finni.ui.components.CALENDAR_RATIO
import ru.vinteno.finni.ui.components.Calendar
import ru.vinteno.finni.ui.components.Coin
import ru.vinteno.finni.ui.components.DOOR_HANDLE
import ru.vinteno.finni.ui.components.MARK_BAND
import ru.vinteno.finni.ui.components.MainButton
import ru.vinteno.finni.ui.components.NeedBadge
import ru.vinteno.finni.ui.components.FinniIcons
import ru.vinteno.finni.ui.components.NoteStep
import ru.vinteno.finni.ui.components.Picture
import ru.vinteno.finni.ui.components.PlateText
import ru.vinteno.finni.ui.components.ProgressCells
import ru.vinteno.finni.ui.components.RoomJars
import ru.vinteno.finni.ui.components.SHELF_SURFACE
import ru.vinteno.finni.ui.components.SavingsPlate
import ru.vinteno.finni.ui.components.SayBubble
import ru.vinteno.finni.ui.components.StepMark
import ru.vinteno.finni.ui.components.TAIL
import ru.vinteno.finni.ui.components.TEXT_BOTTOM
import ru.vinteno.finni.ui.components.TEXT_SIDE
import ru.vinteno.finni.ui.components.TEXT_TOP
import ru.vinteno.finni.ui.components.Thing
import ru.vinteno.finni.ui.components.Txt
import ru.vinteno.finni.ui.components.WallNote
import ru.vinteno.finni.ui.components.WalletPlate
import ru.vinteno.finni.ui.components.drawRoom
import ru.vinteno.finni.ui.components.foodLayer
import ru.vinteno.finni.ui.components.roomJarsWidth
import ru.vinteno.finni.ui.components.scrollHint
import ru.vinteno.finni.ui.components.softPlate
import ru.vinteno.finni.ui.components.thingRatio
import ru.vinteno.finni.ui.components.typo
import ru.vinteno.finni.ui.motion.Appear
import ru.vinteno.finni.ui.motion.CoinTarget
import ru.vinteno.finni.ui.motion.SlideUp
import ru.vinteno.finni.ui.motion.anchor
import ru.vinteno.finni.ui.pet.Finni
import ru.vinteno.finni.ui.pet.Reaction
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniMotion
import ru.vinteno.finni.ui.theme.FinniText

enum class HomeTarget { PLAN, SHOP, SITUATION, PIGGY, SORT, SUMMARY, DIARY, ADULT, EVENT }

/** Свободная игра без касаний дольше 30 секунд — Финни засыпает (сценарий §7, свободная игра). */
private const val SLEEP_AFTER_MS = 30_000L

/*
 * Комната — фон всего экрана, и всё в ней привязано к линии пола, стыку стены и пола. Нижней кнопки
 * нет (I45): под вещами на полу остаётся полоса пола не меньше 48 dp. На коротком экране срезается
 * верх стены, а не вещи. Дверь — часть стены: низ ровно на стыке. Финни, посылка, миска, качели и
 * мячик стоят на полу — низ чуть ниже стыка, для глубины. На стене: записка и табличка над дверью
 * слева; справа полка с банками, мылом и копилкой, под ней календарь над миской.
 */

/** Полоса пола под вещами на полу. */
private val STRIP = 48.dp

/** Отступ вещей от края экрана. */
private val SIDE = 8.dp

/** Пустое место между посылкой и соседями: ребёнок промахивается по мелкой цели рядом с большой. */
private val GAP = 16.dp

/** Насколько ниже стыка стоят вещи на полу: чем ближе к ребёнку, тем ниже. */
private val DEPTH_SWING = 4.dp
private val DEPTH_FINNI = 10.dp
private val DEPTH_PARCEL = 12.dp
private val DEPTH_BOWL = 14.dp

/** Посылка и миска — ширина по непрозрачному краю картинки. */
private val PARCEL = 52.dp
private val BOWL = 68.dp

/** Дверь всегда выше Финни. */
private const val DOOR_K = 1.08f
private val DOOR_W = 1f / thingRatio("dver")

/** Финни: ширина к росту — канва рига 100 × 212. */
private const val FINNI_W = 100f / 212f

/** Рост Финни, от которого считаются полка и вещи на ней. Не мельче 96 dp по гайду (§7.4). */
private val FINNI_REF = 158.dp
private val FINNI_MAX = 220.dp

/** Самый маленький Финни: не мельче 96 dp по гайду и не уже 48 dp — это его зона нажатия. */
private val FINNI_MIN = maxOf(FinniDimens.PetFull, FinniDimens.MinTouch / FINNI_W)

/**
 * Вещи на полке: банки, мыло, копилка. Зона нажатия каждой — не меньше 48 dp, вещь в ней стоит на
 * доске. Банки втроём — один предмет.
 */
private val PIGGY = 58.dp
private val SOAP = 44.dp
private val JARS_H = 30.dp
private val SHELF_GAP = 2.dp

/** Доля ширины полки, где на доске стоят вещи: края доски — под кронштейнами. */
private const val SHELF_USABLE = 0.9f

/** Окно уже этого — уже не окно: пропадает совсем. */
private val WINDOW_MIN = 56.dp

/** Календарь на стене над миской. */
private val CALENDAR = 50.dp

/** Записка: ширина по умолчанию и самая узкая, на которой шаг ещё читается по словам. */
private val NOTE_MIN = 104.dp
private val NOTE_MAX = 150.dp

/** Картинка цели и клетки в плашке накоплений. */
private val GOAL_PIC = 28.dp
private val PRICE_COIN = 14.dp
private val CELL = 10.dp
private val CELL_MIN = 7.dp

/** Предметы комнаты, у каждого шага недели — свой (I45). */
private enum class Prop { PARCEL, JARS, DOOR, BOWL, SOAP, PIGGY, CALENDAR }

/** Где неделя сейчас — от этого зависит ответ предмета на касание (таблица I45). */
private enum class Stage { BEFORE_PARCEL, BEFORE_PLAN, AFTER_PLAN, AFTER_SUMMARY, AFTER_EVENT }

/** Предмет текущего шага: над ним значок. */
private fun stepProp(step: Step, canFeed: Boolean): Prop? = when (step) {
    Step.PARCEL -> Prop.PARCEL
    Step.ANNOUNCE, Step.PLAN -> Prop.JARS
    Step.SHOP -> Prop.DOOR
    Step.CARE -> if (canFeed) Prop.BOWL else Prop.SOAP
    Step.SAVE -> Prop.PIGGY
    Step.SORT, Step.SUMMARY, Step.NEXT_WEEK -> Prop.CALENDAR
    Step.EVENT, Step.NONE -> null
}

/** Слова шага на записке — те же, что были на нижней кнопке. */
private val STEP_KEYS = listOf(
    "step.parcel", "step.plan", "step.shop", "step.care", "step.wash", "step.save", "step.piggy",
    "step.sort", "step.summary", "step.nextWeek", "home.note.chapterDone",
)

/**
 * Дом — сценарий главы 1, §5а; I45. Комната несёт требования ТЗ 2.5.3 сама: питомец, кошелёк,
 * накопления клетками с числом, цель, три потребности, записка с текущим шагом недели.
 * Нижней кнопки нет: шаг показывает записка — она не нажимается, — действие делается касанием предмета комнаты, над
 * предметом шага — неподвижный значок. Ни одно касание не остаётся без ответа: предмет, чей шаг
 * ещё не настал, отвечает репликой Финни — что сделать сначала.
 */
@Composable
fun HomeScreen(s: GameState, open: (HomeTarget) -> Unit) {
    val a = app()
    val g = a.game
    val w = s.week
    val step = g.nextStep(s)
    val scope = rememberCoroutineScope()
    var parcelNote by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableIntStateOf(0) }
    var sleeping by remember { mutableStateOf(false) }
    // Реплика Финни: уходит при следующем касании экрана.
    var say by remember { mutableStateOf<String?>(null) }
    var lookRight by remember { mutableStateOf(false) }
    // Где стоят предметы — из последней раскладки: в их сторону Финни замечает.
    val propX = remember { mutableMapOf<Prop, Float>() }
    var finniX by remember { mutableStateOf(0f) }
    val walk = remember { Animatable(0f) }
    val ballJump = remember { Animatable(0f) }
    // Еда, которую Финни сейчас ест: в игре он уже сыт, а на экране она лежит, пока идёт анимация.
    var eatingFood by remember { mutableStateOf<String?>(null) }
    var foodFading by remember { mutableStateOf(false) }
    val foodFade = remember { Animatable(1f) }

    val canFeed = w != null && s.phase != Phase.ONBOARDING && g.canFeed(s)
    val target = stepProp(step, canFeed)
    // Плашка перехода главы и плашка бонуса взрослого — после посылки и объявления, по одной.
    val transitionUp = s.phase == Phase.TRANSITION
    val bonusUp = w != null && w.bonus > 0 && !w.bonusSeen && !parcelNote && step != Step.ANNOUNCE
    // Смена обстановки на переходе главы: кроссфейд или затемнение, 320 мс; без анимаций — сразу.
    val fade = remember(s.transition?.chapter) { Animatable(if (s.phase == Phase.TRANSITION && a.animationOn) 1f else 0f) }
    LaunchedEffect(s.transition?.chapter) { fade.animateTo(0f, tween(FinniMotion.SCREEN_MS)) }
    // Плашка на экране: пока её не закрыли, предметы только замечают её.
    val plateUp = parcelNote || step == Step.ANNOUNCE || transitionUp || bonusUp
    val stage = when {
        s.phase == Phase.FREE_PLAY || s.phase == Phase.TRANSITION -> Stage.AFTER_EVENT
        s.phase == Phase.AFTER_SUMMARY || s.phase == Phase.EVENT -> Stage.AFTER_SUMMARY
        w == null || w.parcel == null -> Stage.BEFORE_PARCEL
        !w.planConfirmed -> Stage.BEFORE_PLAN
        else -> Stage.AFTER_PLAN
    }

    LaunchedEffect(step) { if (step == Step.EVENT) open(HomeTarget.EVENT) }
    // Шаг сменился — Финни замечает в сторону предмета нового шага (сначала посылки, шаг 1). Пока
    // на экране плашка, он замечает её; предмет — когда плашку закрыли.
    LaunchedEffect(target, plateUp, w?.number) {
        if (target == null) return@LaunchedEffect
        withFrameNanos { }
        if (!plateUp) {
            propX[target]?.let { lookRight = it > finniX }
            a.react(Reaction.NOTICE)
        }
    }
    var poke by remember { mutableIntStateOf(0) }
    // Объявление ситуации: Финни замечает плашку (шаг 2).
    LaunchedEffect(step, parcelNote) { if (step == Step.ANNOUNCE && !parcelNote) a.react(Reaction.NOTICE) }
    val freePlay = s.phase == Phase.AFTER_SUMMARY || s.phase == Phase.FREE_PLAY
    LaunchedEffect(freePlay, lastTouch) {
        sleeping = false
        if (freePlay) { delay(SLEEP_AFTER_MS); sleeping = true }
    }

    /** Коробка открывается, монеты вылетают в кошелёк, счётчик растёт вместе с прилётом (шаг 1). */
    fun openParcel() {
        val before = s.progress.wallet
        if (!a.act(g::openParcel)) return
        parcelNote = true
        val came = a.state.value.progress.wallet - before
        a.flights.launch("parcel", "wallet", came, CoinTarget.WALLET, a.animationOn)
    }

    /** Кормление: событийное перемещение к миске, до 800 мс, затем `ест` и обратно (animation-howto §6.4). */
    fun feed() {
        // Еда видна в миске, пока Финни прыгает к ней и ест. Без анимаций — исчезает сразу.
        if (a.animationOn) eatingFood = foodLayer(w?.purchases.orEmpty())
        if (!a.act(g::feed)) { eatingFood = null; return }
        scope.launch {
            if (a.animationOn) walk.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
            a.react(Reaction.EAT)
            delay(520)
            if (a.animationOn) {
                // Сразу после «ест» слой еды гаснет за 200 мс, остаётся пустая миска (§7.3).
                launch {
                    foodFade.snapTo(1f)
                    foodFading = true
                    foodFade.animateTo(0f, tween(FinniMotion.APPEAR_MS, easing = LinearOutSlowInEasing))
                    eatingFood = null
                    foodFading = false
                }
                walk.animateTo(0f, tween(800, easing = FastOutSlowInEasing))
            }
        }
    }

    fun happy() = a.react(Reaction.HAPPY)
    fun line(key: String) { say = a.t(key) }

    /**
     * Касание предмета — таблица I45. Реплики называют, что сделать сначала, и никогда не советуют,
     * что купить: «Миска пустая.» — факт. После события главы предметы отвечают `доволен`.
     */
    fun tap(p: Prop) {
        if (plateUp) { a.react(Reaction.NOTICE); return }
        when (p) {
            Prop.PARCEL -> openParcel()
            Prop.BOWL -> when {
                canFeed -> feed()
                w?.fed == true -> happy()
                else -> line("home.say.bowlEmpty")
            }
            Prop.SOAP -> if (a.act(g::wash)) happy()
            Prop.JARS -> when (stage) {
                Stage.BEFORE_PARCEL -> line("home.say.parcelFirst")
                Stage.AFTER_EVENT -> happy()
                else -> open(HomeTarget.PLAN)
            }
            Prop.DOOR -> when (stage) {
                Stage.BEFORE_PARCEL -> line("home.say.parcelFirst")
                Stage.BEFORE_PLAN -> open(HomeTarget.SHOP)
                // Неделя с ситуацией: сначала экран выбора, вариант ложится в корзину (главы 2 и 3).
                Stage.AFTER_PLAN -> open(if (g.situationOpen(s)) HomeTarget.SITUATION else HomeTarget.SHOP)
                Stage.AFTER_SUMMARY -> line("home.say.weekOver")
                Stage.AFTER_EVENT -> happy()
            }
            Prop.PIGGY -> when (stage) {
                Stage.BEFORE_PARCEL -> line("home.say.parcelFirst")
                Stage.AFTER_EVENT -> happy()
                else -> open(HomeTarget.PIGGY)
            }
            // Календарь ведёт к итогу и следующей неделе; когда не ведёт — открывает дневник (I49, F4).
            Prop.CALENDAR -> when (stage) {
                Stage.AFTER_PLAN -> when {
                    g.sortPending(s) && g.shopDone(s) -> open(HomeTarget.SORT)
                    g.summaryOpen(s) -> open(HomeTarget.SUMMARY)
                    else -> open(HomeTarget.DIARY)
                }
                Stage.AFTER_SUMMARY -> a.act(g::nextWeek)
                Stage.AFTER_EVENT -> if (s.week != null) open(HomeTarget.DIARY) else happy()
                else -> if (w != null) open(HomeTarget.DIARY) else line("home.say.parcelFirst")
            }
        }
    }

    // Записка — только текущий шаг. Она не нажимается: ребёнок идёт к предмету шага сам, значок над
    // ним показывает, к какому.
    val noteStep = when (step) {
        Step.PARCEL -> "step.parcel"
        Step.ANNOUNCE, Step.PLAN -> "step.plan"
        Step.SHOP -> "step.shop"
        Step.CARE -> if (canFeed) "step.care" else "step.wash"
        Step.SAVE -> if (g.canDeposit(s)) "step.save" else "step.piggy"
        Step.SORT -> "step.sort"
        Step.SUMMARY -> "step.summary"
        Step.NEXT_WEEK -> "step.nextWeek"
        Step.EVENT, Step.NONE -> "home.note.chapterDone"
    }.let(a::t)
    // Плашка накоплений: цель с клетками по 5 монет и «N из M». После события цели нет — только
    // «Накопили N» (QA-M4).
    val goal = s.chapter.goalId?.takeIf { s.phase != Phase.FREE_PLAY }?.let { g.content.goal(it) }
    val savedText = when {
        goal != null || s.phase == Phase.FREE_PLAY -> a.f("home.saved", "n" to s.progress.savings)
        else -> null
    }
    val texts = RoomTexts(STEP_KEYS.map(a::t), a.t("home.shopSign"), savedText, goal?.price)
    // В банках комнаты — черновик плана до подтверждения, потом — сколько осталось в каждом направлении.
    val jars = w?.let {
        if (it.planConfirmed) listOf(it.plan.need - it.paidNeed, it.plan.want - it.paidWant, it.plan.save - it.deposit).map { v -> v.coerceAtLeast(0) }
        else listOf(it.plan.need, it.plan.want, it.plan.save)
    } ?: listOf(0, 0, 0)
    // Мыло стоит на полке, пока куплено и не использовано: умыл Финни — мыла нет.
    val soapShown = w != null && s.phase != Phase.ONBOARDING && g.canWash(s)

    val tm = rememberTextMeasurer()
    // Комната выше экрана только при крупном шрифте; тогда сначала видны пол, Финни и дверь,
    // а записка и копилка — прокруткой вверх.
    val stageScroll = rememberScrollState(Int.MAX_VALUE)

    // Плашки посылки и объявления: выезжают снизу, закрываются «Понятно».
    val plateLines: List<String>? = when {
        parcelNote -> parcelLines(s)
        step == Step.ANNOUNCE -> g.weekContent(s).announcement.map { a.f(it, "name" to s.profile.petName) }
        transitionUp -> a.explain.transitionLines(s)
        bonusUp -> listOf(a.f("bonus.plate", "n" to w!!.bonus))
        else -> null
    }
    // Картинка плашки: посылка — у записки бабушки, предмет недели — у объявления (гайд §12.2), копилка —
    // у бонуса взрослого.
    val platePicture = when {
        parcelNote -> "posylka"
        step == Step.ANNOUNCE -> g.weekContent(s).announceItem
        bonusUp -> "kopilka"
        else -> null
    }
    // Плашка выезжает и уезжает обратно (§7.4); пока уезжает, показывает прежние строки.
    var lastPlate by remember { mutableStateOf<Pair<List<String>, String?>>(emptyList<String>() to null) }
    if (plateLines != null) lastPlate = plateLines to platePicture

    SubcomposeLayout(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                lastTouch++
                say = null
            }
        },
    ) { c ->
        val loose = c.copy(minWidth = 0, minHeight = 0)
        val width = c.maxWidth
        val height = c.maxHeight
        // Шапка: потребности слева, кошелёк справа. Не помещаются в ряд (крупный шрифт) — потребности
        // строкой ниже.
        val purse = subcompose(Slot.PURSE) { WalletPlate(s.progress.wallet) }.map { it.measure(loose) }
        val needs = subcompose(Slot.NEEDS) { Needs(fed = w?.fed == true, clean = w?.washed == true, warm = !g.cold(s)) }.map { it.measure(loose) }
        val pad = HEADER_PAD.roundToPx()
        val purseW = purse.maxOf { it.width }
        val purseH = purse.maxOf { it.height }
        val needsW = needs.maxOf { it.width }
        val needsH = needs.maxOf { it.height }
        val stacked = needsW + purseW + pad * 3 > width
        val needsY = if (stacked) pad + purseH + 4.dp.roundToPx() else pad
        // «Взрослым» — под кошельком справа (I49, F6); на крупном шрифте — в строку с потребностями,
        // не помещается — строкой ниже.
        val gate = subcompose(Slot.GATE) { AdultGate(a.t("home.adult"), onOpen = { open(HomeTarget.ADULT) }) }.map { it.measure(loose) }
        val gateW = gate.maxOf { it.width }
        val gateH = gate.maxOf { it.height }
        val gap4 = 4.dp.roundToPx()
        val gateY = when {
            !stacked -> pad + purseH + gap4
            needsW + gateW + pad * 3 <= width -> needsY
            else -> needsY + needsH + gap4
        }
        val leftTop = (needsY + needsH).toDp()
        val rightTop = maxOf(if (stacked) needsY + needsH else pad + purseH, gateY + gateH).toDp()
        val headerH = maxOf(needsY + needsH, pad + purseH, gateY + gateH)

        val floor = height.toDp() - STRIP - DEPTH_BOWL
        val plan = planRoom(width.toDp(), leftTop, rightTop, floor, texts, tm, this)
        // Не помещается и при самом маленьком Финни (крупный шрифт) — комната выше экрана, и полоса
        // под шапкой прокручивается; в конце прокрутки пол — внизу, как без неё.
        val extra = plan.lack.roundToPx()
        val scrolling = extra > 0
        val viewTop = if (scrolling) headerH else 0
        val viewH = height - viewTop
        val stageH = height + extra
        val fl = (floor.roundToPx() + extra).toDp()
        plan.props(fl).forEach { (p, x) -> propX[p] = x.toPx() }
        finniX = (plan.finniX + plan.finniW / 2).toPx()

        val back = subcompose(Slot.BACK) {
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) { drawRoom(floor.toPx()) }
                // Переход в главу 2: холодная комната проявляется сквозь прежнюю, 320 мс.
                if (transitionUp && s.progress.chapter == 2) {
                    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) { drawRoom(floor.toPx(), "room_sand") }
                }
            }
        }.map { it.measure(Constraints.fixed(width, height)) }
        // Переезд в главу 3: экран гаснет на 320 мс — единственное затемнение в игре — и открывается новый дом.
        val dark = subcompose(Slot.DARK) {
            if (transitionUp && s.progress.chapter == 3) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.background(androidx.compose.ui.graphics.Color.Black))
            }
        }.map { it.measure(Constraints.fixed(width, height)) }
        val stage = subcompose(Slot.STAGE) {
            Box(Modifier.fillMaxSize().then(if (scrolling) Modifier.verticalScroll(stageScroll) else Modifier)) {
                Room(
                    s, plan, fl, stageH.toDp(), Modifier.layout { m, _ ->
                        val r = m.measure(Constraints.fixed(width, stageH))
                        layout(width, stageH - viewTop) { r.place(0, -viewTop) }
                    },
                    background = scrolling,
                    note = noteStep, jars = jars, soapShown = soapShown,
                    // Пока открыта плашка, значка нет: предметы сейчас только замечают её.
                    mark = target?.takeIf { stage != Stage.AFTER_EVENT && !plateUp },
                    say = say,
                    onProp = ::tap,
                    onBall = {
                        // Свободная игра: мячик подпрыгивает, Финни — `доволен`. Ничего не даёт.
                        scope.launch { ballJump.animateTo(1f, tween(160)); ballJump.animateTo(0f, tween(160)) }
                        a.react(Reaction.HAPPY)
                    },
                    // Касание Финни — реакция ушами и подсказка, что сделать сейчас, словами записки (I49, F5).
                    onFinni = {
                        poke++
                        if (!plateUp && step != Step.NONE && step != Step.EVENT) say = noteStep
                    },
                    finni = { m ->
                        // Событийное перемещение к миске — тремя прыжками, не скольжением (animation-howto §6.4).
                        val hop = kotlin.math.abs(kotlin.math.sin(walk.value * 3f * Math.PI.toFloat()))
                        val moving = walk.value > 0f && walk.value < 1f
                        Box(m.graphicsLayer { translationX = plan.toBowl.toPx() * walk.value }) {
                            Finni(
                                s.profile.fur, s.profile.accessory, Modifier.width(plan.finniW),
                                reaction = if (sleeping) Reaction.SLEEP else a.reaction, reactionKey = a.reactionKey,
                                animate = a.animationOn,
                                // Замечает в сторону предмета шага: слева — посылка и дверь, справа — полка и миска.
                                lookRight = lookRight,
                                earPoke = poke,
                                hop = hop, moving = moving,
                                description = s.profile.petName,
                                wear = g.worn(s),
                                // `зябнет` — только дома, с перехода в главу 2 до покупки куртки (I49, G2).
                                cold = g.cold(s) && !sleeping && !moving,
                            )
                        }
                    },
                    food = { if (w?.fed == false) foodLayer(w.purchases) else eatingFood },
                    foodAlpha = { if (foodFading) foodFade.value else 1f },
                    ballLift = { ballJump.value },
                )
            }
        }.map { it.measure(Constraints.fixed(width, viewH)) }
        val hint = subcompose(Slot.HINT) {
            // Полоса прокрутки — у правого края, только когда есть что докрутить.
            Box(Modifier.fillMaxSize().padding(end = 8.dp).scrollHint(stageScroll))
        }.map { it.measure(Constraints.fixed(width, viewH)) }

        // Плашка посылки или объявления не закрывает Финни: встаёт на полосу пола под его ногами, а не
        // помещается там — наверху стены, под шапкой, до макушки Финни; не влезает и туда — прокручивается.
        val scrolled = if (scrolling) stageScroll.value else 0
        val finniTop = (fl + DEPTH_FINNI - plan.finniH).roundToPx() - scrolled
        val finniBottom = (fl + DEPTH_FINNI).roundToPx() - scrolled
        val gap = 8.dp.roundToPx()
        val plate = subcompose(Slot.PLATE) {
            SlideUp(plateLines != null) {
                BottomPlate(lastPlate.first, lastPlate.second, a.t("common.ok")) {
                    when {
                        parcelNote -> parcelNote = false
                        transitionUp -> a.act(g::seeTransition)
                        step == Step.ANNOUNCE -> a.act(g::seeAnnouncement)
                        bonusUp -> a.act(g::seeBonus)
                    }
                }
            }
        }
        val natural = plate.maxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0
        val below = height - finniBottom - gap
        val onFloor = natural <= below
        // Плашка может закрыть «Взрослым»: пока она открыта, раздел взрослого не нужен, а место — нужно.
        val topY = (if (stacked) headerH else maxOf(needsY + needsH, pad + purseH)) + gap
        val room = if (onFloor) below else (finniTop - gap - topY).coerceAtLeast(FinniDimens.MainButtonHeight.roundToPx() * 2)
        val placedPlate = plate.map { it.measure(Constraints(maxWidth = width, minWidth = width, maxHeight = room)) }

        layout(width, height) {
            back.forEach { it.place(0, 0) }
            stage.forEach { it.place(0, viewTop) }
            if (scrolling) hint.forEach { it.place(0, viewTop) }
            needs.forEach { it.place(pad, needsY) }
            purse.forEach { it.place(width - pad - purseW, pad) }
            gate.forEach { it.place(width - pad - gateW, gateY) }
            dark.forEach { it.place(0, 0) }
            placedPlate.forEach { it.place(0, if (onFloor) height - it.height else topY) }
        }
    }
}

private enum class Slot { PURSE, NEEDS, GATE, BACK, STAGE, HINT, DARK, PLATE }

private val HEADER_PAD = 12.dp

/**
 * Тексты, от которых зависит раскладка: шаги и задания на записке, табличка, строка накоплений.
 * Место под записку — по самому длинному шагу с самым длинным заданием: Финни и дверь не меняют
 * размер от шага к шагу.
 */
private data class RoomTexts(val steps: List<String>, val sign: String, val saved: String?, val price: Int?) {
    /** Клетки по 5 монет до цены цели. */
    val cells: Int? get() = price?.let { (it + 4) / 5 }
}

/** Где что стоит, в dp от верха комнаты и левого края экрана, при полу [fl] без прокрутки. */
private data class RoomPlan(
    val finniW: Dp, val finniH: Dp, val finniX: Dp, val toBowl: Dp,
    val doorW: Dp, val doorH: Dp,
    val parcelX: Dp, val bowlX: Dp,
    val noteW: Dp, val noteH: Dp, val noteTop: Dp,
    val signH: Dp,
    val shelfW: Dp, val shelfTop: Dp, val piggyW: Dp, val soapW: Dp, val jarsH: Dp, val itemsH: Dp,
    val calendarW: Dp, val calendarTop: Dp,
    /** Окно: ширины 0 — окна нет. */
    val windowW: Dp, val windowX: Dp, val windowTop: Dp,
    val plateMaxW: Dp, val plateH: Dp,
    /** Сколько высоты не хватило при самом маленьком Финни: на столько комната прокручивается. */
    val lack: Dp,
    /** Клетка плашки накоплений. */
    val cell: Dp = CELL,
) {
    val shelfLeft: Dp get() = bowlX + BOWL - shelfW
    val shelfH: Dp get() = shelfW * thingRatio("polka")
    val board: Dp get() = shelfTop + shelfH * SHELF_SURFACE
    val jarsX: Dp get() = shelfLeft + shelfW * (1f - SHELF_USABLE) / 2
    val jarsZone: Dp get() = maxOf(FinniDimens.MinTouch, roomJarsWidth(jarsH))
    val soapX: Dp get() = jarsX + jarsZone + SHELF_GAP
    val piggyX: Dp get() = shelfLeft + shelfW * (1f + SHELF_USABLE) / 2 - maxOf(FinniDimens.MinTouch, piggyW)
    val calendarX: Dp get() = bowlX + BOWL / 2 - calendarW / 2

    /** Середина каждого предмета по горизонтали — в ту сторону Финни замечает. */
    fun props(fl: Dp): Map<Prop, Dp> = mapOf(
        Prop.PARCEL to parcelX + PARCEL / 2,
        Prop.DOOR to SIDE + doorW / 2,
        Prop.JARS to jarsX + jarsZone / 2,
        Prop.SOAP to soapX + FinniDimens.MinTouch / 2,
        Prop.PIGGY to piggyX + maxOf(FinniDimens.MinTouch, piggyW) / 2,
        Prop.BOWL to bowlX + BOWL / 2,
        Prop.CALENDAR to calendarX + calendarW / 2,
    )
}

/**
 * Раскладка комнаты. Размеры — от свободной высоты между шапкой и полом и от ширины экрана: Финни,
 * дверь и полка масштабируются вместе. Финни — самый крупный, при котором по ширине помещаются дверь,
 * посылка с пустыми полосами по 16 dp, Финни и миска, а по высоте — слева записка, значок и табличка
 * над дверью, справа — плашка, значок и вещи над полкой, под полкой значок и календарь, под ними
 * значок над миской; окно — между дверью и Финни. Места мало — сначала уменьшается записка, потом
 * пропадает окно.
 */
private fun planRoom(width: Dp, leftTop: Dp, rightTop: Dp, floor: Dp, t: RoomTexts, tm: TextMeasurer, d: Density): RoomPlan {
    fun textH(text: String, style: TextStyle, maxW: Dp): Dp = with(d) {
        tm.measure(typo(text), style, constraints = Constraints(maxWidth = maxW.roundToPx().coerceAtLeast(1)), density = d).size.height.toDp()
    }
    fun textW(text: String, style: TextStyle): Dp = with(d) { tm.measure(text, style, density = d).size.width.toDp() }
    val noteRatio = thingRatio("zapiska")
    fun noteH(nw: Dp, step: String): Dp {
        val inner = nw * (1f - 2 * TEXT_SIDE)
        val body = textH(step, NoteStep, inner)
        return maxOf(nw * noteRatio, body / (1f - TEXT_TOP - TEXT_BOTTOM))
    }
    val signH = textH(t.sign, PlateText, width) + 4.dp
    // Самое длинное слово шагов помещается на листке целиком: при крупном шрифте листок шире.
    val longestWord = t.steps.flatMap { typo(it).split(' ') }.maxOf { textW(it, NoteStep) }
    val noteMin = maxOf(NOTE_MIN, longestWord / (1f - 2 * TEXT_SIDE) + 4.dp)
    val noteMax = maxOf((width * 0.36f).coerceIn(NOTE_MIN, NOTE_MAX), noteMin)
    val widths = generateSequence(noteMax) { it - 4.dp }.takeWhile { it >= noteMin }.toList().ifEmpty { listOf(noteMax) }
    fun reserveH(nw: Dp) = t.steps.maxOf { step -> noteH(nw, step) }
    val reserveW = widths.minBy(::reserveH)
    val noteReserve = reserveH(reserveW)

    // Плашка накоплений — справа от записки, строки переносятся по словам; слово не рвётся. Рядом
    // не помещается (крупный шрифт) — плашка встаёт ниже записки, во всю ширину.
    fun wordW(text: String) = typo(text).split(' ').maxOf { textW(it, PlateText) }
    // Справа в плашке — цель: картинка и под ней монета с ценой.
    val priceSize = t.price?.let { with(d) { tm.measure(it.toString(), PlateText, density = d).size.let { z -> z.width.toDp() to z.height.toDp() } } }
    val goalCol = priceSize?.let { maxOf(GOAL_PIC, PRICE_COIN + 4.dp + it.first) } ?: 0.dp
    val pic = if (t.price != null) goalCol + 8.dp else 0.dp
    val plateFrame = 20.dp + pic + 2.dp
    val besideNote = width - SIDE - (SIDE + 4.dp + reserveW + 8.dp)
    // Клетки мельчают, чтобы плашка встала рядом с запиской: у целей глав 2 и 3 их до 12, а не 6–9.
    val cell = t.cells?.let { n -> ((besideNote - plateFrame - 4.dp * (n - 1)) / n).coerceIn(CELL_MIN, CELL) } ?: CELL
    val cellsW = t.cells?.let { cell * it + 4.dp * (it - 1) } ?: 0.dp
    val plateMinW = t.saved?.let { plateFrame + maxOf(cellsW, wordW(it)) } ?: 0.dp
    val below = plateMinW > besideNote
    val plateMaxW = if (below) width - SIDE * 2 else besideNote
    val plateH = t.saved?.let { saved ->
        val cells = if (t.cells != null) cell + 4.dp else 0.dp
        maxOf(FinniDimens.MinTouch, 12.dp + maxOf(priceSize?.let { GOAL_PIC + it.second } ?: 0.dp, cells + textH(saved, PlateText, plateMaxW - plateFrame)))
    } ?: 0.dp
    val plateTop = if (below) maxOf(rightTop, leftTop + 8.dp + noteReserve) + 8.dp else rightTop + 8.dp

    // По ширине: дверь | 16 | посылка | 16 | Финни | 8 | миска.
    val row = width - SIDE * 2 - GAP * 2 - PARCEL - 8.dp - BOWL
    val byWidth = row / (DOOR_K * DOOR_W + FINNI_W)
    val free = floor - leftTop

    fun scale(hf: Dp) = (hf / FINNI_REF).coerceIn(0.8f, 1.25f)
    fun jarsZone(u: Float) = maxOf(FinniDimens.MinTouch, roomJarsWidth(JARS_H * u))
    fun shelfW(u: Float) = maxOf(128.dp * u, (jarsZone(u) + SHELF_GAP + maxOf(FinniDimens.MinTouch, SOAP * u) + SHELF_GAP + maxOf(FinniDimens.MinTouch, PIGGY * u)) / SHELF_USABLE)
    fun itemsH(u: Float) = maxOf(FinniDimens.MinTouch, PIGGY * u * thingRatio("kopilka"))
    fun calendarW(u: Float) = maxOf(FinniDimens.MinTouch, CALENDAR * u)
    val bowlH = BOWL * thingRatio("miska")

    // Справа сверху вниз: плашка, хвостик, значок, вещи на доске, полка; снизу вверх: миска, значок,
    // календарь, значок. Полка к тому же не ниже верха двери — над головой Финни.
    fun shelfBottomMin(u: Float) = plateTop + plateH + TAIL + MARK_BAND + itemsH(u) + shelfW(u) * thingRatio("polka") * (1f - SHELF_SURFACE)
    fun calendarTop(fl: Dp, u: Float) = fl + DEPTH_BOWL - bowlH - MARK_BAND - 4.dp - calendarW(u) * CALENDAR_RATIO
    fun shelfBottomMax(fl: Dp, hd: Dp, u: Float) = minOf(calendarTop(fl, u) - MARK_BAND - 4.dp, fl - hd - 8.dp)
    /** Сколько места над верхом двери нужно слева: записка, значок над дверью и табличка. */
    // Плашка под запиской занимает и левую половину — тогда табличка с дверью ниже неё.
    fun leftNeed(noteH: Dp) = maxOf(8.dp + noteH + 8.dp, if (below) plateTop - leftTop + plateH + TAIL + 8.dp else 0.dp) + MARK_BAND + signH + 4.dp
    fun lackAt(hf: Dp): Dp {
        val u = scale(hf)
        val hd = hf * DOOR_K
        return maxOf(leftNeed(noteReserve) - (free - hd), shelfBottomMin(u) - shelfBottomMax(floor, hd, u), 0.dp)
    }

    var hf = minOf(byWidth, FINNI_MAX)
    while (hf > FINNI_MIN && lackAt(hf) > 0.dp) hf -= 2.dp
    hf = hf.coerceAtLeast(FINNI_MIN)
    val lack = lackAt(hf)
    val fl = floor + lack
    val u = scale(hf)
    val hd = hf * DOOR_K

    // Записка — самая широкая, что помещается над значком и табличкой; не помещается никакая — самая низкая.
    val nw = widths.firstOrNull { leftNeed(reserveH(it)) <= fl - hd - leftTop } ?: widths.minBy(::reserveH)
    val nh = reserveH(nw)

    val fw = hf * FINNI_W
    val parcelX = SIDE + hd * DOOR_W + GAP
    val bowlX = width - SIDE - BOWL
    val finniX = (width / 2 - fw / 2).coerceIn(parcelX + PARCEL + GAP, maxOf(parcelX + PARCEL + GAP, bowlX - 8.dp - fw))

    // Окно — на стене между дверью и серединой Финни: верх на высоте верха двери, низ не ниже ручки и
    // над значком посылки. Не помещается окно шириной 56 dp — окна нет, оно декор и уступает первым.
    val doorTop = fl - hd
    val windowL = SIDE + hd * DOOR_W + 8.dp
    val windowR = finniX + fw / 2
    val windowBottom = minOf(doorTop + hd * DOOR_HANDLE, fl + DEPTH_PARCEL - PARCEL * thingRatio("posylka") - MARK_BAND - 4.dp)
    val windowH = minOf(windowBottom - doorTop, (windowR - windowL) * thingRatio("okno"))
    val windowW = (windowH / thingRatio("okno")).takeIf { it >= WINDOW_MIN } ?: 0.dp
    val windowX = windowL + (windowR - windowL - windowW) / 2

    // Полка — как можно ниже: над календарём с его значком и не ниже верха двери.
    val sw = shelfW(u)
    val shelfTop = shelfBottomMax(fl, hd, u) - sw * thingRatio("polka")

    return RoomPlan(
        finniW = fw, finniH = hf, finniX = finniX, toBowl = bowlX + BOWL / 2 - finniX - fw,
        doorW = hd * DOOR_W, doorH = hd,
        parcelX = parcelX, bowlX = bowlX,
        noteW = nw, noteH = nh, noteTop = leftTop + 8.dp,
        signH = signH,
        shelfW = sw, shelfTop = shelfTop, piggyW = PIGGY * u, soapW = SOAP * u, jarsH = JARS_H * u, itemsH = itemsH(u),
        calendarW = calendarW(u), calendarTop = calendarTop(fl, u),
        windowW = windowW, windowX = windowX, windowTop = doorTop,
        plateMaxW = plateMaxW, plateH = plateH,
        lack = lack,
        cell = cell,
    )
}

/** Положить вещь низом на [bottom], левым краем на [x]. Пустое место над ней касания не ловит. */
private fun Modifier.standOn(x: Dp, bottom: Dp) = offset(x = x).height(bottom)

/** Предмет комнаты для касания и диктора: кнопка с названием; картинки внутри не читаются отдельно. */
private fun Modifier.prop(name: String, onClick: () -> Unit) =
    semantics { contentDescription = name; role = Role.Button }.clickable(null, null, role = Role.Button, onClick = onClick)

/**
 * Комната: фон во весь экран, линия пола на картинке — на [floor]. Порядок — от стены к ребёнку:
 * записка, полка с банками, мылом и копилкой, календарь, дверь, качели, миска, посылка, мячик, Финни,
 * значок шага [mark] и реплика [say].
 */
@Composable
private fun Room(
    s: GameState,
    p: RoomPlan,
    floor: Dp,
    height: Dp,
    modifier: Modifier,
    background: Boolean,
    note: String,
    jars: List<Int>,
    soapShown: Boolean,
    mark: Prop?,
    say: String?,
    onProp: (Prop) -> Unit,
    onBall: () -> Unit,
    onFinni: () -> Unit,
    finni: @Composable (Modifier) -> Unit,
    food: () -> String?,
    foodAlpha: () -> Float,
    ballLift: () -> Float,
) {
    val a = app()
    val g = a.game
    val w = s.week
    Box(modifier.fillMaxWidth().height(height)) {
        // Фон комнаты лежит под всем экраном; свой — только у прокручиваемой комнаты.
        if (background) Canvas(Modifier.fillMaxSize()) { drawRoom(floor.toPx()) }

        // ---------- Стена ----------
        // Записка читается диктором, но не нажимается.
        WallNote(note, null, p.noteW, p.noteH, Modifier.offset(x = SIDE + 4.dp, y = p.noteTop).semantics(mergeDescendants = true) {})
        // Окно — декор на стене между дверью и Финни, может уходить за Финни. Места мало — окна нет.
        // В главе 2 — окно с инеем целиком на замену обычного (final-plan §3, п. 1).
        val window = if (s.progress.chapter == 2) "okno_inej" else "okno"
        if (p.windowW > 0.dp) Thing(window, p.windowW, Modifier.offset(x = p.windowX, y = p.windowTop))
        // Гирлянда — на стене над окном, огоньки статичные (сценарий главы 3, §10).
        if ("girlyanda" in s.progress.inventory) {
            val gw = if (p.windowW > 0.dp) p.windowW * 1.3f else 96.dp * (p.finniH / FINNI_REF)
            val gx = if (p.windowW > 0.dp) p.windowX - (gw - p.windowW) / 2 else SIDE + p.doorW + 4.dp
            val gy = (if (p.windowW > 0.dp) p.windowTop else floor - p.doorH) - gw * thingRatio("girlyanda") * 0.7f
            Appear("girlyanda", Modifier.offset(x = gx, y = gy)) { Thing("girlyanda", gw, description = g.content.item("girlyanda").name) }
        }

        // Полка справа, над календарём и миской. На ней банки плана, мыло, пока оно куплено, и копилка.
        Thing("polka", p.shelfW, Modifier.offset(x = p.shelfLeft, y = p.shelfTop))
        Box(Modifier.standOn(p.jarsX, p.board)) {
            Box(
                Modifier.align(Alignment.BottomStart).size(p.jarsZone, FinniDimens.MinTouch)
                    .prop(a.t("plan.need") + ", " + a.t("plan.want") + ", " + a.t("plan.save")) { onProp(Prop.JARS) },
                contentAlignment = Alignment.BottomCenter,
            ) {
                val scaleMax = maxOf(g.ch(s).income, jars.max())
                RoomJars(jars, scaleMax, p.jarsH)
            }
        }
        if (w != null && soapShown) {
            Box(Modifier.standOn(p.soapX, p.board)) {
                Appear("soap:${w.number}", Modifier.align(Alignment.BottomStart)) {
                    Box(
                        Modifier.size(maxOf(FinniDimens.MinTouch, p.soapW)).prop(g.content.item("mylo").name) { onProp(Prop.SOAP) },
                        contentAlignment = Alignment.BottomCenter,
                    ) { Thing("mylo", p.soapW) }
                }
            }
        }
        Box(Modifier.standOn(p.piggyX, p.board)) {
            Box(
                Modifier.align(Alignment.BottomStart).anchor(a.flights, "piggy").prop(a.t("piggy.title")) { onProp(Prop.PIGGY) },
            ) {
                // Зона нажатия — не меньше 48 dp, копилка в ней стоит на доске.
                Box(Modifier.sizeIn(minWidth = FinniDimens.MinTouch, minHeight = FinniDimens.MinTouch), contentAlignment = Alignment.BottomCenter) {
                    Thing("kopilka", p.piggyW)
                }
            }
        }
        // Плашка накоплений — над копилкой, хвостиком к ней: будто сведения исходят от неё. Между ними —
        // полоса значка шага.
        s.chapter.goalId?.let {
            val saved = a.f("home.saved", "n" to s.progress.savings)
            val itemsTop = p.board - p.itemsH - MARK_BAND
            val right = p.bowlX + BOWL
            val goal = g.content.goal(it).takeIf { s.phase != Phase.FREE_PLAY }
            Box(Modifier.fillMaxWidth().height(itemsTop - TAIL).padding(end = SIDE), contentAlignment = Alignment.BottomEnd) {
                SavingsPlate(
                    tailFromEnd = right - (p.piggyX + maxOf(FinniDimens.MinTouch, p.piggyW) / 2),
                    maxWidth = p.plateMaxW,
                    modifier = Modifier.prop(a.t("piggy.title")) { onProp(Prop.PIGGY) },
                ) {
                    // Клетки по 5 монет и «Накопили N», рядом цель с ценой.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            goal?.let { gl -> ProgressCells(minOf(s.progress.savings, gl.price) / 5, (gl.price + 4) / 5, cell = p.cell) }
                            Txt(saved, PlateText)
                        }
                        goal?.let { gl ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Picture(gl.id, GOAL_PIC, description = gl.name)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Coin(PRICE_COIN)
                                    Txt(gl.price.toString(), PlateText)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Календарь — листок на стене над миской, неделя цифрой. Итог и следующая неделя — через него.
        // Номер на календаре — сквозной за игру: «Неделя 3» в главе 2.
        val weekNo = g.weekNumber(s)
        Box(
            Modifier.offset(x = p.calendarX, y = p.calendarTop)
                .prop(a.t("home.week") + " " + weekNo) { onProp(Prop.CALENDAR) },
        ) { Calendar(weekNo, p.calendarW) }

        // ---------- Пол ----------
        // Дверь — часть стены: низ ровно на стыке стены и пола. Над ней — табличка «Магазин».
        Box(Modifier.standOn(SIDE, floor)) {
            Column(
                Modifier.align(Alignment.BottomStart).width(p.doorW),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Табличка шире двери (крупный шрифт) — от левого края двери вправо, не за край экрана.
                Box(
                    Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                        .softPlate(FinniDimens.RadiusSmall).padding(horizontal = 8.dp, vertical = 2.dp),
                ) { Txt(a.t("home.shopSign"), PlateText) }
                // Нажимается сама дверь: табличка над ней может нависать над местом посылки.
                Box(Modifier.prop(a.t("home.shopSign")) { onProp(Prop.DOOR) }) { Thing("dver", p.doorW) }
            }
        }

        // Полотенце висит на крючке двери: другого места на стене при 360 dp нет (решение I50). Касания
        // проходят к двери.
        if ("polotence" in s.progress.inventory) {
            val tw = p.doorW * 0.34f
            val id = if ("vyshivka" in s.progress.inventory) "polotence_vyshivka" else "polotence"
            Appear("polotence", Modifier.offset(x = SIDE + p.doorW * 0.6f, y = floor - p.doorH * 0.78f)) {
                Thing(id, tw, box = "polotence", description = g.content.item("polotence").name)
            }
        }

        // Задний ряд у стены — вещи глав 2 и 3 (санки, коробки переезда, коробка, лампа, цели глав 2 и 3):
        // стоят на линии пола, мельче вещей на полу и за ними; касания не ловят.
        BackRow(s, p, floor)

        // Качели и мячик — купленные вещи остаются навсегда. Стоят между дверью и Финни: мячик — на месте
        // посылки (посылки в это время уже нет), качели — у стены, за посылкой и мячиком. После новоселья
        // с целью главы 3 вещи сложены в неё.
        val slotL = SIDE + p.doorW + 4.dp
        val slotR = p.finniX - 4.dp
        val packed = packedAway(s)
        if ("kacheli" in s.progress.inventory && !packed) {
            val sw = minOf(slotR - slotL, 84.dp * (p.finniH / FINNI_REF))
            Box(Modifier.standOn(slotL + (slotR - slotL - sw) / 2, floor + DEPTH_SWING)) {
                Appear("kacheli", Modifier.align(Alignment.BottomStart)) {
                    Thing("kacheli", sw, description = g.content.item("kacheli").name)
                }
            }
        }

        // Миска на полу справа стоит всё время; в ней то, что куплено: крупа, каша или каша с ягодами.
        Box(Modifier.standOn(p.bowlX, floor + DEPTH_BOWL)) {
            Box(Modifier.align(Alignment.BottomStart).prop(a.t("a11y.bowl")) { onProp(Prop.BOWL) }) {
                Thing("miska", BOWL)
                // Еда появляется по правилу появления §7.1, одинаково для любой ступеньки, и гаснет
                // после «ест» тоже одинаково — меняется только этот слой.
                food()?.let { f ->
                    Appear("food:$f:${w?.number}") {
                        Thing(f, BOWL, Modifier.graphicsLayer { alpha = foodAlpha() }, box = "miska")
                    }
                }
            }
        }

        // Посылка у двери, пока не открыта: между ней и соседями — пустые полосы.
        if (w != null && w.parcel == null) {
            Box(Modifier.standOn(p.parcelX, floor + DEPTH_PARCEL)) {
                Appear("parcel:${w.number}", Modifier.align(Alignment.BottomStart)) {
                    Box(Modifier.anchor(a.flights, "parcel").prop(a.t("a11y.parcel")) { onProp(Prop.PARCEL) }) {
                        Thing("posylka", PARCEL)
                    }
                }
            }
        }
        if ("myachik" in s.progress.inventory && !packed && !(w != null && w.parcel == null)) {
            val x = (slotL + slotR) / 2 - FinniDimens.MinTouch / 2
            Box(Modifier.standOn(x, floor + DEPTH_BOWL)) {
                Box(
                    Modifier.align(Alignment.BottomStart)
                        .graphicsLayer { translationY = -ballLift() * 24.dp.toPx() }
                        .prop(g.content.item("myachik").name, onBall),
                ) {
                    Appear("myachik") {
                        Box(Modifier.size(FinniDimens.MinTouch), contentAlignment = Alignment.BottomCenter) {
                            Thing("myachik", 40.dp)
                        }
                    }
                }
            }
        }

        // Финни ближе к центру, не мельче 96 dp.
        Box(Modifier.standOn(p.finniX, floor + DEPTH_FINNI)) {
            finni(Modifier.align(Alignment.BottomStart).clickable(null, null, onClick = onFinni))
        }

        // Значок над предметом текущего шага — неподвижный (инвариант 4).
        mark?.let { m ->
            val top = when (m) {
                Prop.PARCEL -> floor + DEPTH_PARCEL - PARCEL * thingRatio("posylka") - MARK_BAND
                Prop.DOOR -> floor - p.doorH - 4.dp - p.signH - MARK_BAND
                Prop.JARS, Prop.SOAP, Prop.PIGGY -> p.board - p.itemsH - MARK_BAND
                Prop.BOWL -> floor + DEPTH_BOWL - BOWL * thingRatio("miska") - MARK_BAND
                Prop.CALENDAR -> p.calendarTop - MARK_BAND
            }
            if (m != Prop.SOAP || soapShown) StepMark(p.props(floor).getValue(m), top)
        }

        // Реплика Финни — над его головой, хвостиком к нему; не выходит за края экрана.
        say?.let { text ->
            Box(
                Modifier.fillMaxWidth().height(floor + DEPTH_FINNI - p.finniH - TAIL).padding(horizontal = SIDE),
                contentAlignment = Alignment.BottomStart,
            ) { SayBubble(text, anchorX = p.finniX + p.finniW / 2 - SIDE, maxWidth = 220.dp) }
        }
    }
}

/** Цели главы 3 — куда складываются вещи на новоселье. */
private val STORAGE = listOf("polka_veshchey", "korzina", "sunduk")

/** Вещи сложены в цель главы 3: она куплена на новоселье (сценарий главы 3, §7). */
private fun packedAway(s: GameState): Boolean = STORAGE.any { it in s.progress.inventory }

/**
 * Задний ряд у стены: что стоит и в каком порядке слева направо, с шириной при Финни обычного роста.
 * Санки — у двери; коробки переезда — пока вещи не сложены; дальше коробка, лампа, мебель главы 2,
 * цель главы 3 с вещами.
 */
private fun backRow(s: GameState): List<Pair<String, Dp>> {
    val inv = s.progress.inventory
    val packed = packedAway(s)
    return buildList {
        if ("sanki" in inv && !packed) add("sanki" to 62.dp)
        // Мячик стоит на месте посылки; пока посылка у двери (главы 2 и 3), он лежит у стены.
        if ("myachik" in inv && !packed && s.week?.parcel == null && s.week != null) add("myachik" to 30.dp)
        if (s.progress.chapter == 3 && !packed) add("korobki_pereezd" to 70.dp)
        if ("korobka" in inv && !packed) add((if ("nakleyki" in inv) "korobka_nakleyki" else "korobka") to 38.dp)
        if ("lampa" in inv) add((if ("abazhur" in inv) "lampa_abazhur" else "lampa") to 34.dp)
        listOf("plaid" to 66.dp, "lezhanka" to 62.dp, "pechka" to 40.dp).forEach { (id, w) -> if (id in inv) add(id to w) }
        STORAGE.forEach { if (it in inv) add("${it}_full" to 58.dp) }
    }
}

/** Рамка картинки вещи заднего ряда: у вещи с надбавкой — рамка основы. */
private fun rowBox(id: String) = when (id) {
    "korobka_nakleyki" -> "korobka"
    "lampa_abazhur" -> "lampa"
    else -> id
}

/**
 * Задний ряд: от двери до правого края, вещи ровно по местам с одинаковыми промежутками; не помещаются —
 * все уменьшаются вместе, но не мельче 0,6. Появляются по правилу появления §7.1, одинаково для базы и
 * надбавки.
 */
@Composable
private fun BackRow(s: GameState, p: RoomPlan, floor: Dp) {
    val items = backRow(s)
    if (items.isEmpty()) return
    val u = p.finniH / FINNI_REF
    val left = SIDE + p.doorW + 2.dp
    val avail = (p.bowlX + BOWL - left).coerceAtLeast(1.dp)
    val natural = items.fold(0.dp) { acc, it -> acc + it.second * u }
    val k = minOf(1f, (avail / natural)).coerceAtLeast(0.6f)
    val gap = ((avail - natural * k) / (items.size + 1)).coerceAtLeast(0.dp)
    var x = left + gap
    items.forEach { (id, w) ->
        val iw = w * u * k
        Box(Modifier.standOn(x, floor + 2.dp)) {
            Appear("row:$id", Modifier.align(Alignment.BottomStart)) {
                Thing(id, iw, box = rowBox(id), description = app().game.content.items[id.substringBefore("_")]?.name)
            }
        }
        x += iw + gap
    }
}

/** Три потребности: миска, мыло, тепло. «Тепло» закрыто всегда, кроме главы 2 до покупки куртки (I9). */
@Composable
private fun Needs(fed: Boolean, clean: Boolean, warm: Boolean) {
    val a = app()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NeedBadge("item_miska", FinniIcons.Fed, a.t("home.state.fed"), fed)
        NeedBadge("item_mylo", FinniIcons.Clean, a.t("home.state.clean"), clean)
        NeedBadge(if (warm) "acc_scarf" else "item_kurtka", FinniIcons.Warm, a.t("home.state.warm"), warm)
    }
}

@Composable
private fun parcelLines(s: GameState): List<String> {
    val a = app()
    val w = s.week ?: return emptyList()
    return if (w.parcel == ParcelResult.ARRIVED) {
        listOf(
            a.t(if (w.number == 1) "parcel.note.first" else "parcel.note.again"),
            a.f("parcel.amount", "n" to s.progress.wallet),
        )
    } else listOf(a.t("parcel.none.1"), a.t("parcel.none.2"))
}

/**
 * Плашка записки бабушки или объявления ситуации. Одна главная кнопка. Первая строка —
 * главная, крупнее; остальные — основным текстом, как в окне §10.8: так видно, с чего начинать
 * читать. Рядом с первой строкой — картинка того, о чём речь. При шрифте ×2,0 плашка прокручивается.
 */
@Composable
private fun BottomPlate(lines: List<String>, picture: String?, ok: String, modifier: Modifier = Modifier, onOk: () -> Unit) {
    Box(modifier.fillMaxWidth().padding(FinniDimens.ScreenPadding), contentAlignment = Alignment.BottomCenter) {
        val shape = RoundedCornerShape(FinniDimens.RadiusCard)
        Column(
            Modifier.fillMaxWidth().padding(bottom = FinniDimens.BottomGap - FinniDimens.ScreenPadding)
                .background(FinniColors.Surface, shape).border(FinniDimens.Outline, FinniColors.StrokeStrong, shape)
                .padding(FinniDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Над Финни места мало (крупный шрифт) — прокручиваются строки, «Понятно» видна всегда.
            val scroll = rememberScrollState()
            Column(
                Modifier.weight(1f, fill = false).scrollHint(scroll).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    picture?.let { Picture(it, 48.dp) }
                    lines.firstOrNull()?.let { Txt(it, FinniText.Subtitle, Modifier.weight(1f)) }
                }
                lines.drop(1).forEach { Txt(it) }
            }
            MainButton(ok, onOk)
        }
    }
}
