package ru.vinteno.finni

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
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
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.Phase
import ru.vinteno.finni.data.GameStore
import ru.vinteno.finni.ui.AppModel
import ru.vinteno.finni.ui.FinniNavHost
import ru.vinteno.finni.ui.LocalApp
import java.io.File

/**
 * «Ребёнок в облаке»: проходит игру целиком с первого запуска по настоящим экранам — как ребёнок,
 * который читает записку и нажимает на то, что видит. Ни одного перехода в обход интерфейса: только
 * касания по тексту и подписям. Каждый новый экран снимается, на каждом проверяется вёрстка:
 *
 * - экран не прокручивается при обычном шрифте (главный экран — ни при каком размере из списка);
 * - текст не обрезан и не вылезает за край окна;
 * - зоны нажатия не меньше 48 dp и не наезжают друг на друга;
 * - инварианты текста: фраза ≤ 5 слов, экран ≤ 25, числа ≤ 100, запретных слов нет.
 *
 * Итог — build/journey/<окно>/: снимки по порядку (NNN_экран.png), report.md с находками и путём,
 * index.html — все снимки подряд. Лист снимков для глаз: python3 scripts/sheet.py.
 *
 * Запуск: ./gradlew :app:recordRoborazziDebug --tests '*JourneyTest*'
 * Находки тест не валит — валит только застревание (ребёнку некуда нажать) и падение.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JourneyTest {
    @get:Rule val compose = createComposeRule()

    /** Окно игры телефона 16:9 1080 × 1920 без системных полос — эмулятор Эмиля. */
    @Config(sdk = [35], qualifiers = "w411dp-h659dp-xxhdpi")
    @Test fun phone411x659() = journey("411x659")

    /** Самый частый маленький Android: 360 × 640 без полос. */
    @Config(sdk = [35], qualifiers = "w360dp-h600dp-xxhdpi")
    @Test fun phone360x600() = journey("360x600")

    /** Современный вытянутый телефон. */
    @Config(sdk = [35], qualifiers = "w411dp-h860dp-xxhdpi")
    @Test fun phone411x860() = journey("411x860")

    /** Крупный шрифт ×1,3 — частая системная настройка у родителей. */
    @Config(sdk = [35], qualifiers = "w360dp-h720dp-xxhdpi")
    @Test fun font130() = journey("360x720_font130", fontScale = 1.3f)

    // ---------------------------------------------------------------------------------------------

    private val game = Game(Content.fromResources())
    private lateinit var store: GameStore
    private lateinit var dir: File
    private val log = mutableListOf<String>()
    private val findings = linkedMapOf<String, MutableList<String>>()
    private var shotNo = 0
    private var screenName = ""
    private var fontScale = 1f
    private val seen = mutableSetOf<String>()
    private val s get() = store.state.value

    private fun journey(device: String, fontScale: Float = 1f) {
        this.fontScale = fontScale
        dir = File("build/journey/$device").apply { deleteRecursively(); mkdirs() }
        store = GameStore(RuntimeEnvironment.getApplication())
        store.wipe()
        // Анимации выключены: снимок — конечное состояние экрана; игра при этом та же.
        store.replace(GameState().let { it.copy(profile = it.profile.copy(animationOn = false)) })
        val model = AppModel(game, store)
        compose.setContent {
            val st by store.state.collectAsState()
            val d = LocalDensity.current
            CompositionLocalProvider(LocalApp provides model, LocalDensity provides Density(d.density, fontScale)) { FinniNavHost(st) }
        }
        idle()
        var stuck = 0
        var lastKey = ""
        var error: Throwable? = null
        try {
            for (i in 0 until 700) {
                look()
                if (s.phase == Phase.FREE_PLAY || s.phase == Phase.GAME_OVER && !has("Играть дальше")) break
                val before = key()
                act()
                idle()
                val after = key()
                stuck = if (after == before || after == lastKey) stuck + 1 else 0
                lastKey = before
                if (stuck >= 4) { note("ЗАСТРЯЛ", "на «$screenName»: ${texts().joinToString(" | ")}"); break }
            }
            look()
        } catch (t: Throwable) {
            error = t
            note("ПАДЕНИЕ", "${t::class.simpleName}: ${t.message?.take(300)} на «$screenName»")
            runCatching { shot("FAIL") }
        } finally {
            report(device)
        }
        error?.let { throw it }
        assertTrue("застрял — build/journey/$device/report.md", "ЗАСТРЯЛ" !in findings)
    }

    // ---------- Ребёнок ----------

    private var week = 0
    private var shopVisits = 0

    /** Одно действие ребёнка на текущем экране. */
    private fun act() {
        week = game.weekNumber(s)
        when {
            // Первый запуск.
            has("Выбери мне цвет") && clickable("Рыжий") -> { tap("Рыжий"); tap("Шарф"); press("Дальше") }
            has("Придумай мне имя") -> { press("Ириска"); press("Готово") }
            // Цель главы: первая по порядку.
            s.chapter.goalId == null && game.ch(s).goalIds.any { clickable(game.content.goal(it).name) } -> {
                // Глава 1 — первая цель, глава 2 — средняя (лежанка), глава 3 — самая дорогая: 12 клеток на доме.
                val ids = game.ch(s).goalIds
                val goal = game.content.goal(ids[minOf(ids.lastIndex, s.progress.chapter - 1)]).name
                tap(goal)
                if (clickable("Выбрать")) press("Выбрать")
            }
            has("Играть дальше") -> press("Играть дальше")
            // Окна нехватки и оплаты — берём, что предлагают.
            clickable("Взять из «Хочу»") -> press("Взять из «Хочу»")
            clickable("Взять из копилки") -> press("Взять из копилки")
            clickableStarts("Отдать") -> pressStarts("Отдать")
            clickable("Вернуться к полке") -> press("Вернуться к полке")
            // F5: на первой неделе с мячиком — оставить в копилке.
            clickable("Оставить в копилке") -> press("Оставить в копилке")
            // План.
            clickable("Подтвердить план") -> plan()
            has("Монеты в банках") -> back()
            // Ситуация: чётные недели — вариант с надбавкой, нечётные — основной.
            has("Что возьмём?") -> situation()
            // Магазин.
            has("Что возьмёшь на неделю?") || has("Что в корзине?") || has("На нужное:") -> shop()
            // F6.
            has("Куда ушли монеты?") -> sort()
            // Копилка.
            clickableStarts("Отложить ") -> pressStarts("Отложить ")
            has("Что задумали и что вышло") && clickable("Оставить план") -> press("Оставить план")
            clickable("Понятно") -> press("Понятно")
            clickable("Дальше") -> press("Дальше")
            clickable("Домой") -> press("Домой")
            home() -> {}
            clickable("Назад") -> back()
            // Предыстория листается сама.
            !s.profile.introSeen -> {}
            else -> note("НЕТ ДЕЙСТВИЯ", "«$screenName»: ${texts().joinToString(" | ")}")
        }
    }

    /** Дом: идти к предмету, который называет записка. */
    private fun home(): Boolean {
        val steps = mapOf(
            "Открой посылку" to "Посылка", "Разложи монеты" to "Нужное, Хочу, Копилка", "В магазин" to "Магазин",
            "Покорми" to "Миска", "Умой" to "Мыло", "Отложить" to "Копилка", "Открой копилку" to "Копилка",
            "Разложи траты" to "Неделя $week", "Итог недели" to "Неделя $week", "Следующая неделя" to "Неделя $week",
        )
        val step = steps.keys.firstOrNull { has(it, exact = true) } ?: return false
        val target = steps.getValue(step)
        if (!clickable(target)) { note("НЕТ ПРЕДМЕТА ШАГА", "записка «$step», а «$target» не нажать; неделя $week"); return false }
        if (step == "В магазин") shopVisits = 0
        tap(target)
        return true
    }

    private fun plan() {
        // Не весь кошелёк разложен — остаток в «Хочу»; перебор — убрать из «Хочу».
        texts().firstNotNullOfOrNull { Regex("Осталось разложить (\\d+)").find(plain(it))?.groupValues?.get(1)?.toInt() }?.let { n ->
            repeat(n) { compose.onAllNodes(hasContentDescription("Добавить монету") and hasClickAction())[1].performClick() }
            idle()
        }
        texts().firstNotNullOfOrNull { Regex("Убери (\\d+)").find(plain(it))?.groupValues?.get(1)?.toInt() }?.let { n ->
            repeat(n) { compose.onAllNodes(hasContentDescription("Убрать монету") and hasClickAction())[1].performClick() }
            idle()
        }
        look()
        press("Подтвердить план")
    }

    private fun situation() {
        val options = clickables().filter { it != "Назад" && it != "Взять" && !it.startsWith("Взрослым") }
        val pick = if (week % 2 == 0) options.lastOrNull() else options.firstOrNull()
        pick?.let { tap(it) }
        if (clickable("Взять")) press("Взять")
    }

    private fun shop() {
        if (clickable("Домой")) { press("Домой"); return }
        if (clickable("Понятно")) { press("Понятно"); return }
        shopVisits++
        if (shopVisits > 2) { back(); return }
        if (has("Что в корзине?")) { press("Купить"); return }
        // Основной вариант каждой полки, которая есть на экране; хотелку главы — на неделе 2.
        val want = setOf("Качели", "Санки", "Гирлянда")
        listOf("Каша", "Крупа", "Мыло").firstOrNull { clickable(it) }?.let { tap(it) }
        if (clickable("Мыло")) tap("Мыло")
        if (week == 2) want.firstOrNull { clickable(it) }?.let { tap(it) }
        look()
        if (clickable("Купить")) press("Купить") else back()
    }

    private fun sort() {
        val targets = listOf("Нужное", "Хочу", "Копилка")
        if (clickable("Домой")) { press("Домой"); return }
        if (clickable("Понятно")) { press("Понятно"); return }
        repeat(12) {
            if (!has("Куда ушли монеты?") || clickable("Понятно") || clickable("Домой")) return
            val card = clickables().firstOrNull { l -> targets.none { l.startsWith(it) } && l != "Назад" && l != "Понятно" && l != "Домой" && !l.startsWith("Взрослым") } ?: return
            tap(card)
            for (t in targets) {
                if (!clickable(t)) continue
                tap(t)
                if (!has("Посмотри на значок")) break
            }
            look()
        }
    }

    // ---------- Касания ----------

    private fun idle() { compose.mainClock.advanceTimeBy(2_500); compose.waitForIdle() }
    private fun plain(t: String) = t.replace('\u00A0', ' ')
    private fun hasText(text: String, exact: Boolean = true) = SemanticsMatcher("текст «$text»") { n ->
        n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { t -> if (exact) plain(t.text) == text else plain(t.text).startsWith(text) }
    }
    private fun label(n: SemanticsNode): String =
        n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")?.takeIf { it.isNotBlank() }
            ?: n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { plain(it.text) }
            ?: ""
    private fun clickables(): List<String> = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map(::label).filter { it.isNotBlank() }
    private fun clickable(l: String) = compose.onAllNodes((hasText(l) or hasContentDescription(l)) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() ||
        clickables().any { it == l }
    private fun clickableStarts(p: String) = clickables().any { it.startsWith(p) }
    private fun has(text: String, exact: Boolean = false) = texts().any { if (exact) it == text else text in it }

    private fun click(m: SemanticsMatcher, what: String) {
        if (compose.onAllNodes(m and hasClickAction()).fetchSemanticsNodes().isEmpty()) {
            note("Нечего нажать", "$what на «$screenName»")
            return
        }
        val n = compose.onAllNodes(m and hasClickAction())[0]
        runCatching { n.performScrollTo() }
        n.performClick()
        log += "$what [$screenName]"
        idle()
        look()
    }
    private fun tap(l: String) = click(hasText(l) or hasContentDescription(l), "«$l»")
    private fun press(l: String) = tap(l)
    private fun pressStarts(p: String) = click(hasText(p, exact = false), "«$p…»")
    private fun back() = tap("Назад")

    private fun key() = "${s.hashCode()}|${texts().joinToString("|")}"

    // ---------- Что на экране ----------

    private fun nodes(): List<SemanticsNode> =
        compose.onAllNodes(isRoot()).fetchSemanticsNodes(atLeastOneRootRequired = true).flatMap { root ->
            fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
            walk(root)
        }

    private fun texts(): List<String> = nodes().flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { plain(it.text) } }
        .filter { it.isNotBlank() }

    private fun name(t: List<String>): String {
        val home = t.firstOrNull { it in HOME_NOTES }?.takeIf { "Взрослым" in t }?.let { "дом_$it" }
        return home ?: t.firstOrNull { it.split(' ').size >= 2 && !it.startsWith("Накопили") } ?: t.firstOrNull() ?: "экран"
    }

    /** Снять экран, если он новый, и проверить вёрстку. */
    private fun look() {
        val t = texts()
        screenName = name(t)
        val sig = "$week|${s.phase}|${t.sorted().joinToString("|")}"
        if (!seen.add(sig)) return
        shot(screenName)
        checkLayout(t)
    }

    private fun shot(n: String) {
        shotNo++
        val file = "%03d_w%d_%s".format(shotNo, game.weekNumber(s), translit(n).take(40).replace(Regex("[^A-Za-z0-9]+"), "_"))
        compose.onRoot().captureRoboImage("${dir.path}/$file.png")
        log += "📷 $file"
    }

    /** Имена файлов латиницей: JVM облака пишет кириллицу в имени файла вопросами. */
    private fun translit(t: String): String {
        val map = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя".toList().zip(listOf("a","b","v","g","d","e","e","zh","z","i","y","k","l","m","n","o","p","r","s","t","u","f","h","c","ch","sh","sch","","y","","e","yu","ya")).toMap()
        return t.lowercase().map { map[it] ?: it.toString() }.joinToString("")
    }

    private fun note(kind: String, detail: String) {
        val list = findings.getOrPut(kind) { mutableListOf() }
        if (list.size < 8 && detail !in list) list += detail
    }

    private fun words(s: String) = s.split(Regex("[\\s ]+")).count { w -> w.any { it.isLetter() } }

    private fun checkLayout(t: List<String>) {
        val all = nodes()
        val root = all.first().boundsInRoot
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val where = "«$screenName», неделя ${game.weekNumber(s)}"
        val isHome = t.any { it in HOME_NOTES } && "Взрослым" in t
        // Прокрутка: что-то на экране длиннее окна.
        all.forEach { n ->
            val range = n.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) ?: return@forEach
            val max = range.maxValue()
            if (max > 1f) {
                val kind = if (isHome) "ДОМ ПРОКРУЧИВАЕТСЯ" else if (fontScale <= 1f) "Прокрутка при обычном шрифте" else "Прокрутка при крупном шрифте"
                note(kind, "$where: докрутить ещё ${(max / density).toInt()} dp")
            }
        }
        // Текст за краем окна или обрезан.
        all.forEach { n ->
            val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { plain(it.text) } ?: return@forEach
            if (text.isBlank()) return@forEach
            val b = n.boundsInRoot
            if (b.width <= 0f || b.height <= 0f) return@forEach
            val inScroll = generateSequence(n.parent) { it.parent }.any { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null }
            if (!inScroll && (b.left < root.left - 1 || b.right > root.right + 1 || b.top < root.top - 1 || b.bottom > root.bottom + 1)) {
                note("Текст за краем окна", "$where: «$text»")
            }
            val layout = mutableListOf<TextLayoutResult>()
            n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layout)
            // hasVisualOverflow врёт, когда абзац мерился шире, чем встал: смотрим строки и высоту сами.
            layout.firstOrNull()?.let { r ->
                val cut = r.multiParagraph.height > r.size.height + 1 ||
                    (0 until r.lineCount).any { l -> r.getLineRight(l) - r.getLineLeft(l) > r.size.width + 1 || r.isLineEllipsized(l) }
                if (cut) note("Текст обрезан", "$where: «$text»")
            }
        }
        // Открыто окно (нехватка, копилка, оплата) — под ним экран не нажимается и не читается: считается окно.
        val dialog = all.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == "dialog" }
        val scope = dialog?.let { d -> all.filter { it.id == d.id || isAncestor(d, it) } } ?: all
        // Нажатия.
        val clicks = scope.filter { it.config.contains(SemanticsActions.OnClick) && it.boundsInRoot.width > 0 }
        clicks.forEach { n ->
            val w = n.boundsInRoot.width / density
            val h = n.boundsInRoot.height / density
            if (w < 47.5f || h < 47.5f) note("Цель нажатия меньше 48 dp", "$where: «${label(n)}» ${w.toInt()}×${h.toInt()}")
        }
        for (i in clicks.indices) for (j in i + 1 until clicks.size) {
            val a = clicks[i]; val b = clicks[j]
            if (isAncestor(a, b) || isAncestor(b, a)) continue
            // Плашка с «Понятно» выезжает поверх комнаты: предметы под ней не достать — это не наложение целей.
            if (label(a) in ON_TOP || label(b) in ON_TOP) continue
            val x = a.boundsInRoot.intersect(b.boundsInRoot)
            if (x.width > 4 * density && x.height > 4 * density) {
                note("Зоны нажатия наезжают", "$where: «${label(a)}» и «${label(b)}» на ${(x.width / density).toInt()}×${(x.height / density).toInt()} dp")
            }
        }
        // Тексты.
        t.forEach { line ->
            Regex("\\d+").findAll(line).map { it.value.toInt() }.filter { it > 100 }.forEach { note("Число больше 100", "$where: «$line»") }
            if ('!' in line) note("Восклицательный знак", "$where: «$line»")
            val low = line.lowercase()
            FORBIDDEN.filter { it in low }.forEach { note("Запретное слово «$it»", "$where: «$line»") }
            line.split(Regex("(?<=[.?])\\s+")).filter { words(it) > 5 }.forEach { note("Фраза длиннее 5 слов", "$where: «$it»") }
        }
        val total = scope.flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { plain(it.text) } }.sumOf(::words)
        if (total > 25 && !has("Для взрослого")) note("Экран больше 25 слов", "$where: $total — ${t.joinToString(" | ")}")
    }

    private fun isAncestor(a: SemanticsNode, b: SemanticsNode): Boolean = generateSequence(b.parent) { it.parent }.any { it.id == a.id }

    private fun Rect.intersect(o: Rect) = Rect(maxOf(left, o.left), maxOf(top, o.top), minOf(right, o.right), minOf(bottom, o.bottom)).let {
        if (it.width < 0 || it.height < 0) Rect.Zero else it
    }

    // ---------- Отчёт ----------

    private fun report(device: String) {
        val md = buildString {
            appendLine("# Прогон «ребёнка» — $device, шрифт ×$fontScale")
            appendLine()
            appendLine("Дошёл до: глава ${s.progress.chapter}, неделя ${game.weekNumber(s)}, фаза ${s.phase}. Снимков: $shotNo.")
            appendLine()
            if (findings.isEmpty()) appendLine("Находок нет.")
            findings.forEach { (k, v) ->
                appendLine("## $k")
                v.forEach { appendLine("- $it") }
                appendLine()
            }
            appendLine("## Путь")
            log.forEach { appendLine("- $it") }
        }
        File(dir, "report.md").writeText(md)
        val html = buildString {
            append("<!doctype html><meta charset=utf-8><title>Финни — $device</title>")
            append("<style>body{font-family:sans-serif;background:#eee}div{display:inline-block;margin:6px;vertical-align:top;width:230px;font-size:12px}img{width:230px;border:1px solid #999}</style>")
            append("<h1>$device</h1><pre>${findings.entries.joinToString("\n") { (k, v) -> "$k: ${v.size}" }}</pre>")
            dir.listFiles { f -> f.name.endsWith(".png") }!!.sortedBy { it.name }.forEach { append("<div>${it.name}<br><img src='${it.name}'></div>") }
        }
        File(dir, "index.html").writeText(html)
        println("JOURNEY $device: ${findings.keys} → ${dir.absolutePath}/report.md")
    }

    private companion object {
        val HOME_NOTES = setOf(
            "Открой посылку", "Разложи монеты", "В магазин", "Покорми", "Умой", "Отложить", "Открой копилку",
            "Разложи траты", "Итог недели", "Следующая неделя", "Глава пройдена", "Мы обжились",
        )
        val ON_TOP = setOf("Понятно", "Дальше", "Домой")
        val FORBIDDEN = listOf("неправильн", "ошибк", "проиграл", "молодец", "умница", "отлично", "быстрее", "процент", "бюджет")
    }
}
