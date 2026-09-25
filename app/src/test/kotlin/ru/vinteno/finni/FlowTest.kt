package ru.vinteno.finni

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
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
import ru.vinteno.finni.core.engine.Game
import ru.vinteno.finni.core.engine.Step
import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.model.Fur
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.Phase
import ru.vinteno.finni.core.model.Plan
import ru.vinteno.finni.core.model.SummaryChoice
import ru.vinteno.finni.data.GameStore
import ru.vinteno.finni.ui.AppModel
import ru.vinteno.finni.ui.FinniNavHost
import ru.vinteno.finni.ui.LocalApp
import ru.vinteno.finni.ui.screens.HomeScreen
import ru.vinteno.finni.ui.screens.HomeTarget
import ru.vinteno.finni.ui.theme.FinniColors
import java.io.File

/**
 * Ведение по неделе запиской и предметами (I45). Три вещи:
 * 1) прогон главы по настоящей навигации — обе недели и крайние пути, снимки в build/shots/flow/;
 * 2) главный экран на каждом шаге недели в четырёх окнах: зоны нажатия не меньше 48 dp и не
 *    пересекаются, значок шага не закрывает соседей, плашка не закрывает Финни;
 * 3) таблица касаний: каждый предмет в каждом состоянии — build/qa/home-taps.md, пустых клеток нет.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h600dp-xxhdpi")
class FlowTest {
    @get:Rule val compose = createComposeRule()

    private val game = Game(Content.fromResources())
    private val store = GameStore(RuntimeEnvironment.getApplication())
    private val model = AppModel(game, store)
    private var run by mutableIntStateOf(0)
    private var opened: HomeTarget? = null

    private fun fresh(): GameState {
        var s = GameState()
        s = game.seeIntro(s)
        s = game.createPet(s, "Бублик", Fur.BLUE, Accessory.CAP)
        return s.copy(profile = s.profile.copy(animationOn = false))
    }

    private fun week1() = game.chooseGoal(fresh(), "podarok_kniga")

    /** Весь кошелёк по направлениям: «Нужное» и «Копилка» по 10, остальное — в «Хочу». */
    private fun fill(s: GameState) = game.setPlan(s, Plan(10, s.progress.wallet - 20, 10))

    // ---------- Прогон по навигации ----------

    private fun app(state: GameState, scale: Float = 1f) {
        store.replace(state)
        compose.setContent {
            val s by store.state.collectAsState()
            val d = LocalDensity.current
            CompositionLocalProvider(LocalApp provides model, LocalDensity provides Density(d.density, scale)) { FinniNavHost(s) }
        }
        idle()
    }

    private fun idle() { compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle() }
    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/shots/flow/$name.png")
    private fun node(m: SemanticsMatcher) = compose.onAllNodes(m and hasClickAction())[0]

    /** Текст экрана как его видит ребёнок: неразрывный пробел (typo) — тот же пробел. */
    private fun plain(t: String) = t.replace('\u00A0', ' ')
    private fun hasText(text: String, substring: Boolean = false) = SemanticsMatcher("текст «$text»") { n ->
        n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { t -> if (substring) text in plain(t.text) else plain(t.text) == text }
    }
    private fun tap(desc: String) { node(hasContentDescription(desc)).performClick(); idle() }
    private fun press(text: String) { node(hasText(text)).performClick(); idle() }
    private fun shown(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun noteSays(text: String) {
        val ok = compose.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        if (!ok) shot("FAIL_note_$text")
        assertTrue("на записке «$text»; на экране: " + compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { label(it) }, ok)
    }
    private val s get() = store.state.value

    private val jars = "Нужное, Хочу, Копилка"

    @Test fun weekOneAndTwo() {
        app(week1())
        noteSays("Открой посылку"); shot("w1_01_parcel")
        // До посылки предметы отвечают репликой, а не молчат.
        tap("Неделя 1"); assertTrue(shown("Сначала открой посылку.")); shot("w1_02_say_parcel_first")
        tap("Посылка"); shot("w1_03_parcel_note")
        press("Понятно"); shot("w1_04_announce")
        press("Понятно"); noteSays("Разложи монеты"); shot("w1_05_step_plan")
        // До плана: витрина магазина, копилка — только посмотреть, календарь — реплика.
        tap("Магазин"); assertTrue(shown("Сначала разложи монеты.")); shot("w1_06_shop_showcase")
        tap("Назад")
        tap("Копилка"); assertTrue(shown("Сначала разложи монеты.")); assertFalse(shown("Отложить")); shot("w1_07_piggy_before_plan")
        tap("Назад")
        tap("Неделя 1"); assertTrue(shown("Сначала разложи монеты.")); shot("w1_08_say_plan_first")
        tap(jars); shot("w1_09_plan")
        press("Подтвердить план"); noteSays("В магазин"); shot("w1_10_step_shop")
        tap(jars); assertTrue(shown("Монеты в банках")); assertFalse(shown("Подтвердить план")); shot("w1_11_plan_frozen")
        tap("Назад")
        // Зашёл и вышел без покупки: шаг остаётся, награды нет, итог ещё закрыт.
        tap("Магазин"); tap("Назад")
        noteSays("В магазин"); assertEquals(0, s.progress.savings); assertFalse(s.week!!.taskDone)
        tap("Неделя 1"); assertTrue(shown("Итог — в конце недели.")); shot("w1_12_say_summary_later")
        tap("Миска"); assertTrue(shown("Миска пустая.")); shot("w1_13_say_bowl_empty")
        // Покупка: каша с ягодами — в «Нужное» одной позицией 8; объяснение — в магазине, с «Домой».
        tap("Магазин")
        listOf("Каша с ягодами", "Мыло").forEach { tap(it) }
        shot("w1_14_cart")
        press("Купить"); press("Взять из «Хочу»")
        assertEquals(10, s.progress.savings); assertTrue(s.week!!.taskDone)
        assertTrue(shown("Домой")); shot("w1_15_shop_explained")
        press("Домой"); noteSays("Покорми"); shot("w1_16_step_feed")
        tap("Миска"); noteSays("Умой"); shot("w1_17_step_wash")
        tap("Мыло"); noteSays("Отложить"); shot("w1_18_step_save")
        tap("Копилка"); press("Отложить 10"); assertTrue(shown("Домой")); shot("w1_19_piggy_deposited")
        press("Домой"); noteSays("Итог недели"); shot("w1_20_step_summary")
        tap("Неделя 1"); shot("w1_21_summary")
        press("Оставить план"); noteSays("Следующая неделя"); shot("w1_22_step_next_week")
        tap("Магазин"); assertTrue(shown("Неделя уже закончилась.")); shot("w1_23_say_week_over")
        tap("Копилка"); assertFalse(shown("Отложить")); assertFalse(shown("Домой")); shot("w1_24_piggy_after_summary")
        tap("Назад")
        tap("Неделя 1")

        // Неделя 2: 39 в кошельке, 10 / 10 / 10 не подтвердить, пока не разложен весь кошелёк.
        assertEquals(2, s.week!!.number); shot("w2_01_parcel")
        tap("Посылка"); press("Понятно"); press("Понятно")
        tap(jars)
        assertEquals(39, s.progress.wallet)
        assertTrue(shown("Осталось разложить 9 монет."))
        assertFalse(game.canConfirmPlan(s)); shot("w2_02_plan_under")
        repeat(9) { compose.onAllNodes(hasContentDescription("Добавить монету") and hasClickAction())[1].performClick() }
        idle()
        assertEquals(Plan(10, 19, 10), s.week!!.plan); shot("w2_03_plan_full")
        press("Подтвердить план")
        // Качели — на остаток, без копилки.
        tap("Магазин"); listOf("Каша", "Мыло", "Качели").forEach { tap(it) }
        press("Купить")
        assertTrue("kacheli" in s.progress.inventory); assertEquals(20, s.progress.savings)
        shot("w2_04_shop_explained")
        press("Домой")
        tap("Миска"); tap("Мыло"); noteSays("Отложить")
        tap("Копилка"); press("Отложить 10"); shot("w2_05_f5")
        press("Взять мячик"); shot("w2_06_f5_after")
        press("Домой"); noteSays("Итог недели"); shot("w2_07_step_summary")
        tap("Неделя 2"); press("Оставить план")
        assertEquals(Phase.EVENT, s.phase); shot("w2_08_event")
        press("Дальше")
        assertEquals(Phase.FREE_PLAY, s.phase); noteSays("Глава пройдена."); shot("w2_09_free_play")
        tap("Неделя 2"); tap("Магазин"); tap(jars); tap("Копилка")
        assertEquals(Phase.FREE_PLAY, s.phase); shot("w2_10_free_play_taps")
    }

    /** Ничего не покупал всю неделю: итог доступен, неделя кончается, упрёков нет. Путь — через копилку. */
    @Test fun nothingBought() {
        app(game.confirmPlan(game.seeAnnouncement(game.openParcel(week1()))))
        tap("Магазин"); tap("Назад"); noteSays("В магазин")
        tap("Копилка"); press("Отложить 10"); press("Домой")
        noteSays("Итог недели"); assertEquals(0, s.week!!.purchases.size)
        tap("Неделя 1"); shot("x_01_summary_nothing")
        press("Оставить план")
        assertEquals(Phase.AFTER_SUMMARY, s.phase); noteSays("Следующая неделя")
    }

    /** F5 — «Оставить в копилке»: тот же «Домой», та же реакция. */
    @Test fun ballKept() {
        var st = game.confirmPlan(game.seeAnnouncement(game.openParcel(week1())))
        st = game.wash(game.feed(game.buy(st, listOf("kasha", "mylo"))))
        st = game.nextWeek(game.finishWeek(game.leavePiggy(game.deposit(st)), SummaryChoice.KEEP_PLAN))
        st = game.seeAnnouncement(game.openParcel(st))
        st = game.wash(game.feed(game.buy(game.confirmPlan(fill(st)), listOf("kasha", "mylo"))))
        app(st)
        tap("Копилка"); press("Отложить 10"); press("Оставить в копилке"); shot("x_02_f5_kept")
        press("Домой"); noteSays("Итог недели")
    }

    // ---------- Главный экран на каждом шаге ----------

    /** Шаги недели по порядку записки — и неделя 2 с F5, и после события. */
    private fun steps(): List<Pair<String, GameState>> {
        val w1 = week1()
        val parcel = w1
        val note = game.openParcel(w1)
        val plan = game.seeAnnouncement(note)
        val shop = game.confirmPlan(plan)
        val shopLeft = game.leaveShop(shop)
        val feed = game.buy(shop, listOf("kasha", "yagody", "mylo"), agreedWant = true)
        val wash = game.feed(feed)
        val save = game.wash(wash)
        val summary = game.leavePiggy(game.deposit(save))
        val next = game.finishWeek(summary, SummaryChoice.KEEP_PLAN)
        var w2 = game.seeAnnouncement(game.openParcel(game.nextWeek(next)))
        val w2plan = w2
        w2 = game.confirmPlan(game.setPlan(w2, Plan(10, 19, 10)))
        w2 = game.wash(game.feed(game.buy(w2, listOf("kasha", "mylo", "kacheli"))))
        val w2f5 = game.deposit(w2)
        val free = game.playEvent(game.finishWeek(game.leavePiggy(game.chooseBall(w2f5, true)), SummaryChoice.KEEP_PLAN))
        return listOf(
            "01_parcel" to parcel, "02_parcel_note" to note, "03_plan" to plan, "04_shop" to shop, "05_shop_left" to shopLeft,
            "06_feed" to feed, "07_wash" to wash, "08_save" to save, "09_summary" to summary, "10_next_week" to next,
            "11_w2_plan" to w2plan, "12_w2_f5" to w2f5, "13_free" to free,
        ).also { list ->
            assertEquals(
                listOf(Step.PARCEL, Step.ANNOUNCE, Step.PLAN, Step.SHOP, Step.SHOP, Step.CARE, Step.CARE, Step.SAVE, Step.SUMMARY,
                    Step.NEXT_WEEK, Step.PLAN, Step.SAVE, Step.NONE),
                list.map { game.nextStep(it.second) },
            )
        }
    }

    private fun home(state: GameState, scale: Float) {
        store.replace(state)
        compose.setContent {
            val st by store.state.collectAsState()
            val d = LocalDensity.current
            key(run) {
                CompositionLocalProvider(LocalApp provides model, LocalDensity provides Density(d.density, scale)) {
                    Box(Modifier.fillMaxSize().background(FinniColors.BgSand)) { HomeScreen(st) { opened = it } }
                }
            }
        }
        idle()
    }

    private fun switch(state: GameState) {
        compose.runOnUiThread { store.replace(state); run++ }
        idle()
    }

    private fun label(n: SemanticsNode): String =
        (n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).joinToString(" ")

    private fun homeSteps(size: String, scale: Float = 1f) {
        val list = steps()
        home(list.first().second, scale)
        val dp = compose.density.density
        list.forEach { (name, state) ->
            switch(state)
            // Открытую посылку показываем с плашкой: касание, как у ребёнка.
            if (name == "02_parcel_note") { switch(game.chooseGoal(fresh(), "podarok_kniga")); tap("Посылка") }
            val shotName = "home_${name}_$size"
            compose.onRoot().captureRoboImage("build/shots/flow/$shotName.png")
            val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            nodes.forEach { n ->
                assertTrue("$shotName: «${label(n)}» ${n.size.width / dp}×${n.size.height / dp} dp", n.size.width / dp >= 47.5f && n.size.height / dp >= 47.5f)
            }
            val zones = nodes.map { label(it) to it.boundsInRoot }.filter { it.second.width > 0f && it.second.height > 0f }
            for (i in zones.indices) for (j in i + 1 until zones.size) {
                val x = zones[i].second.intersect(zones[j].second)
                // Кнопка «Понятно» лежит на плашке поверх комнаты: плашка модальна для предметов (они её замечают).
                val overPlate = listOf(zones[i].first, zones[j].first).any { it == "Понятно" }
                assertTrue("$shotName: пересекаются «${zones[i].first}» и «${zones[j].first}»", overPlate || x.width <= 0.5f || x.height <= 0.5f)
            }
            // Значок шага не закрывает соседние предметы.
            compose.onAllNodes(hasTestTag("mark")).fetchSemanticsNodes().forEach { m ->
                zones.forEach { (l, r) ->
                    val x = m.boundsInRoot.intersect(r)
                    assertTrue("$shotName: значок шага закрывает «$l»", x.width <= 0.5f || x.height <= 0.5f)
                }
            }
            // Плашка посылки или объявления не закрывает Финни.
            val finni = compose.onAllNodes(hasContentDescription("Бублик") and hasClickAction()).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
            val plate = compose.onAllNodes(hasText("Понятно") and hasClickAction()).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
            if (finni != null && plate != null) {
                val x = finni.intersect(plate)
                assertTrue("$shotName: плашка закрывает Финни", x.width <= 0.5f || x.height <= 0.5f)
            }
        }
    }

    @Test fun home600() = homeSteps("360x600")
    @Test fun home600x2() = homeSteps("360x600_x2", 2f)
    @Config(qualifiers = "w360dp-h760dp-xxhdpi") @Test fun home800() = homeSteps("360x800")
    @Config(qualifiers = "w412dp-h875dp-xxhdpi") @Test fun home915() = homeSteps("412x915")

    /** Анимации выключены: всё на местах сразу, значок шага и реплика видны. */
    @Test fun noAnimation() {
        home(week1(), 1f)
        assertTrue(compose.onAllNodes(hasTestTag("mark")).fetchSemanticsNodes().isNotEmpty())
        tap("Магазин")
        assertTrue(shown("Сначала открой посылку."))
        compose.onRoot().captureRoboImage("build/shots/flow/home_no_anim_say.png")
    }

    // ---------- Таблица касаний ----------

    @Test fun tapsTable() {
        val w1 = week1()
        val announce = game.openParcel(w1)
        val beforePlan = game.seeAnnouncement(announce)
        val planned = game.confirmPlan(beforePlan)
        val bought = game.buy(planned, listOf("kasha", "mylo"))
        val fed = game.feed(bought)
        val washed = game.wash(fed)
        val summary = game.finishWeek(game.leavePiggy(game.deposit(washed)), SummaryChoice.KEEP_PLAN)
        var w2 = game.seeAnnouncement(game.openParcel(game.nextWeek(summary)))
        w2 = game.wash(game.feed(game.buy(game.confirmPlan(fill(w2)), listOf("kasha", "mylo"))))
        val event = game.playEvent(game.finishWeek(game.leavePiggy(game.chooseBall(game.deposit(w2), false)), SummaryChoice.KEEP_PLAN))
        val states = listOf(
            "до посылки" to w1,
            "плашка объявления" to announce,
            "после посылки, до плана" to beforePlan,
            "после плана, ничего не куплено" to planned,
            "после плана, куплено, не поел" to bought,
            "после плана, поел, не умыт" to fed,
            "после плана, умыт" to washed,
            "после итога недели" to summary,
            "после события" to event,
        )
        val props = listOf("Посылка", "Нужное, Хочу, Копилка", "Магазин", "Копилка", "Миска", "Мыло", "Неделя", "Записка")
        val rows = mutableListOf<String>()
        home(w1, 1f)
        states.forEach { (stateName, st) ->
            props.forEach { prop ->
                switch(st)
                opened = null
                val before = store.state.value
                val keyBefore = model.reactionKey
                val matcher = when (prop) {
                    "Неделя" -> hasContentDescription("Неделя", substring = true)
                    "Записка" -> SemanticsMatcher("записка") { n ->
                        n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { t ->
                            plain(t.text) in listOf("Открой посылку", "Разложи монеты", "В магазин", "Покорми", "Умой", "Отложить",
                                "Открой копилку", "Итог недели", "Следующая неделя", "Глава пройдена.")
                        }
                    }
                    else -> hasContentDescription(prop)
                }
                val found = compose.onAllNodes(matcher and hasClickAction()).fetchSemanticsNodes()
                if (found.isEmpty()) {
                    rows += "| $prop | $stateName | нет на экране |"
                    return@forEach
                }
                // Плашка объявления встаёт наверху стены, над запиской: касание приходится на плашку.
                val plateUp = compose.onAllNodes(hasText("Понятно") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
                if (plateUp && prop == "Записка") {
                    rows += "| $prop | $stateName | закрыта плашкой объявления — касание приходится на плашку |"
                    return@forEach
                }
                compose.onAllNodes(matcher and hasClickAction())[0].performClick()
                idle()
                val after = store.state.value
                val said = listOf("Сначала открой посылку.", "Сначала разложи монеты.", "Итог — в конце недели.",
                    "Неделя уже закончилась.", "Миска пустая.").firstOrNull { shown(it) }
                val what = when {
                    opened != null -> "открывает: " + when (opened!!) {
                        HomeTarget.PLAN -> if (before.week!!.planConfirmed) "план (смотреть)" else "план (менять)"
                        HomeTarget.SHOP -> if (before.week!!.planConfirmed) "магазин" else "магазин-витрина"
                        HomeTarget.PIGGY -> if (before.week!!.planConfirmed && before.phase == Phase.WEEK) "копилка" else "копилка (смотреть)"
                        HomeTarget.SUMMARY -> "итог недели"
                        HomeTarget.EVENT -> "событие"
                    }
                    said != null -> "реплика «$said»"
                    after.week?.number != before.week?.number -> "следующая неделя"
                    after.week?.parcel != before.week?.parcel -> "открывает посылку"
                    after.week?.fed != before.week?.fed -> "кормит"
                    after.week?.washed != before.week?.washed -> "моет"
                    model.reactionKey != keyBefore -> "Финни `${model.reaction?.name?.let { mapOf("HAPPY" to "доволен", "NOTICE" to "замечает", "EAT" to "ест").getValue(it) }}`"
                    else -> "НИЧЕГО"
                }
                rows += "| $prop | $stateName | $what |"
            }
        }
        val out = File("build/qa/home-taps.md").apply { parentFile.mkdirs() }
        out.writeText("| Предмет | Состояние | Что произошло |\n|---|---|---|\n" + rows.joinToString("\n") + "\n")
        println(out.readText())
        assertTrue(rows.filter { "НИЧЕГО" in it }.joinToString("\n"), rows.none { "НИЧЕГО" in it })
    }
}
