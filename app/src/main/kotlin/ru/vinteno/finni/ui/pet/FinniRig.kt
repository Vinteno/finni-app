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
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.model.Fur
import ru.vinteno.finni.ui.art.Art
import ru.vinteno.finni.ui.art.FinniSpec
import kotlin.random.Random

/**
 * Финни — риг из animation-howto.md §3: шесть поворотных частей на одной общей канве — заднее ухо,
 * туловище, две лапы, голова, переднее ухо; глаза и рот лежат на голове и подменяются. У каждой
 * части своя точка поворота. Части рисуются PNG-слоями из art/app по finni_pivots.json; пока
 * слоёв нет — заглушкой из простых фигур (решение I11). Анимации у обоих путей одни.
 *
 * Координаты анимаций — в единицах логической канвы 100 × 212 (пропорция фигуры 0,47, гайд §11.2).
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
    hop: Float = 0f,
    moving: Boolean = false,
    description: String? = null,
    wear: Set<String> = emptySet(),
    cold: Boolean = false,
) {
    val pose = remember { Pose() }
    var eyes by remember { mutableStateOf(Eyes.OPEN) }
    var mouthOpen by remember { mutableStateOf(false) }

    // Холостое состояние §6.2: дыхание 2000 мс, уши не в такт, моргание раз в 4–6 с.
    val live = animate && idle && reaction != Reaction.SLEEP
    val t = rememberInfiniteTransition(label = "idle")
    val breath = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val earWave = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, 300, FastOutSlowInEasing), RepeatMode.Reverse), label = "e")
    val sleepBreath = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")

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

    // `зябнет` (§6.3): лапы обхватывают тело на 20° — главный сигнал, он держится и без анимаций;
    // едва заметная дрожь ±2 dp, три колебания и пауза, — только с анимациями. Дома до покупки куртки.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(cold, animate) {
        shake.snapTo(0f)
        while (cold && animate) {
            repeat(3) { shake.animateTo(2.7f, tween(53)); shake.animateTo(-2.7f, tween(53)) }
            shake.animateTo(0f, tween(53))
            delay(1500)
        }
    }
    val coldK = if (cold && reaction != Reaction.SLEEP) 1f else 0f

    val idleK = if (live) 1f else 0f
    val sleepK = if (animate && reaction == Reaction.SLEEP) 1f else 0f
    val kurtka = "kurtka" in wear
    val motion = { Motion(pose, idleK, sleepK, breath.value, earWave.value, sleepBreath.value, hop, moving, coldK, shake.value, kurtka) }
    val semantics = if (description != null) Modifier.semantics { contentDescription = description } else Modifier

    // Финни из PNG, если в сборке есть все его слои и finni_pivots.json; иначе — заглушка целиком.
    Art.init(LocalContext.current)
    val spec = Art.finni
    val files = spec?.files(fur, accessory, headOnly)
    // Этот окрас — первым, остальные окрасы и аксессуары — следом, по одному: на экране создания
    // смена окраса не ждёт загрузки.
    LaunchedEffect(fur, accessory, spec) {
        if (spec == null) return@LaunchedEffect
        val furs = listOf(fur) + Fur.entries.filter { it != fur }
        val accs = listOf(accessory) + Accessory.entries.filter { it != accessory }
        Art.warmUp(furs.flatMap { f -> accs.flatMap { a -> spec.files(f, a, false) } }.distinct())
    }
    if (spec != null && files!!.none(Art::missing)) {
        PngFinni(spec, fur, accessory, headOnly, { eyes }, { mouthOpen }, motion, modifier.then(semantics), wear.filter { !headOnly }.toSet())
    } else {
        StubFinni(palette(fur), accessory, headOnly, motion(), eyes, mouthOpen, modifier.then(semantics))
    }
}

/**
 * Поза в кадре из анимируемых значений: углы в градусах, смещения в единицах канвы 100 × 212.
 * Углы ушей и лап — свои, относительно родителя; поворот головы они получают от неё.
 */
private class Motion(
    pose: Pose, idleK: Float, sleepK: Float, breath: Float, earWave: Float, sleepBreath: Float, hop: Float, moving: Boolean,
    coldK: Float = 0f, shake: Float = 0f, kurtka: Boolean = false,
) {
    // Прыжок при перемещении — §6.1 и §6.4: у земли присед (≤ 4%), в воздухе вытяжка и подъём на 7%
    // высоты, уши отстают, тень в верхней точке сжимается до 0,8. `hop` — высота прыжка 0..1.
    private val crouch = if (moving) (1f - hop).let { it * it * it * it * it * it } else 0f
    private val breathe = idleK * breath * 0.02f + sleepK * sleepBreath * 0.015f
    private val armWave = idleK * breath * 1.6f

    val bodyDy = pose.bodyY.value - hop * 0.07f * H
    val bodyDx = shake * coldK
    /** В куртке лапы отводятся не больше чем на 25° — рукав шире лапы (final-plan §3, п. 1). */
    private val armMax = if (kurtka) 25f else 180f
    val bodySX = pose.bodySX.value * (1f - 0.02f * hop + 0.03f * crouch)
    val bodySY = pose.bodySY.value * (1f + breathe) * (1f + 0.03f * hop - 0.04f * crouch)
    val headRot = pose.headRot.value + sleepK * 5f
    val headDrop = pose.headDrop.value
    val armL = (pose.armL.value + armWave - 20f * coldK).coerceIn(-armMax, armMax)
    val armR = (-pose.armR.value - armWave + 20f * coldK).coerceIn(-armMax, armMax)
    val earB = pose.earB.value - idleK * earWave * 2.5f - 9f * hop + 4f * crouch - 4f * coldK
    val earF = pose.earF.value + idleK * earWave * 2f + 7f * hop - 3f * crouch
    val shadow = 1f - 0.2f * hop
}

