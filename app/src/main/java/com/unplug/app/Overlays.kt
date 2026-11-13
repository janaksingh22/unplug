package com.unplug.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ================================================================
// SOS
// ================================================================

fun dialEmergency(context: Context, number: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** The phone's own emergency dialer (works on most phones); falls back to the normal dialer. */
fun openEmergencyDialer(context: Context, number: String) {
    val ok = runCatching {
        context.startActivity(
            Intent("com.android.phone.EmergencyDialer.DIAL").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
    if (!ok) dialEmergency(context, number)
}

@Composable
fun SosDialog(number: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    FormDialog("Emergency", onDismiss) {
        Body("Calls always work, even during focus.")
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Call $number") {
            dialEmergency(context, number)
            onDismiss()
        }
        Spacer(Modifier.height(10.dp))
        OutlineButton("Emergency dialer", Modifier.fillMaxWidth()) {
            openEmergencyDialer(context, number)
            onDismiss()
        }
        TextLink("Cancel", onDismiss)
    }
}

/** Small "SOS" pill shown on the focus screen. */
@Composable
fun SosPill(onClick: () -> Unit) {
    Box(
        Modifier
            .tap(onClick = onClick)
            .border(1.dp, Ink, CircleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text("SOS", color = Ink, fontSize = 11.sp, letterSpacing = 2.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
    }
}

// ================================================================
// Emergency unlock
// ================================================================

@Composable
fun UnlockDialog(onUnlocked: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var failures by remember { mutableIntStateOf(0) }
    var lockedUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lockedUntil) {
        while (System.currentTimeMillis() < lockedUntil) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val waitSeconds = ((lockedUntil - now + 999) / 1000).coerceAtLeast(0)

    FormDialog("Emergency unlock", onDismiss) {
        Body("Ends the current focus session. Time already focused still counts.")
        Spacer(Modifier.height(12.dp))
        MonoField(password, { password = it; error = null }, "Password", password = true)
        val message = if (waitSeconds > 0) "Too many tries. Wait ${waitSeconds}s." else error
        message?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Ink, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
        DialogButtons("Cancel", onDismiss, "Unlock") {
            if (waitSeconds > 0) return@DialogButtons
            if (UnlockPassword.verify(context, password)) {
                FocusSession.endEarly(context)
                onUnlocked()
            } else {
                failures++
                password = ""
                error = "Wrong password."
                if (failures % 3 == 0) lockedUntil = System.currentTimeMillis() + 30_000L * (failures / 3)
            }
        }
    }
}

// ================================================================
// Pause before opening
// ================================================================

@Composable
fun PauseScreen(appLabel: String, seconds: Int, onOpen: () -> Unit, onCancel: () -> Unit) {
    var left by remember { mutableIntStateOf(seconds) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left--
        }
    }
    BackHandler(onBack = onCancel)

    // Slow breathing circle: 4 s in, 4 s out.
    val breath = rememberInfiniteTransition(label = "breath")
    val scale by breath.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val ink = Ink
    val faint = Faint

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .pointerInput(Unit) { detectTapGestures { } }
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(faint, radius = r, style = Stroke(1.dp.toPx()))
                drawCircle(ink.copy(alpha = 0.06f), radius = r * scale)
                drawCircle(ink, radius = r * scale, style = Stroke(1.5.dp.toPx()))
            }
            Text(
                if (left > 0) "$left" else "·",
                color = Ink,
                fontSize = 56.sp,
                style = digitsStyle(),
            )
        }
        Spacer(Modifier.height(32.dp))
        Text("Take a breath.", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        Body("You're about to open $appLabel.\nIs this what you meant to do?", center = true)
        Spacer(Modifier.weight(1f))
        PrimaryButton("Not now", onClick = onCancel)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.graphicsLayer { alpha = if (left > 0) 0.35f else 1f }) {
            OutlineButton(if (left > 0) "Open in ${left}s" else "Open anyway", Modifier.fillMaxWidth()) {
                if (left == 0) onOpen()
            }
        }
    }
}

// ================================================================
// Stats
// ================================================================

