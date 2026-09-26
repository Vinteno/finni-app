package ru.vinteno.finni.core.engine

import ru.vinteno.finni.core.content.Category
import ru.vinteno.finni.core.content.ChapterContent
import ru.vinteno.finni.core.content.Content
import ru.vinteno.finni.core.content.Impact
import ru.vinteno.finni.core.content.Item
import ru.vinteno.finni.core.content.ItemType
import ru.vinteno.finni.core.content.Shelf
import ru.vinteno.finni.core.content.TaskDef
import ru.vinteno.finni.core.content.TaskTemplate
import ru.vinteno.finni.core.content.WeekContent
import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.model.BallChoice
import ru.vinteno.finni.core.model.ChapterState
import ru.vinteno.finni.core.model.EventOutcome
import ru.vinteno.finni.core.model.Fur
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.ParcelResult
import ru.vinteno.finni.core.model.PayChoice
import ru.vinteno.finni.core.model.Phase
import ru.vinteno.finni.core.model.Plan
import ru.vinteno.finni.core.model.Profile
import ru.vinteno.finni.core.model.Progress
import ru.vinteno.finni.core.model.Reason
import ru.vinteno.finni.core.model.SummaryChoice
import ru.vinteno.finni.core.model.Transition
import ru.vinteno.finni.core.model.WeekRecord
import ru.vinteno.finni.core.model.WeekState

/** Шаг недели на записке дома — screen-map.md §3, I45. `SORT` — задание F6 перед итогом. */
enum class Step { PARCEL, ANNOUNCE, PLAN, SHOP, CARE, SAVE, SORT, SUMMARY, NEXT_WEEK, EVENT, NONE }

/**
 * Хватит ли на цель к событию — строка под «Копилкой» на плане (I13, I14).
 * Говорит не про срок, а про сумму: сколько накопится к событию при таком взносе.
 */
enum class Enough { SURPLUS, EXACT, SHORT }

/** Направление плана, из которого платится покупка. */
enum class Direction { NEED, WANT }

/**
 * Расклад оплаты корзины по направлениям — data-model §5 «Из какого направления что списывается».
 * Порядок один для любой покупки: своё направление → «Хочу» → копилка с отдельным подтверждением.
 * Копилка добирает недостающее, а не платит целиком (решение I5).
 */
data class Checkout(
    /** Позиции, за которые платим; уже купленные долговременные вещи отброшены — E11. */
    val items: List<Item>,
    val total: Int,
    val fromNeed: Int,
    val fromWantOwn: Int,
    /** Недостача «Нужного», которую может закрыть «Хочу»: окно «Не хватает N. В „Хочу“ есть M». */
    val needShortage: Int,
    val wantAvailableForNeed: Int,
    val needFromWant: Int,
    /** Сколько добирается из копилки; окно «Взять из копилки?». */
    val fromSavings: Int,
    val savingsAfter: Int,
) {
    val asksWant: Boolean get() = needFromWant > 0
    val asksSavings: Boolean get() = fromSavings > 0
    val fromWallet: Int get() = fromNeed + fromWantOwn + needFromWant
}

/** Предпросмотр задания F5 — последствие показывается до выбора. */
data class BallOffer(
    val available: Boolean,
    val price: Int,
    /** «Останется {n} из {goal}» — копилка сразу после покупки мячика. */
    val savingsAfter: Int,
    val goalPrice: Int,
    /** `S − 15 + планСбережения + награда ≥ цена цели` — «Подарок всё равно будет». */
    val giftStillPossible: Boolean,
)

/** Три факта итога — плана, выхода и награды; «Вышло» — покупки плюс взнос (C3). */
data class Summary(
    val planned: Int,
    /** Потрачено из кошелька по плану: из «Нужного», из «Хочу», в копилку. */
    val fact: Plan,
    val reward: Int,
    val needFromWant: Int,
    /** Покупки, оплаченные из копилки: добор при нехватке и мячик. Как их показывать на итоге — вопрос I18. */
    val paidFromSavings: Int,
    val fedThisWeek: Boolean,
)

/**
 * Карточка задания F6: одна трата недели — картинка, цена и направление, куда её кладут. `direction`
 * `null` — взнос, он кладётся в «Копилку». Направление — по категории вещи, как её подписывает корзина.
 */
data class SortCard(val id: String, val price: Int, val direction: Direction?)

class IllegalMove(message: String) : IllegalStateException(message)

private fun rule(ok: Boolean, message: () -> String) {
    if (!ok) throw IllegalMove(message())
}

/**
 * Правила игры, три главы. Чистые функции: состояние на вход, новое состояние на выход.
 * Интерфейс ничего не считает сам — только спрашивает здесь.
 */
class Game(val content: Content) {
    /** Глава, которая идёт сейчас. */
    fun ch(s: GameState): ChapterContent = content.chapter(s.progress.chapter)

    // ---------- Первый запуск ----------

    fun seeIntro(s: GameState): GameState = s.copy(profile = s.profile.copy(introSeen = true))

    /**
     * Внешность и имя выбираются на двух экранах подряд: сначала ребёнок собирает питомца, потом
     * называет того, кого собрал. Выбор внешности сохраняется сразу, чтобы перезапуск между экранами
     * его не терял.
     */
    fun chooseLook(s: GameState, fur: Fur, accessory: Accessory): GameState {
        rule(!s.profile.created) { "Питомец уже создан" }
        return s.copy(profile = s.profile.copy(fur = fur, accessory = accessory, lookChosen = true))
    }

    /** С экрана имени назад к внешности: выбор остаётся, его можно поменять. */
    fun backToLook(s: GameState): GameState {
        rule(!s.profile.created) { "Питомец уже создан" }
        return s.copy(profile = s.profile.copy(lookChosen = false))
    }

