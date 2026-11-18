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

// Nothing OS widget red from the brand reference; the print N-Red #C8102E reads too dark on black
val Red = Color(0xFFD71920)

data class Palette(val bg: Color, val fg: Color, val dim: Color, val faint: Color)

// Nothing foundation palette: pure black, pure white, Window Grey #B1B3B3, N-Grey (reference gives #DCD7D2 but RGB 242/242/238;
// the lighter RGB value keeps grey text readable on it). Red used sparingly.
private val Dark = Palette(bg = Color.Black, fg = Color.White, dim = Color(0xFFB1B3B3), faint = Color(0xFF262626))
// light mode keeps secondary text darker than #B1B3B3 so it stays readable on white
private val Light = Palette(bg = Color.White, fg = Color.Black, dim = Color(0xFF6B6D6D), faint = Color(0xFFF2F2EE))

val LocalPalette = staticCompositionLocalOf { Dark }
val colors: Palette @Composable get() = LocalPalette.current

// Nothing's type roles with stand-ins (NType 82 and Lettera Mono are proprietary):
// NDot -> dot-matrix, hero numerals only, one size per screen; NType 82 -> Space Grotesk (headlines/body);
// Lettera Mono -> Space Mono (small metadata labels, tracking 0).
val DotFont = FontFamily(Font(R.font.doto_bold, FontWeight.Bold))
val SansFont = FontFamily(Font(R.font.grotesk_regular), Font(R.font.grotesk_medium, FontWeight.Medium))
val MonoFont = FontFamily(Font(R.font.mono_regular))

@Composable
fun SolatTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** Body text (NType Regular role, sentence case) or, with [caps], a small mono metadata label (Lettera role). */
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
        fontFamily = if (caps) MonoFont else SansFont,
        fontWeight = when { // mono has one weight file, so caps + medium uses synthetic bold
            medium && caps -> FontWeight.Bold
            medium -> FontWeight.Medium
            else -> FontWeight.Normal
        },
        fontSize = size,
        color = color,
        // brand reference: mono tracking 0 / leading 140%; NType +1% tracking, leading 125% up to 15pt, 115% above
        letterSpacing = if (caps) 0.em else 0.01.em,
        lineHeight = if (caps) 1.4.em else if (size.value <= 15f) 1.25.em else 1.15.em,
        textAlign = align,
    ),
    maxLines = maxLines,
)

/** Short one- or two-word headline (NType Headline role): 40sp+, -2% tracking, sentence case. */
@Composable
fun Headline(text: String, modifier: Modifier = Modifier, size: TextUnit = 40.sp, color: Color = colors.fg) = BasicText(
    text,
    modifier,
    style = TextStyle(fontFamily = SansFont, fontWeight = FontWeight.Medium, fontSize = size, color = color, letterSpacing = (-0.02).em),
    maxLines = 1,
)

/** Dot-matrix hero numerals. Reference rule: tracking untouched, one size per screen. */
@Composable
fun Dots(text: String, modifier: Modifier = Modifier, size: TextUnit = 20.sp, color: Color = colors.fg) = BasicText(
    text,
    modifier,
    style = TextStyle(fontFamily = DotFont, fontWeight = FontWeight.Bold, fontSize = size, color = color),
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
