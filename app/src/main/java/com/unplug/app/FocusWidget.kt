package com.unplug.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews

/**
 * Home-screen widget (for other launchers, or a second screen): shows the focus countdown,
 * or today's focused time when idle. The countdown ticks on its own — no updates needed.
 */
class FocusWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, FocusWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val views = RemoteViews(context.packageName, R.layout.widget_focus)
            val session = FocusSession.current(context)

            if (session != null) {
                val remaining = (session.endAt - System.currentTimeMillis()).coerceAtLeast(0L)
                views.setTextViewText(R.id.widget_title, "focus.")
                views.setViewVisibility(R.id.widget_countdown, View.VISIBLE)
                views.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime() + remaining, null, true)
                views.setChronometerCountDown(R.id.widget_countdown, true)
                views.setTextViewText(R.id.widget_status, "REMAINING")
            } else {
                val today = Stats.minutesOn(context, System.currentTimeMillis())
                views.setTextViewText(R.id.widget_title, "unplug.")
                views.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime(), null, false)
                views.setViewVisibility(R.id.widget_countdown, View.GONE)
                views.setTextViewText(
                    R.id.widget_status,
                    if (today > 0) "TODAY ${formatMinutes(today).uppercase()} · TAP TO FOCUS" else "TAP TO FOCUS",
                )
            }

            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(ids, views)
        }
    }
}
