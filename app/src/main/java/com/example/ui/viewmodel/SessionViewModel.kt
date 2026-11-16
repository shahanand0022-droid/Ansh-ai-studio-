package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.extractFirestoreMessage
import com.example.data.model.NotificationItem
import com.example.data.model.StudioConstants
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SystemConfig
import com.example.data.model.UpgradeRequest
import com.example.data.model.UserProfile
import com.example.data.repository.StudioRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SessionUiState {
  data object Loading : SessionUiState
  data class NeedsInvite(
    val uid: String,
    val email: String,
    val displayName: String,
    val isBootstrappingOwner: Boolean = false,
  ) : SessionUiState
  data class Suspended(val profile: UserProfile) : SessionUiState
  data class MaintenanceBlocked(val profile: UserProfile, val message: String) : SessionUiState
  data class Ready(
    val profile: UserProfile,
    val systemConfig: SystemConfig,
    val plans: List<SubscriptionPlan>,
    val notifications: List<NotificationItem>,
    val myUpgradeRequests: List<UpgradeRequest>,
  ) : SessionUiState
  data class Error(val message: String) : SessionUiState
}

sealed interface AdminAuthGateState {
  data object Idle : AdminAuthGateState
  data object Verifying : AdminAuthGateState
  data object Verified : AdminAuthGateState
  data class Denied(val email: String, val reason: String) : AdminAuthGateState
}

