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
        listOf("Каша с\u00A0ягодами", "Мыло", "Купить").forEach { compose.onNodeWithText(it).performClick() }
        compose.onRoot().captureRoboImage("build/shots/14_dialog_want.png")
    }

    @Test fun dialogSavings() {
        val s = game.leaveShop(game.confirmPlan(planned(week1(), Plan(4, 0, 10))))
        shot("15_dialog_savings", s) { ShopScreen(it) {} }
        listOf("Каша", "Мыло", "Купить").forEach { compose.onNodeWithText(it).performClick() }
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
}
