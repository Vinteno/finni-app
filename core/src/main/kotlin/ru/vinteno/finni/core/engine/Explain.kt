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
        val nouns = itemIds.distinct().map(game.content::item).filter { it.addonOf == null }.map { it.accusative }
        return when (nouns.size) {
            0 -> texts["explain.didNothing"]
            1 -> texts.format("explain.did", "what" to nouns[0])
            2 -> texts.format("explain.did", "what" to "${nouns[0]} и ${nouns[1]}")
            else -> texts.format("explain.didList", "what" to nouns.dropLast(1).joinToString(", ") + " и " + nouns.last())
        }
    }

    /**
     * Плашка после выхода из магазина: три строки. Вторая называет направление, о котором речь
     * (QA-M7): «Нужное», если в заходе было нужное или не куплено ничего, иначе «Хочу».
     * Третья — что это значит для недели: есть ли всё нужное (решение Эмиля 24.09). Факт о неделе,
     * а не правило, как надо тратить: одна структура при любом наборе, оценки нет.
     */
    fun afterShop(s: GameState, boughtNow: List<String>): List<String> {
        val items = boughtNow.map(game.content::item)
        val want = items.isNotEmpty() && items.none { game.direction(it) == Direction.NEED }
        return listOf(
            did(boughtNow),
            if (want) texts.format("explain.resultWant", "n" to game.wantLeft(s).coerceAtLeast(0))
            else texts.format("explain.resultNeed", "n" to game.needLeft(s).coerceAtLeast(0)),
            weekNeeds(s),
        )
    }

    /** «Всё нужное на неделю есть.» или чего на неделю пока нет — по закрытым полкам обязательного. */
    fun weekNeeds(s: GameState): String {
        val missing = game.weekContent(s).shelves.filter { it.mandatory && it.id !in game.boughtShelves(s) }
        return when (missing.size) {
            0 -> texts["explain.needs.all"]
            1 -> texts["explain.needs.no." + missing.single().id]
            else -> texts["explain.needs.none"]
        }
    }

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

    /** Постоянные слова экрана итога: заголовок, подписи рядов, две кнопки. */
    private val summaryFixedWords: Int
        get() = listOf("summary.title", "summary.planned", "summary.actual", "summary.reward", "summary.keepPlan", "summary.takeActual")
            .sumOf { texts.screenWords(texts[it]) }

    /**
     * Строки под рядами итога — по приоритету, пока экран целиком не длиннее 25 слов (инвариант 10,
     * QA-M5): 1) что взято в ситуации недели или что её покупки не было; 2) откуда пришла разница —
     * из «Хочу», из копилки (I18); 3) где остальное задуманное — «9 монет — у тебя»; 4) что Финни
     * не поел, если ситуация недели не еда. Последние уступают место первым: голод и так виден
     * полосой «Сыт» на доме.
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
        val sum = game.summary(s)
        val candidates = listOfNotNull(
            first,
            sum.needFromWant.takeIf { it > 0 }?.let { texts.format("summary.spillWant", "n" to it) },
            sum.paidFromSavings.takeIf { it > 0 }?.let { texts.format("summary.spillSavings", "n" to it) },
            // Куда делась разница «Задумал» и «Вышло»: монеты не пропали, они в кошельке (решение Эмиля 24.09).
            (sum.planned - sum.fact.total).takeIf { it > 0 }?.let { texts.format("summary.left", "n" to it) },
            // «Не покупаю ничего»: нейтральный факт о еде виден на итоге в любую неделю — сценарий §11.
            if (!situationIsFood && !boughtFood) texts.format("summary.notFed", "name" to name(s)) else null,
        )
        var budget = SCREEN_WORDS - summaryFixedWords
        return candidates.filterIndexed { i, line ->
            val n = texts.screenWords(line)
            (i == 0 || n <= budget).also { if (it) budget -= n }
        }
    }

    companion object {
        /** Экран целиком — не больше 25 слов (инвариант 10). */
        const val SCREEN_WORDS = 25
    }
}
