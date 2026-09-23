package ru.vinteno.finni.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniDimens
import ru.vinteno.finni.ui.theme.FinniMotion
import ru.vinteno.finni.ui.theme.FinniText

/**
 * «Губа» — style-guide.md §8.3: у всего, что нажимается. При нажатии элемент опускается
 * на высоту губы за 120 мс, губа схлопывается. Без губы элемент не нажимается.
 */
@Composable
private fun Pressable(
    onClick: () -> Unit,
    enabled: Boolean,
    lip: Dp,
    lipColor: Color,
    shape: Shape,
    modifier: Modifier,
    description: String?,
    face: @Composable (Modifier) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val drop by animateDpAsState(if (pressed && enabled) lip else 0.dp, tween(FinniMotion.PRESS_MS), label = "press")
    Box(
        modifier
            .semantics { description?.let { contentDescription = it } }
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        val hasLip = enabled && lip > 0.dp
        if (hasLip) {
            // Губа: та же форма, сдвинутая вниз на свою высоту, под лицом кнопки.
            Box(Modifier.matchParentSize().padding(top = lip).background(lipColor, shape))
        }
        face(Modifier.offset(y = drop).padding(bottom = if (hasLip) lip else 0.dp))
    }
}

/** Главная кнопка: одна на экран, во всю ширину, 64 dp, заливка `action` — §10.1. */
@Composable
fun MainButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(FinniDimens.RadiusButton)
    Pressable(onClick, enabled, FinniDimens.LipMain, FinniColors.ActionPress, shape, modifier.fillMaxWidth(), null) { m ->
        Box(
            m.fillMaxWidth()
                .heightIn(min = FinniDimens.MainButtonHeight)
                .background(if (enabled) FinniColors.Action else FinniColors.DisabledBg, shape)
                .padding(horizontal = FinniDimens.CardPadding, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text,
                style = FinniText.Button.copy(
                    color = if (enabled) Color.White else FinniColors.DisabledInk,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

/** Вторичная кнопка: 56 dp, обводка 2 dp `action`, без заливки — §10.2. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, below: String? = null) {
    val shape = RoundedCornerShape(FinniDimens.RadiusButton)
    Pressable(onClick, true, FinniDimens.LipSecondary, FinniColors.Action, shape, modifier.fillMaxWidth(), null) { m ->
        Box(
            m.fillMaxWidth()
                .heightIn(min = FinniDimens.SecondaryButtonHeight)
                .background(FinniColors.Surface, shape)
                .border(FinniDimens.Outline, FinniColors.Action, shape)
                .padding(horizontal = FinniDimens.CardPadding, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(text, style = FinniText.Button.copy(color = FinniColors.Action, textAlign = TextAlign.Center))
                if (below != null) {
                    BasicText(below, style = FinniText.Caption.copy(color = FinniColors.Ink, textAlign = TextAlign.Center))
                }
            }
        }
    }
}

/** Круглая кнопка счётчика «−» и «+»: 56 dp, губа 2 dp — §10.4. */
@Composable
fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Pressable(onClick, true, FinniDimens.LipSecondary, FinniColors.Action, CircleShape, modifier, description) { m ->
        Box(
            m.size(FinniDimens.CounterButton)
                .background(FinniColors.Surface, CircleShape)
                .border(FinniDimens.Outline, FinniColors.Action, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, tint = FinniColors.Action, size = 28.dp)
        }
    }
}

/** Кнопка возврата: всегда верхний левый угол, 48 dp, всегда ведёт на Дом — §7.4. */
@Composable
fun BackButton(onClick: () -> Unit, description: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(FinniDimens.RadiusButton)
    Pressable(onClick, true, FinniDimens.LipSecondary, FinniColors.StrokeStrong, shape, modifier, description) { m ->
        Box(
            m.size(FinniDimens.BackButton)
                .background(FinniColors.Surface, shape)
                .border(FinniDimens.Outline, FinniColors.StrokeStrong, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, tint = FinniColors.Ink, size = 24.dp)
        }
    }
}

/** Нажимаемая карточка: обводка, губа 2 dp. Выбранная — обводка 3 dp и подложка `action-soft` (§10.3). */
@Composable
fun PressCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    background: Color = FinniColors.Surface,
    outline: Color = FinniColors.StrokeStrong,
    fillHeight: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(FinniDimens.RadiusCard)
    Pressable(onClick, true, FinniDimens.LipSecondary, outline, shape, modifier, null) { m ->
        Box(
            // Лицо тянется на всю высоту карточки, если попросили: соседние карточки полки одного размера.
            (if (fillHeight) m.fillMaxHeight() else m).sizeIn(minWidth = FinniDimens.MinTouch, minHeight = FinniDimens.MinTouch)
                .background(if (selected) FinniColors.ActionSoft else background, shape)
                .border(if (selected) 3.dp else FinniDimens.Outline, if (selected) FinniColors.Action else outline, shape),
        ) { content() }
    }
}