class SessionViewModel(
  private val repository: StudioRepository,
  private val uid: String,
  private val email: String,
  private val displayName: String,
  private val photoUrl: String,
  private val emailVerified: Boolean,
) : ViewModel() {

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _feedback = MutableStateFlow<String?>(null)
  val feedback: StateFlow<String?> = _feedback.asStateFlow()

  private val _errorBanner = MutableStateFlow<String?>(null)
  val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

  private val _adminGateState = MutableStateFlow<AdminAuthGateState>(AdminAuthGateState.Idle)
  val adminGateState: StateFlow<AdminAuthGateState> = _adminGateState.asStateFlow()

  private var ownerBootstrapAttempted = false

  val uiState: StateFlow<SessionUiState> = combine(
    repository.observeUserProfile(uid),
    repository.observeSystemConfig(),
    repository.observeSubscriptionPlans(),
    repository.observeUserNotifications(uid),
    repository.observeMyUpgradeRequests(uid),
  ) { profile, config, plans, notifications, requests ->
    if (profile == null) {
      val isOwnerAccount = emailVerified && email.equals(StudioConstants.OWNER_EMAIL, ignoreCase = true)
      if (isOwnerAccount) {
        if (!ownerBootstrapAttempted) {
          ownerBootstrapAttempted = true
          launchOwnerBootstrap()
        }
        SessionUiState.NeedsInvite(uid, email, displayName, isBootstrappingOwner = true)
      } else {
        SessionUiState.NeedsInvite(uid, email, displayName, isBootstrappingOwner = false)
      }
    } else if (!profile.isActive) {
      SessionUiState.Suspended(profile)
    } else if (config.maintenanceMode && !profile.isAdminOrOwner) {
      SessionUiState.MaintenanceBlocked(profile, config.maintenanceMessage)
    } else {
      SessionUiState.Ready(
        profile = profile,
        systemConfig = config,
        plans = plans,
        notifications = notifications,
        myUpgradeRequests = requests,
      )
    }
  }
    .catch { err ->
      if (err is CancellationException) throw err
      Log.e(TAG, "Error observing session state", err)
      emit(SessionUiState.Error(err.extractFirestoreMessage("Failed to load studio session.")))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = SessionUiState.Loading,
    )

  private fun launchOwnerBootstrap() {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.bootstrapOwnerAndDefaults(uid, email, displayName, photoUrl)
      _isBusy.value = false
      res.onFailure {
        _errorBanner.value = it.extractFirestoreMessage("Owner bootstrap failed.")
      }
    }
  }

  fun retryOwnerBootstrap() {
    ownerBootstrapAttempted = false
    launchOwnerBootstrap()
  }

  fun verifyAndEnterAdminConsole() {
    if (_adminGateState.value is AdminAuthGateState.Verifying) return
    if (!emailVerified) {
      _adminGateState.value = AdminAuthGateState.Denied(
        email = email,
        reason = "Your Google account email must be verified before opening the Owner & Admin Console.",
      )
      return
    }
    viewModelScope.launch {
      _adminGateState.value = AdminAuthGateState.Verifying
      val res = repository.verifyAdminAccess(uid, email, emailVerified)
      res.onSuccess { granted ->
        if (granted) {
          _adminGateState.value = AdminAuthGateState.Verified
        } else {
          _adminGateState.value = AdminAuthGateState.Denied(
            email = email,
            reason = "No active Owner or delegated Admin grant found in Firebase for $email.",
          )
        }
      }.onFailure { err ->
        _adminGateState.value = AdminAuthGateState.Denied(
          email = email,
          reason = err.extractFirestoreMessage("Unable to verify administrator privileges."),
        )
      }
    }
  }

  fun resetAdminGate() {
    _adminGateState.value = AdminAuthGateState.Idle
  }

  fun redeemInvite(inviteCode: String) {
    if (_isBusy.value) return
    viewModelScope.launch {
      _isBusy.value = true
      _errorBanner.value = null
      _feedback.value = null
      val res = repository.redeemInvitationAtomically(uid, email, displayName, photoUrl, inviteCode)
      _isBusy.value = false
      res.onSuccess {
        _feedback.value = "Invitation verified! Welcome to PRINCE AI STUDIO."
      }.onFailure {
        _errorBanner.value = it.extractFirestoreMessage("Unable to redeem invitation code.")
      }
    }
  }

  fun updateProfile(newName: String, newBio: String, preferredLanguage: String) {
    if (_isBusy.value) return
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.updateOwnProfile(uid, newName, newBio, preferredLanguage)
      _isBusy.value = false
      res.onSuccess { _feedback.value = "Profile updated." }
        .onFailure { _errorBanner.value = it.extractFirestoreMessage("Failed to update profile.") }
    }
  }

  fun submitUpgradeRequest(profile: UserProfile, requestedPlanId: String, note: String) {
    if (_isBusy.value) return
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.submitUpgradeRequest(profile, requestedPlanId, note)
      _isBusy.value = false
      res.onSuccess {
        _feedback.value = "Upgrade request submitted for Admin review. No automatic payment was charged."
      }.onFailure {
        _errorBanner.value = it.extractFirestoreMessage("Failed to submit upgrade request.")
      }
    }
  }

  fun markNotificationRead(notificationId: String) {
    viewModelScope.launch {
      repository.markNotificationRead(notificationId)
    }
  }

  fun sendSupportMessage(profile: UserProfile, subject: String, message: String) {
    if (_isBusy.value || subject.isBlank() || message.isBlank()) return
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.sendSupportMessage(profile, subject, message)
      _isBusy.value = false
      res.onSuccess { _feedback.value = "Support message sent to Administrator." }
        .onFailure { _errorBanner.value = it.extractFirestoreMessage("Failed to send support message.") }
    }
  }

  fun clearMessages() {
    _feedback.value = null
    _errorBanner.value = null
  }

  companion object {
    private const val TAG = "SessionVM"

    fun provideFactory(
      databaseId: String,
      uid: String,
      email: String,
      displayName: String,
      photoUrl: String,
      emailVerified: Boolean,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance(databaseId)
        SessionViewModel(
          repository = StudioRepository(db),
          uid = uid,
          email = email,
          displayName = displayName,
          photoUrl = photoUrl,
          emailVerified = emailVerified,
        )
      }
    }
  }
}
