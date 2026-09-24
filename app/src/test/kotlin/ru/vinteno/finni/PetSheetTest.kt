package ru.vinteno.finni

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.model.Fur
import ru.vinteno.finni.ui.art.Art
import ru.vinteno.finni.ui.pet.Finni
import ru.vinteno.finni.ui.pet.Reaction
import ru.vinteno.finni.ui.theme.FinniColors

/**
 * Контрольный лист 3 × 3 — чек-лист animation-howto.md §11, пункт 2: девять комбинаций из одного
 * тела, из PNG-слоёв art/app. Внизу — головы 48 и 24 dp, как в плашках и на плане.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class PetSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nineCombinations() {
        // Лист собран из PNG, а не из заглушки: все слои и finni_pivots.json есть в сборке.
        Art.init(RuntimeEnvironment.getApplication())
        val spec = Art.finni
        assertNotNull("нет finni_pivots.json в ассетах", spec)
        Fur.entries.forEach { f -> Accessory.entries.forEach { a ->
            spec!!.files(f, a, headOnly = false).forEach { assertFalse("нет слоя $it", Art.missing(it)) }
        } }
        compose.setContent {
            Column(Modifier.background(FinniColors.BgSand).padding(8.dp)) {
                Fur.entries.forEach { fur ->
                    Row {
                        Accessory.entries.forEach { acc ->
                            Finni(fur, acc, Modifier.width(110.dp).padding(4.dp), animate = false)
                        }
                    }
                }
                Row {
                    Fur.entries.forEach { fur ->
                        Accessory.entries.forEach { acc ->
                            Finni(fur, acc, Modifier.width(36.dp).padding(2.dp), animate = false, headOnly = true)
                        }
                    }
                }
                Row {
                    Fur.entries.forEach { fur ->
                        Accessory.entries.forEach { acc ->
                            Finni(fur, acc, Modifier.size(24.dp).padding(1.dp), animate = false, headOnly = true)
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/shots/pet_sheet.png")
    }

    /** Кадры посреди реакций: лапы разведены и не обрезаны, уши едут с головой, лицо подменено. */
    @Test fun reactionFrames() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Row(Modifier.background(FinniColors.BgSand).padding(horizontal = 24.dp, vertical = 8.dp)) {
                listOf(Reaction.NOTICE, Reaction.HAPPY, Reaction.EAT, Reaction.SLEEP).forEach { r ->
                    Finni(Fur.GINGER, Accessory.BOW, Modifier.width(72.dp).padding(horizontal = 4.dp), reaction = r, reactionKey = 1, idle = false)
                }
            }
        }
        compose.mainClock.advanceTimeBy(240)
        compose.onRoot().captureRoboImage("build/shots/pet_reactions.png")
    }
}
