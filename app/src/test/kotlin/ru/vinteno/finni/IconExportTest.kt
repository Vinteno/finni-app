package ru.vinteno.finni

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.vinteno.finni.core.model.Accessory
import ru.vinteno.finni.core.model.Fur
import ru.vinteno.finni.ui.components.Coin
import ru.vinteno.finni.ui.pet.Finni
import ru.vinteno.finni.ui.theme.FinniColors

/**
 * Иконка приложения — гайд §13.1: голова Финни и одна монета, больше ничего, без текста, фон
 * `bg-sand`. Снимается из того же рига, что и в игре. Выгрузка: ./gradlew :app:recordRoborazziDebug,
 * затем scripts/icons.py раскладывает PNG по mipmap и делает 512 × 512 для витрины.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w432dp-h432dp-xhdpi")
class IconExportTest {
    @get:Rule val compose = createComposeRule()

    /** Содержимое иконки на квадрате `side`: голова в центре, монета справа внизу. */
    @Composable
    private fun IconArt(side: Dp) {
        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            Finni(Fur.GINGER, Accessory.BOW, Modifier.width(side * 0.64f).offset(x = -side * 0.04f, y = -side * 0.03f), animate = false, headOnly = true)
            Coin(side * 0.2f, Modifier.align(Alignment.Center).offset(x = side * 0.3f, y = side * 0.3f))
        }
    }

    /** Передний слой адаптивной иконки: 108 dp, рисунок в центральных 72 dp, фон прозрачный. */
    @Test fun foreground() {
        compose.setContent {
            Box(Modifier.size(432.dp).background(Color.Transparent), contentAlignment = Alignment.Center) { IconArt(288.dp) }
        }
        compose.onRoot().captureRoboImage("build/icons/foreground.png")
    }

    /** Витрина RuStore: 512 × 512, без прозрачности, 10% поля по периметру. */
    @Test fun store() {
        compose.setContent {
            Box(Modifier.size(432.dp).background(FinniColors.BgSand).padding(43.dp), contentAlignment = Alignment.Center) { IconArt(346.dp) }
        }
        compose.onRoot().captureRoboImage("build/icons/store.png")
    }
}
