package ru.vinteno.finni

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.vinteno.finni.core.content.Content
import ru.vinteno.finni.core.engine.Demo
import ru.vinteno.finni.core.engine.Game
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.Phase
import ru.vinteno.finni.core.model.Plan
import ru.vinteno.finni.data.GameStore
import ru.vinteno.finni.ui.AppModel
import ru.vinteno.finni.ui.FinniNavHost
import ru.vinteno.finni.ui.LocalApp

/**
 * Главы 2 и 3 по настоящей навигации — final-plan, блок G: неделя 3 с F4 и экраном ситуации, неделя 4 с F3
 * и первым снегом, переход в главу 3 и выбор цели, F6 на неделе 7, новоселье, конец игры, раздел
 * взрослого с демо и возвратом игры ребёнка. Снимки — build/shots/flow2/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h600dp-xxhdpi")
class ChaptersFlowTest {
    @get:Rule val compose = createComposeRule()

    private val game = Game(Content.fromResources())
    private val demo = Demo(game)
    private val store = GameStore(RuntimeEnvironment.getApplication())
    private val model = AppModel(game, store)
    private val s get() = store.state.value

    private fun quiet(s: GameState) = s.copy(profile = s.profile.copy(animationOn = false))

    private fun app(state: GameState) {
        store.replace(quiet(state))
        compose.setContent {
            val st by store.state.collectAsState()
            CompositionLocalProvider(LocalApp provides model) { FinniNavHost(st) }
        }
        idle()
    }

    private fun idle() { compose.mainClock.advanceTimeBy(2_000); compose.waitForIdle() }
    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/shots/flow2/$name.png")
    private fun plain(t: String) = t.replace(' ', ' ')
    private fun hasText(text: String, substring: Boolean = false) = SemanticsMatcher("текст «$text»") { n ->
        n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { t -> if (substring) text in plain(t.text) else plain(t.text) == text }
    }
    private fun tap(desc: String) { compose.onAllNodes(hasContentDescription(desc) and hasClickAction())[0].performClick(); idle() }
    private fun press(text: String) {
        val n = compose.onAllNodes(hasText(text) and hasClickAction())[0]
        // В разделе взрослого кнопка может быть ниже края: сначала докрутить.
        runCatching { n.performScrollTo() }
        n.performClick(); idle()
    }
    private fun shown(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun check(text: String) { if (!shown(text)) shot("FAIL_$text"); assertTrue("на экране «$text»", shown(text)) }

    private val jars = "Нужное, Хочу, Копилка"

    @Test fun chapterTwo() {
        // Неделя 3: посылка и объявление пройдены, план разложен — F4 на плане.
        val start = game.seeAnnouncement(game.openParcel(demo.weekStart(3)))
        app(game.setPlan(start, Plan(14, 10, 10)))
        assertTrue(game.cold(s))
        tap(jars); check("Сколько отложишь?"); shot("01_f4")
        press("Подтвердить план"); check("Откладываем 10 монет"); assertEquals(10, s.progress.savings); shot("02_f4_after")
        press("Домой")
        // Дверь после плана — экран ситуации; выбор кладёт куртку в корзину и ведёт в магазин.
        tap("Магазин"); check("Обе куртки одинаково тёплые"); shot("03_situation")
        tap("Куртка с рисунком"); check("Что возьмёшь на неделю?")
        assertEquals(1, s.week!!.situationPick)
        tap("Каша"); tap("Мыло"); shot("04_cart")
        press("Купить"); check("Купили кашу, мыло и куртку"); shot("05_bought")
        assertTrue("kurtka" in s.progress.inventory && "risunok" in s.progress.inventory)
        assertFalse(game.cold(s))
        press("Домой")
        tap("Миска"); tap("Мыло"); tap("Копилка"); press("Отложить 10"); press("Домой")
        assertEquals(20, s.progress.savings)
        tap("Неделя 3"); check("Мы взяли куртку с рисунком"); shot("06_summary")
        press("Оставить план"); assertEquals(Phase.AFTER_SUMMARY, s.phase)
        tap("Неделя 3"); assertEquals(2, s.week!!.number)

        // Неделя 4: заноза, F3 в корзине, первый снег.
        tap("Посылка"); press("Понятно"); check("Так бывает у всех"); shot("07_announce_zanoza"); press("Понятно")
        tap(jars)
        repeat(2) { compose.onAllNodes(hasContentDescription("Добавить монету") and hasClickAction())[0].performClick() }
        idle()
        press("Подтвердить план")
        tap("Магазин"); check("Лапа заживёт одинаково")
        tap("Лечение с бинтом"); check("Что в корзине?"); shot("08_f3")
        press("Купить"); check("Второй раз покупать не нужно"); shot("09_f3_after")
        assertTrue(s.week!!.taskDone); assertEquals(0, s.week!!.taskReward)
        press("Понятно")
        tap("Каша"); tap("Мыло"); press("Купить"); press("Домой")
        assertTrue("bint" in game.worn(s))
        tap("Миска"); tap("Мыло"); tap("Копилка"); press("Отложить 10"); press("Домой")
        tap("Неделя 4"); press("Оставить план")
        assertEquals(Phase.EVENT, s.phase); check("Выпал первый снег"); check("Я сплю на лежанке"); shot("10_snow")
        press("Дальше")
        // Переезд: плашка со строкой причины, затем цель главы 3.
        assertEquals(Phase.TRANSITION, s.phase); check("Мы переехали"); shot("11_move")
        press("Понятно"); check("Куда сложим вещи?")
        tap("Корзина для вещей"); press("Выбрать")
        assertEquals("korzina", s.chapter.goalId); assertEquals(5, game.weekNumber(s)); shot("12_ch3_home")
        assertTrue("lezhanka" in s.progress.inventory)
    }

    @Test fun payF2() {
        // Неделя 5: F2 — чем заплатить.
        app(game.confirmPlan(game.setPlan(game.seeAnnouncement(game.openParcel(demo.weekStart(5))), Plan(13, 19, 10))))
        tap("Магазин"); tap("Коробка"); tap("Каша"); tap("Мыло")
        press("Купить"); check("Чем заплатишь?"); shot("20_f2")
        press("Отдать 10 и взять сдачу"); check("Нам вернули 5"); shot("21_f2_after")
        assertEquals(29, s.progress.wallet); assertEquals(10, s.progress.savings)
    }

    @Test fun sortF6() {
        // Неделя 7: F6 касанием перед итогом.
        var w7 = game.confirmPlan(game.setPlan(game.seeAnnouncement(game.openParcel(demo.weekStart(7))), Plan(13, 11, 10)))
        w7 = game.chooseSituation(w7, 1)
        w7 = game.buy(w7, listOf("kasha", "mylo") + game.situationCart(w7))
        w7 = game.leavePiggy(game.deposit(game.leaveShop(w7)))
        app(game.wash(game.feed(w7)))
        tap("Неделя 7"); check("Куда ушли монеты?"); shot("22_f6")
        tap("Глазурь"); tap("Нужное"); check("Посмотри на значок")
        tap("Хочу")
        listOf("Каша", "Мыло", "Угощение").forEach { tap(it); tap("Нужное") }
        tap("Взнос"); tap("Копилка")
        press("Понятно"); check("На нужное ушло 13"); shot("23_f6_after")
        assertTrue(s.week!!.taskDone)
        press("Домой"); tap("Неделя 7"); check("Что задумали и что вышло")
    }

    @Test fun housewarmingAndEnd() {
        // Неделя 8 — новоселье и конец игры.
        app(demo.playWeek(demo.weekStart(8)))
        check("Пришла Кира"); check("Мы купили корзину"); shot("24_housewarming")
        press("Дальше"); check("Мы обжились"); shot("25_end")
        press("Играть дальше"); assertEquals(Phase.FREE_PLAY, s.phase); shot("26_free")
        tap("Магазин"); assertEquals(Phase.FREE_PLAY, s.phase)
    }

    @Test fun adultAndDemo() {
        // Игра ребёнка — не демо: из неё демо откладывается и возвращается.
        app(demo.weekStart(6).copy(demo = false))
        val child = s
        // Барьер — удержание 3 секунды; диктор открывает двойным касанием — так же проверяется здесь.
        compose.onAllNodes(hasContentDescription("Взрослым"))[0].performSemanticsAction(SemanticsActions.OnClick); idle()
        check("Для взрослого"); shot("30_adult")
        press("Добавить 5 монет в копилку"); assertEquals(child.progress.savings + 5, s.progress.savings)
        check("Бонус этой недели уже добавлен")
        press("Сложнее"); assertTrue(s.profile.senior)
        press("Начать демо"); press("Да")
        assertTrue(s.demo); check("На что копим Кире?"); shot("31_demo_goal")
        tap("Книжка для Киры"); press("Выбрать")
        compose.onAllNodes(hasContentDescription("Взрослым"))[0].performSemanticsAction(SemanticsActions.OnClick); idle()
        compose.onAllNodes(hasContentDescription("К неделе… 4") and hasClickAction())[0].let { runCatching { it.performScrollTo() }; it.performClick() }; idle()
        assertEquals(4, game.weekNumber(s)); assertEquals(2, s.progress.chapter)
        compose.onAllNodes(hasContentDescription("Взрослым"))[0].performSemanticsAction(SemanticsActions.OnClick); idle()
        press("Вернуться к игре ребёнка")
        assertFalse(s.demo); assertEquals(child.progress.wallet, s.progress.wallet); assertEquals(3, s.progress.chapter)
        compose.onAllNodes(hasContentDescription("Взрослым"))[0].performSemanticsAction(SemanticsActions.OnClick); idle()
        press("Удалить данные игры"); press("Да, удалить")
        assertFalse(s.profile.introSeen)
    }
}
