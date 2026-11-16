package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.AdminLog
import com.example.data.model.AssistantNote
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.Invitation
import com.example.data.model.NotificationItem
import com.example.data.model.StudioConstants
import com.example.data.model.StudioProject
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SystemConfig
import com.example.data.model.UpgradeRequest
import com.example.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class StudioRepository(private val db: FirebaseFirestore) {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id),
    ),
  )

  private fun <T> Flow<T>.handleSnapshotErrors(path: String): Flow<T> = catch { error ->
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val toLog = firestoreError ?: (error as? Exception)
    if (toLog != null && FirebaseAuth.getInstance().currentUser != null) {
      handleFirestoreError(toLog, OperationType.LIST, path)
    }
    throw error
  }

  fun observeUserProfile(uid: String): Flow<UserProfile?> =
    db.collection("users").document(uid)
      .snapshots()
      .map { snap ->
        if (snap.exists()) {
          snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else {
          null
        }
      }
      .handleSnapshotErrors("users/$uid")

  fun observeSystemConfig(): Flow<SystemConfig> =
    db.collection("system_config").document("global")
      .snapshots()
      .map { snap ->
        if (snap.exists()) {
          snap.toObject(SystemConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: SystemConfig()
        } else {
          SystemConfig()
        }
      }
      .handleSnapshotErrors("system_config/global")

  fun observeSubscriptionPlans(): Flow<List<SubscriptionPlan>> =
    db.collection("subscription_plans")
      .snapshots()
      .map { snap ->
        snap.toObjects(SubscriptionPlan::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.priceUsd }
      }
      .handleSnapshotErrors("subscription_plans")

  fun observeMyProjects(uid: String): Flow<List<StudioProject>> =
    db.collection("projects")
      .whereEqualTo("userId", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(StudioProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .handleSnapshotErrors("projects")

  fun observeAllProjects(): Flow<List<StudioProject>> =
    db.collection("projects")
      .snapshots()
      .map { snap ->
        snap.toObjects(StudioProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .handleSnapshotErrors("projects")

  fun observeMyUpgradeRequests(uid: String): Flow<List<UpgradeRequest>> =
    db.collection("upgrade_requests")
      .whereEqualTo("userId", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(UpgradeRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .handleSnapshotErrors("upgrade_requests")

  fun observeAllUpgradeRequests(): Flow<List<UpgradeRequest>> =
    db.collection("upgrade_requests")
      .snapshots()
      .map { snap ->
        snap.toObjects(UpgradeRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .handleSnapshotErrors("upgrade_requests")

  fun observeUserNotifications(uid: String): Flow<List<NotificationItem>> =
    db.collection("notifications")
      .whereIn("targetUserId", listOf(uid, "all"))
      .snapshots()
      .map { snap ->
        snap.toObjects(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .handleSnapshotErrors("notifications")

  fun observeAdminInboxNotifications(): Flow<List<NotificationItem>> =
    db.collection("notifications")
      .snapshots()
      .map { snap ->
        snap.toObjects(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .handleSnapshotErrors("notifications")

  fun observeAllUsers(): Flow<List<UserProfile>> =
    db.collection("users")
      .snapshots()
      .map { snap ->
        snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .handleSnapshotErrors("users")

  fun observeInvitations(): Flow<List<Invitation>> =
    db.collection("invitations")
      .snapshots()
      .map { snap ->
        snap.toObjects(Invitation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .handleSnapshotErrors("invitations")

  fun observeAdminLogs(): Flow<List<AdminLog>> =
    db.collection("admin_logs")
      .orderBy("createdAt", Query.Direction.DESCENDING)
      .limit(100)
      .snapshots()
      .map { snap ->
        snap.toObjects(AdminLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .handleSnapshotErrors("admin_logs")

  fun observeChatSessions(uid: String): Flow<List<ChatSession>> =
    db.collection("chat_sessions")
      .whereEqualTo("userId", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(ChatSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
      }
      .handleSnapshotErrors("chat_sessions")

  fun observeChatMessages(sessionId: String, uid: String): Flow<List<ChatMessage>> =
    db.collection("chat_sessions")
      .document(sessionId)
      .collection("messages")
      .whereEqualTo("userId", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.createdAt?.toDate()?.time ?: 0L }
      }
      .handleSnapshotErrors("chat_sessions/$sessionId/messages")

  fun observeAssistantNotes(uid: String): Flow<List<AssistantNote>> =
    db.collection("assistant_notes")
      .whereEqualTo("userId", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(AssistantNote::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .handleSnapshotErrors("assistant_notes")

  suspend fun bootstrapOwnerAndDefaults(
    uid: String,
    email: String,
    displayName: String,
    photoUrl: String,
  ): Result<Unit> {
    val userRef = db.collection("users").document(uid)
    return try {
      val existingUser = userRef.get().await()
      if (!existingUser.exists()) {
        val batch = db.batch()
        batch.set(
          userRef,
          mapOf(
            "userId" to uid,
            "email" to email,
            "displayName" to displayName.ifBlank { "Prince Owner" },
            "photoUrl" to photoUrl,
            "bio" to "Platform Owner & Lead Creator",
            "preferredLanguage" to "en",
            "role" to StudioConstants.ROLE_OWNER,
            "status" to StudioConstants.STATUS_ACTIVE,
            "planId" to StudioConstants.PLAN_CUSTOM,
            "videoUsed" to 0L,
            "voiceUsed" to 0L,
            "imageUsed" to 0L,
            "scriptUsed" to 0L,
            "chatUsed" to 0L,
            "customVideoLimit" to -1L,
            "customVoiceLimit" to -1L,
            "customImageLimit" to -1L,
            "customScriptLimit" to -1L,
            "customChatLimit" to -1L,
            "recommendedPlanId" to "",
            "recommendedNote" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        batch.set(
          db.collection("admins").document(uid),
          mapOf(
            "uid" to uid,
            "email" to email,
            "grantedBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
        batch.commit().await()
      }

      val sysRef = db.collection("system_config").document("global")
      if (!sysRef.get().await().exists()) {
        sysRef.set(
          mapOf(
            "maintenanceMode" to false,
            "maintenanceMessage" to "Scheduled maintenance in progress. Only Admins have access.",
            "invitesEnabled" to true,
            "videoStudioEnabled" to true,
            "voiceStudioEnabled" to true,
            "imageStudioEnabled" to true,
            "scriptStudioEnabled" to true,
            "aiChatEnabled" to true,
            "voiceAssistantEnabled" to true,
            "phoneActionsEnabled" to true,
            "activeChatModel" to "gemini-3.8-flash",
            "maxVideoDurationSeconds" to 90,
            "videoProviderConfigured" to false,
            "videoProviderName" to "On-Device Storyboard MP4 Composer (Gemini Keyframes + MediaCodec)",
            "updatedByUid" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      }

      seedDefaultPlansIfMissing()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "users/$uid")
      Result.failure(e)
    }
  }

  suspend fun verifyAdminAccess(
    uid: String,
    email: String,
    emailVerified: Boolean,
  ): Result<Boolean> {
    if (!emailVerified) {
      return Result.success(false)
    }
    val adminRef = db.collection("admins").document(uid)
    val isOwner = email.equals(StudioConstants.OWNER_EMAIL, ignoreCase = true)
    return try {
      val adminSnap = adminRef.get().await()
      val hasGrant = if (isOwner) {
        if (!adminSnap.exists()) {
          adminRef.set(
            mapOf(
              "uid" to uid,
              "email" to email,
              "grantedBy" to uid,
              "createdAt" to FieldValue.serverTimestamp(),
            ),
          ).await()
        }
        true
      } else {
        adminSnap.exists()
      }

      if (hasGrant) {
        logAdminAction(
          actorUid = uid,
          actorEmail = email,
          action = "ADMIN_SIGN_IN",
          targetId = uid,
          details = if (isOwner) "Owner authenticated into Admin Console" else "Delegated Admin authenticated into Admin Console",
          severity = "info",
        )
      }
      Result.success(hasGrant)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, "admins/$uid")
      Result.failure(e)
    }
  }

  private suspend fun seedDefaultPlansIfMissing() {
    val plans = listOf(
      "free" to mapOf(
        "name" to "FREE PLAN",
        "description" to "Starter private access with core Gemini scripting and voice synthesis.",
        "priceUsd" to 0.0,
        "billingPeriod" to "monthly",
        "videoLimit" to 5L,
        "voiceLimit" to 10L,
        "imageLimit" to 5L,
        "scriptLimit" to 20L,
        "chatLimit" to 50L,
        "includedTools" to listOf("AI Chat", "Phone Assistant", "Voice Studio", "Image Studio", "Video Studio"),
        "isActive" to true,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      ),
      "basic" to mapOf(
        "name" to "BASIC PLAN",
        "description" to "Designed for regular creators producing weekly scripts, thumbnails, chats, and narrations.",
        "priceUsd" to 12.0,
        "billingPeriod" to "monthly",
        "videoLimit" to 25L,
        "voiceLimit" to 50L,
        "imageLimit" to 30L,
        "scriptLimit" to 100L,
        "chatLimit" to 250L,
        "includedTools" to listOf("AI Chat", "Phone Assistant", "Voice Studio", "Image Studio", "Video Studio"),
        "isActive" to true,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      ),
      "pro" to mapOf(
        "name" to "PRO PLAN",
        "description" to "High-volume documentary, multi-scene video, and creative production capacity across all AI studios.",
        "priceUsd" to 39.0,
        "billingPeriod" to "monthly",
        "videoLimit" to 150L,
        "voiceLimit" to 300L,
        "imageLimit" to 150L,
        "scriptLimit" to 500L,
        "chatLimit" to 1000L,
        "includedTools" to listOf("AI Chat", "Phone Assistant", "Voice Studio", "Image Studio", "Video Studio", "Priority Support"),
        "isActive" to true,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      ),
      "custom" to mapOf(
        "name" to "CUSTOM PLAN",
        "description" to "Tailored enterprise and owner-configured limits (-1 denotes unlimited quota).",
        "priceUsd" to 99.0,
        "billingPeriod" to "custom",
        "videoLimit" to -1L,
        "voiceLimit" to -1L,
        "imageLimit" to -1L,
        "scriptLimit" to -1L,
        "chatLimit" to -1L,
        "includedTools" to listOf("All Active AI Studios", "Custom Limits", "Direct Admin Allocation"),
        "isActive" to true,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      ),
    )
    for ((planId, data) in plans) {
      val ref = db.collection("subscription_plans").document(planId)
      if (!ref.get().await().exists()) {
        ref.set(data).await()
      }
    }
  }

  suspend fun redeemInvitationAtomically(
    uid: String,
    email: String,
    displayName: String,
    photoUrl: String,
    rawInviteCode: String,
  ): Result<Unit> {
    val inviteCode = rawInviteCode.trim().uppercase()
    if (inviteCode.isBlank()) {
      return Result.failure(IllegalArgumentException("Please enter an invitation code."))
    }
    if (!inviteCode.matches(Regex("^[A-Za-z0-9_\\-]{4,64}$"))) {
      return Result.failure(
        IllegalArgumentException("Invalid invitation code format. Enter the code issued by the Owner/Admin (e.g. PRINCE-XXXXXX)."),
      )
    }
    val inviteRef = db.collection("invitations").document(inviteCode)
    val userRef = db.collection("users").document(uid)

    return try {
      val sysSnap = db.collection("system_config").document("global").get().await()
      val invitesEnabled = sysSnap.getBoolean("invitesEnabled") ?: true
      if (!invitesEnabled) {
        return Result.failure(IllegalStateException("New invitations are temporarily paused by the Administrator."))
      }

      val inviteSnap = try {
        inviteRef.get().await()
      } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
          return Result.failure(
            IllegalArgumentException("Invitation code \"$inviteCode\" was not found, has already been used, or is not authorized for $email."),
          )
        }
        throw e
      }

      if (!inviteSnap.exists()) {
        return Result.failure(
          IllegalArgumentException("Invitation code \"$inviteCode\" was not found, has already been used, or is not authorized for $email."),
        )
      }
      val status = inviteSnap.getString("status") ?: ""
      if (status != "pending") {
        return Result.failure(IllegalStateException("This invitation has already been $status."))
      }
      val invitedEmail = inviteSnap.getString("invitedEmail").orEmpty()
      if (invitedEmail.isNotBlank() && !invitedEmail.equals(email, ignoreCase = true)) {
        return Result.failure(IllegalStateException("This invitation is reserved for $invitedEmail."))
      }
      val assignedPlan = inviteSnap.getString("planId") ?: StudioConstants.PLAN_FREE

      val batch = db.batch()
      batch.update(
        inviteRef,
        mapOf(
          "status" to "accepted",
          "acceptedByUid" to uid,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.set(
        userRef,
        mapOf(
          "userId" to uid,
          "email" to email,
          "displayName" to displayName.ifBlank { email.substringBefore("@") },
          "photoUrl" to photoUrl,
          "bio" to "",
          "preferredLanguage" to "en",
          "role" to StudioConstants.ROLE_USER,
          "status" to StudioConstants.STATUS_ACTIVE,
          "planId" to assignedPlan,
          "videoUsed" to 0L,
          "voiceUsed" to 0L,
          "imageUsed" to 0L,
          "scriptUsed" to 0L,
          "chatUsed" to 0L,
          "customVideoLimit" to -1L,
          "customVoiceLimit" to -1L,
          "customImageLimit" to -1L,
          "customScriptLimit" to -1L,
          "customChatLimit" to -1L,
          "recommendedPlanId" to "",
          "recommendedNote" to "",
          "inviteCode" to inviteCode,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "invitations/$inviteCode")
      Result.failure(e)
    }
  }

  suspend fun createInvitation(
    adminUid: String,
    adminEmail: String,
    invitedEmail: String,
    invitedName: String,
    planId: String,
    note: String,
  ): Result<String> {
    val code = "PRINCE-" + UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
    val ref = db.collection("invitations").document(code)
    return try {
      ref.set(
        mapOf(
          "inviteCode" to code,
          "invitedEmail" to invitedEmail.trim().lowercase(),
          "invitedName" to invitedName.trim(),
          "role" to StudioConstants.ROLE_USER,
          "planId" to planId,
          "status" to "pending",
          "createdByUid" to adminUid,
          "acceptedByUid" to "",
          "note" to note.trim(),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      logAdminAction(adminUid, adminEmail, "CREATE_INVITE", code, "Invited ${invitedEmail.ifBlank { "any valid user" }} on $planId plan", "info")
      Result.success(code)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun revokeInvitation(adminUid: String, adminEmail: String, inviteCode: String): Result<Unit> {
    val ref = db.collection("invitations").document(inviteCode)
    return try {
      ref.update(
        mapOf(
          "status" to "revoked",
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      logAdminAction(adminUid, adminEmail, "REVOKE_INVITE", inviteCode, "Revoked invitation code", "warning")
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun createPreprovisionedUser(
    adminUid: String,
    adminEmail: String,
    displayName: String,
    email: String,
    planId: String,
  ): Result<String> {
    val cleanEmail = email.trim().lowercase()
    if (cleanEmail.isBlank()) return Result.failure(IllegalArgumentException("Email is required."))
    // Creates an email-locked invitation so the user is provisioned securely when they sign in with Google.
    return createInvitation(
      adminUid = adminUid,
      adminEmail = adminEmail,
      invitedEmail = cleanEmail,
      invitedName = displayName,
      planId = planId,
      note = "Direct Admin Account Provisioning",
    )
  }

  suspend fun updateUserStatus(
    adminUid: String,
    adminEmail: String,
    targetUserId: String,
    newStatus: String,
  ): Result<Unit> {
    val ref = db.collection("users").document(targetUserId)
    return try {
      ref.update(
        mapOf(
          "status" to newStatus,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      logAdminAction(adminUid, adminEmail, "UPDATE_USER_STATUS", targetUserId, "Status changed to $newStatus", "warning")
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun setAdminRoleByOwner(
    ownerUid: String,
    ownerEmail: String,
    targetUser: UserProfile,
    makeAdmin: Boolean,
  ): Result<Unit> {
    val userRef = db.collection("users").document(targetUser.userId)
    val adminRef = db.collection("admins").document(targetUser.userId)
    return try {
      val batch = db.batch()
      if (makeAdmin) {
        batch.set(
          adminRef,
          mapOf(
            "uid" to targetUser.userId,
            "email" to targetUser.email,
            "grantedBy" to ownerUid,
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
        batch.update(
          userRef,
          mapOf(
            "role" to StudioConstants.ROLE_ADMIN,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      } else {
        batch.delete(adminRef)
        batch.update(
          userRef,
          mapOf(
            "role" to StudioConstants.ROLE_USER,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()
      logAdminAction(
        ownerUid,
        ownerEmail,
        if (makeAdmin) "GRANT_ADMIN" else "REVOKE_ADMIN",
        targetUser.userId,
        "${if (makeAdmin) "Granted" else "Revoked"} admin role for ${targetUser.email}",
        "warning",
      )
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, userRef.path)
      Result.failure(e)
    }
  }

  suspend fun assignUserPlanAndLimits(
    adminUid: String,
    adminEmail: String,
    targetUserId: String,
    planId: String,
    customVideoLimit: Long,
    customVoiceLimit: Long,
    customImageLimit: Long,
    customScriptLimit: Long,
    customChatLimit: Long = -1L,
  ): Result<Unit> {
    val ref = db.collection("users").document(targetUserId)
    return try {
      ref.update(
        mapOf(
          "planId" to planId,
          "customVideoLimit" to customVideoLimit,
          "customVoiceLimit" to customVoiceLimit,
          "customImageLimit" to customImageLimit,
          "customScriptLimit" to customScriptLimit,
          "customChatLimit" to customChatLimit,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      logAdminAction(adminUid, adminEmail, "ASSIGN_PLAN", targetUserId, "Assigned plan $planId with custom limits", "info")
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun recommendPlanToUser(
    adminUid: String,
    adminEmail: String,
    targetUser: UserProfile,
    recommendedPlanId: String,
    note: String,
  ): Result<Unit> {
    val userRef = db.collection("users").document(targetUser.userId)
    val notifRef = db.collection("notifications").document()
    return try {
      val batch = db.batch()
      batch.update(
        userRef,
        mapOf(
          "recommendedPlanId" to recommendedPlanId,
          "recommendedNote" to note.trim(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.set(
        notifRef,
        mapOf(
          "targetUserId" to targetUser.userId,
          "title" to "Plan Recommendation: ${recommendedPlanId.uppercase()}",
          "message" to note.ifBlank { "The Administrator recommends upgrading to the ${recommendedPlanId.uppercase()} plan. Review plans in Subscription Details—no automatic charges apply." },
          "category" to "recommendation",
          "recommendedPlanId" to recommendedPlanId,
          "isRead" to false,
          "createdByUid" to adminUid,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      logAdminAction(adminUid, adminEmail, "RECOMMEND_PLAN", targetUser.userId, "Recommended $recommendedPlanId to ${targetUser.email}", "info")
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, userRef.path)
      Result.failure(e)
    }
  }

  suspend fun saveSubscriptionPlan(
    adminUid: String,
    adminEmail: String,
    plan: SubscriptionPlan,
  ): Result<Unit> {
    val ref = db.collection("subscription_plans").document(plan.id)
    return try {
      if (ref.get().await().exists()) {
        ref.update(
          mapOf(
            "name" to plan.name,
            "description" to plan.description,
            "priceUsd" to plan.priceUsd,
            "billingPeriod" to plan.billingPeriod,
            "videoLimit" to plan.videoLimit,
            "voiceLimit" to plan.voiceLimit,
            "imageLimit" to plan.imageLimit,
            "scriptLimit" to plan.scriptLimit,
            "chatLimit" to plan.chatLimit,
            "includedTools" to plan.includedTools.take(10),
            "isActive" to plan.isActive,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      } else {
        ref.set(
          mapOf(
            "name" to plan.name,
            "description" to plan.description,
            "priceUsd" to plan.priceUsd,
            "billingPeriod" to plan.billingPeriod,
            "videoLimit" to plan.videoLimit,
            "voiceLimit" to plan.voiceLimit,
            "imageLimit" to plan.imageLimit,
            "scriptLimit" to plan.scriptLimit,
            "chatLimit" to plan.chatLimit,
            "includedTools" to plan.includedTools.take(10),
            "isActive" to plan.isActive,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      }
      logAdminAction(adminUid, adminEmail, "EDIT_PLAN", plan.id, "Updated ${plan.name} ($${plan.priceUsd}/${plan.billingPeriod})", "info")
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun submitUpgradeRequest(
    user: UserProfile,
    requestedPlanId: String,
    note: String,
  ): Result<Unit> {
    val ref = db.collection("upgrade_requests").document()
    return try {
      ref.set(
        mapOf(
          "userId" to user.userId,
          "userEmail" to user.email,
          "userName" to user.displayName,
          "currentPlanId" to user.planId,
          "requestedPlanId" to requestedPlanId,
          "note" to note.trim(),
          "status" to "pending",
          "adminNote" to "",
          "reviewedByUid" to "",
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun reviewUpgradeRequest(
    adminUid: String,
    adminEmail: String,
    request: UpgradeRequest,
    approve: Boolean,
    adminNote: String,
  ): Result<Unit> {
    val reqRef = db.collection("upgrade_requests").document(request.id)
    val userRef = db.collection("users").document(request.userId)
    val notifRef = db.collection("notifications").document()
    val newStatus = if (approve) "approved" else "rejected"
    return try {
      val batch = db.batch()
      batch.update(
        reqRef,
        mapOf(
          "status" to newStatus,
          "adminNote" to adminNote.trim(),
          "reviewedByUid" to adminUid,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      if (approve) {
        batch.update(
          userRef,
          mapOf(
            "planId" to request.requestedPlanId,
            "recommendedPlanId" to "",
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.set(
        notifRef,
        mapOf(
          "targetUserId" to request.userId,
          "title" to "Upgrade Request ${newStatus.replaceFirstChar { it.uppercase() }}",
          "message" to if (approve) {
            "Your upgrade to ${request.requestedPlanId.uppercase()} has been approved. ${adminNote.trim()}"
          } else {
            "Your upgrade request to ${request.requestedPlanId.uppercase()} was not approved. ${adminNote.trim()}"
          },
          "category" to "system",
          "recommendedPlanId" to "",
          "isRead" to false,
          "createdByUid" to adminUid,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      logAdminAction(adminUid, adminEmail, "REVIEW_UPGRADE", request.id, "$newStatus ${request.requestedPlanId} for ${request.userEmail}", "info")
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, reqRef.path)
      Result.failure(e)
    }
  }

  suspend fun saveProjectAndIncrementUsage(
    user: UserProfile,
    title: String,
    type: String,
    prompt: String,
    outputText: String,
    language: String,
    modelUsed: String,
    localMediaUri: String = "",
  ): Result<String> {
    val projRef = db.collection("projects").document()
    val userRef = db.collection("users").document(user.userId)
    val usageField = when (type) {
      StudioConstants.TYPE_VIDEO_PLAN, StudioConstants.TYPE_VIDEO_MP4 -> "videoUsed"
      StudioConstants.TYPE_VOICE -> "voiceUsed"
      StudioConstants.TYPE_IMAGE -> "imageUsed"
      StudioConstants.TYPE_CHAT -> "chatUsed"
      else -> "scriptUsed"
    }
    return try {
      val batch = db.batch()
      batch.set(
        projRef,
        mapOf(
          "userId" to user.userId,
          "title" to title.trim().take(140).ifBlank { "Untitled Creation" },
          "type" to type,
          "prompt" to prompt.trim().take(3900),
          "outputText" to outputText.take(19500),
          "language" to language,
          "modelUsed" to modelUsed,
          "status" to "completed",
          "localMediaUri" to localMediaUri.take(490),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.update(
        userRef,
        mapOf(
          usageField to FieldValue.increment(1L),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(projRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, projRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteProject(projectId: String): Result<Unit> {
    val ref = db.collection("projects").document(projectId)
    return try {
      ref.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun updateOwnProfile(
    uid: String,
    displayName: String,
    bio: String,
    preferredLanguage: String,
  ): Result<Unit> {
    val ref = db.collection("users").document(uid)
    return try {
      ref.update(
        mapOf(
          "displayName" to displayName.trim().take(100).ifBlank { "Creator" },
          "bio" to bio.trim().take(300),
          "preferredLanguage" to preferredLanguage,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun sendNotification(
    adminUid: String,
    adminEmail: String,
    targetUserId: String,
    title: String,
    message: String,
    category: String,
  ): Result<Unit> {
    val ref = db.collection("notifications").document()
    return try {
      ref.set(
        mapOf(
          "targetUserId" to targetUserId,
          "title" to title.trim().take(140),
          "message" to message.trim().take(1500),
          "category" to category,
          "recommendedPlanId" to "",
          "isRead" to false,
          "createdByUid" to adminUid,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      logAdminAction(adminUid, adminEmail, "SEND_NOTIFICATION", targetUserId, "Sent $category: $title", "info")
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun markNotificationRead(notificationId: String): Result<Unit> {
    val ref = db.collection("notifications").document(notificationId)
    return try {
      ref.update(
        mapOf(
          "isRead" to true,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun sendSupportMessage(
    user: UserProfile,
    subject: String,
    message: String,
  ): Result<Unit> {
    val ref = db.collection("notifications").document()
    return try {
      ref.set(
        mapOf(
          "targetUserId" to "admin_inbox",
          "title" to "Support from ${user.displayName}: ${subject.trim().take(80)}",
          "message" to "[${user.email}] ${message.trim().take(1300)}",
          "category" to "support",
          "recommendedPlanId" to "",
          "isRead" to false,
          "createdByUid" to user.userId,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun updateSystemConfig(
    adminUid: String,
    adminEmail: String,
    config: SystemConfig,
  ): Result<Unit> {
    val ref = db.collection("system_config").document("global")
    val safeModel = config.activeChatModel.trim().ifBlank { "gemini-3.8-flash" }.take(100)
    val safeDuration = config.maxVideoDurationSeconds.coerceIn(15, 300)
    val safeProvider = config.videoProviderName.trim().take(120)
    return try {
      if (ref.get().await().exists()) {
        ref.update(
          mapOf(
            "maintenanceMode" to config.maintenanceMode,
            "maintenanceMessage" to config.maintenanceMessage.trim().take(400),
            "invitesEnabled" to config.invitesEnabled,
            "videoStudioEnabled" to config.videoStudioEnabled,
            "voiceStudioEnabled" to config.voiceStudioEnabled,
            "imageStudioEnabled" to config.imageStudioEnabled,
            "scriptStudioEnabled" to config.scriptStudioEnabled,
            "aiChatEnabled" to config.aiChatEnabled,
            "voiceAssistantEnabled" to config.voiceAssistantEnabled,
            "phoneActionsEnabled" to config.phoneActionsEnabled,
            "activeChatModel" to safeModel,
            "maxVideoDurationSeconds" to safeDuration,
            "videoProviderConfigured" to config.videoProviderConfigured,
            "videoProviderName" to safeProvider,
            "updatedByUid" to adminUid,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      } else {
        ref.set(
          mapOf(
            "maintenanceMode" to config.maintenanceMode,
            "maintenanceMessage" to config.maintenanceMessage.trim().take(400),
            "invitesEnabled" to config.invitesEnabled,
            "videoStudioEnabled" to config.videoStudioEnabled,
            "voiceStudioEnabled" to config.voiceStudioEnabled,
            "imageStudioEnabled" to config.imageStudioEnabled,
            "scriptStudioEnabled" to config.scriptStudioEnabled,
            "aiChatEnabled" to config.aiChatEnabled,
            "voiceAssistantEnabled" to config.voiceAssistantEnabled,
            "phoneActionsEnabled" to config.phoneActionsEnabled,
            "activeChatModel" to safeModel,
            "maxVideoDurationSeconds" to safeDuration,
            "videoProviderConfigured" to config.videoProviderConfigured,
            "videoProviderName" to safeProvider,
            "updatedByUid" to adminUid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      }
      logAdminAction(
        adminUid,
        adminEmail,
        "UPDATE_SYSTEM_CONFIG",
        "global",
        "Chat=${config.aiChatEnabled} ($safeModel), VoiceAsst=${config.voiceAssistantEnabled}, Phone=${config.phoneActionsEnabled}, MaxVid=${safeDuration}s, Maintenance=${config.maintenanceMode}",
        "warning",
      )
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun createChatSession(
    uid: String,
    title: String,
    language: String,
    modelUsed: String,
  ): Result<String> {
    val safeLang = if (language in listOf("en", "hi", "ne")) language else "en"
    val ref = db.collection("chat_sessions").document()
    return try {
      ref.set(
        mapOf(
          "userId" to uid,
          "title" to title.trim().take(120).ifBlank { "New Chat" },
          "language" to safeLang,
          "modelUsed" to modelUsed.ifBlank { "gemini-3.8-flash" }.take(90),
          "lastMessagePreview" to "",
          "messageCount" to 0L,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      Result.success(ref.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun appendChatMessage(
    sessionId: String,
    uid: String,
    role: String,
    text: String,
    hasImageAttachment: Boolean,
    modelUsed: String,
    updateSessionTitle: String? = null,
    incrementChatQuota: Boolean = false,
  ): Result<String> {
    val sessionRef = db.collection("chat_sessions").document(sessionId)
    val msgRef = sessionRef.collection("messages").document()
    val safeText = text.trim().take(24500).ifBlank { "..." }
    return try {
      val batch = db.batch()
      batch.set(
        msgRef,
        mapOf(
          "userId" to uid,
          "role" to if (role == "model") "model" else "user",
          "text" to safeText,
          "hasImageAttachment" to hasImageAttachment,
          "modelUsed" to modelUsed.take(90),
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      val sessionUpdates = mutableMapOf<String, Any>(
        "lastMessagePreview" to safeText.take(280),
        "messageCount" to FieldValue.increment(1L),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      if (!updateSessionTitle.isNullOrBlank()) {
        sessionUpdates["title"] = updateSessionTitle.trim().take(120)
      }
      batch.update(sessionRef, sessionUpdates)
      if (incrementChatQuota) {
        batch.update(
          db.collection("users").document(uid),
          mapOf(
            "chatUsed" to FieldValue.increment(1L),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()
      Result.success(msgRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, msgRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteChatSession(sessionId: String): Result<Unit> {
    val sessionRef = db.collection("chat_sessions").document(sessionId)
    return try {
      val messagesSnap = sessionRef.collection("messages").get().await()
      if (!messagesSnap.isEmpty) {
        val chunks = messagesSnap.documents.chunked(400)
        for (chunk in chunks) {
          val batch = db.batch()
          chunk.forEach { batch.delete(it.reference) }
          batch.commit().await()
        }
      }
      sessionRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, sessionRef.path)
      Result.failure(e)
    }
  }

  suspend fun createAssistantNote(
    uid: String,
    title: String,
    content: String,
    kind: String,
    dueLabel: String = "",
  ): Result<String> {
    val ref = db.collection("assistant_notes").document()
    val safeKind = if (kind == "reminder") "reminder" else "note"
    return try {
      ref.set(
        mapOf(
          "userId" to uid,
          "title" to title.trim().take(140).ifBlank { "Quick Note" },
          "content" to content.trim().take(3800),
          "kind" to safeKind,
          "dueLabel" to dueLabel.trim().take(100),
          "isCompleted" to false,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      Result.success(ref.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun toggleAssistantNoteCompleted(noteId: String, completed: Boolean): Result<Unit> {
    val ref = db.collection("assistant_notes").document(noteId)
    return try {
      ref.update(
        mapOf(
          "isCompleted" to completed,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun deleteAssistantNote(noteId: String): Result<Unit> {
    val ref = db.collection("assistant_notes").document(noteId)
    return try {
      ref.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, ref.path)
      Result.failure(e)
    }
  }

  suspend fun logServiceError(
    userId: String,
    userEmail: String,
    studioName: String,
    errorMessage: String,
  ) {
    val ref = db.collection("admin_logs").document()
    runCatching {
      ref.set(
        mapOf(
          "actorUid" to userId,
          "actorEmail" to userEmail,
          "action" to "AI_SERVICE_ERROR",
          "targetId" to studioName,
          "details" to errorMessage.take(950),
          "severity" to "error",
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
    }
  }

  private suspend fun logAdminAction(
    actorUid: String,
    actorEmail: String,
    action: String,
    targetId: String,
    details: String,
    severity: String,
  ) {
    val ref = db.collection("admin_logs").document()
    runCatching {
      ref.set(
        mapOf(
          "actorUid" to actorUid,
          "actorEmail" to actorEmail,
          "action" to action,
          "targetId" to targetId,
          "details" to details.take(950),
          "severity" to severity,
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
    }
  }
}
