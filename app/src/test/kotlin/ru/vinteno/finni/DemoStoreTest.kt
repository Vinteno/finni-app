package ru.vinteno.finni

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.vinteno.finni.core.content.Content
import ru.vinteno.finni.core.engine.Demo
import ru.vinteno.finni.core.engine.Game
import ru.vinteno.finni.data.GameStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DemoStoreTest {
    @Test fun `игра ребёнка переживает переходы демо и перезапуск приложения`() {
        val context = RuntimeEnvironment.getApplication()
        val game = Game(Content.fromResources())
        val demo = Demo(game)
        val store = GameStore(context)
        store.wipe()
        val child = demo.weekStart(6).copy(demo = false)
        store.replace(child)

        store.startDemo(demo.initial())
        assertTrue(store.state.value.demo)
        assertFalse(store.state.value.profile.introSeen)
        store.startDemo(demo.weekStart(4))
        val reopened = GameStore(context)
        assertTrue(reopened.childSaved)
        assertEquals(4, game.weekNumber(reopened.state.value))
        assertTrue(reopened.endDemo())
        assertEquals(child, reopened.state.value)
        assertFalse(reopened.childSaved)
        assertFalse(reopened.endDemo())
        assertEquals(child, reopened.state.value)
        reopened.wipe()
    }
}
