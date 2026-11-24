package app.monolauncher

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Roboto változtatható betűtípus: a vastagság minden telefonon pontosan úgy jelenik meg, ahogy kérjük. */
@OptIn(ExperimentalTextApi::class)
fun clockFont(weight: Int): FontFamily = FontFamily(
    Font(
        resId = R.font.roboto_var,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )
)

val UiFont500: FontFamily = clockFont(500)

object Ui {
    val bg = Color.Black
    val fg = Color.White
    val dim = Color(0x99FFFFFF)
    val faint = Color(0x33FFFFFF)
    val line = Color(0x26FFFFFF)
    val card = Color(0xFF111111)
    val shape = RoundedCornerShape(28.dp)
}

/** A referencia-képernyő arányai (kártya-magasság/szélesség százalékában). */
@Immutable
class Dims(val w: Dp, val h: Dp) {
    val rowH: Dp = h * 0.0628f
    val iconStart: Dp = w * 0.13f
    val textStart: Dp = w * 0.267f
}

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            background = Color.Black,
            onBackground = Color.White,
            surface = Ui.card,
            onSurface = Color.White,
        ),
        content = content,
    )
}
