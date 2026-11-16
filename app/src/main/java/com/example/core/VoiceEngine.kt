package com.example.core

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import java.util.UUID

enum class NarrationLanguage(
  val code: String,
  val displayName: String,
  val locale: Locale,
) {
  ENGLISH("en", "English", Locale.US),
  HINDI("hi", "Hindi (हिन्दी)", Locale("hi", "IN")),
  NEPALI("ne", "Nepali (नेपाली)", Locale("ne", "NP")),
}

data class VoiceEngineState(
  val isReady: Boolean = false,
  val isSpeaking: Boolean = false,
  val isSynthesizing: Boolean = false,
  val isPlayingFile: Boolean = false,
  val languageAvailable: Boolean = true,
  val statusMessage: String = "Initializing speech engine...",
  val availableVoices: List<String> = emptyList(),
  val selectedVoiceName: String? = null,
  val lastSynthesizedFile: File? = null,
)

class VoiceEngine(context: Context) : TextToSpeech.OnInitListener {
  private val appContext = context.applicationContext
  private var tts: TextToSpeech? = null
  private var mediaPlayer: MediaPlayer? = null
  private var currentLanguage: NarrationLanguage = NarrationLanguage.ENGLISH

  private val _state = MutableStateFlow(VoiceEngineState())
  val state: StateFlow<VoiceEngineState> = _state.asStateFlow()

  init {
    tts = TextToSpeech(appContext, this)
  }

  override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
      tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
          if (utteranceId?.startsWith("synth_") == true) {
            _state.value = _state.value.copy(
              isSynthesizing = true,
              statusMessage = "Synthesizing WAV audio...",
            )
          } else {
            _state.value = _state.value.copy(
              isSpeaking = true,
              statusMessage = "Playing preview...",
            )
          }
        }

        override fun onDone(utteranceId: String?) {
          if (utteranceId?.startsWith("synth_") == true) {
            _state.value = _state.value.copy(
              isSynthesizing = false,
              statusMessage = "Audio WAV ready for playback & download",
            )
          } else {
            _state.value = _state.value.copy(
              isSpeaking = false,
              statusMessage = "Preview complete",
            )
          }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
          _state.value = _state.value.copy(
            isSpeaking = false,
            isSynthesizing = false,
            statusMessage = "Speech synthesis failed on this device voice",
          )
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
          _state.value = _state.value.copy(
            isSpeaking = false,
            isSynthesizing = false,
            statusMessage = "Speech engine error (code $errorCode)",
          )
        }
      })
      setLanguage(currentLanguage)
    } else {
      _state.value = _state.value.copy(
        isReady = false,
        languageAvailable = false,
        statusMessage = "Text-to-Speech engine unavailable on this device",
      )
    }
  }

  fun setLanguage(language: NarrationLanguage) {
    currentLanguage = language
    val engine = tts ?: return
    val availability = engine.setLanguage(language.locale)
    val isAvailable = availability != TextToSpeech.LANG_MISSING_DATA &&
      availability != TextToSpeech.LANG_NOT_SUPPORTED
    val voicesForLang = runCatching {
      engine.voices
        ?.filter { it.locale.language == language.locale.language }
        ?.sortedBy { it.name }
        ?.map { it.name }
        .orEmpty()
    }.getOrDefault(emptyList())

    _state.value = _state.value.copy(
      isReady = true,
      languageAvailable = isAvailable,
      availableVoices = voicesForLang,
      selectedVoiceName = voicesForLang.firstOrNull(),
      statusMessage = if (isAvailable) {
        "${language.displayName} voice ready (${voicesForLang.size} voice${if (voicesForLang.size == 1) "" else "s"})"
      } else {
        "${language.displayName} voice pack is not installed on this device; Android TTS will fall back or prompt system voice install"
      },
    )
  }

  fun selectVoice(voiceName: String) {
    val engine = tts ?: return
    val match: Voice? = runCatching {
      engine.voices?.firstOrNull { it.name == voiceName }
    }.getOrNull()
    if (match != null) {
      engine.voice = match
      _state.value = _state.value.copy(
        selectedVoiceName = voiceName,
        statusMessage = "Selected voice: $voiceName",
      )
    }
  }

  fun preview(text: String, speechRate: Float, pitch: Float) {
    val engine = tts ?: return
    stopAll()
    engine.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
    engine.setPitch(pitch.coerceIn(0.5f, 2.0f))
    val params = Bundle()
    engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, "preview_${UUID.randomUUID()}")
  }

  fun synthesizeToWav(
    text: String,
    speechRate: Float,
    pitch: Float,
  ): File? {
    val engine = tts ?: return null
    stopAll()
    engine.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
    engine.setPitch(pitch.coerceIn(0.5f, 2.0f))
    val outDir = File(appContext.filesDir, "generated_voice").apply { mkdirs() }
    val outFile = File(outDir, "prince_voice_${currentLanguage.code}_${System.currentTimeMillis()}.wav")
    _state.value = _state.value.copy(
      isSynthesizing = true,
      lastSynthesizedFile = outFile,
      statusMessage = "Synthesizing audio file...",
    )
    val result = engine.synthesizeToFile(text, Bundle(), outFile, "synth_${UUID.randomUUID()}")
    if (result != TextToSpeech.SUCCESS) {
      _state.value = _state.value.copy(
        isSynthesizing = false,
        statusMessage = "Unable to synthesize WAV file on this device",
      )
      return null
    }
    return outFile
  }

  fun playFile(file: File) {
    if (!file.exists() || file.length() == 0L) {
      _state.value = _state.value.copy(statusMessage = "Audio file is not ready yet")
      return
    }
    stopAll()
    runCatching {
      mediaPlayer = MediaPlayer().apply {
        setDataSource(file.absolutePath)
        setOnCompletionListener {
          _state.value = _state.value.copy(isPlayingFile = false, statusMessage = "Playback finished")
        }
        prepare()
        start()
      }
      _state.value = _state.value.copy(isPlayingFile = true, statusMessage = "Playing ${file.name}")
    }.onFailure {
      _state.value = _state.value.copy(
        isPlayingFile = false,
        statusMessage = "Playback error: ${it.localizedMessage}",
      )
    }
  }

  fun stopAll() {
    runCatching { tts?.stop() }
    runCatching {
      mediaPlayer?.let {
        if (it.isPlaying) it.stop()
        it.release()
      }
    }
    mediaPlayer = null
    _state.value = _state.value.copy(isSpeaking = false, isPlayingFile = false)
  }

  fun release() {
    stopAll()
    runCatching { tts?.shutdown() }
    tts = null
  }
}
