package ru.vinteno.finni.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.core.engine.Direction
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.Plan
import ru.vinteno.finni.ui.app
import ru.vinteno.finni.ui.components.Coin
import ru.vinteno.finni.ui.components.DirectionLabel
import ru.vinteno.finni.ui.components.FitColumn
import ru.vinteno.finni.ui.components.JAR_RATIO
import ru.vinteno.finni.ui.components.Jar
import ru.vinteno.finni.ui.components.MainButton
import ru.vinteno.finni.ui.components.PLANK
import ru.vinteno.finni.ui.components.Picture
import ru.vinteno.finni.ui.components.RoundButton
import ru.vinteno.finni.ui.components.ShelfPlank
import ru.vinteno.finni.ui.components.SoftScreen
import ru.vinteno.finni.ui.components.TAIL
import ru.vinteno.finni.ui.components.Txt
import ru.vinteno.finni.ui.components.UpTailPlate
import ru.vinteno.finni.ui.components.bigFont
import ru.vinteno.finni.ui.components.directionStyle
import ru.vinteno.finni.ui.components.flex
import ru.vinteno.finni.ui.components.scrollHint
import ru.vinteno.finni.ui.components.textHeight
import ru.vinteno.finni.ui.pet.Finni
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniText

/** Потолок числа на экране — инвариант 9. Сумма направления не растёт выше. */
private const val MAX_NUMBER = 99

/** Банки: не ниже 96 dp, не шире своей колонки. На крупном шрифте — маленькая банка в строке. */
private val JAR_MIN = 96.dp
private val JAR_ROW = 64.dp

/** «−» и «+» — по 48 dp в одну строку под каждой банкой (I40). */
private val STEP = FinniDimens.MinTouch
private val COL_GAP = 8.dp

/** Голова Финни у заголовка: ширина к высоте по рамке головы рига. */
private const val HEAD_RATIO = 312f / 459f

/**
 * Экран плана — ядро продукта, сценарий главы 1, шаг 3. Три одинаковые банки на одной полке в
 * неизменном порядке: Нужное, Хочу, Копилка. В банке — монеты рядами по 5, больше монет — выше
 * столбик; под банкой число и «− +». Черновик 10 / 10 / 10 или то, что выбрано на прошлом итоге.
 * Превышение разрешено: в банке просто больше монет, остаток показан строкой, подтверждение закрыто,
 * лишнее убирает сам ребёнок. Ни одного движения на экране, Финни не реагирует на суммы —
 * animation-howto.md §10.
 */
