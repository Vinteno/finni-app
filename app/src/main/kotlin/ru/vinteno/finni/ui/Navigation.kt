package ru.vinteno.finni.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import ru.vinteno.finni.ui.motion.FlightLayer
import ru.vinteno.finni.core.engine.Game
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.core.model.Phase
import ru.vinteno.finni.ui.screens.LookScreen
import ru.vinteno.finni.ui.screens.NameScreen
import ru.vinteno.finni.ui.screens.EventScreen
import ru.vinteno.finni.ui.screens.GoalScreen
import ru.vinteno.finni.ui.screens.HomeScreen
import ru.vinteno.finni.ui.screens.HomeTarget
import ru.vinteno.finni.ui.screens.StoryScreen
import ru.vinteno.finni.ui.screens.PiggyScreen
import ru.vinteno.finni.ui.screens.PlanScreen
import ru.vinteno.finni.ui.screens.ShopScreen
import ru.vinteno.finni.ui.screens.SummaryScreen
import ru.vinteno.finni.ui.screens.SituationScreen
import ru.vinteno.finni.ui.screens.SortScreen
import ru.vinteno.finni.ui.screens.DiaryScreen
import ru.vinteno.finni.ui.screens.AdultScreen
import ru.vinteno.finni.ui.screens.EndScreen
import ru.vinteno.finni.ui.components.RoomArt
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniMotion

/**
 * Экраны — screen-map.md §2. Экрана ситуации в неделях 1 и 2 нет: выбор ступеньки сделан на полке
 * магазина; в главах 2 и 3 ситуация недели — свой экран перед магазином. Плашка перехода главы — на Доме.
 */
enum class Screen(val hasBack: Boolean) {
    INTRO(false), LOOK(false), NAME(false), GOAL(false),
    HOME(false), PLAN(true), SITUATION(true), SHOP(true), PIGGY(true), SORT(true), SUMMARY(true),
    DIARY(true), ADULT(true),
    EVENT(false), END(false),
}

/**
 * Первый запуск проигрывается один раз; дальше точка возврата — Дом. Переход главы — плашкой на Доме,
 * потом выбор цели новой главы; после новоселья — экран конца игры.
 */
fun startScreen(s: GameState): Screen = when {
    !s.profile.introSeen -> Screen.INTRO
    !s.profile.created && !s.profile.lookChosen -> Screen.LOOK
    !s.profile.created -> Screen.NAME
    s.phase == Phase.TRANSITION -> Screen.HOME
    s.phase == Phase.GAME_OVER -> Screen.END
    s.chapter.goalId == null && s.phase == Phase.ONBOARDING -> Screen.GOAL
    s.phase == Phase.EVENT -> Screen.EVENT
    else -> Screen.HOME
}

/**
 * Экран, который допускает текущее состояние игры; иначе — Дом. Защита от восстановленного
 * после смерти процесса экрана, которому состояние уже не соответствует: итог раньше конца недели,
 * магазин после итога и так далее. До подтверждения плана тратить нельзя — инвариант 6: магазин
 * тогда только витрина.
 */
private fun allowed(sc: Screen, s: GameState, game: Game): Screen {
    val w = s.week
    val ok = when (sc) {
        Screen.PLAN -> w != null && w.announcementSeen
        // До плана магазин открыт витриной — купить нельзя (I45).
        Screen.SHOP -> w != null && w.announcementSeen && s.phase == Phase.WEEK
        // Копилку посмотреть можно всегда, пока идёт глава.
        Screen.PIGGY -> w != null && w.parcel != null && s.phase != Phase.FREE_PLAY
        Screen.SUMMARY -> game.summaryOpen(s)
        Screen.EVENT -> s.phase == Phase.EVENT
        Screen.SITUATION -> game.situationOpen(s)
        // F6 открыт и после раскладки — на нём объяснение с «Домой».
        Screen.SORT -> s.phase == Phase.WEEK && game.weekTask(s)?.template == ru.vinteno.finni.core.content.TaskTemplate.SORT && game.shopDone(s)
        Screen.DIARY -> s.week != null
        else -> true
    }
    return if (ok) sc else Screen.HOME
}

