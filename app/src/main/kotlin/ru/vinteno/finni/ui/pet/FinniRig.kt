package ru.vinteno.finni.ui.pet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.model.Fur
import kotlin.random.Random

/**
 * Финни-заглушка из простых фигур (решение I11). Устроен как настоящий риг из
 * animation-howto.md §3: шесть поворотных частей на одной общей канве — заднее ухо, туловище,
 * две лапы, голова, переднее ухо; глаза и рот лежат на голове и подменяются. У каждой части своя
 * точка поворота. Когда придут PNG, меняется только отрисовка части, анимации остаются.
 *
 * Координаты — в единицах канвы 100 × 212 (пропорция фигуры 0,47, гайд §11.2).
 */

/** Пять реакций на весь продукт — animation-howto.md §6. `зябнет` в главе 1 не играется (I9). */
enum class Reaction { NOTICE, HAPPY, EAT, SLEEP }

/** Палитры перекраски — animation-howto.md §4: мех, светлый мех, живот, внутреннее ухо, лапы. Контур общий. */
private class Palette(val fur: Color, val furLight: Color, val belly: Color, val innerEar: Color, val paws: Color)

private val Outline = Color(0xFF5A3A22)
private val EyeDark = Color(0xFF2B1B10)

private fun palette(f: Fur) = when (f) {
    Fur.GINGER -> Palette(Color(0xFFF8994E), Color(0xFFF9A058), Color(0xFFFDDFB8), Color(0xFFFBCC9B), Color(0xFFA65E33))
    Fur.BLUE -> Palette(Color(0xFF8FA8BE), Color(0xFF96AFC4), Color(0xFFDCE7EF), Color(0xFFC2D2DF), Color(0xFF5A7186))
    Fur.BROWN -> Palette(Color(0xFFA9794F), Color(0xFFB08157), Color(0xFFEBD9C1), Color(0xFFDCC3A4), Color(0xFF71462A))
}

/** Аксессуары не перекрашиваются — §5. */
private val Scarf = Color(0xFF3E9B96)
private val Cap = Color(0xFF4D880B)
private val Bow = Color(0xFF6B238B)

private const val W = 100f
private const val H = 212f

/** Точки поворота частей в единицах канвы. */
private object Pivot {
    val torso = Offset(50f, 198f)      // низ по центру, между стоп
    val head = Offset(50f, 116f)       // «шея»
    val armL = Offset(31f, 124f)       // плечо
    val armR = Offset(69f, 124f)
    val earBack = Offset(39f, 64f)     // основание уха
    val earFront = Offset(62f, 64f)
}

/** Состояние позы: углы в градусах, масштабы, смещения в единицах канвы. */
private class Pose {
    val bodyY = Animatable(0f)
    val bodySX = Animatable(1f)
    val bodySY = Animatable(1f)
    val headRot = Animatable(0f)
    val headDrop = Animatable(0f)
    val armL = Animatable(0f)
    val armR = Animatable(0f)
    val earB = Animatable(0f)
    val earF = Animatable(0f)
}

private val Spring = CubicBezierEasing(.34f, 1.56f, .64f, 1f)

/**
 * @param reaction реакция и её ключ: новый ключ — новое проигрывание. Одинаковая для базового
 *   варианта и надбавки, для любой суммы и исхода — инвариант 8.
 * @param animate флаг «Анимации»: при выключенном — поза покоя, idle не играет, долгое состояние
 *   показывается позой (§9).
 * @param headOnly голова 48 dp на плотных экранах — гайд §7.4.
 * @param lookRight куда смотрит `замечает`.
 */
