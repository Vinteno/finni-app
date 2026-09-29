package ru.vinteno.finni.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vinteno.finni.core.content.Content
import ru.vinteno.finni.core.engine.Game
import ru.vinteno.finni.core.engine.requireWeek
import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.model.EventOutcome
import ru.vinteno.finni.core.model.Fur
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.PayChoice
import ru.vinteno.finni.core.model.Phase
import ru.vinteno.finni.core.model.Plan
import ru.vinteno.finni.core.model.SummaryChoice

/**
 * Экономика целиком (I81, аудит ECON-01): стратегии ребёнка проходят всю игру через настоящий [Game], и
 * проверяется неотрицательный баланс и то, что выбор что-то значит — хотелка и цель спорят за одни
 * монеты, расточительный путь не берёт всё, стадия не меняется сама.
 */
class StrategiesTest {
    private val content = Content.fromResources()
    private val game = Game(content)

    enum class Goal { CHEAP, MID, EXPENSIVE }
    enum class Save { PACE, TEN, ZERO, MAX }

    data class Strategy(
        val goal: Goal = Goal.MID,
        val save: Save = Save.PACE,
        /** Еда — крупа, а не каша. */
        val cheapFood: Boolean = false,
        /** Брать надбавку недели, если помещается в «Хочу» (или добирать из копилки при [greedy]). */
        val addons: Boolean = false,
        /** Покупать хотелку главы, когда хватает в «Хочу». */
        val wants: Boolean = false,
        /** Покупать надбавки и хотелки, даже если придётся брать из копилки. */
        val greedy: Boolean = false,
        val ball: Boolean = false,
        /** Ошибочные ветки заданий: F2 из копилки, F3 купить вторую куртку. */
        val wrongTasks: Boolean = false,
        val takeActual: Boolean = false,
    )

    data class Run(
        val weeks: Int,
        val ended: Boolean,
        val goals: List<Boolean>,
        val wants: Int,
        val finalTotal: Int,
        val maxTotal: Int,
        val chapter: Int,
        val stage: Int,
        /** Самое большое число в кошельке или копилке за игру. */
        val maxPocket: Int = 0,
        /** Цена самой дорогой цели, на которую не хватило. */
        val missedPrice: Int = 0,
    )

    private fun price(id: String) = content.item(id).price

    private fun goalId(s: GameState, g: Goal) = game.ch(s).goalIds[g.ordinal]

    private fun pace(s: GameState): Int {
        val c = game.ch(s)
        val w = s.requireWeek()
        val weeksLeft = maxOf(c.minWeeks - w.number + 1, 1)
        val left = game.goalPrice(s) - s.progress.savings
        return ((left + weeksLeft - 1) / weeksLeft).coerceAtLeast(0)
    }

