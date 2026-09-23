package ru.vinteno.finni.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.core.model.EventOutcome
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.SummaryChoice
import ru.vinteno.finni.ui.app
import ru.vinteno.finni.ui.components.CoinRow
import ru.vinteno.finni.ui.components.GameScreen
import ru.vinteno.finni.ui.components.MainButton
import ru.vinteno.finni.ui.components.Picture
import ru.vinteno.finni.ui.components.ProgressCells
import ru.vinteno.finni.ui.components.SecondaryButton
import ru.vinteno.finni.ui.components.Txt
import ru.vinteno.finni.ui.pet.Finni
import ru.vinteno.finni.ui.pet.Reaction
import ru.vinteno.finni.ui.theme.FinniText

/** Одна клетка копилки — пять монет (сценарий §5а, гайд §10.9). */
private const val COINS_PER_CELL = 5

/**
 * Копилка — сценарий главы 1, шаг 7. Сумма здесь не выбирается: кнопка откладывает ровно то,
 * что стоит в плане. При нуле в плане и при закрытой цели кнопки нет. Цель не покупается
 * кнопкой: «Подарок готов» ничего не списывает (I4).
 */
@Composable
fun PiggyScreen(s: GameState, onBack: () -> Unit) {
    val a = app()
    val g = a.game
    val goal = g.content.goal(s.chapter.goalId ?: return)
    val saved = s.progress.savings
    val reached = g.goalReached(s)
    GameScreen(
        title = a.t("piggy.title"),
        wallet = s.progress.wallet,
        onBack = { a.act(g::leavePiggy); onBack() },
        backDescription = a.t("common.back"),
        titleAside = { PetHead(s, live = true) },
        bottom = if (!g.canDeposit(s)) null else ({
            MainButton(a.f("piggy.deposit", "n" to s.week!!.plan.save), onClick = {
                // `доволен` одинаков для любой суммы взноса — разная реакция была бы оценкой.
                if (a.act(g::deposit)) a.react(Reaction.HAPPY)
            })
        }),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Picture(goal.id, 96.dp, description = goal.name)
            Column(Modifier.weight(1f)) {
                Txt(goal.name, FinniText.Subtitle)
                Txt(a.f("piggy.goal", "n" to goal.price))
            }
        }
        val cells = (goal.price + COINS_PER_CELL - 1) / COINS_PER_CELL
        ProgressCells(minOf(saved, goal.price) / COINS_PER_CELL, cells, cell = 24.dp)
        Txt(if (reached) a.f("piggy.ready", "n" to saved) else a.f("piggy.saved", "n" to saved), FinniText.Subtitle)
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
        bottom = {
            SecondaryButton(a.t("summary.keepPlan"), onClick = { choose(SummaryChoice.KEEP_PLAN) })
            // Три числа по направлениям без подписей: порядок всегда Нужное, Хочу, Копилка.
            SecondaryButton(
                a.t("summary.takeActual"),
                onClick = { choose(SummaryChoice.TAKE_ACTUAL) },
                below = "${sum.fact.need}   ${sum.fact.want}   ${sum.fact.save}",
            )
        },
    ) {
        FactRow(a.t("summary.planned"), sum.planned, scale)
        FactRow(a.t("summary.actual"), actual, scale)
        FactRow(a.t("summary.reward"), sum.reward, scale)
        a.explain.summaryLines(s).forEach { Txt(it) }
    }
}

@Composable
private fun FactRow(label: String, value: Int, scale: Int) {
    Column(Modifier.fillMaxWidth()) {
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
        listOf(a.t("event.a.2"), a.t("event.a.3"))
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
        Row(Modifier.fillMaxWidth().wrapContentWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Finni(s.profile.fur, s.profile.accessory, Modifier.width(110.dp),
                reaction = Reaction.HAPPY, reactionKey = 1, animate = s.profile.animationOn)
            Box { Picture("kira", 120.dp) }
        }
        if (given) Picture(s.chapter.goalId ?: "", 72.dp)
        lines.forEach { Txt(it, FinniText.Subtitle) }
    }
}
