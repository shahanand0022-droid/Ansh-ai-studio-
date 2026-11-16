package com.example.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.NarrationLanguage
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.ChatTurn
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SystemConfig
import com.example.data.model.UserProfile
import com.example.data.repository.AiRepository
import com.example.data.repository.StudioRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
  private val repository: StudioRepository,
  private val aiRepository: AiRepository,
  private val userId: String,
) : ViewModel() {

  val sessions: StateFlow<List<ChatSession>> = repository.observeChatSessions(userId)
    .catch { if (it is CancellationException) throw it else emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  private val _activeSessionId = MutableStateFlow<String?>(null)
  val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

  @OptIn(ExperimentalCoroutinesApi::class)
  val activeMessages: StateFlow<List<ChatMessage>> = _activeSessionId
    .flatMapLatest { sessionId ->
      if (sessionId.isNullOrBlank()) {
        flowOf(emptyList())
      } else {
        repository.observeChatMessages(sessionId, userId)
          .catch { if (it is CancellationException) throw it else emit(emptyList()) }
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  private val _streamingText = MutableStateFlow("")
  val streamingText: StateFlow<String> = _streamingText.asStateFlow()

  private val _isGenerating = MutableStateFlow(false)
  val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private val _selectedLanguage = MutableStateFlow(NarrationLanguage.ENGLISH)
  val selectedLanguage: StateFlow<NarrationLanguage> = _selectedLanguage.asStateFlow()

  private val _selectedModel = MutableStateFlow("gemini-3.8-flash")
  val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

  private val _attachedImage = MutableStateFlow<Bitmap?>(null)
  val attachedImage: StateFlow<Bitmap?> = _attachedImage.asStateFlow()

  private var generationJob: Job? = null

  fun selectSession(sessionId: String) {
    if (_isGenerating.value) stopGeneration()
    _errorMessage.value = null
    _streamingText.value = ""
    _activeSessionId.value = sessionId
  }

  fun startNewChat() {
    if (_isGenerating.value) stopGeneration()
    _errorMessage.value = null
    _streamingText.value = ""
    _attachedImage.value = null
    _activeSessionId.value = null
  }

  fun setLanguage(language: NarrationLanguage) {
    _selectedLanguage.value = language
  }

  fun setModel(modelName: String) {
    _selectedModel.value = modelName
  }

  fun attachImage(bitmap: Bitmap?) {
    _attachedImage.value = bitmap
  }

  fun clearError() {
    _errorMessage.value = null
  }

  fun deleteSession(sessionId: String) {
    viewModelScope.launch {
      if (_activeSessionId.value == sessionId) {
        _activeSessionId.value = null
      }
      repository.deleteChatSession(sessionId)
        .onFailure { _errorMessage.value = it.localizedMessage ?: "Unable to delete chat session." }
    }
  }

  private fun checkChatGate(
    profile: UserProfile,
    config: SystemConfig,
    plans: List<SubscriptionPlan>,
  ): String? {
    if (!profile.isActive) return "Account is not active."
    if (!config.aiChatEnabled) return "AI Chat Assistant is currently disabled by the Administrator."
    if (profile.isAdminOrOwner) return null

    val plan = plans.firstOrNull { it.id == profile.planId }
    val effectiveLimit = if (profile.customChatLimit >= 0L) {
      profile.customChatLimit
    } else {
      plan?.chatLimit ?: 50L
    }
    if (effectiveLimit >= 0L && profile.chatUsed >= effectiveLimit) {
      return "AI Chat usage limit reached (${profile.chatUsed} / $effectiveLimit). Request a plan upgrade in Account & Plans."
    }
    return null
  }

  fun sendMessage(
    profile: UserProfile,
    config: SystemConfig,
    plans: List<SubscriptionPlan>,
    rawPrompt: String,
  ) {
    val prompt = rawPrompt.trim()
    if (_isGenerating.value || prompt.isBlank()) return

    val gateError = checkChatGate(profile, config, plans)
    if (gateError != null) {
      _errorMessage.value = gateError
      return
    }

    val modelToUse = _selectedModel.value.ifBlank { config.activeChatModel }.ifBlank { "gemini-3.8-flash" }
    val language = _selectedLanguage.value
    val bitmapToSend = _attachedImage.value
    val historyTurns = activeMessages.value.map { ChatTurn(role = it.role, text = it.text) }

    _errorMessage.value = null
    _attachedImage.value = null
    _streamingText.value = ""

    generationJob = viewModelScope.launch {
      _isGenerating.value = true
      var currentSessionId = _activeSessionId.value
      var accumulatedReply = ""
      try {
        val isBrandNew = currentSessionId.isNullOrBlank()
        if (currentSessionId.isNullOrBlank()) {
          val createdId = repository.createChatSession(
            uid = userId,
            title = prompt.take(48),
            language = language.code,
            modelUsed = modelToUse,
          ).getOrThrow()
          currentSessionId = createdId
          _activeSessionId.value = createdId
        }

        repository.appendChatMessage(
          sessionId = currentSessionId,
          uid = userId,
          role = "user",
          text = prompt,
          hasImageAttachment = bitmapToSend != null,
          modelUsed = modelToUse,
          updateSessionTitle = if (isBrandNew) prompt.take(48) else null,
          incrementChatQuota = false,
        ).getOrThrow()

        aiRepository.streamGeneralChat(
          modelName = modelToUse,
          languageDisplay = language.displayName,
          userPrompt = prompt,
          history = historyTurns,
          attachedImage = bitmapToSend,
        ).collect { partial ->
          accumulatedReply = partial
          _streamingText.value = partial
        }

        repository.appendChatMessage(
          sessionId = currentSessionId,
          uid = userId,
          role = "model",
          text = accumulatedReply,
          hasImageAttachment = false,
          modelUsed = modelToUse,
          incrementChatQuota = true,
        ).getOrThrow()
      } catch (e: CancellationException) {
        if (accumulatedReply.isNotBlank() && !currentSessionId.isNullOrBlank()) {
          repository.appendChatMessage(
            sessionId = currentSessionId,
            uid = userId,
            role = "model",
            text = "$accumulatedReply\n\n_(Stopped by user)_",
            hasImageAttachment = false,
            modelUsed = modelToUse,
            incrementChatQuota = true,
          )
        }
        throw e
      } catch (e: Exception) {
        val msg = e.localizedMessage ?: "AI Chat failed to respond."
        _errorMessage.value = msg
        repository.logServiceError(profile.userId, profile.email, "AIChat", msg)
      } finally {
        _streamingText.value = ""
        _isGenerating.value = false
      }
    }
  }

  fun regenerateLastResponse(
    profile: UserProfile,
    config: SystemConfig,
    plans: List<SubscriptionPlan>,
  ) {
    if (_isGenerating.value) return
    val msgs = activeMessages.value
    val lastUserMsg = msgs.lastOrNull { it.role == "user" } ?: return
    val gateError = checkChatGate(profile, config, plans)
    if (gateError != null) {
      _errorMessage.value = gateError
      return
    }
    val sessionId = _activeSessionId.value ?: return
    val modelToUse = _selectedModel.value.ifBlank { config.activeChatModel }.ifBlank { "gemini-3.8-flash" }
    val language = _selectedLanguage.value

    val idxOfLastUser = msgs.indexOfLast { it.id == lastUserMsg.id }
    val priorHistory = if (idxOfLastUser > 0) {
      msgs.subList(0, idxOfLastUser).map { ChatTurn(it.role, it.text) }
    } else {
      emptyList()
    }

    _errorMessage.value = null
    _streamingText.value = ""

    generationJob = viewModelScope.launch {
      _isGenerating.value = true
      var accumulatedReply = ""
      try {
        aiRepository.streamGeneralChat(
          modelName = modelToUse,
          languageDisplay = language.displayName,
          userPrompt = lastUserMsg.text,
          history = priorHistory,
          attachedImage = null,
        ).collect { partial ->
          accumulatedReply = partial
          _streamingText.value = partial
        }

        repository.appendChatMessage(
          sessionId = sessionId,
          uid = userId,
          role = "model",
          text = accumulatedReply,
          hasImageAttachment = false,
          modelUsed = modelToUse,
          incrementChatQuota = true,
        ).getOrThrow()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val msg = e.localizedMessage ?: "Regeneration failed."
        _errorMessage.value = msg
        repository.logServiceError(profile.userId, profile.email, "AIChatRegenerate", msg)
      } finally {
        _streamingText.value = ""
        _isGenerating.value = false
      }
    }
  }

  fun stopGeneration() {
    generationJob?.cancel()
    generationJob = null
    _isGenerating.value = false
  }

  companion object {
    fun provideFactory(
      databaseId: String,
      userId: String,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val db = FirebaseFirestore.getInstance(databaseId)
        ChatViewModel(
          repository = StudioRepository(db),
          aiRepository = AiRepository(),
          userId = userId,
        )
      }
    }
  }
}
