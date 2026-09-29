package ru.vinteno.finni.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vinteno.finni.core.content.Content
import ru.vinteno.finni.core.engine.Enough
import ru.vinteno.finni.core.engine.Game
import ru.vinteno.finni.core.engine.IllegalMove
import ru.vinteno.finni.core.engine.Step
import ru.vinteno.finni.core.engine.requireWeek
import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.model.EventOutcome
import ru.vinteno.finni.core.model.Fur
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.ParcelResult
import ru.vinteno.finni.core.model.Phase
import ru.vinteno.finni.core.model.Plan
import ru.vinteno.finni.core.model.SummaryChoice
import kotlin.random.Random

/**
 * Экономика главы 1 по test-cases.md, E01–E18. Числа — младший профиль, канонический путь.
 * Если реализация даёт другие числа, ошибка в реализации (scenario-chapter-1.md §10).
 */
class EconomyTest {
    private val content = Content.fromResources()
    private val game = Game(content)

    private fun newGame(goal: String = "podarok_kniga"): GameState {
        var s = GameState()
        s = game.seeIntro(s)
        s = game.createPet(s, "", Fur.GINGER, Accessory.SCARF)
        return game.chooseGoal(s, goal)
    }

    /**
     * Посылка, объявление, план — и подтверждение. Без [plan] черновик дополняется до кошелька в «Хочу»:
     * недобор блокирует подтверждение (I45), а остаток ребёнок раскладывает сам — на неделе 2 это 10 / 9 / 10.
     */
    private fun toPlan(s0: GameState, plan: Plan? = null): GameState {
        var s = game.openParcel(s0)
        s = game.seeAnnouncement(s)
        val p = plan ?: s.week!!.plan.let { it.copy(want = it.want + s.progress.wallet - it.total) }
        s = game.setPlan(s, p)
        return game.confirmPlan(s)
    }

    /** Зашёл в магазин, ничего не купил и пошёл дальше — в копилку: шаг магазина пройден (I45). */
    private fun skipShop(s: GameState) = game.leavePiggy(game.leaveShop(s))

    private fun inShop(s: GameState, vararg cart: String) =
        game.leaveShop(game.buy(s, cart.toList(), agreedWant = true, agreedSavings = true))

    /** Неделя 1 канонического пути: 10/5/10, каша с ягодами и мыло, взнос 10, F1. */
    private fun canonicalWeek1(goal: String = "podarok_kniga"): GameState {
        var s = toPlan(newGame(goal))
        s = inShop(s, "kasha", "yagody", "mylo")
        s = game.feed(s); s = game.wash(s)
        s = game.deposit(s)
        return game.leavePiggy(s)
    }

    @Test fun `внешность и имя двумя ходами, внешность сохраняется сразу`() {
        var s = game.seeIntro(GameState())
        s = game.chooseLook(s, Fur.BROWN, Accessory.BOW)
        assertTrue(s.profile.lookChosen)
        assertFalse(s.profile.created)
        assertEquals(Fur.BROWN, s.profile.fur)
        assertEquals(Accessory.BOW, s.profile.accessory)
        s = game.namePet(s, " Кнопка ")
        assertTrue(s.profile.created)
        assertEquals("Кнопка", s.profile.petName)
    }

    @Test fun `имя только после внешности, назад к внешности выбор не теряет`() {
        val s0 = game.seeIntro(GameState())
        assertThrows { game.namePet(s0, "Кнопка") }
        val s1 = game.backToLook(game.chooseLook(s0, Fur.BLUE, Accessory.CAP))
        assertFalse(s1.profile.lookChosen)
        assertEquals(Fur.BLUE, s1.profile.fur)
        assertEquals(Accessory.CAP, s1.profile.accessory)
        val named = game.createPet(s0, "Ириска", Fur.GINGER, Accessory.SCARF)
        assertThrows { game.chooseLook(named, Fur.BLUE, Accessory.CAP) }
    }

    @Test fun `E01 посылка приходит при пустом кошельке`() {
        val s = game.openParcel(newGame())
        assertEquals(25, s.progress.wallet)
        assertEquals(ParcelResult.ARRIVED, s.week!!.parcel)
    }

    @Test fun `E02 посылка приходит каждую неделю, остаток не мешает`() {
        // Ничего не купить и ничего не отложить: 25 в кошельке — посылка всё равно приходит (I81).
        var s = toPlan(newGame(), Plan(0, 25, 0))
        s = skipShop(s)
        s = game.finishWeek(s, SummaryChoice.KEEP_PLAN)
        assertEquals(25, s.progress.wallet)
        s = game.openParcel(game.nextWeek(s))
        assertEquals(ParcelResult.ARRIVED, s.week!!.parcel)
        assertEquals(50, s.progress.wallet)
    }

