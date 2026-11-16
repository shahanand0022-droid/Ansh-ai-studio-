package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

object StudioConstants {
  const val OWNER_EMAIL = "shahprince0021@gmail.com"
  const val APP_BRAND_NAME = "PRINCE AI STUDIO"
  const val APP_SHORT_NAME = "PRINCE AI"
  const val APP_TAGLINE = "Imagine. Create. Innovate."

  const val ROLE_OWNER = "owner"
  const val ROLE_ADMIN = "admin"
  const val ROLE_USER = "user"

  const val STATUS_ACTIVE = "active"
  const val STATUS_SUSPENDED = "suspended"
  const val STATUS_DEACTIVATED = "deactivated"

  const val PLAN_FREE = "free"
  const val PLAN_BASIC = "basic"
  const val PLAN_PRO = "pro"
  const val PLAN_CUSTOM = "custom"

  const val TYPE_VIDEO_PLAN = "video_plan"
  const val TYPE_VIDEO_MP4 = "video_mp4"
  const val TYPE_VOICE = "voice"
  const val TYPE_IMAGE = "image"
  const val TYPE_SCRIPT = "script"
  const val TYPE_CHAT = "chat"
}

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val email: String = "",
  val displayName: String = "",
  val photoUrl: String = "",
  val bio: String = "",
  val preferredLanguage: String = "en",
  val role: String = StudioConstants.ROLE_USER,
  val status: String = StudioConstants.STATUS_ACTIVE,
  val planId: String = StudioConstants.PLAN_FREE,
  val videoUsed: Long = 0L,
  val voiceUsed: Long = 0L,
  val imageUsed: Long = 0L,
  val scriptUsed: Long = 0L,
  val chatUsed: Long = 0L,
  val customVideoLimit: Long = -1L,
  val customVoiceLimit: Long = -1L,
  val customImageLimit: Long = -1L,
  val customScriptLimit: Long = -1L,
  val customChatLimit: Long = -1L,
  val recommendedPlanId: String = "",
  val recommendedNote: String = "",
  val inviteCode: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val isOwner: Boolean get() = role == StudioConstants.ROLE_OWNER
  val isAdminOrOwner: Boolean get() = role == StudioConstants.ROLE_OWNER || role == StudioConstants.ROLE_ADMIN
  val isActive: Boolean get() = status == StudioConstants.STATUS_ACTIVE
}

@IgnoreExtraProperties
data class Invitation(
  @DocumentId val id: String = "",
  val inviteCode: String = "",
  val invitedEmail: String = "",
  val invitedName: String = "",
  val role: String = StudioConstants.ROLE_USER,
  val planId: String = StudioConstants.PLAN_FREE,
  val status: String = "pending",
  val createdByUid: String = "",
  val acceptedByUid: String = "",
  val note: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SubscriptionPlan(
  @DocumentId val id: String = "",
  val name: String = "",
  val description: String = "",
  val priceUsd: Double = 0.0,
  val billingPeriod: String = "monthly",
  val videoLimit: Long = 0L,
  val voiceLimit: Long = 0L,
  val imageLimit: Long = 0L,
  val scriptLimit: Long = 0L,
  val chatLimit: Long = 50L,
  val includedTools: List<String> = emptyList(),
  val isActive: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class UpgradeRequest(
  @DocumentId val id: String = "",
  val userId: String = "",
  val userEmail: String = "",
  val userName: String = "",
  val currentPlanId: String = StudioConstants.PLAN_FREE,
  val requestedPlanId: String = StudioConstants.PLAN_PRO,
  val note: String = "",
  val status: String = "pending",
  val adminNote: String = "",
  val reviewedByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class StudioProject(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val type: String = StudioConstants.TYPE_SCRIPT,
  val prompt: String = "",
  val outputText: String = "",
  val language: String = "en",
  val modelUsed: String = "",
  val status: String = "completed",
  val localMediaUri: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ChatSession(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "New Chat",
  val language: String = "en",
  val modelUsed: String = "gemini-3.8-flash",
  val lastMessagePreview: String = "",
  val messageCount: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ChatMessage(
  @DocumentId val id: String = "",
  val userId: String = "",
  val role: String = "user",
  val text: String = "",
  val hasImageAttachment: Boolean = false,
  val modelUsed: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AssistantNote(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val content: String = "",
  val kind: String = "note",
  val dueLabel: String = "",
  val isCompleted: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class NotificationItem(
  @DocumentId val id: String = "",
  val targetUserId: String = "all",
  val title: String = "",
  val message: String = "",
  val category: String = "announcement",
  val recommendedPlanId: String = "",
  val isRead: Boolean = false,
  val createdByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SystemConfig(
  @DocumentId val id: String = "global",
  val maintenanceMode: Boolean = false,
  val maintenanceMessage: String = "Scheduled maintenance in progress. Only Admins have access.",
  val invitesEnabled: Boolean = true,
  val videoStudioEnabled: Boolean = true,
  val voiceStudioEnabled: Boolean = true,
  val imageStudioEnabled: Boolean = true,
  val scriptStudioEnabled: Boolean = true,
  val aiChatEnabled: Boolean = true,
  val voiceAssistantEnabled: Boolean = true,
  val phoneActionsEnabled: Boolean = true,
  val activeChatModel: String = "gemini-3.8-flash",
  val maxVideoDurationSeconds: Int = 90,
  val videoProviderConfigured: Boolean = false,
  val videoProviderName: String = "On-Device Storyboard MP4 Composer (Gemini Keyframes + MediaCodec)",
  val updatedByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AdminLog(
  @DocumentId val id: String = "",
  val actorUid: String = "",
  val actorEmail: String = "",
  val action: String = "",
  val targetId: String = "",
  val details: String = "",
  val severity: String = "info",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

data class VideoScene(
  val sceneNumber: Int,
  val durationSeconds: Int,
  val visualPrompt: String,
  val cameraDirection: String,
  val narrationLine: String,
  val audioCue: String,
)

data class VideoStoryboard(
  val title: String,
  val format: String,
  val logline: String,
  val totalDurationSeconds: Int,
  val scenes: List<VideoScene>,
)

data class VideoProductionConfig(
  val sourceMode: String = "Text-to-Video",
  val aspectRatioLabel: String = "16:9 Widescreen",
  val resolutionLabel: String = "720p HD",
  val visualStyle: String = "Cinematic Documentary",
  val durationSeconds: Int = 30,
)

data class ChatTurn(
  val role: String,
  val text: String,
)

data class AssistantTurn(
  val id: String = "",
  val role: String,
  val text: String,
  val actionType: String = "",
  val actionSummary: String = "",
  val hasImage: Boolean = false,
)
