package com.unplug.app

import android.content.Context
import java.util.Calendar

/** What's locking the phone right now. */
data class ActiveSession(val endAt: Long, val durationMs: Long, val scheduled: Boolean)

/**
 * The single source of truth for focus time: manual sessions (started from the ⏻ button)
 * and scheduled windows (Settings → Schedules). Stored in SharedPreferences so it
 * survives the app closing or the phone restarting. Everything is local.
 */
object FocusSession {
    private const val PREFS = "focus_session"
    private const val KEY_END = "end_at"
    private const val KEY_DURATION = "duration_ms"
    private const val KEY_TOTAL = "total_ms"
    private const val KEY_COUNT = "sessions"
    private const val KEY_SCHED_END = "sched_end"
    private const val KEY_SCHED_LEN = "sched_len"
    private const val KEY_SKIP_UNTIL = "skip_until"

    /** Longest session (manual or scheduled). */
    const val MAX_MINUTES = 8 * 60

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun start(c: Context, durationMs: Long) {
        val now = System.currentTimeMillis()
        val duration = durationMs.coerceIn(60_000L, MAX_MINUTES * 60_000L)
        prefs(c).edit()
            .putLong(KEY_DURATION, duration)
            .putLong(KEY_END, now + duration)
            .apply()
        FocusWidget.updateAll(c)
    }

    /** The session in force now (whichever of manual / scheduled ends later), or null. */
    fun current(c: Context): ActiveSession? {
        settle(c)
        val now = System.currentTimeMillis()
        val p = prefs(c)
        val manualEnd = p.getLong(KEY_END, 0L)
        val manual = if (manualEnd > now) ActiveSession(manualEnd, p.getLong(KEY_DURATION, 0L), false) else null

        val window = Schedules.activeWindow(SettingsStore.load(c).schedules, now)
            ?.takeIf { it.first >= p.getLong(KEY_SKIP_UNTIL, 0L) } // not emergency-unlocked
        val scheduled = window?.let { (start, end) ->
            // Remember it so its minutes are credited to stats when it ends.
            if (p.getLong(KEY_SCHED_END, 0L) != end) {
                p.edit().putLong(KEY_SCHED_END, end).putLong(KEY_SCHED_LEN, end - start).apply()
            }
            ActiveSession(end, end - start, true)
        }
        return listOfNotNull(manual, scheduled).maxByOrNull { it.endAt }
    }

    fun isActive(c: Context): Boolean = current(c) != null

    fun remainingMs(c: Context): Long =
        current(c)?.let { (it.endAt - System.currentTimeMillis()).coerceAtLeast(0L) } ?: 0L

    /** Duration of the session in force, or of the last manual one. */
    fun durationMs(c: Context): Long = current(c)?.durationMs ?: prefs(c).getLong(KEY_DURATION, 0L)

    /** End time of the manual session; 0 when none is running. */
    fun endAt(c: Context): Long = prefs(c).getLong(KEY_END, 0L)

    fun totalFocusMs(c: Context): Long = prefs(c).getLong(KEY_TOTAL, 0L)

    fun sessionsCompleted(c: Context): Int = prefs(c).getInt(KEY_COUNT, 0)

    /**
     * Emergency unlock: ends whatever is running now. Time already focused still counts.
     * A scheduled window is skipped until it ends (it comes back next time).
     */
    fun endEarly(c: Context) {
        val now = System.currentTimeMillis()
        val p = prefs(c)
        val e = p.edit()
        val manualEnd = p.getLong(KEY_END, 0L)
        if (manualEnd > now) {
            val focused = p.getLong(KEY_DURATION, 0L) - (manualEnd - now)
            Stats.add(c, now, focused)
            e.putLong(KEY_END, 0L)
        }
        Schedules.activeWindow(SettingsStore.load(c).schedules, now)?.let { (start, end) ->
            Stats.add(c, now, now - start)
            e.putLong(KEY_SKIP_UNTIL, end).putLong(KEY_SCHED_END, 0L)
        }
        e.apply()
        FocusWidget.updateAll(c)
    }

    /** Close finished sessions and add them to the stats. */
    private fun settle(c: Context) {
        val now = System.currentTimeMillis()
        val p = prefs(c)
        val end = p.getLong(KEY_END, 0L)
        if (end in 1..now) {
            val duration = p.getLong(KEY_DURATION, 0L)
            p.edit()
                .putLong(KEY_END, 0L)
                .putLong(KEY_TOTAL, p.getLong(KEY_TOTAL, 0L) + duration)
                .putInt(KEY_COUNT, p.getInt(KEY_COUNT, 0) + 1)
                .apply()
            Stats.add(c, end, duration)
            FocusWidget.updateAll(c)
        }
        val schedEnd = p.getLong(KEY_SCHED_END, 0L)
        if (schedEnd in 1..now) {
            val len = p.getLong(KEY_SCHED_LEN, 0L)
            p.edit()
                .putLong(KEY_SCHED_END, 0L)
                .putLong(KEY_TOTAL, p.getLong(KEY_TOTAL, 0L) + len)
                .putInt(KEY_COUNT, p.getInt(KEY_COUNT, 0) + 1)
                .apply()
            Stats.add(c, schedEnd, len)
        }
    }
}

/** Works out whether a schedule window covers a given moment. */
object Schedules {
    /** (start, end) in epoch ms of the enabled window covering [now], or null. */
    fun activeWindow(schedules: List<Schedule>, now: Long): Pair<Long, Long>? {
        var best: Pair<Long, Long>? = null
        for (s in schedules) {
            if (!s.enabled || s.lengthMinutes == 0) continue
            val length = s.lengthMinutes.coerceAtMost(FocusSession.MAX_MINUTES) * 60_000L
            // A window that started today, or one that started yesterday and runs past midnight.
            for (daysBack in 0..1) {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.DAY_OF_YEAR, -daysBack)
                    set(Calendar.HOUR_OF_DAY, s.startMinute / 60)
                    set(Calendar.MINUTE, s.startMinute % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (s.days and dayBit(cal) == 0) continue
                val start = cal.timeInMillis
                val end = start + length
                if (now in start until end && (best == null || end > best.second)) best = start to end
            }
        }
        return best
    }

    /** Monday = bit 0 … Sunday = bit 6. */
    fun dayBit(cal: Calendar): Int {
        val index = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // MONDAY(2) → 0, SUNDAY(1) → 6
        return 1 shl index
    }
}