    @Test fun `E02 посылка не приходит, если кошелёк перевалил бы за 100`() {
        val s0 = newGame()
        val s = game.openParcel(s0.copy(progress = s0.progress.copy(wallet = 80)))
        assertEquals(ParcelResult.NOT_ARRIVED, s.week!!.parcel)
        assertEquals(80, s.progress.wallet)
    }

    @Test fun `E03 остаток переносится`() {
        var s = game.finishWeek(canonicalWeek1(), SummaryChoice.KEEP_PLAN)
        assertEquals(4, s.progress.wallet)
        s = game.openParcel(game.nextWeek(s))
        assertEquals(29, s.progress.wallet)
    }

    @Test fun `E04 канонический путь недели 1 — кошелёк 4, копилка 15`() {
        val s = canonicalWeek1()
        assertEquals(4, s.progress.wallet)
        assertEquals(15, s.progress.savings)
    }

    @Test fun `факт — по тому, что куплено, 8 3 10`() {
        val s = canonicalWeek1()
        val sum = game.summary(s)
        assertEquals(25, sum.planned)
        assertEquals(Plan(8, 3, 10), sum.fact)
        assertEquals(21, sum.fact.total)
        assertEquals(5, sum.reward)
        assertEquals(0, sum.needFromWant)
        val next = game.finishWeek(s, SummaryChoice.TAKE_ACTUAL)
        assertEquals(Plan(8, 3, 10), next.nextPlan)
        assertEquals(Plan(10, 5, 10), game.finishWeek(s, SummaryChoice.KEEP_PLAN).nextPlan)
    }

    @Test fun `I84 перерасход нужного виден на итоге, «Взять как вышло» его и берёт`() {
        // «Нужное» 5, каша и мыло стоят 8: 3 добираются из «Хочу». Вышло на нужное 8 — больше задуманного.
        var s = toPlan(newGame(), Plan(5, 10, 10))
        s = game.buy(s, listOf("kasha", "mylo"), agreedWant = true)
        s = game.leavePiggy(game.deposit(game.leaveShop(s)))
        assertEquals(Plan(8, 0, 10), game.summary(s).fact)
        assertEquals(3, game.summary(s).needFromWant)
        assertTrue(game.overPlan(s))
        assertFalse(game.onPlan(s))
        val next = game.finishWeek(s, SummaryChoice.TAKE_ACTUAL)
        assertEquals(Plan(8, 0, 10), next.nextPlan)
        assertEquals(0, next.progress.marksPlan)          // неделя не по плану — отметки плана нет (I82)
        assertEquals(1, next.progress.marksCare)
        assertEquals(1, next.progress.marksSave)
    }

    @Test fun `I82 отметка плана — за неделю по плану, а не за подтверждение`() {
        val kept = game.finishWeek(canonicalWeek1(), SummaryChoice.KEEP_PLAN)
        assertEquals(1, kept.progress.marksPlan)
        assertTrue(kept.progress.history.last().onPlan)
        // Пустая неделя: план подтверждён, но ничего не куплено и не отложено — отметки нет.
        val empty = game.finishWeek(skipShop(toPlan(newGame(), Plan(10, 15, 0))), SummaryChoice.KEEP_PLAN)
        assertEquals(0, empty.progress.marksPlan)
        // Меньше задуманного — не расхождение: отказ от покупки не ошибка.
        var less = toPlan(newGame(), Plan(10, 5, 10))
        less = game.leavePiggy(game.deposit(game.leaveShop(game.buy(less, listOf("krupa", "mylo")))))
        assertEquals(1, game.finishWeek(less, SummaryChoice.KEEP_PLAN).progress.marksPlan)
    }

    @Test fun `надбавка платится из Хочу, база из Нужного`() {
        val s = toPlan(newGame())
        val q = game.quote(s, listOf("kasha", "yagody", "mylo"))
        assertEquals(11, q.total)
        assertEquals(8, q.fromNeed)
        assertEquals(3, q.fromWantOwn)
        assertEquals(0, q.needShortage)
        assertFalse(q.asksWant)
        assertFalse(q.asksSavings)
    }

    @Test fun `надбавка при пустом Хочу не берётся молча из Нужного`() {
        val s = toPlan(newGame(), Plan(15, 0, 10))
        val q = game.quote(s, listOf("kasha", "yagody"))
        assertEquals(5, q.fromNeed)
        assertEquals(0, q.fromWantOwn)
        assertEquals(3, q.fromSavings)
    }

    @Test fun `Оставить план оставляет план этой недели`() {
        var s = toPlan(newGame(), Plan(14, 1, 10))
        // Итог открывается после магазина: зашёл и пошёл дальше в копилку (I45).
        s = game.leavePiggy(game.leaveShop(s))
        assertEquals(Plan(14, 1, 10), game.finishWeek(s, SummaryChoice.KEEP_PLAN).nextPlan)
    }

