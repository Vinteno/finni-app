package ru.vinteno.finni

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
import ru.vinteno.finni.core.engine.Game
import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.model.Fur
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.Plan
import ru.vinteno.finni.core.model.SummaryChoice
import ru.vinteno.finni.data.GameStore
import ru.vinteno.finni.ui.AppModel
import ru.vinteno.finni.ui.LocalApp
import ru.vinteno.finni.ui.screens.HomeScreen
import ru.vinteno.finni.ui.theme.FinniColors

/**
 * Главный экран на трёх размерах и при шрифте ×2: снимки для глаз и проверка зон нажатия — каждая
 * не меньше 48 × 48 dp, никакие две не пересекаются, у посылки с дверью и Финни — не меньше 16 dp
 * пустого места. Окно телефона 360 × 640 — это 360 × 600 без строки состояния, 360 × 800 — 360 × 760.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h600dp-xxhdpi")
class HomeLayoutTest {
    @get:Rule val compose = createComposeRule()

    private val game = Game(Content.fromResources())

    private fun week1(): GameState {
        var s = GameState()
        s = game.seeIntro(s)
        s = game.createPet(s, "Бублик", Fur.BLUE, Accessory.CAP)
        s = s.copy(profile = s.profile.copy(animationOn = false))
        return game.chooseGoal(s, "podarok_kniga")
    }

    private fun shopped(): GameState =
        game.leaveShop(game.buy(game.confirmPlan(game.seeAnnouncement(game.openParcel(week1()))), listOf("kasha", "yagody", "mylo"), agreedWant = true))

    private fun week1Done() = game.wash(game.feed(shopped()))

    /** Неделя 2 у посылки, качели куплены на неделе 1 — посылка и качели вместе. */
    private fun week2Parcel(): GameState {
        val s = game.nextWeek(game.finishWeek(game.leavePiggy(game.deposit(week1Done())), SummaryChoice.KEEP_PLAN))
        return s.copy(progress = s.progress.copy(inventory = s.progress.inventory + "kacheli"))
    }

    private val demo = ru.vinteno.finni.core.engine.Demo(game)

    private fun quiet(s: GameState) = s.copy(profile = s.profile.copy(animationOn = false))

    /** Свободная игра после новоселья: вещи сложены в корзину, лампа, гирлянда, полотенце, лежанка. */
    private fun freePlay(): GameState = quiet(game.keepPlaying(game.playEvent(demo.playWeek(demo.weekStart(8)))).let {
        it.copy(progress = it.progress.copy(inventory = it.progress.inventory + "myachik"))
    })

    /** Глава 2, начало: Финни зябнет, окно с инеем, у двери — посылка. */
    private fun cold(): GameState = quiet(demo.weekStart(3))

    /** Глава 2, неделя 4: в куртке с рисунком и бинтом, мячик, качели, санки. */
    private fun dressed(): GameState {
        var s = demo.weekStart(4)
        s = game.seeAnnouncement(game.openParcel(s))
        s = game.confirmPlan(game.setPlan(s, Plan(12, s.progress.wallet - 22, 10)))
        s = game.chooseSituation(game.resolveDuplicate(s), 1)
        s = game.leaveShop(game.buy(s, listOf("kasha", "mylo") + game.situationCart(s)))
        return quiet(s.copy(progress = s.progress.copy(inventory = s.progress.inventory + listOf("myachik", "sanki"))))
    }

    /** Глава 3, неделя 8: коробки переезда, коробка с наклейками, лампа, гирлянда, лежанка, качели. */
    private fun crowded(): GameState = quiet(demo.weekStart(8).let {
        it.copy(progress = it.progress.copy(inventory = it.progress.inventory + listOf("myachik", "sanki", "polotence", "vyshivka")))
    })

    private fun check(name: String, state: GameState, scale: Float = 1f) {
        val store = GameStore(RuntimeEnvironment.getApplication())
        store.replace(state)
        ru.vinteno.finni.ui.components.RoomArt.name = ru.vinteno.finni.ui.components.RoomArt.of(state.progress.chapter, "lampa" in state.progress.inventory)
        val model = AppModel(game, store)
        compose.setContent {
            val s by store.state.collectAsState()
            val d = LocalDensity.current
            CompositionLocalProvider(LocalApp provides model, LocalDensity provides Density(d.density, scale)) {
                Box(Modifier.fillMaxSize().background(FinniColors.BgSand)) { HomeScreen(s) {} }
            }
        }
        compose.onRoot().captureRoboImage("build/shots/home/$name.png")
        val dp = compose.density.density
        // Размер зоны — без обрезки прокруткой; узлы, целиком ушедшие за край прокрутки, не нажимаются.
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        nodes.forEach { n ->
            assertTrue("$name: «${label(n)}» ${n.size.width / dp}×${n.size.height / dp} dp", n.size.width / dp >= 47.5f && n.size.height / dp >= 47.5f)
        }
        val zones = nodes.map { label(it) to it.boundsInRoot }.filter { it.second.width > 0f && it.second.height > 0f }
        for (i in zones.indices) for (j in i + 1 until zones.size) {
            val x = zones[i].second.intersect(zones[j].second)
            assertTrue("$name: пересекаются «${zones[i].first}» и «${zones[j].first}»", x.width <= 0.5f || x.height <= 0.5f)
        }
        val parcel = zones.firstOrNull { "Посылка" in it.first }?.second ?: return
        listOf("Магазин", state.profile.petName).forEach { n ->
            val r = zones.first { n in it.first }.second
            assertTrue("$name: у посылки и «$n» ${gap(parcel, r) / dp} dp", gap(parcel, r) / dp >= 15.5f)
        }
    }

    private fun gap(a: Rect, b: Rect): Float = maxOf(b.left - a.right, a.left - b.right, b.top - a.bottom, a.top - b.bottom)

    private fun label(n: SemanticsNode): String =
        (n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).joinToString(" ")

    @Test fun parcel640() = check("parcel_360x640", week1())
    @Test fun care640() = check("care_360x640", shopped())
    @Test fun week2Parcel640() = check("week2_parcel_360x640", week2Parcel())
    @Test fun free640() = check("free_360x640", freePlay())
    @Test fun careBig640() = check("care_360x640_x2", shopped(), 2f)
    @Test fun parcelBig640() = check("parcel_360x640_x2", week1(), 2f)

    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun parcel800() = check("parcel_360x800", week1())
    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun care800() = check("care_360x800", shopped())
    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun week2Parcel800() = check("week2_parcel_360x800", week2Parcel())
    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun free800() = check("free_360x800", freePlay())
    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun careBig800() = check("care_360x800_x2", shopped(), 2f)

    @Config(qualifiers = "w412dp-h875dp-xxhdpi") @Test fun parcel915() = check("parcel_412x915", week1())
    @Config(qualifiers = "w412dp-h875dp-xxhdpi") @Test fun care915() = check("care_412x915", shopped())
    @Config(qualifiers = "w412dp-h875dp-xxhdpi") @Test fun free915() = check("free_412x915", freePlay())

    // ---------- Главы 2 и 3 ----------
    @Test fun cold640() = check("ch2_cold_360x640", cold())
    @Test fun dressed640() = check("ch2_dressed_360x640", dressed())
    @Test fun crowded640() = check("ch3_crowded_360x640", crowded())
    @Test fun crowdedBig640() = check("ch3_crowded_360x640_x2", crowded(), 2f)
    @Test fun dressedBig640() = check("ch2_dressed_360x640_x2", dressed(), 2f)
    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun dressed800() = check("ch2_dressed_360x800", dressed())
    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun crowded800() = check("ch3_crowded_360x800", crowded())
    @Config(qualifiers = "w412dp-h875dp-xxhdpi") @Test fun dressed915() = check("ch2_dressed_412x915", dressed())
    @Config(qualifiers = "w412dp-h875dp-xxhdpi") @Test fun crowded915() = check("ch3_crowded_412x915", crowded())
}
