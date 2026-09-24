package ru.vinteno.finni.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.vinteno.finni.core.model.GameState
import ru.vinteno.finni.ui.app
import ru.vinteno.finni.ui.pet.Finni
import ru.vinteno.finni.ui.theme.FinniDimens

/**
 * Голова Финни 48 dp рядом с заголовком на плотных экранах — гайд §7.4.
 * На плане и итоге `live = false`: там нет ни одного движения (animation-howto.md §10).
 * На магазине и копилке `live = true` — только реакция на покупку и взнос; холостого дыхания
 * нет, потому что это экраны решения, а там зацикленное движение запрещено (U06).
 */
@Composable
fun PetHead(s: GameState, live: Boolean = false) {
    val a = app()
    Finni(
        s.profile.fur, s.profile.accessory, Modifier.width(FinniDimens.PetHead),
        reaction = if (live) a.reaction else null, reactionKey = a.reactionKey,
        animate = live && a.animationOn, idle = false, headOnly = true,
    )
}

/** Иконка Финни 24 dp в плашке объяснения — гайд §10.11. Неподвижна. */
@Composable
fun PetIcon(s: GameState) {
    Finni(s.profile.fur, s.profile.accessory, Modifier.size(24.dp), animate = false, headOnly = true)
}
