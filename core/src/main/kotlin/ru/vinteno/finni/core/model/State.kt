package ru.vinteno.finni.core.model

import kotlinx.serialization.Serializable

/**
 * Состояние игры — data-model.md §1–5. Хранится локально целиком, сервера нет.
 * Числа сюда не зашиваются: всё, что не состояние, лежит в контенте.
 */

@Serializable
enum class Fur { GINGER, BLUE, BROWN }

@Serializable
enum class Accessory { SCARF, CAP, BOW }

/**
 * Профиль. Сложность выбирает взрослый (I42, I49 A7): `junior` — «Проще», план недели открывается
 * 10 / 10 / 10 или тем, что выбрано на итоге; `senior` — «Сложнее», план каждой недели открывается
 * пустым. Числа одни и те же — потолок 100 держится для всех.
 */
@Serializable
data class Profile(
    val petName: String = "",
    val fur: Fur = Fur.GINGER,
    val accessory: Accessory = Accessory.SCARF,
    val scale: String = JUNIOR,
    val introSeen: Boolean = false,
    /** Внешность выбрана, дальше экран имени. У старых сохранений `false`, их ведёт `created`. */
    val lookChosen: Boolean = false,
    val created: Boolean = false,
    val soundOn: Boolean = true,
    /** Флаг закладывается с первого дня, даже без тумблера — animation-howto.md §9. */
    val animationOn: Boolean = true,
    /** Анимации выбраны взрослым в разделе — тогда выбор перекрывает системную настройку телефона. */
    val animationSet: Boolean = false,
) {
    val senior: Boolean get() = scale == SENIOR

    companion object {
        const val JUNIOR = "junior"
        const val SENIOR = "senior"
    }
}

/** Прошедшая неделя — для дневника и раздела взрослого: только факты, без оценок. */
@Serializable
data class WeekRecord(
    val chapter: Int,
    val week: Int,
    /** Сквозной номер недели игры — как на календаре. */
    val number: Int,
    val plan: Plan,
    val fact: Plan,
    val reward: Int,
    val purchases: List<String>,
    val taskId: String? = null,
    val taskDone: Boolean = false,
    /** Всё обязательное недели куплено. */
    val care: Boolean = false,
    val bonus: Int = 0,
)

/** Прогресс: счётчики только растут — data-model §3. */
@Serializable
data class Progress(
    val chapter: Int = 1,
    val weekInChapter: Int = 1,
    val marksCare: Int = 0,
    val marksPlan: Int = 0,
    val marksSave: Int = 0,
    val inventory: List<String> = emptyList(),
    val wallet: Int = 0,
    val savings: Int = 0,
    /** Задания, за которые награда уже выдана: повтор не даёт ничего — E06. */
    val rewardedTasks: List<String> = emptyList(),
    /** Пройденные задания — и те, что без награды (F3, запасная неделя). Для дневника. */
    val doneTasks: List<String> = emptyList(),
    /** Сколько недель начато за игру: номер на календаре. У старых сохранений — 0, тогда номер недели главы. */
    val weekTotal: Int = 0,
    /** Сквозной номер недели, когда куплено носимое со сроком (бинт): снимается само через неделю. */
    val boughtAt: Map<String, Int> = emptyMap(),
    val history: List<WeekRecord> = emptyList(),
)

@Serializable
data class ChapterState(
    val goalId: String? = null,
    val wantBought: Boolean = false,
    /** Недели этой главы, когда было действие: для строки причины на переходе (D6). */
    val careWeeks: Int = 0,
    val saveWeeks: Int = 0,
    val planWeeks: Int = 0,
)

@Serializable
data class Plan(val need: Int, val want: Int, val save: Int) {
    val total: Int get() = need + want + save

    companion object {
        /** План по умолчанию 10 / 10 / 10 [КОНЦЕПТ]. */
        val DEFAULT = Plan(10, 10, 10)

        /** Пустой план профиля «Сложнее» (I49, A7). */
        val EMPTY = Plan(0, 0, 0)
    }
}

