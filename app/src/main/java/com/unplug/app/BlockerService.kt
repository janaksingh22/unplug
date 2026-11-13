package com.unplug.app

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Watches which window comes to the front and what the volume keys are doing.
 *
 * During focus:
 *  - apps not on the allow list are covered by the Unplug home screen;
 *  - the power menu is closed as soon as it opens (unless the battery is nearly flat);
 *  - holding BOTH volume keys for 5 s asks for the emergency-unlock password (if enabled).
 * Any time:
 *  - apps over their daily limit are sent home;
 *  - "mindful" apps get a short pause screen before they open.
 *
 * Only window-change and key events are requested; window content is never read.
 */
class BlockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var settings = LauncherSettings()
    private var allowed: Set<String> = emptySet()
    private var transient: Set<String> = emptySet()
    private var builtAt = 0L
    private var builtForSession = -1L

    /** Last real app in front (keyboard / system UI events are ignored). */
    private var foreground: String? = null

    override fun onServiceConnected() {
        instance = this
        refresh(-1L)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ------------------------------------------------------------
    // Window changes
    // ------------------------------------------------------------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        val session = FocusSession.current(this)
        val sessionKey = session?.endAt ?: 0L
        if (sessionKey != builtForSession || SystemClock.elapsedRealtime() - builtAt > 30_000) refresh(sessionKey)

        if (session != null) {
            if (settings.blockPowerMenu && isPowerMenu(pkg, event.className?.toString()) && !batteryNearlyFlat()) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                sendHome(REASON_POWER)
                return
            }
            if (pkg !in allowed) {
                sendHome(REASON_APP)
                return
            }
        }

        if (pkg == packageName || pkg in transient) return
        foreground = pkg
        checkWellbeing(pkg)
    }

    /** Daily limits first, then the mindful pause. */
    private fun checkWellbeing(pkg: String) {
        handler.removeCallbacks(limitCheck)
        val limitMinutes = settings.appLimits[pkg]
        if (limitMinutes != null) {
            val left = limitMinutes * 60_000L - UsageTracker.usedTodayMs(this, pkg)
            if (left <= 0) {
                sendHome(REASON_LIMIT, pkg)
                return
            }
            // Check again the moment the limit runs out.
            handler.postDelayed(limitCheck, left + 1_000L)
        }
        if (pkg in settings.mindfulApps && !Passes.isValid(pkg)) {
            sendHome(REASON_PAUSE, pkg)
        }
    }

    private val limitCheck = Runnable { foreground?.let { checkWellbeing(it) } }

    // ------------------------------------------------------------
    // Volume keys: emergency unlock
    // ------------------------------------------------------------

    private var volumeUpHeld = false
    private var volumeDownHeld = false
    private var unlockPending = false

    private val unlockAfterHold = Runnable {
        unlockPending = false
        if (volumeUpHeld && volumeDownHeld &&
            SettingsStore.load(this).emergencyUnlock && FocusSession.isActive(this)
        ) {
            sendHome(REASON_UNLOCK)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> volumeUpHeld = down
            KeyEvent.KEYCODE_VOLUME_DOWN -> volumeDownHeld = down
            else -> return false
        }
        if (volumeUpHeld && volumeDownHeld) {
            if (!unlockPending) {
                unlockPending = true
                handler.postDelayed(unlockAfterHold, UNLOCK_HOLD_MS)
            }
        } else if (unlockPending) {
            unlockPending = false
            handler.removeCallbacks(unlockAfterHold)
        }
        return false // never swallow volume presses
    }

    override fun onInterrupt() = Unit

    // ------------------------------------------------------------

    private fun refresh(session: Long) {
        settings = SettingsStore.load(this)
        allowed = AllowList.build(this)
        transient = AllowList.transientPackages(this)
        builtAt = SystemClock.elapsedRealtime()
        builtForSession = session
    }

    /** Leave the power menu alone when the phone is about to die, so it can be shut down safely. */
    private fun batteryNearlyFlat(): Boolean {
        val threshold = settings.lowBatteryPercent
        if (threshold <= 0) return false
        val (level, charging) = batteryState(this) ?: return false
        return !charging && level <= threshold
    }

    private fun sendHome(reason: String, pkg: String? = null) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_BLOCKED, reason)
                .putExtra(MainActivity.EXTRA_PACKAGE, pkg)
        )
    }

    /**
     * The power menu is a system dialog: "GlobalActions…" in AOSP / Pixel / Xiaomi / OnePlus,
     * a separate globalactions package on Samsung. Matched by window class name only.
     */
    private fun isPowerMenu(pkg: String, className: String?): Boolean {
        val systemSource = pkg == "android" || "systemui" in pkg || "globalactions" in pkg
        if (!systemSource || className == null) return false
        val cls = className.lowercase()
        return POWER_MENU_HINTS.any { it in cls }
    }

    companion object {
        const val REASON_APP = "app"
        const val REASON_POWER = "power"
        const val REASON_LIMIT = "limit"
        const val REASON_PAUSE = "pause"
        const val REASON_UNLOCK = "unlock"

        private const val UNLOCK_HOLD_MS = 5_000L
        private val POWER_MENU_HINTS = listOf("globalactions", "powermenu", "poweroff", "shutdown", "powerdialog")

        @Volatile
        private var instance: BlockerService? = null

        /** Swipe-down on the home screen: pull the notification shade (needs the service on). */
        fun openNotifications() {
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        }

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, BlockerService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it) == me
            }
        }
    }
}
