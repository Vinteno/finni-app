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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import ru.vinteno.finni.ui.components.Coin
import ru.vinteno.finni.ui.components.DOOR_HANDLE
import ru.vinteno.finni.ui.components.ExplainPlate
import ru.vinteno.finni.ui.components.FinniIcons
import ru.vinteno.finni.ui.components.Icon
import ru.vinteno.finni.ui.components.MainButton
import ru.vinteno.finni.ui.components.NeedBadge
import ru.vinteno.finni.ui.components.Picture
import ru.vinteno.finni.ui.components.PlateText
import ru.vinteno.finni.ui.components.ProgressCells
import ru.vinteno.finni.ui.components.SHELF_SURFACE
import ru.vinteno.finni.ui.components.SavingsPlate
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

enum class HomeTarget { PLAN, SHOP, PIGGY, SUMMARY, EVENT }

/** Свободная игра без касаний дольше 30 секунд — Финни засыпает (сценарий §7, свободная игра). */
private const val SLEEP_AFTER_MS = 30_000L

/*
 * Комната — фон всего экрана, и всё в ней привязано к линии пола, стыку стены и пола. Линия пола —
 * чуть выше нижнего блока «подсказка + кнопка»; на коротком экране срезается верх стены, а не вещи.
 * Дверь — часть стены: низ ровно на стыке. Финни, посылка, миска, качели и мячик стоят на полу —
 * низ чуть ниже стыка, для глубины. Полка с копилкой и мылом, окно и записка — на стене.
 */

/** Линия пола — на столько выше нижнего блока. */
private val LIFT = 12.dp

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

/** Вещи на полке: копилка и мыло. Зона нажатия мыла — не меньше 48 dp, мыло в ней стоит на доске. */
private val PIGGY = 58.dp
private val SOAP = 44.dp

/** Записка: ширина по умолчанию и самая узкая, на которой название ещё читается по словам. */
private val NOTE_MIN = 104.dp
private val NOTE_MAX = 150.dp

/** Окно ниже этого — уже не окно: пропадает совсем. */
private val WINDOW_MIN = 56.dp

/** Картинка цели и клетки в плашке накоплений. */
private val GOAL_PIC = 28.dp
private val PRICE_COIN = 14.dp
private val CELL = 10.dp

