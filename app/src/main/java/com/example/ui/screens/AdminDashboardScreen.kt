package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminLog
import com.example.data.model.Invitation
import com.example.data.model.NotificationItem
import com.example.data.model.StudioProject
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SystemConfig
import com.example.data.model.UpgradeRequest
import com.example.data.model.UserProfile
import com.example.ui.components.PrinceLogoEmblem
import com.example.ui.components.SectionHeader
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.DeepSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.PrinceAiStudioTheme
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.TextMuted

enum class AdminNavDestination(
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val subtitle: String,
  val tag: String,
) {
  OVERVIEW(
    label = "Overview",
    selectedIcon = Icons.Filled.Dashboard,
    unselectedIcon = Icons.Outlined.Dashboard,
    subtitle = "Platform telemetry & recent activity",
    tag = "admin_rail_overview",
  ),
  USERS(
    label = "Users",
    selectedIcon = Icons.Filled.Group,
    unselectedIcon = Icons.Outlined.Group,
    subtitle = "Private invites, roles, limits & status",
    tag = "admin_rail_users",
  ),
  SUBSCRIPTIONS(
    label = "Plans",
    selectedIcon = Icons.Filled.WorkspacePremium,
    unselectedIcon = Icons.Outlined.WorkspacePremium,
    subtitle = "Subscription tiers & upgrade approvals",
    tag = "admin_rail_subscriptions",
  ),
  AI_CONFIG(
    label = "AI Config",
    selectedIcon = Icons.Filled.Tune,
    unselectedIcon = Icons.Outlined.Tune,
    subtitle = "AI providers, tool toggles & maintenance",
    tag = "admin_rail_ai_config",
  ),
  COMMS_LOGS(
    label = "Logs",
    selectedIcon = Icons.Filled.Campaign,
    unselectedIcon = Icons.Outlined.Campaign,
    subtitle = "Broadcasts, support inbox & audit logs",
    tag = "admin_rail_logs",
  ),
}