@Composable
fun Finni(
    fur: Fur,
    accessory: Accessory,
    modifier: Modifier = Modifier,
    reaction: Reaction? = null,
    reactionKey: Int = 0,
    animate: Boolean = true,
    idle: Boolean = true,
    headOnly: Boolean = false,
    lookRight: Boolean = true,
    earPoke: Int = 0,
) {
    val p = palette(fur)
    val pose = remember { Pose() }
    var eyes by remember { mutableStateOf(Eyes.OPEN) }
    var mouthOpen by remember { mutableStateOf(false) }

    // Холостое состояние §6.2: дыхание 2000 мс, уши не в такт, моргание раз в 4–6 с.
    val live = animate && idle && reaction != Reaction.SLEEP
    val t = rememberInfiniteTransition(label = "idle")
    val breath by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val earWave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, 300, FastOutSlowInEasing), RepeatMode.Reverse), label = "e")
    val sleepBreath by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")

    LaunchedEffect(live) {
        while (live) {
            delay(4000L + Random.nextLong(2000))
            eyes = Eyes.CLOSED; delay(120); eyes = Eyes.OPEN
        }
    }

    LaunchedEffect(reaction, reactionKey, animate) {
        eyes = Eyes.OPEN; mouthOpen = false
        if (reaction == null) return@LaunchedEffect
        if (!animate) {
            // Без анимаций долгое состояние — позой: `спит` — закрытые глаза.
            if (reaction == Reaction.SLEEP) eyes = Eyes.CLOSED
            return@LaunchedEffect
        }
        play(pose, reaction, lookRight, onEyes = { eyes = it }, onMouth = { mouthOpen = it })
    }

    // Касание по фигуре: уши поворачиваются, как в «замечает», — часть холостого состояния (§6.2).
    LaunchedEffect(earPoke) {
        if (earPoke == 0 || !live) return@LaunchedEffect
        coroutineScope {
            launch { delay(50); pose.earB.animateTo(14f, tween(150, easing = Spring)); pose.earB.animateTo(0f, tween(200)) }
            launch { delay(70); pose.earF.animateTo(9f, tween(150, easing = Spring)); pose.earF.animateTo(0f, tween(200)) }
        }
    }

    val idleK = if (live) 1f else 0f
    val sleepK = if (animate && reaction == Reaction.SLEEP) 1f else 0f
    val breathe = idleK * breath * 0.02f + sleepK * sleepBreath * 0.015f

    val box = if (headOnly) Rect(4f, 6f, 96f, 122f) else Rect(0f, 0f, W, H)
    Box(modifier.aspectRatio(box.width / box.height).clipToBounds()) {
        @Composable
        fun Part(pivot: Offset, rot: Float = 0f, sx: Float = 1f, sy: Float = 1f, dy: Float = 0f, draw: DrawScope.() -> Unit) {
            Canvas(
                Modifier.fillMaxSize().graphicsLayer {
                    val k = size.width / box.width
                    transformOrigin = TransformOrigin((pivot.x - box.left) / box.width, (pivot.y - box.top) / box.height)
                    rotationZ = rot
                    scaleX = sx
                    scaleY = sy
                    translationY = dy * k
                },
            ) {
                val k = size.width / box.width
                drawContext.transform.translate(-box.left * k, -box.top * k)
                drawContext.transform.scale(k, k, Offset.Zero)
                draw()
            }
        }

        val bodyDy = pose.bodyY.value
        val bodySX = pose.bodySX.value
        val bodySY = pose.bodySY.value * (1f + breathe)
        // Всё, что выше туловища, едет вместе с ним: дочерние части наследуют сдвиг.
        val headDy = bodyDy - (bodySY - 1f) * (Pivot.torso.y - Pivot.head.y) + pose.headDrop.value
        val headRot = pose.headRot.value + sleepK * 5f
        val armWave = idleK * breath * 1.6f

        if (!headOnly) {
            Part(Pivot.torso, sx = bodySX, sy = bodySY, dy = bodyDy) { drawShadow() }
        }
        // Заднее ухо — дочь головы, Z 1.
        Part(Pivot.earBack, rot = headRot + pose.earB.value - idleK * earWave * 2.5f, dy = headDy) { drawEarBack(p) }
        if (!headOnly) {
            Part(Pivot.torso, sx = bodySX, sy = bodySY, dy = bodyDy) { drawTorso(p) }
            if (accessory == Accessory.SCARF) Part(Pivot.torso, sx = bodySX, sy = bodySY, dy = bodyDy) { drawScarf() }
            Part(Pivot.armL, rot = pose.armL.value + armWave, dy = bodyDy) { drawArm(p, left = true) }
            Part(Pivot.armR, rot = -pose.armR.value - armWave, dy = bodyDy) { drawArm(p, left = false) }
        }
        Part(Pivot.head, rot = headRot, dy = headDy) {
            drawHead(p)
            drawFace(eyes, mouthOpen)
            if (accessory == Accessory.CAP) drawCap()
        }
        Part(Pivot.earFront, rot = headRot + pose.earF.value + idleK * earWave * 2f, dy = headDy) {
            drawEarFront(p)
            if (accessory == Accessory.BOW) drawBow()
        }
        if (headOnly && accessory == Accessory.SCARF) {
            Part(Pivot.head, dy = headDy) { drawScarf() }
        }
    }
}

