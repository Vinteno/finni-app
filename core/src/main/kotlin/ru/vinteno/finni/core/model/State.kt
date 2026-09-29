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
 * 10 / 5 / 10 (I81) или тем, что выбрано на итоге; `senior` — «Сложнее», план каждой недели открывается
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
    /** Неделя прошла по плану — отметка плана (I82): без перелива из «Хочу» и добора из копилки. */
    val onPlan: Boolean = false,
    /** Задание сыграно ошибочной веткой: объяснение показано, награды нет (I83). */
    val taskMissed: Boolean = false,
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
    /**
     * Стадия роста Финни, 1–4 (I82): растёт в конце главы, только когда набраны отметки — сквозные пороги
     * 2, 4 и 8 каждого типа. Глава и обстановка меняются по сюжету, стадия — по решениям. Только растёт.
     */
    val stage: Int = 1,
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
        /**
         * План по умолчанию 10 / 5 / 10 — под доход 25 (I81). Было 10 / 10 / 10 [КОНЦЕПТ] под доход 30.
         * «Нужное» 10 оставлено: каша, ягоды и мыло (11) по-прежнему не влезают в него на один.
         */
        val DEFAULT = Plan(10, 5, 10)

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

/**
 * Чем ребёнок заплатил в задании F2 (I83): из «Нужного», как задумано, или из копилки — ошибочная ветка,
 * копилка уменьшается. `EXACT` и `CHANGE` — прежняя версия задания («ровно» / «с 10 и сдачей»),
 * остались для старых сохранений и считаются оплатой из «Нужного».
 */
@Serializable
enum class PayChoice { EXACT, CHANGE, NEED, SAVINGS }

/** Сколько и откуда заплачено за вторую куртку в задании F3 — чтобы её можно было вернуть (I83). */
@Serializable
data class DuplicatePaid(val need: Int, val want: Int, val needFromWant: Int, val savings: Int) {
    val fromWallet: Int get() = need + want
}

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
    /** Задание сыграно ошибочной веткой: объяснение показано, награды нет (I83). */
    val taskMissed: Boolean = false,
    /** F3: вторая куртка куплена и ещё не возвращена — сколько за неё заплачено и откуда. */
    val duplicatePaid: DuplicatePaid? = null,
    val ballChoice: BallChoice? = null,
    val payChoice: PayChoice? = null,
    /** Выбор на экране ситуации (недели глав 2 и 3): ступенька полки ситуации, лежит в корзине. */
    val situationPick: Int? = null,
    val fed: Boolean = false,
    val washed: Boolean = false,
    val summaryChoice: SummaryChoice? = null,
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

/**
 * Плашка перехода: строка причины считается при смене главы и хранится до «Понятно». [grew] — Финни
 * подрос: [reason] — что замкнуло порог; не подрос — [reason] — отметка, которой не хватило (I82).
 * После последней главы та же запись остаётся для экрана конца игры.
 */
@Serializable
data class Transition(val chapter: Int, val reason: Reason, val weeks: Int, val grew: Boolean = true, val stage: Int = 0)

@Serializable
data class GameState(
    val version: Int = 3,
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
