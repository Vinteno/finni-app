package ru.vinteno.finni.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Раскладка «от свободной высоты»: у экрана есть окно заданной высоты, всё неизменное меряется
 * первым, а гибкие части — картинки, банки, полки — получают остаток. Не хватает — гибкие
 * уменьшаются по очереди уступок, каждая не ниже своего минимума. Не хватает и на минимумах
 * (крупный шрифт) — столбец выше окна, и его прокручивает тот, кто его держит.
 */

private data class Flex(val min: Dp, val max: Dp, val order: Int) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@Flex
}

/**
 * Гибкая часть [FitColumn]: высота от [min] до [max]. Уступает место в порядке [order] — меньший
 * раньше; части одного порядка уменьшаются вместе, пропорционально запасу. [max] бесконечный —
 * распорка: не уменьшается ниже [min] и забирает всё, что осталось.
 */
fun Modifier.flex(min: Dp, max: Dp = Dp.Infinity, order: Int = 0): Modifier = then(Flex(min, maxOf(min, max), order))

/**
 * Столбец высотой не меньше [viewport]: неизменные дети меряются по своему размеру, гибкие ([flex])
 * получают остаток. Ширина детей — ширина столбца.
 */
@Composable
fun FitColumn(viewport: Dp, modifier: Modifier = Modifier, gap: Dp = 0.dp, content: @Composable () -> Unit) {
    Layout(content, modifier) { ms, c ->
        val w = c.maxWidth
        val g = gap.roundToPx()
        val flex = ms.map { it.parentData as? Flex }
        val fixed = ms.mapIndexed { i, m -> if (flex[i] == null) m.measure(Constraints(maxWidth = w)) else null }
        val room = viewport.roundToPx() - fixed.sumOf { it?.height ?: 0 } - g * (ms.size - 1).coerceAtLeast(0)
        val minPx = flex.map { it?.min?.roundToPx() ?: 0 }
        val size = IntArray(ms.size) { i ->
            val f = flex[i] ?: return@IntArray 0
            if (f.max == Dp.Infinity) minPx[i] else f.max.roundToPx()
        }
        var over = flex.indices.filter { flex[it] != null }.sumOf { size[it] } - room
        if (over > 0) {
            for (order in flex.mapNotNull { it?.order }.distinct().sorted()) {
                val group = flex.indices.filter { flex[it]?.order == order }
                val slack = group.sumOf { size[it] - minPx[it] }
                if (slack <= 0) continue
                val take = minOf(over, slack)
                var taken = 0
                group.forEachIndexed { k, i ->
                    val d = if (k == group.lastIndex) take - taken else (take.toLong() * (size[i] - minPx[i]) / slack).toInt()
                    size[i] -= d
                    taken += d
                }
                over -= take
                if (over <= 0) break
            }
        } else if (over < 0) {
            val grow = flex.indices.filter { flex[it]?.max == Dp.Infinity }
            grow.forEachIndexed { k, i -> size[i] += -over / grow.size + if (k < -over % grow.size) 1 else 0 }
        }
        val placed = ms.mapIndexed { i, m -> fixed[i] ?: m.measure(Constraints(maxWidth = w, minHeight = size[i], maxHeight = size[i])) }
        val total = placed.sumOf { it.height } + g * (ms.size - 1).coerceAtLeast(0)
        val h = maxOf(total, viewport.roundToPx()).coerceIn(c.minHeight, c.maxHeight)
        layout(w, h) {
            var y = 0
            placed.forEach { it.place(0, y); y += it.height + g }
        }
    }
}

/** Высота самого высокого из [texts] стилем [style] в ширину [width] — место под строку, которая меняется. */
@Composable
fun textHeight(texts: List<String>, style: TextStyle, width: Dp): Dp {
    val tm = rememberTextMeasurer()
    val d = LocalDensity.current
    val px = with(d) { width.roundToPx().coerceAtLeast(1) }
    return texts.maxOfOrNull { t ->
        with(d) { tm.measure(typo(t), style, constraints = Constraints(maxWidth = px), density = d).size.height.toDp() }
    } ?: 0.dp
}

/** Ширина однострочного [text] стилем [style]. */
@Composable
fun textWidth(text: String, style: TextStyle): Dp {
    val tm = rememberTextMeasurer()
    val d = LocalDensity.current
    return with(d) { tm.measure(typo(text), style, density = d).size.width.toDp() }
}
