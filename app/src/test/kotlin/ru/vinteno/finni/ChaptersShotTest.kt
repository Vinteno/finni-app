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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
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
import ru.vinteno.finni.core.model.Plan
import ru.vinteno.finni.core.model.SummaryChoice
import ru.vinteno.finni.data.GameStore
import ru.vinteno.finni.ui.AppModel
import ru.vinteno.finni.ui.LocalApp
import ru.vinteno.finni.ui.components.RoomArt
import ru.vinteno.finni.ui.screens.AdultScreen
import ru.vinteno.finni.ui.screens.DiaryScreen
import ru.vinteno.finni.ui.screens.EndScreen
import ru.vinteno.finni.ui.screens.EventScreen
import ru.vinteno.finni.ui.screens.GoalScreen
import ru.vinteno.finni.ui.screens.HomeScreen
import ru.vinteno.finni.ui.screens.PiggyScreen
import ru.vinteno.finni.ui.screens.PlanScreen
import ru.vinteno.finni.ui.screens.ShopScreen
import ru.vinteno.finni.ui.screens.SituationScreen
import ru.vinteno.finni.ui.screens.SortScreen
import ru.vinteno.finni.ui.screens.SummaryScreen
import ru.vinteno.finni.ui.theme.FinniColors

/**
 * Снимки экранов глав 2 и 3, раздела взрослого и дневника — final-plan, блоки B–E. 360 × 600 dp обычным
 * шрифтом и ×2,0; состояния — начало недель канонического пути из демо. Анимации выключены. Снимки —
 * app/build/shots/ch/, смотреть глазами: вёрстка, переносы, шрифт ×2, число слов.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h600dp-xxhdpi")
class ChaptersShotTest {
    @get:Rule val compose = createComposeRule()

    private val game = Game(Content.fromResources())
    private val demo = Demo(game)

    private fun quiet(s: GameState) = s.copy(profile = s.profile.copy(animationOn = false))

    /** Начало недели [n] канонического пути, посылка и объявление пройдены, план — канонический. */
    private fun planned(n: Int, confirm: Boolean = true): GameState {
        var s = game.seeAnnouncement(game.openParcel(demo.weekStart(n)))
        val need = 8 + (game.situationShelf(s)?.tiers?.first()?.sumOf { game.content.item(it).price } ?: 0)
        s = game.setPlan(s, Plan(need, s.progress.wallet - need - 10, 10))
        return quiet(if (confirm) game.confirmPlan(s) else s)
    }

    /** Неделя [n]: ситуация с надбавкой, еда и мыло куплены. */
    private fun bought(n: Int): GameState {
        var s = planned(n)
        if (game.duplicatePending(s)) s = game.resolveDuplicate(s)
        s = game.chooseSituation(s, 1)
        val cart = listOf("kasha", "mylo") + game.situationCart(s)
        val q = game.quote(s, cart)
        s = game.buy(s, cart, agreedWant = true, pay = if (game.asksPay(s, q)) ru.vinteno.finni.core.model.PayChoice.CHANGE else null)
        return quiet(game.leaveShop(s))
    }

    private fun shot(name: String, state: GameState, scale: Float = 1f, content: @Composable (GameState) -> Unit) {
        val store = GameStore(RuntimeEnvironment.getApplication())
        store.replace(state)
        RoomArt.name = RoomArt.of(state.progress.chapter, "lampa" in state.progress.inventory)
        val model = AppModel(game, store)
        compose.setContent {
            val s by store.state.collectAsState()
            val d = LocalDensity.current
            CompositionLocalProvider(LocalApp provides model, LocalDensity provides Density(d.density, scale)) {
                Box(Modifier.fillMaxSize().background(FinniColors.BgSand)) { content(s) }
            }
        }
        compose.onRoot().captureRoboImage("build/shots/ch/$name.png")
    }

    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/shots/ch/$name.png")

    /** Текст на экране — как его видит ребёнок: неразрывный пробел (typo) — тот же пробел. */
    private fun shown(text: String) = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("текст «$text»") { n ->
        n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }.orEmpty().any { text in it.text.replace('\u00A0', ' ') }
    }).fetchSemanticsNodes().isNotEmpty()

    // ---------- Переход и цели ----------
    private fun transition(week: Int) = quiet(game.playEvent(demo.playWeek(demo.weekStart(week))))

    @Test fun transition2() = shot("20_transition_ch2", transition(2)) { HomeScreen(it) {} }
    @Test fun transition3() = shot("21_transition_ch3", transition(4)) { HomeScreen(it) {} }
    @Test fun transition3Big() = shot("21b_transition_ch3_x2", transition(4), 2f) { HomeScreen(it) {} }
    @Test fun goal2() = shot("22_goal_ch2", game.seeTransition(transition(2))) { GoalScreen() }
    @Test fun goal3() = shot("23_goal_ch3", game.seeTransition(transition(4))) { GoalScreen() }
    @Test fun goal3Big() = shot("23b_goal_ch3_x2", game.seeTransition(transition(4)), 2f) { GoalScreen() }

    // ---------- Дом ----------
    @Test fun homeCold() = shot("24_home_ch2_cold", quiet(demo.weekStart(3))) { HomeScreen(it) {} }
    @Test fun homeAnnounceKurtka() = shot("24b_home_ch2_announce", quiet(game.openParcel(demo.weekStart(3)))) { HomeScreen(it) {} }
    @Test fun homeAnnounceZanoza() = shot("24c_home_ch2_announce_x2", quiet(game.openParcel(demo.weekStart(4))), 2f) { HomeScreen(it) {} }
    @Test fun homeKurtka() = shot("25_home_ch2_kurtka", bought(4)) { HomeScreen(it) {} }
    @Test fun homeMove() = shot("26_home_ch3_start", quiet(demo.weekStart(5))) { HomeScreen(it) {} }
    @Test fun homeFull() = shot("27_home_ch3_w8", bought(8)) { HomeScreen(it) {} }
    @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    @Test fun homeFullTall() = shot("27b_home_ch3_w8_tall", bought(8)) { HomeScreen(it) {} }
    @Test fun homeBonus() = shot("28_home_bonus", quiet(game.adultBonus(demo.weekStart(6)))) { HomeScreen(it) {} }
    @Test fun homeFree() = shot("29_home_free", quiet(game.keepPlaying(game.playEvent(demo.playWeek(demo.weekStart(8)))))) { HomeScreen(it) {} }

    // ---------- Неделя с ситуацией ----------
    @Test fun f4() = shot("30_plan_f4", planned(3, confirm = false)) { PlanScreen(it, {}, {}) }
    @Test fun f4Big() = shot("30b_plan_f4_x2", planned(3, confirm = false), 2f) { PlanScreen(it, {}, {}) }
    @Test fun f4After() {
        shot("30c_plan_f4_after", planned(3, confirm = false)) { PlanScreen(it, {}, {}) }
        compose.onAllNodesWithText("Подтвердить план")[0].performClick()
        compose.waitForIdle()
        capture("30c_plan_f4_after")
        assertTrue(shown("Откладываем 10 монет"))
    }
    @Test fun situation() = shot("31_situation_kurtka", planned(3)) { SituationScreen(it, {}, {}) }
    @Test fun situationBig() = shot("31b_situation_x2", planned(3), 2f) { SituationScreen(it, {}, {}) }
    @Test fun situationLamp() = shot("31c_situation_lampa", planned(6)) { SituationScreen(it, {}, {}) }
    @Test fun shopWithSituation() = shot("32_shop_ch2", quiet(game.chooseSituation(planned(3), 1))) { ShopScreen(it) {} }
    @Test fun shopWithSituationBig() = shot("32b_shop_ch2_x2", quiet(game.chooseSituation(planned(3), 1)), 2f) { ShopScreen(it) {} }
    @Test fun shopF3() = shot("33_shop_f3", planned(4)) { ShopScreen(it) {} }
    @Test fun shopF3After() {
        shot("33b_shop_f3_after", planned(4)) { ShopScreen(it) {} }
        compose.onAllNodesWithText("Купить")[0].performClick()
        compose.waitForIdle()
        capture("33b_shop_f3_after")
        assertTrue(shown("Куртка у нас уже есть"))
    }
    @Test fun shopF2() {
        shot("34_shop_f2_pay", quiet(game.chooseSituation(planned(5), 0))) { ShopScreen(it) {} }
        compose.onAllNodesWithText("Купить")[0].performClick()
        compose.waitForIdle()
        capture("34_shop_f2_pay")
        assertTrue(shown("Отдать 10 и взять сдачу"))
    }
    @Test fun piggyReady() = shot("35_piggy_ch3", quiet(game.deposit(bought(7)))) { PiggyScreen(it) {} }
    @Test fun sort() = shot("36_sort_f6", quiet(game.leavePiggy(game.deposit(bought(7))))) { SortScreen(it, {}, {}) }
    @Test fun sortBig() = shot("36b_sort_f6_x2", quiet(game.leavePiggy(game.deposit(bought(7)))), 2f) { SortScreen(it, {}, {}) }
    @Test fun summary() = shot("37_summary_ch2", quiet(game.leavePiggy(game.deposit(bought(3))))) { SummaryScreen(it, {}, {}) }
    @Test fun summaryNothing() = shot("37b_summary_ch3_not_taken", run {
        var s = planned(6)
        s = game.leavePiggy(game.deposit(game.leaveShop(game.buy(s, listOf("kasha", "mylo")))))
        quiet(s)
    }) { SummaryScreen(it, {}, {}) }

    // ---------- События и конец ----------
    private fun eventOf(week: Int, given: Boolean): GameState {
        val s = demo.playWeek(demo.weekStart(week))
        return quiet(if (given) s else s.copy(progress = s.progress.copy(savings = 0)))
    }

    @Test fun snowA() = shot("40_event_snow_a", eventOf(4, true)) { EventScreen(it) {} }
    @Test fun snowB() = shot("41_event_snow_b", eventOf(4, false)) { EventScreen(it) {} }
    @Test fun snowBBig() = shot("41b_event_snow_b_x2", eventOf(4, false), 2f) { EventScreen(it) {} }
    @Test fun homeA() = shot("42_event_home_a", eventOf(8, true)) { EventScreen(it) {} }
    @Test fun homeB() = shot("43_event_home_b", eventOf(8, false)) { EventScreen(it) {} }
    @Test fun end() = shot("44_end", quiet(game.playEvent(demo.playWeek(demo.weekStart(8))))) { EndScreen(it) }
    @Test fun endBig() = shot("44b_end_x2", quiet(game.playEvent(demo.playWeek(demo.weekStart(8)))), 2f) { EndScreen(it) }

    // ---------- Дневник и раздел взрослого ----------
    @Test fun diary() = shot("50_diary", bought(6)) { DiaryScreen(it) {} }
    @Test fun diaryTabs() {
        shot("51_diary_tasks", bought(6)) { DiaryScreen(it) {} }
        compose.onAllNodesWithText("Задания")[0].performClick(); compose.waitForIdle(); capture("51_diary_tasks")
        compose.onAllNodesWithText("Покупки")[0].performClick(); compose.waitForIdle(); capture("52_diary_buys")
        compose.onAllNodesWithText("Цель")[0].performClick(); compose.waitForIdle(); capture("53_diary_goal")
        compose.onAllNodesWithText("Слова")[0].performClick(); compose.waitForIdle(); capture("54_diary_words")
        compose.onAllNodesWithText("Дальше")[0].performClick(); compose.waitForIdle(); capture("55_diary_words_2")
    }
    @Test fun diaryBig() = shot("56_diary_x2", bought(6), 2f) { DiaryScreen(it) {} }
    @Test fun adult() = shot("60_adult", bought(6)) { AdultScreen(it) {} }
    @Test fun adultBig() = shot("60b_adult_x2", bought(6), 2f) { AdultScreen(it) {} }
    @Test fun summaryCh3() = shot("38_summary_f6_done", run {
        var s = game.leavePiggy(game.deposit(bought(7)))
        s = game.finishSort(s)
        quiet(s)
    }) { SummaryScreen(it, {}, {}) }
    @Test fun afterSummary() = shot("39_home_after_summary", quiet(game.finishWeek(game.leavePiggy(game.deposit(bought(5))), SummaryChoice.KEEP_PLAN))) { HomeScreen(it) {} }
}