/**
 * Дом — сценарий главы 1, §5а. Комната несёт требования ТЗ 2.5.3 сама: питомец, кошелёк,
 * накопления клетками с числом, цель, три потребности, записка с заданием.
 * Внизу одна кнопка с текущим шагом недели — ребёнок не выбирает маршрут, он видит одно действие.
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
    val walk = remember { Animatable(0f) }
    val ballJump = remember { Animatable(0f) }
    // Еда, которую Финни сейчас ест: в игре он уже сыт, а на экране она лежит, пока идёт анимация.
    var eatingFood by remember { mutableStateOf<String?>(null) }
    var foodFading by remember { mutableStateOf(false) }
    val foodFade = remember { Animatable(1f) }

    LaunchedEffect(step) { if (step == Step.EVENT) open(HomeTarget.EVENT) }
    // Посылка у двери: Финни замечает коробку, 200 мс, затем idle (шаг 1).
    LaunchedEffect(step == Step.PARCEL, w?.number) { if (step == Step.PARCEL) a.react(Reaction.NOTICE) }
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

    fun wash() {
        if (a.act(g::wash)) a.react(Reaction.HAPPY)
    }

    /**
     * Дверь и записка ведут в магазин. До подтверждения плана магазин закрыт (инвариант 6),
     * поэтому нажатие ведёт в план — это и есть дверь в магазин. Пока не открыта посылка и не
     * прочитано объявление, а также после итога недели Финни только «замечает» — ни одно
     * нажатие не остаётся без отклика (QA-M10, QA-B17).
     */
    fun toShop() {
        val wk = w ?: return
        when {
            s.phase != Phase.WEEK -> a.react(Reaction.NOTICE)
            wk.planConfirmed -> open(HomeTarget.SHOP)
            wk.announcementSeen -> open(HomeTarget.PLAN)
            else -> a.react(Reaction.NOTICE)
        }
    }

    // Записка на стене с активным заданием. После события главы задание сделано и следующего
    // в прототипе нет — записки нет (QA-M4).
    val noteTitle = w?.takeIf { s.phase != Phase.FREE_PLAY }
        ?.let { g.weekContent(s).taskId }?.let { a.t(g.content.chapter1.task(it).title) }
    // Плашка накоплений: цель с клетками по 5 монет и «N из M». После события цели нет — только
    // «Накопили N», и копилка не нажимается (QA-M4).
    val goal = s.chapter.goalId?.takeIf { s.phase != Phase.FREE_PLAY }?.let { g.content.goal(it) }
    val savedText = when {
        goal != null || s.phase == Phase.FREE_PLAY -> a.f("home.saved", "n" to s.progress.savings)
        else -> null
    }
    val openPiggy: (() -> Unit)? = if (goal != null) ({ open(HomeTarget.PIGGY) }) else null
    val shopSign = a.t("home.shopSign")
    val allTasks = g.content.chapter1.tasks.map { a.t(it.title) }

    val overlay = parcelNote || step == Step.ANNOUNCE || a.pendingPlate != null
    val tm = rememberTextMeasurer()
    // Комната выше экрана только при крупном шрифте; тогда сначала видны пол, Финни и дверь,
    // а записка и копилка — прокруткой вверх.
    val stageScroll = rememberScrollState(Int.MAX_VALUE)

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                lastTouch++
            }
        },
    ) {
        SubcomposeLayout(Modifier.fillMaxSize()) { c ->
            val loose = c.copy(minWidth = 0, minHeight = 0)
            val width = c.maxWidth
            val height = c.maxHeight
            val purse = subcompose(Slot.PURSE) {
                Purse(s.progress.wallet, onWeek = { if (w?.announcementSeen == true) open(HomeTarget.PLAN) else a.react(Reaction.NOTICE) })
            }.map { it.measure(loose) }
            val needs = subcompose(Slot.NEEDS) { Needs(fed = w?.fed == true, clean = w?.washed == true) }.map { it.measure(loose) }
            val bottom = subcompose(Slot.BOTTOM) {
                BottomBlock(showHint = step == Step.CARE && !overlay) {
                    if (!overlay) when (step) {
                        Step.PARCEL -> MainButton(a.t("step.parcel"), ::openParcel)
                        Step.PLAN -> MainButton(a.t("step.plan"), { open(HomeTarget.PLAN) })
                        Step.SHOP -> MainButton(a.t("step.shop"), { open(HomeTarget.SHOP) })
                        // Кнопка называет то действие, которое сделает: сначала покормить, потом умыть.
                        Step.CARE -> MainButton(a.t(if (g.canFeed(s)) "step.care" else "step.wash"), { if (g.canFeed(s)) feed() else wash() })
                        // На неделе с заданием F5 шаг «Копилка» стоит и тогда, когда откладывать нечего (QA-M3).
                        Step.SAVE -> MainButton(a.t(if (g.canDeposit(s)) "step.save" else "step.piggy"), { open(HomeTarget.PIGGY) })
                        Step.SUMMARY -> MainButton(a.t("step.summary"), { open(HomeTarget.SUMMARY) })
                        Step.NEXT_WEEK -> MainButton(a.t("step.nextWeek"), { a.act(g::nextWeek) })
                        else -> {}
                    }
                }
            }.map { it.measure(loose.copy(minWidth = width, maxWidth = width)) }

            // Шапка: потребности слева, кошелёк и «Неделя» справа. Не помещаются в ряд (крупный
            // шрифт) — потребности строкой ниже, как раньше в верхней полосе.
            val pad = HEADER_PAD.roundToPx()
            val purseW = purse.maxOf { it.width }
            val purseH = purse.maxOf { it.height }
            val needsW = needs.maxOf { it.width }
            val needsH = needs.maxOf { it.height }
            val stacked = needsW + purseW + pad * 3 > width
            val needsY = if (stacked) pad + purseH + 4.dp.roundToPx() else pad
            val leftTop = (needsY + needsH).toDp()
            val rightTop = (if (stacked) needsY + needsH else pad + purseH).toDp()
            val bottomH = bottom.maxOf { it.height }

            val texts = RoomTexts(noteTitle, allTasks, shopSign, savedText, goal?.price)
            val floor = (height - bottomH).toDp() - LIFT
            val plan = planRoom(width.toDp(), leftTop, rightTop, floor, texts, tm, this)
            // Не помещается и при самом маленьком Финни (крупный шрифт) — комната выше экрана, и полоса
            // между шапкой и нижним блоком прокручивается; в конце прокрутки пол — над кнопкой, как без неё.
            val extra = plan.lack.roundToPx()
            val scrolling = extra > 0
            val headerH = maxOf(needsY + needsH, pad + purseH)
            val viewTop = if (scrolling) headerH else 0
            val viewH = if (scrolling) height - headerH - bottomH else height
            val stageH = height + extra
            val back = subcompose(Slot.BACK) { Canvas(Modifier.fillMaxSize()) { drawRoom(floor.toPx()) } }
                .map { it.measure(Constraints.fixed(width, height)) }
            val stage = subcompose(Slot.STAGE) {
                Box(Modifier.fillMaxSize().then(if (scrolling) Modifier.verticalScroll(stageScroll) else Modifier)) {
                    Room(
                        s, plan, (floor.roundToPx() + extra).toDp(), stageH.toDp(), texts,
                        Modifier.layout { m, _ ->
                            val r = m.measure(Constraints.fixed(width, stageH))
                            layout(width, stageH - viewTop - (height - viewTop - viewH)) { r.place(0, -viewTop) }
                        },
                        background = scrolling,
                        onShop = ::toShop, onPiggy = openPiggy, onParcel = ::openParcel,
                        onBowl = { if (g.canFeed(s)) feed() }, onSoap = ::wash,
                        onBall = {
                            // Свободная игра: мячик подпрыгивает, Финни — `доволен`. Ничего не даёт.
                            scope.launch { ballJump.animateTo(1f, tween(160)); ballJump.animateTo(0f, tween(160)) }
                            a.react(Reaction.HAPPY)
                        },
                        onFinni = { poke++ },
                        finni = { m ->
                            // Событийное перемещение к миске — тремя прыжками, не скольжением (animation-howto §6.4).
                            val hop = kotlin.math.abs(kotlin.math.sin(walk.value * 3f * Math.PI.toFloat()))
                            val moving = walk.value > 0f && walk.value < 1f
                            Box(m.graphicsLayer { translationX = plan.toBowl.toPx() * walk.value }) {
                                Finni(
                                    s.profile.fur, s.profile.accessory, Modifier.width(plan.finniW),
                                    reaction = if (sleeping) Reaction.SLEEP else a.reaction, reactionKey = a.reactionKey,
                                    animate = a.animationOn,
                                    // Посылка и дверь — слева от Финни: туда он и смотрит, когда замечает.
                                    lookRight = false,
                                    earPoke = poke,
                                    hop = hop, moving = moving,
                                    description = s.profile.petName,
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

            layout(width, height) {
                back.forEach { it.place(0, 0) }
                stage.forEach { it.place(0, viewTop) }
                if (scrolling) hint.forEach { it.place(0, viewTop) }
                needs.forEach { it.place(pad, needsY) }
                purse.forEach { it.place(width - pad - purseW, pad) }
                bottom.forEach { it.place(0, height - bottomH) }
            }
        }

        // ---------- Плашки: выезжают снизу, закрываются «Понятно» ----------
        val plateLines: List<String>? = when {
            parcelNote -> parcelLines(s)
            step == Step.ANNOUNCE -> g.weekContent(s).announcement.map { a.f(it, "name" to s.profile.petName) }
            else -> null
        }
        // Картинка плашки: посылка — у записки бабушки, предмет недели — у объявления (гайд §12.2).
        val platePicture = when {
            parcelNote -> "posylka"
            step == Step.ANNOUNCE -> g.weekContent(s).announceItem
            else -> null
        }
        // Плашка выезжает снизу и уезжает обратно (§7.4); пока уезжает, показывает прежние строки.
        var lastPlate by remember { mutableStateOf<Pair<List<String>, String?>>(emptyList<String>() to null) }
        if (plateLines != null) lastPlate = plateLines to platePicture
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            val maxPlate = maxHeight
            SlideUp(plateLines != null) {
                BottomPlate(lastPlate.first, lastPlate.second, a.t("common.ok"), Modifier.heightIn(max = maxPlate)) {
                    if (parcelNote) parcelNote = false else a.act(g::seeAnnouncement)
                }
            }
        }
        var lastExplain by remember { mutableStateOf<List<String>>(emptyList()) }
        a.pendingPlate?.let { lastExplain = it }
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            val maxPlate = maxHeight
            SlideUp(plateLines == null && a.pendingPlate != null) {
                Column(Modifier.heightIn(max = maxPlate).verticalScroll(rememberScrollState())
                    .padding(FinniDimens.ScreenPadding).padding(bottom = FinniDimens.BottomGap - FinniDimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExplainPlate(lastExplain, onClose = { a.pendingPlate = null }) { PetIcon(s) }
                    MainButton(a.t("common.ok"), { a.pendingPlate = null })
                }
            }
        }
    }
}

private enum class Slot { PURSE, NEEDS, BOTTOM, BACK, STAGE, HINT }

private val HEADER_PAD = 12.dp

/**
 * Тексты, от которых зависит раскладка: название задания на записке, табличка, строка накоплений.
 * Место под записку — по самому длинному заданию главы [allNotes]: Финни и дверь не меняют размер
 * от недели к неделе.
 */
private data class RoomTexts(val note: String?, val allNotes: List<String>, val sign: String, val saved: String?, val price: Int?) {
    /** Клетки по 5 монет до цены цели. */
    val cells: Int? get() = price?.let { (it + 4) / 5 }
}

/** Где что стоит, в dp от верха комнаты и левого края экрана. Пол — [floor]; окна нет — [windowW] 0. */
private data class RoomPlan(
    val finniW: Dp, val finniH: Dp, val finniX: Dp, val toBowl: Dp,
    val doorW: Dp, val doorH: Dp,
    val parcelX: Dp, val bowlX: Dp,
    val noteW: Dp, val noteH: Dp, val noteTop: Dp,
    val signH: Dp,
    val shelfW: Dp, val shelfTop: Dp, val piggyW: Dp, val soapW: Dp,
    val plateMaxW: Dp, val plateH: Dp,
    val windowW: Dp, val windowTop: Dp, val windowX: Dp,
    /** Сколько высоты не хватило при самом маленьком Финни: на столько комната прокручивается. */
    val lack: Dp,
)

/**
 * Раскладка комнаты. Размеры — от свободной высоты между шапкой и полом и от ширины экрана: Финни,
 * дверь и полка масштабируются вместе. Финни — самый крупный, при котором по ширине помещаются дверь,
 * посылка с пустыми полосами по 16 dp, Финни и миска, а по высоте — записка и табличка над дверью
 * слева и копилка с плашкой над полкой справа. Места мало — сначала уменьшаются окно и записка, потом
 * окно пропадает совсем; дверь, полка с копилкой, миска и посылка остаются всегда.
 */
private fun planRoom(width: Dp, leftTop: Dp, rightTop: Dp, floor: Dp, t: RoomTexts, tm: TextMeasurer, d: Density): RoomPlan {
    fun textH(text: String, style: TextStyle, maxW: Dp): Dp = with(d) {
        tm.measure(typo(text), style, constraints = Constraints(maxWidth = maxW.roundToPx().coerceAtLeast(1)), density = d).size.height.toDp()
    }
    val noteRatio = thingRatio("zapiska")
    fun noteH(nw: Dp, title: String? = t.note): Dp {
        if (title == null) return nw * noteRatio
        val need = textH(title, PlateText, nw * (1f - 2 * TEXT_SIDE)) / (1f - TEXT_TOP - TEXT_BOTTOM)
        return maxOf(nw * noteRatio, need)
    }
    val signH = textH(t.sign, PlateText, width) + 4.dp
    // Самое длинное слово заданий помещается на листке целиком: при крупном шрифте листок шире.
    val longestWord = t.allNotes.flatMap { typo(it).split(' ') }.maxOfOrNull { word ->
        with(d) { tm.measure(word, PlateText, density = d).size.width.toDp() }
    } ?: 0.dp
    val noteMin = maxOf(NOTE_MIN, longestWord / (1f - 2 * TEXT_SIDE) + 4.dp)
    val noteMax = maxOf((width * 0.36f).coerceIn(NOTE_MIN, NOTE_MAX), noteMin)
    val widths = generateSequence(noteMax) { it - 4.dp }.takeWhile { it >= noteMin }.toList().ifEmpty { listOf(noteMax) }
    fun reserveH(nw: Dp) = (t.allNotes.map { noteH(nw, it) } + noteH(nw, null)).max()
    val reserveW = widths.minBy(::reserveH)
    val noteReserve = reserveH(reserveW)

    // Плашка накоплений — справа от записки, строки переносятся по словам; слово не рвётся. Рядом
    // не помещается (крупный шрифт) — плашка встаёт ниже записки, во всю ширину.
    fun wordW(text: String) = typo(text).split(' ').maxOf { with(d) { tm.measure(it, PlateText, density = d).size.width.toDp() } }
    // Справа в плашке — цель: картинка и под ней монета с ценой.
    val priceSize = t.price?.let { with(d) { tm.measure(it.toString(), PlateText, density = d).size.let { z -> z.width.toDp() to z.height.toDp() } } }
    val goalCol = priceSize?.let { maxOf(GOAL_PIC, PRICE_COIN + 4.dp + it.first) } ?: 0.dp
    val pic = if (t.price != null) goalCol + 8.dp else 0.dp
    val cellsW = t.cells?.let { CELL * it + 4.dp * (it - 1) } ?: 0.dp
    val plateFrame = 20.dp + pic + 2.dp
    val besideNote = width - SIDE - (SIDE + 4.dp + reserveW + 8.dp)
    val plateMinW = t.saved?.let { plateFrame + maxOf(cellsW, wordW(it)) } ?: 0.dp
    val below = plateMinW > besideNote
    val plateMaxW = if (below) width - SIDE * 2 else besideNote
    val plateH = t.saved?.let { saved ->
        val cells = if (t.cells != null) CELL + 4.dp else 0.dp
        maxOf(FinniDimens.MinTouch, 12.dp + maxOf(priceSize?.let { GOAL_PIC + it.second } ?: 0.dp, cells + textH(saved, PlateText, plateMaxW - plateFrame)))
    } ?: 0.dp
    val plateTop = if (below) maxOf(rightTop, leftTop + 8.dp + noteReserve) + 8.dp else rightTop + 8.dp

    // По ширине: дверь | 16 | посылка | 16 | Финни | 8 | миска.
    val row = width - SIDE * 2 - GAP * 2 - PARCEL - 8.dp - BOWL
    val byWidth = row / (DOOR_K * DOOR_W + FINNI_W)
    val free = floor - leftTop

    fun scale(hf: Dp) = (hf / FINNI_REF).coerceIn(0.8f, 1.25f)
    fun shelfW(u: Float) = maxOf(128.dp * u, (48.dp + 8.dp + PIGGY * u) / 0.8f)
    /** Сколько места над верхом двери нужно слева (записка и табличка) и справа (плашка и копилка над полкой). */
    // Плашка под запиской занимает и левую половину — тогда табличка с дверью ниже неё.
    fun leftNeed(noteH: Dp) = maxOf(8.dp + noteH + 8.dp, if (below) plateTop - leftTop + plateH + TAIL + 8.dp else 0.dp) + signH + 4.dp
    fun rightNeed(u: Float) = plateTop - leftTop + plateH + TAIL + 2.dp + PIGGY * u * thingRatio("kopilka") -
        shelfW(u) * thingRatio("polka") * SHELF_SURFACE
    fun fits(hf: Dp) = free - hf * DOOR_K >= maxOf(leftNeed(noteReserve), rightNeed(scale(hf)))

    var hf = minOf(byWidth, FINNI_MAX)
    while (hf > FINNI_MIN && !fits(hf)) hf -= 2.dp
    hf = hf.coerceAtLeast(FINNI_MIN)
    val lack = (maxOf(leftNeed(noteReserve), rightNeed(scale(hf))) - (free - hf * DOOR_K)).coerceAtLeast(0.dp)
    val fl = floor + lack
    val u = scale(hf)
    val hd = hf * DOOR_K
    val doorTop = fl - hd

    // Записка — самая широкая, что помещается над табличкой.
    // Записка — самая широкая, что помещается над табличкой; не помещается никакая — самая низкая.
    val nw = widths.firstOrNull { leftNeed(noteH(it)) <= fl - hd - leftTop } ?: widths.minBy { noteH(it) }
    val nh = noteH(nw)

    val fw = hf * FINNI_W
    val parcelX = SIDE + hd * DOOR_W + GAP
    val bowlX = width - SIDE - BOWL
    val finniX = (width / 2 - fw / 2).coerceIn(parcelX + PARCEL + GAP, maxOf(parcelX + PARCEL + GAP, bowlX - 8.dp - fw))

    // Полка — выше окна, верх не ниже верха двери; копилка с плашкой над ней должны поместиться.
    val sw = shelfW(u)
    val sh = sw * thingRatio("polka")
    val piggyH = PIGGY * u * thingRatio("kopilka")
    val shelfMin = plateTop + plateH + TAIL + 2.dp + piggyH - sh * SHELF_SURFACE
    val shelfTop = maxOf(shelfMin, doorTop - 8.dp - sh)

    // Окно: верх — на высоте верха двери, подоконник — на высоте ручки; не лезет на полку.
    val windowTop = maxOf(doorTop, shelfTop + sh + 8.dp)
    val windowH = doorTop + hd * DOOR_HANDLE - windowTop
    val windowW = if (windowH >= WINDOW_MIN) windowH / thingRatio("okno") else 0.dp
    val shelfX = width - SIDE - sw
    val windowMaxX = width - SIDE - windowW
    val windowX = ((finniX + fw / 2 + shelfX + sw / 2) / 2 - windowW / 2).coerceIn(minOf(finniX + fw / 2, windowMaxX), windowMaxX)

    return RoomPlan(
        finniW = fw, finniH = hf, finniX = finniX, toBowl = bowlX + BOWL / 2 - finniX - fw,
        doorW = hd * DOOR_W, doorH = hd,
        parcelX = parcelX, bowlX = bowlX,
        noteW = nw, noteH = nh, noteTop = leftTop + 8.dp,
        signH = signH,
        shelfW = sw, shelfTop = shelfTop, piggyW = PIGGY * u, soapW = SOAP * u,
        plateMaxW = plateMaxW, plateH = plateH,
        windowW = windowW, windowTop = windowTop, windowX = windowX,
        lack = lack,
    )
}

/** Положить вещь низом на [bottom], левым краем на [x]. Пустое место над ней касания не ловит. */
private fun Modifier.standOn(x: Dp, bottom: Dp) = offset(x = x).height(bottom)

/**
 * Комната: фон во весь экран, линия пола на картинке — на [floor]. Порядок — от стены к ребёнку:
 * окно, записка, полка, дверь, качели, миска, посылка, мячик, Финни.
 */
@Composable
private fun Room(
    s: GameState,
    p: RoomPlan,
    floor: Dp,
    height: Dp,
    texts: RoomTexts,
    modifier: Modifier,
    background: Boolean,
    onShop: () -> Unit,
    onPiggy: (() -> Unit)?,
    onParcel: () -> Unit,
    onBowl: () -> Unit,
    onSoap: () -> Unit,
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
        if (p.windowW > 0.dp) {
            Thing("okno", p.windowW, Modifier.offset(x = p.windowX, y = p.windowTop))
        }
        texts.note?.let { title ->
            WallNote(title, p.noteW, p.noteH, Modifier.offset(x = SIDE + 4.dp, y = p.noteTop).clickable(null, null, onClick = onShop))
        }

        // Полка справа, над миской. На ней мыло, пока куплено и не использовано, и копилка.
        val shelfLeft = p.bowlX + BOWL - p.shelfW
        val shelfH = p.shelfW * thingRatio("polka")
        val board = p.shelfTop + shelfH * SHELF_SURFACE
        Thing("polka", p.shelfW, Modifier.offset(x = shelfLeft, y = p.shelfTop))
        val usable = shelfLeft + p.shelfW * 0.1f
        val piggyX = shelfLeft + p.shelfW * 0.9f - p.piggyW
        if (w != null && g.canWash(s)) {
            Box(Modifier.standOn(usable, board)) {
                Appear("soap:${w.number}", Modifier.align(Alignment.BottomStart)) {
                    Box(
                        Modifier.size(maxOf(FinniDimens.MinTouch, p.soapW)).clickable(null, null, onClick = onSoap),
                        contentAlignment = Alignment.BottomCenter,
                    ) { Thing("mylo", p.soapW, description = g.content.item("mylo").name) }
                }
            }
        }
        Box(Modifier.standOn(piggyX, board)) {
            Box(
                Modifier.align(Alignment.BottomStart).anchor(a.flights, "piggy")
                    .then(if (onPiggy != null) Modifier.clickable(null, null, onClick = onPiggy) else Modifier),
            ) {
                // Зона нажатия — не меньше 48 dp, копилка в ней стоит на доске.
                Box(Modifier.sizeIn(minWidth = FinniDimens.MinTouch, minHeight = FinniDimens.MinTouch), contentAlignment = Alignment.BottomCenter) {
                    Thing("kopilka", p.piggyW, description = a.t("piggy.title"))
                }
            }
        }
        // Плашка накоплений — над копилкой, хвостиком к ней: будто сведения исходят от неё.
        texts.saved?.let { saved ->
            val piggyTop = board - p.piggyW * thingRatio("kopilka")
            val right = p.bowlX + BOWL
            Box(Modifier.fillMaxWidth().height(piggyTop - TAIL - 2.dp).padding(end = SIDE), contentAlignment = Alignment.BottomEnd) {
                SavingsPlate(
                    tailFromEnd = right - (piggyX + p.piggyW / 2),
                    maxWidth = p.plateMaxW,
                    modifier = if (onPiggy != null) Modifier.clickable(null, null, onClick = onPiggy) else Modifier,
                ) {
                    // Клетки по 5 монет и «Накопили N», рядом цель с ценой — как было на панели копилки.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            texts.cells?.let { cells ->
                                ProgressCells(minOf(s.progress.savings, texts.price!!) / 5, cells, cell = CELL)
                            }
                            Txt(saved, PlateText)
                        }
                        s.chapter.goalId?.takeIf { texts.price != null }?.let { id ->
                            val goal = g.content.goal(id)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Picture(goal.id, GOAL_PIC, description = goal.name)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Coin(PRICE_COIN)
                                    Txt(goal.price.toString(), PlateText)
                                }
                            }
                        }
                    }
                }
            }
        }

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
                Box(Modifier.clickable(null, null, onClick = onShop)) {
                    Thing("dver", p.doorW, description = a.t("home.shopSign"))
                }
            }
        }

        // Качели и мячик — купленные вещи остаются навсегда. Стоят между дверью и Финни: мячик — на месте
        // посылки (посылки в это время уже нет), качели — у стены, за посылкой и мячиком.
        val slotL = SIDE + p.doorW + 4.dp
        val slotR = p.finniX - 4.dp
        if ("kacheli" in s.progress.inventory) {
            val sw = minOf(slotR - slotL, 84.dp * (p.finniH / FINNI_REF))
            Box(Modifier.standOn(slotL + (slotR - slotL - sw) / 2, floor + DEPTH_SWING)) {
                Appear("kacheli", Modifier.align(Alignment.BottomStart)) {
                    Thing("kacheli", sw, description = g.content.item("kacheli").name)
                }
            }
        }

        // Миска на полу справа стоит всё время; в ней то, что куплено: крупа, каша или каша с ягодами.
        Box(Modifier.standOn(p.bowlX, floor + DEPTH_BOWL)) {
            Box(
                Modifier.align(Alignment.BottomStart).semantics { contentDescription = a.t("a11y.bowl") }
                    .clickable(null, null, onClick = onBowl),
            ) {
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
                    Box(Modifier.anchor(a.flights, "parcel").clickable(null, null, onClick = onParcel)) {
                        Thing("posylka", PARCEL, description = a.t("a11y.parcel"))
                    }
                }
            }
        }
        if ("myachik" in s.progress.inventory) {
            val x = (slotL + slotR) / 2 - FinniDimens.MinTouch / 2
            Box(Modifier.standOn(x, floor + DEPTH_BOWL)) {
                Box(
                    Modifier.align(Alignment.BottomStart)
                        .graphicsLayer { translationY = -ballLift() * 24.dp.toPx() }
                        .clickable(null, null, onClick = onBall),
                ) {
                    Appear("myachik") {
                        Box(Modifier.size(FinniDimens.MinTouch), contentAlignment = Alignment.BottomCenter) {
                            Thing("myachik", 40.dp, description = g.content.item("myachik").name)
                        }
                    }
                }
            }
        }

        // Финни ближе к центру, не мельче 96 dp.
        Box(Modifier.standOn(p.finniX, floor + DEPTH_FINNI)) {
            finni(Modifier.align(Alignment.BottomStart).clickable(null, null, onClick = onFinni))
        }
    }
}

