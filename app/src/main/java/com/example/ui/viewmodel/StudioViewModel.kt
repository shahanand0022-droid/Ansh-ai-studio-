package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.MediaExportHelper
import com.example.core.NarrationLanguage
import com.example.core.VideoAssembler
import com.example.core.VideoRenderSceneInput
import com.example.core.VideoResolutionPreset
import com.example.core.VoiceEngine
import com.example.core.VoiceEngineState
import com.example.data.model.ChatTurn
import com.example.data.model.StudioConstants
import com.example.data.model.StudioProject
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SystemConfig
import com.example.data.model.UserProfile
import com.example.data.model.VideoProductionConfig
import com.example.data.model.VideoScene
import com.example.data.model.VideoStoryboard
import com.example.data.repository.AiRepository
import com.example.data.repository.StudioAspectRatio
import com.example.data.repository.StudioRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface StudioOpState<out T> {
  data object Idle : StudioOpState<Nothing>
  data object Loading : StudioOpState<Nothing>
  data class Streaming(val text: String) : StudioOpState<Nothing>
  data class Success<T>(val data: T) : StudioOpState<T>
  data class Error(val message: String) : StudioOpState<Nothing>
}

data class GeneratedImageResult(
  val prompt: String,
  val styleMode: String,
  val aspectRatio: StudioAspectRatio,
  val bitmap: Bitmap,
  val localFile: File,
)

