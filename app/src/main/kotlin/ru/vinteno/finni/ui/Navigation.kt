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
import ru.vinteno.finni.ui.theme.FinniColors
import ru.vinteno.finni.ui.theme.FinniMotion

/**
 * Экраны прототипа — screen-map.md §2 минус снятое build-plan.md §2: «Выбор масштаба», прогресс,
 * раздел взрослого, плашка перехода, конец игры. Экрана ситуации в неделях 1 и 2 нет:
 * выбор ступеньки сделан на полке магазина.
 */
enum class Screen(val hasBack: Boolean) {
    INTRO(false), LOOK(false), NAME(false), GOAL(false),
    HOME(false), PLAN(true), SHOP(true), PIGGY(true), SUMMARY(true),
    EVENT(false),
}

/** Первый запуск проигрывается один раз; дальше точка возврата — Дом. */
fun startScreen(s: GameState): Screen = when {
    !s.profile.introSeen -> Screen.INTRO
    !s.profile.created && !s.profile.lookChosen -> Screen.LOOK
    !s.profile.created -> Screen.NAME
    s.chapter.goalId == null -> Screen.GOAL
    s.phase == Phase.EVENT -> Screen.EVENT
    else -> Screen.HOME
}

/**
 * Экран, который допускает текущее состояние игры; иначе — Дом. Защита от восстановленного
 * после смерти процесса экрана, которому состояние уже не соответствует: магазин до плана,
 * итог без недели и так далее. До подтверждения плана тратить нельзя — инвариант 6.
 */
private fun allowed(sc: Screen, s: GameState): Screen {
    val w = s.week
    val ok = when (sc) {
        Screen.PLAN -> w != null && w.announcementSeen
        Screen.SHOP -> w != null && w.planConfirmed && s.phase == Phase.WEEK
        Screen.PIGGY -> w != null && w.planConfirmed && s.phase != Phase.FREE_PLAY
        Screen.SUMMARY -> w != null && w.planConfirmed && s.phase == Phase.WEEK
        Screen.EVENT -> s.phase == Phase.EVENT
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
    val onboarding = startScreen(state).takeIf { it != Screen.HOME && it != Screen.EVENT }
    val screen = onboarding ?: if (state.phase == Phase.EVENT && chosen != Screen.SUMMARY) Screen.EVENT else allowed(chosen, state)
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
                            HomeTarget.PIGGY -> Screen.PIGGY
                            HomeTarget.SUMMARY -> Screen.SUMMARY
                            HomeTarget.EVENT -> Screen.EVENT
                        }
                    }
                    Screen.PLAN -> PlanScreen(state, onBack = home, onConfirmed = home)
                    Screen.SHOP -> ShopScreen(state, onLeave = home)
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