    /**
     * Имя после внешности. Экран не даёт нажать «Готово» с пустым полем; пустое имя здесь всё равно
     * становится «Финни», чтобы правило не зависело от экрана.
     */
    fun namePet(s: GameState, name: String): GameState {
        rule(s.profile.lookChosen) { "Сначала выбирается внешность" }
        rule(!s.profile.created) { "Питомец уже создан" }
        val petName = name.trim().ifEmpty { content.texts["create.defaultName"] }
        return s.copy(profile = s.profile.copy(petName = petName, created = true))
    }

    /** Внешность и имя одним ходом: для тестов и демо-профиля. */
    fun createPet(s: GameState, name: String, fur: Fur, accessory: Accessory): GameState =
        namePet(chooseLook(s, fur, accessory), name)

    /** Цель выбирается один раз в начале главы, до первого плана — E13. */
    fun chooseGoal(s: GameState, goalId: String): GameState {
        rule(s.chapter.goalId == null) { "Цель главы уже выбрана" }
        rule(s.phase == Phase.ONBOARDING) { "Цель выбирается в начале главы" }
        rule(goalId in ch(s).goalIds) { "Цели $goalId нет в главе" }
        return startWeek(s.copy(chapter = s.chapter.copy(goalId = goalId)), 1)
    }

    // ---------- Настройки взрослого ----------

    /** «Проще / Сложнее» (I49, A7): действует со следующего плана, текущий не трогает. */
    fun setDifficulty(s: GameState, senior: Boolean): GameState =
        s.copy(profile = s.profile.copy(scale = if (senior) Profile.SENIOR else Profile.JUNIOR))

    /** Анимации — тумблер взрослого перекрывает системную настройку (ТЗ 3.6). */
    fun setAnimations(s: GameState, on: Boolean): GameState = s.copy(profile = s.profile.copy(animationOn = on, animationSet = true))

    /**
     * Бонус взрослого (I49, F12.3): раз в игровую неделю +5 в копилку «за дело в жизни». Дом показывает
     * плашку с источником и суммой (ТЗ 2.5.4). Копилка не поднимается выше 100 — инвариант 9.
     */
    fun canBonus(s: GameState): Boolean {
        val w = s.week ?: return false
        if (s.phase != Phase.WEEK && s.phase != Phase.AFTER_SUMMARY || w.bonus != 0) return false
        // Взнос и награда этой недели ещё придут в копилку — место под них держится.
        val pending = if (s.phase == Phase.WEEK) (if (w.deposited) 0 else w.plan.save) + pendingReward(s) else 0
        return s.progress.savings + pending + BONUS <= CEILING
    }

    fun adultBonus(s: GameState): GameState {
        rule(canBonus(s)) { "Бонус этой недели уже добавлен" }
        val w = s.requireWeek()
        return s.copy(
            progress = s.progress.copy(savings = s.progress.savings + BONUS),
            week = w.copy(bonus = BONUS, bonusSeen = false),
        )
    }

    fun seeBonus(s: GameState): GameState {
        val w = s.requireWeek()
        return s.copy(week = w.copy(bonusSeen = true))
    }

    // ---------- Неделя ----------

    private fun startWeek(s: GameState, n: Int): GameState {
        val total = s.progress.weekTotal + 1
        val wc = ch(s).week(n)
        val taskId = if (wc.spare) spareTask(s) else wc.taskId
        // Черновик не кладёт в копилку больше, чем в неё влезет до 100 с наградой недели (инвариант 9).
        val reward = taskId?.let(content::task)?.takeIf { !wc.spare && it.id !in s.progress.rewardedTasks }?.reward ?: 0
        val draft = if (s.profile.senior) Plan.EMPTY else s.nextPlan
        val plan = draft.copy(save = minOf(draft.save, (CEILING - s.progress.savings - reward).coerceAtLeast(0)))
        // Носимое со сроком снимается само: бинт — через неделю после покупки.
        val expired = s.progress.boughtAt.filter { (id, at) -> content.item(id).wearWeeks?.let { at + it <= total } == true }.keys
        return s.copy(
            phase = Phase.WEEK,
            progress = s.progress.copy(
                weekInChapter = n,
                weekTotal = total,
                inventory = s.progress.inventory - expired,
                boughtAt = s.progress.boughtAt - expired,
            ),
            // «Сыт» и «чист» обнуляются в начале недели: еда и мыло — расходники (I9).
            // «Сложнее» — план каждой недели пустой (I49, A7).
            week = WeekState(number = n, plan = plan, taskId = taskId),
        )
    }

    /**
     * Задание запасной недели — по недостающему типу отметок (§9а): не хватает двух — по тому, которого
     * меньше; поровну — по порядку забота, накопления, план.
     */
    private fun spareTask(s: GameState): String {
        val goal = ch(s).marksToLeave ?: return SPARE_TASKS.getValue(Reason.CARE)
        val p = s.progress
        val marks = listOf(Reason.CARE to p.marksCare, Reason.SAVE to p.marksSave, Reason.PLAN to p.marksPlan)
        val missing = marks.filter { it.second < goal }.ifEmpty { marks }
        return SPARE_TASKS.getValue(missing.minBy { it.second }.first)
    }

    fun weekContent(s: GameState): WeekContent = ch(s).week(s.requireWeek().number)

    /** Номер недели на календаре — сквозной за игру. */
    fun weekNumber(s: GameState): Int = s.progress.weekTotal.takeIf { it > 0 } ?: s.week?.number ?: 1

