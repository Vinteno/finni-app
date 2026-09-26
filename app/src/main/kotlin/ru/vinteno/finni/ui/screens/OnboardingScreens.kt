package ru.vinteno.finni.ui.screens

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.core.engine.Direction
import ru.vinteno.finni.core.engine.Enough
import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.model.Fur
import ru.vinteno.finni.ui.app
import ru.vinteno.finni.ui.components.Coin
import ru.vinteno.finni.ui.components.DirectionTag
import ru.vinteno.finni.ui.components.directionTagWidth
import ru.vinteno.finni.ui.components.FitColumn
import ru.vinteno.finni.ui.components.FloorShadow
import ru.vinteno.finni.ui.components.FurSwatch
import ru.vinteno.finni.ui.components.Icon
import ru.vinteno.finni.ui.components.MainButton
import ru.vinteno.finni.ui.components.PickButton
import ru.vinteno.finni.ui.components.PlateText
import ru.vinteno.finni.ui.components.Picture
import ru.vinteno.finni.ui.components.ROOM_DEPTH
import ru.vinteno.finni.ui.components.SoftCard
import ru.vinteno.finni.ui.components.SoftScreen
import ru.vinteno.finni.ui.components.Thing
import ru.vinteno.finni.ui.components.Txt
import ru.vinteno.finni.ui.components.bigFont
import ru.vinteno.finni.ui.components.directionStyle
import ru.vinteno.finni.ui.components.drawRoom
import ru.vinteno.finni.ui.components.flex
import ru.vinteno.finni.ui.components.scrollHint
import ru.vinteno.finni.ui.components.softPlate
import ru.vinteno.finni.ui.components.textHeight
import ru.vinteno.finni.ui.components.textWidth
import ru.vinteno.finni.ui.components.thingRatio
import ru.vinteno.finni.ui.components.tray
import ru.vinteno.finni.ui.pet.Finni
import ru.vinteno.finni.ui.pet.Reaction
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniText
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import ru.vinteno.finni.core.model.GameState

