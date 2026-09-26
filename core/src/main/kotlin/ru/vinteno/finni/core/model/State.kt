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

/** Профиль. Масштаб в прототипе всегда `junior` — вопрос A7 снят с прототипа. */
@Serializable
data class Profile(
    val petName: String = "",
    val fur: Fur = Fur.GINGER,
    val accessory: Accessory = Accessory.SCARF,
    val scale: String = "junior",
    val introSeen: Boolean = false,
    /** Внешность выбрана, дальше экран имени. У старых сохранений `false`, их ведёт `created`. */
    val lookChosen: Boolean = false,
    val created: Boolean = false,
    val soundOn: Boolean = true,
    /** Флаг закладывается с первого дня, даже без тумблера — animation-howto.md §9. */
    val animationOn: Boolean = true,
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
)

@Serializable
data class ChapterState(
    val goalId: String? = null,
    val wantBought: Boolean = false,
)

@Serializable
data class Plan(val need: Int, val want: Int, val save: Int) {
    val total: Int get() = need + want + save

    companion object {
        /** План по умолчанию 10 / 10 / 10 [КОНЦЕПТ]. */
        val DEFAULT = Plan(10, 10, 10)
    }
}

@Serializable
enum class ParcelResult { ARRIVED, NOT_ARRIVED }

@Serializable
enum class BallChoice { TAKEN, KEPT }

@Serializable
enum class SummaryChoice { KEEP_PLAN, TAKE_ACTUAL }

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
    val taskDone: Boolean = false,
    val taskReward: Int = 0,
    val ballChoice: BallChoice? = null,
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
    /** Конец последней недели: событие главы. */
    EVENT,
    /** Прототип кончился событием; дальше свободная игра без кнопки «Следующая неделя». */
    FREE_PLAY,
}

@Serializable
enum class EventOutcome { GIFT_GIVEN, NOT_ENOUGH }

@Serializable
data class GameState(
    val version: Int = 1,
    val profile: Profile = Profile(),
    val progress: Progress = Progress(),
    val chapter: ChapterState = ChapterState(),
    val phase: Phase = Phase.ONBOARDING,
    val week: WeekState? = null,
    /** Черновик следующего плана — результат выбора на итоге, а не константа. */
    val nextPlan: Plan = Plan.DEFAULT,
    val eventOutcome: EventOutcome? = null,
)