/**
 * Финни из PNG-слоёв на общей канве. Логическая канва 100 × 212 и все числа анимаций — прежние:
 * `figure_box` растягивается ровно на неё, смещения переводятся в пиксели канвы тем же масштабом.
 * Каждая часть получает преобразование родителя, затем своё — как в эталонном демо анимации:
 * лапы и голова едут с туловищем, уши и кепка — с головой, бант — с передним ухом.
 * Целый Финни не обрезается: лапы и уши при движении выходят за рамку до `motion_box`.
 */
@Composable
private fun PngFinni(
    spec: FinniSpec,
    fur: Fur,
    accessory: Accessory,
    headOnly: Boolean,
    eyes: () -> Eyes,
    mouthOpen: () -> Boolean,
    motion: () -> Motion,
    modifier: Modifier,
    wear: Set<String> = emptySet(),
) {
    val box = if (headOnly) spec.headBox else spec.figureBox
    val clothes = wornFiles(fur, wear).filterNot(Art::missing)
    val kurtka = "kurtka" in wear && clothes.size == wornFiles(fur, wear).size
    val ratio = if (headOnly) box.width / box.height else W / H
    Canvas(modifier.aspectRatio(ratio).then(if (headOnly) Modifier.clipToBounds() else Modifier)) {
        // Пока слои декодируются, не рисуем ничего — это доли секунды при первом показе.
        val img = (spec.files(fur, accessory, headOnly) + clothes).associateWith { Art.image(it) ?: return@Canvas }
        val m = motion()
        val u = spec.figureBox.height / H
        val e = eyes()
        val mouth = mouthOpen()

        fun DrawTransform.local(part: String) {
            val pivot = spec.pivots[part] ?: return
            when (part) {
                "torso" -> { translate(m.bodyDx * u, m.bodyDy * u); scale(m.bodySX, m.bodySY, pivot) }
                FinniSpec.HEAD -> { translate(0f, m.headDrop * u); rotate(m.headRot, pivot) }
                "arm_left" -> rotate(m.armL, pivot)
                "arm_right" -> rotate(m.armR, pivot)
                "ear_back" -> rotate(m.earB, pivot)
                "ear_front" -> rotate(m.earF, pivot)
            }
        }
        fun DrawTransform.chain(part: String) {
            spec.parents[part]?.let { chain(it) }
            local(part)
        }

        withTransform({
            scale(size.width / box.width, size.height / box.height, Offset.Zero)
            translate(-box.left, -box.top)
        }) {
            // Тень остаётся на земле, у точки поворота туловища, и сжимается, пока Финни в воздухе.
            val torso = spec.pivots["torso"]
            val feet = img[FinniSpec.body(fur, "torso")]
            if (!headOnly && torso != null && feet != null) {
                val w = feet.bitmap.width * 0.92f
                val h = 8f * u
                withTransform({ scale(m.shadow, m.shadow, torso) }) {
                    drawOval(Color(0x22000000), Offset(torso.x - w / 2, torso.y - h / 2), Size(w, h))
                }
            }
            for (part in spec.zOrder) {
                if (headOnly && !spec.underHead(part)) continue
                val file = when (part) {
                    "eyes" -> when (e) {
                        Eyes.OPEN -> null
                        Eyes.CLOSED -> FinniSpec.body(fur, "eyes_closed")
                        Eyes.HAPPY -> FinniSpec.body(fur, "eyes_happy")
                    }
                    "mouth" -> if (mouth) FinniSpec.body(fur, "mouth_open") else null
                    // В куртке лапы — рукава с кистью, та же точка поворота (final-plan §3, п. 1).
                    "arm_left", "arm_right" -> if (kurtka) FinniSpec.body(fur, "kurtka_$part") else FinniSpec.body(fur, part)
                    else -> spec.files(part, fur, accessory).firstOrNull()
                } ?: continue
                val layer = img[file] ?: continue
                withTransform({ chain(part) }) {
                    drawImage(layer.bitmap, Offset(layer.left.toFloat(), layer.top.toFloat()))
                }
                // Носимое — дочерний слой части тела: бинт на правой лапе, пола куртки и рисунок — на
                // туловище поверх лап. Ездят с поворотом и масштабом сами (items.md §6).
                if (part == "arm_right" && !headOnly) {
                    val bandage = if (kurtka) "item_bint_worn_kurtka" else "item_bint_worn"
                    img[bandage]?.let { b -> withTransform({ chain("arm_right") }) { drawImage(b.bitmap, Offset(b.left.toFloat(), b.top.toFloat())) } }
                    if (kurtka) listOf("item_kurtka_body", "item_risunok_worn").forEach { f ->
                        img[f]?.let { b -> withTransform({ chain("torso") }) { drawImage(b.bitmap, Offset(b.left.toFloat(), b.top.toFloat())) } }
                    }
                }
            }
        }
    }
}