/**
 * Знакомство — сценарий §6.1, облегчённая версия до истории-онбординга (I44): три карточки по одной —
 * нужное, желаемое, копилка; предметы и строки — каша, качели, копилка (I12). Ни механики, ни чисел.
 * Предмет крупно стоит на полу комнаты, строка — на мягкой плашке над кнопкой. Сверху вместо точек
 * три метки «Нужное», «Хочу», «Копилка» — теми же словами, что потом над банками плана (QA-U6):
 * какая карточка из трёх, видно формой и словом, не только цветом. Метки не нажимаются.
 * Плашка держит место под самую длинную из трёх строк: пол и предмет между карточками не прыгают.
 * Уступает место предмет, не мельче 96 dp. Крупный шрифт — метки столбиком, прокручивается середина.
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
    // Системное «назад» листает карточки назад, а не закрывает игру (QA-B15).
    BackHandler(enabled = i > 0) { i-- }
    val lines = cards.map { c -> a.t(c.second).split(". ").map { it.trimEnd('.') + "." } }
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val inner = maxWidth - FinniDimens.ScreenPadding * 2
        // Метки в ряд, пока каждое слово встаёт в свою метку одной строкой; нет (крупный шрифт) — столбиком.
        val column = cards.maxOf { directionTagWidth(a.t(directionStyle(it.third).labelKey)) } > (inner - 16.dp) / 3
        val textW = inner - INTRO_PAD_H * 2
        val plateH = INTRO_PAD_V * 2 + lines.maxOf { ls ->
            ls.fold(0.dp) { h, l -> h + textHeight(listOf(l), IntroText, textW) } + INTRO_LINE_GAP * (ls.size - 1)
        }
        val slotW = minOf(inner, INTRO_ITEM_MAX)
        SubcomposeLayout(Modifier.fillMaxSize()) { c ->
            val w = c.maxWidth
            val h = c.maxHeight
            val top = subcompose(0) {
                val tags: @Composable (Modifier, Int) -> Unit = { m, k ->
                    DirectionTag(cards[k].third, a.t(directionStyle(cards[k].third).labelKey), k == i, m)
                }
                if (column) Column(
                    Modifier.fillMaxWidth().padding(start = FinniDimens.ScreenPadding, end = FinniDimens.ScreenPadding, top = FinniDimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { cards.indices.forEach { tags(Modifier.fillMaxWidth(), it) } }
                else Row(
                    Modifier.fillMaxWidth().padding(start = FinniDimens.ScreenPadding, end = FinniDimens.ScreenPadding, top = FinniDimens.ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) { cards.indices.forEach { tags(Modifier.weight(1f), it) } }
            }.map { it.measure(Constraints(maxWidth = w)) }
            val bot = subcompose(1) {
                Box(Modifier.fillMaxWidth().padding(horizontal = FinniDimens.ScreenPadding).padding(top = 8.dp, bottom = FinniDimens.BottomGap)) {
                    MainButton(a.t("intro.next"), { if (i < cards.lastIndex) i++ else a.act(a.game::seeIntro) })
                }
            }.map { it.measure(Constraints(minWidth = w, maxWidth = w)) }
            val topH = top.maxOfOrNull { it.height } ?: 0
            val botH = bot.maxOfOrNull { it.height } ?: 0
            val midH = (h - topH - botH).coerceAtLeast(0)
            // Сверху вниз: зазор под метками, предмет, зазор до плашки, плашка, зазор до кнопки.
            val above = INTRO_GAP.roundToPx()
            val step = INTRO_STEP.roundToPx()
            val below = INTRO_GAP.roundToPx()
            val plate = plateH.roundToPx()
            val itemH = (midH - above - step - plate - below).coerceIn(INTRO_ITEM_MIN.roundToPx(), (slotW * INTRO_SLOT_K).roundToPx())
            val contentH = maxOf(midH, above + itemH + step + plate + below)
            val plateTop = contentH - below - plate
            val itemBottom = plateTop - step
            val mid = subcompose(2) {
                Box(
                    Modifier.fillMaxSize().padding(horizontal = FinniDimens.ScreenPadding).scrollHint(scroll).verticalScroll(scroll),
                ) {
                    Box(Modifier.fillMaxWidth().height(contentH.toDp())) {
                        Box(
                            Modifier.align(Alignment.TopCenter).offset(y = (itemBottom - itemH).toDp()).size(slotW, itemH.toDp()),
                            contentAlignment = Alignment.BottomCenter,
                        ) { IntroThing(cards[i].first, slotW, itemH.toDp()) }
                        Column(
                            Modifier.offset(y = plateTop.toDp()).fillMaxWidth().height(plateH)
                                .softPlate(FinniDimens.RadiusCard - 4.dp).padding(horizontal = INTRO_PAD_H, vertical = INTRO_PAD_V),
                            verticalArrangement = Arrangement.spacedBy(INTRO_LINE_GAP, Alignment.CenterVertically),
                        ) { lines[i].forEach { Txt(it, IntroText) } }
                    }
                }
            }.map { it.measure(Constraints.fixed(w, midH)) }
            // Пол — под предметом: предмет стоит на [ROOM_DEPTH] ниже стыка стены и пола; едет вместе с прокруткой.
            val floor = topH + itemBottom - ROOM_DEPTH.roundToPx()
            val back = subcompose(3) {
                Canvas(Modifier.fillMaxSize()) { drawRoom((floor - scroll.value).toFloat()) }
            }.map { it.measure(Constraints.fixed(w, h)) }
            layout(w, h) {
                back.forEach { it.place(0, 0) }
                mid.forEach { it.place(0, topH) }
                top.forEach { it.place(0, 0) }
                bot.forEach { it.place(0, h - botH) }
            }
        }
    }
}

/**
 * Предмет карточки в месте [width] × [height]: вписан целиком, низ — низ места, под ним тень. Три
 * предмета вписываются в одно и то же место — одного видимого размера. Каша — миска и каша в ней,
 * как дома.
 */
@Composable
private fun IntroThing(id: String, width: Dp, height: Dp) {
    val box = if (id == "kasha") "miska" else id
    val ratio = thingRatio(box)
    val w = minOf(width, height / ratio)
    Box(Modifier.size(w, w * ratio)) {
        FloorShadow(w * 0.92f, Modifier.align(Alignment.BottomCenter).offset(y = w * 0.04f))
        if (id == "kasha") {
            Thing("miska", w)
            Thing("food_kasha", w, box = "miska")
        } else Thing(id, w)
    }
}

