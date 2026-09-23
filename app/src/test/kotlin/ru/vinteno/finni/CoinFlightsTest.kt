package ru.vinteno.finni

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vinteno.finni.ui.motion.CoinFlights
import ru.vinteno.finni.ui.motion.CoinTarget

/** Полёт монет — animation-howto.md §7.2: не больше шести в полёте, остальные — сразу. */
class CoinFlightsTest {
    private fun flights() = CoinFlights().apply {
        anchors["parcel"] = Rect(Offset(300f, 600f), Size(60f, 60f))
        anchors["wallet"] = Rect(Offset(280f, 20f), Size(80f, 48f))
    }

    @Test fun `тридцать монет — шесть летят, двадцать четыре на счётчике сразу`() {
        val f = flights()
        f.launch("parcel", "wallet", 30, CoinTarget.WALLET, animate = true)
        assertEquals(6, f.flights.size)
        assertEquals(6, f.walletPending) // на экране 30 − 6 = 24, дальше растёт с каждой прилетевшей
        assertEquals(listOf(0, 40, 80, 120, 160, 200), f.flights.map { it.delayMs })
        f.arrived(f.flights.first())
        assertEquals(5, f.walletPending)
    }

    @Test fun `касание досматривает полёт`() {
        val f = flights()
        f.launch("parcel", "wallet", 30, CoinTarget.WALLET, animate = true)
        f.finishAll()
        assertTrue(f.flights.isEmpty())
        assertEquals(0, f.walletPending)
    }

    @Test fun `без анимаций монеты не летят, счётчик сразу конечный`() {
        val f = flights()
        f.launch("parcel", "wallet", 30, CoinTarget.WALLET, animate = false)
        assertTrue(f.flights.isEmpty())
        assertEquals(0, f.walletPending)
    }

    @Test fun `взнос меньше шести — летят все`() {
        val f = flights().apply { anchors["piggy"] = Rect(Offset(20f, 500f), Size(200f, 24f)) }
        f.launch("wallet", "piggy", 4, CoinTarget.SAVINGS, animate = true)
        assertEquals(4, f.flights.size)
        assertEquals(4, f.savingsPending)
    }
}
