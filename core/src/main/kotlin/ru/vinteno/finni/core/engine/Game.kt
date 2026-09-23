package ru.vinteno.finni.core.engine

import ru.vinteno.finni.core.content.Category
import ru.vinteno.finni.core.content.ChapterContent
import ru.vinteno.finni.core.content.Content
import ru.vinteno.finni.core.content.Impact
import ru.vinteno.finni.core.content.Item
import ru.vinteno.finni.core.content.TaskTemplate
import ru.vinteno.finni.core.content.WeekContent
import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.model.BallChoice
import ru.vinteno.finni.core.model.EventOutcome
import ru.vinteno.finni.core.model.Fur
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.ParcelResult
import ru.vinteno.finni.core.model.Phase
import ru.vinteno.finni.core.model.Plan
import ru.vinteno.finni.core.model.SummaryChoice
import ru.vinteno.finni.core.model.WeekState

/** Шаг недели на нижней кнопке дома — screen-map.md §3. */
enum class Step { PARCEL, ANNOUNCE, PLAN, SHOP, CARE, SAVE, SUMMARY, NEXT_WEEK, EVENT, NONE }

/**
 * Хватит ли на цель к событию — строка под «Копилкой» на плане и подписи на экране цели (I13, I14).
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

class IllegalMove(message: String) : IllegalStateException(message)

private fun rule(ok: Boolean, message: () -> String) {
    if (!ok) throw IllegalMove(message())
}

/**
 * Правила игры главы 1 для прототипа. Чистые функции: состояние на вход, новое состояние на выход.
 * Интерфейс ничего не считает сам — только спрашивает здесь.
 */
class Game(val content: Content) {
    private val ch: ChapterContent get() = content.chapter1

    // ---------- Первый запуск ----------

    fun seeIntro(s: GameState): GameState = s.copy(profile = s.profile.copy(introSeen = true))

    /** Имя не обязательно: пустое становится «Финни» — сценарий 6.3. */
    fun createPet(s: GameState, name: String, fur: Fur, accessory: Accessory): GameState {
        val petName = name.trim().ifEmpty { content.texts["create.defaultName"] }
        return s.copy(profile = s.profile.copy(petName = petName, fur = fur, accessory = accessory, created = true))
    }

    /** Цель выбирается один раз в начале главы, до первого плана — E13. */
    fun chooseGoal(s: GameState, goalId: String): GameState {
        rule(s.chapter.goalId == null) { "Цель главы уже выбрана" }
        rule(goalId in ch.goalIds) { "Цели $goalId нет в главе" }
        return startWeek(s.copy(chapter = s.chapter.copy(goalId = goalId)), 1)
    }

    // ---------- Неделя ----------

    private fun startWeek(s: GameState, n: Int): GameState = s.copy(
        phase = Phase.WEEK,
        progress = s.progress.copy(weekInChapter = n),
        // «Сыт» и «чист» обнуляются в начале недели: еда и мыло — расходники (I9).
        week = WeekState(number = n, plan = s.nextPlan),
    )

    fun weekContent(s: GameState): WeekContent = ch.week(s.requireWeek().number)

    /** Посылка: +30, если в кошельке меньше 30; иначе не приходит, и это не ошибка — E01, E02. */
    fun openParcel(s: GameState): GameState {
        val w = s.requireWeek()
        rule(w.parcel == null) { "Посылка этой недели уже открыта" }
        val arrives = s.progress.wallet < ch.income
        return s.copy(
            progress = if (arrives) s.progress.copy(wallet = s.progress.wallet + ch.income) else s.progress,
            week = w.copy(parcel = if (arrives) ParcelResult.ARRIVED else ParcelResult.NOT_ARRIVED),
        )
    }

    fun seeAnnouncement(s: GameState): GameState {
        val w = s.requireWeek()
        rule(w.parcel != null) { "Объявление идёт после посылки" }
        return s.copy(week = w.copy(announcementSeen = true))
    }