private val IntroText = FinniText.Subtitle
private val INTRO_PAD_H = 18.dp
private val INTRO_PAD_V = 14.dp
private val INTRO_LINE_GAP = 4.dp
private val INTRO_GAP = 12.dp
/** От низа предмета до плашки: полоса пола видна, тень не ложится на плашку. */
private val INTRO_STEP = 14.dp
private val INTRO_ITEM_MIN = 96.dp
private val INTRO_ITEM_MAX = 220.dp
/** Высота места под предмет к его ширине: миска, качели и копилка в нём одного видимого размера. */
private const val INTRO_SLOT_K = 0.9f

/**
 * Внешность: первый из двух экранов создания питомца (решение 25.09). Питомец крупно стоит на полу
 * комнаты и говорит репликой сверху: сначала «Выбери мне цвет.», после первого выбора меха «Что мне
 * надеть?». Место под реплику держится по более длинной из двух, питомец не прыгает. Снизу кремовый
 * лоток: три кружка настоящего меха и три головы с аксессуаром, уже в выбранном мехе. Ровно девять
 * комбинаций, три меха на три аксессуара, ТЗ 2.6.
 *
 * Аксессуар можно перетащить на питомца или просто нажать: нажатие остаётся для доступности и для
 * автопрогона, который только нажимает. Питомца можно потаскать пальцем, он пружинит на место.
 * Смена меха или аксессуара проигрывает `замечает`.
 */
@Composable
fun LookScreen(s: GameState) {
    val a = app()
    var fur by rememberSaveable { mutableStateOf(s.profile.fur) }
    var acc by rememberSaveable { mutableStateOf(s.profile.accessory) }
    var furTouched by rememberSaveable { mutableStateOf(false) }
    var key by remember { mutableIntStateOf(0) }
    var ghost by remember { mutableStateOf<AccessoryGhost?>(null) }
    val pet = remember { PetHandle() }
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { pet.root = it.positionInRoot() }) {
        val inner = maxWidth - FinniDimens.ScreenPadding * 2
        val lines = listOf(a.t("look.color"), a.t("look.acc"))
        val speechH = textHeight(lines, QuestionText, inner - SPEECH_PAD_H * 2) + SPEECH_PAD_V * 2
        SubcomposeLayout(Modifier.fillMaxSize().clipToBounds()) { c ->
            val w = c.maxWidth
            val h = c.maxHeight
            val top = FinniDimens.TitleTop.roundToPx()
            val bubble = subcompose(LookSlot.BUBBLE) { Speech(if (furTouched) lines[1] else lines[0], inner, speechH) }
                .map { it.measure(Constraints(maxWidth = w)) }
            val topH = top + (bubble.maxOfOrNull { it.height } ?: 0) + 8.dp.roundToPx()
            val button = subcompose(LookSlot.BUTTON) {
                Box(Modifier.fillMaxWidth().padding(horizontal = FinniDimens.ScreenPadding).padding(top = 8.dp, bottom = FinniDimens.BottomGap)) {
                    MainButton(a.t("intro.next"), { a.act { st -> a.game.chooseLook(st, fur, acc) } })
                }
            }.map { it.measure(Constraints(minWidth = w, maxWidth = w)) }
            val buttonH = button.maxOfOrNull { it.height } ?: 0
            val gap = FEET_GAP.roundToPx()
            val pad = TRAY_PAD.roundToPx()
            val finniMin = FinniDimens.PetFull.roundToPx()
            val optMax = (h - topH - finniMin - gap - pad - buttonH).coerceAtLeast(FinniDimens.MinTouch.roundToPx())
            val options = subcompose(LookSlot.OPTIONS) {
                LookOptions(
                    fur, acc, scroll,
                    onFur = { fur = it; furTouched = true; key++ },
                    onAcc = { acc = it; key++ },
                    onDrag = { ghost = it },
                    onDrop = { g ->
                        if (g != null && pet.hit(g.at)) { acc = g.acc; key++ }
                        ghost = null
                    },
                )
            }.map { it.measure(Constraints(minWidth = w, maxWidth = w, maxHeight = optMax)) }
            val optH = options.maxOfOrNull { it.height } ?: 0
            val trayH = pad + optH + buttonH
            val finniH = (h - topH - gap - trayH).coerceIn(finniMin, PET_MAX.roundToPx())
            val trayTop = maxOf(h - trayH, topH + finniH + gap)
            val feet = trayTop - gap
            val finni = subcompose(LookSlot.FINNI) { DraggablePet(pet, fur, acc, finniH.toDp(), key, null) }
                .map { it.measure(Constraints()) }
            val floor = feet - ROOM_DEPTH.roundToPx()
            val back = subcompose(LookSlot.ROOM) { Canvas(Modifier.fillMaxSize()) { drawRoom(floor.toFloat()) } }
                .map { it.measure(Constraints.fixed(w, h)) }
            val tray = subcompose(LookSlot.TRAY) { Box(Modifier.fillMaxSize().tray()) }
                .map { it.measure(Constraints.fixed(w, trayH)) }
            val g = ghost
            val flying = subcompose(LookSlot.GHOST) {
                if (g != null) Finni(fur, g.acc, Modifier.height(GHOST_H), animate = false, headOnly = true)
            }.map { it.measure(Constraints()) }
            // Реплика говорит питомец, поэтому она над его головой; на высоком экране она не висит
            // под потолком, а опускается к нему.
            val petTop = feet - (finni.maxOfOrNull { it.height } ?: 0)
            val bubbleY = maxOf(top, petTop - (bubble.maxOfOrNull { it.height } ?: 0) - SPEECH_GAP.roundToPx())
            layout(w, h) {
                back.forEach { it.place(0, 0) }
                bubble.forEach { it.place((w - it.width) / 2, bubbleY) }
                finni.forEach { it.place((w - it.width) / 2, feet - it.height) }
                tray.forEach { it.place(0, trayTop) }
                options.forEach { it.place(0, trayTop + pad) }
                button.forEach { it.place(0, trayTop + pad + optH) }
                if (g != null) flying.forEach { p ->
                    val at = g.at - pet.root
                    p.place(at.x.roundToInt() - p.width / 2, at.y.roundToInt() - p.height / 2)
                }
            }
        }
    }
}