private enum class Eyes { OPEN, CLOSED, HAPPY }

/** Реакции §6.3: основное движение до 320 мс, доводка до 600 мс; уши отстают на 50–90 мс. */
private suspend fun play(pose: Pose, r: Reaction, lookRight: Boolean, onEyes: (Eyes) -> Unit, onMouth: (Boolean) -> Unit) = coroutineScope {
    val dir = if (lookRight) 1f else -1f
    when (r) {
        Reaction.NOTICE -> {
            launch { pose.headRot.animateTo(8f * dir, tween(200, easing = LinearOutSlowInEasing)); delay(60); pose.headRot.animateTo(0f, tween(200)) }
            launch { delay(50); pose.earB.animateTo(14f * dir, tween(150, easing = Spring)); pose.earB.animateTo(10f * dir, tween(60)); pose.earB.animateTo(0f, tween(200)) }
            launch { delay(70); pose.earF.animateTo(9f * dir, tween(150, easing = Spring)); pose.earF.animateTo(6f * dir, tween(60)); pose.earF.animateTo(0f, tween(200)) }
            val arm = if (lookRight) pose.armR else pose.armL
            launch { arm.animateTo(12f, tween(200, easing = Spring)); delay(60); arm.animateTo(0f, tween(200)) }
        }
        Reaction.HAPPY -> {
            onEyes(Eyes.HAPPY); onMouth(true)
            launch {
                pose.bodySX.animateTo(1.03f, tween(60)); pose.bodySY.snapTo(0.96f)
                launch { pose.bodySX.animateTo(0.98f, tween(110)) }
                launch { pose.bodySY.animateTo(1.03f, tween(110)) }
                pose.bodyY.animateTo(-6f, tween(110, easing = LinearOutSlowInEasing))
                pose.bodyY.animateTo(0f, tween(90, easing = FastOutSlowInEasing))
                launch { pose.bodySX.animateTo(1.02f, tween(40)); pose.bodySX.animateTo(1f, tween(60)) }
                pose.bodySY.animateTo(0.97f, tween(40)); pose.bodySY.animateTo(1f, tween(60))
            }
            launch { delay(40); launch { pose.armL.animateTo(32f, tween(200, easing = Spring)); pose.armL.animateTo(0f, tween(180)) }
                pose.armR.animateTo(32f, tween(200, easing = Spring)); pose.armR.animateTo(0f, tween(180)) }
            launch { delay(60); pose.earB.animateTo(-8f, tween(160)); pose.earB.animateTo(5f, tween(160, easing = Spring)); pose.earB.animateTo(0f, tween(160)) }
            launch { delay(80); pose.earF.animateTo(8f, tween(160)); pose.earF.animateTo(-4f, tween(160, easing = Spring)); pose.earF.animateTo(0f, tween(160)) }
            delay(520); onEyes(Eyes.OPEN); onMouth(false)
        }
        Reaction.EAT -> {
            launch { pose.headDrop.animateTo(3.4f, tween(100)); pose.headRot.animateTo(4f, tween(100)) }
            launch { pose.armL.animateTo(-16f, tween(160)); delay(220); pose.armL.animateTo(0f, tween(200, easing = Spring)) }
            launch { pose.armR.animateTo(-16f, tween(160)); delay(220); pose.armR.animateTo(0f, tween(200, easing = Spring)) }
            delay(100)
            repeat(2) {
                onMouth(true)
                launch { pose.earB.animateTo(3f, tween(55)); pose.earB.animateTo(-3f, tween(55)) }
                delay(55); onMouth(false); delay(55)
            }
            launch { pose.earB.animateTo(0f, tween(100)) }
            launch { pose.headRot.animateTo(0f, tween(200)) }
            pose.headDrop.animateTo(0f, tween(200))
        }
        Reaction.SLEEP -> {
            onEyes(Eyes.CLOSED)
            launch { pose.headRot.animateTo(5f, tween(320, easing = FastOutSlowInEasing)) }
            launch { pose.armL.animateTo(-5f, tween(600)) }
            launch { pose.armR.animateTo(-5f, tween(600)) }
            pose.earB.animateTo(6f, tween(600))
        }
    }
}

