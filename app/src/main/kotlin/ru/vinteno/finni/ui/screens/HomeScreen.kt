package ru.vinteno.finni.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.vinteno.finni.core.content.Impact
import ru.vinteno.finni.core.engine.Step
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.ParcelResult
import ru.vinteno.finni.core.model.Phase
import ru.vinteno.finni.ui.app
import ru.vinteno.finni.ui.components.BackButton
import ru.vinteno.finni.ui.components.Coin
import ru.vinteno.finni.ui.components.ExplainPlate
import ru.vinteno.finni.ui.components.FinniIcons
import ru.vinteno.finni.ui.components.Icon
import ru.vinteno.finni.ui.components.MainButton
import ru.vinteno.finni.ui.components.Picture
import ru.vinteno.finni.ui.components.PressCard
import ru.vinteno.finni.ui.components.ProgressCells
import ru.vinteno.finni.ui.components.Txt
import ru.vinteno.finni.ui.components.Wallet
import ru.vinteno.finni.ui.components.bigFont
import ru.vinteno.finni.ui.motion.Appear
import ru.vinteno.finni.ui.motion.CoinTarget
import ru.vinteno.finni.ui.motion.SlideUp
import ru.vinteno.finni.ui.motion.anchor
import ru.vinteno.finni.ui.pet.Finni
import ru.vinteno.finni.ui.pet.Reaction
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniText

enum class HomeTarget { PLAN, SHOP, PIGGY, SUMMARY, EVENT }

/** Свободная игра без касаний дольше 30 секунд — Финни засыпает (сценарий §7, свободная игра). */
private const val SLEEP_AFTER_MS = 30_000L