@Serializable
enum class ParcelResult { ARRIVED, NOT_ARRIVED }

@Serializable
enum class BallChoice { TAKEN, KEPT }

@Serializable
enum class SummaryChoice { KEEP_PLAN, TAKE_ACTUAL }

/** Как ребёнок заплатил в задании F2: ровно или с 10 и сдачей. В кошельке — чистая стоимость. */
@Serializable
enum class PayChoice { EXACT, CHANGE }

/** Неделя живёт от посылки до итога — data-model §5. */
@Serializable
data class WeekState(
    val number: Int,
    val parcel: ParcelResult? = null,
    val announcementSeen: Boolean = false,
    val plan: Plan,
    val planConfirmed: Boolean = false,
    /** Факт по направлению, из которого заплатили, а не по тому, что куплено. */
    val paidNeed: Int = 0,
    val paidWant: Int = 0,
    /** Часть `paidWant`, ушедшая на нужное при нехватке в «Нужном» — строка перелива на итоге. */
    val needFromWant: Int = 0,
    /** Взнос: откладывается ровно `plan.save`, один раз за неделю. */
    val deposit: Int = 0,
    val deposited: Boolean = false,
    /** Сколько добрано из копилки при нехватке в направлениях — снятие R. */
    val fromSavings: Int = 0,
    val purchases: List<String> = emptyList(),
    val shopVisited: Boolean = false,
    val piggyVisited: Boolean = false,
    /** Задание недели. У запасной недели выбирается по недостающим отметкам при её начале. */
    val taskId: String? = null,
    val taskDone: Boolean = false,
    val taskReward: Int = 0,
    val ballChoice: BallChoice? = null,
    val payChoice: PayChoice? = null,
    /** Выбор на экране ситуации (недели глав 2 и 3): ступенька полки ситуации, лежит в корзине. */
    val situationPick: Int? = null,
    val fed: Boolean = false,
    val washed: Boolean = false,
    val summaryChoice: SummaryChoice? = null,
    /** Бонус взрослого за эту неделю (I49, F12.3) и видел ли ребёнок плашку о нём. */
    val bonus: Int = 0,
    val bonusSeen: Boolean = false,
)

@Serializable
enum class Phase {
    /** Знакомство, создание Финни, выбор цели. */
    ONBOARDING,
    /** Неделя идёт. */
    WEEK,
    /** Итог пройден, свободная игра до «Следующая неделя». */
    AFTER_SUMMARY,
    /** Конец последней недели главы: событие главы. */
    EVENT,
    /** Событие сыграно, началась новая глава: дома плашка перехода со строкой причины, потом выбор цели. */
    TRANSITION,
    /** Новоселье сыграно: экран конца игры. */
    GAME_OVER,
    /** Свободная игра без дохода, расходов и отметок: после конца игры — «Играть дальше». */
    FREE_PLAY,
}

@Serializable
enum class EventOutcome { GIFT_GIVEN, NOT_ENOUGH }

/** Что замкнуло порог главы последним или чего было больше — строка причины на переходе. */
@Serializable
enum class Reason { CARE, SAVE, PLAN }

/** Плашка перехода: строка причины считается при смене главы и хранится до «Понятно». */
@Serializable
data class Transition(val chapter: Int, val reason: Reason, val weeks: Int)

@Serializable
data class GameState(
    val version: Int = 2,
    val profile: Profile = Profile(),
    val progress: Progress = Progress(),
    val chapter: ChapterState = ChapterState(),
    val phase: Phase = Phase.ONBOARDING,
    val week: WeekState? = null,
    /** Черновик следующего плана — результат выбора на итоге, а не константа. */
    val nextPlan: Plan = Plan.DEFAULT,
    val eventOutcome: EventOutcome? = null,
    /** Цель сыгранного события: на экране события и в комнате после него. */
    val eventGoal: String? = null,
    val transition: Transition? = null,
    /** Демо для проверки (ТЗ 2.5.13): игра ребёнка сохранена отдельно и вернётся по кнопке. */
    val demo: Boolean = false,
)
