package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ui.theme.PrinceAiStudioTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdminSignInFlowTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun signInScreen_togglesBetweenCreatorAndAdminPortal() {
    composeTestRule.setContent {
      PrinceAiStudioTheme {
        SignInScreen(onAuthSuccess = {})
      }
    }

    composeTestRule.onNodeWithTag("signin_mode_creator").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("signin_mode_admin").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("signin_mode_admin").performScrollTo().performClick()

    composeTestRule.onNodeWithText("Administrator Authentication").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("admin_google_sign_in_button").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun adminAccessDeniedScreen_exposesSwitchAndContinueActions() {
    var switched = false
    var continued = false

    composeTestRule.setContent {
      PrinceAiStudioTheme {
        AdminAccessDeniedScreen(
          email = "princeshah3472@gmail.com",
          reason = "No active Owner or delegated Admin grant found.",
          canContinueAsCreator = true,
          onContinueAsCreator = { continued = true },
          onSwitchAccount = { switched = true },
        )
      }
    }

    composeTestRule.onNodeWithTag("admin_access_denied_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("admin_denied_switch_account_button").performClick()
    composeTestRule.onNodeWithTag("admin_denied_continue_button").performClick()

    assertTrue(switched)
    assertTrue(continued)
  }
}
