package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.NotificationItem
import com.example.data.model.StudioConstants
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SystemConfig
import com.example.data.model.UserProfile
import com.example.ui.components.QuotaProgressBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioGlassCard
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PlasmaViolet
import com.example.ui.theme.RoyalGold

@Composable
fun UserDashboardScreen(
  profile: UserProfile,
  plan: SubscriptionPlan?,
  config: SystemConfig,
  notifications: List<NotificationItem>,
  projectCount: Int,
  chatSessionCount: Int,
  onOpenStudio: (String) -> Unit,
  onOpenPhoneAssistant: () -> Unit,
  onOpenPermissionsDashboard: () -> Unit,
  onOpenProjects: () -> Unit,
  onOpenAccount: () -> Unit,
  onOpenAdmin: () -> Unit,
) {
  val unreadCount = notifications.count { !it.isRead }
  val videoLimit = if (profile.customVideoLimit >= 0L) profile.customVideoLimit else (plan?.videoLimit ?: 5L)
  val voiceLimit = if (profile.customVoiceLimit >= 0L) profile.customVoiceLimit else (plan?.voiceLimit ?: 10L)
  val imageLimit = if (profile.customImageLimit >= 0L) profile.customImageLimit else (plan?.imageLimit ?: 5L)
  val scriptLimit = if (profile.customScriptLimit >= 0L) profile.customScriptLimit else (plan?.scriptLimit ?: 20L)
  val chatLimit = if (profile.customChatLimit >= 0L) profile.customChatLimit else (plan?.chatLimit ?: 50L)

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Hero Profile Header Card
    StudioGlassCard(borderColor = ElectricCyan.copy(alpha = 0.4f)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (profile.photoUrl.isNotBlank()) {
          AsyncImage(
            model = profile.photoUrl,
            contentDescription = "${profile.displayName} avatar",
            modifier = Modifier.size(52.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
          )
        } else {
          Surface(modifier = Modifier.size(52.dp), shape = CircleShape, color = CyanContainer) {
            Row(
              modifier = Modifier.fillMaxSize(),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(profile.displayName.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = ElectricCyan)
            }
          }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text("Welcome back, ${profile.displayName}", style = MaterialTheme.typography.titleLarge)
          Text(profile.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(profile.role.uppercase(), if (profile.isOwner) RoyalGold else ElectricCyan)
            StatusPill("${profile.planId.uppercase()} PLAN", EmeraldSuccess)
          }
        }
      }

      if (profile.isAdminOrOwner) {
        Spacer(Modifier.height(12.dp))
        Button(
          onClick = onOpenAdmin,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_admin_dashboard_button"),
          colors = ButtonDefaults.buttonColors(containerColor = RoyalGold, contentColor = ObsidianBg),
        ) {
          Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Open Owner & Admin Console", fontWeight = FontWeight.Bold)
        }
      }
    }

    // Admin Recommendation Banner
    if (profile.recommendedPlanId.isNotBlank() && profile.recommendedPlanId != profile.planId) {
      StudioGlassCard(borderColor = RoyalGold) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Star, contentDescription = null, tint = RoyalGold)
          Spacer(Modifier.width(8.dp))
          Text("Admin Plan Recommendation: ${profile.recommendedPlanId.uppercase()}", style = MaterialTheme.typography.titleMedium, color = RoyalGold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
          profile.recommendedNote.ifBlank { "The Administrator recommends upgrading to ${profile.recommendedPlanId.uppercase()}. Review plans below—no automatic charge applies." },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = onOpenAccount, modifier = Modifier.height(48.dp)) {
          Text("Review Recommended Plan")
        }
      }
    }

    // Usage & Quota Summary
    SectionHeader(
      title = "Usage & Creation Quotas",
      subtitle = "$projectCount saved projects • $chatSessionCount chat threads",
    )
    StudioGlassCard {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        QuotaProgressBar("AI Chat Assistant", profile.chatUsed, chatLimit, ElectricCyan)
        QuotaProgressBar("AI Video Productions", profile.videoUsed, videoLimit, RoyalGold)
        QuotaProgressBar("AI Voiceovers", profile.voiceUsed, voiceLimit, PlasmaViolet)
        QuotaProgressBar("AI Images", profile.imageUsed, imageLimit, RoyalGold)
        QuotaProgressBar("AI Scripts & Prompts", profile.scriptUsed, scriptLimit, EmeraldSuccess)
      }
    }

    // Primary AI Studios & Companion Grid
    SectionHeader(
      title = "AI Studios & Phone Assistant",
      subtitle = "Tap any workspace below to begin",
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StudioLaunchTile(
          title = "AI Chat",
          subtitle = if (config.aiChatEnabled) "$chatSessionCount chats • EN/HI/NE" else "Disabled by Admin",
          icon = Icons.Default.ChatBubble,
          accent = ElectricCyan,
          enabled = config.aiChatEnabled,
          modifier = Modifier.weight(1f).testTag("tile_chat_studio"),
          onClick = { onOpenStudio(StudioConstants.TYPE_CHAT) },
        )
        StudioLaunchTile(
          title = "Phone Assistant",
          subtitle = if (config.voiceAssistantEnabled) "Voice + Device Actions" else "Disabled by Admin",
          icon = Icons.Default.RecordVoiceOver,
          accent = RoyalGold,
          enabled = config.voiceAssistantEnabled,
          modifier = Modifier.weight(1f).testTag("tile_phone_assistant"),
          onClick = onOpenPhoneAssistant,
        )
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StudioLaunchTile(
          title = "AI Video Studio",
          subtitle = if (config.videoStudioEnabled) "Up to ${config.maxVideoDurationSeconds}s MP4" else "Disabled by Admin",
          icon = Icons.Default.MovieCreation,
          accent = ElectricCyan,
          enabled = config.videoStudioEnabled,
          modifier = Modifier.weight(1f).testTag("tile_video_studio"),
          onClick = { onOpenStudio(StudioConstants.TYPE_VIDEO_PLAN) },
        )
        StudioLaunchTile(
          title = "AI Voice Studio",
          subtitle = if (config.voiceStudioEnabled) "EN • HI • NE Audio" else "Disabled by Admin",
          icon = Icons.Default.GraphicEq,
          accent = PlasmaViolet,
          enabled = config.voiceStudioEnabled,
          modifier = Modifier.weight(1f).testTag("tile_voice_studio"),
          onClick = { onOpenStudio(StudioConstants.TYPE_VOICE) },
        )
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StudioLaunchTile(
          title = "AI Image Studio",
          subtitle = if (config.imageStudioEnabled) "Nano Banana 2.1" else "Disabled by Admin",
          icon = Icons.Default.Brush,
          accent = RoyalGold,
          enabled = config.imageStudioEnabled,
          modifier = Modifier.weight(1f).testTag("tile_image_studio"),
          onClick = { onOpenStudio(StudioConstants.TYPE_IMAGE) },
        )
        StudioLaunchTile(
          title = "Script Assistant",
          subtitle = if (config.scriptStudioEnabled) "Gemini 3.8 Flash" else "Disabled by Admin",
          icon = Icons.Default.AutoAwesome,
          accent = EmeraldSuccess,
          enabled = config.scriptStudioEnabled,
          modifier = Modifier.weight(1f).testTag("tile_script_studio"),
          onClick = { onOpenStudio(StudioConstants.TYPE_SCRIPT) },
        )
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StudioLaunchTile(
          title = "My Projects",
          subtitle = "$projectCount saved files",
          icon = Icons.Default.Folder,
          accent = ElectricCyan,
          enabled = true,
          modifier = Modifier.weight(1f).testTag("tile_projects_library"),
          onClick = onOpenProjects,
        )
        StudioLaunchTile(
          title = "Permissions",
          subtitle = "Privacy & access control",
          icon = Icons.Default.Security,
          accent = EmeraldSuccess,
          enabled = true,
          modifier = Modifier.weight(1f).testTag("tile_permissions_dashboard"),
          onClick = onOpenPermissionsDashboard,
        )
      }
    }

    // Recent Notifications
    SectionHeader(
      title = "Notifications & Subscription",
      subtitle = if (unreadCount > 0) "$unreadCount unread updates" else "Plan & account alerts",
      trailing = {
        Button(onClick = onOpenAccount, modifier = Modifier.height(48.dp)) {
          Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Account ($unreadCount)")
        }
      },
    )
    if (notifications.isEmpty()) {
      StudioGlassCard {
        Text(
          "No announcements or alerts right now.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      notifications.take(2).forEach { notif ->
        StudioGlassCard(
          borderColor = if (!notif.isRead) ElectricCyan.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
          onClick = onOpenAccount,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(notif.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            StatusPill(notif.category.uppercase(), ElectricCyan)
          }
          Spacer(Modifier.height(4.dp))
          Text(notif.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    Spacer(Modifier.height(16.dp))
  }
}

@Composable
private fun StudioLaunchTile(
  title: String,
  subtitle: String,
  icon: ImageVector,
  accent: Color,
  enabled: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  StudioGlassCard(
    modifier = modifier,
    borderColor = if (enabled) accent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline,
    onClick = onClick,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = title,
      tint = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(28.dp),
    )
    Spacer(Modifier.height(10.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(2.dp))
    Text(
      subtitle,
      style = MaterialTheme.typography.bodySmall,
      color = if (enabled) accent else MaterialTheme.colorScheme.error,
    )
  }
}
