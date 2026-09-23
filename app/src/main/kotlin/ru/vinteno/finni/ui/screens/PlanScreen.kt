package ru.vinteno.finni.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.core.engine.Direction
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.Plan
import ru.vinteno.finni.ui.app
import ru.vinteno.finni.ui.components.GameScreen
import ru.vinteno.finni.ui.components.Icon
import ru.vinteno.finni.ui.components.MainButton
import ru.vinteno.finni.ui.components.RoundButton
import ru.vinteno.finni.ui.components.Txt
import ru.vinteno.finni.ui.components.bigFont
import ru.vinteno.finni.ui.components.directionStyle
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniText

/** Потолок числа на экране — инвариант 9. Сумма направления не растёт выше. */
private const val MAX_NUMBER = 99

/**
 * Экран плана — ядро продукта, сценарий главы 1, шаг 3. Три карточки в неизменном порядке,
 * черновик 10 / 10 / 10 или то, что выбрано на прошлом итоге. Превышение разрешено: остаток
 * показан, подтверждение закрыто, лишнее убирает сам ребёнок. Ни одного движения на экране,
 * Финни не реагирует на суммы — animation-howto.md §10.
 */
@Composable
fun PlanScreen(s: GameState, onBack: () -> Unit, onConfirmed: () -> Unit) {
    val a = app()
    val w = s.week ?: return
    val plan = w.plan
    val frozen = w.planConfirmed
    val wallet = s.progress.wallet
    val left = wallet - plan.total

    // Инвариант 9: любое число на экране не выше 100, включая «Разложил N из W». Поэтому «+»
    // не поднимает сумму плана выше 99; превышение кошелька при этом остаётся возможным (E07).
    fun set(p: Plan) = if (p.total > MAX_NUMBER && p.total > plan.total) false else a.act { a.game.setPlan(it, p) }

    GameScreen(
        title = a.t("plan.title"),
        wallet = wallet,
        onBack = onBack,
        backDescription = a.t("common.back"),
        titleAside = { PetHead(s) },
        bottom = if (frozen) null else ({
            MainButton(a.t("plan.confirm"), onClick = { if (a.act(a.game::confirmPlan)) onConfirmed() }, enabled = a.game.canConfirmPlan(s))
        }),
    ) {
        val cards: @Composable (Modifier, Boolean) -> Unit = { m, wide ->
            DirectionCard(Direction.NEED, plan.need, frozen, m, wide,
                onPlus = { set(plan.copy(need = (plan.need + 1).coerceAtMost(MAX_NUMBER))) },
                onMinus = { if (plan.need > 0) set(plan.copy(need = plan.need - 1)) })
            DirectionCard(Direction.WANT, plan.want, frozen, m, wide,
                onPlus = { set(plan.copy(want = (plan.want + 1).coerceAtMost(MAX_NUMBER))) },
                onMinus = { if (plan.want > 0) set(plan.copy(want = plan.want - 1)) })
            DirectionCard(null, plan.save, frozen, m, wide, note = a.t(enoughKey(a.game.enoughForGoal(s))),
                onPlus = { set(plan.copy(save = (plan.save + 1).coerceAtMost(MAX_NUMBER))) },
                onMinus = { if (plan.save > 0) set(plan.copy(save = plan.save - 1)) })
        }
        // Порядок всегда Нужное, Хочу, Копилка: слева направо, при крупном шрифте — сверху вниз.
        if (bigFont()) {
            Column(verticalArrangement = Arrangement.spacedBy(FinniDimens.CardGap)) { cards(Modifier.fillMaxWidth(), true) }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(FinniDimens.CardGap)) {
                cards(Modifier.weight(1f).fillMaxHeight(), false)
            }
        }
        if (plan.need == 0) {
            Txt(a.f("plan.needZero", "name" to s.profile.petName))
        }
        if (!frozen) {
            Txt(
                if (left >= 0) a.f("plan.left", "n" to left)
                else a.f("plan.over", "sum" to plan.total, "wallet" to wallet, "n" to -left),
                FinniText.Subtitle,
            )
        }
    }
}

/**
 * Карточка направления — гайд §10.3: иконка 32, подпись, сумма 28 sp, счётчик. При ширине
 * 104 dp «−» и «+» по 56 dp в ряд не помещаются, поэтому счётчик стоит столбиком: «+» над
 * суммой, «−» под ней. При крупном шрифте карточка — строка во всю ширину. Перерасход фон не меняет.
 */
@Composable
private fun DirectionCard(
    d: Direction?,
    value: Int,
    frozen: Boolean,
    modifier: Modifier,
    wide: Boolean,
    note: String? = null,
    onPlus: () -> Unit,
    onMinus: () -> Unit,
) {
    val a = app()
    val st = directionStyle(d)
    val shape = RoundedCornerShape(FinniDimens.RadiusCard)
    val box = modifier.background(st.bg, shape).border(FinniDimens.Outline, st.color, shape)
    val number: @Composable () -> Unit = {
        Txt(
            value.toString(),
            FinniText.Title.copy(textAlign = TextAlign.Center),
            Modifier.widthIn(min = 56.dp).semantics { contentDescription = "${a.t(st.labelKey)} $value" },
        )
    }
    if (wide) {
        // Во всю ширину: иконка с подписью строкой, под ними «− N +».
        Column(box.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(st.icon, st.color, 32.dp)
                Txt(a.t(st.labelKey), FinniText.Caption)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                if (!frozen) RoundButton(Icons.Outlined.Remove, a.t("common.minus"), onMinus)
                number()
                if (!frozen) RoundButton(Icons.Outlined.Add, a.t("common.plus"), onPlus)
            }
            note?.let { Txt(it, FinniText.Caption) }
        }
    } else {
        Column(
            box.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(st.icon, st.color, 32.dp)
            Txt(a.t(st.labelKey), FinniText.Caption.copy(textAlign = TextAlign.Center), Modifier.fillMaxWidth())
            if (!frozen) RoundButton(Icons.Outlined.Add, a.t("common.plus"), onPlus)
            number()
            if (!frozen) RoundButton(Icons.Outlined.Remove, a.t("common.minus"), onMinus)
            // Единственная строка экрана, которая отвечает на нажатия: хватит ли к событию (I14). Оценки нет.
            note?.let { Txt(it, FinniText.Caption.copy(textAlign = TextAlign.Center), Modifier.fillMaxWidth()) }
        }
    }
}
