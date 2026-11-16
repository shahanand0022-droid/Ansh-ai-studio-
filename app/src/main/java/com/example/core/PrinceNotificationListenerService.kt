package com.example.core

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AssistantNotificationSummary(
  val key: String,
  val packageName: String,
  val appName: String,
  val title: String,
  val text: String,
  val postedTimeMillis: Long,
)

class PrinceNotificationListenerService : NotificationListenerService() {

  override fun onListenerConnected() {
    super.onListenerConnected()
    _isListenerConnected.value = true
    refreshSnapshot()
  }

  override fun onListenerDisconnected() {
    super.onListenerDisconnected()
    _isListenerConnected.value = false
    _notifications.value = emptyList()
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    super.onNotificationPosted(sbn)
    refreshSnapshot()
  }

  override fun onNotificationRemoved(sbn: StatusBarNotification?) {
    super.onNotificationRemoved(sbn)
    refreshSnapshot()
  }

  private fun refreshSnapshot() {
    if (!_readingEnabledByUser.value) {
      _notifications.value = emptyList()
      return
    }
    val list = runCatching {
      activeNotifications
        ?.filter { !it.isOngoing }
        ?.sortedByDescending { it.postTime }
        ?.take(12)
        ?.mapNotNull { sbn ->
          val extras = sbn.notification?.extras ?: return@mapNotNull null
          val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
          val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
          if (title.isBlank() && text.isBlank()) return@mapNotNull null
          val appLabel = runCatching {
            val ai = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(ai).toString()
          }.getOrDefault(sbn.packageName.substringAfterLast('.'))
          AssistantNotificationSummary(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = appLabel,
            title = title.ifBlank { appLabel },
            text = text.take(180),
            postedTimeMillis = sbn.postTime,
          )
        }
        .orEmpty()
    }.getOrDefault(emptyList())
    _notifications.value = list
  }

  companion object {
    private val _isListenerConnected = MutableStateFlow(false)
    val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

    private val _readingEnabledByUser = MutableStateFlow(true)
    val readingEnabledByUser: StateFlow<Boolean> = _readingEnabledByUser.asStateFlow()

    private val _notifications = MutableStateFlow<List<AssistantNotificationSummary>>(emptyList())
    val notifications: StateFlow<List<AssistantNotificationSummary>> = _notifications.asStateFlow()

    fun setReadingEnabled(enabled: Boolean) {
      _readingEnabledByUser.value = enabled
      if (!enabled) {
        _notifications.value = emptyList()
      }
    }
  }
}
