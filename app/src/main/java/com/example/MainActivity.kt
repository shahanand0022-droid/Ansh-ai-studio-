package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.attemptAutoSignIn
import com.example.core.authStateFlow
import com.example.core.configureAppCheck
import com.example.core.signOut
import com.example.data.model.StudioConstants
import com.example.ui.components.PrinceLogoEmblem
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioGlassCard
import com.example.ui.screens.AccountBlockedScreen
import com.example.ui.screens.AdminAccessDeniedScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AdminVerifyingScreen
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.InviteRedemptionScreen
import com.example.ui.screens.PermissionDashboardScreen
import com.example.ui.screens.PhoneAssistantScreen
import com.example.ui.screens.ProjectsAndHistoryScreen
import com.example.ui.screens.ScriptAssistantScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.screens.SubscriptionAndAccountScreen
import com.example.ui.screens.UserDashboardScreen
import com.example.ui.screens.VideoStudioScreen
import com.example.ui.screens.VoiceStudioScreen
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.DeepSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.PrinceAiStudioTheme
import com.example.ui.theme.RoyalGold
import com.example.ui.viewmodel.AdminAuthGateState
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.AssistantViewModel
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.SessionUiState
import com.example.ui.viewmodel.SessionViewModel
import com.example.ui.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

enum class AppDestination(val route: String, val label: String, val icon: ImageVector) {
  HOME("dashboard", "Home", Icons.Default.Dashboard),
  CHAT("ai_chat", "Chat", Icons.Default.ChatBubble),
  ASSISTANT("phone_assistant", "Assistant", Icons.Default.RecordVoiceOver),
  VIDEO("video_studio", "Video", Icons.Default.MovieCreation),
  VOICE("voice_studio", "Voice", Icons.Default.GraphicEq),
  IMAGE("image_studio", "Image", Icons.Default.Brush),
  SCRIPT("script_assistant", "Script", Icons.Default.AutoAwesome),
  PROJECTS("projects", "Projects", Icons.Default.Folder),
  PERMISSIONS("permissions", "Privacy", Icons.Default.Security),
  ACCOUNT("account", "Account", Icons.Default.AccountCircle),
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Configure Firebase App Check with launch intent before rendering Compose UI or ViewModels.
    configureAppCheck(this, intent)

    enableEdgeToEdge()
    setContent {
      PrinceAiStudioTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          PrinceAiAppRoot()
        }
      }
    }
  }
}

@Composable
fun PrinceAiAppRoot() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val auth = Firebase.auth
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

  var autoSignInChecked by remember { mutableStateOf(auth.currentUser != null) }
  var adminPortalRequested by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    if (auth.currentUser == null) {
      attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = { autoSignInChecked = true },
        onUnauthenticated = { autoSignInChecked = true },
        scope = scope,
      )
    }
  }

  val user = currentUser
  if (!autoSignInChecked && user == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PrinceLogoEmblem(size = 72.dp)
        Spacer(Modifier.height(14.dp))
        CircularProgressIndicator(color = ElectricCyan)
        Spacer(Modifier.height(10.dp))
        Text(StudioConstants.APP_BRAND_NAME, style = MaterialTheme.typography.titleLarge)
      }
    }
  } else if (user == null) {
    SignInScreen(
      onAuthSuccess = { requestedAdmin ->
        adminPortalRequested = requestedAdmin
      },
    )
  } else {
    key(user.uid) {
      AuthenticatedSessionHost(
        firebaseUser = user,
        adminPortalRequested = adminPortalRequested,
        onConsumeAdminPortalRequest = { adminPortalRequested = false },
        onSignOut = {
          adminPortalRequested = false
          signOut(credentialManager, scope)
        },
      )
    }
  }
}