    fun play(st: Strategy, maxWeeks: Int = 20): Run {
        var s = game.createPet(game.seeIntro(GameState()), "", Fur.GINGER, Accessory.SCARF)
        s = game.chooseGoal(s, goalId(s, st.goal))
        val goals = mutableListOf<Boolean>()
        var wants = 0
        var maxTotal = 0
        var maxPocket = 0
        var missed = 0
        var weeks = 0
        while (weeks < maxWeeks) {
            weeks++
            s = game.openParcel(s)
            s = game.seeAnnouncement(s)
            val wc = game.weekContent(s)
            val sit = wc.shelves.first { it.id == wc.situationShelf }
            val food = if (st.cheapFood) "krupa" else "kasha"
            val sitBase = if (sit.onScreen && game.situationShelf(s) != null) sit.tiers.first() else emptyList()
            val need = price(food) + price("mylo") + sitBase.sumOf(::price)
            val wallet = s.progress.wallet
            val free = (wallet - need).coerceAtLeast(0)
            val save = minOf(
                when (st.save) {
                    Save.PACE -> pace(s)
                    Save.TEN -> 10
                    Save.ZERO -> 0
                    Save.MAX -> free
                },
                free, game.saveCap(s),
            )
            s = game.setPlan(s, Plan(minOf(need, wallet), wallet - minOf(need, wallet) - save, save))
            s = game.confirmPlan(s)

            val addon = sit.tiers.last().map(content::item).filter { it.addonOf != null }.sumOf { it.price }
            val takeAddon = st.addons && (st.greedy || game.wantLeft(s) >= addon)
            game.situationShelf(s)?.let { sh -> s = game.chooseSituation(s, if (takeAddon) sh.tiers.lastIndex else 0) }
            val foodTier = if (sit.id == "food" && takeAddon && !st.cheapFood) sit.tiers.last() else listOf(food)
            val soapTier = if (sit.id == "soap" && takeAddon) sit.tiers.last() else listOf("mylo")
            val cart = (foodTier + soapTier + game.situationCart(s)).filter { id ->
                game.activeShelves(s).any { sh -> sh.tiers.flatten().contains(id) }
            }
            if (game.duplicatePending(s)) {
                s = if (st.wrongTasks && game.quoteDuplicate(s).let { it.savingsAfter >= 0 && it.fromWallet <= s.progress.wallet }) {
                    game.buyDuplicate(s, agreedWant = true, agreedSavings = true)
                } else game.resolveDuplicate(s)
            }
            val q = game.quote(s, cart)
            if (q.items.isNotEmpty() && q.savingsAfter >= 0 && q.fromWallet <= s.progress.wallet) {
                val pay = if (game.asksPay(s, q)) {
                    if (st.wrongTasks && game.payFromSavingsAfter(s) >= 0) PayChoice.SAVINGS else PayChoice.NEED
                } else null
                s = game.buy(s, cart, agreedWant = true, agreedSavings = true, pay = pay)
            }
            val wantId = game.ch(s).chapterWantId
            if (st.wants && !game.owns(s, wantId)) {
                val wq = game.quote(s, listOf(wantId))
                val fits = game.wantLeft(s) >= price(wantId)
                if (fits || (st.greedy && wq.savingsAfter >= 0 && wq.fromWallet <= s.progress.wallet)) {
                    s = game.buy(s, listOf(wantId), agreedWant = true, agreedSavings = true)
                    wants++
                }
            }
            s = game.leaveShop(s)
            if (game.canFeed(s)) s = game.feed(s)
            if (game.canWash(s)) s = game.wash(s)
            if (game.canDeposit(s)) s = game.deposit(s)
            if (game.choiceOpen(s)) {
                s = if (game.ballOffer(s).available) game.chooseBall(s, take = st.ball) else game.acknowledgeNoBall(s)
            }
            s = game.leavePiggy(s)
            if (game.sortPending(s)) s = game.finishSort(s)
            maxTotal = maxOf(maxTotal, s.progress.wallet + s.progress.savings)
            maxPocket = maxOf(maxPocket, s.progress.wallet, s.progress.savings)
            val price = game.goalPrice(s)
            s = game.finishWeek(s, if (st.takeActual) SummaryChoice.TAKE_ACTUAL else SummaryChoice.KEEP_PLAN)
            when (s.phase) {
                Phase.AFTER_SUMMARY -> s = game.nextWeek(s)
                Phase.EVENT -> {
                    s = game.playEvent(s)
                    goals += s.eventOutcome == EventOutcome.GIFT_GIVEN
                    if (s.eventOutcome != EventOutcome.GIFT_GIVEN) missed = maxOf(missed, price)
                    maxTotal = maxOf(maxTotal, s.progress.wallet + s.progress.savings)
                    if (s.phase == Phase.GAME_OVER) {
                        return Run(weeks, true, goals, wants, s.progress.wallet + s.progress.savings, maxTotal, 3, s.progress.stage, maxPocket, missed)
                    }
                    s = game.seeTransition(s)
                    s = game.chooseGoal(s, goalId(s, st.goal))
                }
                else -> error("Неожиданная фаза ${s.phase}")
            }
        }
        return Run(weeks, false, goals, wants, s.progress.wallet + s.progress.savings, maxTotal, s.progress.chapter, s.progress.stage, maxPocket, missed)
    }

    private val all: List<Strategy> = buildList {
        for (goal in Goal.entries) for (save in listOf(Save.PACE, Save.TEN, Save.MAX)) for (addons in listOf(false, true))
            for (wants in listOf(false, true)) for (ball in listOf(false, true))
                for (greedy in listOf(false, true)) for (cheapFood in listOf(false, true))
                    add(Strategy(goal, save, cheapFood, addons, wants, greedy, ball))
    }

    @Test fun `награда задания меньше обязательных трат недели`() {
        val minMandatory = price("krupa") + price("mylo")
        content.tasks.values.forEach { assertTrue("${it.id}: ${it.reward}", it.reward < minMandatory) }
    }

    @Test fun `в первую неделю нельзя купить всё сразу и держать темп средней цели`() {
        var s = game.createPet(game.seeIntro(GameState()), "", Fur.GINGER, Accessory.SCARF)
        s = game.chooseGoal(s, "podarok_kniga")
        s = game.openParcel(s)
        val everything = price("kasha") + price("yagody") + price("mylo") + price("kacheli")
        assertTrue("кошелёк ${s.progress.wallet}, всё сразу $everything", everything > s.progress.wallet)
        // Темп средней цели: 40 за две недели с двумя наградами — 15 в неделю.
        assertTrue(price("kasha") + price("yagody") + price("mylo") + pace(game.seeAnnouncement(s)) - 5 > s.progress.wallet)
    }

