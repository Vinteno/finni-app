package ru.vinteno.finni.ui.motion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Куда летят монеты: счётчик этого места растёт синхронно с прилётом. */
enum class CoinTarget { WALLET, SAVINGS }

/** Одна монета в полёте: откуда, куда, через сколько после начала стартует. */
data class Flight(val id: Long, val from: Offset, val to: Offset, val delayMs: Int, val target: CoinTarget)

/**
 * Полёт монет — animation-howto.md §7.2: посылка → кошелёк, кошелёк → копилка.
 * По дуге, 300 мс на монету, задержка 40 мс между монетами, в полёте одновременно не больше
 * шести — остальные добавляются к счётчику сразу. Любое касание досматривает полёт до конца (§8).
 *
 * Счётчик на экране — это настоящее число минус монеты, ещё не долетевшие: так он растёт
 * вместе с прилётом, а состояние игры при этом уже сохранено целиком.
 */
class CoinFlights {
    /** Места на экране в координатах корня: кошелёк, посылка, копилка. */
    val anchors = mutableStateMapOf<String, Rect>()
    val flights = mutableStateListOf<Flight>()

    var walletPending by mutableIntStateOf(0)
        private set
    var savingsPending by mutableIntStateOf(0)
        private set

    private var nextId = 0L

    /** Запустить полёт `count` монет. Без анимаций или без известных мест — ничего не летит. */
    fun launch(fromKey: String, toKey: String, count: Int, target: CoinTarget, animate: Boolean) {
        val from = anchors[fromKey]?.center
        val to = anchors[toKey]?.center
        if (!animate || from == null || to == null || count <= 0) return
        val flying = minOf(MAX_IN_FLIGHT, count)
        add(target, flying)
        repeat(flying) { i -> flights += Flight(nextId++, from, to, i * STAGGER_MS, target) }
    }

    fun arrived(f: Flight) {
        if (flights.remove(f)) add(f.target, -1)
    }

    /** Касание досматривает анимацию: все монеты сразу на месте, счётчики — конечные. */
    fun finishAll() {
        flights.clear()
        walletPending = 0
        savingsPending = 0
    }

    private fun add(t: CoinTarget, d: Int) = when (t) {
        CoinTarget.WALLET -> walletPending = (walletPending + d).coerceAtLeast(0)
        CoinTarget.SAVINGS -> savingsPending = (savingsPending + d).coerceAtLeast(0)
    }

    companion object {
        const val MAX_IN_FLIGHT = 6
        const val STAGGER_MS = 40
        const val FLIGHT_MS = 300
        const val ARC_DP = 24
    }
}
