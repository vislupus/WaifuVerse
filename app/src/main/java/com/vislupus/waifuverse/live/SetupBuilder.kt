package com.vislupus.waifuverse.live

import com.vislupus.waifuverse.config.CharactersConfig
import com.vislupus.waifuverse.config.ModelConfig
import com.vislupus.waifuverse.data.Message
import com.vislupus.waifuverse.data.Role
import com.vislupus.waifuverse.data.Waifu
import org.json.JSONArray
import org.json.JSONObject

/** Builds the "setup" message and the full system prompt for a live session. */
object SetupBuilder {

    /**
     * System prompt = persona prompt + memory of remembered conversations +
     * transcript of the conversation being resumed (if any).
     */
    fun systemPrompt(waifu: Waifu, memory: List<Message>, resume: List<Message>): String {
        val sb = StringBuilder(waifu.systemPrompt())
        if (memory.isNotEmpty()) {
            sb.append("\n\n").append(CharactersConfig.MEMORY_PROMPT_HEADER).append('\n')
            sb.append(transcript(waifu, memory, com.vislupus.waifuverse.config.AppConfig.MEMORY_MAX_CHARS))
        }
        if (resume.isNotEmpty()) {
            sb.append("\n\n").append(CharactersConfig.RESUME_PROMPT_HEADER).append('\n')
            sb.append(transcript(waifu, resume, com.vislupus.waifuverse.config.AppConfig.MEMORY_MAX_CHARS))
        }
        return sb.toString()
    }

    /** Formats messages as "User: ..." / "<Name>: ..." lines, keeping the most recent ones under [maxChars]. */
    private fun transcript(waifu: Waifu, messages: List<Message>, maxChars: Int): String {
        val lines = messages
            .filter { it.role == Role.USER || it.role == Role.ASSISTANT }
            .map { m ->
                val who = if (m.role == Role.USER) CharactersConfig.TRANSCRIPT_USER_LABEL else waifu.name
                "$who: ${m.text.trim()}"
            }
        // Keep the newest lines that fit in the budget.
        val kept = ArrayList<String>()
        var total = 0
        for (line in lines.asReversed()) {
            total += line.length + 1
            if (total > maxChars) break
            kept.add(line)
        }
        return kept.asReversed().joinToString("\n")
    }

    fun setupMessage(
        model: String,
        waifu: Waifu,
        systemPrompt: String,
        resumptionHandle: String?,
    ): JSONObject {
        val speech = JSONObject().put(
            "voiceConfig",
            JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", waifu.voice)),
        )
        if (ModelConfig.SPEECH_LANGUAGE_CODE.isNotBlank()) speech.put("languageCode", ModelConfig.SPEECH_LANGUAGE_CODE)

        val generation = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put("temperature", waifu.temperature.toDouble())
            .put("topP", waifu.topP.toDouble())
            .put("topK", waifu.topK)
            .put("maxOutputTokens", waifu.maxOutputTokens)
            .put("speechConfig", speech)

        val setup = JSONObject()
            .put("model", "models/$model")
            .put("generationConfig", generation)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))

        if (ModelConfig.INPUT_TRANSCRIPTION) setup.put("inputAudioTranscription", JSONObject())
        if (ModelConfig.OUTPUT_TRANSCRIPTION) setup.put("outputAudioTranscription", JSONObject())
        if (ModelConfig.CONTEXT_WINDOW_COMPRESSION) {
            setup.put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
        }
        val resumption = JSONObject()
        if (!resumptionHandle.isNullOrEmpty()) resumption.put("handle", resumptionHandle)
        setup.put("sessionResumption", resumption)
        if (ModelConfig.ENABLE_AFFECTIVE_DIALOG) setup.put("enableAffectiveDialog", true)
        if (ModelConfig.ENABLE_PROACTIVE_AUDIO) setup.put("proactivity", JSONObject().put("proactiveAudio", true))

        return JSONObject().put("setup", setup)
    }
}
