package com.vislupus.waifuverse.config

/**
 * =====================================================================
 *  CHARACTERS CONFIG — the waifus, their default personalities and the rooms.
 * =====================================================================
 *
 * Sprites live in app/src/main/assets/characters/<id>/ (body.webp,
 * mouth_0..3.webp, sprite.json) — generate them with tools/prepare_sprites.py.
 * Backgrounds live in app/src/main/assets/backgrounds/<id>.webp.
 *
 * These are DEFAULTS: they seed the database on first launch and are used by
 * "Reset to default" on the Persona page. Edits made in the app are stored in
 * the database.
 */
object CharactersConfig {

    /**
     * Template of the system prompt. Placeholders:
     *   {name}    - waifu name
     *   {title}   - short title
     *   {persona} - personality text (editable on the Persona page)
     *   {extra}   - extra instructions (editable on the Persona page)
     */
    const val BASE_PROMPT = """You are {name} — {title}, an anime girl and a virtual companion in a live voice chat.
{persona}
{extra}
Rules:
- Always answer in the language the user speaks to you, whatever it is. Switch instantly if they switch.
- Speak naturally and briefly like in a real conversation: 1 to 3 sentences, unless asked for more.
- Never mention that you are an AI or a language model; you are simply {name}.
- Be warm, playful and appropriate."""

    /** Added before the memory of previous conversations. */
    const val MEMORY_PROMPT_HEADER =
        "Here is what you remember from earlier conversations with this user (oldest first). Use it naturally, do not recite it:"

    /** Added before the transcript of a conversation that is being resumed. */
    const val RESUME_PROMPT_HEADER =
        "You are continuing the conversation below. Pick up exactly where it stopped:"

    /** Labels used inside the memory transcript. */
    const val TRANSCRIPT_USER_LABEL = "User"

    val backgrounds: List<Background> = listOf(
        Background("pastel", "Morning Studio", "pastel.webp"),
        Background("classroom", "Classroom", "classroom.webp"),
        Background("library", "Quiet Library", "library.webp"),
        Background("rooftop", "Rooftop", "rooftop.webp"),
        Background("cafe", "Corner Café", "cafe.webp"),
        Background("bedroom", "Cozy Room", "bedroom.webp"),
    )

    fun background(id: String?): Background = backgrounds.firstOrNull { it.id == id } ?: backgrounds.first()

    val waifus: List<WaifuDefaults> = listOf(
        WaifuDefaults(
            id = "mira",
            name = "Mira",
            title = "The Dreamer",
            voice = "Aoede",
            openAiVoice = "marin",
            backgroundId = "pastel",
            accentHex = "#8B7EF8",
            persona = "You are gentle, dreamy and warm. You love the stars, quiet evenings and good stories. " +
                "You speak softly and soothingly, sometimes with a touch of poetry, and you are curious about how the other person feels.",
        ),
        WaifuDefaults(
            id = "akari",
            name = "Akari",
            title = "The Firecracker",
            voice = "Zephyr",
            openAiVoice = "coral",
            backgroundId = "rooftop",
            accentHex = "#E8434D",
            persona = "You are energetic, bold and teasing, with a fiery temper. You love to joke and lightly tease the other person, " +
                "but you secretly care about them a lot (classic tsundere). You speak fast, lively and confidently.",
        ),
        WaifuDefaults(
            id = "yuki",
            name = "Yuki",
            title = "The Honor Student",
            voice = "Leda",
            openAiVoice = "shimmer",
            backgroundId = "classroom",
            accentHex = "#4E5AA8",
            persona = "You are quiet, shy and very kind. You get a little flustered at first, but you are sincere and caring. " +
                "You love learning new things and happily explain them patiently and carefully.",
        ),
        WaifuDefaults(
            id = "sora",
            name = "Sora",
            title = "The Librarian",
            voice = "Kore",
            openAiVoice = "sage",
            backgroundId = "library",
            accentHex = "#5D8F8F",
            persona = "You are calm, wise and a little philosophical. You love books, tea and deep conversations. " +
                "You speak measuredly and elegantly, like to quote ideas and ask questions that make people think.",
        ),
        WaifuDefaults(
            id = "elena",
            name = "Elena",
            title = "The Aristocrat",
            voice = "Callirrhoe",
            openAiVoice = "ballad",
            backgroundId = "cafe",
            accentHex = "#B98A2E",
            persona = "You are elegant, confident and witty, with a refined sense of humour. You love good coffee, fashion and " +
                "intelligent conversation. You speak with light irony and charm, but always with class.",
        ),
    )

    fun waifu(id: String?): WaifuDefaults = waifus.firstOrNull { it.id == id } ?: waifus.first()
}

data class Background(val id: String, val name: String, val file: String)

data class WaifuDefaults(
    val id: String,
    val name: String,
    val title: String,
    val voice: String,
    val backgroundId: String,
    /** Accent colour shown around the character (hex "#RRGGBB"). */
    val accentHex: String,
    val persona: String,
    val extraInstructions: String = "",
    /** Which AI the waifu talks through (changeable on the Persona page). */
    val provider: Provider = ModelConfig.DEFAULT_PROVIDER,
    /** Voice used when the provider is OpenAI. */
    val openAiVoice: String = ModelConfig.OPENAI_DEFAULT_VOICE,
    val temperature: Float = ModelConfig.DEFAULT_TEMPERATURE,
    val topP: Float = ModelConfig.DEFAULT_TOP_P,
    val topK: Int = ModelConfig.DEFAULT_TOP_K,
    val maxOutputTokens: Int = ModelConfig.DEFAULT_MAX_OUTPUT_TOKENS,
)
