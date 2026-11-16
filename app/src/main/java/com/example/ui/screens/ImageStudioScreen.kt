package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.example.core.MediaExportHelper
import com.example.data.model.StudioConstants
import com.example.data.model.StudioProject
import com.example.data.model.SystemConfig
import com.example.data.repository.StudioAspectRatio
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudioGlassCard
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.RoyalGold
import com.example.ui.viewmodel.GeneratedImageResult
import com.example.ui.viewmodel.StudioOpState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageStudioScreen(
  config: SystemConfig,
  opState: StudioOpState<GeneratedImageResult>,
  recentProjects: List<StudioProject>,
  loadBitmap: (String) -> android.graphics.Bitmap?,
  onGenerateImage: (prompt: String, ratio: StudioAspectRatio, styleMode: String) -> Unit,
  onShowToast: (String) -> Unit,
) {
  val context = LocalContext.current
  val modes = listOf("Text-to-Image", "AI Thumbnail", "Documentary Visual", "Creative Art")
  var selectedMode by remember { mutableStateOf(modes.first()) }
  var selectedRatio by remember { mutableStateOf(StudioAspectRatio.SQUARE) }
  var prompt by remember { mutableStateOf("") }

  val imageHistory = remember(recentProjects) {
    recentProjects.filter { it.type == StudioConstants.TYPE_IMAGE }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    SectionHeader(
      title = "AI Image Studio",
      subtitle = "Powered by Gemini Nano Banana 2.1 • Thumbnails, Documentary & Art",
    )

    if (!config.imageStudioEnabled) {
      StudioGlassCard(borderColor = CrimsonError) {
        Text(
          "AI Image Studio is currently disabled by the Administrator.",
          style = MaterialTheme.typography.titleSmall,
          color = CrimsonError,
        )
      }
      return
    }

    StudioGlassCard(borderColor = RoyalGold.copy(alpha = 0.45f)) {
      Text("Creation Mode", style = MaterialTheme.typography.labelLarge)
      Spacer(Modifier.height(6.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.forEach { mode ->
          FilterChip(
            selected = selectedMode == mode,
            onClick = {
              selectedMode = mode
              if (mode == "AI Thumbnail") selectedRatio = StudioAspectRatio.THUMBNAIL
            },
            label = { Text(mode) },
          )
        }
      }

      Spacer(Modifier.height(10.dp))
      Text("Aspect Ratio", style = MaterialTheme.typography.labelLarge)
      Spacer(Modifier.height(6.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StudioAspectRatio.entries.forEach { ratio ->
          FilterChip(
            selected = selectedRatio == ratio,
            onClick = { selectedRatio = ratio },
            label = { Text(ratio.label) },
          )
        }
      }

      Spacer(Modifier.height(10.dp))
      OutlinedTextField(
        value = prompt,
        onValueChange = { prompt = it },
        label = { Text("Image Prompt") },
        placeholder = { Text("e.g., Dramatic golden hour aerial view of snow-capped peaks, 8K documentary") },
        minLines = 3,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("image_prompt_input"),
      )

      Spacer(Modifier.height(12.dp))
      Button(
        onClick = { onGenerateImage(prompt, selectedRatio, selectedMode) },
        enabled = opState !is StudioOpState.Loading && prompt.isNotBlank(),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("generate_image_button"),
      ) {
        if (opState is StudioOpState.Loading) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          Spacer(Modifier.width(8.dp))
          Text("Synthesizing Image (30-60s)...")
        } else {
          Icon(Icons.Default.Brush, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Generate Image")
        }
      }
    }

    when (opState) {
      is StudioOpState.Loading -> {
        StudioGlassCard(borderColor = ElectricCyan.copy(alpha = 0.5f)) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(220.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator(color = ElectricCyan)
              Spacer(Modifier.height(10.dp))
              Text("Rendering with Nano Banana 2.1...", style = MaterialTheme.typography.titleSmall)
              Text("High-resolution image synthesis can take 30–60s.", style = MaterialTheme.typography.bodySmall)
            }
          }
        }
      }
      is StudioOpState.Error -> {
        StudioGlassCard(borderColor = CrimsonError) {
          Text(opState.message, style = MaterialTheme.typography.bodySmall, color = CrimsonError)
        }
      }
      is StudioOpState.Success -> {
        val result = opState.data
        StudioGlassCard(borderColor = RoyalGold.copy(alpha = 0.6f)) {
          Image(
            bitmap = result.bitmap.asImageBitmap(),
            contentDescription = result.prompt,
            modifier = Modifier
              .fillMaxWidth()
              .height(280.dp)
              .clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Fit,
          )
          Spacer(Modifier.height(12.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = {
                val res = MediaExportHelper.exportBitmapToGallery(context, result.bitmap, result.styleMode)
                onShowToast(res.getOrElse { it.localizedMessage ?: "Export failed" })
              },
              modifier = Modifier
                .weight(1f)
                .height(48.dp),
            ) {
              Icon(Icons.Default.Download, contentDescription = null)
              Spacer(Modifier.width(6.dp))
              Text("Save to Gallery")
            }
            OutlinedButton(
              onClick = { MediaExportHelper.shareFile(context, result.localFile, "image/png", "Share AI Image") },
              modifier = Modifier.height(48.dp),
            ) {
              Icon(Icons.Default.Share, contentDescription = "Share image")
            }
          }
        }
      }
      else -> Unit
    }

    if (imageHistory.isNotEmpty()) {
      SectionHeader("Recent Image History", "${imageHistory.size} saved creations")
      LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(imageHistory.take(10), key = { it.id }) { proj ->
          val bmp = remember(proj.localMediaUri) { loadBitmap(proj.localMediaUri) }
          StudioGlassCard(modifier = Modifier.width(180.dp)) {
            if (bmp != null) {
              Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = proj.title,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(110.dp)
                  .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop,
              )
              Spacer(Modifier.height(6.dp))
            }
            Text(proj.title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(proj.prompt, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
    Spacer(Modifier.height(16.dp))
  }
}
