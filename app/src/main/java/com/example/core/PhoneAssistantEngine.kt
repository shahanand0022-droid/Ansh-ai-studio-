package com.example.core

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder
import java.util.Locale

enum class AssistantActionType {
  CONVERSATION,
  OPEN_APP,
  WEB_SEARCH,
  OPEN_SETTINGS,
  CREATE_NOTE,
  CREATE_REMINDER,
  SET_ALARM,
  SET_TIMER,
  ADJUST_VOLUME,
  ADJUST_BRIGHTNESS,
  TOGGLE_FLASHLIGHT,
  READ_NOTIFICATIONS,
  PREPARE_CALL,
  PREPARE_MESSAGE,
  NAVIGATE_STUDIO,
  UNSUPPORTED,
}

data class AssistantActionIntent(
  val type: AssistantActionType = AssistantActionType.CONVERSATION,
  val target: String = "",
  val secondaryText: String = "",
  val numericValue: Int = -1,
  val hour: Int = -1,
  val minute: Int = -1,
  val requiresConfirmation: Boolean = false,
  val spokenReply: String = "",
  val explanation: String = "",
) {
  val isSensitiveAction: Boolean
    get() = type == AssistantActionType.PREPARE_CALL || type == AssistantActionType.PREPARE_MESSAGE
}

data class AssistantPermissionSnapshot(
  val micGranted: Boolean = false,
  val speechRecognitionAvailable: Boolean = false,
  val notificationListenerGranted: Boolean = false,
  val writeSettingsGranted: Boolean = false,
  val hasFlashlightHardware: Boolean = false,
  val flashlightEnabled: Boolean = false,
  val installableAppCount: Int = 0,
)

data class SpeechCaptureState(
  val isListening: Boolean = false,
  val partialTranscript: String = "",
  val rmsDb: Float = 0f,
  val errorMessage: String? = null,
)

data class ActionExecutionOutcome(
  val executed: Boolean,
  val summary: String,
  val permissionNeeded: String? = null,
  val navigateToRoute: String? = null,
  val saveNoteTitle: String? = null,
  val saveNoteContent: String? = null,
  val saveNoteKind: String? = null,
  val saveDueLabel: String? = null,
)

class PhoneAssistantEngine(context: Context) {
  private val appContext = context.applicationContext
  private var speechRecognizer: SpeechRecognizer? = null
  private var isTorchOn = false

  private val _speechState = MutableStateFlow(SpeechCaptureState())
  val speechState: StateFlow<SpeechCaptureState> = _speechState.asStateFlow()

  fun inspectPermissions(): AssistantPermissionSnapshot {
    val micGranted = ContextCompat.checkSelfPermission(
      appContext,
      Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
    val speechAvailable = runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false)
    val notifEnabled = isNotificationAccessGranted()
    val writeSettings = runCatching { Settings.System.canWrite(appContext) }.getOrDefault(false)
    val hasFlash = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    val launcherAppsCount = queryLaunchableApps().size

    return AssistantPermissionSnapshot(
      micGranted = micGranted,
      speechRecognitionAvailable = speechAvailable,
      notificationListenerGranted = notifEnabled,
      writeSettingsGranted = writeSettings,
      hasFlashlightHardware = hasFlash,
      flashlightEnabled = isTorchOn,
      installableAppCount = launcherAppsCount,
    )
  }

  fun isNotificationAccessGranted(): Boolean {
    val enabledListeners = runCatching {
      Settings.Secure.getString(appContext.contentResolver, "enabled_notification_listeners")
    }.getOrNull().orEmpty()
    val cn = ComponentName(appContext, PrinceNotificationListenerService::class.java).flattenToString()
    return enabledListeners.contains(cn, ignoreCase = true) ||
      enabledListeners.contains(appContext.packageName, ignoreCase = true)
  }