    @Test fun `E05 награда падает в копилку после покупки, кошелёк — только на цену`() {
        val s = game.buy(toPlan(newGame()), listOf("kasha", "mylo"))
        assertEquals(17, s.progress.wallet)
        assertEquals(5, s.progress.savings)
    }

    @Test fun `F1 — награда, когда куплено всё нужное недели, а не за любую покупку`() {
        var s = game.leaveShop(toPlan(newGame()))
        assertEquals(0, s.progress.savings)
        assertFalse(s.week!!.taskDone)
        s = game.buy(game.leaveShop(s), listOf("krupa"))
        assertEquals(0, s.progress.savings)                 // мыла нет — задание ещё не пройдено (I83)
        assertFalse(s.week!!.taskDone)
        s = game.buy(s, listOf("mylo"))
        assertEquals(5, s.progress.savings)
        assertTrue(s.week!!.taskDone)
    }

    @Test fun `F1 — только хотелка — задание не пройдено, награды нет`() {
        var s = toPlan(newGame(), Plan(0, 25, 0))
        s = game.buy(s, listOf("kacheli"))
        assertFalse(s.week!!.taskDone)
        assertEquals(0, s.progress.savings)
        s = game.finishWeek(game.leavePiggy(game.leaveShop(s)), SummaryChoice.KEEP_PLAN)
        assertFalse(s.progress.history.last().taskDone)
        assertTrue("F1" !in s.progress.doneTasks)
    }

    @Test fun `E06 повтор задания не даёт ничего`() {
        var s = game.buy(toPlan(newGame(), Plan(10, 15, 0)), listOf("krupa", "mylo"))
        val once = s.progress
        s = game.buy(s, listOf("kacheli"))
        assertEquals(once.savings, s.progress.savings)
        assertEquals(5, s.week!!.taskReward)
    }

    @Test fun `E07 превышение не блокирует ввод, блокирует подтверждение`() {
        var s = game.seeAnnouncement(game.openParcel(newGame()))
        s = game.setPlan(s, Plan(14, 10, 10))
        assertEquals(Plan(14, 10, 10), s.week!!.plan) // сумма не исправлена сама
        assertFalse(game.canConfirmPlan(s))
        assertThrows { game.confirmPlan(s) }
        assertThrows { game.quote(s, listOf("kasha")) } // до подтверждения тратить нельзя
    }

    @Test fun `недобор блокирует подтверждение так же, как перебор`() {
        var s = game.seeAnnouncement(game.openParcel(game.nextWeek(game.finishWeek(canonicalWeek1(), SummaryChoice.KEEP_PLAN))))
        assertEquals(29, s.progress.wallet)
        assertEquals(Plan(10, 5, 10), s.week!!.plan)           // план по умолчанию прежний
        assertFalse(game.canConfirmPlan(s))                     // «Осталось разложить 4 монеты.»
        assertThrows { game.confirmPlan(s) }
        s = game.setPlan(s, Plan(10, 19, 0))
        assertTrue(game.canConfirmPlan(s))
        s = game.confirmPlan(s)
        val q = game.quote(s, listOf("kacheli"))                // качели — на весь остаток, без копилки
        assertFalse(q.asksWant || q.asksSavings)
        s = game.buy(s, listOf("kacheli"))
        assertEquals(15, s.progress.savings)
        assertTrue("kacheli" in s.progress.inventory)
    }

    @Test fun `E08 ноль в Нужном и в Копилке разрешён`() {
        var s = game.seeAnnouncement(game.openParcel(newGame()))
        s = game.setPlan(s, Plan(0, 25, 0))
        assertTrue(game.canConfirmPlan(s))
    }

    @Test fun `E09 взнос равен плановой сумме`() {
        var s = toPlan(newGame(), Plan(10, 0, 15))
        s = game.deposit(s)
        assertEquals(15, s.week!!.deposit)
        assertEquals(15, s.progress.savings)
        assertFalse(game.canDeposit(s)) // второй раз за неделю — нет
    }

    @Test fun `ноль в Копилке — кнопки взноса нет`() {
        val s = toPlan(newGame(), Plan(10, 15, 0))
        assertFalse(game.canDeposit(s))
    }