/** Три потребности: миска, мыло, шарф. «Тепло» в главе 1 всегда закрыто (I9). */
@Composable
private fun Needs(fed: Boolean, clean: Boolean) {
    val a = app()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NeedBadge("item_miska", FinniIcons.Fed, a.t("home.state.fed"), fed)
        NeedBadge("item_mylo", FinniIcons.Clean, a.t("home.state.clean"), clean)
        NeedBadge("acc_scarf", FinniIcons.Warm, a.t("home.state.warm"), true)
    }
}

/** Кошелёк плашкой и под ним «Неделя» плашкой поменьше; зона нажатия «Недели» — 48 dp. */
@Composable
private fun Purse(wallet: Int, onWeek: () -> Unit) {
    val a = app()
    Column(horizontalAlignment = Alignment.End) {
        WalletPlate(wallet)
        Box(
            Modifier.heightIn(min = FinniDimens.MinTouch).clickable(remember { MutableInteractionSource() }, null, onClick = onWeek),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.softPlate(FinniDimens.RadiusButton).padding(start = 10.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.EditNote, FinniColors.Ink, 20.dp)
                Txt(a.t("home.week"), PlateText)
            }
        }
    }
}

/**
 * Низ: подсказка и одна кнопка поверх пола. Место подсказки занято всегда, даже когда её нет: так
 * линия пола не прыгает между шагами недели.
 */
