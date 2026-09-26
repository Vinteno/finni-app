package ru.vinteno.finni.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vinteno.finni.core.content.Content
import ru.vinteno.finni.core.engine.Demo
import ru.vinteno.finni.core.engine.Direction
import ru.vinteno.finni.core.engine.Explain
import ru.vinteno.finni.core.engine.Game
import ru.vinteno.finni.core.engine.IllegalMove
import ru.vinteno.finni.core.engine.Step
import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.model.EventOutcome
import ru.vinteno.finni.core.model.Fur
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.PayChoice
import ru.vinteno.finni.core.model.Phase
import ru.vinteno.finni.core.model.Plan
import ru.vinteno.finni.core.model.Reason
import ru.vinteno.finni.core.model.SummaryChoice

/**
 * Главы 2 и 3 — final-plan.md, блок A, п. 10. Канонический путь восьми недель пересчитан по правилу
 * I48 п. 2 (надбавка платится из «Хочу»): цены те же, меняется только, из какого направления уплачено,
 * и что взнос недели 8 исполняется по плану (I32 во всех главах, I49 A5).
 */
class ChaptersTest {
    private val content = Content.fromResources()
    private val game = Game(content)
    private val demo = Demo(game)
    private val explain = Explain(game)

    private fun assertThrows(block: () -> Unit) {
        try { block() } catch (e: IllegalMove) { return }
        throw AssertionError("Ход должен быть запрещён")
    }

    private fun fresh(goal: String = "podarok_kniga"): GameState {
        var s = game.seeIntro(GameState())
        s = game.createPet(s, "Бублик", Fur.BLUE, Accessory.CAP)
        return game.chooseGoal(s, goal)
    }

    /** Посылка, объявление и весь кошелёк: «Нужное» [need], «Копилка» [save], остальное — в «Хочу». */
    private fun plan(s0: GameState, need: Int, save: Int): GameState {
        var s = game.seeAnnouncement(game.openParcel(s0))
        s = game.setPlan(s, Plan(need, s.progress.wallet - need - save, save))
        return game.confirmPlan(s)
    }

    /** Ничего не купить: зайти в магазин и выйти через копилку — шаг пройден (I45). */
    private fun skipWeek(s: GameState, save: Int = 0, choice: SummaryChoice = SummaryChoice.KEEP_PLAN): GameState {
        var x = plan(s, 0, save)
        x = game.leaveShop(x)
        if (game.canDeposit(x)) x = game.deposit(x)
        if (game.choiceOpen(x)) x = if (game.ballOffer(x).available) game.chooseBall(x, false) else game.acknowledgeNoBall(x)
        x = game.leavePiggy(x)
        if (game.sortPending(x)) x = game.finishSort(x)
        return game.finishWeek(x, choice)
    }

    // ---------- Канонический путь ----------

    @Test fun `канонический путь восьми недель — кошелёк и копилка на конец каждой недели`() {
        // Неделя: кошелёк и копилка на конце, после события главы — копилка после списания цели.
        val expected = listOf(9 to 20, 4 to 40, 6 to 20, 12 to 30, 1 to 20, 4 to 30, 8 to 50, 12 to 60)
        var s = demo.weekStart(1)
        expected.forEachIndexed { i, (wallet, savings) ->
            assertEquals("номер недели", i + 1, game.weekNumber(s))
            s = demo.playWeek(s)
            assertEquals("кошелёк на конец недели ${i + 1}", wallet, s.progress.wallet)
            assertEquals("копилка на конец недели ${i + 1}", savings, s.progress.savings)
            if (i < expected.lastIndex) s = demo.nextWeekStart(s)
        }
        assertEquals(Phase.EVENT, s.phase)
        s = game.playEvent(s)
        assertEquals(EventOutcome.GIFT_GIVEN, s.eventOutcome)
        assertEquals(10, s.progress.savings) // корзина 50 из 60
        assertEquals(Phase.GAME_OVER, s.phase)
        assertTrue("korzina" in s.progress.inventory)
    }