    /** Посылка: +30, если в кошельке меньше 30; иначе не приходит, и это не ошибка — E01, E02. */
    fun openParcel(s: GameState): GameState {
        val w = s.requireWeek()
        rule(s.phase == Phase.WEEK) { "Посылка приходит в начале недели" }
        rule(w.parcel == null) { "Посылка этой недели уже открыта" }
        val income = ch(s).income
        val arrives = s.progress.wallet < income
        return s.copy(
            progress = if (arrives) s.progress.copy(wallet = s.progress.wallet + income) else s.progress,
            week = w.copy(parcel = if (arrives) ParcelResult.ARRIVED else ParcelResult.NOT_ARRIVED),
        )
    }

    fun seeAnnouncement(s: GameState): GameState {
        val w = s.requireWeek()
        rule(w.parcel != null) { "Объявление идёт после посылки" }
        return s.copy(week = w.copy(announcementSeen = true))
    }

    // ---------- План ----------

    /**
     * Сколько можно положить в «Копилку» на этой неделе, чтобы копилка с наградой за задание не
     * перевалила за 100 — инвариант 9. Каноническому пути не мешает: копилка там не выше 60.
     */
    fun saveCap(s: GameState): Int = (CEILING - s.progress.savings - pendingReward(s)).coerceAtLeast(0)

    /** Награда за задание этой недели, которая ещё придёт в копилку. */
    private fun pendingReward(s: GameState): Int =
        weekTask(s)?.let { if (s.requireWeek().taskDone) 0 else taskReward(s, it) } ?: 0

    /** Черновик допускает превышение: ввод не блокируется и не исправляется — E07. */
    fun setPlan(s: GameState, plan: Plan): GameState {
        val w = s.requireWeek()
        rule(!w.planConfirmed) { "План подтверждён и заморожен до конца недели" }
        rule(plan.need >= 0 && plan.want >= 0 && plan.save >= 0) { "В направлении не бывает меньше нуля" }
        rule(plan.save <= maxOf(saveCap(s), w.plan.save)) { "Копилка не бывает больше 100" }
        return s.copy(week = w.copy(plan = plan))
    }

    /**
     * Подтвердить можно, только когда разложен весь кошелёк: перебор и недобор блокируют одинаково
     * (сценарий, шаг 3, правило 2; I45). Иначе остаток прошлой недели лежал бы в кошельке, а
     * потратить его было бы нельзя ни из одного направления.
     */
    fun canConfirmPlan(s: GameState): Boolean {
        val w = s.requireWeek()
        return s.phase == Phase.WEEK && !w.planConfirmed && w.announcementSeen && w.plan.total == s.progress.wallet &&
            w.plan.save <= saveCap(s)
    }

    /** Подтверждение плана. Задание F4 «Сколько отложить» живёт на плане и проходится здесь (A6). */
    fun confirmPlan(s: GameState): GameState {
        rule(canConfirmPlan(s)) { "Подтверждение недоступно: разложен не весь кошелёк или план уже подтверждён" }
        val w = s.requireWeek()
        val next = s.copy(week = w.copy(planConfirmed = true))
        return if (weekTaskTemplate(next) == TaskTemplate.PLAN) completeTask(next) else next
    }

    /** План этой недели — задание F4: заголовок «Сколько отложишь?», копилка — предмет задания. */
    fun planTask(s: GameState): Boolean = weekTaskTemplate(s) == TaskTemplate.PLAN && !s.requireWeek().planConfirmed

    // ---------- Магазин ----------

    /**
     * Направление по категории самой вещи. Надбавка (ягоды, пена, рисунок…) это «Хочу», как её и
     * подписывает корзина (I25, I48 п. 2), даже если продаётся вместе с нужной вещью: база из
     * «Нужного», надбавка из «Хочу».
     */
    fun direction(item: Item): Direction =
        if (item.category == Category.NEED) Direction.NEED else Direction.WANT

    fun needLeft(s: GameState): Int = s.requireWeek().let { it.plan.need - it.paidNeed }
    fun wantLeft(s: GameState): Int = s.requireWeek().let { it.plan.want - it.paidWant }

    fun owns(s: GameState, itemId: String): Boolean = itemId in s.progress.inventory

    /**
     * Полки этой недели, которые ещё есть: полка, где всё — уже купленные долговременные вещи, пропадает
     * (куртка на запасной неделе, если куплена). Словами это не объясняется (items.md §1). Полка, с которой
     * куплено на этой неделе, в счёте остаётся — по ней видно, что обязательное куплено.
     */
    fun activeShelves(s: GameState): List<Shelf> {
        val w = s.week ?: return emptyList()
        return weekContent(s).shelves.filter { sh ->
            val items = sh.tiers.flatten()
            items.any { it in w.purchases } || !items.all { content.item(it).isDurable && owns(s, it) }
        }
    }

    /**
     * Полки, с которых на этой неделе уже куплено. Полка закрыта до конца недели (QA-M1): еда и мыло —
     * на неделю, вторая крупа ничего не даёт Финни и только съедает «Нужное». Как и у долговременной
     * вещи, это не объясняется словами — полки просто нет (items.md §1).
     */
    fun boughtShelves(s: GameState): Set<String> {
        val w = s.week ?: return emptySet()
        return weekContent(s).shelves.filter { sh -> sh.tiers.flatten().any { it in w.purchases } }.map { it.id }.toSet()
    }

    /** Полки магазина: без полки ситуации — она выбирается на своём экране. */
    fun shopShelves(s: GameState): List<Shelf> = activeShelves(s).filter { !it.onScreen }

    private fun shelfOf(s: GameState, itemId: String): String? =
        weekContent(s).shelves.firstOrNull { sh -> sh.tiers.flatten().contains(itemId) }?.id

    // ---------- Ситуация недели с предметом (главы 2 и 3) ----------

    /** Полка ситуации этой недели, если её ещё не купили: куртка, лечение, коробка… */
    fun situationShelf(s: GameState): Shelf? {
        val sh = activeShelves(s).firstOrNull { it.onScreen } ?: return null
        return sh.takeIf { it.id !in boughtShelves(s) }
    }