    // ---------- План ----------

    /** Черновик допускает превышение: ввод не блокируется и не исправляется — E07. */
    fun setPlan(s: GameState, plan: Plan): GameState {
        val w = s.requireWeek()
        rule(!w.planConfirmed) { "План подтверждён и заморожен до конца недели" }
        rule(plan.need >= 0 && plan.want >= 0 && plan.save >= 0) { "В направлении не бывает меньше нуля" }
        return s.copy(week = w.copy(plan = plan))
    }

    fun canConfirmPlan(s: GameState): Boolean {
        val w = s.requireWeek()
        return !w.planConfirmed && w.announcementSeen && w.plan.total <= s.progress.wallet
    }

    fun confirmPlan(s: GameState): GameState {
        rule(canConfirmPlan(s)) { "Подтверждение недоступно: разложено больше, чем в кошельке, или план уже подтверждён" }
        val w = s.requireWeek()
        return s.copy(week = w.copy(planConfirmed = true))
    }

    // ---------- Магазин ----------

    fun direction(item: Item): Direction {
        val base = item.addonOf?.let { content.item(it) } ?: item
        return if (base.category == Category.NEED) Direction.NEED else Direction.WANT
    }

    fun needLeft(s: GameState): Int = s.requireWeek().let { it.plan.need - it.paidNeed }
    fun wantLeft(s: GameState): Int = s.requireWeek().let { it.plan.want - it.paidWant }

    fun owns(s: GameState, itemId: String): Boolean = itemId in s.progress.inventory

