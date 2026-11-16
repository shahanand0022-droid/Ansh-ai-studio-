package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.core.AssistantActionIntent
import com.example.core.AssistantActionType
import com.example.core.AssistantPermissionSnapshot
import com.example.core.NarrationLanguage
import com.example.core.SpeechCaptureState
import com.example.core.VoiceEngineState
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SystemConfig
import com.example.data.model.UserProfile
import com.example.data.model.VideoProductionConfig
import com.example.ui.theme.PrinceAiStudioTheme
import com.example.ui.viewmodel.AssistantPhase
import com.example.ui.viewmodel.StudioOpState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatAndAssistantScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun aiChatScreen_sendMessageAndStopButtonWork() {
    var sentPrompt = ""
    val sampleProfile = UserProfile(
      userId = "u1",
      email = "creator@example.com",
      displayName = "Creator",
      planId = "pro",
      chatUsed = 4L,
    )
    val samplePlan = SubscriptionPlan(
      id = "pro",
      name = "PRO PLAN",
      chatLimit = 500L,
    )

    composeTestRule.setContent {
      PrinceAiStudioTheme {
        AiChatScreen(
          profile = sampleProfile,
          config = SystemConfig(aiChatEnabled = true),
          plans = listOf(samplePlan),
          sessions = listOf(
            ChatSession(id = "s1", userId = "u1", title = "Kotlin Coroutines", messageCount = 2),
          ),
          activeSessionId = "s1",
          messages = listOf(
            ChatMessage(id = "m1", userId = "u1", role = "user", text = "Hello AI"),
            ChatMessage(id = "m2", userId = "u1", role = "model", text = "Here is Kotlin:\n```kotlin\nfun hi() = 1\n```"),
          ),
          streamingText = "",
          isGenerating = false,
          errorMessage = null,
          selectedLanguage = NarrationLanguage.ENGLISH,
          selectedModel = "gemini-3.8-flash",
          attachedImage = null,
          onSelectSession = {},
          onNewChat = {},
          onDeleteSession = {},
          onSelectLanguage = {},
          onSelectModel = {},
          onAttachImage = {},
          onSendMessage = { sentPrompt = it },
          onRegenerateLast = {},
          onStopGeneration = {},
          onClearError = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chat_history_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("chat_new_button").assertIsDisplayed()
    composeTestRule.onNodeWithText("Chat: 4/500").assertIsDisplayed()
    composeTestRule.onNodeWithText("KOTLIN").assertIsDisplayed()

    composeTestRule.onNodeWithTag("chat_message_input").performTextInput("Explain relativity")
    composeTestRule.onNodeWithTag("chat_send_stop_button").performClick()
    composeTestRule.waitForIdle()

    assertEquals("Explain relativity", sentPrompt)
  }

  @Test
  fun aiChatScreen_showsDisabledBannerWhenAdminDisablesChat() {
    composeTestRule.setContent {
      PrinceAiStudioTheme {
        AiChatScreen(
          profile = UserProfile(userId = "u1"),
          config = SystemConfig(aiChatEnabled = false),
          plans = emptyList(),
          sessions = emptyList(),
          activeSessionId = null,
          messages = emptyList(),
          streamingText = "",
          isGenerating = false,
          errorMessage = null,
          selectedLanguage = NarrationLanguage.ENGLISH,
          selectedModel = "gemini-3.8-flash",
          attachedImage = null,
          onSelectSession = {},
          onNewChat = {},
          onDeleteSession = {},
          onSelectLanguage = {},
          onSelectModel = {},
          onAttachImage = {},
          onSendMessage = {},
          onRegenerateLast = {},
          onStopGeneration = {},
          onClearError = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("AI Chat Assistant is currently disabled by the Administrator.").assertIsDisplayed()
  }

  @Test
  fun phoneAssistantScreen_confirmationGateRequiresExplicitConfirmOrCancel() {
    var confirmed = false
    var cancelled = false
    val callIntent = AssistantActionIntent(
      type = AssistantActionType.PREPARE_CALL,
      target = "555-0199",
      requiresConfirmation = true,
      spokenReply = "Ready to dial 555-0199. Please confirm first.",
    )

    composeTestRule.setContent {
      PrinceAiStudioTheme {
        PhoneAssistantScreen(
          config = SystemConfig(voiceAssistantEnabled = true, phoneActionsEnabled = true),
          phase = AssistantPhase.AwaitingConfirmation(callIntent),
          turns = emptyList(),
          notes = emptyList(),
          speechState = SpeechCaptureState(),
          voiceState = VoiceEngineState(),
          selectedLanguage = NarrationLanguage.ENGLISH,
          voiceModeEnabled = true,
          attachedImage = null,
          statusBanner = null,
          onStartListening = {},
          onSubmitCommand = {},
          onConfirmAction = { confirmed = true },
          onCancelAction = { cancelled = true },
          onStopAll = {},
          onSelectLanguage = {},
          onToggleVoiceMode = {},
          onAttachImage = {},
          onClearConversation = {},
          onToggleNoteCompleted = { _, _ -> },
          onOpenCalendarReminder = {},
          onDeleteNote = {},
          onOpenPermissionsDashboard = {},
          onClearBanner = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("assistant_listen_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("assistant_confirmation_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Confirm Sensitive Action").assertIsDisplayed()
    composeTestRule.onNodeWithText("Recipient / Target: 555-0199").assertIsDisplayed()

    composeTestRule.onNodeWithTag("assistant_confirm_action_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("assistant_cancel_action_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertTrue(confirmed)
    assertTrue(cancelled)
  }

  @Test
  fun permissionDashboardScreen_displaysAllControlsAndSafetyCommitment() {
    var openedSettings = false
    composeTestRule.setContent {
      PrinceAiStudioTheme {
        PermissionDashboardScreen(
          permissions = AssistantPermissionSnapshot(
            micGranted = true,
            speechRecognitionAvailable = true,
            notificationListenerGranted = false,
            writeSettingsGranted = false,
            hasFlashlightHardware = true,
            flashlightEnabled = false,
            installableAppCount = 12,
          ),
          onRefresh = {},
          onOpenNotificationAccess = {},
          onOpenWriteSettings = {},
          onOpenAppSettings = { openedSettings = true },
          onBack = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Safety & Android Policy Commitment").assertIsDisplayed()
    composeTestRule.onNodeWithText("Microphone (RECORD_AUDIO)").assertIsDisplayed()
    composeTestRule.onNodeWithText("GRANTED").assertIsDisplayed()
    composeTestRule.onNodeWithText("Notification Access (Optional)").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Modify System Settings (Optional)").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Camera Torch LED (Hardware)").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("perm_open_app_settings_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertTrue(openedSettings)
  }

  @Test
  fun videoStudioScreen_enforcesAdminMaxDurationCapAndShowsModes() {
    composeTestRule.setContent {
      PrinceAiStudioTheme {
        VideoStudioScreen(
          config = SystemConfig(
            videoStudioEnabled = true,
            maxVideoDurationSeconds = 60,
          ),
          opState = StudioOpState.Idle,
          sceneKeyframes = emptyMap(),
          generatingKeyframeScene = null,
          prodConfig = VideoProductionConfig(),
          seedBitmap = null,
          videoBuildProgress = null,
          videoBuildStatus = null,
          assembledVideoFile = null,
          onSetSeedBitmap = {},
          onGenerateEnhancedPlan = { _, _, _ -> },
          onUpdateScene = { _, _ -> },
          onGenerateSceneKeyframe = { _, _ -> },
          onAssembleMp4 = {},
          onSendNarrationToVoice = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Text-to-Video").assertIsDisplayed()
    composeTestRule.onNodeWithText("Image-to-Video").assertIsDisplayed()
    composeTestRule.onNodeWithText("Script-to-Video").assertIsDisplayed()
    composeTestRule.onNodeWithTag("video_duration_60s").assertIsDisplayed()
    composeTestRule.onNodeWithTag("video_duration_90s").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("video_duration_120s").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("generate_video_plan_button").performScrollTo().assertIsDisplayed()
  }
}
