package com.unplug.app

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The home-screen watch, drawn in whichever face is chosen in settings. */
@Composable
fun ClockView(now: Long, size: Dp, modifier: Modifier = Modifier) {
    val settings = LocalSettings.current
    val context = LocalContext.current

    // Smooth seconds: a counter that only ever goes up, eased between ticks.
    val minuteStart = remember { now - now % 60_000L }
    val secTarget = ((now - minuteStart) / 1000L).toFloat()
    val sec by animateFloatAsState(secTarget, tween(450, easing = FastOutSlowInEasing), label = "sec")
    val secFraction: () -> Float = { (sec % 60f) / 60f }

    val cal = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val second = cal.get(Calendar.SECOND)
    val (time, amPm) = clockParts(context, now, settings.hourFormat)
    val k = size.value / 260f // scale text with the face

    val sized = modifier.size(size)
    when (settings.clockFace) {
        ClockFace.RING -> {
            val fraction: () -> Float =
                if (settings.showSeconds) secFraction else { { (minute + second / 60f) / 60f } }
            TickRing(fraction = fraction, modifier = sized) { TimeText(time, amPm, (52 * k).sp) }
        }

        ClockFace.ANALOG -> AnalogFace(hour, minute, second, secFraction, settings.showSeconds, sized)

        ClockFace.DIGITAL -> Box(sized, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(time, color = Ink, fontSize = (84 * k).sp, style = digitsStyle(), maxLines = 1)
                    if (settings.showSeconds) {
                        Text(
                            "%02d".format(second),
                            color = Dim,
                            fontSize = (16 * k).sp,
                            fontFamily = Mono,
                            modifier = Modifier.padding(start = 6.dp, bottom = (16 * k).dp),
                        )
                    }
                }
                if (amPm != null) Label(amPm)
            }
        }

        ClockFace.ORBIT -> OrbitFace(hour, minute, second, secFraction, settings.showSeconds, sized) {
            TimeText(time, amPm, (38 * k).sp)
        }
    }
}

@Composable
private fun TimeText(time: String, amPm: String?, fontSize: TextUnit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, color = Ink, fontSize = fontSize, style = digitsStyle(), maxLines = 1)
        if (amPm != null) Label(amPm)
    }
}

/** ("9:41", "AM") in 12-hour mode, ("21:41", null) in 24-hour. */
fun clockParts(context: Context, ms: Long, format: HourFormat): Pair<String, String?> {
    val is24 = when (format) {
        HourFormat.SYSTEM -> android.text.format.DateFormat.is24HourFormat(context)
        HourFormat.H12 -> false
        HourFormat.H24 -> true
    }
    val date = Date(ms)
    val locale = Locale.getDefault()
    return if (is24) {
        SimpleDateFormat("HH:mm", locale).format(date) to null
    } else {
        SimpleDateFormat("h:mm", locale).format(date) to SimpleDateFormat("a", locale).format(date)
    }
}

fun dateText(ms: Long): String = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ms))
