package ru.vinteno.finni.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.vinteno.finni.core.content.Texts
import ru.vinteno.finni.core.engine.Explain
import ru.vinteno.finni.core.engine.Game
import ru.vinteno.finni.core.engine.IllegalMove
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.data.GameStore
import ru.vinteno.finni.ui.motion.CoinFlights
import ru.vinteno.finni.ui.pet.Reaction

/**
 * Связка интерфейса с правилами. Экран ничего не считает сам: он зовёт [act] с функцией
 * из [Game], состояние сохраняется после каждого хода. Недопустимый ход просто не проходит —
 * интерфейс такие ходы и не предлагает.
 */
class AppModel(val game: Game, private val store: GameStore) {
    val texts: Texts get() = game.content.texts
    val explain = Explain(game)
    val state get() = store.state

    /** Монеты в полёте и места на экране, между которыми они летают. */
    val flights = CoinFlights()

    /** Предметы комнаты, которые уже появились: `появление` играется один раз на предмет. */
    val seenInRoom = mutableSetOf<String>()

    /** Флаг «Анимации» — animation-howto.md §9. Выключает всё, кроме отклика кнопки. */
    val animationOn: Boolean get() = state.value.profile.animationOn

    /** Плашка объяснения, ждущая показа на Доме: после выхода из магазина. */
    var pendingPlate by mutableStateOf<List<String>?>(null)

    /** Реакция Финни: ключ растёт, чтобы одинаковая реакция игралась повторно. */
    var reaction by mutableStateOf<Reaction?>(null)
        private set
    var reactionKey by mutableStateOf(0)
        private set

    fun react(r: Reaction) {
        reaction = r
        reactionKey++
    }

    fun act(move: (GameState) -> GameState): Boolean = try {
        store.update(move)
        true
    } catch (e: IllegalMove) {
        false
    }

    fun t(key: String) = texts[key]
    fun f(key: String, vararg args: Pair<String, Any>) = texts.format(key, *args)
}

val LocalApp = compositionLocalOf<AppModel> { error("AppModel не передан") }

@Composable
fun app(): AppModel = LocalApp.current