@Composable
fun AdminDashboardScreen(
  currentAdmin: UserProfile,
  systemConfig: SystemConfig,
  plans: List<SubscriptionPlan>,
  allUsers: List<UserProfile>,
  invitations: List<Invitation>,
  upgradeRequests: List<UpgradeRequest>,
  allProjects: List<StudioProject>,
  adminNotifications: List<NotificationItem>,
  adminLogs: List<AdminLog>,
  isBusy: Boolean,
  onCreateInvite: (email: String, name: String, planId: String, note: String) -> Unit,
  onRevokeInvite: (String) -> Unit,
  onSetUserStatus: (userId: String, status: String) -> Unit,
  onToggleAdminRole: (UserProfile, Boolean) -> Unit,
  onAssignPlanAndLimits: (userId: String, planId: String, v: Long, vo: Long, i: Long, s: Long) -> Unit,
  onRecommendPlan: (UserProfile, String, String) -> Unit,
  onSavePlan: (SubscriptionPlan) -> Unit,
  onReviewUpgradeRequest: (UpgradeRequest, Boolean, String) -> Unit,
  onUpdateSystemConfig: (SystemConfig) -> Unit,
  onSendNotification: (targetUid: String, title: String, message: String, category: String) -> Unit,
  onExitAdminMode: () -> Unit,
  onSignOut: () -> Unit = {},
  onAssignPlanAndLimitsWithChat: ((userId: String, planId: String, v: Long, vo: Long, i: Long, s: Long, chat: Long) -> Unit)? = null,
) {
  var selectedDestination by rememberSaveable { mutableStateOf(AdminNavDestination.OVERVIEW) }
  var showCreateInviteDialog by rememberSaveable { mutableStateOf(false) }
  var userBeingManaged by rememberSaveable { mutableStateOf<String?>(null) }
  var planBeingEditedId by rememberSaveable { mutableStateOf<String?>(null) }

  val pendingInviteCount = invitations.count { it.status == "pending" }
  val pendingUpgradeCount = upgradeRequests.count { it.status == "pending" }
  val serviceErrorCount = adminLogs.count { it.severity == "error" }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianBg),
  ) {
    AdminConsoleTopHeader(
      currentAdmin = currentAdmin,
      activeDestination = selectedDestination,
      onExitAdminMode = onExitAdminMode,
      onSignOut = onSignOut,
    )
    HorizontalDivider(color = OutlineSubtle.copy(alpha = 0.7f))

    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
      AdminNavigationRail(
        selectedDestination = selectedDestination,
        pendingInviteCount = pendingInviteCount,
        pendingUpgradeCount = pendingUpgradeCount,
        serviceErrorCount = serviceErrorCount,
        onSelectDestination = { selectedDestination = it },
        onQuickInvite = { showCreateInviteDialog = true },
      )
      VerticalDivider(color = OutlineSubtle.copy(alpha = 0.7f))

      BoxWithConstraints(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
      ) {
        val horizontalPadding = if (maxWidth >= 600.dp) 24.dp else 14.dp
        AnimatedContent(
          targetState = selectedDestination,
          transitionSpec = {
            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(160))
          },
          label = "admin_section_transition",
          modifier = Modifier.fillMaxSize(),
        ) { destination ->
          Column(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = horizontalPadding, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 860.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              when (destination) {
                AdminNavDestination.OVERVIEW -> AdminOverviewSection(
                  allUsers = allUsers,
                  invitations = invitations,
                  upgradeRequests = upgradeRequests,
                  allProjects = allProjects,
                  adminLogs = adminLogs,
                  systemConfig = systemConfig,
                  isBusy = isBusy,
                )

                AdminNavDestination.USERS -> AdminUsersSection(
                  currentAdmin = currentAdmin,
                  allUsers = allUsers,
                  invitations = invitations,
                  isBusy = isBusy,
                  onOpenInviteDialog = { showCreateInviteDialog = true },
                  onRevokeInvite = onRevokeInvite,
                  onSetUserStatus = onSetUserStatus,
                  onToggleAdminRole = onToggleAdminRole,
                  onManagePlan = { userBeingManaged = it.userId },
                )

                AdminNavDestination.SUBSCRIPTIONS -> AdminSubscriptionsSection(
                  plans = plans,
                  upgradeRequests = upgradeRequests,
                  isBusy = isBusy,
                  onEditPlan = { planBeingEditedId = it.id },
                  onReviewRequest = onReviewUpgradeRequest,
                )

                AdminNavDestination.AI_CONFIG -> AdminAiConfigSection(
                  systemConfig = systemConfig,
                  adminLogs = adminLogs,
                  isBusy = isBusy,
                  onUpdateSystemConfig = onUpdateSystemConfig,
                )

                AdminNavDestination.COMMS_LOGS -> AdminCommsLogsSection(
                  allUsers = allUsers,
                  adminNotifications = adminNotifications,
                  adminLogs = adminLogs,
                  isBusy = isBusy,
                  onSendNotification = onSendNotification,
                )
              }
            }
          }
        }
      }
    }
  }

  if (showCreateInviteDialog) {
    CreateInviteDialog(
      onDismiss = { showCreateInviteDialog = false },
      onCreate = { email, name, planId, note ->
        onCreateInvite(email, name, planId, note)
        showCreateInviteDialog = false
      },
    )
  }

  val managedUser = userBeingManaged?.let { uid -> allUsers.firstOrNull { it.userId == uid } }
  if (managedUser != null) {
    ManageUserPlanDialog(
      user = managedUser,
      onDismiss = { userBeingManaged = null },
      onAssignPlanAndLimits = { planId, v, vo, i, s, chat ->
        if (onAssignPlanAndLimitsWithChat != null) {
          onAssignPlanAndLimitsWithChat(managedUser.userId, planId, v, vo, i, s, chat)
        } else {
          onAssignPlanAndLimits(managedUser.userId, planId, v, vo, i, s)
        }
        userBeingManaged = null
      },
      onRecommendPlan = { planId, note ->
        onRecommendPlan(managedUser, planId, note)
        userBeingManaged = null
      },
    )
  }

  val editedPlan = planBeingEditedId?.let { id -> plans.firstOrNull { it.id == id } }
  if (editedPlan != null) {
    EditSubscriptionPlanDialog(
      plan = editedPlan,
      onDismiss = { planBeingEditedId = null },
      onSave = { updated ->
        onSavePlan(updated)
        planBeingEditedId = null
      },
    )
  }
}

