package ru.vinteno.finni.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.core.model.EventOutcome
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.Plan
import ru.vinteno.finni.core.model.SummaryChoice
import ru.vinteno.finni.ui.app
import ru.vinteno.finni.ui.components.Coin
import ru.vinteno.finni.ui.components.CoinRow
import ru.vinteno.finni.ui.components.DirectionNumbers
import ru.vinteno.finni.ui.components.ExplainPlate
import ru.vinteno.finni.ui.components.GameScreen
import ru.vinteno.finni.ui.components.MainButton
import ru.vinteno.finni.ui.components.Picture
import ru.vinteno.finni.ui.components.ProgressCells
import ru.vinteno.finni.ui.components.SecondaryButton
import ru.vinteno.finni.ui.components.Txt
import ru.vinteno.finni.ui.motion.Appear
import ru.vinteno.finni.ui.motion.SlideUp
import ru.vinteno.finni.ui.motion.CoinTarget
import ru.vinteno.finni.ui.motion.anchor
import ru.vinteno.finni.ui.pet.Finni
import ru.vinteno.finni.ui.pet.Reaction
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniText

/** Одна клетка копилки — пять монет (сценарий §5а, гайд §10.9). */
private const val COINS_PER_CELL = 5

/**
 * Копилка — сценарий главы 1, шаг 7. Сумма здесь не выбирается: кнопка откладывает ровно то,
 * что стоит в плане, и при набранной цели тоже — излишек остаётся в копилке (QA-M2). При нуле в
 * плане кнопки нет. Цель не покупается кнопкой: «Подарок готов» ничего не списывает (I4).
 * На неделе 2 после взноса здесь же открывается задание F5 (QA-M3).
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
    val noBall = taskOpen && !g.ballOffer(s).available
    fun leave() { a.act(g::leavePiggy); onBack() }
    // Системное «назад» закрывает шаг так же, как кнопка на экране; задание F5 остаётся ждать.
    BackHandler { leave() }
    GameScreen(
        title = a.t(if (taskMode) "f5.title" else "piggy.title"),
        wallet = s.progress.wallet,
        onBack = ::leave,
        backDescription = a.t("common.back"),
        titleAside = { PetHead(s, live = true) },
        bottom = when {
            ballPlate != null -> ({ MainButton(a.t("common.ok"), onClick = { ballPlate = null }) })
            // В копилке меньше цены мячика: «Понятно» засчитывает задание с наградой (I19).
            noBall -> ({ MainButton(a.t("common.ok"), onClick = { a.act(g::acknowledgeNoBall) }) })
            taskMode || !g.canDeposit(s) -> null
            else -> ({
                val save = s.week!!.plan.save
                MainButton(a.f("piggy.deposit", "n" to save), onClick = {
                    // `доволен` одинаков для любой суммы взноса — разная реакция была бы оценкой.
                    if (a.act(g::deposit)) {
                        a.react(Reaction.HAPPY)
                        // Монеты летят из кошелька в копилку; клетки заполняются по мере прилёта.
                        a.flights.launch("wallet", "piggy", save, CoinTarget.SAVINGS, a.animationOn)
                    }
                })
            })
        },
    ) {
        if (taskOpen) BallChoice(s) { took ->
            if (a.act { g.chooseBall(it, took) }) {
                // Одна и та же реакция при обоих решениях — самое опасное место главы для инварианта 8.
                a.react(Reaction.HAPPY)
                ballPlate = a.explain.afterBall(a.state.value, took)
            }
        }
        ballPlate?.let { lines -> SlideUp(true) { ExplainPlate(lines, onClose = { ballPlate = null }) { PetIcon(s) } } }
        if (taskMode) return@GameScreen
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Picture(goal.id, 96.dp, description = goal.name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Txt(goal.name, FinniText.Subtitle)
                Txt(a.f("piggy.goal", "n" to goal.price))
            }
        }
        // Клетки — во всю ширину экрана: это главный предмет экрана, а не мелкая строка (гайд §10.9).
        val cells = (goal.price + COINS_PER_CELL - 1) / COINS_PER_CELL
        val arrived = (saved - a.flights.savingsPending).coerceAtLeast(0)
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            val gap = 4.dp
            val cell = ((maxWidth - gap * (cells - 1)) / cells).coerceIn(24.dp, 40.dp)
            ProgressCells(minOf(arrived, goal.price) / COINS_PER_CELL, cells, Modifier.anchor(a.flights, "piggy"), cell = cell)
        }
        Txt(a.f(if (reached) "piggy.ready" else "piggy.saved", "n" to saved, "goal" to goal.price), FinniText.Subtitle)
    }
}

/**
 * Итог недели — заявленное ядро, сценарий главы 1, шаг 8, гайд §10.10. Три ряда монет одного
 * масштаба, все золотые, подписи над рядами. Два действия, оба законны, одного вида и размера,
 * порядок не меняется. Анимаций нет вообще, Финни неподвижен.
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

    GameScreen(
        title = a.t("summary.title"),
        wallet = s.progress.wallet,
        onBack = onBack,
        backDescription = a.t("common.back"),
        titleAside = { PetHead(s) },
        bottomInScroll = true,
        bottom = {
            // Под каждой кнопкой — план, с которым откроется следующая неделя: три числа с иконками
            // направлений, без слов. Кнопки одного вида и одной высоты — ни одна не выделена (§10.10).
            val keep = Plan.DEFAULT
            SecondaryButton(a.t("summary.keepPlan"), onClick = { choose(SummaryChoice.KEEP_PLAN) },
                below = { DirectionNumbers(keep.need, keep.want, keep.save) })
            SecondaryButton(a.t("summary.takeActual"), onClick = { choose(SummaryChoice.TAKE_ACTUAL) },
                below = { DirectionNumbers(sum.fact.need, sum.fact.want, sum.fact.save) })
        },
    ) {
        FactRow(a.t("summary.planned"), sum.planned, scale)
        FactRow(a.t("summary.actual"), actual, scale)
        FactRow(a.t("summary.reward"), sum.reward, scale)
        // Объяснение — плашкой §10.11, как везде в игре: откуда разница, словами.
        ExplainPlate(a.explain.summaryLines(s), onClose = null) { PetIcon(s) }
    }
}

@Composable
private fun FactRow(label: String, value: Int, scale: Int) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(label, FinniText.Subtitle, Modifier.weight(1f))
            Txt(value.toString(), FinniText.Subtitle)
        }
        CoinRow(value, scale)
    }
}

/**
 * Событие главы — день рождения Киры, сценарий §8, шаг 9. Играется всегда (I6). Оба исхода
 * наравне: одна реакция Финни, без сравнения. Кнопки «назад» нет: событие не отматывается.
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
    GameScreen(
        title = null,
        wallet = s.progress.wallet,
        onBack = null,
        backDescription = a.t("common.back"),
        bottom = { MainButton(a.t("event.next"), onClick = { a.act(g::playEvent); onDone() }) },
    ) {
        // Все три фигуры в один ряд: на экране 360 × 640 dp строки события помещаются без прокрутки.
        Row(Modifier.fillMaxWidth().wrapContentWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Finni(s.profile.fur, s.profile.accessory, Modifier.width(80.dp),
                reaction = Reaction.HAPPY, reactionKey = 1, animate = a.animationOn, description = s.profile.petName)
            if (given) s.chapter.goalId?.let { id -> Picture(id, 56.dp, description = g.content.goal(id).name) }
            // Кира появляется здесь впервые — по правилу появления §7.1.
            Appear("kira") { Picture("kira", 96.dp, description = a.t("a11y.kira")) }
        }
        lines.forEach { Txt(it, FinniText.Subtitle) }
    }
}

/**
 * Задание F5 — сценарий главы 1, неделя 2, шаг 7 (QA-M3). Последствие показано до выбора, обе кнопки
 * одинаковые: ни одно решение не помечено верным. Мячик платится только из копилки. Вопрос
 * задания — заголовок экрана, в карточке его нет (QA-M9).
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
        val ball = a.game.content.item("myachik")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Picture("myachik", 48.dp, description = ball.name)
            // Что выбирается и сколько стоит — рядом с картинкой, как на карточке товара (§10.6).
            Txt(ball.name, FinniText.Subtitle)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Coin(20.dp)
                Txt(ball.price.toString(), FinniText.Subtitle)
            }
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
