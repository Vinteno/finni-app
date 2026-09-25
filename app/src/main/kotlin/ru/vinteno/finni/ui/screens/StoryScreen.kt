package ru.vinteno.finni.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.ui.app
import ru.vinteno.finni.ui.components.Coin
import ru.vinteno.finni.ui.components.KiraFigure
import ru.vinteno.finni.ui.components.MainButton
import ru.vinteno.finni.ui.components.SHELF_SURFACE
import ru.vinteno.finni.ui.components.Thing
import ru.vinteno.finni.ui.components.drawRoom
import ru.vinteno.finni.ui.components.textHeight
import ru.vinteno.finni.ui.components.thingRatio
import ru.vinteno.finni.ui.components.tray
import ru.vinteno.finni.ui.motion.Appear
import ru.vinteno.finni.ui.pet.Reaction
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniText
import kotlin.math.roundToInt

/**
 * Предыстория (решение 25.09): сцена в комнате вместо трёх карточек знакомства. Рассказывает сам
 * питомец, от первого лица и в настоящем времени, по одной фразе над головой. Тринадцать реплик,
 * около 45 секунд, пять сцен: знакомство, посылка от бабушки Тоси, три монеты, Кира в окне, переход
 * к внешности. Три направления объясняются внутри истории, а не отдельным экраном.
 *
 * Пропустить историю нельзя. Реплика сменяется сама через [BEAT_MS], касание реплики листает
 * дальше, но не раньше [MIN_BEAT_MS]: каждую фразу видно. Внизу три монеты, их можно трогать всё
 * время; в сцене про монеты ребёнок сам относит их в миску, к качелям и в копилку, а не отнёс,
 * монета улетает сама. Питомца можно потаскать, как на экранах создания.
 *
 * Питомец здесь ещё не выбран: он в виде по умолчанию, выбор идёт сразу после истории. Имени в
 * репликах нет, живой голос не может произнести имя, которое ребёнок придумает потом.
 *
 * [autoPlay] и [start] нужны снимочным тестам: сцена без часов и с нужной реплики.
 */
