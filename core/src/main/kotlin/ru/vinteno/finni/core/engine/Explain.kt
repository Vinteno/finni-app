package ru.vinteno.finni.core.engine

import ru.vinteno.finni.core.content.Impact
import ru.vinteno.finni.core.model.GameState

/**
 * Тексты, которые собираются из состояния. Структура одна для любого исхода — style-guide.md §12.7:
 * что сделал → что вышло → что это значит. Разная структура для «верного» и «неверного» — скрытая оценка.
 */
class Explain(private val game: Game) {
    private val texts = game.content.texts

    private fun name(s: GameState) = s.profile.petName.ifEmpty { texts["create.defaultName"] }

    /** «Ты купил кашу и мыло.» Перечисляются базовые позиции: надбавка видна в миске и на итоге (I8, I15). */
    fun did(itemIds: List<String>): String {
        val nouns = itemIds.map(game.content::item).filter { it.addonOf == null }.map { it.accusative }
        return when (nouns.size) {
            0 -> texts["explain.didNothing"]
            1 -> texts.format("explain.did", "what" to nouns[0])
            2 -> texts.format("explain.did", "what" to "${nouns[0]} и ${nouns[1]}")
            else -> texts.format("explain.didList", "what" to nouns.dropLast(1).joinToString(", ") + " и " + nouns.last())
        }
    }

    /** Плашка после выхода из магазина: три строки. «Осталось» — в «Нужном». */
    fun afterShop(s: GameState, boughtNow: List<String>): List<String> = listOf(
        did(boughtNow),
        texts.format("explain.result", "n" to game.needLeft(s).coerceAtLeast(0)),
        texts["explain.meaning.F1"],
    )

    /** Объяснение после выбора в F5 — одинаковой структуры и тона при обоих решениях. */
    fun afterBall(sAfter: GameState, took: Boolean): List<String> {
        val w = sAfter.requireWeek()
        val pendingDeposit = if (w.deposited) 0 else w.plan.save
        val enough = sAfter.progress.savings + pendingDeposit >= game.goalPrice(sAfter)
        return listOf(
            texts[if (took) "f5.did.take" else "f5.did.keep"],
            texts.format("f5.result", "n" to sAfter.progress.savings),
            texts[if (enough) "f5.meaning.yes" else "f5.meaning.no"],
        )
    }

    /**
     * Строки под рядами итога. Первая — что взято в ситуации недели или нейтральный факт,
     * что покупки не было. Дальше — откуда пришла разница: из «Хочу», из копилки (I18).
     */
    fun summaryLines(s: GameState): List<String> {
        val w = s.requireWeek()
        val week = game.weekContent(s)
        val shelf = week.shelves.first { it.id == week.situationShelf }
        val tier = shelf.tiers.lastOrNull { tier -> tier.all { it in w.purchases } }
        val boughtFood = w.purchases.any { game.content.item(it).impact == Impact.FED }
        val situationIsFood = shelf.tiers.flatten().any { game.content.item(it).impact == Impact.FED }
        val first = when {
            tier != null -> texts.format("summary.took", "what" to texts["tier.acc." + tier.joinToString("_")])
            situationIsFood -> texts.format("summary.notFed", "name" to name(s))
            else -> texts.format("summary.notWashed", "name" to name(s))
        }
        // «Не покупаю ничего»: нейтральный факт о еде виден на итоге в любую неделю — сценарий §11.
        val notFed = if (!situationIsFood && !boughtFood) texts.format("summary.notFed", "name" to name(s)) else null
        val sum = game.summary(s)
        return listOfNotNull(
            first,
            notFed,
            sum.needFromWant.takeIf { it > 0 }?.let { texts.format("summary.spillWant", "n" to it) },
            sum.paidFromSavings.takeIf { it > 0 }?.let { texts.format("summary.spillSavings", "n" to it) },
        )
    }
}