// ---------- Отрисовка частей ----------

private fun DrawScope.fillStroke(path: Path, fill: Color, width: Float = 1.8f) {
    drawPath(path, fill)
    drawPath(path, Outline, style = Stroke(width, cap = StrokeCap.Round))
}

private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, fill: Color, stroke: Boolean = true) {
    drawOval(fill, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))
    if (stroke) drawOval(Outline, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), style = Stroke(1.8f))
}

private fun DrawScope.drawShadow() {
    drawOval(Color(0x22000000), Offset(26f, 196f), Size(48f, 8f))
}

private fun DrawScope.drawTorso(p: Palette) {
    // Ноги не режутся: Финни прыгает, а не шагает (§3).
    oval(38f, 193f, 12f, 7f, p.paws)
    oval(62f, 193f, 12f, 7f, p.paws)
    oval(50f, 152f, 25f, 40f, p.fur)
    oval(50f, 156f, 15f, 27f, p.belly, stroke = false)
}

private fun DrawScope.drawArm(p: Palette, left: Boolean) {
    // Лапа чуть отстоит от тела (§2) и несёт кусок плеча: верх заходит внутрь туловища.
    val x = if (left) 31f else 69f
    val out = if (left) -1f else 1f
    val top = Offset(x - out * 2f, 122f)
    val paw = Offset(x + out * 5f, 158f)
    drawLine(Outline, top, paw, 12.6f, StrokeCap.Round)
    drawLine(p.furLight, top, paw, 9f, StrokeCap.Round)
    oval(paw.x, paw.y + 1f, 6.5f, 5.5f, p.paws)
}

private fun DrawScope.drawHead(p: Palette) {
    // Снизу у головы контура нет: нижняя дуга уходит под туловище (§3, правило 1).
    drawOval(p.fur, Offset(18f, 56f), Size(64f, 62f))
    drawArc(Outline, 150f, 240f, false, Offset(18f, 56f), Size(64f, 62f), style = Stroke(1.8f, cap = StrokeCap.Round))
    oval(40f, 92f, 10f, 11f, p.belly, stroke = false)
    oval(60f, 92f, 10f, 11f, p.belly, stroke = false)
    oval(50f, 102f, 15f, 10f, p.belly, stroke = false)
}

