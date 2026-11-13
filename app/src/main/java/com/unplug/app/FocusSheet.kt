package com.unplug.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val PRESETS = listOf(15, 30, 60, 120, 240, 480)

/** Full-screen sheet for starting a focus session (or enabling the blocker first). */
@Composable
fun FocusSheet(serviceOn: Boolean, extraAllowed: Int, onClose: () -> Unit, onStart: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .pointerInput(Unit) { detectTapGestures { } } // block taps to the home screen below
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("focus.", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
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
        if (serviceOn) {
            DurationPicker(Modifier.weight(1f), extraAllowed, onStart)
        } else {
            BlockerSetup(Modifier.weight(1f))
        }
    }
}

@Composable
private fun BlockerSetup(modifier: Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1f))
        Text("One step first.", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(16.dp))
        Body("Turn on Unplug in Accessibility settings. It's how Unplug knows when to block an app.")
        Spacer(Modifier.height(12.dp))
        Body("It only sees which app is open. No internet. Nothing leaves your phone.")
        Spacer(Modifier.weight(1f))
        PrimaryButton("Turn on") { openAccessibilitySettings(context) }
        Spacer(Modifier.height(12.dp))
        TextLink("Switch greyed out? Allow restricted settings") {
            // Side-loaded apps on Android 13+: App info → ⋮ → Allow restricted settings.
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

@Composable
private fun DurationPicker(modifier: Modifier, extraAllowed: Int, onStart: (Int) -> Unit) {
    val context = LocalContext.current
    var minutes by rememberSaveable { mutableIntStateOf(30) }
    var confirming by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton("−") { minutes = (minutes - 5).coerceAtLeast(5) }
            Column(Modifier.width(200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(durationNumber(minutes), color = Ink, fontSize = 88.sp, style = digitsStyle(), maxLines = 1)
                Label(if (minutes < 60) "minutes" else "hours")
            }
            RoundIconButton("+") { minutes = (minutes + 5).coerceAtMost(FocusSession.MAX_MINUTES) }
        }

        Spacer(Modifier.height(40.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            PRESETS.forEach { p ->
                Chip(formatMinutes(p), selected = minutes == p, modifier = Modifier.weight(1f)) { minutes = p }
            }
        }
        Spacer(Modifier.height(12.dp))
        Label("Up to ${formatMinutes(FocusSession.MAX_MINUTES)}")

        Spacer(Modifier.weight(1f))

        val powerNote = if (LocalSettings.current.blockPowerMenu) " The power menu stays locked." else ""
        Body(
            if (extraAllowed > 0) {
                "Only Phone, WhatsApp and your $extraAllowed allowed " +
                    "${if (extraAllowed == 1) "app" else "apps"} will open until the timer ends.$powerNote"
            } else {
                "Only Phone and WhatsApp will open until the timer ends.$powerNote"
            },
            center = true,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Start") { confirming = true }
        Spacer(Modifier.height(20.dp))

        val count = FocusSession.sessionsCompleted(context)
        if (count > 0) {
            Label(
                "$count ${if (count == 1) "session" else "sessions"} · " +
                    "${formatMinutes((FocusSession.totalFocusMs(context) / MIN).toInt())} unplugged"
            )
        }
    }

    if (confirming) {
        Dialog(onDismissRequest = { confirming = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Paper)
                    .border(1.dp, Ink)
                    .padding(24.dp)
            ) {
                Text("Lock for ${formatMinutes(minutes)}?", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(12.dp))
                Body("There is no way to stop early. Calls and your allowed apps will still work.")
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlineButton("Cancel", Modifier.weight(1f)) { confirming = false }
                    PrimaryButton("Lock", Modifier.weight(1f)) {
                        confirming = false
                        onStart(minutes)
                    }
                }
            }
        }
    }
}
