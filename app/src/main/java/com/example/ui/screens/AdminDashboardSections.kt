package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminLog
import com.example.data.model.Invitation
import com.example.data.model.NotificationItem
import com.example.data.model.StudioConstants
import com.example.data.model.StudioProject
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SystemConfig
import com.example.data.model.UpgradeRequest
import com.example.data.model.UserProfile
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioGlassCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PlasmaViolet
import com.example.ui.theme.RoyalGold

@Composable
fun AdminSectionSkeletonList(
  cardCount: Int = 3,
  accentColor: Color = ElectricCyan,
) {
  val transition = rememberInfiniteTransition(label = "admin_skeleton_pulse")
  val alpha by transition.animateFloat(
    initialValue = 0.30f,
    targetValue = 0.70f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 850),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "skeleton_alpha",
  )

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    repeat(cardCount) {
      StudioGlassCard(borderColor = accentColor.copy(alpha = 0.25f)) {
        Box(
          modifier = Modifier
            .fillMaxWidth(0.55f)
            .height(16.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ElevatedSurface.copy(alpha = alpha)),
        )
        Spacer(Modifier.height(10.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth(0.90f)
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ElevatedSurface.copy(alpha = alpha * 0.85f)),
        )
        Spacer(Modifier.height(6.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth(0.70f)
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ElevatedSurface.copy(alpha = alpha * 0.7f)),
        )
      }
    }
  }
}

