package com.vislupus.waifuverse.config

import androidx.compose.ui.graphics.Color

/**
 * =====================================================================
 *  THEME CONFIG — how the app looks.
 * =====================================================================
 *
 * Accent colours, neutral palettes for light/dark mode, font sizes, shapes and
 * spacing. The UI only reads values from here.
 */
object ThemeConfig {

    /** Selectable accent colours (Settings → Accent). The first one is a good default. */
    val accents: List<Accent> = listOf(
        Accent("violet", "Violet", light = Color(0xFF6C5CE7), dark = Color(0xFF9D8DFF)),
        Accent("rose", "Rose", light = Color(0xFFE0457B), dark = Color(0xFFFF7AA8)),
        Accent("coral", "Coral", light = Color(0xFFE8553C), dark = Color(0xFFFF8A70)),
        Accent("amber", "Amber", light = Color(0xFFC98A12), dark = Color(0xFFF2B544)),
        Accent("emerald", "Emerald", light = Color(0xFF1F9D6A), dark = Color(0xFF4CD39A)),
        Accent("teal", "Teal", light = Color(0xFF138D8D), dark = Color(0xFF4FC9C9)),
        Accent("sky", "Sky", light = Color(0xFF2B7FE0), dark = Color(0xFF6FB0FF)),
        Accent("graphite", "Graphite", light = Color(0xFF3C4150), dark = Color(0xFFB9BECB)),
    )

    fun accent(id: String?): Accent = accents.firstOrNull { it.id == id } ?: accents.first()

    /** Font size options (Settings → Font size). Multiplies every text size. */
    val fontScales: List<FontScaleOption> = listOf(
        FontScaleOption("small", 0.88f),
        FontScaleOption("default", 1.00f),
        FontScaleOption("large", 1.12f),
        FontScaleOption("xlarge", 1.26f),
    )

    fun fontScale(id: String?): FontScaleOption = fontScales.firstOrNull { it.id == id } ?: fontScales[1]

    /** Neutral colours of the light theme. */
    val light = Neutrals(
        background = Color(0xFFF7F7FA),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFEFEFF4),
        surfaceContainer = Color(0xFFF1F1F6),
        outline = Color(0xFFD9D9E3),
        text = Color(0xFF15161C),
        textMuted = Color(0xFF6B6E7E),
        bubbleUser = Color(0xFFE9E7FB),
        bubbleAssistant = Color(0xFFFFFFFF),
        error = Color(0xFFD03A3A),
    )

    /** Neutral colours of the dark theme. */
    val dark = Neutrals(
        background = Color(0xFF0F1015),
        surface = Color(0xFF171821),
        surfaceVariant = Color(0xFF20222D),
        surfaceContainer = Color(0xFF1B1C26),
        outline = Color(0xFF2D3040),
        text = Color(0xFFEDEDF3),
        textMuted = Color(0xFF9A9DAF),
        bubbleUser = Color(0xFF2A2C3E),
        bubbleAssistant = Color(0xFF1B1C26),
        error = Color(0xFFFF6B6B),
    )

    // Base sizes (sp) before the font scale is applied.
    const val FONT_BODY = 15f
    const val FONT_BODY_SMALL = 13f
    const val FONT_LABEL = 11f
    const val FONT_TITLE = 20f
    const val FONT_HEADLINE = 26f

    // Shapes (dp)
    const val RADIUS_SMALL = 10
    const val RADIUS_MEDIUM = 16
    const val RADIUS_LARGE = 24

    // Spacing (dp)
    const val SPACE_XS = 4
    const val SPACE_S = 8
    const val SPACE_M = 16
    const val SPACE_L = 24

    /** Height of the bottom navigation bar (dp). */
    const val BOTTOM_BAR_HEIGHT = 68

    /** Vertical distance between the actions of the stage menu on the Chat page (dp). */
    const val STAGE_MENU_SPACING = 14

    /** Size of the round microphone / call button on the Chat page (dp). */
    const val MIC_BUTTON_SIZE = 56

    /** Scrim drawn over the background image so the character stands out (0..1). */
    const val STAGE_SCRIM_ALPHA_TOP = 0.25f
    const val STAGE_SCRIM_ALPHA_BOTTOM = 0.45f
}

data class Accent(val id: String, val name: String, val light: Color, val dark: Color)

data class FontScaleOption(val id: String, val scale: Float)

data class Neutrals(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceContainer: Color,
    val outline: Color,
    val text: Color,
    val textMuted: Color,
    val bubbleUser: Color,
    val bubbleAssistant: Color,
    val error: Color,
)