@Composable
fun StoryScreen(s: GameState, autoPlay: Boolean = true, start: Int = 0) {
    val a = app()
    val lines = remember { (1..STORY_LINES).map { a.t("story.$it") } }
    val last = STORY_LINES - 1
    var beat by rememberSaveable { mutableIntStateOf(start.coerceIn(0, last)) }
    var beatStart by remember { mutableLongStateOf(0L) }
    var reaction by remember { mutableStateOf<Reaction?>(null) }
    var reactionKey by remember { mutableIntStateOf(0) }
    var lookRight by remember { mutableStateOf(true) }
    fun react(r: Reaction, right: Boolean = lookRight) { reaction = r; lookRight = right; reactionKey++ }
    val scope = rememberCoroutineScope()
    val pet = remember { PetHandle() }
    val motion = a.animationOn
    val scene = sceneOf(beat)

    // Монеты: где каждая (центр, пиксели окна экрана), куда легла (цель или null) и заполнена ли цель.
    val coinAt = remember { mutableStateListOf<Offset?>(null, null, null) }
    val coinOn = remember { mutableStateListOf<Int?>(null, null, null) }
    val filled = remember { mutableStateListOf(false, false, false) }
    val home = remember { arrayOfNulls<Offset>(3) }
    val targets = remember { arrayOfNulls<Rect>(3) }

    suspend fun fly(coin: Int, to: Offset) {
        val from = coinAt[coin] ?: to
        if (!motion) { coinAt[coin] = to; return }
        animate(0f, 1f, animationSpec = tween(FLY_MS)) { k, _ -> coinAt[coin] = from + (to - from) * k }
    }
    fun land(coin: Int, target: Int) {
        coinOn[coin] = target
        filled[target] = true
        react(Reaction.HAPPY)
    }

    LaunchedEffect(beat) {
        beatStart = System.currentTimeMillis()
        when (beat) {
            0 -> react(Reaction.HAPPY)
            PARCEL_BEAT -> react(Reaction.NOTICE, right = false)
            KIRA_BEAT -> react(Reaction.NOTICE, right = true)
        }
        // Сцена монет: не отнёс монету к нужной вещи сам, она улетает туда сама.
        val target = beat - COINS_BEAT
        if (target in 0..2) launch {
            delay(AUTO_FLY_DELAY_MS)
            if (!filled[target]) {
                val coin = (0..2).firstOrNull { coinOn[it] == null }
                val rect = targets[target]
                if (coin != null && rect != null) {
                    fly(coin, rect.center)
                    land(coin, target)
                }
            }
        }
        if (autoPlay && beat < last) {
            delay(BEAT_MS)
            beat++
        }
    }
    fun next() {
        if (beat < last && System.currentTimeMillis() - beatStart >= MIN_BEAT_MS) beat++
    }

    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val inner = w - FinniDimens.ScreenPadding * 2
        val speechH = maxOf(FinniDimens.MinTouch, textHeight(lines, FinniText.Body, inner - 32.dp) + 20.dp)
        val bottomH = FinniDimens.MainButtonHeight + 8.dp + FinniDimens.BottomGap + 8.dp
        val ground = h - bottomH - GROUND_GAP
        val top = FinniDimens.TitleTop + speechH + 12.dp
        val petH = minOf(ground - top, h * 0.5f, STORY_PET_MAX).coerceAtLeast(FinniDimens.PetFull)
        val petW = petH * FINNI_K
        val petX = (w - petW) / 2
        val petTop = ground - petH

        // Дверь у левого края, частью за краем экрана; окно справа на стене; полка с копилкой под окном.
        val doorH = minOf(petH * 0.95f, ground - top)
        val doorW = doorH / thingRatio("dver")
        val doorX = -doorW * 0.45f
        val winW = w * 0.34f
        val winH = winW * thingRatio("okno")
        val winX = w - winW - 12.dp
        val winY = maxOf(top, petTop)
        val shelfW = winW * 0.9f
        val shelfH = shelfW * thingRatio("polka")
        val shelfX = w - shelfW - 16.dp
        val piggyW0 = winW * 0.9f * 0.42f
        val shelfY = winY + winH + piggyW0 * thingRatio("kopilka") + 6.dp
        val piggyW = shelfW * 0.42f
        val piggyH = piggyW * thingRatio("kopilka")
        val piggyX = shelfX + (shelfW - piggyW) / 2
        val piggyBottom = shelfY + shelfH * SHELF_SURFACE + 2.dp
        val bowlW = 68.dp
        val bowlH = bowlW * thingRatio("miska")
        val bowlX = minOf(petX + petW + 4.dp, w - bowlW - 8.dp)
        val swingW = 84.dp
        val swingH = swingW * thingRatio("kacheli")
        val swingX = maxOf(4.dp, petX - swingW * 0.8f)
        val parcelW = 56.dp
        val parcelH = parcelW * thingRatio("posylka")
        val parcelX = maxOf(doorX + doorW * 0.72f, petX - parcelW - 4.dp)

        with(density) {
            fun c(x: Dp, y: Dp) = Offset(x.toPx(), y.toPx())
            targets[0] = Rect(c(bowlX, ground - bowlH), c(bowlX + bowlW, ground)).inflate(24.dp.toPx())
            targets[1] = Rect(c(swingX, ground - swingH), c(swingX + swingW, ground)).inflate(16.dp.toPx())
            targets[2] = Rect(c(piggyX, piggyBottom - piggyH), c(piggyX + piggyW, piggyBottom)).inflate(24.dp.toPx())
            val trayY = h - bottomH / 2
            (0..2).forEach { i ->
                home[i] = c(w / 2 + COIN_STEP * (i - 1), trayY)
                if (coinAt[i] == null) coinAt[i] = home[i]
            }
        }

        val floorY = ground - GROUND_DEPTH
        Canvas(Modifier.fillMaxSize()) { drawRoom(with(density) { floorY.toPx() }) }

        Thing("dver", doorW, Modifier.offset(doorX, ground - doorH))
        // Окно, и в сцене с Кирой она смотрит из него: картинка обрезана рамкой окна.
        Box(Modifier.offset(winX, winY).size(winW, winH).clipToBounds()) {
            Thing("okno", winW)
            if (beat >= KIRA_BEAT) Appear("story_kira", Modifier.align(Alignment.BottomCenter)) {
                val bob = if (motion) {
                    rememberInfiniteTransition(label = "kira").animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "bob").value
                } else 0f
                KiraFigure(winH * 0.42f, Modifier.offset(y = winH * 0.28f - KIRA_BOB * bob), description = a.t("a11y.kira"))
            }
        }
        if (scene >= COINS_SCENE) {
            Appear("story_shelf", Modifier.offset(shelfX, shelfY)) { Thing("polka", shelfW) }
            Appear("story_piggy", Modifier.offset(piggyX, piggyBottom - piggyH)) { Glow(beat == COINS_BEAT + 2 && !filled[2]) { Thing("kopilka", piggyW) } }
            Appear("story_swing", Modifier.offset(swingX, ground - swingH)) { Glow(beat == COINS_BEAT + 1 && !filled[1]) { Thing("kacheli", swingW) } }
            Appear("story_bowl", Modifier.offset(bowlX, ground - bowlH)) { Glow(beat == COINS_BEAT && !filled[0]) { Thing("miska", bowlW) } }
        } else if (scene == PARCEL_SCENE) {
            Appear("story_parcel", Modifier.offset(parcelX, ground - parcelH)) { Thing("posylka", parcelW, description = a.t("a11y.parcel")) }
        }

        Box(Modifier.offset(petX, petTop)) {
            DraggablePet(pet, s.profile.fur, s.profile.accessory, petH, reaction, reactionKey, null, lookRight)
        }
        // Реплика над головой питомца; касание листает дальше.
        Box(Modifier.fillMaxWidth().offset(y = maxOf(FinniDimens.TitleTop, petTop - speechH - 8.dp)), contentAlignment = Alignment.TopCenter) {
            Speech(lines[beat], inner, speechH, onClick = ::next)
        }

        Box(Modifier.offset(y = h - bottomH).fillMaxWidth().height(bottomH).tray())
        if (beat == last) {
            Box(Modifier.offset(y = h - bottomH).fillMaxWidth().padding(horizontal = FinniDimens.ScreenPadding).padding(top = 8.dp)) {
                MainButton(a.t("intro.next"), { a.act(a.game::seeIntro) })
            }
        }

        // Монеты поверх всего: в лотке их можно трогать и таскать, у вещи они лежат.
        val coinPx = with(density) { COIN.toPx() }
        (0..2).forEach { i ->
            val at = coinAt[i] ?: return@forEach
            val onTarget = coinOn[i] != null
            if (beat == last && !onTarget) return@forEach
            val size = if (onTarget) COIN * 0.7f else COIN
            val half = with(density) { size.toPx() } / 2
            Box(
                Modifier
                    .offset { IntOffset((at.x - half).roundToInt(), (at.y - half).roundToInt()) }
                    .size(size)
                    .then(if (onTarget) Modifier else Modifier
                        .pointerInput(i) { detectTapGestures { scope.launch { hop(coinAt, i, coinPx, motion) } } }
                        .pointerInput(i, scene) {
                            detectDragGestures(
                                onDragEnd = {
                                    val drop = coinAt[i]
                                    val t = (0..2).firstOrNull { t -> scene >= COINS_SCENE && !filled[t] && drop != null && targets[t]?.contains(drop) == true }
                                    scope.launch {
                                        if (t != null) { fly(i, targets[t]!!.center); land(i, t) } else home[i]?.let { fly(i, it) }
                                    }
                                },
                                onDragCancel = { scope.launch { home[i]?.let { fly(i, it) } } },
                            ) { change, d ->
                                change.consume()
                                coinAt[i] = (coinAt[i] ?: Offset.Zero) + d
                            }
                        }),
            ) { Coin(size) }
        }
    }
}

