package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.ActionExecutionOutcome
import com.example.core.AssistantActionIntent
import com.example.core.AssistantActionType
import com.example.core.AssistantPermissionSnapshot
import com.example.core.NarrationLanguage
import com.example.core.PhoneAssistantEngine
import com.example.core.SpeechCaptureState
import com.example.core.VoiceEngine
import com.example.core.VoiceEngineState
import com.example.data.model.AssistantNote
import com.example.data.model.AssistantTurn
import com.example.data.model.SystemConfig
import com.example.data.model.UserProfile
import com.example.data.repository.AiRepository
import com.example.data.repository.StudioRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface AssistantPhase {
  data object Idle : AssistantPhase
  data object Listening : AssistantPhase
  data object Thinking : AssistantPhase
  data class AwaitingConfirmation(val intent: AssistantActionIntent) : AssistantPhase
  data object Speaking : AssistantPhase
}

class AssistantViewModel(
  private val repository: StudioRepository,
  private val aiRepository: AiRepository,
  private val phoneEngine: PhoneAssistantEngine,
  private val voiceEngine: VoiceEngine,
  private val userId: String,
) : ViewModel() {

  val notes: StateFlow<List<AssistantNote>> = repository.observeAssistantNotes(userId)
    .catch { if (it is CancellationException) throw it else emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val speechState: StateFlow<SpeechCaptureState> = phoneEngine.speechState
  val voiceState: StateFlow<VoiceEngineState> = voiceEngine.state

  private val _phase = MutableStateFlow<AssistantPhase>(AssistantPhase.Idle)
  val phase: StateFlow<AssistantPhase> = _phase.asStateFlow()

  private val _turns = MutableStateFlow<List<AssistantTurn>>(emptyList())
  val turns: StateFlow<List<AssistantTurn>> = _turns.asStateFlow()

  private val _permissions = MutableStateFlow(phoneEngine.inspectPermissions())
  val permissions: StateFlow<AssistantPermissionSnapshot> = _permissions.asStateFlow()

  private val _selectedLanguage = MutableStateFlow(NarrationLanguage.ENGLISH)
  val selectedLanguage: StateFlow<NarrationLanguage> = _selectedLanguage.asStateFlow()

  private val _voiceModeEnabled = MutableStateFlow(true)
  val voiceModeEnabled: StateFlow<Boolean> = _voiceModeEnabled.asStateFlow()

  private val _attachedImage = MutableStateFlow<Bitmap?>(null)
  val attachedImage: StateFlow<Bitmap?> = _attachedImage.asStateFlow()

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  private val _navigateRouteEvent = MutableStateFlow<String?>(null)
  val navigateRouteEvent: StateFlow<String?> = _navigateRouteEvent.asStateFlow()

  private var activeJob: Job? = null

  fun refreshPermissions() {
    _permissions.value = phoneEngine.inspectPermissions()
  }

  fun setLanguage(language: NarrationLanguage) {
    _selectedLanguage.value = language
    voiceEngine.setLanguage(language)
  }

  fun setVoiceMode(voiceMode: Boolean) {
    _voiceModeEnabled.value = voiceMode
    if (!voiceMode) {
      voiceEngine.stopAll()
    }
  }

  fun attachImage(bitmap: Bitmap?) {
    _attachedImage.value = bitmap
  }

  fun clearBanner() {
    _statusBanner.value = null
  }

  fun consumeNavigationEvent() {
    _navigateRouteEvent.value = null
  }

  fun startListening(profile: UserProfile, config: SystemConfig) {
    if (!config.voiceAssistantEnabled) {
      _statusBanner.value = "Voice Assistant is currently disabled by the Administrator."
      return
    }
    voiceEngine.stopAll()
    _statusBanner.value = null
    _phase.value = AssistantPhase.Listening
    phoneEngine.startForegroundListening(
      language = _selectedLanguage.value,
      onFinalTranscript = { spoken ->
        _phase.value = AssistantPhase.Idle
        submitCommand(profile, config, spoken)
      },
      onRecognitionError = { errMsg ->
        _phase.value = AssistantPhase.Idle
        _statusBanner.value = errMsg
      },
    )
  }

  fun submitCommand(
    profile: UserProfile,
    config: SystemConfig,
    rawInput: String,
  ) {
    val command = rawInput.trim()
    if (command.isBlank()) return
    if (!config.voiceAssistantEnabled) {
      _statusBanner.value = "Voice Assistant is currently disabled by the Administrator."
      return
    }

    val lower = command.lowercase()
    if (lower in setOf("stop", "cancel", "stop talking", "ruko", "band karo", "रोकनुहोस्")) {
      stopAllSpeechAndListening()
      _statusBanner.value = "Stopped assistant output."
      return
    }

    activeJob?.cancel()
    val image = _attachedImage.value
    _attachedImage.value = null
    _turns.value = _turns.value + AssistantTurn(
      id = UUID.randomUUID().toString(),
      role = "user",
      text = command,
      hasImage = image != null,
    )

    activeJob = viewModelScope.launch {
      _phase.value = AssistantPhase.Thinking
      try {
        val modelToUse = config.activeChatModel.ifBlank { "gemini-3.8-flash" }
        val appNames = phoneEngine.queryLaunchableApps().map { it.first }
        var intent = aiRepository.processAssistantTurn(
          modelName = modelToUse,
          languageDisplay = _selectedLanguage.value.displayName,
          userInput = command,
          attachedImage = image,
          installedAppNames = appNames,
        )

        val isDeviceAction = intent.type != AssistantActionType.CONVERSATION &&
          intent.type != AssistantActionType.NAVIGATE_STUDIO &&
          intent.type != AssistantActionType.UNSUPPORTED
        if (isDeviceAction && !config.phoneActionsEnabled) {
          intent = intent.copy(
            type = AssistantActionType.UNSUPPORTED,
            explanation = "Phone device actions are currently paused by the Administrator.",
            spokenReply = "Phone device actions are disabled by the Administrator right now.",
          )
        }

        if (intent.isSensitiveAction) {
          _turns.value = _turns.value + AssistantTurn(
            id = UUID.randomUUID().toString(),
            role = "assistant",
            text = intent.spokenReply.ifBlank { "Please confirm this action before I open your phone or messaging app." },
            actionType = intent.type.name,
            actionSummary = "Confirmation needed: ${intent.target}",
          )
          _phase.value = AssistantPhase.AwaitingConfirmation(intent)
          speakIfEnabled(intent.spokenReply.ifBlank { "Please review and confirm to proceed." })
        } else {
          val outcome = phoneEngine.executeAction(intent, confirmed = false)
          handleExecutionOutcome(intent, outcome)
        }
      } catch (e: CancellationException) {
        _phase.value = AssistantPhase.Idle
        throw e
      } catch (e: Exception) {
        val err = e.localizedMessage ?: "Assistant failed to understand request."
        _phase.value = AssistantPhase.Idle
        _statusBanner.value = err
        repository.logServiceError(profile.userId, profile.email, "PhoneAssistant", err)
      }
    }
  }

  fun confirmPendingAction() {
    val awaiting = _phase.value as? AssistantPhase.AwaitingConfirmation ?: return
    val intent = awaiting.intent
    val outcome = phoneEngine.executeAction(intent, confirmed = true)
    viewModelScope.launch {
      handleExecutionOutcome(intent, outcome)
    }
  }

  fun cancelPendingAction() {
    if (_phase.value is AssistantPhase.AwaitingConfirmation) {
      _phase.value = AssistantPhase.Idle
      _turns.value = _turns.value + AssistantTurn(
        id = UUID.randomUUID().toString(),
        role = "assistant",
        text = "Cancelled. No call or message was prepared.",
        actionType = "CANCELLED",
        actionSummary = "Cancelled by user",
      )
      speakIfEnabled("Cancelled.")
    }
  }

  private suspend fun handleExecutionOutcome(
    intent: AssistantActionIntent,
    outcome: ActionExecutionOutcome,
  ) {
    if (outcome.saveNoteTitle != null) {
      repository.createAssistantNote(
        uid = userId,
        title = outcome.saveNoteTitle,
        content = outcome.saveNoteContent.orEmpty(),
        kind = outcome.saveNoteKind ?: "note",
        dueLabel = outcome.saveDueLabel.orEmpty(),
      )
    }
    if (!outcome.navigateToRoute.isNullOrBlank()) {
      _navigateRouteEvent.value = outcome.navigateToRoute
    }

    val replyText = when {
      intent.type == AssistantActionType.CONVERSATION -> intent.spokenReply.ifBlank { outcome.summary }
      outcome.executed && intent.spokenReply.isNotBlank() -> intent.spokenReply
      else -> outcome.summary
    }

    _turns.value = _turns.value + AssistantTurn(
      id = UUID.randomUUID().toString(),
      role = "assistant",
      text = replyText,
      actionType = intent.type.name,
      actionSummary = outcome.summary,
    )
    refreshPermissions()

    if (_voiceModeEnabled.value && replyText.isNotBlank()) {
      _phase.value = AssistantPhase.Speaking
      voiceEngine.preview(replyText, speechRate = 1.0f, pitch = 1.0f)
    } else {
      _phase.value = AssistantPhase.Idle
    }
  }

  private fun speakIfEnabled(text: String) {
    if (_voiceModeEnabled.value && text.isNotBlank()) {
      voiceEngine.preview(text, speechRate = 1.0f, pitch = 1.0f)
    }
  }

  fun stopAllSpeechAndListening() {
    activeJob?.cancel()
    phoneEngine.stopListening()
    voiceEngine.stopAll()
    _phase.value = AssistantPhase.Idle
  }

  fun clearConversation() {
    stopAllSpeechAndListening()
    _turns.value = emptyList()
  }

  fun createManualNote(title: String, content: String, kind: String, dueLabel: String) {
    if (title.isBlank()) return
    viewModelScope.launch {
      repository.createAssistantNote(userId, title, content, kind, dueLabel)
        .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to save note." }
    }
  }

  fun toggleNoteCompleted(noteId: String, completed: Boolean) {
    viewModelScope.launch {
      repository.toggleAssistantNoteCompleted(noteId, completed)
    }
  }

  fun deleteNote(noteId: String) {
    viewModelScope.launch {
      repository.deleteAssistantNote(noteId)
    }
  }

  fun openCalendarForReminder(note: AssistantNote) {
    val res = phoneEngine.openCalendarReminderIntent(note.title, note.content)
    _statusBanner.value = res.summary
  }

  fun openNotificationAccessSettings() {
    val res = phoneEngine.openNotificationAccessSettings()
    _statusBanner.value = res.summary
  }

  fun openWriteSettingsPermission() {
    val res = phoneEngine.openWriteSettingsPermissionPage()
    _statusBanner.value = res.summary
  }

  fun openAppPermissionSettings() {
    val res = phoneEngine.openAppPermissionSettings()
    _statusBanner.value = res.summary
  }

  override fun onCleared() {
    super.onCleared()
    phoneEngine.release()
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
        AssistantViewModel(
          repository = StudioRepository(db),
          aiRepository = AiRepository(),
          phoneEngine = PhoneAssistantEngine(appContext),
          voiceEngine = VoiceEngine(appContext),
          userId = userId,
        )
      }
    }
  }
}