    @Test fun `каждая стратегия без тупика и без отрицательных денег`() {
        for (st in all + listOf(Strategy(save = Save.ZERO), Strategy(goal = Goal.EXPENSIVE, save = Save.ZERO, wants = true, greedy = true))) {
            val r = play(st)
            assertTrue("$st → $r", r.ended)
            assertTrue("$st → $r", r.maxPocket in 0..305)
            assertTrue("$st → $r", r.weeks in 8..11)
        }
    }

    @Test fun `расточительный путь не берёт все цели и не кончается с крупным остатком`() {
        for (goal in listOf(Goal.MID, Goal.EXPENSIVE)) {
            val r = play(Strategy(goal = goal, save = Save.TEN, addons = true, wants = true, greedy = true, ball = true))
            assertFalse("$goal → $r", r.goals.all { it })
            // Остаток — это копилка, которой не хватило на цель: купить на него пропущенное нельзя.
            assertTrue("$goal → $r", r.finalTotal < r.missedPrice)
            assertEquals("$goal → $r", 1, r.stage)
        }
    }

    @Test fun `хотелка главы сдвигает цель`() {
        // Одинаковый путь к средним целям: с хотелками хоть одна цель не успевает к событию либо нужна
        // запасная неделя.
        val plain = play(Strategy(goal = Goal.MID, save = Save.PACE))
        val withWants = play(Strategy(goal = Goal.MID, save = Save.PACE, wants = true, greedy = true))
        assertTrue(plain.goals.all { it })
        assertEquals(8, plain.weeks)
        assertTrue("$withWants", withWants.goals.any { !it } || withWants.weeks > plain.weeks)
    }

    @Test fun `все дорогие цели и все хотелки вместе не помещаются`() {
        val r = play(Strategy(goal = Goal.EXPENSIVE, save = Save.PACE, wants = true, greedy = true, addons = true))
        assertTrue("$r", r.goals.any { !it } || r.wants < 3)
    }

    @Test fun `канонический путь — восемь недель, три цели, без запасных недель`() {
        val r = play(Strategy(goal = Goal.MID, save = Save.PACE, addons = true))
        assertTrue("$r", r.ended)
        assertEquals("$r", 8, r.weeks)
        assertEquals(listOf(true, true, true), r.goals)
    }

    @Test fun `копилка ноль — сюжет идёт, а Финни не растёт`() {
        val r = play(Strategy(save = Save.ZERO))
        assertTrue(r.ended)
        assertEquals(11, r.weeks)                       // в каждой главе — запасная неделя
        assertEquals(1, r.stage)
    }

    @Test fun `канонический путь — Финни растёт до последней стадии`() {
        assertEquals(Game.MAX_STAGE, play(Strategy(goal = Goal.MID, save = Save.PACE, addons = true)).stage)
    }

    @Test fun `ошибочные ветки заданий не мешают дойти до конца`() {
        val r = play(Strategy(goal = Goal.CHEAP, save = Save.PACE, wrongTasks = true, ball = true))
        assertTrue("$r", r.ended)
    }

    /** Таблица для документации: `./gradlew :core:test --tests '*StrategiesTest.таблица*' -i`. */
    @Test fun `таблица стратегий`() {
        val rows = listOf(
            "канонический: средние цели, надбавки по месту" to Strategy(Goal.MID, Save.PACE, addons = true),
            "средние цели, хотелки по месту" to Strategy(Goal.MID, Save.PACE, wants = true),
            "средние цели, хотелки любой ценой" to Strategy(Goal.MID, Save.PACE, wants = true, greedy = true),
            "дешёвые цели, хотелки и надбавки по месту" to Strategy(Goal.CHEAP, Save.PACE, addons = true, wants = true),
            "дорогие цели, крупа" to Strategy(Goal.EXPENSIVE, Save.PACE, cheapFood = true),
            "дорогие цели, всё верхнее, мячик" to Strategy(Goal.EXPENSIVE, Save.TEN, addons = true, wants = true, greedy = true, ball = true),
            "всё в копилку" to Strategy(Goal.MID, Save.MAX),
            "дешёвые цели, крупа, ни одной хотелки" to Strategy(Goal.CHEAP, Save.PACE, cheapFood = true),
            "ничего не откладывать" to Strategy(Goal.MID, Save.ZERO),
            "ошибочные ветки заданий" to Strategy(Goal.CHEAP, Save.PACE, wrongTasks = true, ball = true),
            "план 10/5/10 без правок" to Strategy(Goal.CHEAP, Save.TEN),
        )
        println("| Путь | Недель | Цели | Хотелок | Стадия | Максимум монет | В конце |")
        println("|---|---|---|---|---|---|---|")
        for ((name, st) in rows) {
            val r = play(st)
            println("| $name | ${r.weeks} | ${r.goals.joinToString(" ") { if (it) "да" else "нет" }} | ${r.wants} | ${r.stage} | ${r.maxTotal} | ${r.finalTotal} |")
        }
    }
}
