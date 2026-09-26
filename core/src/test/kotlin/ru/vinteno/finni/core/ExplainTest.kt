package ru.vinteno.finni.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vinteno.finni.core.content.Content
import ru.vinteno.finni.core.engine.Explain
import ru.vinteno.finni.core.engine.Game
import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.model.Fur
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.Plan
import ru.vinteno.finni.core.model.SummaryChoice

/** Собранные из состояния строки: дословные примеры сценария и норма 5 слов на любом наборе. */
class ExplainTest {
    private val content = Content.fromResources()
    private val game = Game(content)
    private val explain = Explain(game)

    private fun week1(name: String = ""): GameState {
        var s = game.createPet(game.seeIntro(GameState()), name, Fur.BLUE, Accessory.BOW)
        s = game.chooseGoal(s, "podarok_kniga")
        s = game.seeAnnouncement(game.openParcel(s))
        return game.confirmPlan(s)
    }

    private fun words(p: String) = p.split(Regex("\\s+")).count { w -> w.any { it.isLetterOrDigit() } }

    @Test fun `канонический путь — строки сценария`() {
        var s = game.buy(week1(), listOf("kasha", "yagody", "mylo"), agreedWant = true)
        assertEquals("Мы купили кашу и мыло.", explain.did(listOf("kasha", "yagody", "mylo")))
        s = game.deposit(game.leaveShop(s))
        // Последняя строка объясняет разницу 30 и 21 (решение Эмиля 24.09). Надбавка платится из «Хочу»,
        // поэтому строки перелива из «Хочу» в «Нужное» на каноническом пути больше нет.
        assertEquals(listOf("Мы взяли кашу с ягодами.", "У тебя 9 монет."), explain.summaryLines(s))
    }

    @Test fun `третья строка после магазина — есть ли всё нужное на неделю`() {
        var s = game.buy(week1(), listOf("kasha", "mylo"))
        assertEquals("Всё нужное на неделю есть.", explain.afterShop(s, listOf("kasha", "mylo"))[2])
        s = game.buy(week1(), listOf("krupa"))
        assertEquals("Мыла на неделю пока нет.", explain.afterShop(s, listOf("krupa"))[2])
        s = game.buy(week1(), listOf("mylo"))
        assertEquals("Еды на неделю пока нет.", explain.afterShop(s, listOf("mylo"))[2])
        assertEquals("Еды и мыла пока нет.", explain.afterShop(week1(), emptyList())[2])
    }

    @Test fun `пустая корзина и ничего не купленное`() {
        val s = game.leaveShop(week1("Бублик"))
        assertEquals("Мы ничего не купили.", explain.did(emptyList()))
        assertEquals(listOf("Я неделю без еды.", "У тебя 30 монет."), explain.summaryLines(s))
    }

    @Test fun `любой набор корзины — фраза не длиннее 5 слов`() {
        val shelves = content.chapter1.weeks.flatMap { it.shelves }
        val options = shelves.groupBy { it.id }.values.map { same -> listOf(emptyList<String>()) + same.flatMap { it.tiers } }
        val carts = options.fold(listOf(emptyList<String>())) { acc, opts -> acc.flatMap { c -> opts.map { c + it } } }
            .flatMap { listOf(it, it + "kacheli") }
        carts.forEach { cart ->
            val p = explain.did(cart)
            assertTrue("$cart → «$p»", words(p) <= 5)
        }
    }

    @Test fun `объяснение F5 одинаковой структуры при обоих решениях`() {
        var s = game.buy(week1(), listOf("kasha", "yagody", "mylo"), agreedWant = true)
        s = game.finishWeek(game.deposit(game.leaveShop(s)), SummaryChoice.KEEP_PLAN)
        s = game.deposit(game.confirmPlan(game.seeAnnouncement(game.openParcel(game.nextWeek(s)))))
        val take = explain.afterBall(game.chooseBall(s, true), took = true)
        val keep = explain.afterBall(game.chooseBall(s, false), took = false)
        assertEquals(3, take.size); assertEquals(3, keep.size)
        assertEquals(listOf("Мы взяли мячик.", "В копилке 25 монет.", "На подарок не хватит."), take)
        assertEquals(listOf("Мы оставили монеты в копилке.", "В копилке 40 монет.", "На подарок хватит."), keep)
        (take + keep).forEach { assertTrue(it, words(it) <= 5) }
    }