    /**
     * Экран ситуации открывается при входе в дверь после плана, пока вариант не выбран и не куплен.
     * Выбор кладёт вариант в корзину; деньги уходят только в магазине.
     */
    fun situationOpen(s: GameState): Boolean {
        val w = s.week ?: return false
        return s.phase == Phase.WEEK && w.planConfirmed && w.situationPick == null && situationShelf(s) != null
    }

    fun chooseSituation(s: GameState, tier: Int): GameState {
        val w = s.requireWeek()
        rule(s.phase == Phase.WEEK && w.planConfirmed) { "До подтверждения плана тратить нельзя" }
        val shelf = situationShelf(s) ?: throw IllegalMove("Ситуации этой недели нет или она куплена")
        rule(tier in shelf.tiers.indices) { "Нет такого варианта" }
        return s.copy(week = w.copy(situationPick = tier))
    }

    /** Вариант убран из корзины: при следующем входе в дверь экран ситуации откроется снова. */
    fun clearSituation(s: GameState): GameState = s.copy(week = s.requireWeek().copy(situationPick = null))

    /** Что из ситуации лежит в корзине. */
    fun situationCart(s: GameState): List<String> {
        val pick = s.week?.situationPick ?: return emptyList()
        return situationShelf(s)?.tiers?.getOrNull(pick).orEmpty()
    }

    fun quote(s: GameState, cart: List<String>): Checkout {
        val w = s.requireWeek()
        rule(w.planConfirmed) { "До подтверждения плана тратить нельзя" }
        // Свободная игра после итога — без дохода, расходов и отметок (сценарий §7).
        rule(s.phase == Phase.WEEK) { "После итога недели не тратят" }
        val closed = boughtShelves(s)
        val items = cart.map(content::item)
            .filterNot { it.isDurable && owns(s, it.id) }
            .filterNot { shelfOf(s, it.id) in closed }
            .distinctBy { it.id }
        val needCost = items.filter { direction(it) == Direction.NEED }.sumOf { it.price }
        val wantCost = items.filter { direction(it) == Direction.WANT }.sumOf { it.price }

        val needLeft = needLeft(s).coerceAtLeast(0)
        val wantLeft = wantLeft(s).coerceAtLeast(0)
        val fromNeed = minOf(needCost, needLeft)
        val fromWantOwn = minOf(wantCost, wantLeft)
        val needShortage = needCost - fromNeed
        val wantSurplus = wantLeft - fromWantOwn
        val needFromWant = minOf(needShortage, wantSurplus)
        val fromSavings = (needShortage - needFromWant) + (wantCost - fromWantOwn)

        return Checkout(
            items = items,
            total = needCost + wantCost,
            fromNeed = fromNeed,
            fromWantOwn = fromWantOwn,
            needShortage = needShortage,
            wantAvailableForNeed = wantSurplus,
            needFromWant = needFromWant,
            fromSavings = fromSavings,
            savingsAfter = s.progress.savings - fromSavings,
        )
    }

    /** В корзине предмет задания F2 — перед оплатой спрашивается, чем заплатить. */
    fun asksPay(s: GameState, q: Checkout): Boolean {
        val task = weekTask(s) ?: return false
        return task.template == TaskTemplate.PAY && !s.requireWeek().taskDone && q.items.any { it.id == task.itemId }
    }

    /**
     * Покупка. Кнопка «Купить» не гаснет; перелив из «Хочу» и добор из копилки
     * проходят только с согласия ребёнка — флаги ставит интерфейс после окна. В задании F2 способ
     * оплаты [pay] обязателен; оба способа равны, в кошельке — чистая стоимость.
     */
    fun buy(
        s: GameState,
        cart: List<String>,
        agreedWant: Boolean = false,
        agreedSavings: Boolean = false,
        pay: PayChoice? = null,
    ): GameState {
        val q = quote(s, cart)
        rule(q.items.isNotEmpty()) { "Корзина пуста" }
        rule(!q.asksWant || agreedWant) { "Перелив из «Хочу» без согласия запрещён" }
        rule(!q.asksSavings || agreedSavings) { "Снятие из копилки без отдельного подтверждения запрещено" }
        rule(q.savingsAfter >= 0) { "В копилке не хватает на добор" }
        rule(q.fromWallet <= s.progress.wallet) { "В кошельке не хватает" }
        val payTask = asksPay(s, q)
        rule(!payTask || pay != null) { "Сначала выбирается, чем заплатить" }

        val w = s.requireWeek()
        val durables = q.items.filter { it.isDurable }.map { it.id }
        val dated = q.items.filter { it.wearWeeks != null }.associate { it.id to weekNumber(s) }
        val situationBought = situationShelf(s)?.let { sh -> q.items.any { shelfOf(s, it.id) == sh.id } } == true
        var next = s.copy(
            progress = s.progress.copy(
                wallet = s.progress.wallet - q.fromWallet,
                savings = q.savingsAfter,
                inventory = s.progress.inventory + durables,
                boughtAt = s.progress.boughtAt + dated,
            ),
            chapter = if (q.items.any { it.id == ch(s).chapterWantId }) s.chapter.copy(wantBought = true) else s.chapter,
            week = w.copy(
                paidNeed = w.paidNeed + q.fromNeed,
                paidWant = w.paidWant + q.fromWantOwn + q.needFromWant,
                needFromWant = w.needFromWant + q.needFromWant,
                fromSavings = w.fromSavings + q.fromSavings,
                purchases = w.purchases + q.items.map { it.id },
                situationPick = if (situationBought) null else w.situationPick,
                payChoice = if (payTask) pay else w.payChoice,
            ),
        )
        if (weekTaskTemplate(next) == TaskTemplate.SHOP || payTask) next = completeTask(next)
        return next
    }