    @Test fun `E10 нехватка в направлении не гасит покупку, а спрашивает`() {
        // «Хочу» пусто, «Нужное» 5: каша и мыло стоят 8 — добор 3 из копилки, где лежит взнос 20.
        var s = game.deposit(toPlan(newGame(), Plan(5, 0, 20)))
        val q = game.quote(s, listOf("kasha", "mylo"))
        assertFalse(q.asksWant)
        assertTrue(q.asksSavings)
        assertEquals(3, q.fromSavings)
        assertEquals(17, q.savingsAfter) // «Останется 17 из 40» — до выбора
        assertThrows { game.buy(s, listOf("kasha", "mylo")) } // без подтверждения нельзя
        s = game.buy(s, listOf("kasha", "mylo"), agreedSavings = true)
        assertEquals(17 + 5, s.progress.savings) // и награда F1 — после покупки всего нужного
        assertEquals(0, s.progress.wallet)
    }

    @Test fun `берём сколько есть в Хочу, остаток — из копилки`() {
        val s = toPlan(newGame(), Plan(4, 2, 19))
        val q = game.quote(s, listOf("kasha", "mylo")) // 8 при «Нужном» 4
        assertEquals(4, q.needShortage)
        assertEquals(2, q.needFromWant)
        assertEquals(2, q.fromSavings)
    }

    @Test fun `E11 уже купленная вещь не списывается`() {
        var s = game.finishWeek(canonicalWeek1(), SummaryChoice.KEEP_PLAN)
        s = toPlan(game.nextWeek(s), Plan(10, 19, 0))
        s = game.buy(s, listOf("kacheli"))
        val w = s.progress.wallet
        val q = game.quote(s, listOf("kacheli"))
        assertEquals(0, q.total)
        assertTrue(q.items.isEmpty())
        assertEquals(w, s.progress.wallet)
    }

    @Test fun `I82 запасная неделя — «ещё день» без посылки`() {
        var s = toPlan(newGame(), Plan(10, 15, 0))
        s = game.finishWeek(skipShop(s), SummaryChoice.KEEP_PLAN)
        s = game.finishWeek(skipShop(toPlan(game.nextWeek(s), Plan(10, 40, 0))), SummaryChoice.KEEP_PLAN)
        assertEquals(Phase.AFTER_SUMMARY, s.phase)          // отметок нет — запасная неделя
        val wallet = s.progress.wallet
        s = game.openParcel(game.nextWeek(s))
        assertTrue(game.spareWeek(s))
        assertEquals(ParcelResult.NOT_ARRIVED, s.week!!.parcel)
        assertEquals(wallet, s.progress.wallet)
    }

    @Test fun `E12 кошелёк и копилка не уходят ниже нуля и выше 100 ни на каком пути, игра всегда кончается`() {
        val rnd = Random(2026)
        repeat(1500) {
            var s = newGame(content.chapter1.goalIds.random(rnd))
            var steps = 0
            while (s.phase != Phase.FREE_PLAY && steps < 3000) {
                steps++
                val before = s
                s = randomMove(s, rnd) ?: continue
                assertTrue("кошелёк ${s.progress.wallet}", s.progress.wallet in 0..100)
                assertTrue("копилка ${s.progress.savings}\n${before.week}\n${s.week}\n${before.progress.savings} ${before.phase} ${s.phase}", s.progress.savings in 0..100)
                assertTrue("недель ${s.progress.weekTotal}", s.progress.weekTotal <= 11) // 3 + 3 + 5
            }
            assertEquals(Phase.FREE_PLAY, s.phase) // тупиков нет: игра доходит до конца
        }
    }

    private fun randomMove(s: GameState, rnd: Random): GameState? = try {
        val w = s.week
        when {
            s.phase == Phase.GAME_OVER -> game.keepPlaying(s)
            s.phase == Phase.TRANSITION -> game.seeTransition(s)
            s.phase == Phase.ONBOARDING -> game.chooseGoal(s, game.ch(s).goalIds.random(rnd))
            s.phase == Phase.EVENT -> game.playEvent(s)
            s.phase == Phase.AFTER_SUMMARY -> game.nextWeek(s)
            w!!.parcel == null -> game.openParcel(s)
            !w.announcementSeen -> game.seeAnnouncement(s)
            !w.planConfirmed -> {
                // Чаще — весь кошелёк по направлениям, иногда — недобор или перебор.
                val wallet = s.progress.wallet
                val need = rnd.nextInt(0, wallet + 1)
                val save = rnd.nextInt(0, wallet - need + 1)
                val p = if (rnd.nextInt(4) > 0) Plan(need, wallet - need - save, save)
                else Plan(rnd.nextInt(0, 25), rnd.nextInt(0, 25), rnd.nextInt(0, 25))
                val s2 = game.setPlan(s, p)
                if (game.canConfirmPlan(s2)) game.confirmPlan(s2) else s2
            }
            else -> when (rnd.nextInt(11)) {
                0 -> {
                    val shelves = game.shopShelves(s)
                    val cart = shelves.filter { rnd.nextBoolean() }.flatMap { it.tiers.random(rnd) } + game.situationCart(s) +
                        (if (rnd.nextBoolean()) listOf(game.ch(s).chapterWantId) else emptyList())
                    val pay = if (rnd.nextBoolean()) ru.vinteno.finni.core.model.PayChoice.NEED else ru.vinteno.finni.core.model.PayChoice.SAVINGS
                    game.buy(s, cart, agreedWant = rnd.nextBoolean(), agreedSavings = rnd.nextBoolean(), pay = pay)
                }
                1 -> game.leaveShop(s)
                2 -> game.deposit(s)
                3 -> if (game.choiceOpen(s) && !game.ballOffer(s).available) game.acknowledgeNoBall(s) else game.chooseBall(s, rnd.nextBoolean())
                4 -> if (rnd.nextBoolean()) game.feed(s) else game.wash(s)
                5 -> game.leavePiggy(s)
                6 -> game.situationShelf(s)?.let { game.chooseSituation(s, rnd.nextInt(it.tiers.size)) }
                7 -> when {
                    game.duplicatePending(s) && rnd.nextBoolean() -> game.resolveDuplicate(s)
                    game.duplicatePending(s) -> game.buyDuplicate(s, rnd.nextBoolean(), rnd.nextBoolean())
                    game.canReturnDuplicate(s) -> game.returnDuplicate(s)
                    else -> game.finishSort(s, game.sortCards(s).map { if (rnd.nextBoolean()) it.direction else null })
                }
                else -> game.finishWeek(s, if (rnd.nextBoolean()) SummaryChoice.KEEP_PLAN else SummaryChoice.TAKE_ACTUAL)
            }
        }
    } catch (e: IllegalMove) {
        null
    }

