package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.AssistantPermissionSnapshot
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioGlassCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoyalGold

@Composable
fun PermissionDashboardScreen(
  permissions: AssistantPermissionSnapshot,
  onRefresh: () -> Unit,
  onOpenNotificationAccess: () -> Unit,
  onOpenWriteSettings: () -> Unit,
  onOpenAppSettings: () -> Unit,
  onBack: () -> Unit,
) {
  val micLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { _ ->
    onRefresh()
  }

  LaunchedEffect(Unit) {
    onRefresh()
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Spacer(Modifier.width(4.dp))
        Text("Privacy & Permissions", style = MaterialTheme.typography.titleLarge, color = RoyalGold)
      }
      IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp).testTag("permissions_refresh_button")) {
        Icon(Icons.Default.Refresh, contentDescription = "Refresh permission status", tint = ElectricCyan)
      }
    }

    StudioGlassCard(borderColor = ElectricCyan.copy(alpha = 0.45f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Security, contentDescription = null, tint = ElectricCyan)
        Spacer(Modifier.width(8.dp))
        Text("Safety & Android Policy Commitment", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
      }
      Spacer(Modifier.height(6.dp))
      Text(
        "• Zero silent background audio recording — Microphone listens only when you tap Speak.\n" +
          "• Calls & SMS open your Phone/Messaging draft app for your final confirmation.\n" +
          "• No Accessibility Services, no broad storage scanning, and no hidden permissions.\n" +
          "• You can inspect or revoke every permission below at any time.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    SectionHeader("Requested Device Permissions")

    PermissionCard(
      icon = Icons.Default.Mic,
      title = "Microphone (RECORD_AUDIO)",
      granted = permissions.micGranted,
      grantedLabel = "GRANTED",
      deniedLabel = "NOT GRANTED",
      why = "Captures your spoken command in the foreground via Android SpeechRecognizer. Speech Recognition service on this device: ${if (permissions.speechRecognitionAvailable) "Available" else "Unavailable (use Text Mode)"}.",
      primaryActionLabel = if (permissions.micGranted) "Manage / Revoke in Settings" else "Grant Microphone",
      onAction = {
        if (permissions.micGranted) {
          onOpenAppSettings()
        } else {
          micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
      },
      testTag = "perm_mic_button",
    )

    PermissionCard(
      icon = Icons.Default.NotificationsActive,
      title = "Notification Access (Optional)",
      granted = permissions.notificationListenerGranted,
      grantedLabel = "ENABLED",
      deniedLabel = "DISABLED",
      why = "Lets PRINCE AI read unread notifications aloud ONLY when you ask. You can revoke it in Android Notification Access settings.",
      primaryActionLabel = if (permissions.notificationListenerGranted) "Manage / Revoke Access" else "Enable Notification Access",
      onAction = onOpenNotificationAccess,
      testTag = "perm_notification_button",
    )

    PermissionCard(
      icon = Icons.Default.Tune,
      title = "Modify System Settings (Optional)",
      granted = permissions.writeSettingsGranted,
      grantedLabel = "ALLOWED",
      deniedLabel = "NOT ALLOWED",
      why = "Needed only if you ask PRINCE AI to change screen brightness directly. Volume adjustments work via Android AudioManager.",
      primaryActionLabel = if (permissions.writeSettingsGranted) "Manage / Revoke Access" else "Grant System Settings",
      onAction = onOpenWriteSettings,
      testTag = "perm_write_settings_button",
    )

    PermissionCard(
      icon = Icons.Default.FlashlightOn,
      title = "Camera Torch LED (Hardware)",
      granted = permissions.hasFlashlightHardware,
      grantedLabel = if (permissions.flashlightEnabled) "READY (TORCH ON)" else "HARDWARE READY",
      deniedLabel = "NO FLASHLIGHT HARDWARE",
      why = "Uses Android CameraManager.setTorchMode() without opening camera preview or capturing photos.",
      primaryActionLabel = "App System Details",
      onAction = onOpenAppSettings,
      testTag = "perm_flashlight_button",
    )

    StudioGlassCard {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Settings, contentDescription = null, tint = RoyalGold)
        Spacer(Modifier.width(8.dp))
        Text("Intent-Based Actions (Zero Extra Permissions)", style = MaterialTheme.typography.titleSmall, color = RoyalGold)
      }
      Spacer(Modifier.height(6.dp))
      Text(
        "Detected ${permissions.installableAppCount} launchable apps via narrow MAIN/LAUNCHER intent queries. Alarms, Timers, Calendar reminders, Web Search, Settings pages, Phone Dialer drafts, and SMS drafts use official Android system intents safely.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(10.dp))
      OutlinedButton(
        onClick = onOpenAppSettings,
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("perm_open_app_settings_button"),
      ) {
        Text("Open Android App Permission Settings")
      }
    }
    Spacer(Modifier.height(16.dp))
  }
}

@Composable
private fun PermissionCard(
  icon: ImageVector,
  title: String,
  granted: Boolean,
  grantedLabel: String,
  deniedLabel: String,
  why: String,
  primaryActionLabel: String,
  onAction: () -> Unit,
  testTag: String,
) {
  StudioGlassCard(borderColor = if (granted) EmeraldSuccess.copy(alpha = 0.5f) else AmberWarning.copy(alpha = 0.45f)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Icon(icon, contentDescription = null, tint = if (granted) EmeraldSuccess else AmberWarning, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
      }
      StatusPill(
        text = if (granted) grantedLabel else deniedLabel,
        color = if (granted) EmeraldSuccess else AmberWarning,
      )
    }
    Spacer(Modifier.height(6.dp))
    Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
    Button(
      onClick = onAction,
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag(testTag),
    ) {
      Text(primaryActionLabel)
    }
  }
}
