package ru.vinteno.finni.ui.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.model.Fur

/**
 * `finni_pivots.json` — как собран Финни из PNG: точки поворота, порядок слоёв, родители и рамки,
 * всё в пикселях канвы для приложения (art-brief §6). Новый лист не требует правки кода.
 */
class FinniSpec private constructor(
    val pivots: Map<String, Offset>,
    val zOrder: List<String>,
    val parents: Map<String, String?>,
    /** Финни в покое со всеми аксессуарами: растягивается ровно на логическую канву рига. */
    val figureBox: Rect,
    /** Голова с ушами и аксессуарами — для значков 24–48 dp. */
    val headBox: Rect,
) {
    /** Часть рисуется в режиме головы: сама голова или её потомок (уши, глаза, рот, кепка, бант). */
    fun underHead(part: String): Boolean {
        var p: String? = part
        while (p != null) { if (p == HEAD) return true; p = parents[p] }
        return false
    }

    /**
     * Файлы слоя [part] из `z_order`: все, что могут понадобиться, — чтобы моргание или улыбка не
     * ждали загрузки. `eyes` и `mouth` сами уже нарисованы на голове, кладутся только подмены.
     */
    fun files(part: String, fur: Fur, acc: Accessory): List<String> = when {
        part == "eyes" -> listOf(body(fur, "eyes_closed"), body(fur, "eyes_happy"))
        part == "mouth" -> listOf(body(fur, "mouth_open"))
        part.startsWith("acc_") -> if (worn(part, acc)) listOf(part) else emptyList()
        else -> listOf(body(fur, part))
    }

    /** Всё, без чего этот Финни рисуется заглушкой. */
    fun files(fur: Fur, acc: Accessory, headOnly: Boolean): List<String> =
        zOrder.filter { !headOnly || underHead(it) }.flatMap { files(it, fur, acc) }

    companion object {
        const val FILE = "finni_pivots.json"
        const val HEAD = "head"

        fun body(fur: Fur, part: String) = "finni_${fur.name.lowercase()}_$part"

        /** `acc_scarf` и его возможные куски вроде `acc_scarf_back` — на шарфе; позы лёжа в главе 1 нет. */
        fun worn(part: String, acc: Accessory): Boolean {
            val name = "acc_${acc.name.lowercase()}"
            return (part == name || part.startsWith("${name}_")) && !part.endsWith("_lying")
        }

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): FinniSpec {
            val j = json.decodeFromString(Raw.serializer(), text)
            fun box(b: List<Float>) = Rect(b[0], b[1], b[0] + b[2], b[1] + b[3])
            return FinniSpec(
                pivots = j.pivots.mapValues { (_, v) -> Offset(v[0], v[1]) },
                zOrder = j.zOrder,
                parents = j.parents,
                figureBox = box(j.figureBox),
                headBox = box(j.headBox),
            )
        }
    }

    @Serializable
    private class Raw(
        val pivots: Map<String, List<Float>>,
        @SerialName("z_order") val zOrder: List<String>,
        val parents: Map<String, String?>,
        @SerialName("figure_box") val figureBox: List<Float>,
        @SerialName("head_box") val headBox: List<Float>,
    )
}