    @Test fun `E13 цель выбирается один раз`() {
        val s = newGame("podarok_myach")
        assertThrows { game.chooseGoal(s, "podarok_kniga") }
    }

    @Test fun `E15 достигнутая цель не списывается на копилке и не отменяет отметку взноса`() {
        var s = game.finishWeek(canonicalWeek1(), SummaryChoice.KEEP_PLAN)
        s = toPlan(game.nextWeek(s), Plan(8, 0, 21))                      // 29 в кошельке
        s = game.deposit(inShop(s, "kasha", "mylo"))
        s = game.leavePiggy(game.chooseBall(s, take = false)) // F5 — на копилке после взноса (QA-M3)
        assertEquals(41, s.progress.savings)
        assertTrue(game.goalReached(s)) // «Накопил 41. Подарок готов.» — ничего не списано
        s = game.finishWeek(s, SummaryChoice.KEEP_PLAN)
        assertEquals(2, s.progress.marksSave)
        assertEquals(Phase.EVENT, s.phase)
        s = game.playEvent(s)
        assertEquals(EventOutcome.GIFT_GIVEN, s.eventOutcome)
        assertEquals(1, s.progress.savings) // списание — на событии
        assertEquals(2, s.progress.stage)   // отметки набраны — Финни подрос (I82)
        assertTrue(s.transition!!.grew)
    }

    @Test fun `E16 при закрытой цели взнос исполняется, как запланирован, излишек в копилке`() {
        var s = game.finishWeek(canonicalWeek1("podarok_myach"), SummaryChoice.KEEP_PLAN)
        s = s.copy(progress = s.progress.copy(savings = 30))           // цель 30 уже набрана
        s = toPlan(game.nextWeek(s), Plan(10, 9, 10))                   // 4 + 25 = 29 в кошельке
        assertTrue(game.goalReached(s))
        assertTrue(game.canDeposit(s))                       // QA-M2: взнос по плану предлагается
        val wallet = s.progress.wallet
        s = game.deposit(s)
        assertEquals(40, s.progress.savings)                  // излишек остаётся в копилке
        assertEquals(wallet - 10, s.progress.wallet)
        assertEquals(Plan(0, 0, 10), game.summary(s).fact)   // план и факт по копилке совпали — призрачной разницы нет
    }

    @Test fun `E17 счётчики только растут`() {
        var s = toPlan(newGame(), Plan(0, 25, 0))
        s = skipShop(s)
        s = game.finishWeek(s, SummaryChoice.KEEP_PLAN)
        assertEquals(0, s.progress.marksCare)
        assertEquals(0, s.progress.marksPlan)                 // пустая неделя — не «по плану» (I82)
        assertEquals(0, s.progress.marksSave)
        s = game.finishWeek(game.leavePiggy(game.deposit(game.leaveShop(toPlan(game.nextWeek(s), Plan(10, 30, 10))))), SummaryChoice.KEEP_PLAN)
        assertEquals(1, s.progress.marksSave)
        assertEquals(1, s.progress.marksPlan)
        assertTrue(s.progress.wallet >= 0)
    }