  fun startForegroundListening(
    language: NarrationLanguage,
    onFinalTranscript: (String) -> Unit,
    onRecognitionError: (String) -> Unit,
  ) {
    if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      onRecognitionError("Microphone permission is not granted. Allow Microphone access to use voice commands.")
      return
    }
    if (!runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false)) {
      onRecognitionError("On-device Speech Recognition service is unavailable on this device or emulator. Use Text Mode instead.")
      return
    }

    stopListening()
    val localeTag = when (language) {
      NarrationLanguage.ENGLISH -> "en-US"
      NarrationLanguage.HINDI -> "hi-IN"
      NarrationLanguage.NEPALI -> "ne-NP"
    }

    runCatching {
      val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
      speechRecognizer = recognizer
      recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
          _speechState.value = SpeechCaptureState(isListening = true, partialTranscript = "", rmsDb = 0f, errorMessage = null)
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
          _speechState.value = _speechState.value.copy(rmsDb = rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
          _speechState.value = _speechState.value.copy(isListening = false)
        }

        override fun onError(error: Int) {
          val msg = mapSpeechError(error)
          _speechState.value = SpeechCaptureState(isListening = false, errorMessage = msg)
          if (error != SpeechRecognizer.ERROR_CLIENT) {
            onRecognitionError(msg)
          }
        }

        override fun onResults(results: Bundle?) {
          val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
          val spoken = matches.firstOrNull()?.trim().orEmpty()
          _speechState.value = SpeechCaptureState(isListening = false, partialTranscript = spoken)
          if (spoken.isNotBlank()) {
            onFinalTranscript(spoken)
          } else {
            onRecognitionError("No speech detected. Try speaking clearly closer to the microphone.")
          }
        }

        override fun onPartialResults(partialResults: Bundle?) {
          val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
          if (partial.isNotBlank()) {
            _speechState.value = _speechState.value.copy(partialTranscript = partial)
          }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
      })

      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
      }
      _speechState.value = SpeechCaptureState(isListening = true)
      recognizer.startListening(intent)
    }.onFailure { err ->
      val msg = err.localizedMessage ?: "Unable to start speech recognizer on this device."
      _speechState.value = SpeechCaptureState(isListening = false, errorMessage = msg)
      onRecognitionError(msg)
    }
  }

  fun stopListening() {
    runCatching { speechRecognizer?.stopListening() }
    runCatching { speechRecognizer?.cancel() }
    runCatching { speechRecognizer?.destroy() }
    speechRecognizer = null
    _speechState.value = _speechState.value.copy(isListening = false)
  }

  fun release() {
    stopListening()
  }

  fun queryLaunchableApps(): List<Pair<String, String>> {
    val pm = appContext.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
      addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val resolveInfos = runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
      } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(mainIntent, 0)
      }
    }.getOrDefault(emptyList())

    return resolveInfos.mapNotNull { info ->
      val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
      val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
      if (label.isBlank()) null else label to pkg
    }.distinctBy { it.second }
  }

  fun executeAction(action: AssistantActionIntent, confirmed: Boolean): ActionExecutionOutcome {
    if (action.isSensitiveAction && !confirmed) {
      return ActionExecutionOutcome(
        executed = false,
        summary = "Confirmation required before preparing a call or message.",
      )
    }

    return when (action.type) {
      AssistantActionType.CONVERSATION -> ActionExecutionOutcome(
        executed = true,
        summary = action.spokenReply,
      )

      AssistantActionType.OPEN_APP -> openInstalledApp(action.target)

      AssistantActionType.WEB_SEARCH -> launchWebSearch(action.target)

      AssistantActionType.OPEN_SETTINGS -> openSystemSettings(action.target)

      AssistantActionType.CREATE_NOTE -> ActionExecutionOutcome(
        executed = true,
        summary = "Saved note: ${action.target.ifBlank { "Quick Note" }}",
        saveNoteTitle = action.target.ifBlank { "Voice Note" },
        saveNoteContent = action.secondaryText.ifBlank { action.target },
        saveNoteKind = "note",
        saveDueLabel = "",
      )

      AssistantActionType.CREATE_REMINDER -> {
        val due = action.secondaryText.ifBlank { "Upcoming" }
        ActionExecutionOutcome(
          executed = true,
          summary = "Saved reminder: ${action.target.ifBlank { "Reminder" }} ($due)",
          saveNoteTitle = action.target.ifBlank { "Voice Reminder" },
          saveNoteContent = action.explanation.ifBlank { action.target },
          saveNoteKind = "reminder",
          saveDueLabel = due,
        )
      }

      AssistantActionType.SET_ALARM -> setSystemAlarm(action.hour, action.minute, action.secondaryText)

      AssistantActionType.SET_TIMER -> setSystemTimer(action.numericValue, action.secondaryText)

      AssistantActionType.ADJUST_VOLUME -> adjustAudioVolume(action.target, action.numericValue)

      AssistantActionType.ADJUST_BRIGHTNESS -> adjustScreenBrightness(action.numericValue)

      AssistantActionType.TOGGLE_FLASHLIGHT -> toggleFlashlight(action.target)

      AssistantActionType.READ_NOTIFICATIONS -> readActiveNotifications()

      AssistantActionType.PREPARE_CALL -> preparePhoneCall(action.target)

      AssistantActionType.PREPARE_MESSAGE -> prepareSmsMessage(action.target, action.secondaryText)

      AssistantActionType.NAVIGATE_STUDIO -> ActionExecutionOutcome(
        executed = true,
        summary = "Opening ${action.target.ifBlank { "studio" }} in PRINCE AI STUDIO.",
        navigateToRoute = action.target,
      )

      AssistantActionType.UNSUPPORTED -> ActionExecutionOutcome(
        executed = false,
        summary = action.explanation.ifBlank {
          action.spokenReply.ifBlank { "This action is not supported by Android security policies or this device." }
        },
      )
    }
  }

  fun openCalendarReminderIntent(title: String, note: String): ActionExecutionOutcome {
    val intent = Intent(Intent.ACTION_INSERT).apply {
      data = CalendarContract.Events.CONTENT_URI
      putExtra(CalendarContract.Events.TITLE, title)
      putExtra(CalendarContract.Events.DESCRIPTION, note)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return startSafeIntent(
      intent = intent,
      successMsg = "Opened Calendar app with pre-filled reminder.",
      missingMsg = "No Calendar app found to schedule an event.",
    )
  }

  fun openNotificationAccessSettings(): ActionExecutionOutcome {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return startSafeIntent(
      intent = intent,
      successMsg = "Opened Android Notification Access settings.",
      missingMsg = "Notification Access settings screen is unavailable on this device.",
    )
  }

  fun openWriteSettingsPermissionPage(): ActionExecutionOutcome {
    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
      data = Uri.parse("package:${appContext.packageName}")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return startSafeIntent(
      intent = intent,
      successMsg = "Opened Modify System Settings permission page.",
      missingMsg = "Modify System Settings page is unavailable on this Android build.",
    )
  }

  fun openAppPermissionSettings(): ActionExecutionOutcome {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
      data = Uri.parse("package:${appContext.packageName}")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return startSafeIntent(
      intent = intent,
      successMsg = "Opened app details to manage or revoke permissions.",
      missingMsg = "Unable to open app settings page.",
    )
  }

  private fun openInstalledApp(appNameQuery: String): ActionExecutionOutcome {
    val cleanQuery = appNameQuery.trim()
    if (cleanQuery.isBlank()) {
      return ActionExecutionOutcome(false, "Tell me which app name you want to open.")
    }
    val apps = queryLaunchableApps()
    val match = apps.firstOrNull { it.first.equals(cleanQuery, ignoreCase = true) }
      ?: apps.firstOrNull { it.first.contains(cleanQuery, ignoreCase = true) }
      ?: apps.firstOrNull { cleanQuery.contains(it.first, ignoreCase = true) }

    if (match == null) {
      return ActionExecutionOutcome(
        executed = false,
        summary = "No launchable app matching \"$cleanQuery\" is installed on this device.",
      )
    }
    val launchIntent = appContext.packageManager.getLaunchIntentForPackage(match.second)?.apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } ?: return ActionExecutionOutcome(
      executed = false,
      summary = "${match.first} does not provide a launchable main screen.",
    )

    return startSafeIntent(
      intent = launchIntent,
      successMsg = "Opening ${match.first}.",
      missingMsg = "Android refused to launch ${match.first}.",
    )
  }

  private fun launchWebSearch(query: String): ActionExecutionOutcome {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) {
      return ActionExecutionOutcome(false, "Please specify what you want to search for.")
    }
    val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
      putExtra("query", cleanQuery)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val result = startSafeIntent(
      intent = searchIntent,
      successMsg = "Searching the web for \"$cleanQuery\".",
      missingMsg = "",
    )
    if (result.executed) return result

    val encoded = runCatching { URLEncoder.encode(cleanQuery, "UTF-8") }.getOrDefault(cleanQuery)
    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return startSafeIntent(
      intent = browserIntent,
      successMsg = "Opened web search for \"$cleanQuery\".",
      missingMsg = "No web browser or search app is installed on this device.",
    )
  }

  private fun openSystemSettings(settingKey: String): ActionExecutionOutcome {
    val normalized = settingKey.trim().lowercase(Locale.ROOT)
    val (action, label) = when {
      "wifi" in normalized || "wi-fi" in normalized -> Settings.ACTION_WIFI_SETTINGS to "Wi-Fi Settings"
      "bluetooth" in normalized -> Settings.ACTION_BLUETOOTH_SETTINGS to "Bluetooth Settings"
      "sound" in normalized || "volume" in normalized -> Settings.ACTION_SOUND_SETTINGS to "Sound Settings"
      "display" in normalized || "bright" in normalized -> Settings.ACTION_DISPLAY_SETTINGS to "Display Settings"
      "battery" in normalized -> Settings.ACTION_BATTERY_SAVER_SETTINGS to "Battery Settings"
      "notif" in normalized -> Settings.ACTION_APP_NOTIFICATION_SETTINGS to "Notification Settings"
      "location" in normalized || "gps" in normalized -> Settings.ACTION_LOCATION_SOURCE_SETTINGS to "Location Settings"
      "storage" in normalized -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS to "Storage Settings"
      "app" in normalized -> Settings.ACTION_APPLICATION_SETTINGS to "Apps Settings"
      else -> Settings.ACTION_SETTINGS to "Android Settings"
    }
    val intent = Intent(action).apply {
      if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
        putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
      }
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return startSafeIntent(
      intent = intent,
      successMsg = "Opened $label.",
      missingMsg = "$label is unavailable on this Android version.",
    )
  }

  private fun setSystemAlarm(hour: Int, minute: Int, label: String): ActionExecutionOutcome {
    val safeHour = if (hour in 0..23) hour else 7
    val safeMinute = if (minute in 0..59) minute else 0
    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
      putExtra(AlarmClock.EXTRA_HOUR, safeHour)
      putExtra(AlarmClock.EXTRA_MINUTES, safeMinute)
      putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "PRINCE AI Alarm" })
      putExtra(AlarmClock.EXTRA_SKIP_UI, false)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val formatted = String.format(Locale.US, "%02d:%02d", safeHour, safeMinute)
    return startSafeIntent(
      intent = intent,
      successMsg = "Opened Clock to set alarm for $formatted.",
      missingMsg = "No compatible Clock / Alarm app is installed on this device.",
    )
  }

  private fun setSystemTimer(seconds: Int, label: String): ActionExecutionOutcome {
    val safeSeconds = if (seconds > 0) seconds else 60
    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
      putExtra(AlarmClock.EXTRA_LENGTH, safeSeconds)
      putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "PRINCE AI Timer" })
      putExtra(AlarmClock.EXTRA_SKIP_UI, false)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return startSafeIntent(
      intent = intent,
      successMsg = "Opened Clock to start a ${safeSeconds}s timer.",
      missingMsg = "No Clock timer handler is available on this device.",
    )
  }

  private fun adjustAudioVolume(directionOrMode: String, percent: Int): ActionExecutionOutcome {
    val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
      ?: return ActionExecutionOutcome(false, "AudioManager service is unavailable.")
    val stream = AudioManager.STREAM_MUSIC
    val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
    val current = audio.getStreamVolume(stream)
    val mode = directionOrMode.lowercase(Locale.ROOT)
    val targetLevel = when {
      percent in 0..100 -> ((percent / 100f) * max).toInt().coerceIn(0, max)
      "mute" in mode || "silent" in mode -> 0
      "down" in mode || "lower" in mode || "decrease" in mode -> (current - (max / 5).coerceAtLeast(1)).coerceAtLeast(0)
      else -> (current + (max / 5).coerceAtLeast(1)).coerceAtMost(max)
    }
    return runCatching {
      audio.setStreamVolume(stream, targetLevel, AudioManager.FLAG_SHOW_UI)
      val pct = ((targetLevel.toFloat() / max) * 100).toInt()
      ActionExecutionOutcome(true, "Media volume adjusted to $pct%.")
    }.getOrElse { err ->
      ActionExecutionOutcome(false, "Android blocked volume change: ${err.localizedMessage}")
    }
  }

  private fun adjustScreenBrightness(percent: Int): ActionExecutionOutcome {
    val canWrite = runCatching { Settings.System.canWrite(appContext) }.getOrDefault(false)
    if (!canWrite) {
      return ActionExecutionOutcome(
        executed = false,
        summary = "Modify System Settings permission is needed to change screen brightness directly.",
        permissionNeeded = "WRITE_SETTINGS",
      )
    }
    val pct = if (percent in 5..100) percent else 60
    val brightnessValue = ((pct / 100f) * 255f).toInt().coerceIn(10, 255)
    return runCatching {
      Settings.System.putInt(
        appContext.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS_MODE,
        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
      )
      Settings.System.putInt(
        appContext.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS,
        brightnessValue,
      )
      ActionExecutionOutcome(true, "Screen brightness set to $pct%.")
    }.getOrElse { err ->
      ActionExecutionOutcome(false, "Unable to modify brightness: ${err.localizedMessage}")
    }
  }

  private fun toggleFlashlight(targetState: String): ActionExecutionOutcome {
    if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
      return ActionExecutionOutcome(
        executed = false,
        summary = "This device or emulator does not have a hardware camera flashlight.",
      )
    }
    val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
      ?: return ActionExecutionOutcome(false, "CameraManager service is unavailable.")
    val torchCameraId = runCatching {
      cameraManager.cameraIdList.firstOrNull { id ->
        val chars = cameraManager.getCameraCharacteristics(id)
        val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        val facingBack = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        hasFlash && facingBack
      } ?: cameraManager.cameraIdList.firstOrNull { id ->
        cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
      }
    }.getOrNull() ?: return ActionExecutionOutcome(
      executed = false,
      summary = "No torch-capable camera LED was found on this device.",
    )

    val desired = when (targetState.trim().lowercase(Locale.ROOT)) {
      "on", "enable", "true" -> true
      "off", "disable", "false" -> false
      else -> !isTorchOn
    }

    return runCatching {
      cameraManager.setTorchMode(torchCameraId, desired)
      isTorchOn = desired
      ActionExecutionOutcome(true, "Flashlight turned ${if (desired) "ON" else "OFF"}.")
    }.getOrElse { err ->
      ActionExecutionOutcome(false, "Flashlight unavailable: ${err.localizedMessage}")
    }
  }

  private fun readActiveNotifications(): ActionExecutionOutcome {
    if (!isNotificationAccessGranted()) {
      return ActionExecutionOutcome(
        executed = false,
        summary = "Notification Access is not enabled. Enable it in Android Settings or the Permission Dashboard to read notifications.",
        permissionNeeded = "NOTIFICATION_LISTENER",
      )
    }
    val active = PrinceNotificationListenerService.notifications.value
    if (active.isEmpty()) {
      return ActionExecutionOutcome(true, "You have no active unread notifications right now.")
    }
    val summary = buildString {
      append("Here are your latest notifications: ")
      active.take(4).forEachIndexed { idx, item ->
        if (idx > 0) append("; ")
        append("${item.appName}: ${item.title}")
        if (item.text.isNotBlank()) append(" — ${item.text.take(80)}")
      }
    }
    return ActionExecutionOutcome(true, summary)
  }

  private fun preparePhoneCall(numberOrContact: String): ActionExecutionOutcome {
    val clean = numberOrContact.trim()
    val uri = if (clean.any { it.isDigit() }) Uri.parse("tel:$clean") else Uri.parse("tel:")
    val intent = Intent(Intent.ACTION_DIAL, uri).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return startSafeIntent(
      intent = intent,
      successMsg = if (clean.isBlank()) "Opened Phone dialer." else "Opened Phone dialer prepared for $clean. Tap Call to place it.",
      missingMsg = "No Phone dialer application is installed on this device.",
    )
  }

  private fun prepareSmsMessage(recipient: String, body: String): ActionExecutionOutcome {
    val cleanRecipient = recipient.trim()
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(cleanRecipient)}")).apply {
      if (body.isNotBlank()) putExtra("sms_body", body)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return startSafeIntent(
      intent = intent,
      successMsg = "Prepared message draft in your SMS app. Review and tap Send to deliver.",
      missingMsg = "No SMS messaging application is available on this device.",
    )
  }

  private fun startSafeIntent(
    intent: Intent,
    successMsg: String,
    missingMsg: String,
  ): ActionExecutionOutcome {
    return try {
      appContext.startActivity(intent)
      ActionExecutionOutcome(true, successMsg)
    } catch (_: ActivityNotFoundException) {
      ActionExecutionOutcome(false, missingMsg.ifBlank { "No app on this device can handle this request." })
    } catch (e: Exception) {
      ActionExecutionOutcome(false, "Unable to start Android action: ${e.localizedMessage}")
    }
  }

  private fun mapSpeechError(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during speech recognition."
    SpeechRecognizer.ERROR_NO_MATCH -> "Could not understand speech. Try again or type your request."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy; try again in a moment."
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard before timeout."
    else -> "Speech recognition unavailable (code $code)."
  }
}
