package ru.vinteno.finni.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Токены гайда — style-guide.md §5–8. Значения взяты как есть; промежуточных не вводить (§1).
 * Зелёного и красного здесь нет и не будет: инвариант 7 проверяется поиском по этому файлу.
 */
object FinniColors {
    val BgSand = Color(0xFFFBF3E7)
    val BgCold = Color(0xFFEDF2F7)
    val BgHome = Color(0xFFF3F0F9)
    val Surface = Color(0xFFFFFFFF)

    val Ink = Color(0xFF2B2622)
    val InkMute = Color(0xFF6B6057)
    val Stroke = Color(0xFFD9CCB8)
    val StrokeStrong = Color(0xFF8A7A64)

    val Action = Color(0xFF2C63A8)
    val ActionPress = Color(0xFF1E4676)
    val ActionSoft = Color(0xFFDCE7F5)

    val Coin = Color(0xFFF2B01E)
    val CoinEdge = Color(0xFFB07A08)

    val Need = Color(0xFF1F7BD6)
    val NeedBg = Color(0xFFA8CBF5)
    val NeedDeep = Color(0xFF2C63A8)
    val Want = Color(0xFFCC3D86)
    val WantBg = Color(0xFFF7BEDB)
    val WantDeep = Color(0xFF9C3B6B)
    val Save = Color(0xFFC57B00)
    val SaveBg = Color(0xFFFCD183)
    val SaveDeep = Color(0xFF9A6008)

    val DisabledBg = Color(0xFFEDE3D3)
    val DisabledInk = Color(0xFF6B6057)

    /** Затемнение фона под диалогом: 40% чёрного — §10.8. */
    val Scrim = Color(0x66000000)
}

/** Шкала кеглей — §6.2. Ниже 16 sp в приложении не существует ничего. */
object FinniType {
    val Number = 40.sp
    val Title = 28.sp
    val Subtitle = 22.sp
    val Button = 20.sp
    val Body = 18.sp
    val Caption = 16.sp
}

/** Сетка 4/8 dp, размеры нажатия, скругления — §7–8. */
object FinniDimens {
    val ScreenPadding = 16.dp
    val TitleTop = 24.dp
    val CardGap = 12.dp
    val CardPadding = 16.dp
    val BottomGap = 24.dp
    val MinTouchGap = 8.dp

    val MainButtonHeight = 64.dp
    val SecondaryButtonHeight = 56.dp
    val CounterButton = 56.dp
    val BackButton = 48.dp
    val MinTouch = 48.dp
    val WalletHeight = 48.dp

    val RadiusSmall = 8.dp
    val RadiusButton = 16.dp
    val RadiusCard = 24.dp

    val Outline = 2.dp
    val LipMain = 4.dp
    val LipSecondary = 2.dp

    /** Финни в полный рост на просторных экранах; голова на плотных — §7.4. */
    val PetFull = 96.dp
    val PetHead = 48.dp
}

/** Длительности и кривые — animation-howto.md §8. Линейной кривой нет нигде. */
object FinniMotion {
    const val PRESS_MS = 120
    const val APPEAR_MS = 200
    const val SCREEN_MS = 320
    const val REACTION_MAIN_MS = 320
    const val REACTION_TAIL_MS = 600
    const val EVENT_MOVE_MS = 800
}
