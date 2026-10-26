package com.vislupus.waifuverse.config

/**
 * =====================================================================
 *  MODEL CONFIG — Gemini Live models, generation defaults and audio.
 * =====================================================================
 */
object ModelConfig {

    /** Gemini Live websocket endpoint (v1beta BidiGenerateContent). */
    const val LIVE_ENDPOINT =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

    /**
     * Models are tried IN THIS ORDER. If one fails to connect or rejects the
     * setup, the next one is used automatically. The first entry can be
     * overridden from Settings → Model.
     */
    val MODEL_CHAIN: List<String> = listOf(
        "gemini-3.8-live",
        "gemini-3.1-flash-live-preview",
        "gemini-2.5-flash-native-audio-preview-12-2025",
        "gemini-2.5-flash-native-audio-preview-09-2025",
        "gemini-live-2.5-flash-preview",
        "gemini-2.0-flash-live-001",
    )

    /** Prebuilt Gemini voices selectable per waifu (Persona page). */
    val VOICES: List<String> = listOf(
        "Aoede", "Kore", "Leda", "Zephyr", "Puck", "Charon", "Fenrir", "Orus",
        "Callirrhoe", "Autonoe", "Enceladus", "Iapetus", "Umbriel", "Algieba",
        "Despina", "Erinome", "Algenib", "Rasalgethi", "Laomedeia", "Achernar",
        "Alnilam", "Schedar", "Gacrux", "Pulcherrima", "Achird", "Zubenelgenubi",
        "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat",
    )

    // Default generation parameters (each waifu can override them on the Persona page).
    const val DEFAULT_TEMPERATURE = 1.0f
    const val DEFAULT_TOP_P = 0.95f
    const val DEFAULT_TOP_K = 40
    const val DEFAULT_MAX_OUTPUT_TOKENS = 1024

    // Ranges for the sliders on the Persona page.
    const val TEMPERATURE_MIN = 0.0f
    const val TEMPERATURE_MAX = 2.0f
    const val TOP_P_MIN = 0.1f
    const val TOP_P_MAX = 1.0f
    const val TOP_K_MIN = 1
    const val TOP_K_MAX = 100
    const val MAX_TOKENS_MIN = 64
    const val MAX_TOKENS_MAX = 8192

    /** Ask Gemini for transcripts of both what you say and what the waifu says. */
    const val INPUT_TRANSCRIPTION = true
    const val OUTPUT_TRANSCRIPTION = true

    /** Keeps long sessions alive by letting Gemini compress old context. */
    const val CONTEXT_WINDOW_COMPRESSION = true

    /**
     * Native-audio-only features. They are sent only when true; older
     * (half-cascade) models reject them, so leave off unless you know your model
     * supports them.
     */
    const val ENABLE_AFFECTIVE_DIALOG = false
    const val ENABLE_PROACTIVE_AUDIO = false

    /**
     * Optional BCP-47 language code for the voice (e.g. "bg-BG", "en-US").
     * Empty = let the model pick from the conversation (recommended for
     * multilingual chats).
     */
    const val SPEECH_LANGUAGE_CODE = ""

    // ---------------------------------------------------------------
    // Audio
    // ---------------------------------------------------------------

    /** Gemini Live expects 16 kHz PCM16 mono in and produces 24 kHz PCM16 mono out. */
    const val INPUT_SAMPLE_RATE = 16_000
    const val OUTPUT_SAMPLE_RATE = 24_000

    /** Microphone chunk sent per websocket message, in milliseconds. */
    const val INPUT_CHUNK_MS = 40

    /** Playback buffer size in milliseconds (bigger = safer against stutter, more latency). */
    const val OUTPUT_BUFFER_MS = 600

    /** Seconds of silence padded after the mic stops so Gemini finalises the last words. */
    const val MIC_TAIL_SILENCE_MS = 200

    /** Default audio mode (Settings → Audio). See [AudioMode]. */
    val DEFAULT_AUDIO_MODE = AudioMode.VOICE_CALL

    /** Default for "mute microphone while the waifu is speaking" (prevents echo in MEDIA mode). */
    const val DEFAULT_MUTE_MIC_WHILE_SPEAKING = false

    /** Attach the platform noise suppressor / AGC / echo canceller effects when available. */
    const val USE_NOISE_SUPPRESSOR = true
    const val USE_AUTOMATIC_GAIN_CONTROL = true
    const val USE_ACOUSTIC_ECHO_CANCELER = true

    /** Route playback to the loudspeaker (false = earpiece in VOICE_CALL mode). */
    const val USE_SPEAKERPHONE = true
}

/**
 * VOICE_CALL: phone's call audio path with hardware echo cancellation — safest
 * for hands-free talking (the waifu does not hear herself).
 * MEDIA: media audio path, highest fidelity playback, software echo cancelling
 * only; combine with "mute mic while speaking" if she interrupts herself.
 */
enum class AudioMode(val id: String) {
    VOICE_CALL("voice_call"), MEDIA("media");

    companion object {
        fun fromId(id: String?): AudioMode = entries.firstOrNull { it.id == id } ?: ModelConfig.DEFAULT_AUDIO_MODE
    }
}
