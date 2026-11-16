package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SystemConfig
import com.example.data.model.UserProfile
import com.example.ui.theme.PrinceAiStudioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdminDashboardScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun adminDashboard_navigationRailSwitchesUsersSubscriptionsAndAiConfig() {
    val owner = UserProfile(
      userId = "owner_1",
      email = "shahprince0021@gmail.com",
      displayName = "Prince Owner",
      role = "owner",
    )
    val samplePlan = SubscriptionPlan(
      id = "pro",
      name = "PRO PLAN",
      description = "Pro creator tier",
      priceUsd = 39.0,
      billingPeriod = "monthly",
      videoLimit = 150,
      voiceLimit = 300,
      imageLimit = 150,
      scriptLimit = 500,
    )

    composeTestRule.setContent {
      PrinceAiStudioTheme {
        AdminDashboardScreen(
          currentAdmin = owner,
          systemConfig = SystemConfig(),
          plans = listOf(samplePlan),
          allUsers = listOf(owner),
          invitations = emptyList(),
          upgradeRequests = emptyList(),
          allProjects = emptyList(),
          adminNotifications = emptyList(),
          adminLogs = emptyList(),
          isBusy = false,
          onCreateInvite = { _, _, _, _ -> },
          onRevokeInvite = {},
          onSetUserStatus = { _, _ -> },
          onToggleAdminRole = { _, _ -> },
          onAssignPlanAndLimits = { _, _, _, _, _, _ -> },
          onRecommendPlan = { _, _, _ -> },
          onSavePlan = {},
          onReviewUpgradeRequest = { _, _, _ -> },
          onUpdateSystemConfig = {},
          onSendNotification = { _, _, _, _ -> },
          onExitAdminMode = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("admin_navigation_rail").assertIsDisplayed()
    composeTestRule.onNodeWithText("Platform Analytics").assertIsDisplayed()

    composeTestRule.onNodeWithTag("admin_rail_users").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("admin_invite_user_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("admin_user_search_input").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("admin_rail_subscriptions").performScrollTo().performClick()
    composeTestRule.onNodeWithText("Configurable Subscription Plans").assertIsDisplayed()
    composeTestRule.onNodeWithText("PRO PLAN").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("admin_rail_ai_config").performScrollTo().performClick()
    composeTestRule.onNodeWithText("AI Providers & Integration Health").assertIsDisplayed()
    composeTestRule.onNodeWithText("AI Tool & Access Controls").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun adminDashboardSkeleton_displaysRailAndSwitchesSections() {
    composeTestRule.setContent {
      PrinceAiStudioTheme {
        AdminDashboardSkeletonScreen(initialSection = AdminNavDestination.USERS)
      }
    }

    composeTestRule.onNodeWithTag("admin_navigation_rail").assertIsDisplayed()
    composeTestRule.onNodeWithText("User & Invitation Management").assertIsDisplayed()

    composeTestRule.onNodeWithTag("admin_rail_subscriptions").performClick()
    composeTestRule.onNodeWithText("Subscriptions & Upgrade Queue").assertIsDisplayed()

    composeTestRule.onNodeWithTag("admin_rail_ai_config").performClick()
    composeTestRule.onNodeWithText("AI Providers & System Controls").assertIsDisplayed()
  }
}