private enum class LookSlot { BUBBLE, BUTTON, OPTIONS, FINNI, ROOM, TRAY, GHOST }

/** Аксессуар в полёте за пальцем: какой и где палец, в координатах окна. */
private data class AccessoryGhost(val acc: Accessory, val at: Offset)

/**
 * Имя: второй экран создания. Собранный питомец крупно на полу и говорит «Придумай мне имя.». Под
 * ним поле, оно в фокусе с первого кадра: своё имя главное. Ниже «Или выбери:» и три готовых имени
 * разного звучания; касание ставит имя в поле, его можно поправить. «Готово» доступна, только когда
 * имя есть. Системное «назад» возвращает к внешности, выбор при этом сохраняется.
 *
 * Пока открыта клавиатура, питомец уменьшается до 96 dp, лоток держит свой размер и уходит под неё,
 * поле остаётся над ней. Имя: одно слово из букв и дефиса, до 12 знаков, оно стоит подлежащим во
 * фразах до пяти слов (инвариант 10, решение I10).
 */
@Composable
fun NameScreen(s: GameState) {
    val a = app()
    var name by rememberSaveable { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val pet = remember { PetHandle() }
    val ready = READY_NAMES.map(a::t)
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler { a.act(a.game::backToLook) }

    // Высота окна без клавиатуры: лоток держит её размер и уходит под клавиатуру целиком.
    val full = remember { IntArray(1) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { pet.root = it.positionInRoot() }) {
        val inner = maxWidth - FinniDimens.ScreenPadding * 2
        val question = a.t("create.nameField")
        val fieldH = maxOf(FinniDimens.MinTouch, textHeight(listOf(question), NameText, inner) + 16.dp)
        SubcomposeLayout(Modifier.fillMaxSize().clipToBounds()) { c ->
            val w = c.maxWidth
            val h = c.maxHeight
            full[0] = if (typing) maxOf(full[0], h) else h
            val top = FinniDimens.TitleTop.roundToPx()
            val bubble = subcompose(NameSlot.BUBBLE) { Speech(question, inner) }.map { it.measure(Constraints(maxWidth = w)) }
            val topH = top + (bubble.maxOfOrNull { it.height } ?: 0) + 8.dp.roundToPx()
            val field = subcompose(NameSlot.FIELD) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    NameField(
                        name, { name = it.filter { ch -> ch.isLetter() || ch == '-' }.take(12) }, question, focus,
                        onDone = { focusManager.clearFocus() }, onFocus = { typing = it },
                        modifier = Modifier.width(minOf(inner, NAME_FIELD_W)).height(fieldH),
                    )
                }
            }.map { it.measure(Constraints(maxWidth = w)) }
            val fieldArea = field.maxOfOrNull { it.height } ?: 0
            val button = subcompose(NameSlot.BUTTON) {
                Box(Modifier.fillMaxWidth().padding(horizontal = FinniDimens.ScreenPadding).padding(top = 8.dp, bottom = FinniDimens.BottomGap)) {
                    MainButton(a.t("create.done"), { a.act { st -> a.game.namePet(st, name) } }, enabled = name.isNotBlank())
                }
            }.map { it.measure(Constraints(minWidth = w, maxWidth = w)) }
            val buttonH = button.maxOfOrNull { it.height } ?: 0
            val gap = FEET_GAP.roundToPx()
            val pad = TRAY_PAD.roundToPx()
            val finniMin = FinniDimens.PetFull.roundToPx()
            val optMax = (full[0] - topH - finniMin - fieldArea - gap - pad - buttonH).coerceAtLeast(FinniDimens.MinTouch.roundToPx())
            val options = subcompose(NameSlot.OPTIONS) {
                NameOptions(ready, name, inner, scroll) { picked ->
                    name = picked
                    focusManager.clearFocus()
                }
            }.map { it.measure(Constraints(minWidth = w, maxWidth = w, maxHeight = optMax)) }
            val optH = options.maxOfOrNull { it.height } ?: 0
            val trayH = pad + optH + buttonH
            val finniH = (h - topH - fieldArea - gap - trayH).coerceIn(finniMin, PET_MAX.roundToPx())
            val trayTop = maxOf(h - trayH, topH + finniH + fieldArea + gap)
            val fieldTop = trayTop - gap - fieldArea
            // Клавиатура на крупном шрифте: поле над ней, всё выше поднимается вместе с ним.
            val lift = if (typing) (fieldTop + fieldArea + 8.dp.roundToPx() - h).coerceAtLeast(0) else 0
            val finni = subcompose(NameSlot.FINNI) {
                DraggablePet(pet, s.profile.fur, s.profile.accessory, finniH.toDp(), 0, name.ifBlank { null })
            }.map { it.measure(Constraints()) }
            // Питомец стоит на [ROOM_DEPTH] ниже стыка стены и пола, поле лежит на полу у его ног.
            val floor = fieldTop - lift - ROOM_DEPTH.roundToPx()
            val back = subcompose(NameSlot.ROOM) { Canvas(Modifier.fillMaxSize()) { drawRoom(floor.toFloat()) } }
                .map { it.measure(Constraints.fixed(w, h)) }
            val tray = subcompose(NameSlot.TRAY) { Box(Modifier.fillMaxSize().tray()) }
                .map { it.measure(Constraints.fixed(w, trayH)) }
            val petTop = fieldTop - (finni.maxOfOrNull { it.height } ?: 0)
            val bubbleY = maxOf(top, petTop - (bubble.maxOfOrNull { it.height } ?: 0) - SPEECH_GAP.roundToPx())
            layout(w, h) {
                back.forEach { it.place(0, 0) }
                bubble.forEach { it.place((w - it.width) / 2, bubbleY - lift) }
                finni.forEach { it.place((w - it.width) / 2, fieldTop - lift - it.height) }
                field.forEach { it.place(0, fieldTop - lift) }
                tray.forEach { it.place(0, trayTop - lift) }
                options.forEach { it.place(0, trayTop - lift + pad) }
                button.forEach { it.place(0, trayTop - lift + pad + optH) }
            }
        }
    }
}