@Composable
fun PlanScreen(s: GameState, onBack: () -> Unit, onConfirmed: () -> Unit) {
    val a = app()
    val w = s.week ?: return
    val plan = w.plan
    val frozen = w.planConfirmed
    val wallet = s.progress.wallet
    val left = wallet - plan.total
    val big = bigFont()

    // Инвариант 9: любое число на экране не выше 100, включая «Разложено N из W». Поэтому «+»
    // не поднимает сумму плана выше 99; превышение кошелька при этом остаётся возможным (E07).
    fun set(p: Plan) = if (p.total > MAX_NUMBER && p.total > plan.total) false else a.act { a.game.setPlan(it, p) }

    val dirs: List<Triple<Direction?, Int, (Int) -> Plan>> = listOf(
        Triple(Direction.NEED, plan.need, { v -> plan.copy(need = v) }),
        Triple(Direction.WANT, plan.want, { v -> plan.copy(want = v) }),
        Triple(null, plan.save, { v -> plan.copy(save = v) }),
    )
    // Масштаб один на три банки: по доходу недели или по самому большому числу, что больше.
    val scaleMax = maxOf(a.game.content.chapter1.income, plan.need, plan.want, plan.save)

    SoftScreen(
        onBack = onBack,
        backDescription = a.t("common.back"),
        wallet = wallet,
        // Строка остатка — над кнопкой, всегда на виду: она и объясняет, почему кнопка закрыта.
        // Место под неё — по самому длинному варианту: банки не прыгают, когда строка меняется.
        bottomGap = 8.dp,
        // Строка остатка — над кнопкой, всегда на виду: она и объясняет, почему кнопка закрыта. Над ней —
        // «Я хочу есть», когда в «Нужном» ноль. Место под обе — по самым длинным вариантам:
        // банки не прыгают, когда строка меняется.
        bottom = if (frozen) null else ({
            val zero = a.f("plan.needZero", "name" to s.profile.petName)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val reserve = textHeight(listOf(zero), FinniText.Body, maxWidth) + 4.dp + textHeight(restVariants(wallet), FinniText.Subtitle, maxWidth)
                Column(Modifier.fillMaxWidth().height(reserve), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom)) {
                    if (plan.need == 0) Txt(zero)
                    Txt(
                        when {
                            left > 0 -> a.f("plan.left", "n" to left)
                            left == 0 -> a.t("plan.done")
                            else -> a.f("plan.over", "sum" to plan.total, "wallet" to wallet, "n" to -left)
                        },
                        FinniText.Subtitle,
                    )
                }
            }
            MainButton(a.t("plan.confirm"), onClick = { if (a.act(a.game::confirmPlan)) onConfirmed() }, enabled = a.game.canConfirmPlan(s))
        }),
    ) { viewport ->
        val scroll = rememberScrollState()
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = FinniDimens.ScreenPadding).scrollHint(scroll).verticalScroll(scroll)) {
            val width = maxWidth
            val colW = (width - COL_GAP * 2) / 3
            val titleH = textHeight(listOf(a.t("plan.title")), FinniText.Title, width - FinniDimens.PetHead - 12.dp)
            val counter: @Composable (Direction?, Int, (Int) -> Plan) -> Unit = { d, v, copy ->
                Counter(frozen, onMinus = { if (v > 0) set(copy(v - 1)) }, onPlus = { set(copy((v + 1).coerceAtMost(MAX_NUMBER))) })
            }
            FitColumn(viewport) {
                Box(Modifier.height(4.dp))
                // Заголовок с головой Финни: голова уступает место вторая, после банок.
                TitleRow(s, a.t("plan.title"), Modifier.fillMaxWidth().flex(min = titleH, max = maxOf(titleH, FinniDimens.PetHead / HEAD_RATIO), order = 2))
                Box(Modifier.height(4.dp))
                if (big) {
                    // Крупный шрифт: направление — строка, маленькая банка и слово слева, число и «− +» справа.
                    Column(verticalArrangement = Arrangement.spacedBy(FinniDimens.CardGap)) {
                        dirs.forEach { (d, v, copy) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                val st = directionStyle(d)
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.padding(start = 4.dp)) { Jar(v, scaleMax, st.bg, deep(d), JAR_ROW) }
                                    DirectionLabel(st.icon, st.color, a.t(st.labelKey))
                                }
                                Number(a.t(st.labelKey), v, Modifier.widthIn(min = 56.dp))
                                Box(Modifier.width(8.dp))
                                counter(d, v, copy)
                            }
                        }
                    }
                } else {
                    // Над банкой — иконка и слово; банки стоят на одной полке, под полкой число и «− +».
                    Columns(colW) { i -> dirs[i].first.let { d -> directionStyle(d).let { DirectionLabel(it.icon, it.color, a.t(it.labelKey)) } } }
                    Box(Modifier.height(2.dp))
                    BoxWithConstraints(Modifier.fillMaxWidth().flex(min = JAR_MIN, max = colW / JAR_RATIO, order = 1)) {
                        val jarH = minOf(maxHeight, colW / JAR_RATIO)
                        Columns(colW, Modifier.fillMaxSize(), fill = true) { i ->
                            val (d, v) = dirs[i]
                            Jar(v, scaleMax, directionStyle(d).bg, deep(d), jarH)
                        }
                    }
                    ShelfPlank(Modifier.fillMaxWidth())
                    Box(Modifier.height(4.dp))
                    Columns(colW) { i -> Number(a.t(directionStyle(dirs[i].first).labelKey), dirs[i].second) }
                    Columns(colW) { i -> val (d, v, copy) = dirs[i]; counter(d, v, copy) }
                }
                Box(Modifier.height(6.dp + TAIL))
                EnoughLine(s, if (big) width - 40.dp else width - colW / 2)
                Box(Modifier.flex(min = 0.dp))
            }
        }
    }
}