    @Test fun `E18 отметка заботы — за покупку, Финни ест сам при переходе к итогу`() {
        var s = toPlan(newGame())
        s = inShop(s, "krupa", "mylo")
        assertFalse(s.week!!.fed)
        s = game.finishWeek(s, SummaryChoice.KEEP_PLAN)
        assertTrue(s.requireWeek().fed)
        assertEquals(1, s.progress.marksCare)
    }

    @Test fun `событие играется всегда — исход Б ничего не списывает, Финни без отметок не растёт`() {
        var s = toPlan(newGame("podarok_samokat"))
        s = skipShop(s)
        s = game.finishWeek(s, SummaryChoice.KEEP_PLAN)
        s = skipShop(toPlan(game.nextWeek(s)))
        s = game.finishWeek(s, SummaryChoice.KEEP_PLAN)
        // Отметок заботы нет — играется запасная неделя, после неё глава кончается в любом случае (§9, D1).
        assertEquals(Phase.AFTER_SUMMARY, s.phase)
        s = skipShop(toPlan(game.nextWeek(s)))
        assertEquals(3, s.week!!.number)
        s = game.finishWeek(s, SummaryChoice.KEEP_PLAN)
        assertEquals(Phase.EVENT, s.phase)
        assertFalse(s.transition!!.grew)
        val before = s.progress.savings
        s = game.playEvent(s)
        assertEquals(EventOutcome.NOT_ENOUGH, s.eventOutcome)
        assertEquals(before, s.progress.savings)
        assertEquals(Phase.TRANSITION, s.phase)
        assertEquals(2, s.progress.chapter)
        assertEquals(1, s.progress.stage)                     // глава сменилась, стадия — нет (I82)
        assertThrows { game.nextWeek(s) } // неделя новой главы — после выбора цели
    }

    /** F5 в новой экономике (I81): мячик в неделю 2 всегда отнимает подарок — честная развилка. */
    @Test fun `F5 — последствие мячика при трёх целях`() {
        // Выбор — на копилке после взноса недели 2 (QA-M3): в копилке 15 + взнос.
        fun week2(goal: String, save: Int): GameState {
            val s = game.finishWeek(canonicalWeek1(goal), SummaryChoice.KEEP_PLAN)
            return game.deposit(toPlan(game.nextWeek(s), Plan(8, 21 - save, save))) // в кошельке 29
        }
        val cheap10 = game.ballOffer(week2("podarok_myach", 10))
        assertTrue(cheap10.available)
        assertEquals(10, cheap10.savingsAfter)
        assertFalse(cheap10.giftStillPossible) // 25 − 15 + 5 = 15 < 30
        assertFalse(game.ballOffer(week2("podarok_myach", 21)).giftStillPossible) // 36 − 15 + 5 = 26 < 30
        assertFalse(game.ballOffer(week2("podarok_kniga", 10)).giftStillPossible)
        assertFalse(game.ballOffer(week2("podarok_samokat", 21)).giftStillPossible)
    }

    @Test fun `F5 — мячик только из копилки, реакция и награда одинаковы при обоих решениях`() {
        val base = game.finishWeek(canonicalWeek1(), SummaryChoice.KEEP_PLAN)
        val s = game.deposit(toPlan(game.nextWeek(base)))
        val taken = game.chooseBall(s, take = true)
        val kept = game.chooseBall(s, take = false)
        assertEquals(s.progress.wallet, taken.progress.wallet)
        assertEquals(25 - 15 + 5, taken.progress.savings)
        assertEquals(25 + 5, kept.progress.savings)
        assertTrue("myachik" in taken.progress.inventory)
        // Мячик — трата «Хочу» на итоге, но отметку плана не отнимает: это выбор про копилку.
        assertEquals(15, game.summary(taken).fact.want - game.summary(kept).fact.want)
        assertFalse(game.overPlan(taken))
    }

    @Test fun `F5 — при копилке меньше 15 выбор не предлагается`() {
        var s = toPlan(newGame(), Plan(10, 15, 0))
        s = game.finishWeek(skipShop(s), SummaryChoice.KEEP_PLAN) // без покупки нет и награды: копилка 0
        s = toPlan(game.nextWeek(s), Plan(10, 40, 0))
        assertTrue(game.choiceOpen(s))                  // при нуле в плане задание открыто сразу
        assertFalse(game.ballOffer(s).available)
    }

    @Test fun `QA-M3 F5 открывается на копилке после взноса, а не до`() {
        val base = game.finishWeek(canonicalWeek1(), SummaryChoice.KEEP_PLAN)
        var s = toPlan(game.nextWeek(base))
        assertFalse(game.choiceOpen(s))
        assertThrows { game.chooseBall(s, true) }
        s = game.leaveShop(s)
        assertFalse(s.week!!.taskDone)                  // магазин задание F5 больше не закрывает
        s = game.deposit(s)
        assertTrue(game.choiceOpen(s))
        assertTrue(game.ballOffer(s).available)
    }