    /**
     * Выход из магазина только отмечает, что ребёнок в нём был. Шаг «В магазин» он не закрывает, и
     * награды F1 за него нет: награда — после «Купить» (сценарий, шаг 4, «Что меняется»; I45).
     * Шаг закрывает покупка или переход дальше — в копилку ([shopDone]).
     * Задание выбора F5 живёт на экране копилки, а не в магазине (QA-M3).
     */
    fun leaveShop(s: GameState): GameState {
        val w = s.requireWeek()
        rule(w.planConfirmed) { "До подтверждения плана магазин закрыт" }
        rule(s.phase == Phase.WEEK) { "После итога недели магазин закрыт" }
        return s.copy(week = w.copy(shopVisited = true))
    }

    /**
     * Шаг «В магазин» пройден: в магазине что-то куплено, или ребёнок побывал в нём и пошёл дальше — в
     * копилку. Просто зашёл и вышел — шаг остаётся (I45). К итогу раньше этого не пускает календарь.
     */
    fun shopDone(s: GameState): Boolean {
        val w = s.requireWeek()
        return w.purchases.isNotEmpty() || (w.shopVisited && w.piggyVisited)
    }

    // ---------- Задания ----------

    /**
     * Задание этой недели, если оно сейчас возможно. F3 «Куртка уже есть» — только когда куртка есть:
     * кто её не купил, тому смотреть в корзине не на что, и задания нет.
     */
    fun weekTask(s: GameState): TaskDef? {
        val w = s.week ?: return null
        val id = w.taskId ?: runCatching { weekContent(s) }.getOrNull()?.takeIf { !it.spare }?.taskId ?: return null
        val task = content.task(id)
        if (task.template == TaskTemplate.DUPLICATE && task.itemId?.let { owns(s, it) } != true && !w.taskDone) return null
        return task
    }

    private fun weekTaskTemplate(s: GameState): TaskTemplate? = weekTask(s)?.template

    /** Награда: на запасной неделе её нет, повторная — ноль (E06, §9а). */
    fun taskReward(s: GameState, task: TaskDef): Int =
        if (weekContent(s).spare || task.id in s.progress.rewardedTasks) 0 else task.reward

    /** Плановый взнос этой недели ещё не сделан. */
    private fun depositPending(s: GameState): Boolean = s.requireWeek().let { !it.deposited && it.plan.save > 0 }

    /** Задание выбора этой недели ещё не пройдено — шаг «Копилка» остаётся в последовательности (QA-M3). */
    fun choicePending(s: GameState): Boolean =
        s.phase == Phase.WEEK && weekTaskTemplate(s) == TaskTemplate.CHOICE && !s.requireWeek().taskDone

    /**
     * Задание F5 открывается на экране копилки сразу после планового взноса, при нуле в плане — сразу
     * (QA-M3). Так к выбору взнос уже лежит в копилке при любом порядке шагов, и «В копилке мало
     * монет» видит только тот, кто действительно не откладывал.
     */
    fun choiceOpen(s: GameState): Boolean = choicePending(s) && s.requireWeek().planConfirmed && !depositPending(s)

    /** Награда падает в копилку, а не в кошелёк; повтор не даёт ничего — E05, E06. */
    private fun completeTask(s: GameState): GameState {
        val w = s.requireWeek()
        if (w.taskDone) return s
        val task = weekTask(s) ?: return s
        // Копилка полна до 100 — награда добирает только до потолка (инвариант 9; бывает, только если
        // откладывать почти всё все недели подряд).
        val reward = minOf(taskReward(s, task), CEILING - s.progress.savings).coerceAtLeast(0)
        return s.copy(
            progress = s.progress.copy(
                savings = s.progress.savings + reward,
                rewardedTasks = if (reward > 0) s.progress.rewardedTasks + task.id else s.progress.rewardedTasks,
                doneTasks = (s.progress.doneTasks + task.id).distinct(),
            ),
            week = w.copy(taskDone = true, taskReward = reward),
        )
    }

    fun ballOffer(s: GameState): BallOffer {
        val w = s.requireWeek()
        val task = weekTask(s) ?: throw IllegalMove("На этой неделе нет задания")
        rule(task.template == TaskTemplate.CHOICE) { "Задание недели — не выбор" }
        val price = content.item(task.itemId!!).price
        val goalPrice = goalPrice(s)
        val savings = s.progress.savings
        val reward = taskReward(s, task)
        // Взнос, который ещё будет сделан по подтверждённому плану; уже сделанный сидит в S.
        val pendingDeposit = if (w.deposited) 0 else w.plan.save
        return BallOffer(
            available = !w.taskDone && savings >= price,
            price = price,
            savingsAfter = savings - price,
            goalPrice = goalPrice,
            giftStillPossible = savings - price + pendingDeposit + reward >= goalPrice,
        )
    }

    /**
     * В копилке меньше цены мячика: выбор не предлагается, ребёнок видит «В копилке мало монет» и
     * нажимает «Понятно». Задание засчитывается с наградой — отсутствие денег не отказ (I19).
     */
    fun acknowledgeNoBall(s: GameState): GameState {
        rule(choiceOpen(s)) { "Задание выбора — на копилке после взноса" }
        rule(!ballOffer(s).available) { "Выбор доступен — его нужно сделать" }
        return completeTask(s)
    }