/**
 * Дом — сценарий главы 1, §5а. Комната несёт требования ТЗ 2.5.3 сама: питомец, кошелёк,
 * накопления клетками с числом, цель с ценой, три индикатора, записка с заданием.
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
        a.flights.launch("parcel", "wallet", came, CoinTarget.WALLET, s.profile.animationOn)
    }

    /** Кормление: событийное перемещение к миске, до 800 мс, затем `ест` и обратно (animation-howto §6.4). */
    fun feed() {
        if (!a.act(g::feed)) return
        scope.launch {
            if (s.profile.animationOn) walk.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
            a.react(Reaction.EAT)
            delay(520)
            if (s.profile.animationOn) walk.animateTo(0f, tween(800, easing = FastOutSlowInEasing))
        }
    }

    fun wash() {
        if (a.act(g::wash)) a.react(Reaction.HAPPY)
    }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                lastTouch++
            }
        },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = FinniDimens.ScreenPadding)) {
            TopBar(s, onWeek = { if (w?.announcementSeen == true) open(HomeTarget.PLAN) })

            // ---------- Комната ----------
            // При крупном шрифте середина экрана прокручивается: записка встаёт строкой над комнатой,
            // комната получает постоянную высоту и ничто ни на что не наезжает.
            val big = bigFont()
            Column(if (big) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier.weight(1f)) {
            if (big) TaskNote(s, Modifier.fillMaxWidth().padding(top = 8.dp)) { open(HomeTarget.SHOP) }
            BoxWithConstraints((if (big) Modifier.height(320.dp) else Modifier.weight(1f)).fillMaxWidth().padding(top = 8.dp)) {
                val roomW = maxWidth
                val roomH = maxHeight
                // Пол — декоративный разделитель `stroke`, фон комнаты — `bg-sand` главы 1.
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).offset(y = (-6).dp).background(FinniColors.Stroke))

                if (!big) TaskNote(s, Modifier.align(Alignment.TopStart).widthIn(max = 150.dp)) { open(HomeTarget.SHOP) }

                // Дверь с вывеской «Магазин» — вход в покупки после подтверждения плана.
                Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt(a.t("home.shopSign"), FinniText.Caption)
                    Box(Modifier.clickable(remember { MutableInteractionSource() }, null) { if (w?.planConfirmed == true) open(HomeTarget.SHOP) }) {
                        Picture("dver", 96.dp, description = a.t("home.shopSign"))
                    }
                }

                // Посылка у двери, пока не открыта.
                if (w != null && w.parcel == null) {
                    Appear("parcel:${w.number}", Modifier.align(Alignment.BottomEnd).offset(y = (-6).dp)) {
                        Box(Modifier.anchor(a.flights, "parcel").clickable(remember { MutableInteractionSource() }, null) { openParcel() }) {
                            Picture("posylka", 64.dp)
                        }
                    }
                }

                // Качели и мячик — купленные вещи остаются навсегда.
                if ("kacheli" in s.progress.inventory) {
                    Appear("kacheli", Modifier.align(Alignment.CenterEnd).offset(y = 24.dp)) { Picture("kacheli", 72.dp) }
                }
                if ("myachik" in s.progress.inventory) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset(x = (-72).dp, y = (-6).dp)
                            .graphicsLayer { translationY = -ballJump.value * 24.dp.toPx() }
                            .clickable(remember { MutableInteractionSource() }, null) {
                                // Свободная игра: мячик подпрыгивает, Финни — `доволен`. Ничего не даёт.
                                scope.launch { ballJump.animateTo(1f, tween(160)); ballJump.animateTo(0f, tween(160)) }
                                a.react(Reaction.HAPPY)
                            },
                    ) { Appear("myachik") { Picture("myachik", 40.dp) } }
                }

                // Миска на полу; в ней то, что куплено. Ягоды — слоем.
                val bought = w?.purchases.orEmpty()
                val foodIn = bought.any { g.content.item(it).impact == Impact.FED } && w?.fed == false
                Box(
                    Modifier.align(Alignment.BottomStart).offset(x = 8.dp, y = (-6).dp)
                        .semantics { contentDescription = a.t("step.care") }
                        .clickable(remember { MutableInteractionSource() }, null) { if (g.canFeed(s)) feed() },
                ) {
                    // Еда в миске и ягоды слоем появляются по правилу появления, одинаково для любой ступеньки.
                    val bowl = if (!foodIn) "miska" else if ("yagody" in bought) "kasha_yagody" else "kasha"
                    Appear("bowl:$bowl:${w?.number}") { Picture(bowl, 56.dp) }
                }
                // Мыло на полке — пока куплено и не использовано.
                // Полка на стене — обстановка; на ней мыло, пока куплено и не использовано.
                Column(Modifier.align(Alignment.CenterStart).offset(y = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(48.dp).clickable(remember { MutableInteractionSource() }, null) { if (g.canWash(s)) wash() }) {
                        if (w != null && g.canWash(s)) Appear("soap:${w.number}") { Picture("mylo", 48.dp) }
                    }
                    Box(Modifier.width(64.dp).height(6.dp).background(FinniColors.StrokeStrong, RoundedCornerShape(3.dp)))
                }

                // Финни на своём месте, не мельче 96 dp.
                val toBowl = -(roomW / 2 - 72.dp)
                Box(
                    Modifier.align(Alignment.BottomCenter).offset(x = toBowl * walk.value)
                        .clickable(remember { MutableInteractionSource() }, null) { poke++ },
                ) {
                    // Финни помещается в комнату при любом шрифте: ширина — от высоты комнаты (пропорция 0,47).
                    val petW = minOf(120.dp, roomH * 0.44f).coerceAtLeast(FinniDimens.PetFull * 0.47f)
                    Finni(
                        s.profile.fur, s.profile.accessory, Modifier.width(petW),
                        reaction = if (sleeping) Reaction.SLEEP else a.reaction, reactionKey = a.reactionKey,
                        animate = s.profile.animationOn,
                        lookRight = step == Step.PARCEL,
                        earPoke = poke,
                    )
                }
            }

            PiggyPanel(s, onClick = { if (s.chapter.goalId != null) open(HomeTarget.PIGGY) })
            }

            // ---------- Низ: подсказка и одна кнопка ----------
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = FinniDimens.BottomGap), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step == Step.CARE) Txt(a.t("home.careHint"))
                val overlay = parcelNote || step == Step.ANNOUNCE || a.pendingPlate != null
                if (!overlay) when (step) {
                    Step.PARCEL -> MainButton(a.t("step.parcel"), ::openParcel)
                    Step.PLAN -> MainButton(a.t("step.plan"), { open(HomeTarget.PLAN) })
                    Step.SHOP -> MainButton(a.t("step.shop"), { open(HomeTarget.SHOP) })
                    Step.CARE -> MainButton(a.t("step.care"), { if (g.canFeed(s)) feed() else wash() })
                    Step.SAVE -> MainButton(a.t("step.save"), { open(HomeTarget.PIGGY) })
                    Step.SUMMARY -> MainButton(a.t("step.summary"), { open(HomeTarget.SUMMARY) })
                    Step.NEXT_WEEK -> MainButton(a.t("step.nextWeek"), { a.act(g::nextWeek) })
                    else -> {}
                }
            }
        }

        // ---------- Плашки: выезжают снизу, закрываются «Понятно» ----------
        val plateLines: List<String>? = when {
            parcelNote -> parcelLines(s)
            step == Step.ANNOUNCE -> g.weekContent(s).announcement.map { a.f(it, "name" to s.profile.petName) }
            else -> null
        }
        // Плашка выезжает снизу и уезжает обратно (§7.4); пока уезжает, показывает прежние строки.
        var lastPlate by remember { mutableStateOf<List<String>>(emptyList()) }
        if (plateLines != null) lastPlate = plateLines
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            SlideUp(plateLines != null) {
                BottomPlate(lastPlate, a.t("common.ok")) {
                    if (parcelNote) parcelNote = false else a.act(g::seeAnnouncement)
                }
            }
        }
        var lastExplain by remember { mutableStateOf<List<String>>(emptyList()) }
        a.pendingPlate?.let { lastExplain = it }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            SlideUp(plateLines == null && a.pendingPlate != null) {
                Column(Modifier.padding(FinniDimens.ScreenPadding).padding(bottom = FinniDimens.BottomGap - FinniDimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExplainPlate(lastExplain, onClose = { a.pendingPlate = null }) { PetIcon(s) }
                    MainButton(a.t("common.ok"), { a.pendingPlate = null })
                }
            }
        }
    }
}