    @Test fun `канонический путь — главы меняются после недель 2 и 4 по отметкам, строка причины считается`() {
        var s = demo.playWeek(demo.weekStart(2))
        assertEquals(Phase.EVENT, s.phase)
        assertEquals(listOf(2, 2, 2), listOf(s.progress.marksCare, s.progress.marksPlan, s.progress.marksSave))
        // Порог замкнули все три группы разом — берётся забота; недели заботы в главе — две.
        assertEquals(Reason.CARE, s.transition!!.reason)
        s = game.playEvent(s)
        assertEquals(Phase.TRANSITION, s.phase)
        assertEquals(2, s.progress.chapter)
        assertEquals(listOf("Мы ели две недели", "Похолодало", "Мне зябко"), explain.transitionLines(s))
        s = game.seeTransition(s)
        assertEquals(Phase.ONBOARDING, s.phase)
        assertNull(s.chapter.goalId)
        assertThrows { game.chooseGoal(s, "podarok_kniga") } // цели прошлой главы не предлагаются
        s = game.chooseGoal(s, "lezhanka")
        assertEquals(3, game.weekNumber(s))
        assertEquals(1, s.week!!.number) // недели считаются внутри главы

        s = demo.playWeek(demo.weekStart(4))
        assertEquals(Phase.EVENT, s.phase)
        assertEquals(4, s.progress.marksSave)
        s = game.playEvent(s)
        assertEquals(3, s.progress.chapter)
        assertEquals(listOf("Мы ели две недели", "Мы переехали", "Дом больше прежнего"), explain.transitionLines(s))
        assertTrue("lezhanka" in s.progress.inventory) // мебель главы 2 остаётся в комнате
    }

    @Test fun `глава 3 всегда четыре недели, даже при всех отметках`() {
        var s = demo.weekStart(5)
        repeat(3) {
            s = demo.playWeek(s)
            assertEquals(Phase.AFTER_SUMMARY, s.phase)
            s = demo.nextWeekStart(s)
        }
        s = demo.playWeek(s)
        assertEquals(Phase.EVENT, s.phase)
        assertEquals(8, game.weekNumber(s))
    }