@Composable
private fun AdminConsoleTopHeader(
  currentAdmin: UserProfile,
  activeDestination: AdminNavDestination,
  onExitAdminMode: () -> Unit,
  onSignOut: () -> Unit = {},
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(Brush.horizontalGradient(listOf(DeepSurface, ObsidianBg)))
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.weight(1f),
    ) {
      PrinceLogoEmblem(size = 32.dp)
      Spacer(Modifier.width(10.dp))
      Column {
        Text(
          text = "Owner & Admin Console",
          style = MaterialTheme.typography.titleLarge,
          color = RoyalGold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${if (currentAdmin.isOwner) "Owner" else "Admin"} • ${activeDestination.subtitle}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Spacer(Modifier.width(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedButton(
        onClick = onExitAdminMode,
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("admin_exit_button"),
      ) {
        Text("Creator View")
      }
      Spacer(Modifier.width(4.dp))
      IconButton(
        onClick = onSignOut,
        modifier = Modifier.testTag("admin_sign_out_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Logout,
          contentDescription = "Sign out administrator",
          tint = RoyalGold,
        )
      }
    }
  }
}

@Composable
fun AdminNavigationRail(
  selectedDestination: AdminNavDestination,
  pendingInviteCount: Int = 0,
  pendingUpgradeCount: Int = 0,
  serviceErrorCount: Int = 0,
  onSelectDestination: (AdminNavDestination) -> Unit,
  onQuickInvite: () -> Unit,
) {
  NavigationRail(
    containerColor = DeepSurface,
    contentColor = ElectricCyan,
    modifier = Modifier
      .fillMaxHeight()
      .testTag("admin_navigation_rail"),
    header = {
      SmallFloatingActionButton(
        onClick = onQuickInvite,
        containerColor = ElectricCyan,
        contentColor = ObsidianBg,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
        modifier = Modifier
          .padding(top = 4.dp, bottom = 4.dp)
          .testTag("admin_rail_quick_invite_fab"),
      ) {
        Icon(
          imageVector = Icons.Default.PersonAdd,
          contentDescription = "Quick invite user",
        )
      }
    },
  ) {
    Column(
      modifier = Modifier.verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      AdminNavDestination.entries.forEach { destination ->
      val isSelected = selectedDestination == destination
      val badgeCount = when (destination) {
        AdminNavDestination.USERS -> pendingInviteCount
        AdminNavDestination.SUBSCRIPTIONS -> pendingUpgradeCount
        AdminNavDestination.AI_CONFIG -> serviceErrorCount
        else -> 0
      }

      NavigationRailItem(
        selected = isSelected,
        onClick = { onSelectDestination(destination) },
        modifier = Modifier.testTag(destination.tag),
        icon = {
          BadgedBox(
            badge = {
              if (badgeCount > 0) {
                Badge {
                  Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                }
              }
            },
          ) {
            Icon(
              imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
              contentDescription = destination.label,
            )
          }
        },
        label = {
          Text(
            text = destination.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        colors = NavigationRailItemDefaults.colors(
          selectedIconColor = ElectricCyan,
          selectedTextColor = ElectricCyan,
          indicatorColor = CyanContainer,
          unselectedIconColor = TextMuted,
          unselectedTextColor = TextMuted,
        ),
      )
    }
    }
  }
}

@Composable
fun AdminDashboardSkeletonScreen(
  initialSection: AdminNavDestination = AdminNavDestination.USERS,
  onExitAdminMode: () -> Unit = {},
) {
  var selectedDestination by rememberSaveable { mutableStateOf(initialSection) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianBg),
  ) {
    AdminConsoleTopHeader(
      currentAdmin = UserProfile(
        userId = "owner_preview",
        email = "shahprince0021@gmail.com",
        displayName = "Prince Owner",
        role = "owner",
      ),
      activeDestination = selectedDestination,
      onExitAdminMode = onExitAdminMode,
    )
    HorizontalDivider(color = OutlineSubtle.copy(alpha = 0.7f))

    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
      AdminNavigationRail(
        selectedDestination = selectedDestination,
        pendingInviteCount = 2,
        pendingUpgradeCount = 1,
        serviceErrorCount = 0,
        onSelectDestination = { selectedDestination = it },
        onQuickInvite = {},
      )
      VerticalDivider(color = OutlineSubtle.copy(alpha = 0.7f))

      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        SectionHeader(
          title = when (selectedDestination) {
            AdminNavDestination.OVERVIEW -> "Platform Overview"
            AdminNavDestination.USERS -> "User & Invitation Management"
            AdminNavDestination.SUBSCRIPTIONS -> "Subscriptions & Upgrade Queue"
            AdminNavDestination.AI_CONFIG -> "AI Providers & System Controls"
            AdminNavDestination.COMMS_LOGS -> "Broadcasts & Audit Logs"
          },
          subtitle = selectedDestination.subtitle,
        )
        AdminSectionSkeletonList(
          cardCount = 4,
          accentColor = when (selectedDestination) {
            AdminNavDestination.SUBSCRIPTIONS -> RoyalGold
            AdminNavDestination.AI_CONFIG -> ElectricCyan
            else -> ElectricCyan
          },
        )
      }
    }
  }
}

@Preview(showBackground = true, widthDp = 840, heightDp = 640)
@Composable
private fun AdminDashboardSkeletonPreview() {
  PrinceAiStudioTheme {
    AdminDashboardSkeletonScreen()
  }
}