    @Test fun `QA-M3 взнос 10 открывает мячик тому, кто купил нужное`() {
        var s = toPlan(newGame(), Plan(10, 15, 0))
        s = game.finishWeek(game.buy(s, listOf("kasha", "mylo")), SummaryChoice.KEEP_PLAN) // награда 5, в кошельке 17
        s = game.deposit(toPlan(game.nextWeek(s), Plan(10, 22, 10)))      // 42 в кошельке; 5 + 10 = 15
        assertTrue(game.ballOffer(s).available)
    }

    @Test fun `QA-M3 шаг Копилка на неделе 2 стоит, пока задание не пройдено, даже при нуле`() {
        var s = toPlan(newGame(), Plan(10, 15, 0))
        s = game.finishWeek(skipShop(s), SummaryChoice.KEEP_PLAN)
        s = toPlan(game.nextWeek(s), Plan(10, 40, 0))
        s = game.leaveShop(s)
        assertEquals(Step.SHOP, game.nextStep(s))       // зашёл и вышел без покупки — шаг остаётся
        s = game.leavePiggy(s)                          // пошёл в копилку — магазин пройден, вышел, не ответив
        assertEquals(Step.SAVE, game.nextStep(s))
        s = game.acknowledgeNoBall(s)
        assertEquals(Step.SUMMARY, game.nextStep(s))
        assertEquals(0, s.week!!.taskReward)            // I83: без монет в копилке выбора нет — и награды нет
        assertTrue(s.week!!.taskMissed)
    }

    @Test fun `неделя 2 с перенесённым остатком — качели помещаются`() {
        var s = game.finishWeek(canonicalWeek1(), SummaryChoice.KEEP_PLAN)
        s = toPlan(game.nextWeek(s), Plan(10, 19, 0)) // весь кошелёк 29
        val q = game.quote(s, listOf("kacheli"))
        assertFalse(q.asksWant || q.asksSavings)
        s = game.buy(s, listOf("kacheli"))
        assertTrue(s.chapter.wantBought)
    }

    @Test fun `записка ведёт по шагам недели`() {
        var s = newGame()
        val steps = mutableListOf(game.nextStep(s))
        s = game.openParcel(s); steps += game.nextStep(s)
        s = game.seeAnnouncement(s); steps += game.nextStep(s)
        s = game.confirmPlan(s); steps += game.nextStep(s)
        s = game.buy(s, listOf("kasha", "mylo")); steps += game.nextStep(s) // покупка закрывает магазин и без выхода
        s = game.feed(s); steps += game.nextStep(s)
        s = game.wash(s); steps += game.nextStep(s)
        s = game.leavePiggy(s); steps += game.nextStep(s) // вышел, не отложив — шаг закрыт
        s = game.finishWeek(s, SummaryChoice.KEEP_PLAN); steps += game.nextStep(s)
        assertEquals(
            listOf(Step.PARCEL, Step.ANNOUNCE, Step.PLAN, Step.SHOP, Step.CARE, Step.CARE, Step.SAVE, Step.SUMMARY, Step.NEXT_WEEK),
            steps,
        )
    }

    @Test fun `при Копилке 0 шага Отложить нет`() {
        var s = toPlan(newGame(), Plan(10, 15, 0))
        s = game.buy(s, listOf("krupa"))
        s = game.feed(s)
        assertEquals(Step.SUMMARY, game.nextStep(s))
    }

    @Test fun `шаг магазина — вход без покупки не закрывает, переход в копилку закрывает`() {
        var s = toPlan(newGame())
        assertEquals(Step.SHOP, game.nextStep(s))
        s = game.leaveShop(s)
        assertEquals(Step.SHOP, game.nextStep(s))
        assertFalse(game.summaryOpen(s))                // «Итог — в конце недели.»
        assertThrows { game.finishWeek(s, SummaryChoice.KEEP_PLAN) }
        s = game.leavePiggy(s)
        assertEquals(Step.SUMMARY, game.nextStep(s))    // вышел из копилки, не отложив: шаг «Отложить» закрыт
        assertTrue(game.summaryOpen(s))
    }

    @Test fun `копилка до плана — только посмотреть, шагов не закрывает`() {
        var s = game.seeAnnouncement(game.openParcel(newGame()))
        s = game.leavePiggy(s)
        assertFalse(s.week!!.piggyVisited)
        s = game.leaveShop(game.confirmPlan(s))
        assertEquals(Step.SHOP, game.nextStep(s))
    }

