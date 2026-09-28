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
 * Канонический путь (I81): «Нужное» — ровно обязательное недели обычной ступенькой; «Копилка» — столько,
 * чтобы к событию хватило на среднюю цель главы (остаток цели за вычетом будущих наград — поровну на
 * оставшиеся недели); остальное — в «Хочу». Надбавка недели берётся, только если помещается в «Хочу»;
 * хотелка главы — тоже. Мячик остаётся в копилке, F2 — из «Нужного», F3 — вторая куртка убрана, F6 —
 * разложено верно. Состояние начала любой из восьми недель получается честным прогоном этих ходов через
 * [Game], а не записанными числами: если правило поменяется, демо поменяется вместе с ним.
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

    /** Сколько откладывать в неделю, чтобы к событию хватило на цель: остаток за вычетом наград — поровну. */
    private fun pace(s: GameState): Int {
        val c = game.ch(s)
        val w = s.requireWeek()
        val weeksLeft = maxOf(c.minWeeks - w.number + 1, 1)
        val rewards = (w.number..maxOf(w.number, c.minWeeks)).sumOf { n ->
            val id = if (n == w.number) w.taskId else c.week(n).taskId
            id?.let(game.content::task)?.takeIf { !c.week(n).spare && it.id !in s.progress.rewardedTasks }?.reward ?: 0
        }
        val left = game.goalPrice(s) - s.progress.savings - rewards
        return ((left + weeksLeft - 1) / weeksLeft).coerceAtLeast(0)
    }

    /** Неделя канонического пути от посылки до итога. */
    fun playWeek(s0: GameState): GameState {
        var s = game.openParcel(s0)
        s = game.seeAnnouncement(s)
        val wc = game.weekContent(s)
        fun price(id: String) = game.content.item(id).price
        val sit = wc.shelves.first { it.id == wc.situationShelf }
        val sitBase = if (sit.onScreen) sit.tiers.first() else emptyList()
        // Обязательное обычной ступенькой: каша, мыло и вещь недели без надбавки.
        val need = price("kasha") + price("mylo") + sitBase.sumOf(::price)
        val wallet = s.progress.wallet
        val save = minOf(pace(s), wallet - need, game.saveCap(s)).coerceAtLeast(0)
        val want = wallet - need - save
        s = game.setPlan(s, Plan(need, want, save))
        s = game.confirmPlan(s)

        // Надбавка недели — верхняя ступенька полки ситуации, если помещается в «Хочу».
        val addon = sit.tiers.last().map(game.content::item).filter { it.addonOf != null }.sumOf { it.price }
        val withAddon = want >= addon
        val situation = game.situationShelf(s)
        if (situation != null) s = game.chooseSituation(s, if (withAddon) situation.tiers.lastIndex else 0)
        val pick = buildList {
            addAll(if (sit.id == "food" && withAddon) sit.tiers.last() else listOf("kasha"))
            addAll(if (sit.id == "soap" && withAddon) sit.tiers.last() else listOf("mylo"))
            addAll(game.situationCart(s))
        }
        if (game.duplicatePending(s)) s = game.resolveDuplicate(s)
        val q = game.quote(s, pick)
        s = game.buy(s, pick, agreedWant = q.asksWant, agreedSavings = q.asksSavings, pay = if (game.asksPay(s, q)) PayChoice.NEED else null)
        // Хотелка главы — если помещается в «Хочу».
        val wantId = game.ch(s).chapterWantId
        if (!game.owns(s, wantId) && game.wantLeft(s) >= price(wantId)) {
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
