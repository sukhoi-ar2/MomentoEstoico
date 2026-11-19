package com.momentoestoico.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.momentoestoico.R

/** Paleta zen: papel y tinta de día; piedra y oro viejo de noche. */
@Immutable
data class ZenColors(
    val skyTop: Color,
    val skyBottom: Color,
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val accent: Color,
    val card: Color,
    val line: Color,
    val dark: Boolean,
) {
    val sky: Brush get() = Brush.verticalGradient(listOf(skyTop, skyBottom))
}

val DayColors = ZenColors(
    skyTop = Color(0xFFF6F2EA),
    skyBottom = Color(0xFFE8DFD0),
    ink = Color(0xFF2F2B27),
    inkSoft = Color(0xFF4A443E),
    muted = Color(0xFF8C8278),
    accent = Color(0xFF7C8B6F),
    card = Color(0x99FFFFFF),
    line = Color(0x1F2F2B27),
    dark = false,
)

val NightColors = ZenColors(
    skyTop = Color(0xFF101214),
    skyBottom = Color(0xFF1D2125),
    ink = Color(0xFFEAE3D6),
    inkSoft = Color(0xFFCFC7B9),
    muted = Color(0xFF8F887D),
    accent = Color(0xFFB9A77E),
    card = Color(0x14FFFFFF),
    line = Color(0x1FEAE3D6),
    dark = true,
)

val LocalZen = staticCompositionLocalOf { DayColors }

private fun cormorant(weight: Int, italic: Boolean = false) = Font(
    if (italic) R.font.cormorant_italic else R.font.cormorant,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Cormorant Garamond (SIL OFL): serif clásica, de inspiración renacentista. */
val Serif = FontFamily(
    cormorant(300), cormorant(400), cormorant(500), cormorant(600),
    cormorant(300, true), cormorant(400, true), cormorant(500, true),
)

object ZenType {
    /** Texto de la cita. */
    val quote = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 38.sp)
    val quoteSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 21.sp, lineHeight = 27.sp)
    val title = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 44.sp)
    val time = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Light, fontSize = 44.sp, lineHeight = 44.sp)
    val author = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, fontSize = 19.sp)
    /** Versalitas espaciadas para etiquetas. */
    val caps = TextStyle(fontSize = 11.sp, letterSpacing = 0.28.em, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)
}

@Composable
fun ZenTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val zen = if (dark) NightColors else DayColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = zen.accent, onPrimary = Color(0xFF1A1A17), surface = Color(0xFF1B1E21),
            onSurface = zen.ink, background = zen.skyTop, onBackground = zen.ink,
            surfaceContainerHigh = Color(0xFF23272B), surfaceContainerHighest = Color(0xFF2A2E33),
            onSurfaceVariant = zen.muted, outline = zen.muted, secondaryContainer = Color(0xFF34392F),
            onSecondaryContainer = zen.ink, tertiaryContainer = Color(0xFF3A3529), primaryContainer = Color(0xFF3A3529),
            onPrimaryContainer = zen.ink,
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF5E6E52), onPrimary = Color.White, surface = Color(0xFFF7F3EC),
            onSurface = zen.ink, background = zen.skyTop, onBackground = zen.ink,
            surfaceContainerHigh = Color(0xFFF1EBE1), surfaceContainerHighest = Color(0xFFEAE2D5),
            onSurfaceVariant = zen.muted, outline = zen.muted, secondaryContainer = Color(0xFFDCE2D3),
            onSecondaryContainer = zen.ink, tertiaryContainer = Color(0xFFE7DFCC), primaryContainer = Color(0xFFDCE2D3),
            onPrimaryContainer = zen.ink,
        )
    }
    CompositionLocalProvider(LocalZen provides zen) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
