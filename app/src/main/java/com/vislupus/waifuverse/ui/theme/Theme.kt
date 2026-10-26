package com.vislupus.waifuverse.ui.theme

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vislupus.waifuverse.config.AppConfig
import com.vislupus.waifuverse.config.EnglishTexts
import com.vislupus.waifuverse.config.Neutrals
import com.vislupus.waifuverse.config.Texts
import com.vislupus.waifuverse.config.TextsConfig
import com.vislupus.waifuverse.config.ThemeConfig
import com.vislupus.waifuverse.config.ThemeMode
import com.vislupus.waifuverse.data.AppSettings

/** Current UI strings (language chosen in Settings). */
val LocalTexts = staticCompositionLocalOf<Texts> { EnglishTexts }

/** Extra colours that Material 3 has no slot for (bubbles, muted text...). */
val LocalNeutrals = staticCompositionLocalOf<Neutrals> { ThemeConfig.light }

/** Roboto-like system sans-serif font used everywhere. */
val AppFontFamily: FontFamily = FontFamily.SansSerif

/** Parses "#RRGGBB" / "#AARRGGBB" strings from the config into a Compose colour. */
fun colorFromHex(hex: String, fallback: Color = Color.Gray): Color = try {
    Color(AndroidColor.parseColor(hex))
} catch (_: Exception) {
    fallback
}

@Composable
fun WaifuVerseTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accent = ThemeConfig.accent(settings.accentId)
    val primary = if (dark) accent.dark else accent.light
    val neutrals = if (dark) ThemeConfig.dark else ThemeConfig.light
    val scheme = buildColorScheme(dark, primary, neutrals)
    val scale = ThemeConfig.fontScale(settings.fontScaleId).scale
    val texts = TextsConfig.forLanguage(settings.language)

    val context = LocalContext.current
    LaunchedEffect(dark) {
        val activity = context as? ComponentActivity
        activity?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
    }

    CompositionLocalProvider(LocalTexts provides texts, LocalNeutrals provides neutrals) {
        MaterialTheme(
            colorScheme = scheme,
            typography = buildTypography(scale),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(ThemeConfig.RADIUS_SMALL.dp),
                small = RoundedCornerShape(ThemeConfig.RADIUS_SMALL.dp),
                medium = RoundedCornerShape(ThemeConfig.RADIUS_MEDIUM.dp),
                large = RoundedCornerShape(ThemeConfig.RADIUS_LARGE.dp),
                extraLarge = RoundedCornerShape(ThemeConfig.RADIUS_LARGE.dp),
            ),
            content = content,
        )
    }
}

private fun buildColorScheme(dark: Boolean, primary: Color, n: Neutrals): ColorScheme {
    val onPrimary = if (primary.luminance() > 0.6f) Color(0xFF15161C) else Color.White
    val container = primary.copy(alpha = if (dark) 0.26f else 0.14f).compositeOver(n.surface)
    val onContainer = if (dark) lerp(primary, Color.White, 0.6f) else lerp(primary, Color.Black, 0.45f)
    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = primary,
            onSecondary = onPrimary,
            secondaryContainer = container,
            onSecondaryContainer = onContainer,
            tertiary = primary,
            background = n.background,
            onBackground = n.text,
            surface = n.surface,
            onSurface = n.text,
            surfaceVariant = n.surfaceVariant,
            onSurfaceVariant = n.textMuted,
            surfaceContainer = n.surfaceContainer,
            surfaceContainerHigh = n.surfaceVariant,
            surfaceContainerHighest = n.surfaceVariant,
            surfaceContainerLow = n.surface,
            surfaceContainerLowest = n.background,
            outline = n.outline,
            outlineVariant = n.outline,
            error = n.error,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = primary,
            onSecondary = onPrimary,
            secondaryContainer = container,
            onSecondaryContainer = onContainer,
            tertiary = primary,
            background = n.background,
            onBackground = n.text,
            surface = n.surface,
            onSurface = n.text,
            surfaceVariant = n.surfaceVariant,
            onSurfaceVariant = n.textMuted,
            surfaceContainer = n.surfaceContainer,
            surfaceContainerHigh = n.surfaceVariant,
            surfaceContainerHighest = n.surfaceVariant,
            surfaceContainerLow = n.surface,
            surfaceContainerLowest = Color.White,
            outline = n.outline,
            outlineVariant = n.outline,
            error = n.error,
        )
    }
}

/** Relative luminance (0..1) used to pick a readable text colour on the accent. */
private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private fun buildTypography(scale: Float): Typography {
    fun style(size: Float, weight: FontWeight = FontWeight.Normal, lineHeightFactor: Float = 1.4f) = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = weight,
        fontSize = (size * scale).sp,
        lineHeight = (size * scale * lineHeightFactor).sp,
    )
    return Typography(
        displaySmall = style(ThemeConfig.FONT_HEADLINE + 6f, FontWeight.SemiBold, 1.2f),
        headlineMedium = style(ThemeConfig.FONT_HEADLINE, FontWeight.SemiBold, 1.2f),
        headlineSmall = style(ThemeConfig.FONT_TITLE + 2f, FontWeight.SemiBold, 1.25f),
        titleLarge = style(ThemeConfig.FONT_TITLE, FontWeight.SemiBold, 1.25f),
        titleMedium = style(ThemeConfig.FONT_BODY + 1f, FontWeight.SemiBold, 1.3f),
        titleSmall = style(ThemeConfig.FONT_BODY_SMALL + 1f, FontWeight.SemiBold, 1.3f),
        bodyLarge = style(ThemeConfig.FONT_BODY),
        bodyMedium = style(ThemeConfig.FONT_BODY_SMALL + 1f),
        bodySmall = style(ThemeConfig.FONT_BODY_SMALL),
        labelLarge = style(ThemeConfig.FONT_BODY_SMALL + 1f, FontWeight.Medium, 1.2f),
        labelMedium = style(ThemeConfig.FONT_LABEL + 1f, FontWeight.Medium, 1.2f),
        labelSmall = style(ThemeConfig.FONT_LABEL, FontWeight.Medium, 1.2f),
    )
}

/** Replaces "{key}" placeholders in a config text, e.g. texts.usingModel.fill("model" to "x"). */
fun String.fill(vararg args: Pair<String, String>): String {
    var out = this
    for ((k, v) in args) out = out.replace("{$k}", v)
    return out
}

fun String.fill(args: Map<String, String>): String {
    var out = this
    for ((k, v) in args) out = out.replace("{$k}", v)
    return out
}

/** Pre-computed defaults used by previews. */
object PreviewDefaults {
    val settings = AppSettings(accentId = AppConfig.DEFAULT_ACCENT_ID)
}
