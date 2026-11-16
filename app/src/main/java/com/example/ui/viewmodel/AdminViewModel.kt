package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.extractFirestoreMessage
import com.example.data.model.AdminLog
import com.example.data.model.Invitation
import com.example.data.model.NotificationItem
import com.example.data.model.StudioProject
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SystemConfig
import com.example.data.model.UpgradeRequest
import com.example.data.model.UserProfile
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

class AdminViewModel(
  private val repository: StudioRepository,
  private val adminUid: String,
  private val adminEmail: String,
) : ViewModel() {

  val allUsers: StateFlow<List<UserProfile>> = repository.observeAllUsers()
    .catch { if (it is CancellationException) throw it else emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val invitations: StateFlow<List<Invitation>> = repository.observeInvitations()
    .catch { if (it is CancellationException) throw it else emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val allUpgradeRequests: StateFlow<List<UpgradeRequest>> = repository.observeAllUpgradeRequests()
    .catch { if (it is CancellationException) throw it else emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val allProjects: StateFlow<List<StudioProject>> = repository.observeAllProjects()
    .catch { if (it is CancellationException) throw it else emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val adminNotifications: StateFlow<List<NotificationItem>> = repository.observeAdminInboxNotifications()
    .catch { if (it is CancellationException) throw it else emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val adminLogs: StateFlow<List<AdminLog>> = repository.observeAdminLogs()
    .catch { if (it is CancellationException) throw it else emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  fun clearBanner() {
    _statusBanner.value = null
  }

  fun createInvitation(email: String, name: String, planId: String, note: String) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.createInvitation(adminUid, adminEmail, email, name, planId, note)
      _isBusy.value = false
      res.onSuccess { code -> _statusBanner.value = "Invitation created: $code" }
        .onFailure { _statusBanner.value = it.extractFirestoreMessage("Failed to create invitation.") }
    }
  }

  fun revokeInvitation(inviteCode: String) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.revokeInvitation(adminUid, adminEmail, inviteCode)
      _isBusy.value = false
      res.onSuccess { _statusBanner.value = "Revoked $inviteCode" }
        .onFailure { _statusBanner.value = it.extractFirestoreMessage("Failed to revoke invitation.") }
    }
  }

  fun createPreprovisionedAccount(name: String, email: String, planId: String) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.createPreprovisionedUser(adminUid, adminEmail, name, email, planId)
      _isBusy.value = false
      res.onSuccess { code ->
        _statusBanner.value = "Account invite provisioned for $email. Share code $code so they can sign in with Google."
      }.onFailure {
        _statusBanner.value = it.extractFirestoreMessage("Failed to provision account.")
      }
    }
  }

  fun setUserStatus(targetUserId: String, status: String) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.updateUserStatus(adminUid, adminEmail, targetUserId, status)
      _isBusy.value = false
      res.onSuccess { _statusBanner.value = "User status updated to $status." }
        .onFailure { _statusBanner.value = it.extractFirestoreMessage("Failed to update user status.") }
    }
  }

  fun toggleAdminRole(targetUser: UserProfile, makeAdmin: Boolean) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.setAdminRoleByOwner(adminUid, adminEmail, targetUser, makeAdmin)
      _isBusy.value = false
      res.onSuccess {
        _statusBanner.value = "${targetUser.displayName} is now ${if (makeAdmin) "an Admin" else "a regular User"}."
      }.onFailure {
        _statusBanner.value = it.extractFirestoreMessage("Only the Platform Owner can modify Admin roles.")
      }
    }
  }

  fun assignPlanAndLimits(
    targetUserId: String,
    planId: String,
    videoLimit: Long,
    voiceLimit: Long,
    imageLimit: Long,
    scriptLimit: Long,
    chatLimit: Long = -1L,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.assignUserPlanAndLimits(
        adminUid,
        adminEmail,
        targetUserId,
        planId,
        videoLimit,
        voiceLimit,
        imageLimit,
        scriptLimit,
        chatLimit,
      )
      _isBusy.value = false
      res.onSuccess { _statusBanner.value = "Assigned $planId plan and usage limits." }
        .onFailure { _statusBanner.value = it.extractFirestoreMessage("Failed to assign plan.") }
    }
  }

  fun recommendPlan(targetUser: UserProfile, planId: String, note: String) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.recommendPlanToUser(adminUid, adminEmail, targetUser, planId, note)
      _isBusy.value = false
      res.onSuccess { _statusBanner.value = "Plan recommendation sent to ${targetUser.displayName}." }
        .onFailure { _statusBanner.value = it.extractFirestoreMessage("Failed to recommend plan.") }
    }
  }

  fun savePlan(plan: SubscriptionPlan) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.saveSubscriptionPlan(adminUid, adminEmail, plan)
      _isBusy.value = false
      res.onSuccess { _statusBanner.value = "Saved ${plan.name}." }
        .onFailure { _statusBanner.value = it.extractFirestoreMessage("Failed to save plan.") }
    }
  }

  fun reviewUpgradeRequest(request: UpgradeRequest, approve: Boolean, adminNote: String) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.reviewUpgradeRequest(adminUid, adminEmail, request, approve, adminNote)
      _isBusy.value = false
      res.onSuccess {
        _statusBanner.value = if (approve) "Approved upgrade for ${request.userName}." else "Rejected upgrade request."
      }.onFailure {
        _statusBanner.value = it.extractFirestoreMessage("Failed to review upgrade request.")
      }
    }
  }

  fun updateSystemConfig(config: SystemConfig) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.updateSystemConfig(adminUid, adminEmail, config)
      _isBusy.value = false
      res.onSuccess { _statusBanner.value = "System settings updated." }
        .onFailure { _statusBanner.value = it.extractFirestoreMessage("Failed to update system settings.") }
    }
  }

  fun sendBroadcastOrMessage(targetUserId: String, title: String, message: String, category: String) {
    if (title.isBlank() || message.isBlank()) return
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.sendNotification(adminUid, adminEmail, targetUserId, title, message, category)
      _isBusy.value = false
      res.onSuccess { _statusBanner.value = "Notification sent." }
        .onFailure { _statusBanner.value = it.extractFirestoreMessage("Failed to send notification.") }
    }
  }

  companion object {
    fun provideFactory(
      databaseId: String,
      adminUid: String,
      adminEmail: String,
    ): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val db = FirebaseFirestore.getInstance(databaseId)
        AdminViewModel(StudioRepository(db), adminUid, adminEmail)
      }
    }
  }
}
