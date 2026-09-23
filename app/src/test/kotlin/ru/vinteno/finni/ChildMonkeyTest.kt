package ru.vinteno.finni

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.vinteno.finni.core.content.Content
import ru.vinteno.finni.core.engine.Game
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.Phase
import ru.vinteno.finni.data.GameStore
import ru.vinteno.finni.ui.AppModel
import ru.vinteno.finni.ui.FinniNavHost
import ru.vinteno.finni.ui.LocalApp
import java.io.File
import kotlin.random.Random

/**
 * «Ребёнок»: случайные нажатия по всему, что нажимается, ввод имени и системное «назад» —
 * по настоящему приложению целиком. После каждого шага проверяются правила CLAUDE.md и
 * test-cases.md. Находки не валят тест, а собираются в отчёт build/qa/monkey-report.md:
 * одна строка на вид находки, с последними шагами для воспроизведения.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class ChildMonkeyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val game = Game(Content.fromResources())
    private val findings = linkedMapOf<String, String>()
    private val forbidden = listOf("неправильн", "ошибк", "проиграл", "молодец", "не успел", "зря", "лучше бы",
        "надо было", "умница", "отлично", "успей", "быстрее", "процент", "бюджет", "перерасход", "расстроил", "грустит")
    private val names = listOf("", "Бублик", "  ", "Мистер Пушистый Хвост", "Ёж")

    private fun note(key: String, detail: String) { if (key !in findings) findings[key] = detail }

    private fun words(s: String) = s.split(Regex("\\s+")).count { w -> w.any { it.isLetter() } }

    private fun textNodes(): List<SemanticsNode> =
        compose.onAllNodes(isRoot()).fetchSemanticsNodes(atLeastOneRootRequired = true).flatMap { root ->
            fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
            walk(root)
        }.filter { it.config.getOrNull(SemanticsProperties.Text) != null }

    private fun texts(): List<String> = textNodes().flatMap { n ->
        n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    }.filter { it.isNotBlank() }

    private fun screenName(t: List<String>) = t.firstOrNull { words(it) >= 2 } ?: t.firstOrNull() ?: "?"

    private fun check(s: GameState, prev: GameState, log: List<String>, lastAction: String) {
        val trail = log.takeLast(12).joinToString(" → ")
        val t = texts()
        val screen = screenName(t)
        // Деньги и прогресс.
        if (s.progress.wallet < 0 || s.progress.savings < 0) note("E12 минус в деньгах", "$screen: W=${s.progress.wallet} S=${s.progress.savings}; $trail")
        if (s.progress.marksCare < prev.progress.marksCare || s.progress.marksPlan < prev.progress.marksPlan ||
            s.progress.marksSave < prev.progress.marksSave) note("E17 отметка убыла", trail)
        val offWeek = prev.phase == Phase.AFTER_SUMMARY || prev.phase == Phase.FREE_PLAY
        if (offWeek && s.phase == prev.phase && (s.progress.wallet != prev.progress.wallet ||
                s.progress.savings != prev.progress.savings || s.progress.inventory != prev.progress.inventory)) {
            note("Трата после итога (${prev.phase})", "«$lastAction» на «$screen»: W ${prev.progress.wallet}→${s.progress.wallet}, " +
                "S ${prev.progress.savings}→${s.progress.savings}; $trail")
        }
        // Тексты на экране.
        t.forEach { line ->
            Regex("\\d+").findAll(line).map { it.value.toInt() }.filter { it > 100 }.forEach {
                note("Инв. 9 число $it > 100", "«$line» на «$screen»; $trail")
            }
            val low = line.lowercase()
            forbidden.filter { low.contains(it) }.forEach { note("Инв. 11 слово «$it»", "«$line»") }
            if ('!' in line) note("Восклицательный знак", "«$line»")
            line.split(Regex("(?<=[.?])\\s+")).filter { words(it) > 5 }.forEach {
                note("Инв. 10 фраза >5 слов: «$it»", "на «$screen»")
            }
        }
        val total = t.sumOf(::words)
        if (total > 25) note("Инв. 10 экран >25 слов: «$screen»", "$total слов: ${t.joinToString(" | ")}")
        // Размер нажатия.
        val density = compose.activity.resources.displayMetrics.density
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { n ->
            val w = n.size.width / density
            val h = n.size.height / density
            if ((w < 47.5f || h < 47.5f) && w > 0 && h > 0) {
                val label = n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
                    ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString() ?: "без подписи"
                note("U07 цель нажатия <48 dp: $label", "${w.toInt()}×${h.toInt()} dp на «$screen»")
            }
        }
    }

    private fun hasBackButton() = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().any {
        it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Назад") == true
    }

    private fun session(seed: Int, steps: Int, store: GameStore): String {
        val rnd = Random(seed)
        val log = mutableListOf<String>()
        var prev = store.state.value
        repeat(steps) {
            val clickable = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            val action: String
            try {
                val textField = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
                action = when {
                    textField.isNotEmpty() && rnd.nextInt(4) == 0 -> {
                        val name = names.random(rnd)
                        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement(name)
                        "имя «$name»"
                    }
                    hasBackButton() && rnd.nextInt(6) == 0 -> {
                        val t = texts()
                        val fromShop = "Что возьмёшь на неделю?" in t
                        val fromPiggy = t.any { it.startsWith("Подарок стоит") }
                        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
                        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
                        val w = store.state.value.week
                        if (fromShop && w != null && !w.shopVisited) note("Системное «назад» из магазина не закрывает шаг",
                            "после жеста «назад» shopVisited=false, задание F1 не засчитано, плашки нет; ${log.takeLast(6)}")
                        if (fromPiggy && w != null && !w.piggyVisited) note("Системное «назад» из копилки не закрывает шаг",
                            "после жеста «назад» piggyVisited=false: нижняя кнопка снова зовёт «Отложить»; ${log.takeLast(6)}")
                        "назад"
                    }
                    else -> {
                        val i = rnd.nextInt(clickable.size)
                        val n = clickable[i]
                        val label = n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
                            ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                            ?: n.children.flatMap { c -> c.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
                                .joinToString(" ").ifEmpty { "(${n.config.getOrNull(SemanticsActions.OnClick)?.label ?: "нажатие"})" }
                        compose.onAllNodes(hasClickAction())[i].performClick()
                        label.take(30)
                    }
                }
                compose.mainClock.advanceTimeBy(2_000)
                compose.waitForIdle()
            } catch (e: Throwable) {
                if (e is AssertionError && e.message?.contains("Failed to") == true) return@repeat
                note("ПАДЕНИЕ ${e::class.simpleName}: ${e.message?.take(120)}", log.takeLast(12).joinToString(" → ") +
                    "\n    " + e.stackTrace.take(6).joinToString("\n    "))
                return "падение"
            }
            if (compose.activity.isFinishing) return "вышел из приложения"
            log += action
            val s = store.state.value
            check(s, prev, log, action)
            prev = s
        }
        val s = store.state.value
        return "${s.phase}, неделя ${s.week?.number ?: 0}"
    }

    @Test fun childPlays() {
        val app = compose.activity.application
        val store = GameStore(app)
        val model = AppModel(game, store)
        val ends = mutableMapOf<String, Int>()
        store.replace(GameState().let { it.copy(profile = it.profile.copy(animationOn = false)) })
        var run by mutableIntStateOf(0)
        compose.setContent {
            val s by store.state.collectAsState()
            // Новая партия — новое приложение: запомненный экран не переносится.
            key(run) { CompositionLocalProvider(LocalApp provides model) { FinniNavHost(s) } }
        }
        val sessions = 80
        val steps = 300
        for (seed in 1..sessions) {
            compose.runOnUiThread {
                store.replace(GameState().let { it.copy(profile = it.profile.copy(animationOn = false)) })
                model.pendingPlate = null
                run++
            }
            compose.waitForIdle()
            val end = session(seed, steps, store)
            ends[end] = (ends[end] ?: 0) + 1
            if (end == "вышел из приложения") break
        }
        val out = File("build/qa/monkey-report.md").apply { parentFile.mkdirs() }
        out.writeText(buildString {
            appendLine("# Прогон «ребёнка»: $sessions партий по $steps шагов\n")
            appendLine("Чем кончились партии: " + ends.entries.joinToString { "${it.key} — ${it.value}" } + "\n")
            findings.forEach { (k, v) -> appendLine("- **$k**\n  $v") }
        })
        println(out.readText())
    }
}