@Composable
private fun AuthenticatedSessionHost(
  firebaseUser: FirebaseUser,
  adminPortalRequested: Boolean,
  onConsumeAdminPortalRequest: () -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val databaseId = remember(context) { context.getString(R.string.firestore_database_id) }

  val sessionVm: SessionViewModel = viewModel(
    key = "session_${firebaseUser.uid}",
    factory = SessionViewModel.provideFactory(
      databaseId = databaseId,
      uid = firebaseUser.uid,
      email = firebaseUser.email.orEmpty(),
      displayName = firebaseUser.displayName.orEmpty(),
      photoUrl = firebaseUser.photoUrl?.toString().orEmpty(),
      emailVerified = firebaseUser.isEmailVerified,
    ),
  )

  val sessionState by sessionVm.uiState.collectAsStateWithLifecycle()
  val adminGateState by sessionVm.adminGateState.collectAsStateWithLifecycle()
  val isBusy by sessionVm.isBusy.collectAsStateWithLifecycle()
  val feedback by sessionVm.feedback.collectAsStateWithLifecycle()
  val errorBanner by sessionVm.errorBanner.collectAsStateWithLifecycle()

  when (val state = sessionState) {
    is SessionUiState.Loading -> {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          PrinceLogoEmblem(size = 64.dp)
          Spacer(Modifier.height(12.dp))
          CircularProgressIndicator(color = ElectricCyan)
          Spacer(Modifier.height(8.dp))
          Text("Loading PRINCE AI STUDIO...", style = MaterialTheme.typography.bodyMedium)
        }
      }
    }

    is SessionUiState.NeedsInvite -> {
      if (adminPortalRequested && !state.isBootstrappingOwner) {
        AdminAccessDeniedScreen(
          email = state.email,
          reason = "This Google account is neither the Platform Owner (${StudioConstants.OWNER_EMAIL}) nor an invited Administrator.",
          canContinueAsCreator = false,
          onContinueAsCreator = onConsumeAdminPortalRequest,
          onSwitchAccount = onSignOut,
        )
      } else {
        InviteRedemptionScreen(
          email = state.email,
          displayName = state.displayName,
          isBootstrappingOwner = state.isBootstrappingOwner,
          isBusy = isBusy,
          errorMessage = errorBanner,
          onRedeemInvite = sessionVm::redeemInvite,
          onRetryOwnerBootstrap = sessionVm::retryOwnerBootstrap,
          onSignOut = onSignOut,
        )
      }
    }

    is SessionUiState.Suspended -> {
      AccountBlockedScreen(
        profile = state.profile,
        isMaintenance = false,
        message = "Your account has been ${state.profile.status} by the Administrator. Contact the platform Owner to restore access.",
        onSignOut = onSignOut,
      )
    }

    is SessionUiState.MaintenanceBlocked -> {
      AccountBlockedScreen(
        profile = state.profile,
        isMaintenance = true,
        message = state.message.ifBlank { "PRINCE AI STUDIO is currently undergoing scheduled maintenance." },
        onSignOut = onSignOut,
      )
    }

    is SessionUiState.Error -> {
      Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        StudioGlassCard(borderColor = CrimsonError) {
          Text("Session Error", style = MaterialTheme.typography.titleLarge, color = CrimsonError)
          Spacer(Modifier.height(8.dp))
          Text(state.message, style = MaterialTheme.typography.bodyMedium)
          Spacer(Modifier.height(12.dp))
          androidx.compose.material3.OutlinedButton(onClick = onSignOut) {
            Text("Sign Out")
          }
        }
      }
    }

    is SessionUiState.Ready -> {
      StudioShell(
        databaseId = databaseId,
        readyState = state,
        sessionVm = sessionVm,
        adminGateState = adminGateState,
        adminPortalRequested = adminPortalRequested,
        onConsumeAdminPortalRequest = onConsumeAdminPortalRequest,
        sessionFeedback = feedback,
        sessionError = errorBanner,
        isSessionBusy = isBusy,
        onSignOut = onSignOut,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioShell(
  databaseId: String,
  readyState: SessionUiState.Ready,
  sessionVm: SessionViewModel,
  adminGateState: AdminAuthGateState,
  adminPortalRequested: Boolean,
  onConsumeAdminPortalRequest: () -> Unit,
  sessionFeedback: String?,
  sessionError: String?,
  isSessionBusy: Boolean,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val profile = readyState.profile

  val studioVm: StudioViewModel = viewModel(
    key = "studio_${profile.userId}",
    factory = StudioViewModel.provideFactory(
      databaseId = databaseId,
      appContext = context.applicationContext,
      userId = profile.userId,
    ),
  )
  val chatVm: ChatViewModel = viewModel(
    key = "chat_${profile.userId}",
    factory = ChatViewModel.provideFactory(databaseId = databaseId, userId = profile.userId),
  )
  val assistantVm: AssistantViewModel = viewModel(
    key = "assistant_${profile.userId}",
    factory = AssistantViewModel.provideFactory(
      databaseId = databaseId,
      appContext = context.applicationContext,
      userId = profile.userId,
    ),
  )

  var currentDestination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
  var inAdminConsole by rememberSaveable { mutableStateOf(false) }

  val assistantNavTarget by assistantVm.navigateRouteEvent.collectAsStateWithLifecycle()
  LaunchedEffect(assistantNavTarget) {
    val target = assistantNavTarget?.lowercase().orEmpty()
    if (target.isNotBlank()) {
      currentDestination = when {
        "chat" in target -> AppDestination.CHAT
        "video" in target -> AppDestination.VIDEO
        "voice" in target -> AppDestination.VOICE
        "image" in target -> AppDestination.IMAGE
        "script" in target -> AppDestination.SCRIPT
        "project" in target -> AppDestination.PROJECTS
        "permission" in target -> AppDestination.PERMISSIONS
        "account" in target -> AppDestination.ACCOUNT
        else -> currentDestination
      }
      assistantVm.consumeNavigationEvent()
    }
  }

  LaunchedEffect(profile.userId, adminPortalRequested) {
    if (adminPortalRequested) {
      sessionVm.verifyAndEnterAdminConsole()
    }
  }

  LaunchedEffect(adminGateState) {
    if (adminGateState is AdminAuthGateState.Verified) {
      inAdminConsole = true
      onConsumeAdminPortalRequest()
      sessionVm.resetAdminGate()
    }
  }

  // BackHandler for non-Home destinations and Admin Console
  BackHandler(enabled = inAdminConsole || currentDestination != AppDestination.HOME) {
    if (inAdminConsole) {
      inAdminConsole = false
      sessionVm.resetAdminGate()
    } else if (currentDestination == AppDestination.PERMISSIONS) {
      currentDestination = AppDestination.ASSISTANT
    } else {
      currentDestination = AppDestination.HOME
    }
  }

  if (adminGateState is AdminAuthGateState.Verifying) {
    AdminVerifyingScreen(email = profile.email)
    return
  }

  if (adminGateState is AdminAuthGateState.Denied) {
    AdminAccessDeniedScreen(
      email = adminGateState.email,
      reason = adminGateState.reason,
      canContinueAsCreator = true,
      onContinueAsCreator = {
        sessionVm.resetAdminGate()
        onConsumeAdminPortalRequest()
      },
      onSwitchAccount = {
        sessionVm.resetAdminGate()
        onConsumeAdminPortalRequest()
        onSignOut()
      },
    )
    return
  }

  val myProjects by studioVm.myProjects.collectAsStateWithLifecycle()
  val videoPlanState by studioVm.videoPlanState.collectAsStateWithLifecycle()
  val sceneKeyframes by studioVm.sceneKeyframes.collectAsStateWithLifecycle()
  val generatingKeyframeScene by studioVm.generatingKeyframeScene.collectAsStateWithLifecycle()
  val videoProdConfig by studioVm.videoProductionConfig.collectAsStateWithLifecycle()
  val videoSeedBitmap by studioVm.videoSeedBitmap.collectAsStateWithLifecycle()
  val videoBuildProgress by studioVm.videoBuildProgress.collectAsStateWithLifecycle()
  val videoBuildStatus by studioVm.videoBuildStatus.collectAsStateWithLifecycle()
  val assembledVideoFile by studioVm.assembledVideoFile.collectAsStateWithLifecycle()
  val imageGenState by studioVm.imageGenState.collectAsStateWithLifecycle()
  val scriptState by studioVm.scriptState.collectAsStateWithLifecycle()
  val chatTurns by studioVm.chatTurns.collectAsStateWithLifecycle()
  val voiceState by studioVm.voiceState.collectAsStateWithLifecycle()
  val voiceScriptDraft by studioVm.voiceScriptDraft.collectAsStateWithLifecycle()
  val isWritingVoiceScript by studioVm.isWritingVoiceScript.collectAsStateWithLifecycle()
  val studioToast by studioVm.toastMessage.collectAsStateWithLifecycle()

  val chatSessions by chatVm.sessions.collectAsStateWithLifecycle()
  val activeChatSessionId by chatVm.activeSessionId.collectAsStateWithLifecycle()
  val activeChatMessages by chatVm.activeMessages.collectAsStateWithLifecycle()
  val chatStreamingText by chatVm.streamingText.collectAsStateWithLifecycle()
  val chatIsGenerating by chatVm.isGenerating.collectAsStateWithLifecycle()
  val chatError by chatVm.errorMessage.collectAsStateWithLifecycle()
  val chatLang by chatVm.selectedLanguage.collectAsStateWithLifecycle()
  val chatModel by chatVm.selectedModel.collectAsStateWithLifecycle()
  val chatAttachedImage by chatVm.attachedImage.collectAsStateWithLifecycle()

  val assistantPhase by assistantVm.phase.collectAsStateWithLifecycle()
  val assistantTurns by assistantVm.turns.collectAsStateWithLifecycle()
  val assistantNotes by assistantVm.notes.collectAsStateWithLifecycle()
  val assistantPerms by assistantVm.permissions.collectAsStateWithLifecycle()
  val assistantSpeech by assistantVm.speechState.collectAsStateWithLifecycle()
  val assistantVoice by assistantVm.voiceState.collectAsStateWithLifecycle()
  val assistantLang by assistantVm.selectedLanguage.collectAsStateWithLifecycle()
  val assistantVoiceMode by assistantVm.voiceModeEnabled.collectAsStateWithLifecycle()
  val assistantImage by assistantVm.attachedImage.collectAsStateWithLifecycle()
  val assistantBanner by assistantVm.statusBanner.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(sessionFeedback, sessionError, studioToast) {
    val msg = sessionFeedback ?: sessionError ?: studioToast
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      sessionVm.clearMessages()
      studioVm.clearToast()
    }
  }

  if (inAdminConsole && profile.isAdminOrOwner) {
    val adminVm: AdminViewModel = viewModel(
      key = "admin_${profile.userId}",
      factory = AdminViewModel.provideFactory(databaseId, profile.userId, profile.email),
    )
    val allUsers by adminVm.allUsers.collectAsStateWithLifecycle()
    val invitations by adminVm.invitations.collectAsStateWithLifecycle()
    val allUpgradeRequests by adminVm.allUpgradeRequests.collectAsStateWithLifecycle()
    val allProjects by adminVm.allProjects.collectAsStateWithLifecycle()
    val adminNotifications by adminVm.adminNotifications.collectAsStateWithLifecycle()
    val adminLogs by adminVm.adminLogs.collectAsStateWithLifecycle()
    val adminBusy by adminVm.isBusy.collectAsStateWithLifecycle()
    val adminBanner by adminVm.statusBanner.collectAsStateWithLifecycle()

    LaunchedEffect(adminBanner) {
      if (!adminBanner.isNullOrBlank()) {
        snackbarHostState.showSnackbar(adminBanner.orEmpty())
        adminVm.clearBanner()
      }
    }

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
      Box(modifier = Modifier.padding(innerPadding)) {
        AdminDashboardScreen(
          currentAdmin = profile,
          systemConfig = readyState.systemConfig,
          plans = readyState.plans,
          allUsers = allUsers,
          invitations = invitations,
          upgradeRequests = allUpgradeRequests,
          allProjects = allProjects,
          adminNotifications = adminNotifications,
          adminLogs = adminLogs,
          isBusy = adminBusy,
          onCreateInvite = adminVm::createInvitation,
          onRevokeInvite = adminVm::revokeInvitation,
          onSetUserStatus = adminVm::setUserStatus,
          onToggleAdminRole = adminVm::toggleAdminRole,
          onAssignPlanAndLimits = { uid, planId, v, vo, i, s ->
            adminVm.assignPlanAndLimits(uid, planId, v, vo, i, s)
          },
          onAssignPlanAndLimitsWithChat = adminVm::assignPlanAndLimits,
          onRecommendPlan = adminVm::recommendPlan,
          onSavePlan = adminVm::savePlan,
          onReviewUpgradeRequest = adminVm::reviewUpgradeRequest,
          onUpdateSystemConfig = adminVm::updateSystemConfig,
          onSendNotification = adminVm::sendBroadcastOrMessage,
          onExitAdminMode = {
            inAdminConsole = false
            sessionVm.resetAdminGate()
          },
          onSignOut = onSignOut,
        )
      }
    }
    return
  }

  val railDestinations = listOf(
    AppDestination.HOME,
    AppDestination.CHAT,
    AppDestination.ASSISTANT,
    AppDestination.VIDEO,
    AppDestination.VOICE,
    AppDestination.IMAGE,
    AppDestination.SCRIPT,
    AppDestination.PROJECTS,
    AppDestination.PERMISSIONS,
    AppDestination.ACCOUNT,
  )

  val currentPlan = readyState.plans.firstOrNull { it.id == profile.planId }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 680.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        TopAppBar(
          colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSurface),
          title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              PrinceLogoEmblem(size = 30.dp)
              Spacer(Modifier.width(10.dp))
              Column {
                Text(StudioConstants.APP_SHORT_NAME, style = MaterialTheme.typography.titleMedium, color = RoyalGold)
                Text(StudioConstants.APP_TAGLINE, style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
              }
            }
          },
          actions = {
            IconButton(
              onClick = { currentDestination = AppDestination.PERMISSIONS },
              modifier = Modifier.size(48.dp).testTag("topbar_permissions_button"),
            ) {
              Icon(Icons.Default.Security, contentDescription = "Permissions Dashboard", tint = ElectricCyan)
            }
            IconButton(
              onClick = { currentDestination = AppDestination.ACCOUNT },
              modifier = Modifier.size(48.dp).testTag("topbar_account_button"),
            ) {
              Icon(Icons.Default.AccountCircle, contentDescription = "Account and Plans", tint = RoyalGold)
            }
            StatusPill(
              text = profile.planId.uppercase(),
              color = ElectricCyan,
              modifier = Modifier.padding(end = 10.dp),
            )
          },
        )
      },
      bottomBar = {
        if (!useRail) {
          NavigationBar(containerColor = DeepSurface) {
            listOf(
              AppDestination.HOME,
              AppDestination.CHAT,
              AppDestination.ASSISTANT,
              AppDestination.VIDEO,
              AppDestination.PROJECTS,
            ).forEach { dest ->
              NavigationBarItem(
                selected = currentDestination == dest,
                onClick = { currentDestination = dest },
                icon = { Icon(dest.icon, contentDescription = dest.label) },
                label = { Text(dest.label, maxLines = 1) },
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (useRail) {
          NavigationRail(containerColor = DeepSurface) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
              railDestinations.forEach { dest ->
                NavigationRailItem(
                  selected = currentDestination == dest,
                  onClick = { currentDestination = dest },
                  icon = { Icon(dest.icon, contentDescription = dest.label) },
                  label = { Text(dest.label) },
                )
              }
            }
          }
        }

        Box(modifier = Modifier.weight(1f)) {
          when (currentDestination) {
            AppDestination.HOME -> UserDashboardScreen(
              profile = profile,
              plan = currentPlan,
              config = readyState.systemConfig,
              notifications = readyState.notifications,
              projectCount = myProjects.size,
              chatSessionCount = chatSessions.size,
              onOpenStudio = { studioType ->
                currentDestination = when (studioType) {
                  StudioConstants.TYPE_CHAT -> AppDestination.CHAT
                  StudioConstants.TYPE_VIDEO_PLAN, StudioConstants.TYPE_VIDEO_MP4 -> AppDestination.VIDEO
                  StudioConstants.TYPE_VOICE -> AppDestination.VOICE
                  StudioConstants.TYPE_IMAGE -> AppDestination.IMAGE
                  else -> AppDestination.SCRIPT
                }
              },
              onOpenPhoneAssistant = { currentDestination = AppDestination.ASSISTANT },
              onOpenPermissionsDashboard = { currentDestination = AppDestination.PERMISSIONS },
              onOpenProjects = { currentDestination = AppDestination.PROJECTS },
              onOpenAccount = { currentDestination = AppDestination.ACCOUNT },
              onOpenAdmin = {
                if (profile.isAdminOrOwner) {
                  sessionVm.verifyAndEnterAdminConsole()
                }
              },
            )

            AppDestination.CHAT -> AiChatScreen(
              profile = profile,
              config = readyState.systemConfig,
              plans = readyState.plans,
              sessions = chatSessions,
              activeSessionId = activeChatSessionId,
              messages = activeChatMessages,
              streamingText = chatStreamingText,
              isGenerating = chatIsGenerating,
              errorMessage = chatError,
              selectedLanguage = chatLang,
              selectedModel = chatModel,
              attachedImage = chatAttachedImage,
              onSelectSession = chatVm::selectSession,
              onNewChat = chatVm::startNewChat,
              onDeleteSession = chatVm::deleteSession,
              onSelectLanguage = chatVm::setLanguage,
              onSelectModel = chatVm::setModel,
              onAttachImage = chatVm::attachImage,
              onSendMessage = { prompt ->
                chatVm.sendMessage(profile, readyState.systemConfig, readyState.plans, prompt)
              },
              onRegenerateLast = {
                chatVm.regenerateLastResponse(profile, readyState.systemConfig, readyState.plans)
              },
              onStopGeneration = chatVm::stopGeneration,
              onClearError = chatVm::clearError,
            )

            AppDestination.ASSISTANT -> PhoneAssistantScreen(
              config = readyState.systemConfig,
              phase = assistantPhase,
              turns = assistantTurns,
              notes = assistantNotes,
              speechState = assistantSpeech,
              voiceState = assistantVoice,
              selectedLanguage = assistantLang,
              voiceModeEnabled = assistantVoiceMode,
              attachedImage = assistantImage,
              statusBanner = assistantBanner,
              onStartListening = { assistantVm.startListening(profile, readyState.systemConfig) },
              onSubmitCommand = { cmd -> assistantVm.submitCommand(profile, readyState.systemConfig, cmd) },
              onConfirmAction = assistantVm::confirmPendingAction,
              onCancelAction = assistantVm::cancelPendingAction,
              onStopAll = assistantVm::stopAllSpeechAndListening,
              onSelectLanguage = assistantVm::setLanguage,
              onToggleVoiceMode = assistantVm::setVoiceMode,
              onAttachImage = assistantVm::attachImage,
              onClearConversation = assistantVm::clearConversation,
              onToggleNoteCompleted = assistantVm::toggleNoteCompleted,
              onOpenCalendarReminder = assistantVm::openCalendarForReminder,
              onDeleteNote = assistantVm::deleteNote,
              onOpenPermissionsDashboard = { currentDestination = AppDestination.PERMISSIONS },
              onClearBanner = assistantVm::clearBanner,
            )

            AppDestination.PERMISSIONS -> PermissionDashboardScreen(
              permissions = assistantPerms,
              onRefresh = assistantVm::refreshPermissions,
              onOpenNotificationAccess = assistantVm::openNotificationAccessSettings,
              onOpenWriteSettings = assistantVm::openWriteSettingsPermission,
              onOpenAppSettings = assistantVm::openAppPermissionSettings,
              onBack = { currentDestination = AppDestination.ASSISTANT },
            )

            AppDestination.VIDEO -> VideoStudioScreen(
              config = readyState.systemConfig,
              opState = videoPlanState,
              sceneKeyframes = sceneKeyframes,
              generatingKeyframeScene = generatingKeyframeScene,
              prodConfig = videoProdConfig,
              seedBitmap = videoSeedBitmap,
              videoBuildProgress = videoBuildProgress,
              videoBuildStatus = videoBuildStatus,
              assembledVideoFile = assembledVideoFile,
              onSetSeedBitmap = studioVm::setVideoSeedBitmap,
              onGenerateEnhancedPlan = { cfg, topicOrScript, lang ->
                studioVm.generateEnhancedVideoPlan(profile, readyState.systemConfig, readyState.plans, cfg, topicOrScript, lang)
              },
              onUpdateScene = studioVm::updateVideoScene,
              onGenerateSceneKeyframe = { sceneNum, prompt ->
                studioVm.generateSceneKeyframe(profile, readyState.systemConfig, readyState.plans, sceneNum, prompt, context)
              },
              onAssembleMp4 = {
                studioVm.assembleVideoMp4Project(profile, readyState.systemConfig, readyState.plans, context)
              },
              onSendNarrationToVoice = { narration ->
                studioVm.updateVoiceScriptDraft(narration)
                currentDestination = AppDestination.VOICE
              },
              onShowMessage = { msg ->
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
              },
            )

            AppDestination.VOICE -> VoiceStudioScreen(
              config = readyState.systemConfig,
              voiceState = voiceState,
              scriptDraft = voiceScriptDraft,
              isWritingScript = isWritingVoiceScript,
              onScriptDraftChange = studioVm::updateVoiceScriptDraft,
              onSelectLanguage = studioVm::setVoiceLanguage,
              onSelectVoice = studioVm::selectVoice,
              onGenerateAiNarration = { topic, lang, style ->
                studioVm.generateNarrationWithAi(profile, readyState.systemConfig, readyState.plans, topic, lang, style)
              },
              onPreview = { text, rate, pitch ->
                studioVm.previewVoice(profile, readyState.systemConfig, readyState.plans, text, rate, pitch)
              },
              onSynthesizeWav = { text, lang, rate, pitch ->
                studioVm.synthesizeAndSaveVoice(profile, readyState.systemConfig, readyState.plans, text, lang, rate, pitch)
              },
              onPlayFile = studioVm::playVoiceFile,
              onStop = studioVm::stopVoice,
              onShowToast = { msg ->
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
              },
            )

            AppDestination.IMAGE -> ImageStudioScreen(
              config = readyState.systemConfig,
              opState = imageGenState,
              recentProjects = myProjects,
              loadBitmap = studioVm::loadProjectBitmap,
              onGenerateImage = { prompt, ratio, style ->
                studioVm.generateImage(profile, readyState.systemConfig, readyState.plans, prompt, ratio, style, context)
              },
              onShowToast = { msg ->
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
              },
            )

            AppDestination.SCRIPT -> ScriptAssistantScreen(
              config = readyState.systemConfig,
              opState = scriptState,
              chatTurns = chatTurns,
              onSendMessage = { mode, prompt ->
                studioVm.sendScriptMessage(profile, readyState.systemConfig, readyState.plans, mode, prompt)
              },
              onClearChat = studioVm::clearScriptChat,
            )

            AppDestination.PROJECTS -> ProjectsAndHistoryScreen(
              projects = myProjects,
              loadBitmap = studioVm::loadProjectBitmap,
              onPlayVoiceFile = studioVm::playVoiceFile,
              onDeleteProject = studioVm::deleteProject,
            )

            AppDestination.ACCOUNT -> SubscriptionAndAccountScreen(
              profile = profile,
              plans = readyState.plans,
              myRequests = readyState.myUpgradeRequests,
              notifications = readyState.notifications,
              isBusy = isSessionBusy,
              onSubmitUpgradeRequest = { planId, note ->
                sessionVm.submitUpgradeRequest(profile, planId, note)
              },
              onMarkNotificationRead = sessionVm::markNotificationRead,
              onUpdateProfile = sessionVm::updateProfile,
              onSendSupport = { subject, msg ->
                sessionVm.sendSupportMessage(profile, subject, msg)
              },
              onSignOut = onSignOut,
            )
          }
        }
      }
    }
  }
}
