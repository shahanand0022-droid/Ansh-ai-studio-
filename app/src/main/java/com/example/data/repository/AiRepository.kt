package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.core.AssistantActionIntent
import com.example.core.AssistantActionType
import com.example.data.model.ChatTurn
import com.example.data.model.VideoScene
import com.example.data.model.VideoStoryboard
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val AI_TAG = "PrinceAiRepo"

enum class StudioAspectRatio(val label: String, val ratio: AspectRatio) {
  SQUARE("1:1 Square", AspectRatio.SQUARE_1x1),
  THUMBNAIL("16:9 Thumbnail", AspectRatio.LANDSCAPE_16x9),
  DOCUMENTARY("4:3 Documentary", AspectRatio.LANDSCAPE_4x3),
  PORTRAIT("9:16 Vertical", AspectRatio.PORTRAIT_9x16),
}

class AiRepository {
  private val textModel by lazy {
    Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
  }

  private val storyboardSchema = Schema.obj(
    properties = mapOf(
      "title" to Schema.string(description = "Production title"),
      "format" to Schema.string(description = "Documentary, Short Video, or Scene Plan"),
      "logline" to Schema.string(description = "One-sentence summary"),
      "totalDurationSeconds" to Schema.integer(description = "Estimated total runtime in seconds"),
      "scenes" to Schema.array(
        items = Schema.obj(
          properties = mapOf(
            "sceneNumber" to Schema.integer(description = "1-based scene index"),
            "durationSeconds" to Schema.integer(description = "Duration of scene in seconds"),
            "visualPrompt" to Schema.string(description = "Detailed visual generation prompt"),
            "cameraDirection" to Schema.string(description = "Camera framing and movement"),
            "narrationLine" to Schema.string(description = "Voiceover line for this scene"),
            "audioCue" to Schema.string(description = "Sound effects or ambient audio cue"),
          ),
        ),
        description = "Ordered list of production scenes",
      ),
    ),
  )