class StudioViewModel(
  private val repository: StudioRepository,
  private val aiRepository: AiRepository,
  private val voiceEngine: VoiceEngine,
  private val userId: String,
) : ViewModel() {

  val myProjects: StateFlow<List<StudioProject>> = repository.observeMyProjects(userId)
    .catch { err ->
      if (err is CancellationException) throw err
      emit(emptyList())
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = emptyList(),
    )

  val voiceState: StateFlow<VoiceEngineState> = voiceEngine.state

  private val _videoPlanState = MutableStateFlow<StudioOpState<VideoStoryboard>>(StudioOpState.Idle)
  val videoPlanState: StateFlow<StudioOpState<VideoStoryboard>> = _videoPlanState.asStateFlow()

  private val _sceneKeyframes = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
  val sceneKeyframes: StateFlow<Map<Int, Bitmap>> = _sceneKeyframes.asStateFlow()

  private val _generatingKeyframeScene = MutableStateFlow<Int?>(null)
  val generatingKeyframeScene: StateFlow<Int?> = _generatingKeyframeScene.asStateFlow()

  private val _videoProductionConfig = MutableStateFlow(VideoProductionConfig())
  val videoProductionConfig: StateFlow<VideoProductionConfig> = _videoProductionConfig.asStateFlow()

  private val _videoSeedBitmap = MutableStateFlow<Bitmap?>(null)
  val videoSeedBitmap: StateFlow<Bitmap?> = _videoSeedBitmap.asStateFlow()

  private val _videoBuildProgress = MutableStateFlow<Float?>(null)
  val videoBuildProgress: StateFlow<Float?> = _videoBuildProgress.asStateFlow()

  private val _videoBuildStatus = MutableStateFlow<String?>(null)
  val videoBuildStatus: StateFlow<String?> = _videoBuildStatus.asStateFlow()

  private val _assembledVideoFile = MutableStateFlow<File?>(null)
  val assembledVideoFile: StateFlow<File?> = _assembledVideoFile.asStateFlow()

  private val _imageGenState = MutableStateFlow<StudioOpState<GeneratedImageResult>>(StudioOpState.Idle)
  val imageGenState: StateFlow<StudioOpState<GeneratedImageResult>> = _imageGenState.asStateFlow()

  private val _scriptState = MutableStateFlow<StudioOpState<String>>(StudioOpState.Idle)
  val scriptState: StateFlow<StudioOpState<String>> = _scriptState.asStateFlow()

  private val _chatTurns = MutableStateFlow<List<ChatTurn>>(emptyList())
  val chatTurns: StateFlow<List<ChatTurn>> = _chatTurns.asStateFlow()

  private val _voiceScriptDraft = MutableStateFlow("")
  val voiceScriptDraft: StateFlow<String> = _voiceScriptDraft.asStateFlow()

  private val _isWritingVoiceScript = MutableStateFlow(false)
  val isWritingVoiceScript: StateFlow<Boolean> = _isWritingVoiceScript.asStateFlow()

  private val _toastMessage = MutableStateFlow<String?>(null)
  val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

  fun updateVoiceScriptDraft(text: String) {
    _voiceScriptDraft.value = text
  }

  fun clearToast() {
    _toastMessage.value = null
  }

  private fun checkQuotaAndTool(
    profile: UserProfile,
    config: SystemConfig,
    plans: List<SubscriptionPlan>,
    studioType: String,
  ): String? {
    if (!profile.isActive) return "Account is not active."
    val enabled = when (studioType) {
      StudioConstants.TYPE_VIDEO_PLAN -> config.videoStudioEnabled
      StudioConstants.TYPE_VOICE -> config.voiceStudioEnabled
      StudioConstants.TYPE_IMAGE -> config.imageStudioEnabled
      else -> config.scriptStudioEnabled
    }
    if (!enabled) return "This AI studio is currently disabled by the Administrator."

    if (profile.isAdminOrOwner) return null
    val plan = plans.firstOrNull { it.id == profile.planId }
    val (used, customLimit, planLimit, label) = when (studioType) {
      StudioConstants.TYPE_VIDEO_PLAN -> Quad(profile.videoUsed, profile.customVideoLimit, plan?.videoLimit ?: 5L, "Video Plans")
      StudioConstants.TYPE_VOICE -> Quad(profile.voiceUsed, profile.customVoiceLimit, plan?.voiceLimit ?: 10L, "Voice Studio")
      StudioConstants.TYPE_IMAGE -> Quad(profile.imageUsed, profile.customImageLimit, plan?.imageLimit ?: 5L, "Image Studio")
      else -> Quad(profile.scriptUsed, profile.customScriptLimit, plan?.scriptLimit ?: 20L, "Script Assistant")
    }
    val effectiveLimit = if (customLimit >= 0L) customLimit else planLimit
    if (effectiveLimit >= 0L && used >= effectiveLimit) {
      return "Usage limit reached for $label ($used / $effectiveLimit). Request a plan upgrade in Account & Plans."
    }
    return null
  }

  private data class Quad(val used: Long, val custom: Long, val plan: Long, val label: String)

  fun updateVideoProductionConfig(config: VideoProductionConfig) {
    _videoProductionConfig.value = config
  }

  fun setVideoSeedBitmap(bitmap: Bitmap?) {
    _videoSeedBitmap.value = bitmap
  }

  fun generateEnhancedVideoPlan(
    profile: UserProfile,
    config: SystemConfig,
    plans: List<SubscriptionPlan>,
    prodConfig: VideoProductionConfig,
    topicOrScript: String,
    languageName: String,
  ) {
    if (_videoPlanState.value is StudioOpState.Loading) return
    val gateErr = checkQuotaAndTool(profile, config, plans, StudioConstants.TYPE_VIDEO_PLAN)
    if (gateErr != null) {
      _videoPlanState.value = StudioOpState.Error(gateErr)
      return
    }
    if (topicOrScript.isBlank()) {
      _videoPlanState.value = StudioOpState.Error("Please enter a topic, script, or prompt to generate scenes.")
      return
    }
    val cappedDuration = prodConfig.durationSeconds.coerceIn(15, config.maxVideoDurationSeconds.coerceIn(15, 300))
    val effectiveConfig = prodConfig.copy(durationSeconds = cappedDuration)
    _videoProductionConfig.value = effectiveConfig
    _assembledVideoFile.value = null
    val seed = if (effectiveConfig.sourceMode == "Image-to-Video") _videoSeedBitmap.value else null

    viewModelScope.launch {
      _videoPlanState.value = StudioOpState.Loading
      _sceneKeyframes.value = emptyMap()
      try {
        val board = aiRepository.generateEnhancedVideoPlan(
          sourceMode = effectiveConfig.sourceMode,
          topicOrScript = topicOrScript,
          targetDurationSeconds = cappedDuration,
          aspectLabel = effectiveConfig.aspectRatioLabel,
          resolutionLabel = effectiveConfig.resolutionLabel,
          visualStyle = effectiveConfig.visualStyle,
          languageName = languageName,
          seedBitmap = seed,
        )
        _videoPlanState.value = StudioOpState.Success(board)
        val formattedSummary = buildString {
          appendLine("${board.title} (${board.format}, ${board.totalDurationSeconds}s)")
          appendLine(board.logline)
          appendLine()
          board.scenes.forEach { s ->
            appendLine("Scene ${s.sceneNumber} [${s.durationSeconds}s]: ${s.cameraDirection}")
            appendLine("Visual: ${s.visualPrompt}")
            appendLine("Narration: ${s.narrationLine}")
            appendLine("Audio Cue: ${s.audioCue}")
            appendLine()
          }
        }
        repository.saveProjectAndIncrementUsage(
          user = profile,
          title = board.title,
          type = StudioConstants.TYPE_VIDEO_PLAN,
          prompt = "[${effectiveConfig.sourceMode} • ${cappedDuration}s] $topicOrScript",
          outputText = formattedSummary,
          language = languageName,
          modelUsed = "gemini-3.8-flash",
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val msg = e.localizedMessage ?: "Failed to generate video storyboard."
        repository.logServiceError(profile.userId, profile.email, "VideoStudio", msg)
        _videoPlanState.value = StudioOpState.Error(msg)
      }
    }
  }

  fun updateVideoScene(sceneNumber: Int, updated: VideoScene) {
    val current = (_videoPlanState.value as? StudioOpState.Success)?.data ?: return
    val newScenes = current.scenes.map { if (it.sceneNumber == sceneNumber) updated else it }
    _videoPlanState.value = StudioOpState.Success(
      current.copy(
        scenes = newScenes,
        totalDurationSeconds = newScenes.sumOf { it.durationSeconds },
      ),
    )
  }

  fun generateVideoPlan(
    profile: UserProfile,
    config: SystemConfig,
    plans: List<SubscriptionPlan>,
    formatMode: String,
    topic: String,
    sceneCount: Int,
    languageName: String,
  ) {
    val prod = VideoProductionConfig(
      sourceMode = "Text-to-Video",
      visualStyle = formatMode,
      durationSeconds = (sceneCount * 8).coerceIn(15, config.maxVideoDurationSeconds.coerceAtLeast(15)),
    )
    generateEnhancedVideoPlan(profile, config, plans, prod, topic, languageName)
  }

  fun generateSceneKeyframe(
    profile: UserProfile,
    config: SystemConfig,
    plans: List<SubscriptionPlan>,
    sceneNumber: Int,
    visualPrompt: String,
    context: Context,
  ) {
    if (_generatingKeyframeScene.value != null) return
    val gateErr = checkQuotaAndTool(profile, config, plans, StudioConstants.TYPE_IMAGE)
    if (gateErr != null) {
      _toastMessage.value = gateErr
      return
    }
    val prod = _videoProductionConfig.value
    val aspect = when {
      prod.aspectRatioLabel.contains("9:16") -> StudioAspectRatio.PORTRAIT
      prod.aspectRatioLabel.contains("1:1") -> StudioAspectRatio.SQUARE
      prod.aspectRatioLabel.contains("4:3") -> StudioAspectRatio.DOCUMENTARY
      else -> StudioAspectRatio.THUMBNAIL
    }
    val seed = if (prod.sourceMode == "Image-to-Video") _videoSeedBitmap.value else null

    viewModelScope.launch {
      _generatingKeyframeScene.value = sceneNumber
      try {
        val bitmap = aiRepository.generateImage(visualPrompt, aspect, prod.visualStyle, referenceBitmap = seed)
        val file = MediaExportHelper.saveBitmapLocally(context, bitmap, "scene_$sceneNumber")
        _sceneKeyframes.value = _sceneKeyframes.value + (sceneNumber to bitmap)
        repository.saveProjectAndIncrementUsage(
          user = profile,
          title = "Scene $sceneNumber Keyframe",
          type = StudioConstants.TYPE_IMAGE,
          prompt = visualPrompt,
          outputText = "Storyboard keyframe generated (${prod.visualStyle})",
          language = "en",
          modelUsed = "gemini-nano-banana-2.1",
          localMediaUri = file.absolutePath,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val msg = e.localizedMessage ?: "Keyframe generation failed."
        repository.logServiceError(profile.userId, profile.email, "VideoKeyframe", msg)
        _toastMessage.value = msg
      } finally {
        _generatingKeyframeScene.value = null
      }
    }
  }

  fun assembleVideoMp4Project(
    profile: UserProfile,
    config: SystemConfig,
    plans: List<SubscriptionPlan>,
    context: Context,
  ) {
    if (_videoBuildProgress.value != null) return
    val board = (_videoPlanState.value as? StudioOpState.Success)?.data ?: run {
      _toastMessage.value = "Generate a multi-scene storyboard first."
      return
    }
    val gateErr = checkQuotaAndTool(profile, config, plans, StudioConstants.TYPE_VIDEO_PLAN)
    if (gateErr != null) {
      _toastMessage.value = gateErr
      return
    }

    val prod = _videoProductionConfig.value
    val aspect = when {
      prod.aspectRatioLabel.contains("9:16") -> StudioAspectRatio.PORTRAIT
      prod.aspectRatioLabel.contains("1:1") -> StudioAspectRatio.SQUARE
      prod.aspectRatioLabel.contains("4:3") -> StudioAspectRatio.DOCUMENTARY
      else -> StudioAspectRatio.THUMBNAIL
    }
    val resPreset = if (prod.resolutionLabel.contains("1080")) {
      VideoResolutionPreset.FHD_1080
    } else {
      VideoResolutionPreset.HD_720
    }
    val seed = _videoSeedBitmap.value

    viewModelScope.launch {
      _videoBuildProgress.value = 0.02f
      _videoBuildStatus.value = "Preparing scene visuals (${board.scenes.size} scenes)..."
      try {
        val updatedFrames = _sceneKeyframes.value.toMutableMap()
        val totalScenes = board.scenes.size.coerceAtLeast(1)

        // Generate missing keyframes up to scene count using Nano Banana 2.1 when image studio is enabled
        if (config.imageStudioEnabled) {
          for ((idx, scene) in board.scenes.withIndex()) {
            if (!updatedFrames.containsKey(scene.sceneNumber)) {
              _videoBuildStatus.value = "Rendering Scene ${scene.sceneNumber}/${totalScenes} artwork..."
              _videoBuildProgress.value = 0.05f + (0.45f * (idx.toFloat() / totalScenes))
              val bmp = runCatching {
                aiRepository.generateImage(scene.visualPrompt, aspect, prod.visualStyle, seed)
              }.getOrNull()
              if (bmp != null) {
                updatedFrames[scene.sceneNumber] = bmp
                _sceneKeyframes.value = updatedFrames.toMap()
              }
            }
          }
        }

        val inputs = board.scenes.map { scene ->
          VideoRenderSceneInput(
            sceneNumber = scene.sceneNumber,
            durationSeconds = scene.durationSeconds,
            captionText = scene.narrationLine,
            cameraDirection = scene.cameraDirection,
            bitmap = updatedFrames[scene.sceneNumber] ?: seed,
          )
        }

        val result = VideoAssembler.assembleMp4(
          context = context,
          title = board.title,
          scenes = inputs,
          aspectLabel = prod.aspectRatioLabel,
          resolution = resPreset,
          onProgress = { pct, msg ->
            _videoBuildProgress.value = 0.50f + (pct * 0.48f)
            _videoBuildStatus.value = msg
          },
        )
        val mp4File = result.getOrThrow()
        _assembledVideoFile.value = mp4File
        _videoBuildStatus.value = "MP4 ready (${board.totalDurationSeconds}s)"

        repository.saveProjectAndIncrementUsage(
          user = profile,
          title = "${board.title} (${board.totalDurationSeconds}s MP4)",
          type = StudioConstants.TYPE_VIDEO_MP4,
          prompt = "[${prod.sourceMode} • ${prod.aspectRatioLabel} • ${prod.resolutionLabel}] ${board.logline}",
          outputText = "Assembled ${board.scenes.size}-scene MP4 (${board.totalDurationSeconds}s) at ${mp4File.name}",
          language = "en",
          modelUsed = "Gemini + MediaCodec Composer",
          localMediaUri = mp4File.absolutePath,
        )
        _toastMessage.value = "MP4 video composed & saved to Projects!"
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val msg = e.localizedMessage ?: "Failed to assemble MP4 video."
        repository.logServiceError(profile.userId, profile.email, "VideoAssembler", msg)
        _toastMessage.value = msg
      } finally {
        _videoBuildProgress.value = null
      }
    }
  }

  fun generateImage(
    profile: UserProfile,
    config: SystemConfig,
    plans: List<SubscriptionPlan>,
    prompt: String,
    aspectRatio: StudioAspectRatio,
    styleMode: String,
    context: Context,
  ) {
    if (_imageGenState.value is StudioOpState.Loading) return
    val gateErr = checkQuotaAndTool(profile, config, plans, StudioConstants.TYPE_IMAGE)
    if (gateErr != null) {
      _imageGenState.value = StudioOpState.Error(gateErr)
      return
    }
    if (prompt.isBlank()) {
      _imageGenState.value = StudioOpState.Error("Please describe the image you want to create.")
      return
    }
    viewModelScope.launch {
      _imageGenState.value = StudioOpState.Loading
      try {
        val bitmap = aiRepository.generateImage(prompt, aspectRatio, styleMode)
        val file = MediaExportHelper.saveBitmapLocally(context, bitmap, "prince_img")
        val result = GeneratedImageResult(prompt, styleMode, aspectRatio, bitmap, file)
        _imageGenState.value = StudioOpState.Success(result)
        repository.saveProjectAndIncrementUsage(
          user = profile,
          title = prompt.take(48),
          type = StudioConstants.TYPE_IMAGE,
          prompt = "[$styleMode / ${aspectRatio.label}] $prompt",
          outputText = "Generated 1K image (${aspectRatio.label})",
          language = "en",
          modelUsed = "gemini-nano-banana-2.1",
          localMediaUri = file.absolutePath,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val msg = e.localizedMessage ?: "Image generation failed."
        repository.logServiceError(profile.userId, profile.email, "ImageStudio", msg)
        _imageGenState.value = StudioOpState.Error(msg)
      }
    }
  }

  fun loadProjectBitmap(path: String): Bitmap? {
    if (path.isBlank()) return null
    val file = File(path)
    return if (file.exists()) BitmapFactory.decodeFile(path) else null
  }

  fun setVoiceLanguage(language: NarrationLanguage) {
    voiceEngine.setLanguage(language)
  }

  fun selectVoice(voiceName: String) {
    voiceEngine.selectVoice(voiceName)
  }

  fun previewVoice(
    profile: UserProfile,
    config: SystemConfig,
    plans: List<SubscriptionPlan>,
    text: String,
    speechRate: Float,
    pitch: Float,
  ) {
    val gateErr = checkQuotaAndTool(profile, config, plans, StudioConstants.TYPE_VOICE)
    if (gateErr != null) {
      _toastMessage.value = gateErr
      return
    }
    if (text.isBlank()) {
      _toastMessage.value = "Enter narration text to preview."
      return
    }
    voiceEngine.preview(text, speechRate, pitch)
  }

  fun synthesizeAndSaveVoice(
    profile: UserProfile,
    config: SystemConfig,
    plans: List<SubscriptionPlan>,
    text: String,
    language: NarrationLanguage,
    speechRate: Float,
    pitch: Float,
  ) {
    val gateErr = checkQuotaAndTool(profile, config, plans, StudioConstants.TYPE_VOICE)
    if (gateErr != null) {
      _toastMessage.value = gateErr
      return
    }
    if (text.isBlank()) {
      _toastMessage.value = "Enter narration text to synthesize."
      return
    }
    val outFile = voiceEngine.synthesizeToWav(text, speechRate, pitch)
    if (outFile == null) {
      _toastMessage.value = "Speech synthesis failed on this device."
      return
    }
    viewModelScope.launch {
      repository.saveProjectAndIncrementUsage(
        user = profile,
        title = "${language.displayName}: ${text.take(36)}",
        type = StudioConstants.TYPE_VOICE,
        prompt = text,
        outputText = "Synthesized ${language.displayName} narration WAV (${speechRate}x speed)",
        language = language.code,
        modelUsed = "Android TTS (${voiceEngine.state.value.selectedVoiceName ?: "Default"})",
        localMediaUri = outFile.absolutePath,
      )
    }
  }

  fun playVoiceFile(file: File) = voiceEngine.playFile(file)
  fun stopVoice() = voiceEngine.stopAll()

  fun generateNarrationWithAi(
    profile: UserProfile,
    config: SystemConfig,
    plans: List<SubscriptionPlan>,
    topic: String,
    language: NarrationLanguage,
    style: String,
  ) {
    if (_isWritingVoiceScript.value) return
    val gateErr = checkQuotaAndTool(profile, config, plans, StudioConstants.TYPE_SCRIPT)
    if (gateErr != null) {
      _toastMessage.value = gateErr
      return
    }
    if (topic.isBlank()) {
      _toastMessage.value = "Enter a topic for AI narration writing."
      return
    }
    viewModelScope.launch {
      _isWritingVoiceScript.value = true
      try {
        val narration = aiRepository.generateNarrationScript(topic, language.displayName, style)
        _voiceScriptDraft.value = narration
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val msg = e.localizedMessage ?: "Failed to generate narration script."
        repository.logServiceError(profile.userId, profile.email, "VoiceNarrationAI", msg)
        _toastMessage.value = msg
      } finally {
        _isWritingVoiceScript.value = false
      }
    }
  }

  fun sendScriptMessage(
    profile: UserProfile,
    config: SystemConfig,
    plans: List<SubscriptionPlan>,
    mode: String,
    prompt: String,
  ) {
    if (_scriptState.value is StudioOpState.Loading || _scriptState.value is StudioOpState.Streaming) return
    val gateErr = checkQuotaAndTool(profile, config, plans, StudioConstants.TYPE_SCRIPT)
    if (gateErr != null) {
      _scriptState.value = StudioOpState.Error(gateErr)
      return
    }
    if (prompt.isBlank()) return

    val priorHistory = _chatTurns.value
    _chatTurns.value = priorHistory + ChatTurn("user", prompt)

    viewModelScope.launch {
      _scriptState.value = StudioOpState.Loading
      var finalAnswer = ""
      try {
        aiRepository.streamScriptAssistant(mode, prompt, priorHistory).collect { partial ->
          finalAnswer = partial
          _scriptState.value = StudioOpState.Streaming(partial)
        }
        _chatTurns.value = _chatTurns.value + ChatTurn("model", finalAnswer)
        _scriptState.value = StudioOpState.Success(finalAnswer)
        repository.saveProjectAndIncrementUsage(
          user = profile,
          title = "[$mode] ${prompt.take(40)}",
          type = StudioConstants.TYPE_SCRIPT,
          prompt = prompt,
          outputText = finalAnswer,
          language = profile.preferredLanguage,
          modelUsed = "gemini-3.8-flash",
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val msg = e.localizedMessage ?: "Script assistant failed."
        repository.logServiceError(profile.userId, profile.email, "ScriptAssistant", msg)
        _scriptState.value = StudioOpState.Error(msg)
      }
    }
  }

  fun clearScriptChat() {
    _chatTurns.value = emptyList()
    _scriptState.value = StudioOpState.Idle
  }

  fun deleteProject(projectId: String) {
    viewModelScope.launch {
      repository.deleteProject(projectId)
        .onSuccess { _toastMessage.value = "Project deleted." }
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Delete failed." }
    }
  }

  override fun onCleared() {
    super.onCleared()
    voiceEngine.release()
  }

  companion object {
    fun provideFactory(
      databaseId: String,
      appContext: Context,
      userId: String,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val db = FirebaseFirestore.getInstance(databaseId)
        StudioViewModel(
          repository = StudioRepository(db),
          aiRepository = AiRepository(),
          voiceEngine = VoiceEngine(appContext),
          userId = userId,
        )
      }
    }
  }
}
