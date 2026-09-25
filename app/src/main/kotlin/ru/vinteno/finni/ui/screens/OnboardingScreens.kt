package ru.vinteno.finni.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import ru.vinteno.finni.ui.components.FitColumn
import ru.vinteno.finni.ui.components.SoftCard
import ru.vinteno.finni.ui.components.SoftScreen
import ru.vinteno.finni.ui.components.bigFont
import ru.vinteno.finni.ui.components.flex
import ru.vinteno.finni.ui.components.scrollHint
import ru.vinteno.finni.ui.components.textHeight
import ru.vinteno.finni.core.engine.Direction
import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.engine.Enough
import ru.vinteno.finni.core.model.Fur
import ru.vinteno.finni.ui.app
import ru.vinteno.finni.ui.components.Coin
import ru.vinteno.finni.ui.components.GameScreen
import ru.vinteno.finni.ui.components.Icon
import ru.vinteno.finni.ui.components.directionStyle
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
 * Над строкой — метка направления так, как она выглядит на плане: иконка и слово «Нужное»,
 * «Хочу», «Копилка». Иначе «желаемое» из знакомства и «Хочу» на плане — два разных слова, и
 * ребёнок не связывает их (QA-U6). Внизу три точки: какая карточка из трёх — формой, не цветом.
 */
@Composable
fun IntroScreen() {
    val a = app()
    var i by rememberSaveable { mutableIntStateOf(0) }
    val cards = listOf(
        Triple("kasha", "intro.card.need", Direction.NEED),
        Triple("kacheli", "intro.card.want", Direction.WANT),
        Triple("kopilka", "intro.card.save", null),
    )
    val (pic, key, dir) = cards[i]
    // Системное «назад» листает карточки назад, а не закрывает игру (QA-B15).
    BackHandler(enabled = i > 0) { i-- }
    GameScreen(
        title = null, wallet = null, onBack = null, backDescription = a.t("common.back"),
        bottom = {
            MainButton(a.t("intro.next"), {
                if (i < cards.lastIndex) i++ else a.act(a.game::seeIntro)
            })
        },
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) { Picture(pic, 160.dp) }
        val st = directionStyle(dir)
        val chip = RoundedCornerShape(FinniDimens.RadiusButton)
        Row(
            Modifier.padding(top = 12.dp).background(st.bg, chip).border(FinniDimens.Outline, st.color, chip)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(st.icon, st.color, 32.dp)
            Txt(a.t(st.labelKey), FinniText.Subtitle)
        }
        a.t(key).split(". ").forEach { part ->
            Txt(part.trimEnd('.') + ".", FinniText.Title)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            cards.indices.forEach { n ->
                val on = n == i
                Box(
                    Modifier.size(12.dp).background(if (on) FinniColors.Ink else FinniColors.Surface, CircleShape)
                        .border(FinniDimens.Outline, FinniColors.StrokeStrong, CircleShape),
                )
            }
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
            // Фигура 88 dp шириной (около 190 dp ростом): на экране 360 × 640 поле «Имя» и «Готово» видны без прокрутки.
            Finni(fur, acc, Modifier.width(88.dp), reaction = if (key > 0) Reaction.NOTICE else null, reactionKey = key)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FinniDimens.CardGap)) {
            Fur.entries.forEach { f ->
                PressCard({ fur = f; key++ }, Modifier.weight(1f).heightIn(min = 56.dp), selected = fur == f) {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(40.dp).background(furSwatch(f), CircleShape).border(2.dp, Color(0xFF5A3A22), CircleShape))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FinniDimens.CardGap)) {
            Accessory.entries.forEach { ac ->
                PressCard({ acc = ac; key++ }, Modifier.weight(1f), selected = acc == ac) {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        Finni(fur, ac, Modifier.width(48.dp), animate = false, headOnly = true)
                    }
                }
            }
        }
        // Имя — одно слово: оно стоит подлежащим во фразах до 5 слов (инвариант 10, решение I10).
        NameField(name, a.t("create.nameField")) { name = it.filter { c -> c.isLetter() || c == '-' }.take(12) }
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
 * Выбор цели — сценарий §6.4. Три одинаковые карточки в ряд: подарок крупно на светлом круге,
 * название и ценник. Размер, порядок и вид у всех трёх одни, ни одна не помечена рекомендуемой.
 * Строки «хватит ли» под карточками нет (I39): неизменная строка на экране выбора — скрытая
 * рекомендация; она живёт под «Копилкой» на плане. Кнопка «Выбрать» появляется после касания,
 * её место занято заранее: карточки не сдвигаются. Движения нет, кроме отклика нажатия.
 */