    @Test fun `формы монеты в падежах`() {
        val t = content.texts
        assertEquals("Разложено 31 из 30. Убери 1 монету.", t.format("plan.over", "sum" to 31, "wallet" to 30, "n" to 1))
        assertEquals("Не хватает: 1 монета.", t.format("shortfall.want.1", "n" to 1))
        assertEquals("Не хватает: 4 монеты.", t.format("shortfall.want.1", "n" to 4))
        assertEquals("На нужное: 10 монет.", t.format("shop.hint", "n" to 10))
    }

    @Test fun `F5 при копилке меньше 15 засчитывается с наградой`() {
        var s = game.createPet(game.seeIntro(GameState()), "", Fur.GINGER, Accessory.CAP)
        s = game.chooseGoal(s, "podarok_myach")
        s = game.setPlan(game.seeAnnouncement(game.openParcel(s)), Plan(10, 20, 0))
        s = game.finishWeek(game.leaveShop(game.confirmPlan(s)), SummaryChoice.KEEP_PLAN)
        s = game.confirmPlan(game.setPlan(game.seeAnnouncement(game.openParcel(game.nextWeek(s))), Plan(10, 20, 0)))
        assertTrue(!game.ballOffer(s).available)
        s = game.acknowledgeNoBall(s)
        assertEquals(20, s.progress.savings)
        assertEquals(10, s.week!!.taskReward)
    }

    @Test fun `QA-M7 остаток называет направление`() {
        var s = week1()
        s = game.buy(s, listOf("kasha", "mylo"))
        assertEquals("В «Нужном» осталось 2.", explain.afterShop(s, listOf("kasha", "mylo"))[1])
        s = game.setPlan(game.seeAnnouncement(game.openParcel(game.chooseGoal(
            game.createPet(game.seeIntro(GameState()), "", Fur.GINGER, Accessory.CAP), "podarok_myach"))), Plan(5, 20, 5))
        s = game.buy(game.confirmPlan(s), listOf("kacheli"))
        assertEquals("В «Хочу» осталось 5.", explain.afterShop(s, listOf("kacheli"))[1])
        assertEquals("В «Нужном» осталось 5.", explain.afterShop(s, emptyList())[1])
    }

    /** QA-M5: на любом итоге любой партии экран не длиннее 25 слов, а первая строка есть всегда. */
    @Test fun `итог при любом пути укладывается в 25 слов`() {
        val t = content.texts
        val fixed = listOf("summary.title", "summary.planned", "summary.actual", "summary.reward", "summary.keepPlan", "summary.takeActual")
            .sumOf { t.screenWords(t[it]) }
        val rnd = kotlin.random.Random(24)
        var checked = 0
        repeat(1500) {
            var s = game.chooseGoal(game.createPet(game.seeIntro(GameState()), "Бублик", Fur.BROWN, Accessory.BOW),
                content.chapter1.goalIds.random(rnd))
            for (week in 1..2) {
                s = game.seeAnnouncement(game.openParcel(s))
                val p = Plan(rnd.nextInt(0, 16), rnd.nextInt(0, 16), rnd.nextInt(0, 16))
                s = game.confirmPlan(if (p.total <= s.progress.wallet) game.setPlan(s, p) else s)
                val shelves = game.weekContent(s).shelves
                val cart = shelves.filter { rnd.nextBoolean() }.flatMap { it.tiers.random(rnd) } +
                    (if (rnd.nextBoolean()) listOf("kacheli") else emptyList())
                runCatching { s = game.buy(s, cart, agreedWant = true, agreedSavings = true) }
                s = game.leaveShop(s)
                if (rnd.nextBoolean() && game.canDeposit(s)) s = game.deposit(s)
                if (rnd.nextBoolean()) runCatching { s = game.chooseBall(s, rnd.nextBoolean()) }
                val lines = explain.summaryLines(s)
                val total = fixed + lines.sumOf(t::screenWords)
                assertTrue("$total слов: $lines", total <= 25)
                assertTrue(lines.isNotEmpty())
                lines.forEach { l -> assertTrue(l, words(l) <= 5) }
                checked++
                s = game.finishWeek(s, SummaryChoice.KEEP_PLAN)
                if (week == 1) s = game.nextWeek(s)
            }
        }
        assertEquals(3000, checked)
    }
}
