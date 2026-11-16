package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.MediaExportHelper
import com.example.data.model.ChatTurn
import com.example.data.model.SystemConfig
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudioGlassCard
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.viewmodel.StudioOpState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScriptAssistantScreen(
  config: SystemConfig,
  opState: StudioOpState<String>,
  chatTurns: List<ChatTurn>,
  onSendMessage: (mode: String, prompt: String) -> Unit,
  onClearChat: () -> Unit,
) {
  val context = LocalContext.current
  val modes = listOf(
    "Video Script",
    "Documentary Story",
    "Image Prompt",
    "Video Prompt",
    "Narration Polish",
    "Scene Organizer",
  )
  var selectedMode by remember { mutableStateOf(modes.first()) }
  var prompt by remember { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    SectionHeader(
      title = "AI Script & Chat Assistant",
      subtitle = "Powered by Gemini 3.8 Flash • Streaming Creative Co-Pilot",
      trailing = {
        if (chatTurns.isNotEmpty()) {
          IconButton(onClick = onClearChat) {
            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear chat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      },
    )
    Spacer(Modifier.height(8.dp))

    if (!config.scriptStudioEnabled) {
      StudioGlassCard(borderColor = CrimsonError) {
        Text(
          "AI Script Assistant is currently disabled by the Administrator.",
          style = MaterialTheme.typography.titleSmall,
          color = CrimsonError,
        )
      }
      return
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      modes.forEach { mode ->
        FilterChip(
          selected = selectedMode == mode,
          onClick = { selectedMode = mode },
          label = { Text(mode) },
        )
      }
    }
    Spacer(Modifier.height(8.dp))

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (chatTurns.isEmpty() && opState is StudioOpState.Idle) {
        item {
          StudioGlassCard(borderColor = EmeraldSuccess.copy(alpha = 0.45f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = EmeraldSuccess)
              Spacer(Modifier.width(8.dp))
              Text("Ready in $selectedMode Mode", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(
              "Ask PRINCE AI to write scripts, build documentary arcs, engineer image/video prompts, polish narration, or organize scenes.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      items(chatTurns) { turn ->
        val isUser = turn.role == "user"
        StudioGlassCard(
          containerColor = if (isUser) CyanContainer.copy(alpha = 0.55f) else CardSurface,
          borderColor = if (isUser) ElectricCyan.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = if (isUser) "You" else "PRINCE AI",
              style = MaterialTheme.typography.labelMedium,
              color = if (isUser) ElectricCyan else EmeraldSuccess,
            )
            if (!isUser) {
              IconButton(
                onClick = { MediaExportHelper.shareText(context, selectedMode, turn.text) },
                modifier = Modifier.size(36.dp),
              ) {
                Icon(Icons.Default.Share, contentDescription = "Share response", modifier = Modifier.size(16.dp))
              }
            }
          }
          Spacer(Modifier.height(4.dp))
          Text(turn.text, style = MaterialTheme.typography.bodyMedium)
        }
      }

      if (opState is StudioOpState.Loading) {
        item {
          StudioGlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
              Spacer(Modifier.width(10.dp))
              Text("Thinking with Gemini 3.8 Flash...", style = MaterialTheme.typography.bodySmall)
            }
          }
        }
      } else if (opState is StudioOpState.Streaming) {
        item {
          StudioGlassCard(borderColor = EmeraldSuccess.copy(alpha = 0.5f)) {
            Text("PRINCE AI (Streaming...)", style = MaterialTheme.typography.labelMedium, color = EmeraldSuccess)
            Spacer(Modifier.height(4.dp))
            Text(opState.text, style = MaterialTheme.typography.bodyMedium)
          }
        }
      } else if (opState is StudioOpState.Error) {
        item {
          StudioGlassCard(borderColor = CrimsonError) {
            Text(opState.message, style = MaterialTheme.typography.bodySmall, color = CrimsonError)
          }
        }
      }
    }

    Spacer(Modifier.height(8.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = prompt,
        onValueChange = { prompt = it },
        placeholder = { Text("Ask for a $selectedMode...") },
        maxLines = 4,
        modifier = Modifier
          .weight(1f)
          .testTag("script_prompt_input"),
      )
      Spacer(Modifier.width(8.dp))
      IconButton(
        onClick = {
          val text = prompt.trim()
          if (text.isNotEmpty()) {
            prompt = ""
            onSendMessage(selectedMode, text)
          }
        },
        enabled = prompt.isNotBlank() && opState !is StudioOpState.Loading && opState !is StudioOpState.Streaming,
        modifier = Modifier
          .size(48.dp)
          .testTag("send_script_button"),
      ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send prompt", tint = ElectricCyan)
      }
    }
  }
}