    fun quote(s: GameState, cart: List<String>): Checkout {
        val w = s.requireWeek()
        rule(w.planConfirmed) { "До подтверждения плана тратить нельзя" }
        val items = cart.map(content::item).filterNot { it.isDurable && owns(s, it.id) }.distinctBy { it.id }
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

    /**
     * Покупка. Кнопка «Купить» не гаснет; перелив из «Хочу» и добор из копилки
     * проходят только с согласия ребёнка — флаги ставит интерфейс после окна.
     */
    fun buy(s: GameState, cart: List<String>, agreedWant: Boolean = false, agreedSavings: Boolean = false): GameState {
        val q = quote(s, cart)
        rule(q.items.isNotEmpty()) { "Корзина пуста" }
        rule(!q.asksWant || agreedWant) { "Перелив из «Хочу» без согласия запрещён" }
        rule(!q.asksSavings || agreedSavings) { "Снятие из копилки без отдельного подтверждения запрещено" }
        rule(q.savingsAfter >= 0) { "В копилке не хватает на добор" }
        rule(q.fromWallet <= s.progress.wallet) { "В кошельке не хватает" }

        val w = s.requireWeek()
        val durables = q.items.filter { it.isDurable }.map { it.id }
        var next = s.copy(
            progress = s.progress.copy(
                wallet = s.progress.wallet - q.fromWallet,
                savings = q.savingsAfter,
                inventory = s.progress.inventory + durables,
            ),
            chapter = if (q.items.any { it.id == ch.chapterWantId }) s.chapter.copy(wantBought = true) else s.chapter,
            week = w.copy(
                paidNeed = w.paidNeed + q.fromNeed,
                paidWant = w.paidWant + q.fromWantOwn + q.needFromWant,
                needFromWant = w.needFromWant + q.needFromWant,
                fromSavings = w.fromSavings + q.fromSavings,
                purchases = w.purchases + q.items.map { it.id },
            ),
        )
        if (weekTaskTemplate(next) == TaskTemplate.SHOP) next = completeTask(next)
        return next
    }

    /**
     * Шаг закрывается выходом с экрана, а не тратой. F1 засчитывается и при пустой корзине (I7, I8).
     * F5 при копилке меньше цены мячика тоже засчитывается: выбора нет из-за денег, а не из-за
     * отказа, и ноль в «Копилке» не должен стоить награды — иначе это скрытый вердикт (I19).
     */
    fun leaveShop(s: GameState): GameState {
        val w = s.requireWeek()
        rule(w.planConfirmed) { "До подтверждения плана магазин закрыт" }
        var next = s.copy(week = w.copy(shopVisited = true))
        when (weekTaskTemplate(next)) {
            TaskTemplate.SHOP -> next = completeTask(next)
            TaskTemplate.CHOICE -> if (!w.taskDone && !ballOffer(s).available) next = completeTask(next)
            null -> {}
        }
        return next
    }

    // ---------- Задания ----------

    private fun weekTaskTemplate(s: GameState): TaskTemplate? =
        weekContent(s).taskId?.let { ch.task(it).template }

    /** Награда падает в копилку, а не в кошелёк; повтор не даёт ничего — E05, E06. */
    private fun completeTask(s: GameState): GameState {
        val w = s.requireWeek()
        if (w.taskDone) return s
        val task = ch.task(weekContent(s).taskId ?: return s)
        val reward = if (task.id in s.progress.rewardedTasks) 0 else task.reward
        return s.copy(
            progress = s.progress.copy(
                savings = s.progress.savings + reward,
                rewardedTasks = s.progress.rewardedTasks + task.id,
            ),
            week = w.copy(taskDone = true, taskReward = reward),
        )
    }

    fun ballOffer(s: GameState): BallOffer {
        val w = s.requireWeek()
        val task = ch.task(weekContent(s).taskId ?: throw IllegalMove("На этой неделе нет задания"))
        rule(task.template == TaskTemplate.CHOICE) { "Задание недели — не выбор" }
        val price = content.item(task.itemId!!).price
        val goalPrice = goalPrice(s)
        val savings = s.progress.savings
        val reward = if (task.id in s.progress.rewardedTasks) 0 else task.reward
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

    /** «Взять мячик» — только из копилки; «Оставить в копилке» — ничего не списывает. Реакция одна. */
    fun chooseBall(s: GameState, take: Boolean): GameState {
        val w = s.requireWeek()
        rule(w.planConfirmed) { "До подтверждения плана тратить нельзя" }
        val offer = ballOffer(s)
        rule(offer.available) { "Выбор не предлагается: в копилке мало монет или задание пройдено" }
        val itemId = ch.task(weekContent(s).taskId!!).itemId!!
        val next = if (take) s.copy(
            progress = s.progress.copy(
                savings = s.progress.savings - offer.price,
                inventory = s.progress.inventory + itemId,
            ),
            week = w.copy(ballChoice = BallChoice.TAKEN, fromSavings = w.fromSavings + offer.price),
        ) else s.copy(week = w.copy(ballChoice = BallChoice.KEPT))
        return completeTask(next)
    }

    // ---------- Копилка ----------

    /**
     * Сколько будет в копилке к событию: накопленное сейчас плюс по каждой оставшейся неделе главы,
     * включая текущую, взнос `planSave` и награда за задание недели, если её ещё не давали.
     * Взнос текущей недели, уже сделанный, лежит в копилке и второй раз не считается.
     */
    fun savingsAtEvent(s: GameState, planSave: Int): Int {
        val w = s.week
        val from = w?.number ?: 1
        var total = s.progress.savings
        for (n in from..ch.lastWeek) {
            val current = w != null && n == w.number
            total += if (current && w!!.deposited) 0 else planSave
            val task = ch.week(n).taskId?.let(ch::task)
            if (task != null && task.id !in s.progress.rewardedTasks && !(current && w!!.taskDone)) total += task.reward
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

    /** Кнопка откладывает ровно `planSave`; при нуле и при закрытой цели её нет (I7, E16). */
    fun canDeposit(s: GameState): Boolean {
        val w = s.requireWeek()
        return w.planConfirmed && !w.deposited && w.plan.save > 0 && !goalReached(s)
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

    fun leavePiggy(s: GameState): GameState = s.copy(week = s.requireWeek().copy(piggyVisited = true))

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

    // ---------- Итог ----------

    /** Всё обязательное недели куплено — по покупке, а не по касанию миски (E18). */
    fun mandatoryBought(s: GameState): Boolean {
        val w = s.requireWeek()
        return weekContent(s).shelves.filter { it.mandatory }.all { shelf ->
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
     * максимум по одной каждого типа, счётчики только растут (E17).
     */
    fun finishWeek(s: GameState, choice: SummaryChoice): GameState {
        val w = s.requireWeek()
        rule(s.phase == Phase.WEEK) { "Итог этой недели уже пройден" }
        rule(w.planConfirmed) { "Итог идёт после подтверждённого плана" }
        // Еда куплена, миска не тронута — Финни ест сам при переходе к итогу.
        val fed = w.fed || bought(s, Impact.FED)
        val p = s.progress
        val nextPlan = when (choice) {
            SummaryChoice.KEEP_PLAN -> Plan.DEFAULT
            SummaryChoice.TAKE_ACTUAL -> summary(s).fact
        }
        val last = w.number >= ch.lastWeek
        return s.copy(
            progress = p.copy(
                marksCare = p.marksCare + if (mandatoryBought(s)) 1 else 0,
                marksPlan = p.marksPlan + 1,
                marksSave = p.marksSave + if (w.deposit > 0) 1 else 0,
            ),
            week = w.copy(fed = fed, summaryChoice = choice),
            nextPlan = nextPlan,
            // Событие играется всегда, независимо от отметок (I6).
            phase = if (last) Phase.EVENT else Phase.AFTER_SUMMARY,
        )
    }

    /** «Следующая неделя» — единственный способ начать новую неделю; привязки к календарю нет. */
    fun nextWeek(s: GameState): GameState {
        rule(s.phase == Phase.AFTER_SUMMARY) { "Следующая неделя открывается после итога" }
        return startWeek(s, s.requireWeek().number + 1)
    }

    // ---------- Нижняя кнопка ----------

    /**
     * Текущий шаг недели. Порядок свободный, кроме одного — до подтверждения плана тратить нельзя.
     * Шаг закрывается выходом с экрана, а не тратой (I7): зашёл в магазин и вышел — шаг пройден.
     * При «Копилке» 0 и при закрытой цели шага «Отложить» нет, кнопка ведёт сразу на итог.
     */
    fun nextStep(s: GameState): Step {
        when (s.phase) {
            Phase.ONBOARDING -> return Step.NONE
            Phase.AFTER_SUMMARY -> return Step.NEXT_WEEK
            Phase.EVENT -> return Step.EVENT
            Phase.FREE_PLAY -> return Step.NONE
            Phase.WEEK -> {}
        }
        val w = s.requireWeek()
        return when {
            w.parcel == null -> Step.PARCEL
            !w.announcementSeen -> Step.ANNOUNCE
            !w.planConfirmed -> Step.PLAN
            !w.shopVisited -> Step.SHOP
            canFeed(s) || canWash(s) -> Step.CARE
            canDeposit(s) && !w.piggyVisited -> Step.SAVE
            else -> Step.SUMMARY
        }
    }

    // ---------- Событие ----------

    /** Исход А — накоплено не меньше цены цели: списание и вручение здесь. Исход Б — ничего не списывается. */
    fun playEvent(s: GameState): GameState {
        rule(s.phase == Phase.EVENT) { "Событие наступает после итога последней недели" }
        val price = goalPrice(s)
        val given = s.progress.savings >= price
        return s.copy(
            progress = if (given) s.progress.copy(savings = s.progress.savings - price) else s.progress,
            eventOutcome = if (given) EventOutcome.GIFT_GIVEN else EventOutcome.NOT_ENOUGH,
            phase = Phase.FREE_PLAY,
        )
    }
}

fun GameState.requireWeek(): WeekState = week ?: throw IllegalMove("Неделя ещё не началась")
