package ru.vinteno.finni.core.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Контент игры: предметы, цели, недели главы, задания, тексты.
 * Лежит JSON-файлами в `resources/content`, отдельно от кода — ТЗ 2.5.14.
 * Числа и строки берутся только отсюда; источник каждого файла указан в его поле `_source`.
 */

@Serializable
enum class Category { @SerialName("need") NEED, @SerialName("want") WANT }

/** Пять типов предметов, больше не бывает — items.md §1. Обстановка не покупается и в каталоге не лежит. */
@Serializable
enum class ItemType {
    @SerialName("consumable") CONSUMABLE,
    @SerialName("thing") THING,
    @SerialName("wearable") WEARABLE,
    @SerialName("furniture") FURNITURE,
}

/** Иконка влияния на карточке — та же, что в верхней полосе состояния. items.md §6. */
@Serializable
enum class Impact { @SerialName("fed") FED, @SerialName("clean") CLEAN, @SerialName("warm") WARM }

@Serializable
data class Item(
    val id: String,
    val name: String,
    /** Готовая форма винительного падежа: «Ты купил {кашу}». Склонение не генерируется. */
    val accusative: String,
    val category: Category,
    val price: Int,
    val type: ItemType,
    val impact: Impact? = null,
    /** Надбавка к базовой позиции; оплачивается из направления базовой покупки — data-model §5. */
    val addonOf: String? = null,
) {
    /** Долговременная вещь покупается один раз — правило долговременной вещи, items.md §1. */
    val isDurable: Boolean get() = type != ItemType.CONSUMABLE
}

@Serializable
data class Goal(
    val id: String,
    val name: String,
    val price: Int,
    val chapter: Int,
    /** Готовая форма для «Ты подарил Кире {what}.» — склонение не генерируется (I8). */
    val accusative: String = name,
)

@Serializable
data class Shelf(
    val id: String,
    val caption: String? = null,
    val mandatory: Boolean,
    /** Ступеньки полки, по одной на выбор: дешевле, обычно, с надбавкой. */
    val tiers: List<List<String>>,
)

@Serializable
data class WeekContent(
    val week: Int,
    val situation: String,
    /** Полка, на которой выбирается ступенька ситуации: в неделях 1 и 2 отдельного экрана ситуации нет. */
    val situationShelf: String,
    val announcement: List<String>,
    /** Предмет, о котором объявление, — его картинка стоит на плашке рядом с первой строкой. */
    val announceItem: String? = null,
    val shelves: List<Shelf>,
    val taskId: String? = null,
)

@Serializable
enum class TaskTemplate { @SerialName("shop") SHOP, @SerialName("choice") CHOICE }

@Serializable
data class TaskDef(
    val id: String,
    val template: TaskTemplate,
    val title: String,
    val reward: Int,
    val itemId: String? = null,
)

@Serializable
data class ChapterContent(
    val chapter: Int,
    val background: String,
    /** Фиксированный доход посылки; он же порог: при кошельке от него и выше посылка не приходит. */
    val income: Int,
    val eventName: String,
    val goalIds: List<String>,
    val chapterWantId: String,
    val weeks: List<WeekContent>,
    val tasks: List<TaskDef>,
) {
    val lastWeek: Int get() = weeks.maxOf { it.week }
    fun week(n: Int): WeekContent = weeks.first { it.week == n }
    fun task(id: String): TaskDef = tasks.first { it.id == id }
}

@Serializable
private data class Catalog(val items: List<Item>, val goals: List<Goal>)

/**
 * Незаполненный `{ключ}`. Закрывающая скобка экранирована: движок регулярных выражений Android (ICU)
 * без этого падает, хотя обычная Java такое выражение принимает.
 */
private val UNFILLED = Regex("""\{[a-z_]+\}""")

@Serializable
data class Texts(val plural: Map<String, List<String>>, val strings: Map<String, String>) {
    operator fun get(key: String): String = strings[key] ?: error("Нет строки «$key» в texts.ru.json")

    /**
     * Слова экрана для потолка 25 — так, как их считает сам сценарий: итог недели там «23 слова»
     * без чисел. Словом считается то, в чём есть буква; числа идут подписью и в счёт не входят.
     */
    fun screenWords(s: String): Int = s.split(Regex("\\s+")).count { w -> w.any { it.isLetter() } }

    /** Форма слова для числа: 1 монета, 2 монеты, 5 монет. Три формы заданы в файле. */
    fun plural(word: String, n: Int): String {
        val forms = plural[word] ?: error("Нет форм слова «$word»")
        val mod100 = n % 100
        val mod10 = n % 10
        return when {
            mod100 in 11..14 -> forms[2]
            mod10 == 1 -> forms[0]
            mod10 in 2..4 -> forms[1]
            else -> forms[2]
        }
    }

    /**
     * Подстановка `{ключ}`. Для `{n}` сами подставляются формы слова «монета»:
     * `{coins}` — 1 монета, `{coins_acc}` — убери 1 монету, `{coins_gen}` — не хватает 1 монеты.
     */
    fun format(key: String, vararg args: Pair<String, Any>): String {
        val map = args.associate { it.first to it.second.toString() }.toMutableMap()
        map["n"]?.toIntOrNull()?.let { n ->
            map.putIfAbsent("coins", plural("coin", n))
            map.putIfAbsent("coins_acc", plural("coin_acc", n))
            map.putIfAbsent("coins_gen", plural("coin_gen", n))
        }
        var s = get(key)
        for ((k, v) in map) s = s.replace("{$k}", v)
        check(!UNFILLED.containsMatchIn(s)) { "Не подставлено в «$key»: $s" }
        return s
    }
}

class Content(
    val items: Map<String, Item>,
    val goals: Map<String, Goal>,
    val chapter1: ChapterContent,
    val texts: Texts,
) {
    fun item(id: String): Item = items[id] ?: error("Нет предмета $id")
    fun goal(id: String): Goal = goals[id] ?: error("Нет цели $id")

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(read: (String) -> String): Content {
            val catalog = json.decodeFromString<Catalog>(read("items.json"))
            return Content(
                items = catalog.items.associateBy { it.id },
                goals = catalog.goals.associateBy { it.id },
                chapter1 = json.decodeFromString(read("chapter1.json")),
                texts = json.decodeFromString(read("texts.ru.json")),
            )
        }

        /** Из ресурсов модуля core — так же на JVM в тестах и в APK. */
        fun fromResources(): Content = load { name ->
            val stream = Content::class.java.getResourceAsStream("/content/$name")
                ?: error("Нет файла контента $name")
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }
}
