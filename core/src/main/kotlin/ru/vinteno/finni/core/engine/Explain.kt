package ru.vinteno.finni.core.engine

import ru.vinteno.finni.core.content.Impact
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.Reason

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

    /**
     * «Всё нужное на неделю есть.» или чего на неделю пока нет — по закрытым полкам обязательного.
     * Полка ситуации называется своей вещью: «Куртки пока нет».
     */
    fun weekNeeds(s: GameState): String {
        val missing = game.activeShelves(s).filter { it.mandatory && it.id !in game.boughtShelves(s) }
        fun key(sh: ru.vinteno.finni.core.content.Shelf) = if (sh.onScreen) sh.tiers.first().first() else sh.id
        return when {
            missing.isEmpty() -> texts["explain.needs.all"]
            missing.size == 1 -> texts["explain.needs.no." + key(missing.single())]
            missing.map { it.id }.toSet() == setOf("food", "soap") -> texts["explain.needs.none"]
            else -> texts["explain.needs.some"]
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
            week.situationShelf == "soap" -> texts.format("summary.notWashed", "name" to name(s))
            // Ситуация с предметом не куплена — нейтральный факт, без подсказки «купи» (сценарий §11).
            else -> texts.format("summary.notTaken", "what" to game.content.item(shelf.tiers.first().first()).accusative)
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

    /**
     * Объяснение после F4 — три строки, как всегда: сколько откладываем, сколько будет к событию, хватит ли.
     * Числа считаются от выбранной цели и плана, одна структура при любой сумме.
     */
    fun afterPlanTask(s: GameState): List<String> {
        val w = s.requireWeek()
        val atEvent = game.savingsAtEvent(s, w.plan.save)
        val third = if (game.goalReached(s)) chapterText(s, "enough.ready") else texts[
            when (game.enoughForGoal(s)) {
                Enough.SURPLUS -> "enough.surplus"
                Enough.EXACT -> "enough.exact"
                Enough.SHORT -> "enough.short"
            }
        ]
        return listOf(
            texts.format("f4.did", "n" to w.plan.save),
            texts.format("f4.result.${s.progress.chapter}", "n" to atEvent.coerceAtMost(Game.CEILING)),
            third,
        )
    }

    /** F3 «Куртка уже есть»: одинаково при любом выборе — убрал дубль или нажал «Купить». */
    fun afterDuplicate(): List<String> = listOf(texts["f3.did"], texts["f3.result"], texts["f3.meaning"])

    /** F2 «Чем заплатить»: оба способа верны, в кошельке — чистая стоимость, сдача — пояснение. */
    fun afterPay(s: GameState, exact: Boolean): List<String> {
        val price = game.weekTask(s)?.itemId?.let { game.content.item(it).price } ?: 0
        val given = if (exact) price else changeCoin(price)
        return listOf(
            texts.format("f2.did", "n" to given),
            if (exact) texts["f2.noChange"] else texts.format("f2.change", "n" to given - price),
            texts.format("f2.meaning", "n" to price),
        )
    }

    /** «Отдать 10 и взять сдачу»: ближайшая десятка сверху. */
    fun changeCoin(price: Int): Int = (price / 10 + 1) * 10

    /**
     * F6 «Что задумал и что вышло»: что сделали, сколько ушло на нужное, и как это против плана.
     * Факт по категории вещей, а не по тому, как ребёнок разложил, — вывод не подсказывается раньше.
     */
    fun afterSort(s: GameState): List<String> {
        val cards = game.sortCards(s)
        if (cards.isEmpty()) return listOf(texts["f6.empty"])
        val need = cards.filter { it.direction == Direction.NEED }.sumOf { it.price }
        val plan = s.requireWeek().plan.need
        return listOf(
            texts["f6.did"],
            texts.format("f6.need", "n" to need),
            texts[
                when {
                    need > plan -> "f6.more"
                    need < plan -> "f6.less"
                    else -> "f6.same"
                }
            ],
        )
    }

    /** Строка, у которой бывает своя форма для главы: `key.2` — для главы 2; нет своей — общая. */
    fun chapterText(s: GameState, key: String, vararg args: Pair<String, Any>): String {
        val own = "$key.${s.progress.chapter}"
        return if (own in texts.strings) texts.format(own, *args) else texts.format(key, *args)
    }

    /**
     * Плашка перехода главы: строка причины — действие ребёнка, замкнувшее порог (сценарий главы 1, §9),
     * потом две строки о смене обстановки. Строка считается, а не зашита.
     */
    fun transitionLines(s: GameState): List<String> {
        val t = s.transition ?: return emptyList()
        val reason = when (t.reason) {
            Reason.CARE -> texts.format("reason.care", "weeks" to weeksWord(t.weeks))
            Reason.SAVE -> texts.format("reason.save", "weeks" to weeksWord(t.weeks))
            Reason.PLAN -> texts["reason.plan"]
        }
        return listOf(reason) + game.content.chapter(t.chapter).enterLines.map { texts[it] }
    }

    /** «две недели» словами: число в строке причины — недели, когда действие было (D6). */
    private fun weeksWord(n: Int): String = texts["weeks.${n.coerceIn(1, 4)}"]

    /**
     * Строки события главы. Исход считается по копилке, а после события — по сохранённому исходу.
     * Оба исхода одной длины и тона; «не хватило» — факт, без «надо было» (сценарии, шаг 9).
     */
    fun eventLines(s: GameState): List<String> {
        val goalId = s.chapter.goalId ?: s.eventGoal ?: return emptyList()
        val goal = game.content.goal(goalId)
        val given = if (s.phase == ru.vinteno.finni.core.model.Phase.EVENT) s.progress.savings >= goal.price
        else s.eventOutcome == ru.vinteno.finni.core.model.EventOutcome.GIFT_GIVEN
        val chapter = goal.chapter
        return when (chapter) {
            1 -> listOf(texts["event.1"]) + if (given) {
                listOf(texts.format("event.a.2", "what" to goal.accusative), texts["event.a.3"])
            } else listOf(texts["event.b.2"], texts.format("event.b.3", "name" to name(s)), texts["event.b.4"])
            2 -> listOf(texts["event.snow.1"]) + if (given) {
                listOf(texts.format("event.bought", "what" to goal.accusative), texts["event.snow.a.$goalId"])
            } else listOfNotNull(
                texts.format("event.short", "what" to goal.accusative),
                texts["event.snow.b.3"],
                if (game.owns(s, "kurtka")) texts["event.snow.b.4"] else null,
            )
            else -> listOf(texts["event.home.1"]) + if (given) {
                listOf(texts.format("event.bought", "what" to goal.accusative), texts["event.home.a.3"])
            } else listOf(texts.format("event.short", "what" to goal.accusative), texts["event.home.b.3"], texts["event.home.b.4"])
        }
    }

    companion object {
        /** Экран целиком — не больше 25 слов (инвариант 10). */
        const val SCREEN_WORDS = 25
    }
}
