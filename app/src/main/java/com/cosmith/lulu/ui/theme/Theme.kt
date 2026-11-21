package com.cosmith.lulu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cosmith.lulu.R
import com.cosmith.lulu.core.content.SoundFamily

/** Short palette of warm flat colours, taken from the art direction mockups. */
object Lulu {
    val Cream = Color(0xFFFAF2E6)
    val Sand = Color(0xFFEAE4D2)
    val DotOff = Color(0xFFE7D4BC)
    val Ink = Color(0xFF2D281E)
    val Navy = Color(0xFF2B3B51)
    val Terracotta = Color(0xFFD4683F)
    val Brick = Color(0xFFC7522A)
    val Mustard = Color(0xFFF2B84B)
    val Sky = Color(0xFF6C98B5)
    val Sage = Color(0xFF7D916F)
    val Muted = Color(0xFF8A7F70)

    /** One colour per family of sounds, dark enough to read on cream. */
    fun ink(family: SoundFamily): Color = when (family) {
        SoundFamily.Vowel -> Brick
        SoundFamily.Continuant -> Navy
        SoundFamily.NasalVowel -> Color(0xFF5F7A52)
        SoundFamily.StopOrNasal -> Color(0xFF3F6F94)
    }

    /** Fills for the choice pebbles of "écouter et choisir". */
    val pebbles = listOf(Mustard, Sky, Terracotta, Sage)

    /** Mascot colours, one per child profile. */
    val mascots = listOf(Terracotta, Sky, Mustard, Sage, Color(0xFFD98BA0), Color(0xFF9A82C0))
}

val Fredoka = FontFamily(
    Font(R.font.fredoka_medium, FontWeight.Medium),
    Font(R.font.fredoka_semibold, FontWeight.SemiBold),
    Font(R.font.fredoka_bold, FontWeight.Bold),
)

private val typography = Typography().let { base ->
    fun TextStyle.fredoka(weight: FontWeight = FontWeight.Medium) = copy(fontFamily = Fredoka, fontWeight = weight)
    Typography(
        displayLarge = base.displayLarge.fredoka(FontWeight.SemiBold),
        displayMedium = base.displayMedium.fredoka(FontWeight.SemiBold),
        displaySmall = base.displaySmall.fredoka(FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.fredoka(FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.fredoka(FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.fredoka(FontWeight.SemiBold),
        titleLarge = base.titleLarge.fredoka(FontWeight.SemiBold),
        titleMedium = base.titleMedium.fredoka(FontWeight.SemiBold),
        titleSmall = base.titleSmall.fredoka(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.fredoka().copy(fontSize = 17.sp),
        bodyMedium = base.bodyMedium.fredoka(),
        bodySmall = base.bodySmall.fredoka(),
        labelLarge = base.labelLarge.fredoka(FontWeight.SemiBold).copy(fontSize = 16.sp),
        labelMedium = base.labelMedium.fredoka(FontWeight.SemiBold),
        labelSmall = base.labelSmall.fredoka(FontWeight.SemiBold),
    )
}

@Composable
fun LuluTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Lulu.Terracotta,
            onPrimary = Color.White,
            secondary = Lulu.Navy,
            onSecondary = Color.White,
            tertiary = Lulu.Sage,
            background = Lulu.Cream,
            onBackground = Lulu.Ink,
            surface = Lulu.Cream,
            onSurface = Lulu.Ink,
            surfaceVariant = Lulu.Sand,
            onSurfaceVariant = Lulu.Muted,
            surfaceContainer = Color(0xFFF4EADB),
            surfaceContainerHigh = Color(0xFFF0E5D4),
            surfaceContainerHighest = Lulu.Sand,
            surfaceContainerLow = Color(0xFFF7EEE1),
            surfaceContainerLowest = Color.White,
            outline = Color(0xFFCDBFA8),
            outlineVariant = Lulu.DotOff,
        ),
        typography = typography,
        content = content,
    )
}