/** Записка на стене с активным заданием — видна всегда (сценарий §5а). */
@Composable
private fun TaskNote(s: GameState, modifier: Modifier, onOpen: () -> Unit) {
    val a = app()
    val w = s.week ?: return
    val taskId = a.game.weekContent(s).taskId ?: return
    val title = a.t(a.game.content.chapter1.task(taskId).title)
    PressCard(onClick = { if (w.planConfirmed) onOpen() }, modifier) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Picture("zapiska", 32.dp)
            Txt(title, FinniText.Caption)
        }
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

/** Плашка снизу: записка бабушки или объявление ситуации. Одна главная кнопка. */
@Composable
private fun BottomPlate(lines: List<String>, ok: String, onOk: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(FinniDimens.ScreenPadding), contentAlignment = Alignment.BottomCenter) {
        val shape = RoundedCornerShape(FinniDimens.RadiusCard)
        Column(
            Modifier.fillMaxWidth().padding(bottom = FinniDimens.BottomGap - FinniDimens.ScreenPadding)
                .background(FinniColors.Surface, shape).border(FinniDimens.Outline, FinniColors.StrokeStrong, shape)
                .padding(FinniDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            lines.forEach { Txt(it, FinniText.Subtitle) }
            MainButton(ok, onOk)
        }
    }
}

/** Верхняя полоса: три индикатора — иконка, полоса, подпись словом; «Неделя»; кошелёк. Цветом состояние не передаётся. */
@Composable
private fun TopBar(s: GameState, onWeek: () -> Unit) {
    val a = app()
    val w = s.week
    val indicators: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Indicator(FinniIcons.Fed, a.t("home.state.fed"), w?.fed == true)
            Indicator(FinniIcons.Clean, a.t("home.state.clean"), w?.washed == true)
            // «Тепло» в главе 1 всегда полна (I9).
            Indicator(FinniIcons.Warm, a.t("home.state.warm"), true)
        }
    }
    val week: @Composable () -> Unit = {
        PressCard(onWeek, Modifier.padding(horizontal = 8.dp)) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.EditNote, FinniColors.Ink, 28.dp)
                Txt(a.t("home.week"), FinniText.Caption)
            }
        }
    }
    // Первым при крупном шрифте ломается именно эта полоса (test-cases U07): тогда индикаторы — строкой ниже.
    if (bigFont()) {
        Column(Modifier.fillMaxWidth().padding(top = FinniDimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)); week(); Wallet(s.progress.wallet)
            }
            indicators(Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = FinniDimens.ScreenPadding), verticalAlignment = Alignment.Top) {
            indicators(Modifier.weight(1f)); week(); Wallet(s.progress.wallet)
        }
    }
}

@Composable
private fun Indicator(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, full: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, FinniColors.Ink, 20.dp)
        val shape = RoundedCornerShape(4.dp)
        Box(Modifier.width(40.dp).height(10.dp).border(FinniDimens.Outline, FinniColors.StrokeStrong, shape)) {
            if (full) Box(Modifier.fillMaxSize().background(FinniColors.StrokeStrong, shape))
        }
        Txt(label, FinniText.Caption)
    }
}

/** Копилка панелью (решение отложено до прототипа, §5а): клетки по 5 монет, «Накопил N», цель с ценой. */
@Composable
private fun PiggyPanel(s: GameState, onClick: () -> Unit) {
    val a = app()
    val goal = s.chapter.goalId?.let { a.game.content.goal(it) } ?: return
    PressCard(onClick, Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.anchor(a.flights, "piggy")) { Picture("kopilka", 40.dp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val cells = (goal.price + 4) / 5
                ProgressCells(minOf(s.progress.savings, goal.price) / 5, cells, cell = 14.dp)
                Txt(a.f("home.saved", "n" to s.progress.savings), FinniText.Caption)
            }
            Picture(goal.id, 40.dp, description = goal.name)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Coin(16.dp)
                Txt(goal.price.toString(), FinniText.Button)
            }
        }
    }
}
