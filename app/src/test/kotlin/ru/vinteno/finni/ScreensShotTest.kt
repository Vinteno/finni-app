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
import androidx.compose.ui.test.onAllNodesWithText
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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
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
    /** Посылка и объявление; без [p] черновик дополняется до кошелька в «Хочу» — недобор не подтвердить (I45). */
    private fun planned(s: GameState, p: Plan? = null): GameState {
        val x = game.seeAnnouncement(game.openParcel(s))
        val plan = p ?: x.week!!.plan.let { it.copy(want = it.want + x.progress.wallet - it.total) }
        return game.setPlan(x, plan)
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
        s = game.leavePiggy(game.chooseBall(s, true))
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
        s = game.leavePiggy(game.chooseBall(s, false))
        game.finishWeek(s, SummaryChoice.KEEP_PLAN)
    }) { EventScreen(it) {} }
    @Test fun eventB() = shot("13b_event_b", run {
        var s = game.deposit(game.leaveShop(week2()))
        s = game.leavePiggy(game.chooseBall(s, true))
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
        val s = savingsShop()
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
        s = game.leavePiggy(game.chooseBall(s, false))
        return game.finishWeek(s, SummaryChoice.KEEP_PLAN)
    }

    private fun outcomeB(): GameState {
        var s = game.deposit(game.leaveShop(week2()))
        s = game.leavePiggy(game.chooseBall(s, true))
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

    private fun zones(name: String, which: SemanticsMatcher = hasClickAction()) {
        val dp = compose.density.density
        val nodes = compose.onAllNodes(which).fetchSemanticsNodes()
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

    // ---------- Вступление, создание Финни, окна нехватки (I43, I44) ----------

    private fun card(n: Int) = { repeat(n) { compose.onNodeWithText("Дальше").performClick() } }

    // Вступление: три карточки; пол и предмет не прыгают между ними — плашка держит место под самую длинную строку.
    @Test fun sIntroNeed600() = screen("01_intro_need_360x600", GameState()) { IntroScreen() }
    @Test fun sIntroWant600() = screen("01_intro_want_360x600", GameState(), act = card(1)) { IntroScreen() }
    @Test fun sIntroSave600() = screen("01_intro_save_360x600", GameState(), act = card(2)) { IntroScreen() }
    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun sIntro800() = screen("01_intro_360x800", GameState(), act = card(1)) { IntroScreen() }
    @Config(qualifiers = "w412dp-h875dp-xxhdpi") @Test fun sIntro915() = screen("01_intro_412x915", GameState(), act = card(2)) { IntroScreen() }
    @Test fun sIntroNeedBig() = screen("01_intro_need_360x600_x2", GameState(), 2f) { IntroScreen() }
    @Test fun sIntroWantBig() = screen("01_intro_want_360x600_x2", GameState(), 2f, act = card(1)) { IntroScreen() }

    // Создание Финни: по умолчанию, готовое имя, открыт ввод, введено своё, своё на табличке, пустой ввод.
    private fun fresh() = game.seeIntro(GameState()).let { it.copy(profile = it.profile.copy(animationOn = false)) }
    private fun own() { compose.onNodeWithText("Своё").performClick() }
    private fun typeKuzya() { own(); compose.onNode(hasSetTextAction()).performTextInput("Кузя 2") }
    @Test fun sCreate600() = screen("02_create_360x600", fresh()) { CreatePetScreen() }
    @Test fun sCreateNamed600() = screen("02_create_named_360x600", fresh(), act = { compose.onNodeWithText("Пушок").performClick() }) { CreatePetScreen() }
    @Test fun sCreateTyping600() = screen("02_create_typing_360x600", fresh(), act = ::own) { CreatePetScreen() }
    @Test fun sCreateTyped600() = screen("02_create_typed_360x600", fresh(), act = ::typeKuzya) { CreatePetScreen() }
    @Test fun sCreateOwn600() = screen("02_create_own_360x600", fresh(), act = { typeKuzya(); compose.onNode(hasSetTextAction()).performImeAction() }) { CreatePetScreen() }
    @Test fun sCreateEmpty600() = screen("02_create_empty_360x600", fresh(), act = {
        compose.onNodeWithText("Пушок").performClick()
        own()
        compose.onNode(hasSetTextAction()).performImeAction()
    }) { CreatePetScreen() }
    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun sCreate800() = screen("02_create_360x800", fresh(), act = { compose.onNodeWithText("Бублик").performClick() }) { CreatePetScreen() }
    @Config(qualifiers = "w412dp-h875dp-xxhdpi") @Test fun sCreate915() = screen("02_create_412x915", fresh(), act = ::typeKuzya) { CreatePetScreen() }
    @Test fun sCreateBig() = screen("02_create_360x600_x2", fresh(), 2f) { CreatePetScreen() }
    @Test fun sCreateTypingBig() = screen("02_create_typing_360x600_x2", fresh(), 2f, act = ::typeKuzya) { CreatePetScreen() }

    // Открыта клавиатура: над ней на телефоне 360 × 640 остаётся около 300 dp. Финни 96 dp и поле — над ней,
    // лоток — под ней; на ×2,0 заголовок уходит вверх, поле и Финни видны.
    // «Своё» нажато ещё без клавиатуры, поэтому здесь — действием, а не касанием: на 300 dp кнопка уже под краем.
    private fun typeUnderKeyboard() {
        compose.onNodeWithText("Своё").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(hasSetTextAction()).performTextInput("Кузя")
    }
    @Config(qualifiers = "w360dp-h300dp-xxhdpi") @Test fun sCreateKeyboard() = screen("02_create_keyboard_360x300", fresh(), act = ::typeUnderKeyboard) { CreatePetScreen() }
    @Config(qualifiers = "w360dp-h300dp-xxhdpi") @Test fun sCreateKeyboardBig() = screen("02_create_keyboard_360x300_x2", fresh(), 2f, act = ::typeUnderKeyboard) { CreatePetScreen() }

    /** По умолчанию имя с таблички — «Финни». */
    @Test fun createDefaultName() {
        val store = GameStore(RuntimeEnvironment.getApplication())
        store.replace(fresh())
        val model = AppModel(game, store)
        compose.setContent { CompositionLocalProvider(LocalApp provides model) { CreatePetScreen() } }
        compose.onNodeWithText("Финни").assertExists()
        compose.onNodeWithText("Готово").performClick()
        compose.waitForIdle()
        assertEquals("Финни", store.state.value.profile.petName)
    }

    /** Своё имя — одно слово из букв и дефиса: пробел и цифра не входят; после «Готово» на клавиатуре — на табличке. */
    @Test fun createOwnName() {
        val store = GameStore(RuntimeEnvironment.getApplication())
        store.replace(fresh())
        val model = AppModel(game, store)
        compose.setContent { CompositionLocalProvider(LocalApp provides model) { CreatePetScreen() } }
        typeKuzya()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        // Поле снова табличка, на ней своё имя; «Своё» выбрано.
        assertEquals(0, compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        compose.onNodeWithText("Кузя").assertExists()
        compose.onNodeWithText("Готово").performClick()
        compose.waitForIdle()
        assertEquals("Кузя", store.state.value.profile.petName)
    }

    @Test fun createEmptyOwnKeepsName() {
        val store = GameStore(RuntimeEnvironment.getApplication())
        store.replace(fresh())
        val model = AppModel(game, store)
        compose.setContent { CompositionLocalProvider(LocalApp provides model) { CreatePetScreen() } }
        compose.onNodeWithText("Ушастик").performClick()
        own()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        // Табличка вернула прежнее имя: оно и на кнопке, и на табличке.
        assertEquals(2, compose.onAllNodesWithText("Ушастик").fetchSemanticsNodes().size)
        compose.onNodeWithText("Готово").performClick()
        compose.waitForIdle()
        assertEquals("Ушастик", store.state.value.profile.petName)
    }

    // Окна нехватки: «Хочу», копилка, мало монет. Зоны — только кнопок окна: экран под ним закрыт.
    // «Хочу» пусто, «Нужное» 4, в копилке взнос 26: добор 4 из копилки. Весь кошелёк разложен — иначе план не подтвердить (I45).
    private fun savingsShop() = game.leaveShop(game.deposit(game.confirmPlan(planned(week1(), Plan(4, 0, 26)))))
    private fun fewSavingsShop() = savingsShop().let { it.copy(progress = it.progress.copy(savings = 2)) }
    // На крупном шрифте полки прокручиваются: вещь сначала прокручивается в окно, потом нажимается.
    private fun buy(vararg items: String) {
        items.forEach { compose.onNodeWithContentDescription(it).performScrollTo().performClick() }
        compose.onNodeWithText("Купить").performClick()
    }
    private fun openWant() = buy("Каша с ягодами", "Мыло")
    private fun openSavings() = buy("Каша", "Мыло")

    private fun sheet(name: String, state: GameState, scale: Float = 1f, open: () -> Unit, buttons: Int) {
        shot("screens/$name", state, scale) { ShopScreen(it) {} }
        open()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/screens/$name.png")
        val inSheet = hasClickAction() and hasAnyAncestor(hasTestTag("dialog"))
        zones(name, inSheet)
        // Кнопки окна видны всегда, одного размера.
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val found = compose.onAllNodes(inSheet).fetchSemanticsNodes()
        assertEquals(name, buttons, found.size)
        found.forEach { assertTrue("$name: «${label(it)}» ниже экрана", it.boundsInRoot.bottom <= root.bottom + 0.5f) }
        assertTrue("$name: кнопки разного размера", found.map { it.size }.distinct().size == 1)
    }

    @Test fun sSheetWant600() = sheet("10_sheet_want_360x600", shop1(), open = ::openWant, buttons = 2)
    @Test fun sSheetSavings600() = sheet("10_sheet_savings_360x600", savingsShop(), open = ::openSavings, buttons = 2)
    @Test fun sSheetNone600() = sheet("10_sheet_none_360x600", fewSavingsShop(), open = ::openSavings, buttons = 1)
    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun sSheetWant800() = sheet("10_sheet_want_360x800", shop1(), open = ::openWant, buttons = 2)
    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun sSheetSavings800() = sheet("10_sheet_savings_360x800", savingsShop(), open = ::openSavings, buttons = 2)
    @Config(qualifiers = "w412dp-h875dp-xxhdpi") @Test fun sSheetWant915() = sheet("10_sheet_want_412x915", shop1(), open = ::openWant, buttons = 2)
    @Config(qualifiers = "w412dp-h875dp-xxhdpi") @Test fun sSheetNone915() = sheet("10_sheet_none_412x915", fewSavingsShop(), open = ::openSavings, buttons = 1)
    @Test fun sSheetWantBig() = sheet("10_sheet_want_360x600_x2", shop1(), 2f, open = ::openWant, buttons = 2)
    @Test fun sSheetSavingsBig() = sheet("10_sheet_savings_360x600_x2", savingsShop(), 2f, open = ::openSavings, buttons = 2)
    @Test fun sSheetNoneBig() = sheet("10_sheet_none_360x600_x2", fewSavingsShop(), 2f, open = ::openSavings, buttons = 1)

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
