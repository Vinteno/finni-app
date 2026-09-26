package ru.vinteno.finni.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.ui.app
import ru.vinteno.finni.ui.components.CheckMark
import ru.vinteno.finni.ui.components.Coin
import ru.vinteno.finni.ui.components.CoinRoll
import ru.vinteno.finni.ui.components.Picture
import ru.vinteno.finni.ui.components.PressBox
import ru.vinteno.finni.ui.components.ProgressCells
import ru.vinteno.finni.ui.components.SecondaryButton
import ru.vinteno.finni.ui.components.SoftScreen
import ru.vinteno.finni.ui.components.Txt
import ru.vinteno.finni.ui.components.picked
import ru.vinteno.finni.ui.components.scrollHint
import ru.vinteno.finni.ui.components.softPlate
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniText

/** Вкладки дневника — по одной на экран (I49, F4). */
private enum class Tab(val key: String) { WEEK("diary.week"), BUYS("diary.buys"), TASKS("diary.tasks"), GOAL("diary.goal"), WORDS("diary.words") }

/** Девять терминов гайда 12.8 — словами игры: «Нужное» и «Хочу» вместо «обязательного» и «желаемого». */
private val WORDS = listOf("income", "spend", "need", "want", "plan", "save", "goal", "rest", "deposit")
private const val WORDS_PER_PAGE = 3

/** Картинка задания в дневнике — предмет, вокруг которого оно было. */
private fun taskPicture(id: String) = when (id) {
    "F1" -> "kasha"
    "F5" -> "myachik"
    "F4" -> "kopilka"
    "F3" -> "kurtka"
    "F2" -> "korobka"
    else -> "deposit"
}

/**
 * Дневник — экран для ребёнка (I49, F4; ТЗ 2.5.11): итог прошлой недели, покупки этой недели, пройденные
 * задания, прогресс цели и словарь из девяти терминов. Вход — календарь на стене, когда он не ведёт к
 * итогу или следующей неделе. Крупные картинки, текста мало: каждая вкладка — не больше 25 слов вместе
 * с названиями вкладок. Только факты, как на итоге: ни оценок, ни подсказок.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiaryScreen(s: GameState, onBack: () -> Unit) {
    val a = app()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    BackHandler { onBack() }
    SoftScreen(
        onBack = onBack,
        backDescription = a.t("common.back"),
        wallet = s.progress.wallet,
        bottom = {
            if (Tab.entries[tab] == Tab.WORDS) {
                val pages = (WORDS.size + WORDS_PER_PAGE - 1) / WORDS_PER_PAGE
                SecondaryButton(a.t("common.next"), onClick = { page = (page + 1) % pages })
            }
        },
    ) { viewport ->
        val scroll = rememberScrollState()
        Column(
            Modifier.fillMaxSize().padding(horizontal = FinniDimens.ScreenPadding).scrollHint(scroll).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.height(4.dp))
            // Вкладки: выбранная — рамкой и подложкой, не только цветом.
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Tab.entries.forEachIndexed { i, t ->
                    val r = FinniDimens.RadiusSmall + 4.dp
                    PressBox({ tab = i; page = 0 }, shape = RoundedCornerShape(r), description = a.t(t.key)) { m ->
                        Box(m.sizeIn(minWidth = FinniDimens.MinTouch, minHeight = FinniDimens.MinTouch).softPlate(r).picked(tab == i, r).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                            Txt(a.t(t.key), FinniText.Button)
                        }
                    }
                }
            }
            when (Tab.entries[tab]) {
                Tab.WEEK -> LastWeek(s)
                Tab.BUYS -> Buys(s)
                Tab.TASKS -> Tasks(s)
                Tab.GOAL -> GoalTab(s)
                Tab.WORDS -> Words(page)
            }
            Box(Modifier.height(8.dp))
        }
    }
}

/** Прошлая неделя — как на итоге: задумали, вышло, за задание; рядами монет одного масштаба, без кнопок. */
@Composable
private fun LastWeek(s: GameState) {
    val a = app()
    val last = s.progress.history.lastOrNull()
    if (last == null) { Txt(a.t("diary.noWeek"), FinniText.Subtitle); return }
    val scale = maxOf(last.plan.total, last.fact.total, last.reward, 1)
    Column(Modifier.fillMaxWidth().softPlate(FinniDimens.RadiusCard - 6.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("summary.planned" to last.plan.total, "summary.actual" to last.fact.total, "summary.reward" to last.reward).forEach { (key, v) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(a.t(key), FinniText.Button, Modifier.weight(1f))
                    Txt(v.toString(), FinniText.Subtitle)
                }
                CoinRoll(v, scale)
            }
        }
    }
}