    /** «Взять мячик» — только из копилки; «Оставить в копилке» — ничего не списывает. Реакция одна. */
    fun chooseBall(s: GameState, take: Boolean): GameState {
        val w = s.requireWeek()
        rule(w.planConfirmed) { "До подтверждения плана тратить нельзя" }
        rule(s.phase == Phase.WEEK) { "После итога недели не тратят" }
        rule(choiceOpen(s)) { "Задание выбора — на копилке после взноса" }
        val offer = ballOffer(s)
        rule(offer.available) { "Выбор не предлагается: в копилке мало монет или задание пройдено" }
        val itemId = weekTask(s)!!.itemId!!
        val next = if (take) s.copy(
            progress = s.progress.copy(
                savings = s.progress.savings - offer.price,
                inventory = s.progress.inventory + itemId,
            ),
            week = w.copy(ballChoice = BallChoice.TAKEN, fromSavings = w.fromSavings + offer.price),
        ) else s.copy(week = w.copy(ballChoice = BallChoice.KEPT))
        return completeTask(next)
    }

    /**
     * F3 «Куртка уже есть»: в корзине магазина лежит вторая куртка. Ребёнок убирает её или нажимает
     * «Купить» — дубль не списывается ни при каком выборе, награды нет, и это не комментируется.
     */
    fun duplicatePending(s: GameState): Boolean {
        val w = s.week ?: return false
        return s.phase == Phase.WEEK && w.planConfirmed && !w.taskDone && weekTaskTemplate(s) == TaskTemplate.DUPLICATE
    }

    fun resolveDuplicate(s: GameState): GameState {
        rule(duplicatePending(s)) { "Задания «уже есть» сейчас нет" }
        return completeTask(s)
    }

    /** F6 «Что задумал и что вышло»: ждёт, когда ребёнок разложит траты, — перед итогом. */
    fun sortPending(s: GameState): Boolean {
        val w = s.week ?: return false
        return s.phase == Phase.WEEK && w.planConfirmed && !w.taskDone && weekTaskTemplate(s) == TaskTemplate.SORT
    }

    /** Траты недели карточками: покупки по одной, у надбавки своя карточка, и взнос, если был. */
    fun sortCards(s: GameState): List<SortCard> {
        val w = s.requireWeek()
        val bought = w.purchases.map(content::item).map { SortCard(it.id, it.price, direction(it)) }
        return bought + listOfNotNull(if (w.deposit > 0) SortCard(DEPOSIT, w.deposit, null) else null)
    }

    fun finishSort(s: GameState): GameState {
        rule(sortPending(s)) { "Раскладывать сейчас нечего" }
        rule(shopDone(s)) { "Траты раскладываются после магазина" }
        return completeTask(s)
    }

    // ---------- Копилка ----------

    /**
     * Сколько будет в копилке к событию: накопленное сейчас плюс по каждой оставшейся неделе главы,
     * включая текущую, взнос `planSave` и награда за задание недели, если её ещё не давали.
     * Взнос текущей недели, уже сделанный, лежит в копилке и второй раз не считается. Глава считается
     * самой короткой: запасная неделя не обещается заранее.
     */
    fun savingsAtEvent(s: GameState, planSave: Int): Int {
        val c = ch(s)
        val w = s.week
        val from = w?.number ?: 1
        var total = s.progress.savings
        for (n in from..maxOf(from, c.minWeeks)) {
            val current = w != null && n == w.number
            total += if (current && w!!.deposited) 0 else planSave
            val wc = c.week(n)
            val taskId = if (current) w!!.taskId ?: wc.taskId else wc.taskId
            val task = taskId?.let(content::task)
            if (task != null && !wc.spare && task.id !in s.progress.rewardedTasks && !(current && w!!.taskDone)) total += task.reward
        }
        return total
    }

    private fun enough(atEvent: Int, price: Int) = when {
        atEvent > price -> Enough.SURPLUS
        atEvent == price -> Enough.EXACT
        else -> Enough.SHORT
    }

    /** Строка под «Копилкой»: меняется на каждое нажатие счётчика, считается от выбранной цели. */
    fun enoughForGoal(s: GameState): Enough = enough(savingsAtEvent(s, s.requireWeek().plan.save), goalPrice(s))

    /** Подпись под целью на экране выбора — до выбора, при взносе из плана по умолчанию. */
    fun enoughPreview(s: GameState, goalId: String): Enough =
        enough(savingsAtEvent(s, Plan.DEFAULT.save), content.goal(goalId).price)

    fun goalPrice(s: GameState): Int = content.goal(s.chapter.goalId ?: throw IllegalMove("Цель не выбрана")).price

    /** Накоплено не меньше цены цели — «Подарок готов». Ничего не списывает (I4). */
    fun goalReached(s: GameState): Boolean = s.progress.savings >= goalPrice(s)

    /**
     * Кнопка откладывает ровно `planSave`; при нуле её нет (I7). Взнос исполняется, как запланирован,
     * и при набранной цели: излишек остаётся в копилке (QA-M2, E16, I32 во всех главах — I49 A5).
     */
    fun canDeposit(s: GameState): Boolean {
        val w = s.week ?: return false
        return s.phase == Phase.WEEK && w.planConfirmed && !w.deposited && w.plan.save > 0
    }

    fun deposit(s: GameState): GameState {
        rule(canDeposit(s)) { "Взнос сейчас не предлагается" }
        val w = s.requireWeek()
        val d = w.plan.save
        return s.copy(
            progress = s.progress.copy(wallet = s.progress.wallet - d, savings = s.progress.savings + d),
            week = w.copy(deposit = d, deposited = true),
        )
    }

    /**
     * Выход из копилки закрывает шаг «Отложить» и считается переходом дальше после магазина. До плана
     * и после итога копилку только смотрят — тогда выход шагов не закрывает.
     */
    fun leavePiggy(s: GameState): GameState {
        val w = s.requireWeek()
        if (!w.planConfirmed || s.phase != Phase.WEEK) return s
        return s.copy(week = w.copy(piggyVisited = true))
    }

    // ---------- Забота дома: денег не трогает ----------

