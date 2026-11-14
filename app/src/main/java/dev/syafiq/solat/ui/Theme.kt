package dev.syafiq.solat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.syafiq.solat.R

val Red = Color(0xFFD71921)

data class Palette(val bg: Color, val fg: Color, val dim: Color, val faint: Color)

private val Dark = Palette(bg = Color.Black, fg = Color.White, dim = Color(0xFF8C8C8C), faint = Color(0xFF262626))
private val Light = Palette(bg = Color(0xFFF2F2F2), fg = Color.Black, dim = Color(0xFF6E6E6E), faint = Color(0xFFDADADA))

val LocalPalette = staticCompositionLocalOf { Dark }
val colors: Palette @Composable get() = LocalPalette.current

/** Dot-matrix display face (Doto, OFL) standing in for Nothing's NDot. */
val DotFont = FontFamily(Font(R.font.doto_bold, FontWeight.Bold))
val SansFont = FontFamily(Font(R.font.grotesk_regular), Font(R.font.grotesk_medium, FontWeight.Medium))

@Composable
fun SolatTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** Body / label text. */
@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    color: Color = colors.fg,
    medium: Boolean = false,
    caps: Boolean = false,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
) = BasicText(
    if (caps) text.uppercase() else text,
    modifier,
    style = TextStyle(
        fontFamily = SansFont,
        fontWeight = if (medium) FontWeight.Medium else FontWeight.Normal,
        fontSize = size,
        color = color,
        letterSpacing = if (caps) 0.12.em else 0.em,
        textAlign = align,
    ),
    maxLines = maxLines,
)

/** Dot-matrix numerals and headings. */
@Composable
fun Dots(text: String, modifier: Modifier = Modifier, size: TextUnit = 20.sp, color: Color = colors.fg) = BasicText(
    text,
    modifier,
    style = TextStyle(fontFamily = DotFont, fontWeight = FontWeight.Bold, fontSize = size, color = color, letterSpacing = 0.04.em),
)

/** Subtle dot grid behind content, like Nothing's Glyph surfaces. */
fun Modifier.dotGrid(bg: Color, dot: Color) = background(bg).drawBehind {
    val step = 18.dp.toPx()
    val r = 0.9.dp.toPx()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            drawCircle(dot, r, Offset(x, y))
            x += step
        }
        y += step
    }
}
