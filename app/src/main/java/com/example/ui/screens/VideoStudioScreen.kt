package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.model.SystemConfig
import com.example.data.model.VideoProductionConfig
import com.example.data.model.VideoScene
import com.example.data.model.VideoStoryboard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioGlassCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoyalGold
import com.example.ui.viewmodel.StudioOpState
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoStudioScreen(
  config: SystemConfig,
  opState: StudioOpState<VideoStoryboard>,
  sceneKeyframes: Map<Int, Bitmap>,
  generatingKeyframeScene: Int?,
  prodConfig: VideoProductionConfig,
  seedBitmap: Bitmap?,
  videoBuildProgress: Float?,
  videoBuildStatus: String?,
  assembledVideoFile: File?,
  onSetSeedBitmap: (Bitmap?) -> Unit,
  onGenerateEnhancedPlan: (VideoProductionConfig, topicOrScript: String, languageName: String) -> Unit,
  onUpdateScene: (sceneNumber: Int, VideoScene) -> Unit,
  onGenerateSceneKeyframe: (sceneNumber: Int, visualPrompt: String) -> Unit,
  onAssembleMp4: () -> Unit,
  onSendNarrationToVoice: (String) -> Unit,
  onShowMessage: (String) -> Unit = {},
) {
  val context = LocalContext.current
  val sourceModes = listOf("Text-to-Video", "Image-to-Video", "Script-to-Video")
  val durations = listOf(15, 30, 60, 90, 120)
  val aspectRatios = listOf("16:9 Widescreen", "9:16 Vertical", "1:1 Square", "4:3 Documentary")
  val resolutions = listOf("720p HD", "1080p Full HD")
  val visualStyles = listOf("Cinematic Documentary", "Anime / Stylized", "3D Isometric", "Photorealistic", "YouTube Explainer")
  val languages = listOf("English", "Hindi", "Nepali")

  var selectedMode by remember(prodConfig.sourceMode) { mutableStateOf(prodConfig.sourceMode) }
  var selectedDuration by remember(prodConfig.durationSeconds, config.maxVideoDurationSeconds) {
    mutableIntStateOf(prodConfig.durationSeconds.coerceAtMost(config.maxVideoDurationSeconds.coerceAtLeast(15)))
  }
  var selectedAspect by remember(prodConfig.aspectRatioLabel) { mutableStateOf(prodConfig.aspectRatioLabel) }
  var selectedResolution by remember(prodConfig.resolutionLabel) { mutableStateOf(prodConfig.resolutionLabel) }
  var selectedStyle by remember(prodConfig.visualStyle) { mutableStateOf(prodConfig.visualStyle) }
  var selectedLanguage by remember { mutableStateOf(languages.first()) }
  var topicOrScript by remember { mutableStateOf("") }
  var editingSceneNum by remember { mutableIntStateOf(-1) }

  val imagePickerLauncher = rememberLauncherForActivityResult(
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
          val s = 1024f / maxOf(bmp.width, bmp.height)
          Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
        } else {
          bmp
        }
        onSetSeedBitmap(scaled)
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    SectionHeader(
      title = "AI Video Studio",
      subtitle = "Multi-scene 15s–${config.maxVideoDurationSeconds}s Storyboards, Keyframes & MP4 Composer",
    )

    // Honest Provider Availability Card
    StudioGlassCard(borderColor = AmberWarning.copy(alpha = 0.55f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Info, contentDescription = null, tint = AmberWarning)
        Spacer(Modifier.width(8.dp))
        Text("Video Provider & Pipeline Status", style = MaterialTheme.typography.titleSmall, color = AmberWarning)
      }
      Spacer(Modifier.height(8.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusPill("Gemini Multi-Scene Planner: ACTIVE", EmeraldSuccess)
        StatusPill("Nano Banana 2.1 Keyframes: ACTIVE", ElectricCyan)
        StatusPill("On-Device MediaCodec MP4: ACTIVE", EmeraldSuccess)
        StatusPill(
          text = if (config.videoProviderConfigured) "Cloud Veo Endpoint: CONFIGURED" else "Cloud Veo Neural Motion: NOT CONFIGURED",
          color = if (config.videoProviderConfigured) EmeraldSuccess else AmberWarning,
        )
      }
      Spacer(Modifier.height(6.dp))
      Text(
        text = "Generates 15s–${config.maxVideoDurationSeconds}s videos reliably by composing sequential AI-illustrated scenes with smooth Ken Burns camera motion and synchronized captions into standard MP4 via Android MediaCodec. Direct neural cloud rendering (Veo) is disabled until an Admin cloud endpoint is attached.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    if (!config.videoStudioEnabled) {
      StudioGlassCard(borderColor = CrimsonError) {
        Text(
          "AI Video Studio is currently disabled by the Administrator.",
          style = MaterialTheme.typography.titleSmall,
          color = CrimsonError,
        )
      }
      return
    }

    StudioGlassCard {
      Text("Creation Mode", style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sourceModes.forEach { m ->
          FilterChip(
            selected = selectedMode == m,
            onClick = { selectedMode = m },
            label = { Text(m) },
          )
        }
      }

      if (selectedMode == "Image-to-Video") {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedButton(
            onClick = { imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.height(48.dp),
          ) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (seedBitmap == null) "Pick Seed Photo" else "Change Seed Photo")
          }
          if (seedBitmap != null) {
            Image(
              bitmap = seedBitmap.asImageBitmap(),
              contentDescription = "Seed reference photo",
              modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small),
              contentScale = ContentScale.Crop,
            )
            IconButton(onClick = { onSetSeedBitmap(null) }) {
              Icon(Icons.Default.Close, contentDescription = "Clear seed image")
            }
          }
        }
      }

      Spacer(Modifier.height(8.dp))
      Text("Duration (Admin cap: ${config.maxVideoDurationSeconds}s max)", style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        durations.forEach { sec ->
          val allowed = sec <= config.maxVideoDurationSeconds
          FilterChip(
            selected = selectedDuration == sec,
            enabled = allowed,
            onClick = { selectedDuration = sec },
            label = { Text("${sec}s") },
            modifier = Modifier.testTag("video_duration_${sec}s"),
          )
        }
      }

      Spacer(Modifier.height(8.dp))
      Text("Aspect Ratio & Resolution", style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        aspectRatios.forEach { ratio ->
          FilterChip(
            selected = selectedAspect == ratio,
            onClick = { selectedAspect = ratio },
            label = { Text(ratio) },
          )
        }
        resolutions.forEach { res ->
          FilterChip(
            selected = selectedResolution == res,
            onClick = { selectedResolution = res },
            label = { Text(res) },
          )
        }
      }

      Spacer(Modifier.height(8.dp))
      Text("Visual Style & Language", style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        visualStyles.forEach { style ->
          FilterChip(
            selected = selectedStyle == style,
            onClick = { selectedStyle = style },
            label = { Text(style) },
          )
        }
        languages.forEach { lang ->
          FilterChip(
            selected = selectedLanguage == lang,
            onClick = { selectedLanguage = lang },
            label = { Text(lang) },
          )
        }
      }

      Spacer(Modifier.height(10.dp))
      OutlinedTextField(
        value = topicOrScript,
        onValueChange = { topicOrScript = it },
        label = {
          Text(
            when (selectedMode) {
              "Script-to-Video" -> "Paste Full Script or Scene Narration"
              "Image-to-Video" -> "Describe Motion & Story Around Your Seed Image"
              else -> "Video Concept / Documentary Prompt"
            },
          )
        },
        placeholder = { Text("e.g., Cinematic 60-second journey across the Himalayas showing sunrise, monasteries, and glowing stars") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth().testTag("video_topic_input"),
      )

      Spacer(Modifier.height(12.dp))
      Button(
        onClick = {
          val cfg = VideoProductionConfig(
            sourceMode = selectedMode,
            aspectRatioLabel = selectedAspect,
            resolutionLabel = selectedResolution,
            visualStyle = selectedStyle,
            durationSeconds = selectedDuration,
          )
          onGenerateEnhancedPlan(cfg, topicOrScript, selectedLanguage)
        },
        enabled = opState !is StudioOpState.Loading && topicOrScript.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("generate_video_plan_button"),
      ) {
        if (opState is StudioOpState.Loading) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          Spacer(Modifier.width(8.dp))
          Text("Generating ${selectedDuration}s Multi-Scene Plan...")
        } else {
          Icon(Icons.Default.MovieCreation, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Generate ${selectedDuration}s Multi-Scene Video Plan")
        }
      }
    }

    when (opState) {
      is StudioOpState.Error -> {
        StudioGlassCard(borderColor = CrimsonError) {
          Text(opState.message, style = MaterialTheme.typography.bodySmall, color = CrimsonError)
        }
      }
      is StudioOpState.Success -> {
        val board = opState.data
        StudioGlassCard(borderColor = ElectricCyan.copy(alpha = 0.55f)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(board.title, style = MaterialTheme.typography.titleLarge, color = ElectricCyan)
              Text("${board.format} • ${board.scenes.size} scenes • ${board.totalDurationSeconds}s total", style = MaterialTheme.typography.labelSmall, color = RoyalGold)
            }
            OutlinedButton(
              onClick = {
                val exportText = buildString {
                  appendLine(board.title)
                  appendLine(board.logline)
                  appendLine()
                  board.scenes.forEach { s ->
                    appendLine("Scene ${s.sceneNumber} (${s.durationSeconds}s) - ${s.cameraDirection}")
                    appendLine("Visual: ${s.visualPrompt}")
                    appendLine("Narration: ${s.narrationLine}")
                    appendLine("Audio: ${s.audioCue}\n")
                  }
                }
                MediaExportHelper.shareText(context, board.title, exportText)
              },
              modifier = Modifier.height(48.dp),
            ) {
              Icon(Icons.Default.Share, contentDescription = "Share script", modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Share")
            }
          }
          Spacer(Modifier.height(6.dp))
          Text(board.logline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.height(10.dp))

          if (videoBuildProgress != null) {
            LinearProgressIndicator(
              progress = { videoBuildProgress.coerceIn(0f, 1f) },
              modifier = Modifier.fillMaxWidth(),
              color = ElectricCyan,
            )
            Spacer(Modifier.height(6.dp))
            Text(videoBuildStatus.orEmpty(), style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
          } else {
            Button(
              onClick = onAssembleMp4,
              colors = ButtonDefaults.buttonColors(containerColor = RoyalGold, contentColor = ObsidianBg),
              modifier = Modifier.fillMaxWidth().height(50.dp).testTag("build_video_mp4_button"),
            ) {
              Icon(Icons.Default.VideoFile, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text("Render & Combine Scenes into ${board.totalDurationSeconds}s MP4")
            }
          }
        }

        if (assembledVideoFile != null && assembledVideoFile.exists()) {
          StudioGlassCard(borderColor = EmeraldSuccess) {
            Text("Rendered MP4 Video Preview", style = MaterialTheme.typography.titleMedium, color = EmeraldSuccess)
            Spacer(Modifier.height(8.dp))
            AndroidView(
              factory = { ctx ->
                VideoView(ctx).apply {
                  val mc = MediaController(ctx)
                  setMediaController(mc)
                  mc.setAnchorView(this)
                  setVideoURI(Uri.fromFile(assembledVideoFile))
                  setOnPreparedListener { mp ->
                    mp.isLooping = true
                    start()
                  }
                }
              },
              modifier = Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.medium),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(
                onClick = {
                  MediaExportHelper.exportMp4ToMovies(context, assembledVideoFile, board.title)
                    .onSuccess { onShowMessage(it) }
                    .onFailure { onShowMessage(it.localizedMessage ?: "Export failed.") }
                },
                modifier = Modifier.weight(1f).height(48.dp).testTag("download_video_mp4_button"),
              ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save to Movies")
              }
              OutlinedButton(
                onClick = { MediaExportHelper.shareFile(context, assembledVideoFile, "video/mp4", "Share Video") },
                modifier = Modifier.weight(1f).height(48.dp),
              ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share MP4")
              }
            }
          }
        }

        board.scenes.forEach { scene ->
          val isEditing = editingSceneNum == scene.sceneNumber
          StudioGlassCard {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Scene ${scene.sceneNumber}", style = MaterialTheme.typography.titleMedium, color = RoyalGold)
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill("${scene.durationSeconds}s", ElectricCyan)
                IconButton(
                  onClick = { editingSceneNum = if (isEditing) -1 else scene.sceneNumber },
                  modifier = Modifier.size(36.dp),
                ) {
                  Icon(Icons.Default.Edit, contentDescription = "Edit scene ${scene.sceneNumber}", tint = ElectricCyan, modifier = Modifier.size(18.dp))
                }
              }
            }

            if (isEditing) {
              var editPrompt by remember(scene.visualPrompt) { mutableStateOf(scene.visualPrompt) }
              var editNarration by remember(scene.narrationLine) { mutableStateOf(scene.narrationLine) }
              var editDur by remember(scene.durationSeconds) { mutableStateOf(scene.durationSeconds.toString()) }
              Spacer(Modifier.height(6.dp))
              OutlinedTextField(value = editPrompt, onValueChange = { editPrompt = it }, label = { Text("Visual Prompt") }, modifier = Modifier.fillMaxWidth())
              Spacer(Modifier.height(6.dp))
              OutlinedTextField(value = editNarration, onValueChange = { editNarration = it }, label = { Text("Narration Line") }, modifier = Modifier.fillMaxWidth())
              Spacer(Modifier.height(6.dp))
              OutlinedTextField(value = editDur, onValueChange = { editDur = it }, label = { Text("Duration (seconds)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
              Spacer(Modifier.height(8.dp))
              Button(
                onClick = {
                  onUpdateScene(
                    scene.sceneNumber,
                    scene.copy(
                      visualPrompt = editPrompt,
                      narrationLine = editNarration,
                      durationSeconds = (editDur.toIntOrNull() ?: scene.durationSeconds).coerceIn(3, 25),
                    ),
                  )
                  editingSceneNum = -1
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
              ) {
                Text("Save Scene Changes")
              }
            } else {
              Spacer(Modifier.height(4.dp))
              Text("Camera: ${scene.cameraDirection}", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
              Spacer(Modifier.height(4.dp))
              Text("Visual: ${scene.visualPrompt}", style = MaterialTheme.typography.bodySmall)
              Spacer(Modifier.height(4.dp))
              Text("Narration: \"${scene.narrationLine}\"", style = MaterialTheme.typography.bodySmall)
              Spacer(Modifier.height(4.dp))
              Text("Audio Cue: ${scene.audioCue}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val keyframe = sceneKeyframes[scene.sceneNumber]
            if (keyframe != null) {
              Spacer(Modifier.height(10.dp))
              Image(
                bitmap = keyframe.asImageBitmap(),
                contentDescription = "Scene ${scene.sceneNumber} keyframe",
                modifier = Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop,
              )
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = { onGenerateSceneKeyframe(scene.sceneNumber, scene.visualPrompt) },
                enabled = generatingKeyframeScene == null && videoBuildProgress == null,
                modifier = Modifier.weight(1f).height(48.dp),
              ) {
                if (generatingKeyframeScene == scene.sceneNumber) {
                  CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                  Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(if (keyframe != null) "Regen Frame" else "Keyframe")
              }
              OutlinedButton(
                onClick = { onSendNarrationToVoice(scene.narrationLine) },
                modifier = Modifier.weight(1f).height(48.dp),
              ) {
                Text("To Voice Studio")
              }
            }
          }
        }
      }
      else -> Unit
    }
    Spacer(Modifier.height(16.dp))
  }
}
