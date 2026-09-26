package ru.vinteno.finni.core.engine

import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.model.Fur
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.PayChoice
import ru.vinteno.finni.core.model.Phase
import ru.vinteno.finni.core.model.Plan
import ru.vinteno.finni.core.model.SummaryChoice

/**
 * Демо для проверки (ТЗ 2.5.13, 2.6; I49 B5, F7, D5) и канонический путь сценариев.
 *
 * Канонический путь: «Нужное» — ровно обязательное недели обычной ступенькой, «Копилка» — 10, остальное
 * — в «Хочу»; надбавка недели берётся, хотелка главы — когда помещается (качели на неделе 2, гирлянда на
 * неделе 5), мячик остаётся в копилке, F2 — ровно, цели — средние. Состояние начала любой из восьми
 * недель получается честным прогоном этих ходов через [Game], а не записанными числами: если правило
 * поменяется, демо поменяется вместе с ним.
 */
class Demo(private val game: Game) {
    /** Цели канонического пути — средние в каждой главе. */
    private val goals = mapOf(1 to "podarok_kniga", 2 to "lezhanka", 3 to "korzina")

    /** Готовый питомец без предыстории: рыжий, шарф, имя «Финни». Цель выбирается, как обычно. */
    fun profile(): GameState {
        var s = GameState(demo = true)
        s = game.seeIntro(s)
        return game.createPet(s, game.content.texts["create.defaultName"], Fur.GINGER, Accessory.SCARF)
    }

    /** Начало недели [n] игры (1–8): посылка ещё не открыта, всё прежнее сыграно по каноническому пути. */
    fun weekStart(n: Int): GameState {
        require(n in 1..WEEKS) { "Недель в игре $WEEKS" }
        var s = game.chooseGoal(profile(), goals.getValue(1))
        while (game.weekNumber(s) < n) s = nextWeekStart(playWeek(s))
        return s
    }

    /** Неделя канонического пути от посылки до итога. */
    fun playWeek(s0: GameState): GameState {
        var s = game.openParcel(s0)
        s = game.seeAnnouncement(s)
        val wc = game.weekContent(s)
        val shelves = game.activeShelves(s)
        fun price(id: String) = game.content.item(id).price
        // Обязательное обычной ступенькой: каша, мыло и вещь недели без надбавки.
        val need = price("kasha") + price("mylo") + (game.situationShelf(s)?.tiers?.first()?.sumOf(::price) ?: 0)
        val save = minOf(10, game.saveCap(s))
        s = game.setPlan(s, Plan(need, s.progress.wallet - need - save, save))
        s = game.confirmPlan(s)

        // Корзина: обязательное с надбавкой недели — верхняя ступенька ситуации.
        val situation = game.situationShelf(s)
        if (situation != null) s = game.chooseSituation(s, situation.tiers.lastIndex)
        val sitShelf = wc.shelves.first { it.id == wc.situationShelf }
        val food = shelves.first { it.id == "food" }.tiers.last { it.first() == "kasha" }
        val soap = shelves.first { it.id == "soap" }.tiers.last()
        val pick = buildList {
            addAll(if (sitShelf.id == "food") food else listOf("kasha"))
            addAll(if (sitShelf.id == "soap") soap else listOf("mylo"))
            addAll(game.situationCart(s))
        }
        if (game.duplicatePending(s)) s = game.resolveDuplicate(s)
        val q = game.quote(s, pick)
        s = game.buy(s, pick, agreedWant = true, agreedSavings = q.asksSavings, pay = if (game.asksPay(s, q)) PayChoice.EXACT else null)
        // Хотелка главы — если помещается в «Хочу».
        val wantId = game.ch(s).chapterWantId
        if (!game.owns(s, wantId) && game.wantLeft(s) >= game.content.item(wantId).price) {
            s = game.buy(s, listOf(wantId))
        }
        s = game.leaveShop(s)
        if (game.canFeed(s)) s = game.feed(s)
        if (game.canWash(s)) s = game.wash(s)
        if (game.canDeposit(s)) s = game.deposit(s)
        if (game.choiceOpen(s)) s = if (game.ballOffer(s).available) game.chooseBall(s, take = false) else game.acknowledgeNoBall(s)
        s = game.leavePiggy(s)
        if (game.sortPending(s)) s = game.finishSort(s)
        return game.finishWeek(s, SummaryChoice.KEEP_PLAN)
    }

    /** После итога: следующая неделя или событие, переход и выбор средней цели новой главы. */
    fun nextWeekStart(s0: GameState): GameState {
        var s = s0
        if (s.phase == Phase.AFTER_SUMMARY) return game.nextWeek(s)
        s = game.playEvent(s)
        if (s.phase != Phase.TRANSITION) return s
        s = game.seeTransition(s)
        return game.chooseGoal(s, goals.getValue(s.progress.chapter))
    }

    companion object {
        /** Канонический путь: 2 + 2 + 4 недели. */
        const val WEEKS = 8
    }
}
