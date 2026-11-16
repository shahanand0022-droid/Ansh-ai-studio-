package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.StudioConstants
import com.example.data.model.SubscriptionPlan
import com.example.data.model.UserProfile
import com.example.ui.theme.AmberWarning

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateInviteDialog(
  onDismiss: () -> Unit,
  onCreate: (email: String, name: String, planId: String, note: String) -> Unit,
) {
  var email by remember { mutableStateOf("") }
  var name by remember { mutableStateOf("") }
  var planId by remember { mutableStateOf(StudioConstants.PLAN_FREE) }
  var note by remember { mutableStateOf("") }
  val plans = listOf(StudioConstants.PLAN_FREE, StudioConstants.PLAN_BASIC, StudioConstants.PLAN_PRO, StudioConstants.PLAN_CUSTOM)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Invite / Provision User") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          "Leave email blank to generate an open single-use invite token, or lock it to a specific Google email.",
          style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Invited Email (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Recipient Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Initial Plan", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          plans.forEach { p ->
            FilterChip(selected = planId == p, onClick = { planId = p }, label = { Text(p.uppercase()) })
          }
        }
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Admin Note") }, modifier = Modifier.fillMaxWidth())
      }
    },
    confirmButton = {
      Button(onClick = { onCreate(email, name, planId, note) }) { Text("Generate Invite") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManageUserPlanDialog(
  user: UserProfile,
  onDismiss: () -> Unit,
  onAssignPlanAndLimits: (planId: String, videoLimit: Long, voiceLimit: Long, imageLimit: Long, scriptLimit: Long, chatLimit: Long) -> Unit,
  onRecommendPlan: (planId: String, note: String) -> Unit,
) {
  val plans = listOf(StudioConstants.PLAN_FREE, StudioConstants.PLAN_BASIC, StudioConstants.PLAN_PRO, StudioConstants.PLAN_CUSTOM)
  var selectedPlan by remember { mutableStateOf(user.planId) }
  var videoLimit by remember { mutableStateOf(user.customVideoLimit.toString()) }
  var voiceLimit by remember { mutableStateOf(user.customVoiceLimit.toString()) }
  var imageLimit by remember { mutableStateOf(user.customImageLimit.toString()) }
  var scriptLimit by remember { mutableStateOf(user.customScriptLimit.toString()) }
  var chatLimit by remember { mutableStateOf(user.customChatLimit.toString()) }
  var recNote by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Manage ${user.displayName}") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text("Select Plan Tier", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          plans.forEach { p ->
            FilterChip(selected = selectedPlan == p, onClick = { selectedPlan = p }, label = { Text(p.uppercase()) })
          }
        }
        Text("Custom Usage Overrides (-1 = use plan default)", style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(value = chatLimit, onValueChange = { chatLimit = it }, label = { Text("AI Chat Limit") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = videoLimit, onValueChange = { videoLimit = it }, label = { Text("Video Limit") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = voiceLimit, onValueChange = { voiceLimit = it }, label = { Text("Voice Limit") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = imageLimit, onValueChange = { imageLimit = it }, label = { Text("Image Limit") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = scriptLimit, onValueChange = { scriptLimit = it }, label = { Text("Script Limit") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = recNote, onValueChange = { recNote = it }, label = { Text("Recommendation Message (optional)") }, modifier = Modifier.fillMaxWidth())
        Text(
          "Recommending a plan sends an in-app notification without charging.",
          style = MaterialTheme.typography.bodySmall,
          color = AmberWarning,
        )
      }
    },
    confirmButton = {
      Button(onClick = {
        onAssignPlanAndLimits(
          selectedPlan,
          videoLimit.toLongOrNull() ?: -1L,
          voiceLimit.toLongOrNull() ?: -1L,
          imageLimit.toLongOrNull() ?: -1L,
          scriptLimit.toLongOrNull() ?: -1L,
          chatLimit.toLongOrNull() ?: -1L,
        )
      }) {
        Text("Assign Plan")
      }
    },
    dismissButton = {
      TextButton(onClick = { onRecommendPlan(selectedPlan, recNote) }) {
        Text("Recommend Only")
      }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSubscriptionPlanDialog(
  plan: SubscriptionPlan,
  onDismiss: () -> Unit,
  onSave: (SubscriptionPlan) -> Unit,
) {
  var name by remember { mutableStateOf(plan.name) }
  var description by remember { mutableStateOf(plan.description) }
  var price by remember { mutableStateOf(plan.priceUsd.toString()) }
  var period by remember { mutableStateOf(plan.billingPeriod) }
  var videoLimit by remember { mutableStateOf(plan.videoLimit.toString()) }
  var voiceLimit by remember { mutableStateOf(plan.voiceLimit.toString()) }
  var imageLimit by remember { mutableStateOf(plan.imageLimit.toString()) }
  var scriptLimit by remember { mutableStateOf(plan.scriptLimit.toString()) }
  var chatLimit by remember { mutableStateOf(plan.chatLimit.toString()) }
  var isActive by remember { mutableStateOf(plan.isActive) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Edit ${plan.id.uppercase()} Plan") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Plan Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price (USD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Billing Period", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("monthly", "yearly", "custom").forEach { p ->
            FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p) })
          }
        }
        OutlinedTextField(value = chatLimit, onValueChange = { chatLimit = it }, label = { Text("AI Chat Limit (-1 = unlimited)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = videoLimit, onValueChange = { videoLimit = it }, label = { Text("Video Limit (-1 = unlimited)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = voiceLimit, onValueChange = { voiceLimit = it }, label = { Text("Voice Limit (-1 = unlimited)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = imageLimit, onValueChange = { imageLimit = it }, label = { Text("Image Limit (-1 = unlimited)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = scriptLimit, onValueChange = { scriptLimit = it }, label = { Text("Script Limit (-1 = unlimited)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        androidx.compose.foundation.layout.Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("Plan Active")
          Switch(checked = isActive, onCheckedChange = { isActive = it })
        }
      }
    },
    confirmButton = {
      Button(onClick = {
        onSave(
          plan.copy(
            name = name.ifBlank { plan.name },
            description = description,
            priceUsd = price.toDoubleOrNull() ?: plan.priceUsd,
            billingPeriod = period,
            videoLimit = videoLimit.toLongOrNull() ?: plan.videoLimit,
            voiceLimit = voiceLimit.toLongOrNull() ?: plan.voiceLimit,
            imageLimit = imageLimit.toLongOrNull() ?: plan.imageLimit,
            scriptLimit = scriptLimit.toLongOrNull() ?: plan.scriptLimit,
            chatLimit = chatLimit.toLongOrNull() ?: plan.chatLimit,
            isActive = isActive,
          ),
        )
      }) {
        Text("Save Plan")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