/** Все варианты строки остатка с самыми длинными числами — по ним резервируется место. */
@Composable
private fun restVariants(wallet: Int): List<String> {
    val a = app()
    return listOf(21, 22, 25).flatMap { n ->
        listOf(a.f("plan.left", "n" to n), a.f("plan.over", "sum" to MAX_NUMBER, "wallet" to wallet, "n" to n))
    } + a.t("plan.done")
}

/** Обводка крышки — глубокий цвет направления. */
private fun deep(d: Direction?) = when (d) {
    Direction.NEED -> FinniColors.NeedDeep
    Direction.WANT -> FinniColors.WantDeep
    null -> FinniColors.SaveDeep
}

/** Три колонки одной ширины [colW] под тремя банками: всё под банкой стоит строго под ней. */
@Composable
private fun Columns(colW: Dp, modifier: Modifier = Modifier.fillMaxWidth(), fill: Boolean = false, cell: @Composable (Int) -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(COL_GAP)) {
        repeat(3) { i ->
            Box(
                Modifier.width(colW).then(if (fill) Modifier.fillMaxSize() else Modifier),
                contentAlignment = if (fill) Alignment.BottomCenter else Alignment.Center,
            ) { cell(i) }
        }
    }
}

@Composable
private fun TitleRow(s: GameState, title: String, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val head = minOf(FinniDimens.PetHead, maxHeight * HEAD_RATIO)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(FinniDimens.PetHead), contentAlignment = Alignment.Center) {
                // На плане движения нет: голова неподвижна (animation-howto §10).
                Finni(s.profile.fur, s.profile.accessory, Modifier.width(head), animate = false, idle = false, headOnly = true)
            }
            Txt(title, FinniText.Title, Modifier.weight(1f))
        }
    }
}

/** Число под банкой — 28 sp; для экранного диктора с названием направления. */
@Composable
private fun Number(label: String, value: Int, modifier: Modifier = Modifier) {
    Txt(
        value.toString(),
        FinniText.Title.copy(textAlign = TextAlign.Center),
        modifier.semantics { contentDescription = "$label $value" },
    )
}

/** «−» слева, «+» справа, по 48 dp, в одну строку на любом шрифте. На подтверждённом плане их место пустое. */
@Composable
private fun Counter(frozen: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    val a = app()
    Row(
        if (frozen) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier,
        horizontalArrangement = Arrangement.spacedBy(COL_GAP),
    ) {
        RoundButton(Icons.Outlined.Remove, a.t("common.minus"), { if (!frozen) onMinus() }, Modifier.size(STEP, STEP + FinniDimens.LipSecondary))
        RoundButton(Icons.Outlined.Add, a.t("common.plus"), { if (!frozen) onPlus() }, Modifier.size(STEP, STEP + FinniDimens.LipSecondary))
    }
}

/**
 * Строка «хватит ли» (I13, I14) — плашка с хвостиком к банке «Копилка»: цель, монета и цена, вывод
 * одним словом. Единственная строка, что отвечает на нажатия; оценки в ней нет. Цель набрана до
 * плана недели — «Подарок уже готов.» (I32); счётчик не блокируется (QA-M2).
 */
@Composable
private fun EnoughLine(s: GameState, tailX: Dp) {
    val a = app()
    val goal = s.chapter.goalId?.let { a.game.content.goal(it) } ?: return
    UpTailPlate(tailX, Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picture(goal.id, 36.dp, description = goal.name)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Coin(22.dp)
                Txt(goal.price.toString(), FinniText.Button)
            }
            val line = if (a.game.goalReached(s)) a.t("enough.ready") else a.t(enoughKey(a.game.enoughForGoal(s)))
            Txt(line, EnoughText, Modifier.weight(1f).padding(start = 4.dp))
        }
    }
}

/** Вывод в плашке «хватит» — жирным основным кеглем, как в макете: «Подарок уже готов.» — в одну строку. */
private val EnoughText = FinniText.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