/** Покупки этой недели — картинки с ценой: история покупок периода (ТЗ 2.5.6). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Buys(s: GameState) {
    val a = app()
    val bought = s.week?.purchases.orEmpty().map(a.game.content::item)
    if (bought.isEmpty()) { Txt(a.t("diary.noBuys"), FinniText.Subtitle); return }
    // Вещь с надбавкой — одной картинкой и общей ценой, как на полке: «каша с ягодами 8».
    val groups = bought.filter { it.addonOf == null || bought.none { b -> b.id == it.addonOf } }.map { base ->
        listOf(base) + bought.filter { it.addonOf == base.id }
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEach { group ->
            val key = group.joinToString("_") { it.id }
            val name = if (group.size > 1) a.t("shop.tier.$key") else group.first().name
            Column(
                Modifier.width(96.dp).softPlate(FinniDimens.RadiusCard - 8.dp).padding(8.dp).semantics(mergeDescendants = true) { contentDescription = name },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Picture(key, 72.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Coin(20.dp)
                    Txt(group.sumOf { it.price }.toString(), FinniText.Button)
                }
            }
        }
    }
}

/**
 * Задания: первой строкой — задание, которое идёт сейчас («Сейчас: …» — I47 п. 5, риск F3.1), ниже —
 * пройденные, картинкой с галочкой; название — для экранного диктора.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tasks(s: GameState) {
    val a = app()
    val g = a.game
    val now = g.weekTask(s)?.takeIf { s.week?.taskDone == false }
    // «Сейчас» и название задания — двумя строками: вместе фраза длиннее пяти слов.
    now?.let {
        Column {
            Txt(a.t("diary.now"), FinniText.Button)
            Txt(a.t(it.title), FinniText.Subtitle)
        }
    }
    val done = s.progress.doneTasks
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        done.forEach { id ->
            val title = a.t(g.content.task(id).title)
            Box(Modifier.width(96.dp).semantics(mergeDescendants = true) { contentDescription = title }) {
                Box(Modifier.fillMaxWidth().softPlate(FinniDimens.RadiusCard - 8.dp).padding(8.dp), contentAlignment = Alignment.Center) {
                    Picture(taskPicture(id), 72.dp)
                }
                CheckMark(24.dp, Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
        }
    }
}

/** Цель: картинка, клетки по 5 монет и «Накопили N из M». */
@Composable
private fun GoalTab(s: GameState) {
    val a = app()
    val goal = s.chapter.goalId?.let(a.game.content::goal) ?: return
    Column(
        Modifier.fillMaxWidth().softPlate(FinniDimens.RadiusCard - 6.dp).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Picture(goal.id, 120.dp, description = goal.name)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cells = (goal.price + 4) / 5
            val cell = ((maxWidth - 4.dp * (cells - 1)) / cells).coerceIn(16.dp, 32.dp)
            ProgressCells(minOf(s.progress.savings, goal.price) / 5, cells, cell = cell)
        }
        Txt(a.f("diary.saved", "n" to s.progress.savings, "goal" to goal.price), FinniText.Subtitle)
    }
}

/** Словарь: три термина на страницу — слово и одна фраза объяснения (гайд 12.8, ТЗ 2.5.11, 3.6). */
@Composable
private fun Words(page: Int) {
    val a = app()
    WORDS.drop(page * WORDS_PER_PAGE).take(WORDS_PER_PAGE).forEach { w ->
        Column(Modifier.fillMaxWidth().softPlate(FinniDimens.RadiusCard - 8.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Txt(a.t("word.$w"), FinniText.Subtitle)
            Txt(a.t("word.$w.x"))
        }
    }
}