/** Монета в лотке подпрыгивает от касания: ребёнку есть что трогать, пока идёт история. */
private suspend fun hop(coinAt: MutableList<Offset?>, i: Int, coinPx: Float, motion: Boolean) {
    if (!motion) return
    val base = coinAt[i] ?: return
    animate(0f, 1f, animationSpec = tween(HOP_MS)) { k, _ ->
        val up = if (k < 0.5f) k * 2 else (1 - k) * 2
        coinAt[i] = base - Offset(0f, coinPx * 0.5f * up)
    }
    coinAt[i] = base
}

/** Мягкое свечение за вещью, к которой сейчас просит отнести монету реплика. */
@Composable
private fun Glow(on: Boolean, content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        if (on) Box(Modifier.matchParentSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }.background(GlowColor, CircleShape))
        content()
    }
}

private fun sceneOf(beat: Int): Int = when (beat) {
    in 0 until PARCEL_BEAT -> 0
    in PARCEL_BEAT until COINS_BEAT -> PARCEL_SCENE
    in COINS_BEAT until KIRA_BEAT -> COINS_SCENE
    in KIRA_BEAT until STORY_LINES - 1 -> 3
    else -> 4
}

/** Тринадцать реплик истории: `story.1` … `story.13` в texts.ru.json. */
const val STORY_LINES = 13
private const val PARCEL_BEAT = 2
private const val COINS_BEAT = 5
private const val KIRA_BEAT = 8
private const val PARCEL_SCENE = 1
private const val COINS_SCENE = 2

/** Сколько висит реплика, если её не листать. Когда будет голос, реплика подождёт его конца. */
private const val BEAT_MS = 3400L
/** Раньше этого касание реплику не листает: каждую фразу видно. */
private const val MIN_BEAT_MS = 1200L
private const val AUTO_FLY_DELAY_MS = 1400L
private const val FLY_MS = 450
private const val HOP_MS = 260

private val STORY_PET_MAX = 440.dp
private val GROUND_GAP = 12.dp
private val GROUND_DEPTH = 18.dp
private val COIN = 44.dp
private val COIN_STEP = 72.dp
private val KIRA_BOB = 4.dp
private val GlowColor = androidx.compose.ui.graphics.Color(0x66F2B01E)
