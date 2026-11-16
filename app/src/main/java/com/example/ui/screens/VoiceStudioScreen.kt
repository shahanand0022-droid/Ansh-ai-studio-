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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.MediaExportHelper
import com.example.core.NarrationLanguage
import com.example.core.VoiceEngineState
import com.example.data.model.SystemConfig
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioGlassCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.PlasmaViolet
import java.io.File

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VoiceStudioScreen(
  config: SystemConfig,
  voiceState: VoiceEngineState,
  scriptDraft: String,
  isWritingScript: Boolean,
  onScriptDraftChange: (String) -> Unit,
  onSelectLanguage: (NarrationLanguage) -> Unit,
  onSelectVoice: (String) -> Unit,
  onGenerateAiNarration: (topic: String, language: NarrationLanguage, style: String) -> Unit,
  onPreview: (text: String, rate: Float, pitch: Float) -> Unit,
  onSynthesizeWav: (text: String, language: NarrationLanguage, rate: Float, pitch: Float) -> Unit,
  onPlayFile: (File) -> Unit,
  onStop: () -> Unit,
  onShowToast: (String) -> Unit,
) {
  val context = LocalContext.current
  var selectedLanguage by remember { mutableStateOf(NarrationLanguage.ENGLISH) }
  var narrationStyle by remember { mutableStateOf("Documentary") }
  var aiTopic by remember { mutableStateOf("") }
  var speechRate by remember { mutableFloatStateOf(1.0f) }
  var pitch by remember { mutableFloatStateOf(1.0f) }
  var voiceDropdownExpanded by remember { mutableStateOf(false) }

  val styles = listOf("Documentary", "Calm Storyteller", "Energetic Short", "News Brief")

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    SectionHeader(
      title = "AI Voice Studio",
      subtitle = "Multilingual TTS narration (English, Hindi, Nepali) & WAV export",
    )

    if (!config.voiceStudioEnabled) {
      StudioGlassCard(borderColor = CrimsonError) {
        Text(
          "AI Voice Studio is currently disabled by the Administrator.",
          style = MaterialTheme.typography.titleSmall,
          color = CrimsonError,
        )
      }
      return
    }

    StudioGlassCard(borderColor = PlasmaViolet.copy(alpha = 0.45f)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.GraphicEq, contentDescription = null, tint = PlasmaViolet)
          Spacer(Modifier.width(8.dp))
          Text("Speech Engine", style = MaterialTheme.typography.titleSmall)
        }
        StatusPill(
          text = if (voiceState.languageAvailable) "VOICE READY" else "PACK MISSING",
          color = if (voiceState.languageAvailable) EmeraldSuccess else AmberWarning,
        )
      }
      Spacer(Modifier.height(6.dp))
      Text(
        text = voiceState.statusMessage,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(10.dp))

      Text("Narration Language", style = MaterialTheme.typography.labelLarge)
      Spacer(Modifier.height(6.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NarrationLanguage.entries.forEach { lang ->
          FilterChip(
            selected = selectedLanguage == lang,
            onClick = {
              selectedLanguage = lang
              onSelectLanguage(lang)
            },
            label = { Text(lang.displayName) },
          )
        }
      }

      if (voiceState.availableVoices.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        ExposedDropdownMenuBox(
          expanded = voiceDropdownExpanded,
          onExpandedChange = { voiceDropdownExpanded = !voiceDropdownExpanded },
        ) {
          OutlinedTextField(
            value = voiceState.selectedVoiceName ?: "Default Voice",
            onValueChange = {},
            readOnly = true,
            label = { Text("Installed Device Voice") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceDropdownExpanded) },
            modifier = Modifier
              .menuAnchor()
              .fillMaxWidth(),
          )
          ExposedDropdownMenu(
            expanded = voiceDropdownExpanded,
            onDismissRequest = { voiceDropdownExpanded = false },
          ) {
            voiceState.availableVoices.take(15).forEach { voiceName ->
              DropdownMenuItem(
                text = { Text(voiceName) },
                onClick = {
                  onSelectVoice(voiceName)
                  voiceDropdownExpanded = false
                },
              )
            }
          }
        }
      }

      Spacer(Modifier.height(10.dp))
      Text("Speed: ${"%.2f".format(speechRate)}x", style = MaterialTheme.typography.labelMedium)
      Slider(value = speechRate, onValueChange = { speechRate = it }, valueRange = 0.6f..1.6f)
      Text("Pitch: ${"%.2f".format(pitch)}x", style = MaterialTheme.typography.labelMedium)
      Slider(value = pitch, onValueChange = { pitch = it }, valueRange = 0.7f..1.4f)
    }

    // AI Narration Script Generator
    StudioGlassCard {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ElectricCyan)
        Spacer(Modifier.width(8.dp))
        Text("AI Narration Script Writer (Gemini)", style = MaterialTheme.typography.titleSmall)
      }
      Spacer(Modifier.height(8.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        styles.forEach { style ->
          FilterChip(
            selected = narrationStyle == style,
            onClick = { narrationStyle = style },
            label = { Text(style) },
          )
        }
      }
      Spacer(Modifier.height(8.dp))
      OutlinedTextField(
        value = aiTopic,
        onValueChange = { aiTopic = it },
        label = { Text("Narration Topic") },
        placeholder = { Text("e.g., History of Mount Everest in inspiring documentary tone") },
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(8.dp))
      OutlinedButton(
        onClick = { onGenerateAiNarration(aiTopic, selectedLanguage, narrationStyle) },
        enabled = !isWritingScript && aiTopic.isNotBlank(),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp),
      ) {
        if (isWritingScript) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          Spacer(Modifier.width(8.dp))
          Text("Writing Narration...")
        } else {
          Text("Generate ${selectedLanguage.displayName} Narration Script")
        }
      }
    }

    // Main Voiceover Input & Audio Controls
    StudioGlassCard {
      OutlinedTextField(
        value = scriptDraft,
        onValueChange = onScriptDraftChange,
        label = { Text("Voiceover Text") },
        placeholder = { Text("Type or generate narration text to speak & synthesize...") },
        minLines = 4,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("voice_text_input"),
      )
      Spacer(Modifier.height(12.dp))

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = {
            if (voiceState.isSpeaking || voiceState.isPlayingFile) {
              onStop()
            } else {
              onPreview(scriptDraft, speechRate, pitch)
            }
          },
          enabled = scriptDraft.isNotBlank(),
          modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .testTag("preview_voice_button"),
        ) {
          Icon(
            if (voiceState.isSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
          )
          Spacer(Modifier.width(6.dp))
          Text(if (voiceState.isSpeaking) "Stop" else "Preview")
        }

        Button(
          onClick = { onSynthesizeWav(scriptDraft, selectedLanguage, speechRate, pitch) },
          enabled = scriptDraft.isNotBlank() && !voiceState.isSynthesizing,
          modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .testTag("synthesize_voice_button"),
        ) {
          Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text("Synthesize WAV")
        }
      }

      val lastFile = voiceState.lastSynthesizedFile
      if (lastFile != null && lastFile.exists()) {
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { onPlayFile(lastFile) },
            modifier = Modifier
              .weight(1f)
              .height(48.dp),
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Play WAV")
          }
          OutlinedButton(
            onClick = {
              val res = MediaExportHelper.exportWavToDownloads(context, lastFile, "prince_voice_${selectedLanguage.code}")
              onShowToast(res.getOrElse { it.localizedMessage ?: "Export failed" })
            },
            modifier = Modifier
              .weight(1f)
              .height(48.dp),
          ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Save WAV")
          }
          OutlinedButton(
            onClick = { MediaExportHelper.shareFile(context, lastFile, "audio/wav", "Share Narration Audio") },
            modifier = Modifier.height(48.dp),
          ) {
            Icon(Icons.Default.Share, contentDescription = "Share WAV")
          }
        }
      }
    }
    Spacer(Modifier.height(16.dp))
  }
}