@Composable
fun StatsSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val goal = LocalSettings.current.dailyGoalMinutes
    val week = remember { Stats.lastWeek(context) }
    val streak = remember { Stats.currentStreak(context, goal) }
    val best = remember { maxOf(Stats.bestStreak(context, goal), streak) }
    var selected by remember { mutableIntStateOf(week.lastIndex) }
    BackHandler(onBack = onClose)

    val dayName = SimpleDateFormat("EEE", Locale.getDefault())
    val dayLong = SimpleDateFormat("EEEE d MMM", Locale.getDefault())

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .pointerInput(Unit) { detectTapGestures { } }
            .safeDrawingPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("stats.", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Close",
                tint = Ink,
                modifier = Modifier
                    .size(26.dp)
                    .tap(onClick = onClose),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(bottom = 32.dp)
        ) {
            // Hero numbers.
            Row(Modifier.fillMaxWidth()) {
                HeroNumber("$streak", if (streak == 1) "day streak" else "day streak", Modifier.weight(1f))
                HeroNumber("$best", "best streak", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Body("A day counts when you focus for at least ${formatMinutes(goal)}.")

            Spacer(Modifier.height(32.dp))
            Label("Focus · last 7 days", color = Ink)
            Spacer(Modifier.height(12.dp))

            // Selected-day readout (tap a bar to change it).
            val (selDay, selMinutes) = week[selected]
            Text(dayLong.format(Date(selDay)), color = Dim, fontSize = 13.sp)
            Text(
                if (selMinutes == 0) "—" else formatMinutes(selMinutes),
                color = Ink,
                fontSize = 34.sp,
                style = digitsStyle(),
            )
            Spacer(Modifier.height(12.dp))

            WeekBars(
                minutes = week.map { it.second },
                goal = goal,
                selected = selected,
                onSelect = { selected = it },
            )
            Row(Modifier.fillMaxWidth()) {
                week.forEachIndexed { i, (day, _) ->
                    Text(
                        dayName.format(Date(day)).take(2).uppercase(),
                        color = if (i == selected) Ink else Dim,
                        fontSize = 10.sp,
                        fontFamily = Mono,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            // Table view of the same numbers.
            Spacer(Modifier.height(28.dp))
            week.reversed().forEach { (day, m) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Text(dayLong.format(Date(day)), color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(
                        if (m == 0) "—" else formatMinutes(m),
                        color = if (m >= goal) Ink else Dim,
                        fontSize = 13.sp,
                        fontFamily = Mono,
                    )
                    Text(if (m >= goal) "  ✓" else "   ", color = Ink, fontSize = 13.sp, fontFamily = Mono)
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth()) {
                HeroNumber(
                    formatMinutes((FocusSession.totalFocusMs(context) / MIN).toInt()),
                    "unplugged in total",
                    Modifier.weight(1f),
                    small = true,
                )
                HeroNumber("${FocusSession.sessionsCompleted(context)}", "sessions", Modifier.weight(1f), small = true)
            }
        }
    }
}

@Composable
private fun HeroNumber(value: String, label: String, modifier: Modifier, small: Boolean = false) {
    Column(modifier) {
        Text(value, color = Ink, fontSize = if (small) 28.sp else 56.sp, style = digitsStyle(), maxLines = 1)
        Label(label)
    }
}

/**
 * One series, so no legend: thin bars with 4dp rounded tops on a shared baseline,
 * a recessive dashed goal line, and the selected bar's value shown above the chart.
 */
@Composable
private fun WeekBars(minutes: List<Int>, goal: Int, selected: Int, onSelect: (Int) -> Unit) {
    val ink = Ink
    val dim = Dim
    val faint = Faint
    val scaleMax = (maxOf(minutes.maxOrNull() ?: 0, goal) * 1.15f).coerceAtLeast(1f)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(minutes.size) {
                detectTapGestures { pos ->
                    val slot = size.width / minutes.size.toFloat()
                    onSelect((pos.x / slot).toInt().coerceIn(0, minutes.lastIndex))
                }
            }
    ) {
        val slot = size.width / minutes.size
        val barW = (slot * 0.36f).coerceAtMost(18.dp.toPx())
        val base = size.height - 1.dp.toPx()
        val radius = 4.dp.toPx()

        minutes.forEachIndexed { i, m ->
            if (m <= 0) return@forEachIndexed
            val h = (m / scaleMax) * base
            val left = slot * i + (slot - barW) / 2f
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = left,
                        top = base - h,
                        right = left + barW,
                        bottom = base,
                        topLeftCornerRadius = CornerRadius(radius),
                        topRightCornerRadius = CornerRadius(radius),
                        bottomRightCornerRadius = CornerRadius.Zero,
                        bottomLeftCornerRadius = CornerRadius.Zero,
                    )
                )
            }
            drawPath(path, color = if (i == selected) ink else ink.copy(alpha = 0.55f))
        }

        // Baseline.
        drawLine(faint, Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
        // Goal line.
        val gy = base - (goal / scaleMax) * base
        drawLine(
            dim,
            Offset(0f, gy),
            Offset(size.width, gy),
            1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Label("- - goal ${formatMinutes(goal)}")
    }
}