  private val storyboardModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = storyboardSchema
      },
    )
  }

  private val assistantIntentSchema = Schema.obj(
    properties = mapOf(
      "actionType" to Schema.enumeration(
        values = AssistantActionType.entries.map { it.name },
        description = "Exact Android action type or CONVERSATION",
      ),
      "target" to Schema.string(description = "Target app name, search query, setting keyword, note/reminder title, phone number/contact, studio route, or flashlight on/off"),
      "secondaryText" to Schema.string(description = "Optional SMS draft body, note/reminder detail, alarm/timer label, or due date label"),
      "numericValue" to Schema.integer(description = "Numeric percent (0-100) for volume/brightness or seconds for timer, else -1"),
      "hour" to Schema.integer(description = "Hour in 24h (0-23) for SET_ALARM, else -1"),
      "minute" to Schema.integer(description = "Minute (0-59) for SET_ALARM, else -1"),
      "requiresConfirmation" to Schema.boolean(description = "True for PREPARE_CALL, PREPARE_MESSAGE, or sensitive actions"),
      "spokenReply" to Schema.string(description = "Natural concise spoken reply in the user's language (English, Hindi, or Nepali)"),
      "explanation" to Schema.string(description = "Action preview or clear honesty explanation if Android blocks an unsupported capability"),
    ),
  )

  fun streamGeneralChat(
    modelName: String,
    languageDisplay: String,
    userPrompt: String,
    history: List<ChatTurn>,
    attachedImage: Bitmap? = null,
  ): Flow<String> = flow {
    val allowedModel = when (modelName) {
      "gemini-3.5-flash-lite", "gemini-4-argon", "gemini-3.8-flash" -> modelName
      else -> "gemini-3.8-flash"
    }
    val model = Firebase.ai.generativeModel(modelName = allowedModel)
    val systemDirectives = """
      You are PRINCE AI STUDIO's helpful, intelligent AI assistant.
      Respond naturally in $languageDisplay (unless the user explicitly writes in another language, in which case match them).
      Use clear structured markdown when appropriate, fenced code blocks with language tags (e.g. ```kotlin), and clean readable math formatting for formulas and step-by-step solutions.
      Be accurate, thorough, and honest.
    """.trimIndent()

    val chatHistory = history.takeLast(14).map { turn ->
      content(if (turn.role == "user") "user" else "model") { text(turn.text) }
    }
    val chat = model.startChat(history = chatHistory)
    val messageContent = content("user") {
      if (history.isEmpty()) {
        text("$systemDirectives\n\nUser: $userPrompt")
      } else {
        text(userPrompt)
      }
      if (attachedImage != null) {
        image(attachedImage)
      }
    }

    val accumulated = StringBuilder()
    withTimeout(60_000L) {
      chat.sendMessageStream(messageContent).collect { chunk ->
        val piece = chunk.text.orEmpty()
        if (piece.isNotEmpty()) {
          accumulated.append(piece)
          emit(accumulated.toString())
        }
      }
    }
    if (accumulated.isBlank()) {
      throw IllegalStateException("Gemini returned an empty response.")
    }
  }

  suspend fun processAssistantTurn(
    modelName: String,
    languageDisplay: String,
    userInput: String,
    attachedImage: Bitmap?,
    installedAppNames: List<String>,
  ): AssistantActionIntent {
    val allowedModel = when (modelName) {
      "gemini-3.5-flash-lite", "gemini-4-argon", "gemini-3.8-flash" -> modelName
      else -> "gemini-3.8-flash"
    }
    val assistantModel = Firebase.ai.generativeModel(
      modelName = allowedModel,
      generationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = assistantIntentSchema
      },
    )
    val appsContext = if (installedAppNames.isNotEmpty()) {
      installedAppNames.take(35).joinToString(", ")
    } else {
      "Unknown"
    }
    val prompt = """
      You are PRINCE AI PHONE ASSISTANT, an honest voice-controlled Android assistant.
      Respond in $languageDisplay (match Hindi/Nepali/English naturally).
      Decide if the user wants a real Android action or a conversational answer/tutoring/coding/image analysis:
      Supported actions:
      - OPEN_APP: target = app name matching one of [$appsContext]
      - WEB_SEARCH: target = web search query
      - OPEN_SETTINGS: target = wifi | bluetooth | sound | display | battery | notifications | location | storage | apps | general
      - CREATE_NOTE: target = short title, secondaryText = note body
      - CREATE_REMINDER: target = reminder title, secondaryText = due/time label, explanation = details
      - SET_ALARM: hour (0..23), minute (0..59), secondaryText = alarm label
      - SET_TIMER: numericValue = seconds (>0), secondaryText = timer label
      - ADJUST_VOLUME: target = up|down|mute|set, numericValue = 0..100 or -1
      - ADJUST_BRIGHTNESS: numericValue = 5..100
      - TOGGLE_FLASHLIGHT: target = on|off|toggle
      - READ_NOTIFICATIONS: read recent active notifications
      - PREPARE_CALL: target = phone number or contact name, requiresConfirmation = true
      - PREPARE_MESSAGE: target = recipient, secondaryText = SMS draft message, requiresConfirmation = true
      - NAVIGATE_STUDIO: target = chat | video | voice | image | script | projects | permissions
      - UNSUPPORTED: if user asks to secretly record, bypass lock screen, auto-click another app, auto-send SMS/call without tapping, purchase items, or toggle airplane/mobile-data directly on modern Android. Explain clearly why Android security restricts it.
      - CONVERSATION: for questions, math/study help, coding, writing, or describing the attached image. Keep spokenReply clear and natural (2-5 sentences unless detailed explanation requested).

      User command: "$userInput"
    """.trimIndent()

    try {
      val response = withTimeout(60_000L) {
        assistantModel.generateContent(
          content("user") {
            text(prompt)
            if (attachedImage != null) {
              image(attachedImage)
            }
          },
        )
      }
      val jsonText = response.text ?: throw IllegalStateException("Empty assistant response returned.")
      val json = JSONObject(jsonText)
      val typeStr = json.optString("actionType", AssistantActionType.CONVERSATION.name)
      val type = runCatching { AssistantActionType.valueOf(typeStr) }
        .getOrDefault(AssistantActionType.CONVERSATION)
      val sensitive = type == AssistantActionType.PREPARE_CALL || type == AssistantActionType.PREPARE_MESSAGE

      return AssistantActionIntent(
        type = type,
        target = json.optString("target", ""),
        secondaryText = json.optString("secondaryText", ""),
        numericValue = json.optInt("numericValue", -1),
        hour = json.optInt("hour", -1),
        minute = json.optInt("minute", -1),
        requiresConfirmation = sensitive || json.optBoolean("requiresConfirmation", false),
        spokenReply = json.optString("spokenReply", ""),
        explanation = json.optString("explanation", ""),
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(AI_TAG, "Assistant turn timed out", e)
      throw IllegalStateException("Assistant timed out after 60 seconds. Please try again.")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(AI_TAG, "Assistant turn failed", e)
      throw e
    }
  }

  fun streamScriptAssistant(
    mode: String,
    userPrompt: String,
    history: List<ChatTurn>,
  ): Flow<String> = flow {
    val systemPrefix = when (mode) {
      "Video Script" -> "You are an expert video scriptwriter for Prince AI Studio. Write engaging, production-ready scripts with hook, body, and call-to-action."
      "Documentary Story" -> "You are a documentary filmmaker and storyteller. Craft compelling narrative arcs with emotional pacing, historical/factual structure, and visual cues."
      "Image Prompt" -> "You are an expert visual prompt engineer. Turn ideas into detailed, structured, high-craft image generation prompts with lighting, lens, composition, and mood."
      "Video Prompt" -> "You are a cinematography prompt engineer. Write detailed camera-movement and motion prompts suitable for AI video pipelines."
      "Narration Polish" -> "You are a voiceover director. Refine narration scripts for natural spoken rhythm, breath pauses, and clarity."
      "Scene Organizer" -> "You are a creative producer. Organize the user's concept into numbered scenes with timing, visuals, and transitions."
      else -> "You are Prince AI Studio's creative AI assistant helping creators write scripts, documentaries, prompts, and voiceovers."
    }

    val chatHistory = history.takeLast(8).map { turn ->
      content(if (turn.role == "user") "user" else "model") { text(turn.text) }
    }
    val chat = textModel.startChat(history = chatHistory)
    val composedPrompt = "$systemPrefix\n\nCreator request:\n$userPrompt"

    val accumulated = StringBuilder()
    withTimeout(60_000L) {
      chat.sendMessageStream(composedPrompt).collect { chunk ->
        val piece = chunk.text.orEmpty()
        if (piece.isNotEmpty()) {
          accumulated.append(piece)
          emit(accumulated.toString())
        }
      }
    }
    if (accumulated.isBlank()) {
      throw IllegalStateException("Gemini returned an empty response.")
    }
  }

  suspend fun generateVideoPlan(
    formatMode: String,
    topic: String,
    sceneCount: Int,
    languageName: String,
  ): VideoStoryboard = generateEnhancedVideoPlan(
    sourceMode = "Text-to-Video",
    topicOrScript = topic,
    targetDurationSeconds = (sceneCount * 8).coerceIn(15, 120),
    aspectLabel = "16:9 Widescreen",
    resolutionLabel = "720p HD",
    visualStyle = formatMode,
    languageName = languageName,
    seedBitmap = null,
    explicitSceneCount = sceneCount,
  )

  suspend fun generateEnhancedVideoPlan(
    sourceMode: String,
    topicOrScript: String,
    targetDurationSeconds: Int,
    aspectLabel: String,
    resolutionLabel: String,
    visualStyle: String,
    languageName: String,
    seedBitmap: Bitmap? = null,
    explicitSceneCount: Int? = null,
  ): VideoStoryboard {
    val computedSceneCount = explicitSceneCount ?: when {
      targetDurationSeconds <= 15 -> 3
      targetDurationSeconds <= 30 -> 5
      targetDurationSeconds <= 60 -> 8
      targetDurationSeconds <= 90 -> 10
      else -> 12
    }
    val perSceneSec = (targetDurationSeconds.toFloat() / computedSceneCount).toInt().coerceAtLeast(4)
    val prompt = """
      Create a structured multi-scene video production storyboard in $languageName for PRINCE AI STUDIO.
      Creation Mode: $sourceMode
      Visual Style: $visualStyle
      Target Aspect Ratio: $aspectLabel • Resolution: $resolutionLabel
      Target Total Duration: $targetDurationSeconds seconds across exactly $computedSceneCount scenes (~$perSceneSec seconds per scene).
      Make the sum of scene durations approximately $targetDurationSeconds seconds.
      For each scene provide a rich keyframe image visualPrompt tailored to "$visualStyle", cameraDirection, natural spoken narrationLine, and audioCue.
      ${if (seedBitmap != null) "An inspiration/reference image is attached—incorporate its subject, colors, setting, and mood faithfully across the scenes." else ""}
      Topic or Script Input:
      $topicOrScript
    """.trimIndent()

    try {
      val response = withTimeout(60_000L) {
        storyboardModel.generateContent(
          content("user") {
            text(prompt)
            if (seedBitmap != null) {
              image(seedBitmap)
            }
          },
        )
      }
      val jsonText = response.text ?: throw IllegalStateException("Empty storyboard returned.")
      val root = JSONObject(jsonText)
      val scenesJson = root.getJSONArray("scenes")
      val scenes = (0 until scenesJson.length()).map { idx ->
        val obj = scenesJson.getJSONObject(idx)
        VideoScene(
          sceneNumber = obj.optInt("sceneNumber", idx + 1),
          durationSeconds = obj.optInt("durationSeconds", perSceneSec).coerceIn(3, 20),
          visualPrompt = obj.optString("visualPrompt", ""),
          cameraDirection = obj.optString("cameraDirection", ""),
          narrationLine = obj.optString("narrationLine", ""),
          audioCue = obj.optString("audioCue", ""),
        )
      }
      val actualTotal = scenes.sumOf { it.durationSeconds }
      return VideoStoryboard(
        title = root.optString("title", topicOrScript.take(60)),
        format = "$sourceMode • $visualStyle",
        logline = root.optString("logline", ""),
        totalDurationSeconds = actualTotal,
        scenes = scenes,
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(AI_TAG, "Video plan generation timed out", e)
      throw IllegalStateException("Video plan generation timed out after 60 seconds. Please try again.")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(AI_TAG, "Video plan generation failed", e)
      throw e
    }
  }

  suspend fun generateNarrationScript(
    topic: String,
    languageDisplay: String,
    style: String,
  ): String {
    val prompt = """
      Write a clean spoken voiceover narration in $languageDisplay ($style style) ready to be read aloud by a Text-to-Speech narrator.
      Do not include stage directions in brackets or speaker labels—output only the natural spoken narration text.
      Topic: $topic
    """.trimIndent()

    try {
      val response = withTimeout(60_000L) {
        textModel.generateContent(prompt)
      }
      return response.text?.trim()?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("No narration text was returned.")
    } catch (e: TimeoutCancellationException) {
      throw IllegalStateException("Narration script generation timed out after 60 seconds.")
    } catch (e: CancellationException) {
      throw e
    }
  }

  suspend fun generateImage(
    prompt: String,
    aspectRatio: StudioAspectRatio,
    styleMode: String,
    referenceBitmap: Bitmap? = null,
  ): Bitmap {
    val config = generationConfig {
      responseModalities = listOf(ResponseModality.IMAGE)
      imageConfig = ImageConfig.builder()
        .setAspectRatio(aspectRatio.ratio)
        .setImageSize(ImageSize.SIZE_1K)
        .build()
    }
    val model = Firebase.ai.generativeModel(
      modelName = "gemini-nano-banana-2.1",
      generationConfig = config,
    )
    val fullPrompt = "[$styleMode] $prompt"
    try {
      val response = withTimeout(60_000L) {
        model.generateContent(
          content("user") {
            text(fullPrompt)
            if (referenceBitmap != null) {
              image(referenceBitmap)
            }
          },
        )
      }
      val imagePart = response.candidates.firstOrNull()
        ?.content?.parts?.filterIsInstance<ImagePart>()
        ?.firstOrNull()
      return imagePart?.image ?: throw IllegalStateException("No image was returned by Gemini Nano Banana 2.1.")
    } catch (e: TimeoutCancellationException) {
      Log.e(AI_TAG, "Image generation timed out", e)
      throw IllegalStateException("Image generation timed out after 60 seconds. Please try again.")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(AI_TAG, "Image generation failed", e)
      throw e
    }
  }
}
