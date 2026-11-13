package com.unplug.app

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

private const val PAGES = 4

/** First-launch walkthrough: welcome → home screen → blocker → optional extras. */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(0) }

    // Re-read permission states whenever we come back from system settings.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val isHome = remember(resumes) { HomeRole.isDefault(context) }
    val blocker = remember(resumes) { BlockerService.isEnabled(context) }
    val notifications = remember(resumes) { hasNotificationAccess(context) }
    val usage = remember(resumes) { UsageTracker.hasAccess(context) }
    val admin = remember(resumes) { UnplugAdmin.isActive(context) }

    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { resumes++ }

    BackHandler { if (page > 0) page-- }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .pointerInput(Unit) { detectTapGestures { } }
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label("${page + 1} / $PAGES")
            Spacer(Modifier.weight(1f))
            if (page < PAGES - 1) {
                Text("Skip", color = Dim, fontSize = 14.sp, modifier = Modifier.tap(onClick = onDone))
            }
        }

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(320)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(240)))
                    .togetherWith(slideOutHorizontally(tween(260)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(180)))
            },
            modifier = Modifier.weight(1f),
            label = "onboarding",
        ) { p ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                when (p) {
                    0 -> {
                        PowerGlyph(Modifier.size(56.dp))
                        Spacer(Modifier.height(28.dp))
                        Text("unplug.", color = Ink, fontSize = 44.sp, fontWeight = FontWeight.Medium)
                        Text("by Janak", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Light)
                        Spacer(Modifier.height(24.dp))
                        Body(
                            "A calm home screen that helps you put your phone down.\n\n" +
                                "Start a focus session and only Phone, WhatsApp and the apps you choose will open " +
                                "until the timer ends. Everything stays on your phone — no internet, ever."
                        )
                    }
                    1 -> Step(
                        title = "Make it your home screen.",
                        body = "Unplug replaces your launcher, so the first thing you see is calm — not a grid of apps. " +
                            "You can switch back any time in settings.",
                        done = isHome,
                        action = "Set as home screen",
                    ) {
                        val intent = HomeRole.requestIntent(context)
                        if (intent != null) roleRequest.launch(intent) else openHomeSettings(context)
                    }
                    2 -> Step(
                        title = "Turn on the blocker.",
                        body = "Find Unplug under Accessibility and switch it on. It only sees which app is open — " +
                            "it's what blocks apps, locks the power menu and listens for the emergency-unlock keys.\n\n" +
                            "Switch greyed out? Settings → Apps → Unplug → ⋮ → Allow restricted settings.",
                        done = blocker,
                        action = "Open Accessibility",
                    ) { openAccessibilitySettings(context) }
                    else -> {
                        Text("A few extras.", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Light)
                        Spacer(Modifier.height(12.dp))
                        Body("All optional. You can change these later in Launcher settings.")
                        Spacer(Modifier.height(24.dp))
                        ExtraRow("Silence notifications", "Snooze other apps during focus", notifications) {
                            openNotificationAccessSettings(context)
                        }
                        ExtraRow("Daily limits", "Needs usage access", usage) { UsageTracker.openAccessSettings(context) }
                        ExtraRow("Uninstall protection", "Stops Unplug being removed", admin) {
                            context.startActivity(UnplugAdmin.requestIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }
            }
        }

        // Progress dots.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(PAGES) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (i == page) 8.dp else 6.dp)
                        .then(if (i == page) Modifier.background(Ink, CircleShape) else Modifier.border(1.dp, Dim, CircleShape))
                )
            }
        }
        PrimaryButton(if (page == PAGES - 1) "Start" else if (page == 0) "Begin" else "Next") {
            if (page < PAGES - 1) page++ else onDone()
        }
    }
}

@Composable
private fun Step(title: String, body: String, done: Boolean, action: String, onAction: () -> Unit) {
    Text(title, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Light)
    Spacer(Modifier.height(16.dp))
    Body(body)
    Spacer(Modifier.height(28.dp))
    if (done) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(Ink, CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Label("Done", color = Ink)
        }
    } else {
        OutlineButton(action, Modifier.fillMaxWidth(), onAction)
    }
}

@Composable
private fun ExtraRow(title: String, subtitle: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tap(onClick = { if (!on) onClick() })
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 16.sp)
            Text(subtitle, color = Dim, fontSize = 12.sp)
        }
        Label(if (on) "On" else "Set up", color = if (on) Ink else Dim)
    }
}
