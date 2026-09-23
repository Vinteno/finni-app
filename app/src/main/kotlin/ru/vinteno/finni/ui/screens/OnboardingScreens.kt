package ru.vinteno.finni.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.engine.Enough
import ru.vinteno.finni.core.model.Fur
import ru.vinteno.finni.ui.app
import ru.vinteno.finni.ui.components.Coin
import ru.vinteno.finni.ui.components.GameScreen
import ru.vinteno.finni.ui.components.MainButton
import ru.vinteno.finni.ui.components.Picture
import ru.vinteno.finni.ui.components.PressCard
import ru.vinteno.finni.ui.components.Txt
import ru.vinteno.finni.ui.pet.Finni
import ru.vinteno.finni.ui.pet.Reaction
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniText

/**
 * Знакомство — сценарий §6.1: три карточки по одной — нужное, желаемое, копилка; на каждой
 * предмет и строка. Карточки — каша, качели, копилка (I12). Ни механики, ни чисел, ни вопросов.
 */
@Composable
fun IntroScreen() {
    val a = app()
    var i by rememberSaveable { mutableIntStateOf(0) }
    val cards = listOf("kasha" to "intro.card.need", "kacheli" to "intro.card.want", "kopilka" to "intro.card.save")
    val (pic, key) = cards[i]
    GameScreen(
        title = null, wallet = null, onBack = null, backDescription = a.t("common.back"),
        bottom = {
            MainButton(a.t("intro.next"), {
                if (i < cards.lastIndex) i++ else a.act(a.game::seeIntro)
            })
        },
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { Picture(pic, 160.dp) }
        a.t(key).split(". ").forEach { part ->
            Txt(part.trimEnd('.') + ".", FinniText.Title)
        }
    }
}

/**
 * Создание Финни — сценарий §6.3: один силуэт, три цвета шерсти и три аксессуара — девять
 * комбинаций, поле «Имя». Имя не обязательно, по умолчанию «Финни». Смена — `замечает`.
 */
@Composable
fun CreatePetScreen() {
    val a = app()
    var fur by rememberSaveable { mutableStateOf(Fur.GINGER) }
    var acc by rememberSaveable { mutableStateOf(Accessory.SCARF) }
    var name by rememberSaveable { mutableStateOf("") }
    var key by remember { mutableIntStateOf(0) }
    GameScreen(
        title = a.t("create.title"), wallet = null, onBack = null, backDescription = a.t("common.back"),
        bottom = { MainButton(a.t("create.done"), { a.act { a.game.createPet(it, name, fur, acc) } }) },
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Finni(fur, acc, Modifier.width(140.dp), reaction = if (key > 0) Reaction.NOTICE else null, reactionKey = key)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FinniDimens.CardGap)) {
            Fur.entries.forEach { f ->
                PressCard({ fur = f; key++ }, Modifier.weight(1f).heightIn(min = 64.dp), selected = fur == f) {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(40.dp).background(furSwatch(f), CircleShape).border(2.dp, Color(0xFF5A3A22), CircleShape))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FinniDimens.CardGap)) {
            Accessory.entries.forEach { ac ->
                PressCard({ acc = ac; key++ }, Modifier.weight(1f), selected = acc == ac) {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        Finni(fur, ac, Modifier.width(56.dp), animate = false, headOnly = true)
                    }
                }
            }
        }
        NameField(name, a.t("create.nameField")) { name = it.take(16) }
    }
}

private fun furSwatch(f: Fur) = when (f) {
    Fur.GINGER -> Color(0xFFF8994E)
    Fur.BLUE -> Color(0xFF8FA8BE)
    Fur.BROWN -> Color(0xFFA9794F)
}

@Composable
private fun NameField(value: String, label: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(FinniDimens.RadiusButton)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = FinniText.Subtitle,
        cursorBrush = SolidColor(FinniColors.Action),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = FinniDimens.SecondaryButtonHeight)
                    .background(FinniColors.Surface, shape).border(FinniDimens.Outline, FinniColors.StrokeStrong, shape)
                    .padding(horizontal = FinniDimens.CardPadding),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Txt(label, FinniText.Subtitle.copy(color = FinniColors.InkMute))
                inner()
            }
        },
    )
}

/**
 * Выбор цели — сценарий §6.4. Три карточки одинаковые, появляются разом; под каждой — хватит ли
 * к дню рождения при взносе 10, до выбора (I13). Ни одна не помечена рекомендуемой.
 * Движения нет, кроме отклика нажатия.
 */
@Composable
fun GoalScreen() {
    val a = app()
    val ch = a.game.content.chapter1
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    GameScreen(
        title = a.t("goal.title"), wallet = null, onBack = null, backDescription = a.t("common.back"),
        bottom = if (picked == null) null else ({
            MainButton(a.t("goal.choose"), { picked?.let { id -> a.act { a.game.chooseGoal(it, id) } } })
        }),
    ) {
        ch.goalIds.forEach { id ->
            val goal = a.game.content.goal(id)
            PressCard({ picked = id }, Modifier.fillMaxWidth(), selected = picked == id) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Picture(id, 64.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(goal.name, FinniText.Subtitle)
                        val s = a.state.value
                        Txt(a.t(enoughKey(a.game.enoughPreview(s, id))), FinniText.Caption)
                    }
                    Row(Modifier.wrapContentWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Coin(20.dp)
                        Txt(goal.price.toString(), FinniText.Subtitle)
                    }
                }
            }
        }
    }
}

/** Три состояния строки «хватит ли» — одна формулировка на всю игру (I13, I14). */
fun enoughKey(e: Enough) = when (e) {
    Enough.SURPLUS -> "enough.surplus"
    Enough.EXACT -> "enough.exact"
    Enough.SHORT -> "enough.short"
}
