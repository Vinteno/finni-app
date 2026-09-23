package ru.vinteno.finni

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ru.vinteno.finni.core.content.Content
import ru.vinteno.finni.core.engine.Game
import ru.vinteno.finni.data.GameStore
import ru.vinteno.finni.ui.AppModel
import ru.vinteno.finni.ui.FinniNavHost
import ru.vinteno.finni.ui.LocalApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FinniApp
        setContent {
            val state by app.store.state.collectAsState()
            CompositionLocalProvider(LocalApp provides app.model) {
                FinniNavHost(state)
            }
        }
    }
}

/** Один экземпляр контента, правил и хранилища на процесс. */
class FinniApp : android.app.Application() {
    val content: Content by lazy { Content.fromResources() }
    val game: Game by lazy { Game(content) }
    val store: GameStore by lazy { GameStore(this) }
    val model: AppModel by lazy { AppModel(game, store) }
}