    @Test fun `демо — начало любой недели с состоянием канонического пути`() {
        val starts = (1..8).map { demo.weekStart(it) }
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 3, 3), starts.map { it.progress.chapter })
        assertEquals(listOf(1, 2, 1, 2, 1, 2, 3, 4), starts.map { it.week!!.number })
        assertEquals(listOf(0, 9, 4, 6, 12, 1, 4, 8), starts.map { it.progress.wallet })
        assertEquals(listOf(0, 20, 0, 20, 0, 20, 30, 50), starts.map { it.progress.savings })
        assertEquals(listOf("F1", "F5", "F4", "F3", "F2", null, "F6", null), starts.map { game.weekTask(it)?.id })
        assertTrue(starts.all { it.demo && it.week!!.parcel == null })
        assertTrue("kurtka" in starts[3].progress.inventory && "risunok" in starts[3].progress.inventory)
        assertTrue("girlyanda" in starts[5].progress.inventory && "korobka" in starts[5].progress.inventory)
        assertEquals("korzina", starts[7].chapter.goalId)
    }

    // ---------- Длина главы и запасная неделя ----------

    @Test fun `запасная неделя главы 1 — по недостающей заботе, задание F1 без награды`() {
        var s = fresh()
        s = skipWeek(s, save = 10)
        s = skipWeek(game.nextWeek(s), save = 10)
        assertEquals(Phase.AFTER_SUMMARY, s.phase) // заботы 0 из 2 — глава не кончилась
        s = game.nextWeek(s)
        assertEquals(3, s.week!!.number)
        assertEquals("F1", game.weekTask(s)!!.id)
        assertTrue(game.weekContent(s).spare)
        s = plan(s, 8, 10)
        val before = s.progress.savings
        s = game.buy(s, listOf("krupa", "mylo"))
        assertTrue(s.week!!.taskDone)
        assertEquals(0, s.week!!.taskReward)                 // награды нет — §9а
        assertEquals(before, s.progress.savings)
        s = game.deposit(game.leaveShop(s))
        s = game.finishWeek(game.leavePiggy(s), SummaryChoice.KEEP_PLAN)
        assertEquals(Phase.EVENT, s.phase)                    // после третьей — конец в любом случае
        // Порог не замкнут ничем: строка — о том, что делали чаще. Откладывали и смотрели три недели.
        assertEquals(Reason.SAVE, s.transition!!.reason)
        assertEquals(3, s.transition!!.weeks)
        assertEquals("Мы откладывали три недели", explain.transitionLines(game.playEvent(s)).first())
    }

    @Test fun `задание запасной недели — по типу, которого меньше, поровну — забота, накопления, план`() {
        var s = fresh()
        // Еда и мыло куплены, взноса нет: не хватает накоплений — F4.
        repeat(2) { i ->
            if (i > 0) s = game.nextWeek(s)
            s = plan(s, 8, 0)
            s = game.buy(s, listOf("krupa", "mylo"))
            s = game.leaveShop(s)
            if (game.choiceOpen(s)) s = game.acknowledgeNoBall(s)
            s = game.finishWeek(game.leavePiggy(s), SummaryChoice.KEEP_PLAN)
        }
        s = game.nextWeek(s)
        assertEquals("F4", game.weekTask(s)!!.id)
        // Ничего не куплено и не отложено: заботы и накоплений поровну — забота.
        var t = skipWeek(fresh())
        t = skipWeek(game.nextWeek(t))
        assertEquals("F1", game.weekTask(game.nextWeek(t))!!.id)
    }

    @Test fun `путь «ничего не покупаю» — игра всё равно кончается, 3 + 3 + 4 недели`() {
        var s = fresh("podarok_samokat")
        var weeks = 0
        while (s.phase != Phase.GAME_OVER) {
            s = when (s.phase) {
                Phase.WEEK -> { weeks++; skipWeek(s) }
                Phase.AFTER_SUMMARY -> game.nextWeek(s)
                Phase.EVENT -> game.playEvent(s)
                Phase.TRANSITION -> game.seeTransition(s)
                Phase.ONBOARDING -> game.chooseGoal(s, game.ch(s).goalIds.last())
                else -> error(s.phase)
            }
            assertTrue(weeks <= 10)
        }
        assertEquals(10, weeks)
        assertEquals(EventOutcome.NOT_ENOUGH, s.eventOutcome)
        assertTrue(game.cold(s).not()) // в главе 3 не зябнут
    }

    @Test fun `путь «всё в копилку» — копилка не выше 100 ни на одной неделе`() {
        var s = fresh("podarok_samokat")
        var max = 0
        while (s.phase != Phase.GAME_OVER) {
            s = when (s.phase) {
                Phase.WEEK -> skipWeek(s, save = minOf(game.saveCap(game.seeAnnouncement(game.openParcel(s))), s.progress.wallet + 30))
                Phase.AFTER_SUMMARY -> game.nextWeek(s)
                Phase.EVENT -> game.playEvent(s)
                Phase.TRANSITION -> game.seeTransition(s)
                Phase.ONBOARDING -> game.chooseGoal(s, game.ch(s).goalIds.first())
                else -> error(s.phase)
            }
            max = maxOf(max, s.progress.savings, s.progress.wallet)
        }
        assertTrue("до $max", max <= 100)
    }

    // ---------- Задания глав 2 и 3 ----------

    @Test fun `F4 на экране плана — награда 10 в копилку при подтверждении, объяснение из трёх строк`() {
        var s = game.seeAnnouncement(game.openParcel(demo.weekStart(3)))
        assertTrue(game.planTask(s))
        s = game.setPlan(s, Plan(14, 10, 10))
        s = game.confirmPlan(s)
        assertEquals(10, s.progress.savings)
        assertTrue(s.week!!.taskDone)
        assertEquals(listOf("Откладываем 10 монет", "К снегу будет 30", "Хватит ровно"), explain.afterPlanTask(s))
    }

    @Test fun `F3 — дубль куртки не списывается ни при каком выборе, награды нет`() {
        var s = plan(demo.weekStart(4), 12, 10)
        assertTrue(game.duplicatePending(s))
        val q = game.quote(s, listOf("kurtka", "kasha"))
        assertEquals(listOf("kasha"), q.items.map { it.id }) // вторая куртка не считается
        val wallet = s.progress.wallet
        val savings = s.progress.savings
        s = game.resolveDuplicate(s)
        assertEquals(wallet, s.progress.wallet)
        assertEquals(savings, s.progress.savings)
        assertEquals(0, s.week!!.taskReward)
        assertTrue("F3" in s.progress.doneTasks)
        assertFalse("F3" in s.progress.rewardedTasks)
        assertThrows { game.resolveDuplicate(s) }
    }

    @Test fun `F3 без купленной куртки не играется`() {
        var s = demo.weekStart(3)
        s = skipWeek(s, save = 10)
        s = game.nextWeek(s)
        assertNull(game.weekTask(plan(s, 12, 10)))
    }

    @Test fun `F2 — способ оплаты обязателен, оба равны, в кошельке чистая стоимость`() {
        var s = plan(demo.weekStart(5), 13, 10)
        s = game.chooseSituation(s, 0)
        val cart = listOf("kasha", "mylo") + game.situationCart(s)
        assertThrows { game.buy(s, cart) }
        val wallet = s.progress.wallet
        val a = game.buy(s, cart, pay = PayChoice.EXACT)
        val b = game.buy(s, cart, pay = PayChoice.CHANGE)
        assertEquals(wallet - 13, a.progress.wallet)
        assertEquals(a.progress.wallet, b.progress.wallet)
        assertEquals(a.progress.savings, b.progress.savings)
        assertEquals(10, a.week!!.taskReward)
        assertEquals(listOf("Мы дали 10 монет", "Нам вернули 5", "Коробка стоит 5"), explain.afterPay(b, exact = false))
        assertEquals(listOf("Мы дали 5 монет", "Сдачи нет", "Коробка стоит 5"), explain.afterPay(a, exact = true))
    }

    @Test fun `F6 — траты карточками по направлениям, итог открыт после раскладки`() {
        var s = plan(demo.weekStart(7), 13, 10)
        s = game.chooseSituation(s, 1)
        s = game.buy(s, listOf("kasha", "mylo") + game.situationCart(s), agreedWant = true)
        s = game.deposit(game.leaveShop(s))
        s = game.leavePiggy(game.wash(game.feed(s)))
        assertEquals(Step.SORT, game.nextStep(s))
        assertFalse(game.summaryOpen(s))
        val cards = game.sortCards(s)
        assertEquals(listOf("kasha", "mylo", "ugoshchenie", "glazur", Game.DEPOSIT), cards.map { it.id })
        assertEquals(listOf(Direction.NEED, Direction.NEED, Direction.NEED, Direction.WANT, null), cards.map { it.direction })
        assertEquals(listOf("Мы разложили траты", "На нужное ушло 13", "Как задумали"), explain.afterSort(s))
        s = game.finishSort(s)
        assertEquals(10, s.week!!.taskReward)
        assertTrue(game.summaryOpen(s))
    }

    // ---------- Ситуация, вещи, носимое ----------

    @Test fun `ситуация — выбор кладёт вариант в корзину, деньги уходят только в магазине`() {
        var s = game.seeAnnouncement(game.openParcel(demo.weekStart(3)))
        assertFalse(game.situationOpen(s)) // до плана — нет
        assertThrows { game.chooseSituation(s, 0) }
        s = game.confirmPlan(game.setPlan(s, Plan(14, 10, 10)))
        assertTrue(game.situationOpen(s))
        val wallet = s.progress.wallet
        s = game.chooseSituation(s, 1)
        assertEquals(listOf("kurtka", "risunok"), game.situationCart(s))
        assertEquals(wallet, s.progress.wallet)
        assertFalse(game.situationOpen(s))
        s = game.clearSituation(s)
        assertTrue(game.situationOpen(s)) // убрал из корзины — при входе в дверь экран снова
        s = game.chooseSituation(s, 1)
        // Надбавка — из «Хочу»: куртка 6 из «Нужного», рисунок 4 из «Хочу».
        s = game.buy(s, listOf("kasha", "mylo") + game.situationCart(s))
        assertEquals(14, s.week!!.paidNeed)
        assertEquals(4, s.week!!.paidWant)
        assertNull(game.situationShelf(s))
        assertTrue(game.mandatoryBought(s))
        assertEquals(setOf("kurtka", "risunok"), game.worn(s))
    }

    @Test fun `зябнет с перехода в главу 2 до покупки куртки, куртка больше не продаётся`() {
        var s = demo.weekStart(3)
        assertTrue(game.cold(s))
        s = demo.playWeek(s)
        assertFalse(game.cold(s))
        s = demo.nextWeekStart(s)
        s = plan(s, 12, 10)
        assertTrue(game.shopShelves(s).none { sh -> sh.tiers.flatten().contains("kurtka") })
    }

    @Test fun `запасная неделя главы 2 — куртка на полке, пока не куплена`() {
        var s = demo.weekStart(3)
        s = skipWeek(s, save = 10)
        s = skipWeek(game.nextWeek(s), save = 10)
        s = game.nextWeek(s)
        assertEquals(3, s.week!!.number)
        s = plan(s, 14, 10)
        assertTrue(game.situationOpen(s)) // куртка — на экране ситуации и в обязательном
        assertEquals("Куртки пока нет", explain.weekNeeds(game.buy(s, listOf("kasha", "mylo"))))
    }

    @Test fun `бинт снимается сам через неделю, куртка остаётся`() {
        var s = plan(demo.weekStart(4), 12, 10)
        s = game.chooseSituation(s, 1)
        s = game.buy(game.resolveDuplicate(s), listOf("kasha", "mylo") + game.situationCart(s))
        assertTrue("bint" in game.worn(s))
        s = demo.nextWeekStart(game.finishWeek(game.leavePiggy(game.deposit(game.leaveShop(s))), SummaryChoice.KEEP_PLAN))
        assertEquals(3, s.progress.chapter)
        assertFalse("bint" in game.worn(s))
        assertTrue("kurtka" in game.worn(s))
    }

    // ---------- Взрослый, сложность, конец игры ----------

    @Test fun `«Сложнее» — план каждой недели открывается пустым, со следующего плана`() {
        var s = game.setDifficulty(fresh(), senior = true)
        assertEquals(Plan(10, 10, 10), s.week!!.plan) // текущий план не трогается
        s = game.nextWeek(skipWeek(s, save = 10))
        assertEquals(Plan(0, 0, 0), s.week!!.plan)
        s = game.nextWeek(skipWeek(game.setDifficulty(s, senior = false), save = 10))
        assertEquals(Plan(0, 40, 10), s.week!!.plan) // «Проще» — как вышло или оставленный план
    }

    @Test fun `бонус взрослого — раз в неделю, 5 в копилку`() {
        var s = fresh()
        assertTrue(game.canBonus(s))
        s = game.adultBonus(s)
        assertEquals(5, s.progress.savings)
        assertFalse(game.canBonus(s))
        assertThrows { game.adultBonus(s) }
        s = game.nextWeek(skipWeek(s))
        assertTrue(game.canBonus(s))
        assertEquals(0, s.week!!.bonus)
    }

    @Test fun `конец игры — «Играть дальше» без денег, «Начать сначала» с тем же питомцем`() {
        var s = game.playEvent(demo.playWeek(demo.weekStart(8)))
        assertEquals(Phase.GAME_OVER, s.phase)
        assertEquals(Step.NONE, game.nextStep(s))
        val more = game.keepPlaying(s)
        assertEquals(Phase.FREE_PLAY, more.phase)
        assertThrows { game.nextWeek(more) }
        val again = game.restart(more)
        assertEquals(Phase.ONBOARDING, again.phase)
        assertEquals(s.profile, again.profile)
        assertEquals(1, again.progress.chapter)
        assertEquals(0, again.progress.wallet)
        assertTrue(again.progress.inventory.isEmpty())
    }

    // ---------- Тексты ----------

    @Test fun `события — оба исхода не длиннее 25 слов, куртка упоминается, только если куплена`() {
        val texts = content.texts
        val s8 = demo.playWeek(demo.weekStart(8))
        val a = explain.eventLines(s8)
        assertEquals(listOf("Пришла Кира", "Мы купили корзину", "Вещи теперь на месте"), a)
        val b = explain.eventLines(s8.copy(progress = s8.progress.copy(savings = 10)))
        assertEquals("Монет на корзину не хватило", b[1])
        val snow = demo.playWeek(demo.weekStart(4))
        assertEquals(listOf("Выпал первый снег", "Мы купили тёплую лежанку", "Я сплю на лежанке"), explain.eventLines(snow))
        val cold = snow.copy(progress = snow.progress.copy(savings = 0, inventory = emptyList()))
        assertEquals(listOf("Выпал первый снег", "Монет на тёплую лежанку не хватило", "Я сплю у окна"), explain.eventLines(cold))
        (a + b).let { assertTrue(texts.screenWords(it.joinToString(" ")) <= 25) }
    }

    @Test fun `итог недели с предметом — что взяли или что не брали, без подсказки`() {
        var s = plan(demo.weekStart(6), 14, 10)
        s = game.buy(s, listOf("kasha", "mylo"))
        s = game.leavePiggy(game.deposit(game.leaveShop(s)))
        assertEquals("Мы не брали лампу", explain.summaryLines(s).first())
        var t = plan(demo.weekStart(6), 14, 10)
        t = game.chooseSituation(t, 1)
        t = game.buy(t, listOf("kasha", "mylo") + game.situationCart(t))
        assertEquals("Мы взяли лампу с абажуром", explain.summaryLines(t).first())
    }

    // ---------- Сохранение ----------

    @Test fun `сохранение — глава 3 с носимым, бонусом, дневником и отметками читается целиком`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        var s = plan(demo.weekStart(6), 14, 10)
        s = game.adultBonus(game.chooseSituation(s, 1))
        s = game.buy(s, listOf("kasha", "mylo") + game.situationCart(s))
        val back = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), s))
        assertEquals(s, back)
        assertTrue(back.progress.history.size == 5 && "kurtka" in back.progress.inventory && back.week!!.bonus == 5)
    }

    @Test fun `старое сохранение после дня рождения продолжается переходом в главу 2`() {
        var s = demo.playWeek(demo.weekStart(2))
        s = game.playEvent(s)
        // Так выглядело сохранение прототипа: глава 1, свободная игра, версия 1.
        val old = s.copy(version = 1, phase = Phase.FREE_PLAY, progress = s.progress.copy(chapter = 1), chapter = s.chapter.copy(goalId = "podarok_kniga"), transition = null)
        val m = game.migrate(old)
        assertEquals(Phase.TRANSITION, m.phase)
        assertEquals(2, m.progress.chapter)
        assertEquals("Мы ели две недели", explain.transitionLines(m).first())
        assertEquals(m, game.migrate(m))
        val fresh = game.migrate(GameState(version = 1))
        assertEquals(Phase.ONBOARDING, fresh.phase)
    }
}