private enum class NameSlot { BUBBLE, FIELD, BUTTON, OPTIONS, FINNI, ROOM, TRAY }

private val READY_NAMES = listOf("create.name.1", "create.name.2", "create.name.3")

/** Финни: ширина к росту по канве рига. */
private const val FINNI_K = 100f / 212f
/** Питомец на экранах создания: крупно, до половины экрана, но не выше этого. */
private val PET_MAX = 440.dp
private val GHOST_H = 72.dp
private val FEET_GAP = 8.dp
private val TRAY_PAD = 8.dp
private val OPTION_H = 52.dp
private val ACC_OPTION_H = 60.dp
private val NAME_FIELD_W = 240.dp
private val SPEECH_PAD_H = 16.dp
private val SPEECH_PAD_V = 10.dp
private val SPEECH_GAP = 8.dp
private val NameText = FinniText.Subtitle
private val QuestionText = FinniText.Body.copy(fontWeight = FontWeight.Bold)
/** Имена в кнопках: кеглем подписи, жирно. */
private val NameButtonText = PlateText
private val OPTION_GAP = 8.dp

/** Реплика питомца сверху экрана: мягкая плашка, фраза жирным кеглем текста. */
@Composable
private fun Speech(text: String, width: Dp, minHeight: Dp = 0.dp) {
    Box(
        Modifier.padding(horizontal = FinniDimens.ScreenPadding).widthIn(max = width).heightIn(min = minHeight)
            .softPlate(FinniDimens.RadiusButton).padding(horizontal = SPEECH_PAD_H, vertical = SPEECH_PAD_V),
        contentAlignment = Alignment.Center,
    ) { Txt(text, QuestionText) }
}