@Composable
private fun BottomBlock(showHint: Boolean, button: @Composable () -> Unit) {
    val a = app()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = FinniDimens.ScreenPadding).padding(top = 8.dp, bottom = FinniDimens.BottomGap),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Txt(a.t("home.careHint"), modifier = if (showHint) Modifier else Modifier.alpha(0f).clearAndSetSemantics {})
        Box(Modifier.fillMaxWidth().heightIn(min = FinniDimens.MainButtonHeight)) { button() }
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
 * Плашка снизу: записка бабушки или объявление ситуации. Одна главная кнопка. Первая строка —
 * главная, крупнее; остальные — основным текстом, как в окне §10.8: так видно, с чего начинать
 * читать. Рядом с первой строкой — картинка того, о чём речь. При шрифте ×2,0 плашка прокручивается.
 */
@Composable
private fun BottomPlate(lines: List<String>, picture: String?, ok: String, modifier: Modifier, onOk: () -> Unit) {
    Box(modifier.fillMaxWidth().padding(FinniDimens.ScreenPadding), contentAlignment = Alignment.BottomCenter) {
        val shape = RoundedCornerShape(FinniDimens.RadiusCard)
        Column(
            Modifier.fillMaxWidth().padding(bottom = FinniDimens.BottomGap - FinniDimens.ScreenPadding)
                .background(FinniColors.Surface, shape).border(FinniDimens.Outline, FinniColors.StrokeStrong, shape)
                .verticalScroll(rememberScrollState())
                .padding(FinniDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                picture?.let { Picture(it, 48.dp) }
                lines.firstOrNull()?.let { Txt(it, FinniText.Subtitle, Modifier.weight(1f)) }
            }
            lines.drop(1).forEach { Txt(it) }
            Box(Modifier.height(4.dp))
            MainButton(ok, onOk)
        }
    }
}
