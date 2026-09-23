package ru.vinteno.finni.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import ru.vinteno.finni.core.model.GameState
import java.io.File

/**
 * Локальное хранение состояния одним JSON-файлом во внутренней памяти приложения.
 * Сервера нет, персональных данных нет; файл пишется после каждого хода — N04:
 * закрыть приложение посреди недели и открыть заново — всё на месте.
 */
class GameStore(context: Context) {
    private val file = File(context.filesDir, "state.json")
    private val tmp = File(context.filesDir, "state.json.tmp")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<GameState> = _state

    fun update(transform: (GameState) -> GameState) {
        val next = transform(_state.value)
        write(next)
        _state.value = next
    }

    /** Заменить состояние целиком: сброс профиля и проверочные прогоны. */
    fun replace(s: GameState) = update { s }

    private fun read(): GameState =
        runCatching { json.decodeFromString<GameState>(file.readText()) }.getOrElse { GameState() }

    /** Запись через временный файл: обрыв посреди записи не портит сохранение. */
    private fun write(s: GameState) {
        tmp.writeText(json.encodeToString(GameState.serializer(), s))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