@Composable
private fun AdminEmptyStateCard(
  icon: ImageVector,
  title: String,
  hint: String,
) {
  StudioGlassCard {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = ElectricCyan,
        modifier = Modifier.size(28.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
fun AdminOverviewSection(
  allUsers: List<UserProfile>,
  invitations: List<Invitation>,
  upgradeRequests: List<UpgradeRequest>,
  allProjects: List<StudioProject>,
  adminLogs: List<AdminLog>,
  systemConfig: SystemConfig,
  isBusy: Boolean,
) {
  val activeUsers = allUsers.count { it.status == StudioConstants.STATUS_ACTIVE }
  val pendingInvites = invitations.count { it.status == "pending" }
  val pendingRequests = upgradeRequests.count { it.status == "pending" }
  val errorCount = adminLogs.count { it.severity == "error" }

  SectionHeader("Platform Analytics", "Live telemetry across PRINCE AI STUDIO")
  if (isBusy && allUsers.isEmpty()) {
    AdminSectionSkeletonList(cardCount = 2, accentColor = RoyalGold)
  } else {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      MetricBox("Users", "${allUsers.size} ($activeUsers active)", ElectricCyan, Modifier.weight(1f))
      MetricBox("Pending Invites", "$pendingInvites", PlasmaViolet, Modifier.weight(1f))
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      MetricBox("Total Creations", "${allProjects.size}", EmeraldSuccess, Modifier.weight(1f))
      MetricBox("Upgrade Queue", "$pendingRequests", RoyalGold, Modifier.weight(1f))
    }
  }

  StudioGlassCard {
    Text("Usage Breakdown Across Platform", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    val chatTotal = allUsers.sumOf { it.chatUsed }
    val videoTotal = allUsers.sumOf { it.videoUsed }
    val voiceTotal = allUsers.sumOf { it.voiceUsed }
    val imageTotal = allUsers.sumOf { it.imageUsed }
    val scriptTotal = allUsers.sumOf { it.scriptUsed }
    Text(
      "AI Chats: $chatTotal • Videos: $videoTotal • Voice: $voiceTotal • Images: $imageTotal • Scripts: $scriptTotal",
      style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(6.dp))
    Text(
      "Service Errors Logged: $errorCount • Max Video: ${systemConfig.maxVideoDurationSeconds}s • Maintenance: ${if (systemConfig.maintenanceMode) "ON" else "OFF"}",
      style = MaterialTheme.typography.labelMedium,
      color = if (errorCount > 0) AmberWarning else EmeraldSuccess,
    )
  }

  SectionHeader("Recent System Activity")
  if (adminLogs.isEmpty()) {
    AdminEmptyStateCard(
      icon = Icons.Default.Inbox,
      title = "No system activity recorded yet",
      hint = "Admin actions, invites, plan edits, and AI service alerts will appear here.",
    )
  } else {
    adminLogs.take(8).forEach { log ->
      StudioGlassCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(log.action, style = MaterialTheme.typography.titleSmall)
          StatusPill(log.severity.uppercase(), if (log.severity == "error") CrimsonError else ElectricCyan)
        }
        Spacer(Modifier.height(4.dp))
        Text(log.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun MetricBox(
  title: String,
  value: String,
  color: Color,
  modifier: Modifier = Modifier,
) {
  StudioGlassCard(modifier = modifier, borderColor = color.copy(alpha = 0.45f)) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    Text(value, style = MaterialTheme.typography.titleLarge, color = color)
  }
}

@Composable
fun AdminUsersSection(
  currentAdmin: UserProfile,
  allUsers: List<UserProfile>,
  invitations: List<Invitation>,
  isBusy: Boolean,
  onOpenInviteDialog: () -> Unit,
  onRevokeInvite: (String) -> Unit,
  onSetUserStatus: (userId: String, status: String) -> Unit,
  onToggleAdminRole: (UserProfile, Boolean) -> Unit,
  onManagePlan: (UserProfile) -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }
  val filteredUsers = remember(allUsers, searchQuery) {
    val q = searchQuery.trim()
    if (q.isEmpty()) {
      allUsers
    } else {
      allUsers.filter {
        it.displayName.contains(q, ignoreCase = true) ||
          it.email.contains(q, ignoreCase = true) ||
          it.planId.contains(q, ignoreCase = true)
      }
    }
  }

  Button(
    onClick = onOpenInviteDialog,
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 50.dp)
      .testTag("admin_invite_user_button"),
    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
  ) {
    Icon(Icons.Default.PersonAdd, contentDescription = "Invite new user")
    Spacer(Modifier.width(8.dp))
    Text("Invite / Provision New User")
  }

  SectionHeader("Invitations (${invitations.size})", "Private access tokens for onboarding creators")
  if (invitations.isEmpty()) {
    AdminEmptyStateCard(
      icon = Icons.Default.PersonAdd,
      title = "No invitations created",
      hint = "Create an invitation token above to grant private studio access.",
    )
  } else {
    invitations.forEach { inv ->
      StudioGlassCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(inv.inviteCode, style = MaterialTheme.typography.titleMedium, color = RoyalGold)
            Text(
              text = "${inv.invitedEmail.ifBlank { "Open invite token" }} • Plan: ${inv.planId.uppercase()}",
              style = MaterialTheme.typography.bodySmall,
            )
          }
          StatusPill(
            text = inv.status.uppercase(),
            color = when (inv.status) {
              "accepted" -> EmeraldSuccess
              "revoked" -> CrimsonError
              else -> ElectricCyan
            },
          )
        }
        if (inv.status == "pending") {
          Spacer(Modifier.height(8.dp))
          OutlinedButton(
            onClick = { onRevokeInvite(inv.inviteCode) },
            modifier = Modifier.heightIn(min = 48.dp),
          ) {
            Text("Revoke Invitation")
          }
        }
      }
    }
  }

  SectionHeader("Registered Users (${filteredUsers.size})")
  OutlinedTextField(
    value = searchQuery,
    onValueChange = { searchQuery = it },
    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
    label = { Text("Search users by name, email, or plan") },
    singleLine = true,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("admin_user_search_input"),
  )

  if (isBusy && filteredUsers.isEmpty()) {
    AdminSectionSkeletonList(cardCount = 2)
  } else if (filteredUsers.isEmpty()) {
    AdminEmptyStateCard(
      icon = Icons.Default.Groups,
      title = "No matching users",
      hint = "Try clearing the search query or invite a new creator.",
    )
  } else {
    filteredUsers.forEach { user ->
      StudioGlassCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(user.displayName, style = MaterialTheme.typography.titleMedium)
            Text(user.email, style = MaterialTheme.typography.bodySmall, color = ElectricCyan)
            Text(
              "Role: ${user.role.uppercase()} • Plan: ${user.planId.uppercase()} • C:${user.chatUsed} V:${user.videoUsed} Vo:${user.voiceUsed} I:${user.imageUsed} S:${user.scriptUsed}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          StatusPill(
            text = user.status.uppercase(),
            color = if (user.isActive) EmeraldSuccess else CrimsonError,
          )
        }

        if (!user.isOwner) {
          Spacer(Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = { onManagePlan(user) },
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            ) {
              Text("Plan / Limits")
            }
            OutlinedButton(
              onClick = {
                val nextStatus = if (user.isActive) StudioConstants.STATUS_SUSPENDED else StudioConstants.STATUS_ACTIVE
                onSetUserStatus(user.userId, nextStatus)
              },
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            ) {
              Text(if (user.isActive) "Suspend" else "Activate")
            }
          }
          if (currentAdmin.isOwner) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = { onToggleAdminRole(user, user.role != StudioConstants.ROLE_ADMIN) },
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp),
              ) {
                Text(if (user.role == StudioConstants.ROLE_ADMIN) "Revoke Admin" else "Grant Admin")
              }
              OutlinedButton(
                onClick = { onSetUserStatus(user.userId, StudioConstants.STATUS_DEACTIVATED) },
                modifier = Modifier.heightIn(min = 48.dp),
              ) {
                Text("Deactivate")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun AdminSubscriptionsSection(
  plans: List<SubscriptionPlan>,
  upgradeRequests: List<UpgradeRequest>,
  isBusy: Boolean,
  onEditPlan: (SubscriptionPlan) -> Unit,
  onReviewRequest: (UpgradeRequest, Boolean, String) -> Unit,
) {
  SectionHeader("Configurable Subscription Plans", "FREE, BASIC, PRO, and CUSTOM tiers")
  if (isBusy && plans.isEmpty()) {
    AdminSectionSkeletonList(cardCount = 2, accentColor = RoyalGold)
  } else if (plans.isEmpty()) {
    AdminEmptyStateCard(
      icon = Icons.Default.WorkspacePremium,
      title = "No subscription plans loaded",
      hint = "Default subscription tiers initialize automatically for the Platform Owner.",
    )
  } else {
    plans.forEach { plan ->
      StudioGlassCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(plan.name, style = MaterialTheme.typography.titleMedium, color = RoyalGold)
            Text(
              "$${"%.2f".format(plan.priceUsd)} / ${plan.billingPeriod}",
              style = MaterialTheme.typography.labelLarge,
              color = ElectricCyan,
            )
          }
          OutlinedButton(
            onClick = { onEditPlan(plan) },
            modifier = Modifier.heightIn(min = 48.dp),
          ) {
            Icon(Icons.Default.Edit, contentDescription = "Edit plan", modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Edit")
          }
        }
        Spacer(Modifier.height(4.dp))
        Text(plan.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
          "Limits — Chat: ${plan.chatLimit} | Video: ${plan.videoLimit} | Voice: ${plan.voiceLimit} | Image: ${plan.imageLimit} | Script: ${plan.scriptLimit}",
          style = MaterialTheme.typography.labelSmall,
        )
      }
    }
  }

  SectionHeader("Upgrade Requests (${upgradeRequests.size})")
  if (upgradeRequests.isEmpty()) {
    AdminEmptyStateCard(
      icon = Icons.Default.WorkspacePremium,
      title = "No upgrade requests submitted yet",
      hint = "User requests to move between plans will appear here for manual approval.",
    )
  } else {
    upgradeRequests.forEach { req ->
      var adminNote by remember(req.id) { mutableStateOf("") }
      StudioGlassCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Column(modifier = Modifier.weight(1f)) {
            Text("${req.userName} (${req.userEmail})", style = MaterialTheme.typography.titleSmall)
            Text(
              "${req.currentPlanId.uppercase()} -> ${req.requestedPlanId.uppercase()}",
              style = MaterialTheme.typography.labelLarge,
              color = ElectricCyan,
            )
          }
          StatusPill(
            text = req.status.uppercase(),
            color = when (req.status) {
              "approved" -> EmeraldSuccess
              "rejected" -> CrimsonError
              else -> AmberWarning
            },
          )
        }
        if (req.note.isNotBlank()) {
          Spacer(Modifier.height(4.dp))
          Text("User Note: ${req.note}", style = MaterialTheme.typography.bodySmall)
        }
        if (req.status == "pending") {
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(
            value = adminNote,
            onValueChange = { adminNote = it },
            label = { Text("Decision Note (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = { onReviewRequest(req, true, adminNote) },
              enabled = !isBusy,
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            ) {
              Icon(Icons.Default.Check, contentDescription = null)
              Spacer(Modifier.width(4.dp))
              Text("Approve")
            }
            OutlinedButton(
              onClick = { onReviewRequest(req, false, adminNote) },
              enabled = !isBusy,
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            ) {
              Icon(Icons.Default.Close, contentDescription = null)
              Spacer(Modifier.width(4.dp))
              Text("Reject")
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminAiConfigSection(
  systemConfig: SystemConfig,
  adminLogs: List<AdminLog>,
  isBusy: Boolean,
  onUpdateSystemConfig: (SystemConfig) -> Unit,
) {
  var maintenanceMessage by remember(systemConfig.maintenanceMessage) {
    mutableStateOf(systemConfig.maintenanceMessage)
  }
  var providerNameDraft by remember(systemConfig.videoProviderName) {
    mutableStateOf(systemConfig.videoProviderName)
  }

  SectionHeader("AI Providers & Integration Health", "Live backend status across studio pipelines")
  StudioGlassCard {
    ProviderRow("Active Chat & Assistant Model (${systemConfig.activeChatModel})", "CONNECTED (App Check)", EmeraldSuccess)
    Spacer(Modifier.height(6.dp))
    ProviderRow("Gemini Nano Banana 2.1 (AI Images & Keyframes)", "CONNECTED (App Check)", EmeraldSuccess)
    Spacer(Modifier.height(6.dp))
    ProviderRow("On-Device MediaCodec MP4 Composer (Up to ${systemConfig.maxVideoDurationSeconds}s)", "ACTIVE ON DEVICE", EmeraldSuccess)
    Spacer(Modifier.height(6.dp))
    ProviderRow("Android SpeechRecognizer & TTS (EN / HI / NE)", "ACTIVE ON DEVICE", EmeraldSuccess)
    Spacer(Modifier.height(6.dp))
    ProviderRow(
      "External Cloud Video Renderer (Veo)",
      if (systemConfig.videoProviderConfigured) "CONFIGURED" else "NOT CONFIGURED",
      if (systemConfig.videoProviderConfigured) EmeraldSuccess else AmberWarning,
    )
  }

  SectionHeader("Model & Video Duration Policies", "Configure Gemini tier and maximum scene-based video runtime")
  StudioGlassCard {
    Text("Default AI Chat / Assistant Model", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon").forEach { model ->
        FilterChip(
          selected = systemConfig.activeChatModel == model,
          onClick = { onUpdateSystemConfig(systemConfig.copy(activeChatModel = model)) },
          label = { Text(model) },
        )
      }
    }
    Spacer(Modifier.height(8.dp))
    Text("Maximum Video Duration Cap", style = MaterialTheme.typography.labelLarge, color = RoyalGold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf(30, 60, 90, 120, 180).forEach { sec ->
        FilterChip(
          selected = systemConfig.maxVideoDurationSeconds == sec,
          onClick = { onUpdateSystemConfig(systemConfig.copy(maxVideoDurationSeconds = sec)) },
          label = { Text("${sec}s Max") },
          modifier = Modifier.testTag("admin_max_video_${sec}s"),
        )
      }
    }
    Spacer(Modifier.height(8.dp))
    ToggleRow("External Cloud Video Endpoint Flag", systemConfig.videoProviderConfigured) {
      onUpdateSystemConfig(systemConfig.copy(videoProviderConfigured = it))
    }
    OutlinedTextField(
      value = providerNameDraft,
      onValueChange = { providerNameDraft = it },
      label = { Text("Active Video Pipeline Label") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
      onClick = { onUpdateSystemConfig(systemConfig.copy(videoProviderName = providerNameDraft)) },
      enabled = !isBusy,
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
      Text("Save Video Provider Label")
    }
  }

  SectionHeader("AI Tool & Access Controls", "Enable or pause individual studios, assistant features, and invitations")
  StudioGlassCard {
    ToggleRow("AI Chat Assistant", systemConfig.aiChatEnabled) {
      onUpdateSystemConfig(systemConfig.copy(aiChatEnabled = it))
    }
    ToggleRow("PRINCE AI Voice Assistant", systemConfig.voiceAssistantEnabled) {
      onUpdateSystemConfig(systemConfig.copy(voiceAssistantEnabled = it))
    }
    ToggleRow("Android Phone Device Actions", systemConfig.phoneActionsEnabled) {
      onUpdateSystemConfig(systemConfig.copy(phoneActionsEnabled = it))
    }
    ToggleRow("AI Video Studio", systemConfig.videoStudioEnabled) {
      onUpdateSystemConfig(systemConfig.copy(videoStudioEnabled = it))
    }
    ToggleRow("AI Voice Studio", systemConfig.voiceStudioEnabled) {
      onUpdateSystemConfig(systemConfig.copy(voiceStudioEnabled = it))
    }
    ToggleRow("AI Image Studio", systemConfig.imageStudioEnabled) {
      onUpdateSystemConfig(systemConfig.copy(imageStudioEnabled = it))
    }
    ToggleRow("AI Script Assistant", systemConfig.scriptStudioEnabled) {
      onUpdateSystemConfig(systemConfig.copy(scriptStudioEnabled = it))
    }
    ToggleRow("Allow New Invitation Redemptions", systemConfig.invitesEnabled) {
      onUpdateSystemConfig(systemConfig.copy(invitesEnabled = it))
    }
    ToggleRow("Platform Maintenance Mode", systemConfig.maintenanceMode) {
      onUpdateSystemConfig(systemConfig.copy(maintenanceMode = it, maintenanceMessage = maintenanceMessage))
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
      value = maintenanceMessage,
      onValueChange = { maintenanceMessage = it },
      label = { Text("Maintenance Banner Message") },
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
      onClick = { onUpdateSystemConfig(systemConfig.copy(maintenanceMessage = maintenanceMessage)) },
      enabled = !isBusy,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp),
    ) {
      Text("Save Maintenance Message")
    }
  }

  SectionHeader("AI Service & Generation Errors")
  val errors = adminLogs.filter { it.severity == "error" }
  if (errors.isEmpty()) {
    AdminEmptyStateCard(
      icon = Icons.Default.AutoAwesome,
      title = "All AI pipelines nominal",
      hint = "No AI generation or service errors recorded.",
    )
  } else {
    errors.forEach { err ->
      StudioGlassCard(borderColor = CrimsonError.copy(alpha = 0.6f)) {
        Text("${err.targetId} • ${err.actorEmail}", style = MaterialTheme.typography.titleSmall, color = CrimsonError)
        Text(err.details, style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

@Composable
private fun ProviderRow(name: String, status: String, color: Color) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    Spacer(Modifier.width(8.dp))
    StatusPill(status, color)
  }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminCommsLogsSection(
  allUsers: List<UserProfile>,
  adminNotifications: List<NotificationItem>,
  adminLogs: List<AdminLog>,
  isBusy: Boolean,
  onSendNotification: (targetUid: String, title: String, message: String, category: String) -> Unit,
) {
  var targetUid by remember { mutableStateOf("all") }
  var category by remember { mutableStateOf("announcement") }
  var title by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("") }

  SectionHeader("Broadcast Announcements & Notifications")
  StudioGlassCard {
    Text("Category", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf("announcement", "maintenance", "system").forEach { cat ->
        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat.uppercase()) })
      }
    }
    Spacer(Modifier.height(6.dp))
    Text("Recipient", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilterChip(selected = targetUid == "all", onClick = { targetUid = "all" }, label = { Text("All Users") })
      allUsers.take(6).forEach { u ->
        FilterChip(selected = targetUid == u.userId, onClick = { targetUid = u.userId }, label = { Text(u.displayName) })
      }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
      value = title,
      onValueChange = { title = it },
      label = { Text("Title") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
      value = message,
      onValueChange = { message = it },
      label = { Text("Message") },
      minLines = 3,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    Button(
      onClick = {
        onSendNotification(targetUid, title, message, category)
        title = ""
        message = ""
      },
      enabled = !isBusy && title.isNotBlank() && message.isNotBlank(),
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp),
    ) {
      Icon(Icons.Default.Campaign, contentDescription = null)
      Spacer(Modifier.width(8.dp))
      Text("Send Notification")
    }
  }

  val supportInbox = adminNotifications.filter { it.targetUserId == "admin_inbox" }
  SectionHeader("User Support Inbox (${supportInbox.size})")
  if (supportInbox.isEmpty()) {
    AdminEmptyStateCard(
      icon = Icons.Default.Inbox,
      title = "Support inbox empty",
      hint = "Direct creator messages submitted from Account & Support will arrive here.",
    )
  } else {
    supportInbox.forEach { msg ->
      StudioGlassCard {
        Text(msg.title, style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
        Spacer(Modifier.height(4.dp))
        Text(msg.message, style = MaterialTheme.typography.bodySmall)
      }
    }
  }

  SectionHeader("Admin Audit Log (${adminLogs.size})")
  if (adminLogs.isEmpty()) {
    AdminEmptyStateCard(
      icon = Icons.Default.Inbox,
      title = "No audit entries",
      hint = "Administrative changes are automatically logged here.",
    )
  } else {
    adminLogs.forEach { log ->
      StudioGlassCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(log.action, style = MaterialTheme.typography.labelLarge, color = RoyalGold)
          Text(log.actorEmail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(log.details, style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}
