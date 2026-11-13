package com.unplug.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * During focus, notifications from apps that aren't allowed are snoozed until the
 * session ends — they come back afterwards, nothing is deleted.
 * Calls, WhatsApp, your allowed apps and ongoing notifications (music, navigation) stay.
 */
class NotificationSilencer : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        sweep()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val remaining = shouldSilenceFor()
        if (remaining > 0 && shouldSilence(sbn)) snooze(sbn, remaining)
    }

    /** Snoozes everything already in the shade (called when a session starts). */
    fun sweep() {
        val remaining = shouldSilenceFor()
        if (remaining <= 0) return
        runCatching { activeNotifications }.getOrNull()?.forEach { sbn ->
            if (shouldSilence(sbn)) snooze(sbn, remaining)
        }
    }

    /** Ms left in the session if silencing applies right now, else 0. */
    private fun shouldSilenceFor(): Long {
        if (!SettingsStore.load(this).silenceNotifications) return 0L
        return FocusSession.remainingMs(this)
    }

    private fun shouldSilence(sbn: StatusBarNotification): Boolean {
        if (sbn.isOngoing || !sbn.isClearable) return false
        return sbn.packageName !in AllowList.build(this)
    }

    private fun snooze(sbn: StatusBarNotification, ms: Long) {
        runCatching { snoozeNotification(sbn.key, ms) }
    }

    companion object {
        @Volatile
        private var instance: NotificationSilencer? = null

        fun sweepNow() {
            instance?.sweep()
        }
    }
}