    @Test fun `ничего не покупал всю неделю — итог доступен, неделя кончается`() {
        var s = toPlan(newGame())
        s = game.leavePiggy(game.deposit(game.leaveShop(s)))
        assertTrue(game.summaryOpen(s))
        s = game.finishWeek(s, SummaryChoice.KEEP_PLAN)
        assertEquals(Phase.AFTER_SUMMARY, s.phase)
        assertEquals(Step.NEXT_WEEK, game.nextStep(s))
    }

    @Test fun `подписи экрана цели — ровно и не хватит при взносе 10`() {
        val s = game.createPet(game.seeIntro(GameState()), "", Fur.GINGER, Accessory.SCARF)
        // 10 + 5 дважды = 30: дешёвая цель — ровно, средняя и дорогая — не хватит (I81).
        assertEquals(Enough.EXACT, game.enoughPreview(s, "podarok_myach"))
        assertEquals(Enough.SHORT, game.enoughPreview(s, "podarok_kniga"))
        assertEquals(Enough.SHORT, game.enoughPreview(s, "podarok_samokat"))
    }

    @Test fun `E14 строка под Копилкой считается, а не зашита`() {
        val s = game.seeAnnouncement(game.openParcel(newGame("podarok_kniga")))
        assertEquals(Enough.SHORT, game.enoughForGoal(s))                  // 10 + 5 дважды = 30 < 40
        assertEquals(Enough.EXACT, game.enoughForGoal(game.setPlan(s, Plan(10, 0, 15))))   // 15 + 5 дважды = 40
        assertEquals(Enough.SURPLUS, game.enoughForGoal(game.setPlan(s, Plan(8, 0, 17))))
        // Дорогая цель 45: при взносе 15 не хватит, при 18 — с запасом (18 + 5 дважды = 46).
        val exp = game.seeAnnouncement(game.openParcel(newGame("podarok_samokat")))
        assertEquals(Enough.SHORT, game.enoughForGoal(game.setPlan(exp, Plan(10, 0, 15))))
        assertEquals(Enough.SURPLUS, game.enoughForGoal(game.setPlan(exp, Plan(7, 0, 18))))
    }

    @Test fun `строка под Копилкой не считает сделанный взнос и выданную награду дважды`() {
        val s = canonicalWeek1("podarok_myach")                             // копилка 15, взнос и F1 уже внутри
        assertEquals(15, game.savingsAtEvent(s, 10) - 15)                   // впереди только неделя 2: 10 + 5
        val w2 = toPlan(game.nextWeek(game.finishWeek(s, SummaryChoice.KEEP_PLAN)))
        assertEquals(Enough.EXACT, game.enoughForGoal(w2))                   // 15 + 10 + 5 = 30
    }

    @Test fun `QA-B1 после итога и после события деньги не тратятся`() {
        var s = toPlan(newGame(), Plan(10, 5, 10))
        s = game.finishWeek(skipShop(s), SummaryChoice.KEEP_PLAN) // взнос не сделан, еда не куплена
        assertFalse(game.canDeposit(s))
        assertThrows { game.buy(s, listOf("kasha")) }
        assertThrows { game.deposit(s) }
        s = game.finishWeek(skipShop(toPlan(game.nextWeek(s))), SummaryChoice.KEEP_PLAN)
        s = game.finishWeek(skipShop(toPlan(game.nextWeek(s))), SummaryChoice.KEEP_PLAN) // запасная неделя
        s = game.playEvent(s)
        assertThrows { game.buy(s, listOf("kacheli")) }
        assertThrows { game.chooseBall(s, true) }
        assertFalse(game.canDeposit(s))
    }

    @Test fun `QA-B6 объяснение не повторяет предмет, купленный дважды`() {
        val ex = ru.vinteno.finni.core.engine.Explain(game)
        assertEquals("Мы купили крупу и мыло", ex.did(listOf("krupa", "mylo", "krupa")))
    }

    @Test fun `QA-M1 полка закрыта до конца недели после покупки`() {
        var s = toPlan(newGame(), Plan(20, 0, 5))
        s = game.buy(s, listOf("krupa"))
        assertEquals(setOf("food"), game.boughtShelves(s))
        val w = s.progress.wallet
        val q = game.quote(s, listOf("kasha", "mylo"))
        assertEquals(listOf("mylo"), q.items.map { it.id }) // каша с закрытой полки не считается
        s = game.buy(s, listOf("kasha", "mylo"))
        assertEquals(w - 3, s.progress.wallet)
        assertThrows { game.buy(s, listOf("krupa")) }            // пустая корзина — покупки нет
        s = game.finishWeek(game.leaveShop(s), SummaryChoice.KEEP_PLAN)
        s = toPlan(game.nextWeek(s))
        assertTrue(game.boughtShelves(s).isEmpty())               // новая неделя — полки снова открыты
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalMove) {
            return
        }
        throw AssertionError("Ожидался запрет хода")
    }
}
