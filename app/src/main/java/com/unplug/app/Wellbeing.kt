package com.unplug.app

import android.app.AppOpsManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// ================================================================
// Focus stats: minutes per day, streaks
// ================================================================

object Stats {
    private const val PREFS = "stats"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun dayKey(ms: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

    fun add(c: Context, at: Long, focusedMs: Long) {
        val minutes = (focusedMs / 60_000L).toInt()
        if (minutes <= 0) return
        val key = dayKey(at)
        val p = prefs(c)
        p.edit().putInt(key, p.getInt(key, 0) + minutes).apply()
    }

    fun minutesOn(c: Context, dayMs: Long): Int = prefs(c).getInt(dayKey(dayMs), 0)

    /** Last 7 days, oldest first: (day start ms, minutes). */
    fun lastWeek(c: Context): List<Pair<Long, Int>> {
        val cal = startOfDay(System.currentTimeMillis())
        cal.add(Calendar.DAY_OF_YEAR, -6)
        return (0 until 7).map {
            val day = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            day to minutesOn(c, day)
        }
    }

    /** Consecutive days meeting [goal] minutes, ending today (or yesterday if today isn't met yet). */
    fun currentStreak(c: Context, goal: Int): Int {
        val cal = startOfDay(System.currentTimeMillis())
        if (minutesOn(c, cal.timeInMillis) < goal) cal.add(Calendar.DAY_OF_YEAR, -1)
        var streak = 0
        while (streak < 3650 && minutesOn(c, cal.timeInMillis) >= goal) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    fun bestStreak(c: Context, goal: Int): Int {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val days = prefs(c).all
            .filter { (_, v) -> (v as? Int ?: 0) >= goal }
            .keys.mapNotNull { runCatching { fmt.parse(it)?.time }.getOrNull() }
            .sorted()
        var best = 0
        var run = 0
        var prev: Long? = null
        for (d in days) {
            run = if (prev != null && d - prev in 82_800_000L..90_000_000L) run + 1 else 1 // ~1 day, DST-safe
            best = maxOf(best, run)
            prev = d
        }
        return best
    }

    fun startOfDay(ms: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

// ================================================================
// Daily app limits (read from Android's on-device usage stats)
// ================================================================

object UsageTracker {
    fun hasAccess(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openAccessSettings(c: Context) = c.startActivity(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    /** Foreground time today for [pkg], in ms. 0 without usage access. */
    fun usedTodayMs(c: Context, pkg: String): Long {
        val usm = c.getSystemService(UsageStatsManager::class.java) ?: return 0L
        val now = System.currentTimeMillis()
        val dayStart = Stats.startOfDay(now).timeInMillis
        val events = runCatching { usm.queryEvents(dayStart, now) }.getOrNull() ?: return 0L
        val e = UsageEvents.Event()
        var total = 0L
        var resumedAt = -1L
        var sawAny = false
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.packageName != pkg) continue
            @Suppress("DEPRECATION")
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (resumedAt < 0) resumedAt = e.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    // Open since before midnight: count from the start of the day.
                    val from = if (resumedAt >= 0) resumedAt else if (!sawAny) dayStart else -1L
                    if (from >= 0) total += e.timeStamp - from
                    resumedAt = -1L
                }
            }
            sawAny = true
        }
        if (resumedAt >= 0) total += now - resumedAt
        return total
    }
}

// ================================================================
// "Pause before opening": short-lived passes after "Open anyway"
// ================================================================

object Passes {
    private val until = ConcurrentHashMap<String, Long>()

    fun grant(pkg: String, minutes: Int) {
        until[pkg] = System.currentTimeMillis() + minutes * 60_000L
    }

    fun isValid(pkg: String): Boolean = (until[pkg] ?: 0L) > System.currentTimeMillis()
}

// ================================================================
// Uninstall protection (device admin with no policies)
// ================================================================

class UnplugAdmin : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off lets Unplug be uninstalled."

    companion object {
        fun component(c: Context) = ComponentName(c, UnplugAdmin::class.java)

        fun isActive(c: Context): Boolean =
            c.getSystemService(DevicePolicyManager::class.java)?.isAdminActive(component(c)) == true

        fun requestIntent(c: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(c))
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Stops Unplug from being uninstalled while you're trying to stay focused. " +
                        "You can turn this off any time outside a focus session.",
                )

        fun deactivate(c: Context) {
            c.getSystemService(DevicePolicyManager::class.java)?.removeActiveAdmin(component(c))
        }
    }
}

// ================================================================
// Small system helpers
// ================================================================

fun hasNotificationAccess(c: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

fun openNotificationAccessSettings(c: Context) = c.startActivity(
    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
)

/** (percent, charging) or null if unknown. */
fun batteryState(c: Context): Pair<Int, Boolean>? {
    val bm = c.getSystemService(BatteryManager::class.java) ?: return null
    val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    if (level !in 0..100) return null
    return level to bm.isCharging
}