    private fun bought(s: GameState, impact: Impact): Boolean =
        s.requireWeek().purchases.any { content.item(it).impact == impact }

    fun canFeed(s: GameState): Boolean = bought(s, Impact.FED) && !s.requireWeek().fed
    fun canWash(s: GameState): Boolean = bought(s, Impact.CLEAN) && !s.requireWeek().washed

    fun feed(s: GameState): GameState {
        rule(canFeed(s)) { "Кормить нечем или Финни уже поел" }
        return s.copy(week = s.requireWeek().copy(fed = true))
    }

    fun wash(s: GameState): GameState {
        rule(canWash(s)) { "Мыла нет или Финни уже умыт" }
        return s.copy(week = s.requireWeek().copy(washed = true))
    }

    /**
     * Финни зябнет — с перехода в главу 2 до покупки куртки, только дома (I49, G2). Куртка куплена —
     * тепло навсегда; в главе 3 зябнуть нельзя вовсе: реакция принадлежит главе «Холода».
     */
    fun cold(s: GameState): Boolean = s.progress.chapter == 2 && !owns(s, "kurtka")

    /** Что Финни носит сейчас: куртка с рисунком, бинт — слои поверх тела. */
    fun worn(s: GameState): Set<String> =
        s.progress.inventory.filter { content.items[it]?.type == ItemType.WEARABLE }.toSet()

    // ---------- Итог ----------

    /** Всё обязательное недели куплено — по покупке, а не по касанию миски (E18). */
    fun mandatoryBought(s: GameState): Boolean {
        val w = s.requireWeek()
        return activeShelves(s).filter { it.mandatory }.all { shelf ->
            shelf.tiers.flatten().any { it in w.purchases }
        }
    }

    fun summary(s: GameState): Summary {
        val w = s.requireWeek()
        return Summary(
            planned = w.plan.total,
            fact = Plan(w.paidNeed, w.paidWant, w.deposit),
            reward = w.taskReward,
            needFromWant = w.needFromWant,
            paidFromSavings = w.fromSavings,
            fedThisWeek = bought(s, Impact.FED),
        )
    }

    /**
     * Выход с итога одним из двух равноправных действий. Начисляет отметки недели:
     * максимум по одной каждого типа, счётчики только растут (E17). Кончилась глава — событие; строка
     * причины перехода считается здесь, пока видно, что замкнуло порог этой неделей.
     */
    fun finishWeek(s: GameState, choice: SummaryChoice): GameState {
        val w = s.requireWeek()
        rule(s.phase == Phase.WEEK) { "Итог этой недели уже пройден" }
        rule(w.planConfirmed) { "Итог идёт после подтверждённого плана" }
        rule(shopDone(s)) { "Итог — в конце недели: сначала магазин" }
        rule(!sortPending(s)) { "Сначала задание недели" }
        // Еда куплена, миска не тронута — Финни ест сам при переходе к итогу.
        val fed = w.fed || bought(s, Impact.FED)
        val care = mandatoryBought(s)
        val saved = w.deposit > 0
        val p = s.progress
        val nextPlan = when (choice) {
            SummaryChoice.KEEP_PLAN -> w.plan
            SummaryChoice.TAKE_ACTUAL -> summary(s).fact
        }
        val marked = p.copy(
            marksCare = p.marksCare + if (care) 1 else 0,
            marksPlan = p.marksPlan + 1,
            marksSave = p.marksSave + if (saved) 1 else 0,
            history = p.history + WeekRecord(
                chapter = p.chapter, week = w.number, number = weekNumber(s), plan = w.plan,
                fact = summary(s).fact, reward = w.taskReward, purchases = w.purchases,
                taskId = weekTask(s)?.id, taskDone = w.taskDone, care = care, bonus = w.bonus,
            ),
        )
        val chapter = s.chapter.copy(
            careWeeks = s.chapter.careWeeks + if (care) 1 else 0,
            saveWeeks = s.chapter.saveWeeks + if (saved) 1 else 0,
            planWeeks = s.chapter.planWeeks + 1,
        )
        val c = ch(s)
        val goal = c.marksToLeave
        val reached = goal != null && marked.marksCare >= goal && marked.marksPlan >= goal && marked.marksSave >= goal
        // Событие играется всегда, независимо от отметок (I6); главы 1 и 2 — от двух до трёх недель.
        val end = w.number >= c.maxWeeks || (w.number >= c.minWeeks && reached)
        return s.copy(
            progress = marked,
            chapter = chapter,
            week = w.copy(fed = fed, summaryChoice = choice),
            nextPlan = nextPlan,
            phase = if (end) Phase.EVENT else Phase.AFTER_SUMMARY,
            transition = if (end && !c.last) reason(p, marked, chapter, goal!!).let { r ->
                Transition(c.chapter + 1, r, weeksOf(chapter, r))
            } else null,
        )
    }

    /**
     * Строка причины (сценарий главы 1, §9): что замкнуло порог этой неделей — забота, накопления, план
     * по порядку; ничего (глава кончилась по лимиту или порог набран раньше) — что ребёнок делал в главе
     * чаще остального. Число в строке — недели, когда действие было, а не длина главы (D6).
     */
    private fun reason(before: Progress, after: Progress, chapter: ChapterState, goal: Int): Reason {
        val crossed = listOf(
            Reason.CARE to (before.marksCare < goal && after.marksCare >= goal),
            Reason.SAVE to (before.marksSave < goal && after.marksSave >= goal),
            Reason.PLAN to (before.marksPlan < goal && after.marksPlan >= goal),
        ).firstOrNull { it.second }?.first
        return crossed ?: listOf(Reason.CARE, Reason.SAVE, Reason.PLAN).maxBy { weeksOf(chapter, it) * 10 - it.ordinal }
    }