private fun DrawScope.drawFace(eyes: Eyes, mouthOpen: Boolean) {
    when (eyes) {
        Eyes.OPEN -> for (x in listOf(40f, 60f)) {
            oval(x, 88f, 4.6f, 5.6f, EyeDark, stroke = false)
            drawCircle(Color.White, 1.5f, Offset(x + 1.4f, 86f))
        }
        Eyes.CLOSED -> for (x in listOf(40f, 60f)) {
            drawArc(EyeDark, 20f, 140f, false, Offset(x - 5f, 84f), Size(10f, 6f), style = Stroke(1.8f, cap = StrokeCap.Round))
        }
        Eyes.HAPPY -> for (x in listOf(40f, 60f)) {
            drawArc(EyeDark, 200f, 140f, false, Offset(x - 5f, 86f), Size(10f, 7f), style = Stroke(2f, cap = StrokeCap.Round))
        }
    }
    val nose = Path().apply { moveTo(46.5f, 96f); lineTo(53.5f, 96f); lineTo(50f, 100f); close() }
    drawPath(nose, Color(0xFF8A4A2C))
    if (mouthOpen) {
        oval(50f, 104f, 3.5f, 3f, Color(0xFF7A3A2A), stroke = false)
    } else {
        drawArc(Outline, 10f, 160f, false, Offset(44.5f, 100f), Size(5.5f, 4.5f), style = Stroke(1.4f, cap = StrokeCap.Round))
        drawArc(Outline, 10f, 160f, false, Offset(50f, 100f), Size(5.5f, 4.5f), style = Stroke(1.4f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawEarBack(p: Palette) {
    // Поднятое ухо; основание уходит под голову, чтобы при повороте не было щели (§3, правило 6).
    val outer = Path().apply {
        moveTo(33f, 72f); cubicTo(22f, 44f, 20f, 18f, 27f, 8f); cubicTo(36f, 14f, 46f, 40f, 46f, 70f); close()
    }
    fillStroke(outer, p.fur)
    val inner = Path().apply {
        moveTo(35f, 62f); cubicTo(28f, 44f, 27f, 26f, 30f, 17f); cubicTo(36f, 26f, 41f, 44f, 41f, 62f); close()
    }
    drawPath(inner, p.innerEar)
}

private fun DrawScope.drawEarFront(p: Palette) {
    // Загнутое ухо никогда не распрямляется: вращается целиком, форма не меняется (§3).
    val outer = Path().apply {
        moveTo(56f, 70f); cubicTo(58f, 46f, 66f, 30f, 78f, 30f); cubicTo(88f, 30f, 92f, 42f, 88f, 54f)
        cubicTo(82f, 50f, 76f, 44f, 72f, 44f); cubicTo(68f, 50f, 66f, 60f, 67f, 70f); close()
    }
    fillStroke(outer, p.fur)
    val inner = Path().apply { moveTo(60f, 64f); cubicTo(62f, 48f, 68f, 38f, 76f, 37f); cubicTo(70f, 44f, 66f, 54f, 64f, 64f); close() }
    drawPath(inner, p.innerEar)
}

private fun DrawScope.drawScarf() {
    val band = Path().apply {
        moveTo(29f, 112f); quadraticTo(50f, 124f, 71f, 112f); lineTo(72f, 121f); quadraticTo(50f, 133f, 28f, 121f); close()
    }
    fillStroke(band, Scarf)
    val tail = Path().apply { moveTo(58f, 122f); lineTo(66f, 146f); lineTo(58f, 148f); lineTo(53f, 125f); close() }
    fillStroke(tail, Scarf)
}

private fun DrawScope.drawCap() {
    val cap = Path().apply { moveTo(26f, 70f); cubicTo(28f, 50f, 72f, 50f, 74f, 70f); close() }
    fillStroke(cap, Cap)
    val visor = Path().apply { moveTo(22f, 70f); quadraticTo(40f, 76f, 58f, 70f); lineTo(24f, 67f); close() }
    fillStroke(visor, Cap)
}

private fun DrawScope.drawBow() {
    val l = Path().apply { moveTo(78f, 36f); lineTo(68f, 29f); lineTo(69f, 42f); close() }
    val r = Path().apply { moveTo(78f, 36f); lineTo(88f, 29f); lineTo(87f, 42f); close() }
    fillStroke(l, Bow); fillStroke(r, Bow)
    oval(78f, 36f, 2.6f, 2.6f, Bow)
}
