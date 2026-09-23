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
        assertEquals("Ты купил кашу и мыло.", explain.did(listOf("kasha", "yagody", "mylo")))
        s = game.deposit(game.leaveShop(s))
        assertEquals(listOf("Ты взял кашу с ягодами.", "1 монета — из «Хочу»."), explain.summaryLines(s))
    }

    @Test fun `пустая корзина и ничего не купленное`() {
        val s = game.leaveShop(week1("Бублик"))
        assertEquals("Ты ничего не купил.", explain.did(emptyList()))
        assertEquals(listOf("Бублик не поел на неделе."), explain.summaryLines(s))
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
        s = game.confirmPlan(game.seeAnnouncement(game.openParcel(game.nextWeek(s))))
        val take = explain.afterBall(game.chooseBall(s, true), took = true)
        val keep = explain.afterBall(game.chooseBall(s, false), took = false)
        assertEquals(3, take.size); assertEquals(3, keep.size)
        assertEquals(listOf("Ты взял мячик.", "В копилке 15 монет.", "На подарок не хватит."), take)
        assertEquals(listOf("Ты оставил монеты в копилке.", "В копилке 30 монет.", "На подарок хватит."), keep)
        (take + keep).forEach { assertTrue(it, words(it) <= 5) }
    }

    @Test fun `формы монеты в падежах`() {
        val t = content.texts
        assertEquals("Разложил 31 из 30. Убери 1 монету.", t.format("plan.over", "sum" to 31, "wallet" to 30, "n" to 1))
        assertEquals("Не хватает 1 монеты.", t.format("shortfall.want.1", "n" to 1))
        assertEquals("Не хватает 4 монеты.", t.format("shortfall.want.1", "n" to 4))
        assertEquals("На нужное — 10 монет.", t.format("shop.hint", "n" to 10))
    }

    @Test fun `F5 при копилке меньше 15 засчитывается с наградой`() {
        var s = game.createPet(game.seeIntro(GameState()), "", Fur.GINGER, Accessory.CAP)
        s = game.chooseGoal(s, "podarok_myach")
        s = game.setPlan(game.seeAnnouncement(game.openParcel(s)), Plan(10, 20, 0))
        s = game.finishWeek(game.leaveShop(game.confirmPlan(s)), SummaryChoice.KEEP_PLAN)
        s = game.confirmPlan(game.seeAnnouncement(game.openParcel(game.nextWeek(s))))
        assertTrue(!game.ballOffer(s).available)
        s = game.leaveShop(s)
        assertEquals(20, s.progress.savings)
        assertEquals(10, s.week!!.taskReward)
    }
}
