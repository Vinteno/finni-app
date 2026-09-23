package ru.vinteno.finni.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import ru.vinteno.finni.R

/** Nunito, SIL OFL 1.1 — style-guide.md §6.1. Один переменный файл на все веса. */
@OptIn(ExperimentalTextApi::class)
val Nunito = FontFamily(
    Font(R.font.nunito, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.nunito, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.nunito, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

private fun style(size: TextUnit, weight: FontWeight, lineHeight: Float) = TextStyle(
    fontFamily = Nunito,
    fontSize = size,
    fontWeight = weight,
    lineHeight = lineHeight.em,
    color = FinniColors.Ink,
)

/** Шкала §6.2: кегли в sp растут вместе с системной настройкой шрифта. */
object FinniText {
    val Number = style(FinniType.Number, FontWeight.ExtraBold, 1.1f)
    val Title = style(FinniType.Title, FontWeight.Bold, 1.25f)
    val Subtitle = style(FinniType.Subtitle, FontWeight.Bold, 1.3f)
    val Button = style(FinniType.Button, FontWeight.Bold, 1.2f)
    val Body = style(FinniType.Body, FontWeight.Normal, 1.5f)
    val Caption = style(FinniType.Caption, FontWeight.Normal, 1.45f)
}
