package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.MediaExportHelper
import com.example.data.model.NotificationItem
import com.example.data.model.StudioConstants
import com.example.data.model.StudioProject
import com.example.data.model.SubscriptionPlan
import com.example.data.model.UpgradeRequest
import com.example.data.model.UserProfile
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioGlassCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.PlasmaViolet
import com.example.ui.theme.RoyalGold
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectsAndHistoryScreen(
  projects: List<StudioProject>,
  loadBitmap: (String) -> Bitmap?,
  onPlayVoiceFile: (File) -> Unit,
  onDeleteProject: (String) -> Unit,
) {
  val context = LocalContext.current
  val filters = listOf(
    "all" to "All",
    StudioConstants.TYPE_VIDEO_MP4 to "MP4 Videos",
    StudioConstants.TYPE_VIDEO_PLAN to "Video Plans",
    StudioConstants.TYPE_VOICE to "Voice",
    StudioConstants.TYPE_IMAGE to "Images",
    StudioConstants.TYPE_SCRIPT to "Scripts",
  )
  var selectedFilter by remember { mutableStateOf("all") }
  var expandedProject by remember { mutableStateOf<StudioProject?>(null) }

  val filtered = remember(projects, selectedFilter) {
    if (selectedFilter == "all") projects else projects.filter { it.type == selectedFilter }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    SectionHeader(
      title = "My Projects & History",
      subtitle = "${filtered.size} creation${if (filtered.size == 1) "" else "s"} in your private workspace",
    )
    Spacer(Modifier.height(8.dp))

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      filters.forEach { (key, label) ->
        FilterChip(
          selected = selectedFilter == key,
          onClick = { selectedFilter = key },
          label = { Text(label) },
        )
      }
    }
    Spacer(Modifier.height(10.dp))

    if (filtered.isEmpty()) {
      StudioGlassCard {
        Text(
          "No creations yet in this category. Open any AI Studio to create your first project.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(filtered, key = { it.id }) { proj ->
          StudioGlassCard(onClick = { expandedProject = proj }) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(proj.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(proj.modelUsed.ifBlank { proj.type }, style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
              }
              StatusPill(
                text = proj.type.replace("_", " ").uppercase(),
                color = when (proj.type) {
                  StudioConstants.TYPE_IMAGE -> RoyalGold
                  StudioConstants.TYPE_VOICE -> PlasmaViolet
                  StudioConstants.TYPE_VIDEO_PLAN -> ElectricCyan
                  else -> EmeraldSuccess
                },
              )
            }
            Spacer(Modifier.height(6.dp))
            Text(
              text = proj.outputText.ifBlank { proj.prompt },
              style = MaterialTheme.typography.bodySmall,
              maxLines = 3,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (proj.type == StudioConstants.TYPE_VOICE && proj.localMediaUri.isNotBlank()) {
                OutlinedButton(
                  onClick = { onPlayVoiceFile(File(proj.localMediaUri)) },
                  modifier = Modifier.height(48.dp),
                ) {
                  Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("Play")
                }
              }
              if (proj.type == StudioConstants.TYPE_VIDEO_MP4 && proj.localMediaUri.isNotBlank()) {
                val mp4File = File(proj.localMediaUri)
                if (mp4File.exists()) {
                  OutlinedButton(
                    onClick = { MediaExportHelper.shareFile(context, mp4File, "video/mp4", "Share Video") },
                    modifier = Modifier.height(48.dp),
                  ) {
                    Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("MP4")
                  }
                }
              }
              OutlinedButton(
                onClick = { MediaExportHelper.shareText(context, proj.title, "${proj.prompt}\n\n${proj.outputText}") },
                modifier = Modifier.height(48.dp),
              ) {
                Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Share")
              }
              IconButton(onClick = { onDeleteProject(proj.id) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete project", tint = CrimsonError)
              }
            }
          }
        }
      }
    }
  }

  expandedProject?.let { proj ->
    val bmp = remember(proj.localMediaUri) {
      if (proj.type == StudioConstants.TYPE_IMAGE) loadBitmap(proj.localMediaUri) else null
    }
    val videoFile = remember(proj.localMediaUri, proj.type) {
      if (proj.type == StudioConstants.TYPE_VIDEO_MP4 && proj.localMediaUri.isNotBlank()) {
        File(proj.localMediaUri).takeIf { it.exists() }
      } else {
        null
      }
    }
    AlertDialog(
      onDismissRequest = { expandedProject = null },
      title = { Text(proj.title) },
      text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          if (videoFile != null) {
            AndroidView(
              factory = { ctx ->
                VideoView(ctx).apply {
                  val mc = MediaController(ctx)
                  setMediaController(mc)
                  mc.setAnchorView(this)
                  setVideoURI(Uri.fromFile(videoFile))
                  setOnPreparedListener { start() }
                }
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(MaterialTheme.shapes.small),
            )
            Spacer(Modifier.height(10.dp))
          } else if (bmp != null) {
            Image(
              bitmap = bmp.asImageBitmap(),
              contentDescription = proj.title,
              modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(MaterialTheme.shapes.small),
              contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(10.dp))
          }
          Text("Prompt", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
          Text(proj.prompt, style = MaterialTheme.typography.bodySmall)
          Spacer(Modifier.height(10.dp))
          Text("Output", style = MaterialTheme.typography.labelMedium, color = RoyalGold)
          Text(proj.outputText, style = MaterialTheme.typography.bodySmall)
        }
      },
      confirmButton = {
        TextButton(onClick = { expandedProject = null }) { Text("Close") }
      },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubscriptionAndAccountScreen(
  profile: UserProfile,
  plans: List<SubscriptionPlan>,
  myRequests: List<UpgradeRequest>,
  notifications: List<NotificationItem>,
  isBusy: Boolean,
  onSubmitUpgradeRequest: (planId: String, note: String) -> Unit,
  onMarkNotificationRead: (String) -> Unit,
  onUpdateProfile: (displayName: String, bio: String, lang: String) -> Unit,
  onSendSupport: (subject: String, message: String) -> Unit,
  onSignOut: () -> Unit,
) {
  var pendingConfirmPlan by remember { mutableStateOf<SubscriptionPlan?>(null) }
  var upgradeNote by remember { mutableStateOf("") }

  var editName by remember(profile.displayName) { mutableStateOf(profile.displayName) }
  var editBio by remember(profile.bio) { mutableStateOf(profile.bio) }
  var editLang by remember(profile.preferredLanguage) { mutableStateOf(profile.preferredLanguage) }

  var supportSubject by remember { mutableStateOf("") }
  var supportMessage by remember { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    SectionHeader(
      title = "Plans, Inbox & Profile",
      subtitle = "Manage your subscription, notifications, security session & support",
    )

    // Subscription Plans
    StudioGlassCard(borderColor = AmberWarning.copy(alpha = 0.5f)) {
      Text(
        "Billing Notice: Payment processing is disabled until a payment provider is configured. Plan recommendations never charge automatically—upgrade requests require explicit confirmation and Admin approval.",
        style = MaterialTheme.typography.bodySmall,
        color = AmberWarning,
      )
    }

    plans.forEach { plan ->
      val isCurrent = plan.id == profile.planId
      val isRecommended = plan.id == profile.recommendedPlanId
      StudioGlassCard(
        borderColor = when {
          isRecommended -> RoyalGold
          isCurrent -> EmeraldSuccess
          else -> MaterialTheme.colorScheme.outline
        },
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(plan.name, style = MaterialTheme.typography.titleMedium)
            Text(
              text = if (plan.priceUsd == 0.0) "Free" else "$${"%.2f".format(plan.priceUsd)} / ${plan.billingPeriod}",
              style = MaterialTheme.typography.labelLarge,
              color = ElectricCyan,
            )
          }
          if (isCurrent) {
            StatusPill("CURRENT PLAN", EmeraldSuccess)
          } else if (isRecommended) {
            StatusPill("RECOMMENDED", RoyalGold)
          }
        }
        Spacer(Modifier.height(6.dp))
        Text(plan.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
          text = "Quotas — Chat: ${fmtLimit(plan.chatLimit)} • Video: ${fmtLimit(plan.videoLimit)} • Voice: ${fmtLimit(plan.voiceLimit)} • Images: ${fmtLimit(plan.imageLimit)} • Scripts: ${fmtLimit(plan.scriptLimit)}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (!isCurrent) {
          Spacer(Modifier.height(10.dp))
          Button(
            onClick = { pendingConfirmPlan = plan },
            enabled = !isBusy,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("request_upgrade_${plan.id}"),
          ) {
            Text("Request Upgrade to ${plan.name}")
          }
        }
      }
    }

    if (myRequests.isNotEmpty()) {
      SectionHeader("My Upgrade Requests", "Track Admin approval status")
      myRequests.forEach { req ->
        StudioGlassCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("${req.currentPlanId.uppercase()} → ${req.requestedPlanId.uppercase()}", style = MaterialTheme.typography.titleSmall)
            StatusPill(
              text = req.status.uppercase(),
              color = when (req.status) {
                "approved" -> EmeraldSuccess
                "rejected" -> CrimsonError
                else -> AmberWarning
              },
            )
          }
          if (req.adminNote.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("Admin note: ${req.adminNote}", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }

    // Notifications Inbox
    SectionHeader("Notifications & Announcements", "${notifications.size} messages")
    if (notifications.isEmpty()) {
      StudioGlassCard {
        Text("No notifications yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    } else {
      notifications.forEach { notif ->
        StudioGlassCard(borderColor = if (!notif.isRead) ElectricCyan else MaterialTheme.colorScheme.outline) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(notif.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (!notif.isRead && notif.targetUserId == profile.userId) {
              IconButton(onClick = { onMarkNotificationRead(notif.id) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MarkEmailRead, contentDescription = "Mark read", tint = ElectricCyan)
              }
            }
          }
          Text(notif.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    // Profile Settings
    SectionHeader("Profile Settings")
    StudioGlassCard {
      OutlinedTextField(
        value = editName,
        onValueChange = { editName = it },
        label = { Text("Display Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(8.dp))
      OutlinedTextField(
        value = editBio,
        onValueChange = { editBio = it },
        label = { Text("Creator Bio") },
        maxLines = 3,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(8.dp))
      Text("Preferred Language", style = MaterialTheme.typography.labelMedium)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("en" to "English", "hi" to "Hindi", "ne" to "Nepali").forEach { (code, name) ->
          FilterChip(
            selected = editLang == code,
            onClick = { editLang = code },
            label = { Text(name) },
          )
        }
      }
      Spacer(Modifier.height(10.dp))
      Button(
        onClick = { onUpdateProfile(editName, editBio, editLang) },
        enabled = !isBusy,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp),
      ) {
        Text("Save Profile")
      }
    }

    // Contact Admin Support
    SectionHeader("Support & Contact Admin")
    StudioGlassCard {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.SupportAgent, contentDescription = null, tint = ElectricCyan)
        Spacer(Modifier.width(8.dp))
        Text("Message Administrator", style = MaterialTheme.typography.titleSmall)
      }
      Spacer(Modifier.height(8.dp))
      OutlinedTextField(
        value = supportSubject,
        onValueChange = { supportSubject = it },
        label = { Text("Subject") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(8.dp))
      OutlinedTextField(
        value = supportMessage,
        onValueChange = { supportMessage = it },
        label = { Text("Message") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(10.dp))
      OutlinedButton(
        onClick = {
          onSendSupport(supportSubject, supportMessage)
          supportSubject = ""
          supportMessage = ""
        },
        enabled = !isBusy && supportSubject.isNotBlank() && supportMessage.isNotBlank(),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp),
      ) {
        Text("Send Support Message")
      }
    }

    // Session & Security Card
    SectionHeader("Session & Security")
    StudioGlassCard(borderColor = ElectricCyan.copy(alpha = 0.35f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Security, contentDescription = null, tint = ElectricCyan)
        Spacer(Modifier.width(8.dp))
        Text("Verified Google Session", style = MaterialTheme.typography.titleSmall)
      }
      Spacer(Modifier.height(6.dp))
      Text("Account: ${profile.email}", style = MaterialTheme.typography.bodySmall)
      Text("User ID: ${profile.userId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text("Access Role: ${profile.role.uppercase()} • Status: ${profile.status.uppercase()}", style = MaterialTheme.typography.bodySmall)
      Spacer(Modifier.height(6.dp))
      Text(
        "Password & 2FA recovery are secured directly through your Google Account settings.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(12.dp))
      OutlinedButton(
        onClick = onSignOut,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("account_sign_out_button"),
      ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Sign Out")
      }
    }
    Spacer(Modifier.height(16.dp))
  }

  pendingConfirmPlan?.let { targetPlan ->
    AlertDialog(
      onDismissRequest = { pendingConfirmPlan = null },
      title = { Text("Confirm Upgrade Request") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Request upgrade from ${profile.planId.uppercase()} to ${targetPlan.name}?")
          Text(
            "Payment integration is disabled. No automatic charge will occur—your request goes to the Admin for approval.",
            style = MaterialTheme.typography.bodySmall,
            color = AmberWarning,
          )
          OutlinedTextField(
            value = upgradeNote,
            onValueChange = { upgradeNote = it },
            label = { Text("Optional Note for Admin") },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(onClick = {
          onSubmitUpgradeRequest(targetPlan.id, upgradeNote)
          upgradeNote = ""
          pendingConfirmPlan = null
        }) {
          Text("Confirm Request")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingConfirmPlan = null }) { Text("Cancel") }
      },
    )
  }
}

private fun fmtLimit(limit: Long): String = if (limit < 0L) "∞" else limit.toString()
