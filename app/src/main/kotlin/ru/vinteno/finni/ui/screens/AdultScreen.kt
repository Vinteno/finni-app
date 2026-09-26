package ru.vinteno.finni.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.Phase
import ru.vinteno.finni.ui.app
import ru.vinteno.finni.ui.components.CheckMark
import ru.vinteno.finni.ui.components.PressBox
import ru.vinteno.finni.ui.components.SecondaryButton
import ru.vinteno.finni.ui.components.SoftScreen
import ru.vinteno.finni.ui.components.Txt
import ru.vinteno.finni.ui.components.picked
import ru.vinteno.finni.ui.components.scrollHint
import ru.vinteno.finni.ui.components.softPlate
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniText

/** Шрифт раздела: спокойный, плотнее детского, но не мельче 16 sp (гайд 5.6). */
private val AdultBody = FinniText.Body
private val AdultHead = FinniText.Subtitle
private val AdultStrong = FinniText.Body.copy(fontWeight = FontWeight.Bold)

/**
 * Раздел для взрослого — final-plan §4; ТЗ 2.5.12, 2.5.13, 3.5, 3.6. За барьером удержания на доме.
 * Спокойный, плотнее детских экранов; лимиты 5 и 25 слов здесь не действуют, числа больше 100
 * разрешены. Сверху вниз: чему учит игра, что пройдено (факты без оценок ребёнка), настройки, бонус,
 * демо для проверки, удаление данных. Всё, что заметно меняет прогресс, — с подтверждением.
 */
@Composable
fun AdultScreen(s: GameState, onBack: () -> Unit) {
    val a = app()
    BackHandler { onBack() }
    SoftScreen(onBack = onBack, backDescription = a.t("common.back"), wallet = null) { _ ->
        val scroll = rememberScrollState()
        Column(
            Modifier.fillMaxSize().padding(horizontal = FinniDimens.ScreenPadding).scrollHint(scroll).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Txt(a.t("adult.title"), FinniText.Title)
            Teach()
            Done(s)
            Settings(s)
            Bonus(s)
            DemoBlock(onBack)
            Wipe(onBack)
            Box(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().softPlate(FinniDimens.RadiusCard - 8.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Txt(title, AdultHead)
        content()
    }
}

/** Шесть компетенций из концепта (раздел «Компетенции и где они закрываются»). */
@Composable
private fun Teach() {
    val a = app()
    Section(a.t("adult.teach")) { (1..6).forEach { Txt("$it. " + a.t("adult.teach.$it"), AdultBody) } }
}

/**
 * Пройдено: глава и неделя, шесть заданий по трём темам ТЗ 2.5.8 с отметкой, пройдено или нет, и факты
 * по неделям — сколько раз было действие, без оценок ребёнка.
 */
@Composable
private fun Done(s: GameState) {
    val a = app()
    val g = a.game
    Section(a.t("adult.done")) {
        Txt(
            if (s.phase == Phase.GAME_OVER || s.phase == Phase.FREE_PLAY) a.t("adult.whereEnd")
            else a.f("adult.where", "ch" to s.progress.chapter, "n" to g.weekNumber(s)),
            AdultStrong,
        )
        listOf("plan", "save", "shop").forEach { theme ->
            Txt(a.t("adult.theme.$theme"), AdultStrong)
            g.content.tasks.values.filter { it.theme == theme }.forEach { task ->
                val done = task.id in s.progress.doneTasks
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { if (done) CheckMark(20.dp) }
                    Txt("${task.id}. ${a.t(task.title)} — ${a.t(if (done) "adult.taskDone" else "adult.taskOpen")}", AdultBody, Modifier.weight(1f))
                }
            }
        }
        val h = s.progress.history
        if (h.isEmpty()) Txt(a.t("adult.facts.none"), AdultBody)
        else {
            Txt(a.f("adult.facts.care", "n" to h.count { it.care }, "total" to h.size), AdultBody)
            Txt(a.f("adult.facts.save", "n" to h.count { it.fact.save > 0 }, "total" to h.size), AdultBody)
        }
    }
}

/** Сложность «Проще / Сложнее» (I42, I49 A7) и анимации (перекрывает системную настройку); звуков нет. */
@Composable
private fun Settings(s: GameState) {
    val a = app()
    val g = a.game
    Section(a.t("adult.settings")) {
        Txt(a.t("adult.level"), AdultStrong)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Toggle(a.t("adult.level.easy"), !s.profile.senior) { a.act { g.setDifficulty(it, senior = false) } }
            Toggle(a.t("adult.level.hard"), s.profile.senior) { a.act { g.setDifficulty(it, senior = true) } }
        }
        Txt(a.t("adult.level.about"), AdultBody)
        Txt(a.t("adult.anim"), AdultStrong)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Toggle(a.t("adult.anim.on"), s.profile.animationOn) { a.setAnimations(true) }
            Toggle(a.t("adult.anim.off"), !s.profile.animationOn) { a.setAnimations(false) }
        }
        Txt(a.t("adult.sound"), AdultBody)
    }
}