@Composable
fun GoalScreen() {
    val a = app()
    val ch = a.game.content.chapter1
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val big = bigFont()
    SoftScreen(
        onBack = null, backDescription = a.t("common.back"), wallet = null,
        bottom = {
            val hidden = picked == null
            MainButton(
                a.t("goal.choose"),
                { picked?.let { id -> a.act { a.game.chooseGoal(it, id) } } },
                if (hidden) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier,
            )
        },
    ) { viewport ->
        val scroll = rememberScrollState()
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = FinniDimens.ScreenPadding).scrollHint(scroll).verticalScroll(scroll)) {
            val width = maxWidth
            val goals = ch.goalIds.map { a.game.content.goal(it) }
            val names = goals.map { cardName(it.name) }
            FitColumn(viewport) {
                Box(Modifier.height(FinniDimens.TitleTop - FinniDimens.ScreenPadding))
                Txt(a.t("goal.title"), FinniText.Title)
                Box(Modifier.height(8.dp))
                // До этого экрана ребёнок не знает ни Киру, ни праздника (QA-U7).
                Txt(a.t("goal.why"), FinniText.Body)
                Box(Modifier.height(FinniDimens.CardGap + 4.dp))
                if (big) {
                    // Крупный шрифт: карточки столбиком, картинка слева, название и цена справа.
                    Column(verticalArrangement = Arrangement.spacedBy(FinniDimens.CardGap)) {
                        goals.forEachIndexed { i, goal ->
                            SoftCard(picked == goal.id, { picked = goal.id }, Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp).padding(end = 24.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    GoalPicture(goal.id, goal.name, GOAL_LIST_PIC)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Txt(names[i], GoalName)
                                        GoalPrice(goal.price)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Три карточки одной ширины и высоты: место под название — по самому длинному.
                    val cw = (width - GOAL_GAP * 2) / 3
                    val inner = cw - GOAL_PAD * 2
                    val nameH = textHeight(names, GoalName, inner)
                    val frame = GOAL_PAD * 2 + 8.dp + nameH + 6.dp + GOAL_TAG
                    Row(
                        Modifier.fillMaxWidth().flex(min = frame + GOAL_PIC_MIN, max = frame + inner),
                        horizontalArrangement = Arrangement.spacedBy(GOAL_GAP),
                    ) {
                        goals.forEachIndexed { i, goal ->
                            SoftCard(picked == goal.id, { picked = goal.id }, Modifier.weight(1f).fillMaxHeight()) {
                                Column(
                                    Modifier.fillMaxSize().padding(GOAL_PAD),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        GoalPicture(goal.id, goal.name, minOf(maxWidth, maxHeight))
                                    }
                                    Box(Modifier.height(8.dp))
                                    Box(Modifier.height(nameH).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                        Txt(names[i], GoalName.copy(textAlign = TextAlign.Center))
                                    }
                                    Box(Modifier.height(6.dp))
                                    Box(Modifier.height(GOAL_TAG), contentAlignment = Alignment.Center) { GoalPrice(goal.price) }
                                }
                            }
                        }
                    }
                }
                Box(Modifier.flex(min = 8.dp))
            }
        }
    }
}

private val GOAL_GAP = 8.dp
private val GOAL_PAD = 8.dp
private val GOAL_TAG = 40.dp
private val GOAL_PIC_MIN = 56.dp
private val GOAL_LIST_PIC = 72.dp
private val GoalName = FinniText.Body.copy(fontWeight = FontWeight.Bold)

/** «для Киры» не разрывается: на узкой карточке «Киры» одна на строке читалась бы отдельно. */
private fun cardName(name: String) = name.replace("для ", "для\u00A0")

/** Подарок на светлом круге, как вещь на витрине. */
@Composable
private fun GoalPicture(id: String, name: String, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFFF4E6CF), Color(0x00F4E6CF))))
        }
        Picture(id, size * 0.84f, description = name)
    }
}

/** Ценник цели: монета и цена на кремовой подложке. */
@Composable
private fun GoalPrice(price: Int) {
    Row(
        Modifier.background(FinniColors.BgSand, RoundedCornerShape(FinniDimens.RadiusSmall + 4.dp)).padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Coin(24.dp)
        Txt(price.toString(), FinniText.Subtitle)
    }
}

/** Три состояния строки «хватит ли» — одна формулировка на всю игру (I13, I14). */
fun enoughKey(e: Enough) = when (e) {
    Enough.SURPLUS -> "enough.surplus"
    Enough.EXACT -> "enough.exact"
    Enough.SHORT -> "enough.short"
}
