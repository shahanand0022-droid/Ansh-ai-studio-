package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.NarrationLanguage
import com.example.core.SpeechCaptureState
import com.example.core.VoiceEngineState
import com.example.data.model.AssistantNote
import com.example.data.model.AssistantTurn
import com.example.data.model.SystemConfig
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioGlassCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.DeepSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PlasmaViolet
import com.example.ui.theme.RoyalGold
import com.example.ui.viewmodel.AssistantPhase

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneAssistantScreen(
  config: SystemConfig,
  phase: AssistantPhase,
  turns: List<AssistantTurn>,
  notes: List<AssistantNote>,
  speechState: SpeechCaptureState,
  voiceState: VoiceEngineState,
  selectedLanguage: NarrationLanguage,
  voiceModeEnabled: Boolean,
  attachedImage: Bitmap?,
  statusBanner: String?,
  onStartListening: () -> Unit,
  onSubmitCommand: (String) -> Unit,
  onConfirmAction: () -> Unit,
  onCancelAction: () -> Unit,
  onStopAll: () -> Unit,
  onSelectLanguage: (NarrationLanguage) -> Unit,
  onToggleVoiceMode: (Boolean) -> Unit,
  onAttachImage: (Bitmap?) -> Unit,
  onClearConversation: () -> Unit,
  onToggleNoteCompleted: (String, Boolean) -> Unit,
  onOpenCalendarReminder: (AssistantNote) -> Unit,
  onDeleteNote: (String) -> Unit,
  onOpenPermissionsDashboard: () -> Unit,
  onClearBanner: () -> Unit,
) {
  val context = LocalContext.current
  var commandInput by remember { mutableStateOf("") }

  val micPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    if (granted) {
      onStartListening()
    }
  }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) {
      val bmp = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          val src = ImageDecoder.createSource(context.contentResolver, uri)
          ImageDecoder.decodeBitmap(src) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
        } else {
          @Suppress("DEPRECATION")
          MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
      }.getOrNull()
      if (bmp != null) {
        val scaled = if (bmp.width > 1024 || bmp.height > 1024) {
          val scale = 1024f / maxOf(bmp.width, bmp.height)
          Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        } else {
          bmp
        }
        onAttachImage(scaled)
      }
    }
  }

  val pulseTransition = rememberInfiniteTransition(label = "voice_orb_pulse")
  val orbScale by pulseTransition.animateFloat(
    initialValue = 0.96f,
    targetValue = if (phase is AssistantPhase.Listening || phase is AssistantPhase.Speaking || voiceState.isSpeaking) 1.12f else 1.0f,
    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
    label = "orb_scale",
  )

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    SectionHeader(
      title = "PRINCE AI Phone Assistant",
      subtitle = "Voice & Text Android companion with strict user confirmation",
      trailing = {
        OutlinedButton(
          onClick = onOpenPermissionsDashboard,
          modifier = Modifier.height(48.dp).testTag("assistant_open_permissions_button"),
        ) {
          Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp), tint = RoyalGold)
          Spacer(Modifier.width(6.dp))
          Text("Permissions")
        }
      },
    )

    if (!config.voiceAssistantEnabled) {
      StudioGlassCard(borderColor = CrimsonError) {
        Text(
          "Voice & Phone Assistant is currently disabled by the Administrator.",
          style = MaterialTheme.typography.titleSmall,
          color = CrimsonError,
        )
      }
      return
    }

    // Language + Spoken Reply Controls
    StudioGlassCard {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NarrationLanguage.entries.forEach { lang ->
          FilterChip(
            selected = selectedLanguage == lang,
            onClick = { onSelectLanguage(lang) },
            label = { Text(lang.displayName) },
          )
        }
      }
      Spacer(Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.VolumeUp, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Spoken Voice Replies (TTS)", style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = voiceModeEnabled, onCheckedChange = onToggleVoiceMode)
      }
    }

    // Pulsing Wake / Voice Control Orb Card
    StudioGlassCard(
      borderColor = when {
        phase is AssistantPhase.Listening -> ElectricCyan
        phase is AssistantPhase.Thinking -> PlasmaViolet
        phase is AssistantPhase.AwaitingConfirmation -> AmberWarning
        voiceState.isSpeaking || phase is AssistantPhase.Speaking -> RoyalGold
        else -> MaterialTheme.colorScheme.outline
      },
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        val statusLabel = when {
          phase is AssistantPhase.Listening -> "Listening (${selectedLanguage.code.uppercase()})..."
          phase is AssistantPhase.Thinking -> "Thinking & classifying intent..."
          phase is AssistantPhase.AwaitingConfirmation -> "Action Preview — Confirmation Required"
          voiceState.isSpeaking || phase is AssistantPhase.Speaking -> "Speaking reply..."
          else -> "Tap Mic or type any command / question"
        }
        StatusPill(
          text = statusLabel,
          color = when {
            phase is AssistantPhase.Listening -> ElectricCyan
            phase is AssistantPhase.Thinking -> PlasmaViolet
            phase is AssistantPhase.AwaitingConfirmation -> AmberWarning
            voiceState.isSpeaking -> RoyalGold
            else -> EmeraldSuccess
          },
        )
        Spacer(Modifier.height(14.dp))

        Box(
          modifier = Modifier
            .size(92.dp)
            .scale(orbScale)
            .clip(CircleShape)
            .background(
              Brush.radialGradient(
                listOf(
                  ElectricCyan.copy(alpha = 0.45f),
                  PlasmaViolet.copy(alpha = 0.25f),
                  DeepSurface,
                ),
              ),
            )
            .border(2.dp, ElectricCyan, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          FilledIconButton(
            onClick = {
              if (phase is AssistantPhase.Listening) {
                onStopAll()
              } else {
                val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (hasMic) {
                  onStartListening()
                } else {
                  micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
              }
            },
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = if (phase is AssistantPhase.Listening) CrimsonError else ElectricCyan,
              contentColor = ObsidianBg,
            ),
            modifier = Modifier.size(66.dp).testTag("assistant_listen_button"),
          ) {
            if (phase is AssistantPhase.Thinking) {
              CircularProgressIndicator(modifier = Modifier.size(28.dp), color = ObsidianBg, strokeWidth = 3.dp)
            } else if (phase is AssistantPhase.Listening) {
              Icon(Icons.Default.GraphicEq, contentDescription = "Stop listening", modifier = Modifier.size(32.dp))
            } else {
              Icon(Icons.Default.Mic, contentDescription = "Speak voice command", modifier = Modifier.size(32.dp))
            }
          }
        }

        if (speechState.partialTranscript.isNotBlank() && phase is AssistantPhase.Listening) {
          Spacer(Modifier.height(8.dp))
          Text("\"${speechState.partialTranscript}\"", style = MaterialTheme.typography.bodyMedium, color = ElectricCyan)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = onStopAll,
            modifier = Modifier.height(48.dp).testTag("assistant_stop_button"),
          ) {
            Icon(Icons.Default.Stop, contentDescription = null, tint = CrimsonError, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Stop / Interrupt")
          }
          OutlinedButton(
            onClick = onClearConversation,
            modifier = Modifier.height(48.dp),
          ) {
            Text("Clear")
          }
        }
      }
    }

    if (!statusBanner.isNullOrBlank()) {
      StudioGlassCard(borderColor = AmberWarning) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(statusBanner, style = MaterialTheme.typography.bodySmall, color = AmberWarning, modifier = Modifier.weight(1f))
          IconButton(onClick = onClearBanner, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = AmberWarning)
          }
        }
      }
    }

    // Confirmation Gate Card for Sensitive Actions (Call / SMS)
    if (phase is AssistantPhase.AwaitingConfirmation) {
      val intent = phase.intent
      StudioGlassCard(
        borderColor = AmberWarning,
        modifier = Modifier.testTag("assistant_confirmation_card"),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Warning, contentDescription = null, tint = AmberWarning)
          Spacer(Modifier.width(8.dp))
          Text("Confirm Sensitive Action", style = MaterialTheme.typography.titleMedium, color = AmberWarning)
        }
        Spacer(Modifier.height(6.dp))
        Text("Action: ${intent.type.name.replace("_", " ")}", style = MaterialTheme.typography.labelLarge, color = RoyalGold)
        Text("Recipient / Target: ${intent.target.ifBlank { "Prompt in app" }}", style = MaterialTheme.typography.bodyMedium)
        if (intent.secondaryText.isNotBlank()) {
          Text("Message Draft: \"${intent.secondaryText}\"", style = MaterialTheme.typography.bodySmall, color = ElectricCyan)
        }
        Spacer(Modifier.height(6.dp))
        Text(
          "Safety Guarantee: PRINCE AI only opens your official Dialer or SMS draft app—you must still tap Call/Send yourself.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = onConfirmAction,
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = ObsidianBg),
            modifier = Modifier.weight(1f).height(48.dp).testTag("assistant_confirm_action_button"),
          ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Confirm & Open", fontWeight = FontWeight.Bold)
          }
          OutlinedButton(
            onClick = onCancelAction,
            modifier = Modifier.weight(1f).height(48.dp).testTag("assistant_cancel_action_button"),
          ) {
            Text("Cancel")
          }
        }
      }
    }

    // Quick Action Chips
    Text("Quick Commands & Studio Navigation", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      listOf(
        "Open Wi-Fi settings",
        "Search the web for quantum computing",
        "Set alarm for 7:00 AM",
        "Create a note Buy mic stand",
        "Toggle flashlight on",
        "Read my notifications",
        "Call 555-0199",
        "Open AI Image Studio",
      ).forEach { chipCmd ->
        AssistChip(
          onClick = { onSubmitCommand(chipCmd) },
          label = { Text(chipCmd) },
        )
      }
    }

    // Text Command Input & Image Attachment
    if (attachedImage != null) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(DeepSurface).padding(8.dp),
      ) {
        Image(
          bitmap = attachedImage.asImageBitmap(),
          contentDescription = "Attached visual context",
          modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
          contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(8.dp))
        Text("Photo ready for Assistant Q&A", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        IconButton(onClick = { onAttachImage(null) }) {
          Icon(Icons.Default.Close, contentDescription = "Remove image")
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      IconButton(
        onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        modifier = Modifier.size(48.dp),
      ) {
        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Upload photo for assistant", tint = RoyalGold)
      }
      OutlinedTextField(
        value = commandInput,
        onValueChange = { commandInput = it },
        label = { Text("Type voice command or question...") },
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("assistant_text_input"),
      )
      FilledIconButton(
        onClick = {
          if (commandInput.isNotBlank()) {
            val txt = commandInput
            commandInput = ""
            onSubmitCommand(txt)
          }
        },
        enabled = commandInput.isNotBlank() && phase !is AssistantPhase.Thinking,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
        modifier = Modifier.size(48.dp).testTag("assistant_send_command_button"),
      ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send command")
      }
    }

    // Conversation Log
    if (turns.isNotEmpty()) {
      SectionHeader("Assistant Transcript", "${turns.size} turns")
      turns.reversed().take(12).forEach { turn ->
        val isUser = turn.role == "user"
        StudioGlassCard(
          borderColor = if (isUser) ElectricCyan.copy(alpha = 0.4f) else RoyalGold.copy(alpha = 0.4f),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(
                imageVector = if (isUser) Icons.Default.Mic else Icons.Default.RecordVoiceOver,
                contentDescription = null,
                tint = if (isUser) ElectricCyan else RoyalGold,
                modifier = Modifier.size(16.dp),
              )
              Text(if (isUser) "You" else "PRINCE AI", style = MaterialTheme.typography.labelLarge, color = if (isUser) ElectricCyan else RoyalGold)
            }
            if (turn.actionType.isNotBlank()) {
              StatusPill(turn.actionType.replace("_", " "), EmeraldSuccess)
            }
          }
          Spacer(Modifier.height(4.dp))
          Text(turn.text, style = MaterialTheme.typography.bodyMedium)
          if (turn.actionSummary.isNotBlank() && turn.actionSummary != turn.text) {
            Spacer(Modifier.height(4.dp))
            Text("Action: ${turn.actionSummary}", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
          }
        }
      }
    }

    // Saved Notes & Reminders
    SectionHeader("Saved Notes & Reminders (${notes.size})", "Created by voice commands & persisted privately")
    if (notes.isEmpty()) {
      StudioGlassCard {
        Text(
          "No notes or reminders yet. Say or type \"Create a note...\" or \"Remind me to...\" to save one.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      notes.forEach { note ->
        StudioGlassCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = note.isCompleted,
              onCheckedChange = { onToggleNoteCompleted(note.id, it) },
            )
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(note.title, style = MaterialTheme.typography.titleSmall)
                StatusPill(note.kind.uppercase(), if (note.kind == "reminder") AmberWarning else ElectricCyan)
              }
              if (note.content.isNotBlank()) {
                Text(note.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              if (note.dueLabel.isNotBlank()) {
                Text("When: ${note.dueLabel}", style = MaterialTheme.typography.labelSmall, color = RoyalGold)
              }
            }
            if (note.kind == "reminder") {
              IconButton(onClick = { onOpenCalendarReminder(note) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.CalendarToday, contentDescription = "Add to Calendar app", tint = ElectricCyan)
              }
            }
            IconButton(onClick = { onDeleteNote(note.id) }, modifier = Modifier.size(40.dp)) {
              Icon(Icons.Default.Delete, contentDescription = "Delete note", tint = CrimsonError)
            }
          }
        }
      }
    }
    Spacer(Modifier.height(16.dp))
  }
}
