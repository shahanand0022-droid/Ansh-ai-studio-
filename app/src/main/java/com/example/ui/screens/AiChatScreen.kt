package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.core.NarrationLanguage
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SystemConfig
import com.example.data.model.UserProfile
import com.example.ui.components.RichMessageContent
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioGlassCard
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.DeepSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoyalGold

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
  profile: UserProfile,
  config: SystemConfig,
  plans: List<SubscriptionPlan>,
  sessions: List<ChatSession>,
  activeSessionId: String?,
  messages: List<ChatMessage>,
  streamingText: String,
  isGenerating: Boolean,
  errorMessage: String?,
  selectedLanguage: NarrationLanguage,
  selectedModel: String,
  attachedImage: Bitmap?,
  onSelectSession: (String) -> Unit,
  onNewChat: () -> Unit,
  onDeleteSession: (String) -> Unit,
  onSelectLanguage: (NarrationLanguage) -> Unit,
  onSelectModel: (String) -> Unit,
  onAttachImage: (Bitmap?) -> Unit,
  onSendMessage: (String) -> Unit,
  onRegenerateLast: () -> Unit,
  onStopGeneration: () -> Unit,
  onClearError: () -> Unit,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val listState = rememberLazyListState()
  var promptText by remember { mutableStateOf("") }
  var showHistorySheet by remember { mutableStateOf(false) }

  val currentPlan = plans.firstOrNull { it.id == profile.planId }
  val chatLimit = if (profile.isAdminOrOwner) -1L else if (profile.customChatLimit >= 0L) profile.customChatLimit else (currentPlan?.chatLimit ?: 50L)

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) {
      val bmp = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          val src = ImageDecoder.createSource(context.contentResolver, uri)
          ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
          }
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

  LaunchedEffect(messages.size, streamingText.length) {
    if (messages.isNotEmpty() || streamingText.isNotEmpty()) {
      listState.animateScrollToItem((messages.size + if (streamingText.isNotEmpty()) 1 else 0).coerceAtLeast(1) - 1)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 12.dp, vertical = 8.dp),
  ) {
    // Top Header Controls
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
          onClick = { showHistorySheet = true },
          modifier = Modifier.height(48.dp).testTag("chat_history_button"),
        ) {
          Icon(Icons.Default.History, contentDescription = "Chat History", modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("History (${sessions.size})")
        }
        OutlinedButton(
          onClick = onNewChat,
          modifier = Modifier.height(48.dp).testTag("chat_new_button"),
        ) {
          Icon(Icons.Default.AddComment, contentDescription = "New Chat", modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("New")
        }
      }
      StatusPill(
        text = if (chatLimit < 0L) "Chat: ∞" else "Chat: ${profile.chatUsed}/$chatLimit",
        color = ElectricCyan,
      )
    }

    Spacer(Modifier.height(6.dp))

    // Language & Model Selector Row
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      NarrationLanguage.entries.forEach { lang ->
        FilterChip(
          selected = selectedLanguage == lang,
          onClick = { onSelectLanguage(lang) },
          label = { Text(lang.code.uppercase()) },
        )
      }
      listOf(
        "gemini-3.8-flash" to "Gemini 3.8",
        "gemini-3.5-flash-lite" to "Lite",
        "gemini-4-argon" to "Argon Pro",
      ).forEach { (id, label) ->
        FilterChip(
          selected = selectedModel == id,
          onClick = { onSelectModel(id) },
          label = { Text(label) },
        )
      }
    }

    if (!config.aiChatEnabled) {
      Spacer(Modifier.height(8.dp))
      StudioGlassCard(borderColor = CrimsonError) {
        Text(
          "AI Chat Assistant is currently disabled by the Administrator.",
          style = MaterialTheme.typography.titleSmall,
          color = CrimsonError,
        )
      }
      return
    }

    if (!errorMessage.isNullOrBlank()) {
      Spacer(Modifier.height(6.dp))
      StudioGlassCard(borderColor = CrimsonError) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = CrimsonError, modifier = Modifier.weight(1f))
          IconButton(onClick = onClearError, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Dismiss error", tint = CrimsonError)
          }
        }
      }
    }

    Spacer(Modifier.height(6.dp))

    // Message Thread Area
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      if (messages.isEmpty() && streamingText.isBlank() && !isGenerating) {
        Column(
          modifier = Modifier.fillMaxSize().padding(12.dp),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(44.dp))
          Spacer(Modifier.height(10.dp))
          Text("PRINCE AI Chat", style = MaterialTheme.typography.titleLarge, color = RoyalGold)
          Spacer(Modifier.height(4.dp))
          Text(
            "Ask anything in English, हिन्दी, or नेपाली — coding, math equations, study help, writing, or attach a photo for visual analysis.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(14.dp))
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
              "Solve quadratic equation x^2 - 5x + 6 = 0 step by step",
              "Write a Kotlin coroutine retry helper function",
              "नेपालीमा AI को फाइदा र चुनौतीहरू बुझाउनुहोस्",
              "हिंदी में एक मोटिवेशनल YouTube स्क्रिप्ट लिखें",
            ).forEach { starter ->
              AssistChip(
                onClick = { onSendMessage(starter) },
                label = { Text(starter, maxLines = 1) },
              )
            }
          }
        }
      } else {
        LazyColumn(
          state = listState,
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          items(messages, key = { it.id }) { msg ->
            val isUser = msg.role == "user"
            val lastModelId = messages.lastOrNull { it.role == "model" }?.id
            ChatBubbleCard(
              text = msg.text,
              isUser = isUser,
              hasImage = msg.hasImageAttachment,
              modelLabel = msg.modelUsed,
              showRegenerate = !isUser && msg.id == lastModelId && !isGenerating,
              onCopy = { clipboard.setText(AnnotatedString(msg.text)) },
              onRegenerate = onRegenerateLast,
            )
          }

          if (isGenerating || streamingText.isNotBlank()) {
            item(key = "streaming_item") {
              ChatBubbleCard(
                text = streamingText.ifBlank { "Thinking..." },
                isUser = false,
                hasImage = false,
                modelLabel = selectedModel,
                showRegenerate = false,
                isStreaming = true,
                onCopy = { clipboard.setText(AnnotatedString(streamingText)) },
                onRegenerate = {},
              )
            }
          }
        }
      }
    }

    // Stop Generating Action Bar
    if (isGenerating) {
      Spacer(Modifier.height(6.dp))
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        OutlinedButton(
          onClick = onStopGeneration,
          modifier = Modifier.height(48.dp).testTag("chat_stop_button"),
        ) {
          Icon(Icons.Default.Stop, contentDescription = null, tint = CrimsonError)
          Spacer(Modifier.width(6.dp))
          Text("Stop Generating", color = CrimsonError)
        }
      }
    }

    // Attached Image Preview
    if (attachedImage != null) {
      Spacer(Modifier.height(6.dp))
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .clip(RoundedCornerShape(10.dp))
          .background(DeepSurface)
          .padding(6.dp),
      ) {
        Image(
          bitmap = attachedImage.asImageBitmap(),
          contentDescription = "Attached image preview",
          modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
          contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(8.dp))
        Text("Photo attached for Gemini Vision", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { onAttachImage(null) }, modifier = Modifier.size(36.dp)) {
          Icon(Icons.Default.Close, contentDescription = "Remove attached photo")
        }
      }
    }

    Spacer(Modifier.height(8.dp))

    // Bottom Input Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.Bottom,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      IconButton(
        onClick = {
          photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
          )
        },
        modifier = Modifier.size(48.dp).testTag("chat_attach_photo_button"),
      ) {
        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach photo via Android Photo Picker", tint = RoyalGold)
      }

      OutlinedTextField(
        value = promptText,
        onValueChange = { promptText = it },
        placeholder = { Text("Ask PRINCE AI anything (${selectedLanguage.code.uppercase()})...") },
        maxLines = 4,
        modifier = Modifier.weight(1f).testTag("chat_message_input"),
      )

      FilledIconButton(
        onClick = {
          if (isGenerating) {
            onStopGeneration()
          } else if (promptText.isNotBlank()) {
            val sending = promptText
            promptText = ""
            onSendMessage(sending)
          }
        },
        enabled = isGenerating || promptText.isNotBlank(),
        colors = IconButtonDefaults.filledIconButtonColors(
          containerColor = if (isGenerating) CrimsonError else ElectricCyan,
          contentColor = ObsidianBg,
        ),
        modifier = Modifier.size(50.dp).testTag("chat_send_stop_button"),
      ) {
        if (isGenerating) {
          Icon(Icons.Default.Stop, contentDescription = "Stop generating")
        } else {
          Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
        }
      }
    }
  }

  if (showHistorySheet) {
    ModalBottomSheet(
      onDismissRequest = { showHistorySheet = false },
      containerColor = DeepSurface,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Chat History (${sessions.size})", style = MaterialTheme.typography.titleLarge, color = RoyalGold)
          TextButton(onClick = {
            onNewChat()
            showHistorySheet = false
          }) {
            Text("+ New Chat")
          }
        }

        if (sessions.isEmpty()) {
          Text("No saved conversations yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
          LazyColumn(
            modifier = Modifier.height(340.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(sessions, key = { it.id }) { session ->
              val isCurrent = session.id == activeSessionId
              StudioGlassCard(
                borderColor = if (isCurrent) ElectricCyan else MaterialTheme.colorScheme.outline,
                onClick = {
                  onSelectSession(session.id)
                  showHistorySheet = false
                },
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(session.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(
                      "${session.language.uppercase()} • ${session.messageCount} msgs • ${session.lastMessagePreview.take(45)}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                    )
                  }
                  IconButton(onClick = { onDeleteSession(session.id) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete chat", tint = CrimsonError)
                  }
                }
              }
            }
          }
        }
        Spacer(Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun ChatBubbleCard(
  text: String,
  isUser: Boolean,
  hasImage: Boolean,
  modelLabel: String,
  showRegenerate: Boolean,
  isStreaming: Boolean = false,
  onCopy: () -> Unit,
  onRegenerate: () -> Unit,
) {
  Surface(
    color = if (isUser) CyanContainer.copy(alpha = 0.72f) else DeepSurface,
    shape = RoundedCornerShape(14.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      if (isUser) ElectricCyan.copy(alpha = 0.45f) else RoyalGold.copy(alpha = 0.28f),
    ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = if (isUser) "You" else "PRINCE AI • ${modelLabel.ifBlank { "gemini-3.8-flash" }}",
            style = MaterialTheme.typography.labelMedium,
            color = if (isUser) ElectricCyan else RoyalGold,
          )
          if (hasImage) {
            StatusPill("PHOTO", RoyalGold)
          }
          if (isStreaming) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = ElectricCyan)
          }
        }
        Row {
          IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy response", modifier = Modifier.size(16.dp))
          }
          if (showRegenerate) {
            IconButton(
              onClick = onRegenerate,
              modifier = Modifier.size(36.dp).testTag("chat_regenerate_button"),
            ) {
              Icon(Icons.Default.Refresh, contentDescription = "Regenerate response", modifier = Modifier.size(16.dp), tint = ElectricCyan)
            }
          }
        }
      }
      Spacer(Modifier.height(6.dp))
      RichMessageContent(
        rawText = text,
        textColor = MaterialTheme.colorScheme.onSurface,
        onCopiedCode = onCopy,
      )
    }
  }
}