/**
 * Питомец, которого можно потаскать: едет за пальцем в пределах экрана и пружинит на место.
 * Касание без движения поворачивает уши, как в `замечает`. Где он на экране, знает [PetHandle]:
 * туда бросают аксессуар.
 */
private class PetHandle {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var poke by mutableIntStateOf(0)
    /** Где питомец и где сам экран в координатах окна. Не состояние: пишется при раскладке. */
    var bounds = Rect.Zero
    var root = Offset.Zero

    /** Бросили ли на питомца: с запасом, чтобы маленькие пальцы не промахивались. */
    fun hit(p: Offset): Boolean = bounds.inflate(HIT_SLOP).contains(p)

    suspend fun settle(motion: Boolean) {
        if (!motion) { x = 0f; y = 0f; return }
        coroutineScope {
            launch { animate(x, 0f, animationSpec = spring(dampingRatio = 0.45f, stiffness = 300f)) { v, _ -> x = v } }
            launch { animate(y, 0f, animationSpec = spring(dampingRatio = 0.45f, stiffness = 300f)) { v, _ -> y = v } }
        }
    }
}

private const val HIT_SLOP = 32f

@Composable
private fun DraggablePet(pet: PetHandle, fur: Fur, acc: Accessory, height: Dp, reactionKey: Int, description: String?) {
    val a = app()
    val scope = rememberCoroutineScope()
    val motion = a.animationOn
    Box(
        Modifier
            .offset { IntOffset(pet.x.roundToInt(), pet.y.roundToInt()) }
            .onGloballyPositioned { pet.bounds = it.boundsInRoot() }
            .pointerInput(Unit) { detectTapGestures { pet.poke++ } }
            .pointerInput(motion) {
                detectDragGestures(
                    onDragEnd = { scope.launch { pet.settle(motion) } },
                    onDragCancel = { scope.launch { pet.settle(motion) } },
                ) { change, d ->
                    change.consume()
                    pet.x = (pet.x + d.x).coerceIn(-size.width * 0.8f, size.width * 0.8f)
                    pet.y = (pet.y + d.y).coerceIn(-size.height * 0.3f, size.height * 0.05f)
                }
            },
    ) {
        Finni(
            fur, acc, Modifier.width(height * FINNI_K),
            reaction = if (reactionKey > 0) Reaction.NOTICE else null, reactionKey = reactionKey,
            animate = motion, earPoke = pet.poke, description = description,
        )
    }
}

/**
 * Лоток внешности: мех и аксессуар, два ряда кнопок выбора. Выбранная: `picked` и галочка.
 * Голову с аксессуаром можно утащить пальцем на питомца. Не помещается (крупный шрифт):
 * прокручивается, сверху зазор под галочки, торчащие над кнопками.
 */