/** Слои носимого на стоящем Финни: куртка — пола и рукава по окрасу, рисунок — на поле, бинт — на правой лапе. */
private fun wornFiles(fur: Fur, wear: Set<String>): List<String> = buildList {
    val kurtka = "kurtka" in wear
    if (kurtka) {
        add(FinniSpec.body(fur, "kurtka_arm_left")); add(FinniSpec.body(fur, "kurtka_arm_right")); add("item_kurtka_body")
        if ("risunok" in wear) add("item_risunok_worn")
    }
    if ("bint" in wear) add(if (kurtka) "item_bint_worn_kurtka" else "item_bint_worn")
}

/** Заглушка из простых фигур — пока нет PNG или если хоть один слой не прочитался. */
@Composable
private fun StubFinni(p: Palette, accessory: Accessory, headOnly: Boolean, m: Motion, eyes: Eyes, mouthOpen: Boolean, modifier: Modifier) {
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

        // Всё, что выше туловища, едет вместе с ним: дочерние части наследуют сдвиг.
        val headDy = m.bodyDy - (m.bodySY - 1f) * (Pivot.torso.y - Pivot.head.y) + m.headDrop

        if (!headOnly) {
            // Тень остаётся на земле и сжимается, пока Финни в воздухе.
            Part(Pivot.torso, sx = m.shadow, sy = m.shadow) { drawShadow() }
        }
        // Заднее ухо — дочь головы, Z 1.
        Part(Pivot.earBack, rot = m.headRot + m.earB, dy = headDy) { drawEarBack(p) }
        if (!headOnly) {
            Part(Pivot.torso, sx = m.bodySX, sy = m.bodySY, dy = m.bodyDy) { drawTorso(p) }
            if (accessory == Accessory.SCARF) Part(Pivot.torso, sx = m.bodySX, sy = m.bodySY, dy = m.bodyDy) { drawScarf() }
            Part(Pivot.armL, rot = m.armL, dy = m.bodyDy) { drawArm(p, left = true) }
            Part(Pivot.armR, rot = m.armR, dy = m.bodyDy) { drawArm(p, left = false) }
        }
        Part(Pivot.head, rot = m.headRot, dy = headDy) {
            drawHead(p)
            drawFace(eyes, mouthOpen)
            if (accessory == Accessory.CAP) drawCap()
        }
        Part(Pivot.earFront, rot = m.headRot + m.earF, dy = headDy) {
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

/** Рамка позы лёжа на канве листа 640 × 960: целый рисунок, не режется (animation-howto §6.5). */
private val LYING_BOX = Rect(12f, 39f, 634f, 935f)

/** Ширина к высоте у Финни лёжа. */
const val LYING_RATIO = (634f - 12f) / (935f - 39f)

/**
 * Финни лёжа — одна статичная поза для событий главы 2: свернулся на пледе, на лежанке, у печки или
 * у окна. Одинакова в обоих исходах, меняется только место. Аксессуар и носимое — слоями позы лёжа
 * (`acc_*_lying`, `item_kurtka_lying`…): в событии первого снега Финни одет. Нет картинки — ничего.
 */
@Composable
fun FinniLying(fur: Fur, accessory: Accessory, wear: Set<String>, modifier: Modifier = Modifier, description: String? = null) {
    Art.init(LocalContext.current)
    val kurtka = "kurtka" in wear
    val files = buildList {
        add(FinniSpec.body(fur, "lying"))
        if (kurtka) { add("item_kurtka_lying"); if ("risunok" in wear) add("item_risunok_lying") }
        add("acc_${accessory.name.lowercase()}_lying")
        if ("bint" in wear) add("item_bint_lying")
    }.filterNot(Art::missing)
    val semantics = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    Canvas(modifier.aspectRatio(LYING_RATIO).then(semantics)) {
        val img = files.map { Art.image(it) ?: return@Canvas }
        val k = size.width / LYING_BOX.width
        withTransform({ scale(k, k, Offset.Zero); translate(-LYING_BOX.left, -LYING_BOX.top) }) {
            img.forEach { drawImage(it.bitmap, Offset(it.left.toFloat(), it.top.toFloat())) }
        }
    }
}
