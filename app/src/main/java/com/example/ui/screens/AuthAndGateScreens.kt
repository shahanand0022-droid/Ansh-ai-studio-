package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.core.onGoogleSignInClicked
import com.example.data.model.StudioConstants
import com.example.data.model.UserProfile
import com.example.ui.components.PrinceLogoEmblem
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioGlassCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PlasmaViolet
import com.example.ui.theme.RoyalGold

@Composable
fun SignInScreen(
  onAuthSuccess: (isAdminPortal: Boolean) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }
  var isAdminMode by rememberSaveable { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val accentColor = if (isAdminMode) RoyalGold else ElectricCyan

  Box(
    modifier = Modifier
      .fillMaxSize()
      .drawBehind {
        drawRect(ObsidianBg)
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(accentColor.copy(alpha = 0.20f), Color.Transparent),
            center = Offset(size.width * 0.2f, size.height * 0.18f),
            radius = size.width * 0.7f,
          ),
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf((if (isAdminMode) PlasmaViolet else RoyalGold).copy(alpha = 0.14f), Color.Transparent),
            center = Offset(size.width * 0.85f, size.height * 0.78f),
            radius = size.width * 0.65f,
          ),
        )
      }
      .safeDrawingPadding()
      .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 460.dp)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      PrinceLogoEmblem(size = 84.dp)
      Spacer(Modifier.height(14.dp))
      StatusPill(
        text = if (isAdminMode) "OWNER & ADMIN SECURITY GATE" else "PRIVATE ACCESS PLATFORM",
        color = accentColor,
      )
      Spacer(Modifier.height(14.dp))
      Text(
        text = StudioConstants.APP_BRAND_NAME,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        text = StudioConstants.APP_TAGLINE,
        style = MaterialTheme.typography.titleMedium,
        color = accentColor,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(18.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        FilterChip(
          selected = !isAdminMode,
          onClick = {
            isAdminMode = false
            errorMessage = null
          },
          label = { Text("Creator Access") },
          leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = ElectricCyan.copy(alpha = 0.20f),
            selectedLabelColor = ElectricCyan,
            selectedLeadingIconColor = ElectricCyan,
          ),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("signin_mode_creator"),
        )
        FilterChip(
          selected = isAdminMode,
          onClick = {
            isAdminMode = true
            errorMessage = null
          },
          label = { Text("Admin Portal") },
          leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp)) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = RoyalGold.copy(alpha = 0.22f),
            selectedLabelColor = RoyalGold,
            selectedLeadingIconColor = RoyalGold,
          ),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("signin_mode_admin"),
        )
      }

      Spacer(Modifier.height(16.dp))

      if (isAdminMode) {
        StudioGlassCard(borderColor = RoyalGold.copy(alpha = 0.55f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = RoyalGold)
            Spacer(Modifier.width(10.dp))
            Text(
              text = "Administrator Authentication",
              style = MaterialTheme.typography.titleMedium,
              color = RoyalGold,
            )
          }
          Spacer(Modifier.height(10.dp))
          FeatureLine(Icons.Default.VerifiedUser, "Verified Google Identity via Credential Manager", RoyalGold)
          Spacer(Modifier.height(8.dp))
          FeatureLine(Icons.Default.AdminPanelSettings, "Zero-trust Firestore Owner/Admin role verification", RoyalGold)
          Spacer(Modifier.height(8.dp))
          FeatureLine(Icons.Default.Lock, "Direct Admin Console entry with sign-in audit trail", RoyalGold)
          Spacer(Modifier.height(14.dp))
          Surface(
            color = ObsidianBg.copy(alpha = 0.72f),
            shape = MaterialTheme.shapes.small,
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Default.Lock, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(10.dp))
              Text(
                text = "Restricted to Platform Owner (${StudioConstants.OWNER_EMAIL}) and Owner-authorized Admins.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        StudioGlassCard(borderColor = ElectricCyan.copy(alpha = 0.35f)) {
          Text(
            text = "Multi-Studio AI Creation Suite",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(Modifier.height(10.dp))
          FeatureLine(Icons.Default.AutoAwesome, "AI Script & Documentary Assistant (Gemini 3.8 Flash)")
          Spacer(Modifier.height(8.dp))
          FeatureLine(Icons.Default.VerifiedUser, "AI Image Studio & Storyboard Keyframes (Nano Banana 2.1)")
          Spacer(Modifier.height(8.dp))
          FeatureLine(Icons.Default.AdminPanelSettings, "Multilingual Voiceovers (English, Hindi, Nepali)")
          Spacer(Modifier.height(14.dp))
          Surface(
            color = ObsidianBg.copy(alpha = 0.7f),
            shape = MaterialTheme.shapes.small,
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Default.Lock, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(10.dp))
              Text(
                text = "Public self-registration is disabled. Access requires Owner authorization or a valid invitation token.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      Spacer(Modifier.height(24.dp))

      Button(
        onClick = {
          val requestedAdmin = isAdminMode
          isLoading = true
          errorMessage = null
          onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
              isLoading = false
              onAuthSuccess(requestedAdmin)
            },
            onAuthError = { err ->
              isLoading = false
              errorMessage = err
            },
            scope = scope,
            onAuthCancelled = {
              isLoading = false
              errorMessage = "Sign-in was cancelled or no Google account was selected. Ensure a Google account is signed in on this device/emulator and tap Sign in again."
            },
          )
        },
        enabled = !isLoading,
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag(if (isAdminMode) "admin_google_sign_in_button" else "google_sign_in_button"),
        colors = ButtonDefaults.buttonColors(
          containerColor = accentColor,
          contentColor = ObsidianBg,
        ),
      ) {
        if (isLoading) {
          CircularProgressIndicator(modifier = Modifier.size(22.dp), color = ObsidianBg, strokeWidth = 2.dp)
          Spacer(Modifier.width(10.dp))
          Text(
            if (isAdminMode) "Verifying Admin Identity..." else "Authenticating...",
            fontWeight = FontWeight.Bold,
          )
        } else {
          if (isAdminMode) {
            Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
            Spacer(Modifier.width(8.dp))
          }
          Text(
            if (isAdminMode) "Sign in as Administrator (Google)" else "Sign in with Google",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      if (!errorMessage.isNullOrBlank()) {
        Spacer(Modifier.height(12.dp))
        StudioGlassCard(borderColor = CrimsonError) {
          Text(
            text = errorMessage.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = CrimsonError,
          )
        }
      }
    }
  }
}

@Composable
private fun FeatureLine(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  text: String,
  tint: Color = ElectricCyan,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(10.dp))
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
fun AdminVerifyingScreen(email: String) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .safeDrawingPadding()
      .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    StudioGlassCard(
      modifier = Modifier.widthIn(max = 420.dp),
      borderColor = RoyalGold.copy(alpha = 0.6f),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = RoyalGold, strokeWidth = 2.5.dp)
        Spacer(Modifier.width(14.dp))
        Column {
          Text("Verifying Admin Privileges", style = MaterialTheme.typography.titleMedium, color = RoyalGold)
          Spacer(Modifier.height(2.dp))
          Text(
            "Checking Firebase Auth & Firestore admin grant for $email...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
fun AdminAccessDeniedScreen(
  email: String,
  reason: String,
  canContinueAsCreator: Boolean,
  onContinueAsCreator: () -> Unit,
  onSwitchAccount: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .safeDrawingPadding()
      .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    StudioGlassCard(
      modifier = Modifier
        .widthIn(max = 440.dp)
        .testTag("admin_access_denied_card"),
      borderColor = CrimsonError,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Block,
          contentDescription = "Administrator access denied",
          tint = CrimsonError,
          modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
          Text("Administrator Access Denied", style = MaterialTheme.typography.titleLarge, color = CrimsonError)
          Text(email, style = MaterialTheme.typography.bodySmall, color = ElectricCyan)
        }
      }
      Spacer(Modifier.height(12.dp))
      Text(
        text = reason,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(18.dp))
      Button(
        onClick = onSwitchAccount,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("admin_denied_switch_account_button"),
        colors = ButtonDefaults.buttonColors(containerColor = RoyalGold, contentColor = ObsidianBg),
      ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Switch to Admin Account", fontWeight = FontWeight.Bold)
      }
      Spacer(Modifier.height(10.dp))
      OutlinedButton(
        onClick = onContinueAsCreator,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("admin_denied_continue_button"),
      ) {
        Text(if (canContinueAsCreator) "Continue to Creator Studio" else "Use Invitation Code Instead")
      }
    }
  }
}

@Composable
fun InviteRedemptionScreen(
  email: String,
  displayName: String,
  isBootstrappingOwner: Boolean,
  isBusy: Boolean,
  errorMessage: String?,
  onRedeemInvite: (String) -> Unit,
  onRetryOwnerBootstrap: () -> Unit,
  onSignOut: () -> Unit,
) {
  var inviteCode by remember { mutableStateOf("") }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .safeDrawingPadding()
      .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 460.dp)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      PrinceLogoEmblem(size = 64.dp)
      Spacer(Modifier.height(12.dp))
      StatusPill(text = "PRIVATE INVITATION GATE", color = PlasmaViolet)
      Spacer(Modifier.height(10.dp))
      Text("Welcome, $displayName", style = MaterialTheme.typography.headlineMedium)
      Text(email, style = MaterialTheme.typography.bodySmall, color = ElectricCyan)
      Spacer(Modifier.height(20.dp))

      if (isBootstrappingOwner) {
        StudioGlassCard(borderColor = RoyalGold) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = RoyalGold, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Column {
              Text("Initializing Owner Workspace", style = MaterialTheme.typography.titleMedium, color = RoyalGold)
              Text(
                "Configuring Owner privileges, subscription plans, and platform controls...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = CrimsonError)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRetryOwnerBootstrap, modifier = Modifier.height(48.dp)) {
              Text("Retry Owner Setup")
            }
          }
        }
      } else {
        StudioGlassCard(borderColor = PlasmaViolet.copy(alpha = 0.5f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Key, contentDescription = null, tint = RoyalGold)
            Spacer(Modifier.width(10.dp))
            Text("Invitation Required", style = MaterialTheme.typography.titleMedium)
          }
          Spacer(Modifier.height(8.dp))
          Text(
            "PRINCE AI STUDIO operates in Private Access Mode. Public registration is disabled. Enter an invitation code issued by the Owner/Admin to activate your workspace.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(16.dp))
          OutlinedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it.uppercase() },
            label = { Text("Invitation Code (e.g. PRINCE-XXXXXX)") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("invite_code_input"),
          )
          Spacer(Modifier.height(14.dp))
          Button(
            onClick = { onRedeemInvite(inviteCode) },
            enabled = !isBusy && inviteCode.isNotBlank(),
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("redeem_invite_button"),
          ) {
            if (isBusy) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
              Spacer(Modifier.width(8.dp))
            }
            Text("Verify & Activate Account")
          }
          if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = CrimsonError)
          }
        }
      }

      Spacer(Modifier.height(20.dp))
      OutlinedButton(
        onClick = onSignOut,
        modifier = Modifier
          .height(48.dp)
          .testTag("sign_out_button"),
      ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Sign Out / Switch Account")
      }
    }
  }
}

@Composable
fun AccountBlockedScreen(
  profile: UserProfile,
  isMaintenance: Boolean,
  message: String,
  onSignOut: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .safeDrawingPadding()
      .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    StudioGlassCard(
      modifier = Modifier.widthIn(max = 440.dp),
      borderColor = if (isMaintenance) AmberWarning else CrimsonError,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = if (isMaintenance) Icons.Default.Build else Icons.Default.Block,
          contentDescription = null,
          tint = if (isMaintenance) AmberWarning else CrimsonError,
          modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
          Text(
            text = if (isMaintenance) "Maintenance Mode" else "Account ${profile.status.uppercase()}",
            style = MaterialTheme.typography.headlineSmall,
          )
          Text(profile.email, style = MaterialTheme.typography.bodySmall, color = ElectricCyan)
        }
      }
      Spacer(Modifier.height(14.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(18.dp))
      OutlinedButton(
        onClick = onSignOut,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp),
      ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Sign Out")
      }
    }
  }
}
