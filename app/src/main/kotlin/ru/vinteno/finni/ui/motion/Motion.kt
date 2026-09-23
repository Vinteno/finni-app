package ru.vinteno.finni.ui.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import ru.vinteno.finni.ui.app
import ru.vinteno.finni.ui.components.Coin
import ru.vinteno.finni.ui.theme.FinniMotion

/** Запомнить место элемента на экране под именем — туда и оттуда летят монеты. */
fun Modifier.anchor(flights: CoinFlights, name: String): Modifier =
    onGloballyPositioned { flights.anchors[name] = it.boundsInRoot() }

/** Слой полёта монет поверх всех экранов. Монета — та же, что в кошельке, 24 dp. */
@Composable
fun FlightLayer() {
    val flights = app().flights
    val density = LocalDensity.current
    val coinPx = with(density) { 24.dp.toPx() }
    val arcPx = with(density) { CoinFlights.ARC_DP.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        flights.flights.toList().forEach { f ->
            key(f.id) {
                val t = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(f.delayMs.toLong())
                    t.animateTo(1f, tween(CoinFlights.FLIGHT_MS, easing = FastOutSlowInEasing))
                    flights.arrived(f)
                }
                val p = t.value
                val x = f.from.x + (f.to.x - f.from.x) * p
                // Дуга: подъём 24 dp в середине пути.
                val y = f.from.y + (f.to.y - f.from.y) * p - arcPx * 4f * p * (1f - p)
                Box(
                    Modifier.offset { IntOffset((x - coinPx / 2).roundToInt(), (y - coinPx / 2).roundToInt()) }
                        .graphicsLayer { alpha = if (p == 0f && f.delayMs > 0) 0f else 1f },
                ) { Coin(24.dp) }
            }
        }
    }
}

/**
 * Появление объекта — §7.1: масштаб 0,92 → 1,00 и прозрачность 0 → 1 одновременно, 200 мс, ease-out.
 * Играется один раз для нового предмета: вернулся на экран — уже купленное стоит на месте.
 * Базовый вариант и надбавка появляются одинаково — инвариант 8.
 */
@Composable
fun Appear(id: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val a = app()
    val fresh = remember(id) { a.seenInRoom.add(id) && a.animationOn }
    val k = remember(id) { Animatable(if (fresh) 0f else 1f) }
    LaunchedEffect(id) { if (fresh) k.animateTo(1f, tween(FinniMotion.APPEAR_MS, easing = LinearOutSlowInEasing)) }
    Box(modifier.graphicsLayer {
        val s = 0.92f + 0.08f * k.value
        scaleX = s; scaleY = s; alpha = k.value
    }) { content() }
}

/** Выезд плашки — §7.4: снизу на 24 dp с проявлением, 200 мс; уход — обратно за 120 мс. */
@Composable
fun SlideUp(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val a = app()
    val density = LocalDensity.current
    val shift = with(density) { 24.dp.roundToPx() }
    val state = remember { MutableTransitionState(!a.animationOn && visible) }
    state.targetState = visible
    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter = if (a.animationOn) slideInVertically(tween(FinniMotion.APPEAR_MS, easing = LinearOutSlowInEasing)) { shift } +
            fadeIn(tween(FinniMotion.APPEAR_MS)) else slideInVertically(tween(0)) { 0 },
        exit = if (a.animationOn) slideOutVertically(tween(120)) { shift } + fadeOut(tween(120)) else fadeOut(tween(0)),
    ) { content() }
}