@Composable
private fun LookOptions(
    fur: Fur,
    acc: Accessory,
    scroll: ScrollState,
    onFur: (Fur) -> Unit,
    onAcc: (Accessory) -> Unit,
    onDrag: (AccessoryGhost) -> Unit,
    onDrop: (AccessoryGhost?) -> Unit,
) {
    val a = app()
    Box(Modifier.fillMaxWidth().padding(horizontal = FinniDimens.ScreenPadding).scrollHint(scroll).verticalScroll(scroll)) {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(OPTION_GAP)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OPTION_GAP)) {
                Fur.entries.forEach { f ->
                    PickButton(fur == f, { onFur(f) }, Modifier.weight(1f).height(OPTION_H), description = a.t("a11y.fur.${f.name}")) { FurSwatch(f, 36.dp) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OPTION_GAP)) {
                Accessory.entries.forEach { ac ->
                    PickButton(acc == ac, { onAcc(ac) }, Modifier.weight(1f).height(ACC_OPTION_H), description = a.t("a11y.acc.${ac.name}")) {
                        DragSource(ac, onDrag, onDrop) {
                            Finni(fur, ac, Modifier.height(ACC_OPTION_H - 8.dp), animate = false, headOnly = true)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Откуда тащат аксессуар. Перетаскивание стоит внутри кнопки: сдвинул палец, и нажатие кнопки
 * отменяется, отпустил без сдвига, и сработало обычное нажатие.
 */
@Composable
private fun DragSource(
    ac: Accessory,
    onDrag: (AccessoryGhost) -> Unit,
    onDrop: (AccessoryGhost?) -> Unit,
    content: @Composable () -> Unit,
) {
    val origin = remember { arrayOf(Offset.Zero) }
    val at = remember { arrayOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { origin[0] = it.positionInRoot() }
            .pointerInput(ac) {
                detectDragGestures(
                    onDragStart = { p -> at[0] = origin[0] + p; onDrag(AccessoryGhost(ac, at[0])) },
                    onDragEnd = { onDrop(AccessoryGhost(ac, at[0])) },
                    onDragCancel = { onDrop(null) },
                ) { change, d ->
                    change.consume()
                    at[0] += d
                    onDrag(AccessoryGhost(ac, at[0]))
                }
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * «Или выбери:» и три готовых имени. Кнопки одной высоты и по ширине поровну; не помещаются
 * в строку, встают столбиком.
 */
@Composable
private fun NameOptions(ready: List<String>, name: String, width: Dp, scroll: ScrollState, onPick: (String) -> Unit) {
    val a = app()
    val pad = 8.dp
    val h = maxOf(FinniDimens.MinTouch, textHeight(ready, NameButtonText, width) + 12.dp)
    val row = ready.fold(0.dp) { acc, r -> acc + textWidth(r, NameButtonText) + pad * 2 } + OPTION_GAP * (ready.size - 1) <= width
    Box(Modifier.fillMaxWidth().padding(horizontal = FinniDimens.ScreenPadding).scrollHint(scroll).verticalScroll(scroll)) {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(OPTION_GAP)) {
            Txt(a.t("name.or"), PlateText)
            val button: @Composable (String, Modifier) -> Unit = { r, m ->
                PickButton(name == r, { onPick(r) }, m.height(h)) { Txt(r, NameButtonText) }
            }
            if (row) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OPTION_GAP)) {
                ready.forEach { r -> button(r, Modifier.weight(1f)) }
            } else Column(verticalArrangement = Arrangement.spacedBy(OPTION_GAP)) {
                ready.forEach { r -> button(r, Modifier.fillMaxWidth()) }
            }
        }
    }
}

/**
 * Поле имени под питомцем. Клавиатура: первая буква заглавная, без автозамены и подсказок,
 * «Готово» на клавиатуре закрывает её. Имя — одно слово, буквы и дефис,
 * до 12 знаков: оно стоит подлежащим во фразах до 5 слов (инвариант 10, решение I10).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NameField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    focus: FocusRequester,
    onDone: () -> Unit,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(FinniDimens.RadiusButton)
    InterceptPlatformTextInput(NoSuggestions) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = NameText.copy(textAlign = TextAlign.Center),
            cursorBrush = SolidColor(FinniColors.Action),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = modifier.focusRequester(focus)
                .onFocusChanged { onFocus(it.isFocused) }
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxSize().softPlate(FinniDimens.RadiusButton).border(FinniDimens.Outline, FinniColors.Action, shape)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { inner() }
            },
        )
    }
}

/**
 * Подсказок над клавиатурой нет: Compose не умеет их выключить, поэтому флаг ставится в описание поля
 * для клавиатуры. Подсказки подсовывают чужие имена и слова, а ребёнку нужно своё.
 */
@OptIn(ExperimentalComposeUiApi::class)
private object NoSuggestions : PlatformTextInputInterceptor {
    override suspend fun interceptStartInputMethod(
        request: PlatformTextInputMethodRequest,
        nextHandler: PlatformTextInputSession,
    ): Nothing = nextHandler.startInputMethod(object : PlatformTextInputMethodRequest {
        override fun createInputConnection(outAttributes: EditorInfo): InputConnection =
            request.createInputConnection(outAttributes).also {
                outAttributes.inputType = outAttributes.inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
    })
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
