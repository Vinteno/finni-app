package ru.vinteno.finni

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.model.Fur
import ru.vinteno.finni.ui.pet.Finni
import ru.vinteno.finni.ui.theme.FinniColors

/** Контрольный лист 3 × 3 — чек-лист animation-howto.md §11, пункт 2: девять комбинаций из одного тела. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class PetSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nineCombinations() {
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
                    Accessory.entries.forEach { acc ->
                        Finni(Fur.GINGER, acc, Modifier.width(48.dp).padding(2.dp), animate = false, headOnly = true)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/shots/pet_sheet.png")
    }
}
