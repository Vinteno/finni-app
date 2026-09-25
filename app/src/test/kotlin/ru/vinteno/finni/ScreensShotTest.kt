package ru.vinteno.finni

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.vinteno.finni.core.content.Content
import ru.vinteno.finni.core.engine.Game
import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.model.Fur
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.Plan
import ru.vinteno.finni.core.model.SummaryChoice
import ru.vinteno.finni.data.GameStore
import ru.vinteno.finni.ui.AppModel
import ru.vinteno.finni.ui.LocalApp
import ru.vinteno.finni.ui.motion.FlightLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import ru.vinteno.finni.ui.screens.CreatePetScreen
import ru.vinteno.finni.ui.screens.EventScreen
import ru.vinteno.finni.ui.screens.GoalScreen
import ru.vinteno.finni.ui.screens.HomeScreen
import ru.vinteno.finni.ui.screens.IntroScreen
import ru.vinteno.finni.ui.screens.PiggyScreen
import ru.vinteno.finni.ui.screens.PlanScreen
import ru.vinteno.finni.ui.screens.ShopScreen
import ru.vinteno.finni.ui.screens.SummaryScreen
import ru.vinteno.finni.ui.theme.FinniColors

/**
 * Снимки экранов канонического пути главы 1 на 360 × 600 dp — окно игры на телефоне 360 × 640
 * за вычетом системных полос — обычным шрифтом и ×2,0 (гайд §6.5, чек-лист 9–10). Анимации выключены: снимок — конечное состояние.
 * Запись: ./gradlew :app:recordRoborazziDebug, снимки — app/build/shots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h600dp-xxhdpi")
class ScreensShotTest {
    @get:Rule val compose = createComposeRule()

    private val game = Game(Content.fromResources())

    private fun base(): GameState {
        var s = GameState()
        s = game.seeIntro(s)
        s = game.createPet(s, "Бублик", Fur.BLUE, Accessory.CAP)
        return s.copy(profile = s.profile.copy(animationOn = false))
    }

    private fun week1(): GameState = game.chooseGoal(base(), "podarok_kniga")
    private fun planned(s: GameState, p: Plan? = null): GameState {
        var x = game.seeAnnouncement(game.openParcel(s))
        if (p != null) x = game.setPlan(x, p)
        return x
    }

    private fun shot(name: String, state: GameState, scale: Float = 1f, content: @Composable (GameState) -> Unit) {
        val store = GameStore(RuntimeEnvironment.getApplication())
        store.replace(state)
        val model = AppModel(game, store)
        compose.setContent {
            val s by store.state.collectAsState()
            val d = LocalDensity.current
            CompositionLocalProvider(LocalApp provides model, LocalDensity provides Density(d.density, scale)) {
                Box(Modifier.fillMaxSize().background(FinniColors.BgSand)) { content(s) }
            }
        }
        compose.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @Test fun intro() = shot("01_intro", GameState()) { IntroScreen() }
    @Test fun create() = shot("02_create", game.seeIntro(GameState())) { CreatePetScreen() }
    @Test fun goal() = shot("03_goal", base()) { GoalScreen() }
    @Test fun homeParcel() = shot("04_home_parcel", week1()) { HomeScreen(it) {} }
    @Test fun homeAnnounce() = shot("05_home_announce", game.openParcel(week1())) { HomeScreen(it) {} }
    @Test fun homeAnnounceBig() = shot("05b_home_announce_x2", game.openParcel(week1()), 2f) { HomeScreen(it) {} }
    @Test fun plan() = shot("06_plan", planned(week1())) { PlanScreen(it, {}, {}) }
    @Test fun planOver() = shot("07_plan_over", planned(week1(), Plan(14, 10, 10))) { PlanScreen(it, {}, {}) }
    @Test fun planOverBig() = shot("07b_plan_over_x2", planned(week1(), Plan(14, 10, 10)), 2f) { PlanScreen(it, {}, {}) }
    @Test fun shop() = shot("08_shop", game.confirmPlan(planned(week1()))) { ShopScreen(it) {} }
    @Test fun shopBig() = shot("08b_shop_x2", game.confirmPlan(planned(week1())), 2f) { ShopScreen(it) {} }
    @Test fun homeCare() = shot("09_home_care", game.leaveShop(game.buy(game.confirmPlan(planned(week1())), listOf("kasha", "yagody", "mylo"), agreedWant = true))) { HomeScreen(it) {} }
    // Куплена крупа — в миске крупа, а не каша (без ягод: они только на каше).
    @Test fun homeKrupa() = shot("09c_home_krupa", game.leaveShop(game.buy(game.confirmPlan(planned(week1())), listOf("krupa", "mylo")))) { HomeScreen(it) {} }
    // Высокий экран: фон комнаты по полу, окно над Финни.
    @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    @Test fun homeTall() = shot("09d_home_tall", game.leaveShop(game.buy(game.confirmPlan(planned(week1())), listOf("kasha", "mylo")))) { HomeScreen(it) {} }
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    // Исход «не хватило» — строк больше: на телефоне 360 × 640 всё в один ряд и без прокрутки.
    @Test fun eventPhone() = shot("13c_event_b_640", run {
        var s = game.deposit(game.leaveShop(week2()))
        s = game.chooseBall(s, true)
        game.finishWeek(s, SummaryChoice.KEEP_PLAN)
    }) { EventScreen(it) {} }
    @Test fun homeBig() = shot("09b_home_x2", game.leaveShop(game.buy(game.confirmPlan(planned(week1())), listOf("kasha", "mylo"))), 2f) { HomeScreen(it) {} }

    private fun week1Done(): GameState {
        var s = game.confirmPlan(planned(week1()))
        s = game.leaveShop(game.buy(s, listOf("kasha", "yagody", "mylo"), agreedWant = true))
        s = game.wash(game.feed(s))
        return s
    }

    @Test fun piggy() = shot("10_piggy", week1Done()) { PiggyScreen(it) {} }
    @Test fun summary() = shot("11_summary", game.leavePiggy(game.deposit(week1Done()))) { SummaryScreen(it, {}, {}) }
    /** На высоком телефоне кнопки итога стоят внизу экрана, хотя на низком они прокручиваются вместе с текстом. */
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    @Test fun summaryTall() = shot("11c_summary_tall", game.leavePiggy(game.deposit(week1Done()))) { SummaryScreen(it, {}, {}) }
    @Test fun summaryBig() = shot("11b_summary_x2", game.leavePiggy(game.deposit(week1Done())), 2f) { SummaryScreen(it, {}, {}) }

    private fun week2(): GameState {
        val s = game.finishWeek(game.leavePiggy(game.deposit(week1Done())), SummaryChoice.KEEP_PLAN)
        return game.confirmPlan(planned(game.nextWeek(s)))
    }

    /** Задание F5 — на копилке после взноса недели 2 (QA-M3). */
    @Test fun piggyF5() = shot("12_piggy_f5", game.deposit(game.leaveShop(week2()))) { PiggyScreen(it) {} }
    @Test fun event() = shot("13_event", run {
        var s = game.deposit(game.leaveShop(week2()))
        s = game.chooseBall(s, false)
        game.finishWeek(s, SummaryChoice.KEEP_PLAN)
    }) { EventScreen(it) {} }
    @Test fun eventB() = shot("13b_event_b", run {
        var s = game.deposit(game.leaveShop(week2()))
        s = game.chooseBall(s, true)
        game.finishWeek(s, SummaryChoice.KEEP_PLAN)
    }) { EventScreen(it) {} }

    /** Окна нехватки: сначала «Хочу», потом копилка — порядок один для любой покупки (E10). */
    @Test fun dialogWant() {
        shot("14_dialog_want", game.confirmPlan(planned(week1()))) { ShopScreen(it) {} }
        listOf("Каша с ягодами", "Мыло").forEach { compose.onNodeWithContentDescription(it).performClick() }
        compose.onNodeWithText("Купить").performClick()
        compose.onRoot().captureRoboImage("build/shots/14_dialog_want.png")
    }

    @Test fun dialogSavings() {
        val s = game.leaveShop(game.confirmPlan(planned(week1(), Plan(4, 0, 10))))
        shot("15_dialog_savings", s) { ShopScreen(it) {} }
        listOf("Каша", "Мыло").forEach { compose.onNodeWithContentDescription(it).performClick() }
        compose.onNodeWithText("Купить").performClick()
        compose.onRoot().captureRoboImage("build/shots/15_dialog_savings.png")
    }

    /** Посылка с анимациями: монеты летят, кошелёк растёт вместе с прилётом, касание досматривает. */
    @Test fun parcelFlight() {
        val s = week1().let { it.copy(profile = it.profile.copy(animationOn = true)) }
        val store = GameStore(RuntimeEnvironment.getApplication())
        store.replace(s)
        val model = AppModel(game, store)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val st by store.state.collectAsState()
            CompositionLocalProvider(LocalApp provides model) {
                Box(Modifier.fillMaxSize().background(FinniColors.BgSand)) {
                    HomeScreen(st) {}
                    FlightLayer()
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Открой посылку").performClick()
        compose.mainClock.advanceTimeBy(16)
        assertEquals(6, model.flights.flights.size)
        assertEquals(6, model.flights.walletPending)
        compose.mainClock.advanceTimeBy(200)
        compose.onRoot().captureRoboImage("build/shots/16_parcel_flight.png")
        compose.mainClock.advanceTimeBy(1200)
        assertEquals(0, model.flights.walletPending)
        assertEquals(30, store.state.value.progress.wallet)
    }

    /**
     * Кормление (animation-howto §6.4, §7.3): еда в миске, пока Финни прыгает к ней и ест, после «ест»
     * гаснет за 200 мс — пустая миска остаётся на месте. Без анимаций еда исчезает сразу.
     */
    @Test fun feedFood() {
        val bought = game.leaveShop(game.buy(game.confirmPlan(planned(week1())), listOf("kasha", "yagody", "mylo"), agreedWant = true))
        val store = GameStore(RuntimeEnvironment.getApplication())
        store.replace(bought.copy(profile = bought.profile.copy(animationOn = true)))
        val model = AppModel(game, store)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val st by store.state.collectAsState()
            CompositionLocalProvider(LocalApp provides model) {
                Box(Modifier.fillMaxSize().background(FinniColors.BgSand)) { HomeScreen(st) {} }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Покорми").performClick()
        // Прыжки к миске: в игре уже сыт, на экране еда ещё лежит.
        compose.mainClock.advanceTimeBy(500)
        assertEquals(true, store.state.value.week!!.fed)
        compose.onRoot().captureRoboImage("build/shots/17a_feed_hop.png")
        // Конец «ест» и середина угасания.
        compose.mainClock.advanceTimeBy(1000)
        compose.onRoot().captureRoboImage("build/shots/17b_feed_fade.png")
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/shots/17c_feed_done.png")
    }

    @Test fun feedNoAnimation() {
        shot("17d_feed_no_anim", game.leaveShop(game.buy(game.confirmPlan(planned(week1())), listOf("krupa", "mylo")))) { HomeScreen(it) {} }
        compose.onNodeWithText("Покорми").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/17d_feed_no_anim.png")
    }

    // ---------- Экраны главы 1 по макету (I40): три размера окна и шрифт ×2,0 ----------
    // Окно телефона 360 × 640 — 360 × 600 без строки состояния; 360 × 800 — 360 × 760; 412 × 915 — 412 × 875.
    // Снимки — build/shots/screens/; у каждого — проверка зон нажатия: не меньше 48 × 48 dp, не пересекаются.

    private fun week1Goal(goal: String): GameState = game.chooseGoal(base(), goal)

    private fun week2(goal: String): GameState {
        var s = game.confirmPlan(planned(week1Goal(goal)))
        s = game.wash(game.feed(game.leaveShop(game.buy(s, listOf("kasha", "mylo")))))
        s = game.finishWeek(game.leavePiggy(game.deposit(s)), SummaryChoice.KEEP_PLAN)
        return game.confirmPlan(planned(game.nextWeek(s)))
    }

    private fun f5(goal: String) = game.deposit(game.leaveShop(week2(goal)))

    private fun outcomeA(): GameState {
        var s = game.deposit(game.leaveShop(week2()))
        s = game.chooseBall(s, false)
        return game.finishWeek(s, SummaryChoice.KEEP_PLAN)
    }

    private fun outcomeB(): GameState {
        var s = game.deposit(game.leaveShop(week2()))
        s = game.chooseBall(s, true)
        return game.finishWeek(s, SummaryChoice.KEEP_PLAN)
    }

    private fun screen(name: String, state: GameState, scale: Float = 1f, act: (() -> Unit)? = null, content: @Composable (GameState) -> Unit) {
        shot("screens/$name", state, scale, content)
        if (act != null) {
            act()
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/shots/screens/$name.png")
        }
        zones(name)
    }

    private fun zones(name: String) {
        val dp = compose.density.density
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        nodes.forEach { n ->
            assertTrue("$name: «${label(n)}» ${n.size.width / dp}×${n.size.height / dp} dp", n.size.width / dp >= 47.5f && n.size.height / dp >= 47.5f)
        }
        val z = nodes.map { label(it) to it.boundsInRoot }.filter { it.second.width > 0f && it.second.height > 0f }
        for (i in z.indices) for (j in i + 1 until z.size) {
            val x = z[i].second.intersect(z[j].second)
            assertTrue("$name: пересекаются «${z[i].first}» и «${z[j].first}»", x.width <= 0.5f || x.height <= 0.5f)
        }
    }

    private fun label(n: SemanticsNode): String =
        (n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).joinToString(" ")

    private fun pickBook() { compose.onNodeWithText("Книжка", substring = true).performClick() }
    private fun pickFood() { listOf("Каша", "Мыло").forEach { compose.onNodeWithContentDescription(it).performClick() } }
    private fun pickAll() { listOf("Каша с ягодами", "Мыло", "Качели").forEach { compose.onNodeWithContentDescription(it).performClick() } }

    // Выбор цели
    @Test fun sGoal600() = screen("03_goal_360x600", base()) { GoalScreen() }
    @Test fun sGoalPicked600() = screen("03_goal_picked_360x600", base(), act = ::pickBook) { GoalScreen() }
    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun sGoal800() = screen("03_goal_360x800", base(), act = ::pickBook) { GoalScreen() }
    @Config(qualifiers = "w412dp-h875dp-xxhdpi") @Test fun sGoal915() = screen("03_goal_412x915", base(), act = ::pickBook) { GoalScreen() }
    @Test fun sGoalBig() = screen("03_goal_360x600_x2", base(), 2f, act = ::pickBook) { GoalScreen() }

    // План
    @Test fun sPlan600() = screen("04_plan_360x600", planned(week1())) { PlanScreen(it, {}, {}) }
    @Test fun sPlanOver600() = screen("04_plan_over_360x600", planned(week1(), Plan(14, 10, 10))) { PlanScreen(it, {}, {}) }
    @Test fun sPlanZero600() = screen("04_plan_need0_360x600", planned(week1(), Plan(0, 20, 10))) { PlanScreen(it, {}, {}) }
    @Test fun sPlanReady600() = screen("04_plan_ready_360x600", planned(week1().let { it.copy(progress = it.progress.copy(savings = 40)) })) { PlanScreen(it, {}, {}) }
    @Test fun sPlanMax600() = screen("04_plan_max_360x600", planned(week1(), Plan(45, 44, 10))) { PlanScreen(it, {}, {}) }
    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun sPlan800() = screen("04_plan_360x800", planned(week1())) { PlanScreen(it, {}, {}) }
    @Config(qualifiers = "w412dp-h875dp-xxhdpi") @Test fun sPlan915() = screen("04_plan_412x915", planned(week1(), Plan(14, 10, 10))) { PlanScreen(it, {}, {}) }
    @Test fun sPlanBig() = screen("04_plan_over_360x600_x2", planned(week1(), Plan(14, 10, 10)), 2f) { PlanScreen(it, {}, {}) }

    // Магазин
    private fun shop1() = game.confirmPlan(planned(week1()))
    private fun shop2() = week2()
    private fun shop2OneShelf() = game.buy(week2(), listOf("kasha"))
    @Test fun sShop600() = screen("05_shop_360x600", shop1()) { ShopScreen(it) {} }
    @Test fun sShopPicked600() = screen("05_shop_picked_360x600", shop1(), act = ::pickFood) { ShopScreen(it) {} }
    @Test fun sShopFull600() = screen("05_shop_full_360x600", shop1(), act = ::pickAll) { ShopScreen(it) {} }
    @Test fun sShopWeek2() = screen("05_shop_week2_360x600", shop2()) { ShopScreen(it) {} }
    @Test fun sShopWeek2One() = screen("05_shop_week2_one_360x600", shop2OneShelf()) { ShopScreen(it) {} }
    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun sShop800() = screen("05_shop_360x800", shop1(), act = ::pickFood) { ShopScreen(it) {} }
    @Config(qualifiers = "w412dp-h875dp-xxhdpi") @Test fun sShop915() = screen("05_shop_412x915", shop1(), act = ::pickFood) { ShopScreen(it) {} }
    @Test fun sShopBig() = screen("05_shop_360x600_x2", shop1(), 2f, act = ::pickFood) { ShopScreen(it) {} }

    // Копилка
    @Test fun sPiggy600() = screen("06_piggy_360x600", week1Done()) { PiggyScreen(it) {} }
    @Test fun sPiggyWeek2() = screen("06_piggy_week2_360x600", game.leaveShop(week2())) { PiggyScreen(it) {} }
    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun sPiggy800() = screen("06_piggy_360x800", week1Done()) { PiggyScreen(it) {} }
    @Config(qualifiers = "w412dp-h875dp-xxhdpi") @Test fun sPiggy915() = screen("06_piggy_412x915", week1Done()) { PiggyScreen(it) {} }
    @Test fun sPiggyBig() = screen("06_piggy_360x600_x2", week1Done(), 2f) { PiggyScreen(it) {} }

    // Мячик или подарок
    @Test fun sF5Book() = screen("07_f5_goal40_360x600", f5("podarok_kniga")) { PiggyScreen(it) {} }
    @Test fun sF5Ball() = screen("07_f5_goal30_360x600", f5("podarok_myach")) { PiggyScreen(it) {} }
    @Test fun sF5Scooter() = screen("07_f5_goal45_360x600", f5("podarok_samokat")) { PiggyScreen(it) {} }
    @Test fun sF5Few() = screen("07_f5_few_360x600", f5("podarok_kniga").let { it.copy(progress = it.progress.copy(savings = 10)) }) { PiggyScreen(it) {} }
    @Test fun sF5After() = screen("07_f5_after_360x600", f5("podarok_kniga"), act = { compose.onNodeWithText("Взять мячик").performClick() }) { PiggyScreen(it) {} }
    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun sF5800() = screen("07_f5_360x800", f5("podarok_kniga")) { PiggyScreen(it) {} }
    @Config(qualifiers = "w412dp-h875dp-xxhdpi") @Test fun sF5915() = screen("07_f5_412x915", f5("podarok_samokat")) { PiggyScreen(it) {} }
    @Test fun sF5Big() = screen("07_f5_360x600_x2", f5("podarok_kniga"), 2f) { PiggyScreen(it) {} }

    // Итог недели
    private fun summaryState() = game.leavePiggy(game.deposit(week1Done()))
    @Test fun sSummary600() = screen("08_summary_360x600", summaryState()) { SummaryScreen(it, {}, {}) }
    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun sSummary800() = screen("08_summary_360x800", summaryState()) { SummaryScreen(it, {}, {}) }
    @Config(qualifiers = "w412dp-h875dp-xxhdpi") @Test fun sSummary915() = screen("08_summary_412x915", summaryState()) { SummaryScreen(it, {}, {}) }
    @Test fun sSummaryBig() = screen("08_summary_360x600_x2", summaryState(), 2f) { SummaryScreen(it, {}, {}) }

    // Событие
    @Test fun sEventA600() = screen("09_event_a_360x600", outcomeA()) { EventScreen(it) {} }
    @Test fun sEventB600() = screen("09_event_b_360x600", outcomeB()) { EventScreen(it) {} }
    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun sEvent800() = screen("09_event_b_360x800", outcomeB()) { EventScreen(it) {} }
    @Config(qualifiers = "w412dp-h875dp-xxhdpi") @Test fun sEvent915() = screen("09_event_a_412x915", outcomeA()) { EventScreen(it) {} }
    @Test fun sEventBig() = screen("09_event_b_360x600_x2", outcomeB(), 2f) { EventScreen(it) {} }

    /**
     * Касание у края вещи рядом с соседней попадает в свою вещь (I40): на полке — у правого края
     * крупы, рядом с кашей; у банок — у правого края «+» «Нужного», рядом с «−» «Хочу».
     */
    @Test fun edgeTapsShelf() {
        shot("screens/edge_shop", game.confirmPlan(planned(week1()))) { ShopScreen(it) {} }
        val krupa = compose.onNodeWithContentDescription("Крупа")
        val b = krupa.fetchSemanticsNode().boundsInRoot
        krupa.performTouchInput { click(androidx.compose.ui.geometry.Offset(width - 2f, height / 2f)) }
        compose.waitForIdle()
        // После выбора «Крупа» есть и в корзине — первая по порядку та, что на полке.
        assertEquals(true, compose.onAllNodesWithContentDescription("Крупа")[0].fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        assertEquals(false, compose.onNodeWithContentDescription("Каша").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        assertTrue(b.width > 0f)
    }

    @Test fun edgeTapsJars() {
        val store = GameStore(RuntimeEnvironment.getApplication())
        store.replace(planned(week1()))
        val model = AppModel(game, store)
        compose.setContent {
            val s by store.state.collectAsState()
            CompositionLocalProvider(LocalApp provides model) { PlanScreen(s, {}, {}) }
        }
        val plus = compose.onAllNodesWithContentDescription("Добавить монету")[0]
        plus.performTouchInput { click(androidx.compose.ui.geometry.Offset(width - 2f, height / 2f)) }
        compose.waitForIdle()
        assertEquals(11, store.state.value.week!!.plan.need)
        assertEquals(10, store.state.value.week!!.plan.want)
    }
}