/** Выбор из двух: выбранный — рамкой, подложкой и галочкой, не только цветом (гайд §10.3). */
@Composable
private fun Toggle(text: String, selected: Boolean, onClick: () -> Unit) {
    val r = FinniDimens.RadiusSmall + 4.dp
    PressBox(onClick, Modifier.semantics { this.selected = selected }, shape = RoundedCornerShape(r), description = text) { m ->
        Row(
            m.heightIn(min = FinniDimens.MinTouch).softPlate(r).picked(selected, r).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) CheckMark(18.dp)
            Txt(text, FinniText.Button)
        }
    }
}

/** Бонус взрослого (I49, F12.3): +5 в копилку раз в игровую неделю, после — объяснение, почему недоступно. */
@Composable
private fun Bonus(s: GameState) {
    val a = app()
    val g = a.game
    Section(a.t("adult.bonus")) {
        Txt(a.t("adult.bonus.about"), AdultBody)
        val w = s.week
        when {
            g.canBonus(s) -> SecondaryButton(a.t("adult.bonus.add"), onClick = { a.act(g::adultBonus) })
            w == null || (s.phase != Phase.WEEK && s.phase != Phase.AFTER_SUMMARY) -> Txt(a.t("adult.bonus.wait"), AdultStrong)
            w.bonus > 0 -> Txt(a.t("adult.bonus.used"), AdultStrong)
            else -> Txt(a.t("adult.bonus.full"), AdultStrong)
        }
    }
}

/**
 * Демо для проверки (ТЗ 2.5.13, 2.6, 2.5.8): готовый питомец, переход к началу любой из восьми недель с
 * состоянием канонического пути — так любое задание и любая стадия доступны сразу; сброс к исходному
 * тестовому профилю. Игра ребёнка сохраняется отдельно и возвращается кнопкой.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DemoBlock(onBack: () -> Unit) {
    val a = app()
    var ask by remember { mutableStateOf(false) }
    val inDemo = a.inDemo
    Section(a.t("adult.demo")) {
        Txt(a.t(if (inDemo) "adult.demo.on" else "adult.demo.about"), AdultBody)
        if (!inDemo) {
            if (ask) Confirm(a.t("adult.demo.ask"), a.t("adult.yes"), onYes = { ask = false; a.startDemo(); onBack() }, onNo = { ask = false })
            else SecondaryButton(a.t("adult.demo.start"), onClick = { ask = true })
        } else {
            Txt(a.t("adult.demo.week"), AdultStrong)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..8).forEach { n ->
                    val r = FinniDimens.RadiusSmall + 4.dp
                    PressBox({ a.demoWeek(n); onBack() }, shape = RoundedCornerShape(r), description = a.t("adult.demo.week") + " $n") { m ->
                        Box(m.size(FinniDimens.MinTouch + 8.dp).softPlate(r), contentAlignment = Alignment.Center) { Txt(n.toString(), FinniText.Subtitle) }
                    }
                }
            }
            SecondaryButton(a.t("adult.demo.reset"), onClick = { a.startDemo(); onBack() })
            SecondaryButton(a.t("adult.demo.back"), onClick = { a.endDemo(); onBack() })
        }
    }
}

/** Удалить данные игры — с подтверждением (U8); после — первый запуск. Здесь же — что хранится (ТЗ 3.5, D9). */
@Composable
private fun Wipe(onBack: () -> Unit) {
    val a = app()
    var ask by remember { mutableStateOf(false) }
    Section(a.t("adult.wipe")) {
        Txt(a.t("adult.wipe.about"), AdultBody)
        if (ask) Confirm(a.t("adult.wipe.ask"), a.t("adult.wipe.yes"), onYes = { ask = false; a.wipe(); onBack() }, onNo = { ask = false })
        else SecondaryButton(a.t("adult.wipe"), onClick = { ask = true })
    }
}

/** Подтверждение на месте кнопки: вопрос и две одинаковые кнопки. */
@Composable
private fun Confirm(question: String, yes: String, onYes: () -> Unit, onNo: () -> Unit) {
    val a = app()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt(question, AdultStrong.copy(color = FinniColors.Ink))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { SecondaryButton(yes, onClick = onYes) }
            Box(Modifier.weight(1f)) { SecondaryButton(a.t("adult.cancel"), onClick = onNo) }
        }
    }
}