    private fun weeksOf(c: ChapterState, r: Reason) = when (r) {
        Reason.CARE -> c.careWeeks
        Reason.SAVE -> c.saveWeeks
        Reason.PLAN -> c.planWeeks
    }

    /** «Следующая неделя» — единственный способ начать новую неделю; привязки к календарю нет. */
    fun nextWeek(s: GameState): GameState {
        rule(s.phase == Phase.AFTER_SUMMARY) { "Следующая неделя открывается после итога" }
        return startWeek(s, s.requireWeek().number + 1)
    }

    // ---------- Записка ----------

    /**
     * Текущий шаг недели на записке — первый несделанный по порядку: посылка → план → магазин →
     * забота → копилка → итог → следующая неделя (I45). Магазин пройден по [shopDone]. При «Копилке» 0
     * шага «Отложить» нет, записка зовёт сразу к итогу. Исключение — неделя с заданием выбора: шаг
     * «Копилка» стоит, пока задание не пройдено, даже при нуле (QA-M3). Задание F6 — перед итогом.
     */
    fun nextStep(s: GameState): Step {
        when (s.phase) {
            Phase.ONBOARDING, Phase.TRANSITION, Phase.GAME_OVER, Phase.FREE_PLAY -> return Step.NONE
            Phase.AFTER_SUMMARY -> return Step.NEXT_WEEK
            Phase.EVENT -> return Step.EVENT
            Phase.WEEK -> {}
        }
        val w = s.requireWeek()
        return when {
            w.parcel == null -> Step.PARCEL
            !w.announcementSeen -> Step.ANNOUNCE
            !w.planConfirmed -> Step.PLAN
            !shopDone(s) -> Step.SHOP
            canFeed(s) || canWash(s) -> Step.CARE
            choicePending(s) -> Step.SAVE
            canDeposit(s) && !w.piggyVisited -> Step.SAVE
            sortPending(s) -> Step.SORT
            else -> Step.SUMMARY
        }
    }

    /** Итог открывается, когда неделя может кончиться: план подтверждён, магазин пройден, F6 разложено (I45). */
    fun summaryOpen(s: GameState): Boolean =
        s.phase == Phase.WEEK && s.week?.planConfirmed == true && shopDone(s) && !sortPending(s)

    // ---------- Событие, смена главы, конец игры ----------

    /** Хватает ли на цель к событию: исход А или Б. До события и на нём — одно правило. */
    fun eventGiven(s: GameState): Boolean = s.progress.savings >= goalPrice(s)

    /**
     * Исход А — накоплено не меньше цены цели: списание здесь; мебель глав 2 и 3 встаёт в комнату.
     * Исход Б — ничего не списывается. Оба исхода играются, прогресс не обнуляется. Излишек копилки
     * переходит в следующую главу (I49, A5). После события главы 1 или 2 — новая глава с плашкой
     * перехода, после новоселья — конец игры.
     */
    fun playEvent(s: GameState): GameState {
        rule(s.phase == Phase.EVENT) { "Событие наступает после итога последней недели" }
        val goalId = s.chapter.goalId!!
        val price = goalPrice(s)
        val given = s.progress.savings >= price
        val keeps = s.progress.chapter >= 2
        val progress = if (given) s.progress.copy(
            savings = s.progress.savings - price,
            inventory = if (keeps) s.progress.inventory + goalId else s.progress.inventory,
        ) else s.progress
        val done = s.copy(
            progress = progress,
            eventOutcome = if (given) EventOutcome.GIFT_GIVEN else EventOutcome.NOT_ENOUGH,
            eventGoal = goalId,
        )
        if (ch(s).last) return done.copy(phase = Phase.GAME_OVER)
        return done.copy(
            progress = progress.copy(chapter = s.progress.chapter + 1, weekInChapter = 1),
            chapter = ChapterState(),
            phase = Phase.TRANSITION,
            week = null,
            transition = s.transition ?: Transition(s.progress.chapter + 1, Reason.PLAN, s.chapter.planWeeks),
        )
    }

    /** «Понятно» на плашке перехода — дальше выбор цели новой главы. */
    fun seeTransition(s: GameState): GameState {
        rule(s.phase == Phase.TRANSITION) { "Перехода сейчас нет" }
        return s.copy(phase = Phase.ONBOARDING, transition = null)
    }

    /** «Играть дальше» — свободная игра всем купленным, без дохода, расходов и отметок. */
    fun keepPlaying(s: GameState): GameState {
        rule(s.phase == Phase.GAME_OVER) { "Игра ещё не пройдена" }
        return s.copy(phase = Phase.FREE_PLAY)
    }

    /**
     * «Начать сначала» — то же прохождение заново (I49, E7): питомец и настройки остаются, прогресс,
     * монеты и вещи — с нуля, дальше выбор цели главы 1. Вторых вариантов сюжета нет.
     */
    fun restart(s: GameState): GameState {
        rule(s.phase == Phase.GAME_OVER || s.phase == Phase.FREE_PLAY) { "Начать сначала можно после конца игры" }
        return GameState(profile = s.profile, demo = s.demo)
    }

    companion object {
        /** Инвариант 9: любое число на экране не выше 100 — копилка тоже. */
        const val CEILING = 100

        /** Бонус взрослого за неделю (I49, F12.3). */
        const val BONUS = 5

        /** Карточка взноса в задании F6. */
        const val DEPOSIT = "deposit"

        /** Задание запасной недели по недостающему типу отметок — таблица §9а. */
        val SPARE_TASKS = mapOf(Reason.CARE to "F1", Reason.SAVE to "F4", Reason.PLAN to "F6")
    }
}

fun GameState.requireWeek(): WeekState = week ?: throw IllegalMove("Неделя ещё не началась")