/**
 * Навигация глубиной два уровня от дома. «Назад» стоит на одном месте и всегда ведёт на Дом;
 * первый запуск и событие назад не отматываются — screen-map.md §3. Переход — горизонтальный
 * сдвиг 320 мс (animation-howto §7.5), без анимаций — мгновенно.
 */
@Composable
fun FinniNavHost(state: GameState) {
    var chosen by rememberSaveable { mutableStateOf(Screen.HOME) }
    // Раздел взрослого открыт поверх всего, кроме первого запуска: из него запускается демо.
    val onboarding = startScreen(state).takeIf { it != Screen.HOME && it != Screen.EVENT && !(chosen == Screen.ADULT && state.profile.created) }
    val screen = onboarding ?: if (state.phase == Phase.EVENT && chosen != Screen.SUMMARY && chosen != Screen.ADULT) Screen.EVENT else allowed(chosen, state, app().game)
    // Комната главы: холодная, новый дом, со светом лампы — фон всех экранов с комнатой.
    val room = RoomArt.of(state.progress.chapter, "lampa" in state.progress.inventory)
    SideEffect { RoomArt.name = room }
    val home = { chosen = Screen.HOME }
    BackHandler(enabled = screen.hasBack) { home() }

    val flights = app().flights
    val animationOn = app().animationOn
    Box(
        // Клавиатура на экране имени поднимает экран, а не закрывает поле и кнопку «Готово».
        Modifier.fillMaxSize().background(FinniColors.BgSand).windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
            // Любое касание досматривает анимацию до конца и переводит экран в конечное состояние (§8).
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (flights.flights.isNotEmpty()) flights.finishAll()
                }
            },
    ) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val spec = if (animationOn) tween<androidx.compose.ui.unit.IntOffset>(FinniMotion.SCREEN_MS, easing = FastOutSlowInEasing) else snap()
                val forward = targetState.ordinal > initialState.ordinal
                slideInHorizontally(spec) { if (forward) it else -it } togetherWith slideOutHorizontally(spec) { if (forward) -it else it }
            },
            label = "screen",
        ) { sc ->
            Box(Modifier.fillMaxSize().background(FinniColors.BgSand)) {
                when (sc) {
                    Screen.INTRO -> StoryScreen(state)
                    Screen.LOOK -> LookScreen(state)
                    Screen.NAME -> NameScreen(state)
                    Screen.GOAL -> GoalScreen()
                    Screen.HOME -> HomeScreen(state) { target ->
                        chosen = when (target) {
                            HomeTarget.PLAN -> Screen.PLAN
                            HomeTarget.SHOP -> Screen.SHOP
                            HomeTarget.SITUATION -> Screen.SITUATION
                            HomeTarget.PIGGY -> Screen.PIGGY
                            HomeTarget.SORT -> Screen.SORT
                            HomeTarget.SUMMARY -> Screen.SUMMARY
                            HomeTarget.DIARY -> Screen.DIARY
                            HomeTarget.ADULT -> Screen.ADULT
                            HomeTarget.EVENT -> Screen.EVENT
                        }
                    }
                    Screen.PLAN -> PlanScreen(state, onBack = home, onConfirmed = home)
                    Screen.SITUATION -> SituationScreen(state, onBack = home, onChosen = { chosen = Screen.SHOP })
                    Screen.SHOP -> ShopScreen(state, onLeave = home)
                    Screen.SORT -> SortScreen(state, onBack = home, onDone = home)
                    Screen.DIARY -> DiaryScreen(state, onBack = home)
                    Screen.ADULT -> AdultScreen(state, onBack = home)
                    Screen.END -> EndScreen(state)
                    Screen.PIGGY -> PiggyScreen(state, onBack = home)
                    Screen.SUMMARY -> SummaryScreen(state, onBack = home, onDone = {
                        chosen = Screen.HOME
                    })
                    Screen.EVENT -> EventScreen(state, onDone = home)
                }
            }
        }
        FlightLayer()
    }
}
