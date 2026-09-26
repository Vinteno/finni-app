package ru.vinteno.finni.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniDimens

/** Сколько держать палец на плашке «Взрослым», чтобы открылся раздел (I49, F6). */
const val ADULT_HOLD_MS = 3000

/**
 * Вход в раздел для взрослого — маленькая плашка с замком и словом «Взрослым» в шапке дома. Барьер —
 * удержание 3 секунды: кольцо вокруг замка заполняется, отпустил раньше — кольцо пустеет, ничего не
 * открывается. Мигания и спешки нет: кольцо заполняется ровно, только пока держат. Экранный диктор
 * открывает раздел своим двойным касанием — взрослому с диктором удержание недоступно.
 */
@Composable
fun AdultGate(label: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Row(
        modifier.heightIn(min = FinniDimens.MinTouch)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
                onClick { onOpen(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(ADULT_HOLD_MS, easing = LinearEasing))
                        onOpen()
                    }
                    waitForUpOrCancellation()
                    if (progress.value < 1f) {
                        job.cancel()
                        scope.launch { progress.snapTo(0f) }
                    }
                }
            }
            .softPlate(FinniDimens.RadiusSmall + 4.dp)
            .padding(start = 6.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(28.dp)) {
                val sw = 3.dp.toPx()
                drawArc(FinniColors.Stroke, 0f, 360f, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw))
                drawArc(FinniColors.Action, -90f, 360f * progress.value, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
            }
            Icon(Icons.Outlined.Lock, FinniColors.InkMute, 16.dp)
        }
        Txt(label, PlateText.copy(color = FinniColors.InkMute))
    }
}
