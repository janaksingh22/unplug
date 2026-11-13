package com.unplug.app

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay

private enum class SettingsPage { MAIN, EXCEPTIONS, SCHEDULES, LIMITS, MINDFUL, HIDDEN }

/** Full-screen launcher settings. Never reachable while a focus session is running. */
@Composable
fun SettingsSheet(
    store: SettingsStore,
    apps: AppsModel,
    serviceOn: Boolean,
    isDefaultHome: Boolean,
    onOpenStats: () -> Unit,
    onClose: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    BackHandler {
        if (page != SettingsPage.MAIN) page = SettingsPage.MAIN else onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .pointerInput(Unit) { detectTapGestures { } } // block taps to the home screen below
            .safeDrawingPadding()
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != SettingsPage.MAIN
                (slideInHorizontally(tween(280)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(240)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(160)))
            },
            label = "settings-page",
        ) { current ->
            val back = { page = SettingsPage.MAIN }
            when (current) {
                SettingsPage.MAIN -> MainSettings(
                    store = store,
                    apps = apps,
                    serviceOn = serviceOn,
                    isDefaultHome = isDefaultHome,
                    onClose = onClose,
                    onOpen = { page = it },
                    onOpenStats = onOpenStats,
                )
                SettingsPage.EXCEPTIONS -> AppPickerPage(
                    title = "allowed apps.",
                    description = "These open during focus, along with Phone and WhatsApp. " +
                        "Settings and the app installer can never be allowed.",
                    apps = apps,
                    showFixed = true,
                    selected = store.value.exceptions,
                    onToggle = { pkg -> store.update { it.copy(exceptions = it.exceptions.toggle(pkg)) } },
                    onBack = back,
                )
                SettingsPage.MINDFUL -> AppPickerPage(
                    title = "pause first.",
                    description = "Opening these shows a ${store.value.pauseSeconds}-second pause first. " +
                        "Breathe, then decide.",
                    apps = apps,
                    selected = store.value.mindfulApps,
                    onToggle = { pkg -> store.update { it.copy(mindfulApps = it.mindfulApps.toggle(pkg)) } },
                    onBack = back,
                )
                SettingsPage.HIDDEN -> AppPickerPage(
                    title = "hidden apps.",
                    description = "Hidden apps don't appear in the drawer or in search. They still work.",
                    apps = apps,
                    includeEssentials = true,
                    selected = store.value.hiddenApps,
                    onToggle = { pkg -> store.update { it.copy(hiddenApps = it.hiddenApps.toggle(pkg)) } },
                    onBack = back,
                )
                SettingsPage.LIMITS -> LimitsPage(store, apps, back)
                SettingsPage.SCHEDULES -> SchedulesPage(store, back)
            }
        }
    }
}

private fun Set<String>.toggle(pkg: String): Set<String> = if (pkg in this) this - pkg else this + pkg

// ================================================================
// Main page
// ================================================================

@Composable
private fun MainSettings(
    store: SettingsStore,
    apps: AppsModel,
    serviceOn: Boolean,
    isDefaultHome: Boolean,
    onClose: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
    onOpenStats: () -> Unit,
) {
    val context = LocalContext.current
    val s = store.value
    val update = store::update

    // Permission statuses are re-read whenever we come back from system settings.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val usageAccess = remember(resumes) { UsageTracker.hasAccess(context) }
    val notificationAccess = remember(resumes) { hasNotificationAccess(context) }
    val adminActive = remember(resumes) { UnplugAdmin.isActive(context) }
    val spaceFont = remember { isSpaceFontAvailable(context) }

    var dialog by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Header(title = "settings.", onClose = onClose)

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(bottom = 40.dp)
        ) {
            // ---------------- Appearance ----------------
            Section("Appearance") {
                Caption("Theme")
                Segmented(ThemeMode.entries, s.theme, { it.label }) { v -> update { it.copy(theme = v) } }

                Caption("Font")
                FontPicker(
                    options = FontChoice.entries.filter { it != FontChoice.SPACE || spaceFont },
                    selected = s.font,
                ) { v -> update { it.copy(font = v) } }

                Caption("App icons")
                Segmented(IconStyle.entries, s.iconStyle, { it.label }) { v -> update { it.copy(iconStyle = v) } }

                Caption("Home text size")
                Segmented(HomeTextSize.entries, s.homeTextSize, { it.label }) { v -> update { it.copy(homeTextSize = v) } }

                Spacer(Modifier.height(6.dp))
                NavRow("Hidden apps", countLabel(s.hiddenApps.size, "hidden")) { onOpen(SettingsPage.HIDDEN) }
            }

            // ---------------- Watch ----------------
            Section("Watch") {
                val now by produceState(System.currentTimeMillis()) {
                    while (true) {
                        delay(1000L - System.currentTimeMillis() % 1000L)
                        value = System.currentTimeMillis()
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ClockView(now = now, size = 170.dp)
                }

                Caption("Face")
                Segmented(ClockFace.entries, s.clockFace, { it.label }) { v -> update { it.copy(clockFace = v) } }

                Caption("Weight")
                Segmented(ClockWeight.entries, s.clockWeight, { it.label }) { v -> update { it.copy(clockWeight = v) } }

                Caption("Hour format")
                Segmented(HourFormat.entries, s.hourFormat, { it.label }) { v -> update { it.copy(hourFormat = v) } }

                Spacer(Modifier.height(8.dp))
                ToggleRow("Show seconds", null, s.showSeconds) { v -> update { it.copy(showSeconds = v) } }
                ToggleRow("Show date", null, s.showDate) { v -> update { it.copy(showDate = v) } }
                ToggleRow("Show battery", null, s.showBattery) { v -> update { it.copy(showBattery = v) } }
            }

            // ---------------- Focus ----------------
            Section("Focus") {
                val extra = s.exceptions.count { pkg -> apps.apps.any { it.packageName == pkg } }
                NavRow("Allowed apps", "Phone, WhatsApp" + if (extra > 0) " + $extra more" else "") {
                    onOpen(SettingsPage.EXCEPTIONS)
                }
                NavRow(
                    "Schedules",
                    s.schedules.count { it.enabled }.let { if (it == 0) "None" else "$it active" },
                ) { onOpen(SettingsPage.SCHEDULES) }

                ToggleRow("Lock power menu", "Closes power off / restart while the timer runs", s.blockPowerMenu) { v ->
                    update { it.copy(blockPowerMenu = v) }
                }
                if (s.blockPowerMenu) {
                    Caption("Unlock power menu when battery is below")
                    Segmented(listOf(0, 5, 10, 15, 20), s.lowBatteryPercent, { if (it == 0) "Never" else "$it%" }) { v ->
                        update { it.copy(lowBatteryPercent = v) }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                ToggleRow("Silence other notifications", "Snoozed until the session ends", s.silenceNotifications) { v ->
                    update { it.copy(silenceNotifications = v) }
                    if (v && !notificationAccess) openNotificationAccessSettings(context)
                }
                if (s.silenceNotifications && !notificationAccess) {
                    InfoRow("Notification access", "Off — tap to allow") { openNotificationAccessSettings(context) }
                }

                InfoRow("SOS number", s.emergencyNumber) { dialog = "sos" }

                ToggleRow(
                    "Emergency unlock",
                    "Hold both volume keys for 5 s, then enter your password",
                    s.emergencyUnlock,
                ) { v -> update { it.copy(emergencyUnlock = v) } }
                InfoRow(
                    "Unlock password",
                    if (UnlockPassword.isDefault(context)) "Default" else "Custom",
                ) { dialog = "password" }

                ToggleRow(
                    "Uninstall protection",
                    "Unplug can't be uninstalled until you turn this off",
                    adminActive,
                ) { v ->
                    if (v) context.startActivity(UnplugAdmin.requestIntent(context).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    else {
                        UnplugAdmin.deactivate(context)
                        resumes++
                    }
                }

                InfoRow("Longest session", formatMinutes(FocusSession.MAX_MINUTES))
                InfoRow("Blocker", if (serviceOn) "On" else "Off — tap to turn on") { openAccessibilitySettings(context) }
            }

            // ---------------- Wellbeing ----------------
            Section("Wellbeing") {
                NavRow("Daily limits", countLabel(s.appLimits.size, "limited")) { onOpen(SettingsPage.LIMITS) }
                if (s.appLimits.isNotEmpty() && !usageAccess) {
                    InfoRow("Usage access", "Off — needed for limits") { UsageTracker.openAccessSettings(context) }
                }

                NavRow("Pause before opening", countLabel(s.mindfulApps.size, "apps")) { onOpen(SettingsPage.MINDFUL) }
                Caption("Pause length")
                Segmented(listOf(5, 10, 15, 30), s.pauseSeconds, { "${it}s" }) { v -> update { it.copy(pauseSeconds = v) } }
                Caption("Don't ask again for")
                Segmented(listOf(1, 5, 15, 30), s.pauseGraceMinutes, { "${it}m" }) { v ->
                    update { it.copy(pauseGraceMinutes = v) }
                }
                Caption("Daily focus goal")
                Segmented(listOf(15, 30, 60, 120, 240), s.dailyGoalMinutes, { formatMinutes(it) }) { v ->
                    update { it.copy(dailyGoalMinutes = v) }
                }
                Spacer(Modifier.height(6.dp))
                NavRow("Your stats", "Streak, last 7 days", onOpenStats)
            }

            // ---------------- General ----------------
            Section("General") {
                ToggleRow("Haptic feedback", null, s.haptics) { v -> update { it.copy(haptics = v) } }
                InfoRow("Home app", if (isDefaultHome) "Unplug" else "Not default — tap to change") {
                    openHomeSettings(context)
                }
                InfoRow("Phone settings", "") { openPhoneSettings(context) }
                InfoRow("Show welcome again", "") { update { it.copy(onboarded = false) } }
            }

            // ---------------- About ----------------
            Section("About") {
                AboutCard(context)
            }
        }
    }

    when (dialog) {
        "sos" -> SosNumberDialog(s.emergencyNumber, onDismiss = { dialog = null }) { n ->
            update { it.copy(emergencyNumber = n) }
            dialog = null
        }
        "password" -> PasswordDialog(onDismiss = { dialog = null })
    }
}

private fun countLabel(n: Int, word: String) = if (n == 0) "None" else "$n $word"

@Composable
private fun AboutCard(context: Context) {
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "1.0"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .border(1.dp, Faint)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PowerGlyph(Modifier.size(34.dp))
        Spacer(Modifier.height(16.dp))
        Text("unplug.", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text("by Janak", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(16.dp))
        Label("Version $version")
        Spacer(Modifier.height(16.dp))
        Body(
            "A calm home screen and a focus timer that keeps only what matters open. " +
                "Fully offline — no internet permission, no accounts, no tracking. " +
                "Everything stays on your phone.",
            center = true,
        )
        Spacer(Modifier.height(16.dp))
        Label("Made with focus · Janak")
    }
}

// ================================================================
// Dialogs
// ================================================================

@Composable
private fun SosNumberDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var number by remember { mutableStateOf(current) }
    FormDialog("SOS number", onDismiss) {
        Body("Called from the SOS button during focus. 112 works in most countries.")
        Spacer(Modifier.height(12.dp))
        MonoField(number, { v -> number = v.filter { it.isDigit() || it == '+' }.take(15) }, "Number", keyboardType = KeyboardType.Phone)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("112", "911", "999", "108").forEach { n ->
                Chip(n, selected = number == n, modifier = Modifier.weight(1f)) { number = n }
            }
        }
        Spacer(Modifier.height(24.dp))
        DialogButtons("Cancel", onDismiss, "Save") { if (number.isNotBlank()) onSave(number) }
    }
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val isDefault = remember { UnlockPassword.isDefault(context) }

    FormDialog("Unlock password", onDismiss) {
        Body(if (isDefault) "You're using the default password." else "You're using your own password.")
        Spacer(Modifier.height(12.dp))
        MonoField(current, { current = it }, "Current password", password = true)
        Spacer(Modifier.height(8.dp))
        MonoField(new, { new = it }, "New password (4+ characters)", password = true)
        Spacer(Modifier.height(8.dp))
        MonoField(confirm, { confirm = it }, "Repeat new password", password = true)
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Ink, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
        DialogButtons("Cancel", onDismiss, "Save") {
            error = when {
                new.length < 4 -> "New password needs at least 4 characters."
                new != confirm -> "New passwords don't match."
                !UnlockPassword.change(context, current, new) -> "Current password is wrong."
                else -> null
            }
            if (error == null) {
                Toast.makeText(context, "Password changed", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
        if (!isDefault) {
            TextLink("Go back to the default password") {
                if (UnlockPassword.reset(context, current)) {
                    Toast.makeText(context, "Default password restored", Toast.LENGTH_SHORT).show()
                    onDismiss()
                } else {
                    error = "Enter your current password first."
                }
            }
        }
    }
}

// ================================================================
// App pickers (allowed, pause-first, hidden)
// ================================================================

@Composable
private fun AppPickerPage(
    title: String,
    description: String,
    apps: AppsModel,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
    showFixed: Boolean = false,
    includeEssentials: Boolean = false,
) {
    val candidates = remember(apps.apps, includeEssentials) {
        apps.apps.filter {
            includeEssentials || (it.packageName !in AllowList.NEVER && !AllowList.isAlwaysAllowed(it.packageName))
        }.distinctBy { it.packageName }
    }
    Column(Modifier.fillMaxSize()) {
        BackHeader(title, onBack)
        Body(description, Modifier.padding(horizontal = 28.dp))
        Spacer(Modifier.height(12.dp))
        LazyColumn(contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 32.dp)) {
            if (showFixed) {
                item { FixedRow("Phone") }
                item { FixedRow("WhatsApp") }
                item { Spacer(Modifier.height(8.dp)) }
            }
            items(candidates, key = { it.key }) { app ->
                val checked = app.packageName in selected
                AppCheckRow(app, apps, trailing = { CheckBox(checked) }) { onToggle(app.packageName) }
            }
        }
    }
}

// ================================================================
// Daily limits
// ================================================================

@Composable
private fun LimitsPage(store: SettingsStore, apps: AppsModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val limits = store.value.appLimits
    var editing by remember { mutableStateOf<AppEntry?>(null) }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val usageAccess = remember(resumes) { UsageTracker.hasAccess(context) }

    val candidates = remember(apps.apps) {
        apps.apps.filter { it.packageName !in AllowList.NEVER }.distinctBy { it.packageName }
    }
    // Limited apps first.
    val sorted = remember(candidates, limits) { candidates.sortedBy { if (it.packageName in limits) 0 else 1 } }

    Column(Modifier.fillMaxSize()) {
        BackHeader("daily limits.", onBack)
        Body(
            "Once an app reaches its limit it closes for the rest of the day. Limits reset at midnight.",
            Modifier.padding(horizontal = 28.dp),
        )
        if (!usageAccess) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.padding(horizontal = 28.dp)) {
                InfoRow("Usage access", "Off — tap to allow") { UsageTracker.openAccessSettings(context) }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 32.dp)) {
            items(sorted, key = { it.key }) { app ->
                val minutes = limits[app.packageName]
                AppCheckRow(
                    app,
                    apps,
                    trailing = {
                        Text(
                            if (minutes == null) "No limit" else "${formatMinutes(minutes)} / day",
                            color = if (minutes == null) Dim else Ink,
                            fontSize = 13.sp,
                            fontFamily = Mono,
                        )
                    },
                ) { editing = app }
            }
        }
    }

    editing?.let { app ->
        LimitDialog(
            app = app,
            current = limits[app.packageName],
            onDismiss = { editing = null },
            onSave = { minutes ->
                store.update {
                    it.copy(appLimits = if (minutes == null) it.appLimits - app.packageName else it.appLimits + (app.packageName to minutes))
                }
                editing = null
            },
        )
    }
}

@Composable
private fun LimitDialog(app: AppEntry, current: Int?, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    var minutes by remember { mutableIntStateOf(current ?: 30) }
    FormDialog(app.label, onDismiss) {
        Label("Daily limit")
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Stepper(
                formatMinutes(minutes),
                onMinus = { minutes = (minutes - 5).coerceAtLeast(5) },
                onPlus = { minutes = (minutes + 5).coerceAtMost(8 * 60) },
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15, 30, 60, 120).forEach { m ->
                Chip(formatMinutes(m), selected = minutes == m, modifier = Modifier.weight(1f)) { minutes = m }
            }
        }
        Spacer(Modifier.height(24.dp))
        DialogButtons("Remove", { onSave(null) }, "Save") { onSave(minutes) }
    }
}

// ================================================================
// Schedules
// ================================================================

@Composable
private fun SchedulesPage(store: SettingsStore, onBack: () -> Unit) {
    val schedules = store.value.schedules
    var editing by remember { mutableStateOf<Schedule?>(null) }

    Column(Modifier.fillMaxSize()) {
        BackHeader("schedules.", onBack)
        Body(
            "Focus starts and ends on its own at these times — same rules as a normal session. " +
                "Each window is at most ${formatMinutes(FocusSession.MAX_MINUTES)}.",
            Modifier.padding(horizontal = 28.dp),
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 32.dp)) {
            items(schedules, key = { it.id }) { sch ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .tap(onClick = { editing = sch })
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${hhmm(sch.startMinute)} → ${hhmm(sch.endMinute)}",
                            color = if (sch.enabled) Ink else Dim,
                            fontSize = 22.sp,
                            style = digitsStyle(),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${daysLabel(sch.days)} · ${formatMinutes(sch.lengthMinutes.coerceAtMost(FocusSession.MAX_MINUTES))}",
                            color = Dim,
                            fontSize = 12.sp,
                        )
                    }
                    MonoSwitch(sch.enabled) { on ->
                        store.update { s -> s.copy(schedules = s.schedules.map { if (it.id == sch.id) it.copy(enabled = on) else it }) }
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                OutlineButton("Add schedule", Modifier.fillMaxWidth()) {
                    editing = Schedule(System.currentTimeMillis(), 22 * 60, 6 * 60, Schedule.EVERY_DAY)
                }
            }
        }
    }

    editing?.let { sch ->
        val isNew = schedules.none { it.id == sch.id }
        ScheduleDialog(
            initial = sch,
            isNew = isNew,
            onDismiss = { editing = null },
            onDelete = {
                store.update { s -> s.copy(schedules = s.schedules.filterNot { it.id == sch.id }) }
                editing = null
            },
            onSave = { saved ->
                store.update { s ->
                    s.copy(schedules = if (isNew) s.schedules + saved else s.schedules.map { if (it.id == saved.id) saved else it })
                }
                editing = null
            },
        )
    }
}

@Composable
private fun ScheduleDialog(
    initial: Schedule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Schedule) -> Unit,
) {
    var start by remember { mutableIntStateOf(initial.startMinute) }
    var end by remember { mutableIntStateOf(initial.endMinute) }
    var days by remember { mutableIntStateOf(initial.days) }
    val day = 24 * 60
    fun shift(v: Int, by: Int) = ((v + by) % day + day) % day
    val draft = initial.copy(startMinute = start, endMinute = end, days = days)

    FormDialog(if (isNew) "New schedule" else "Edit schedule", onDismiss) {
        Label("Starts")
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Stepper(hhmm(start), onMinus = { start = shift(start, -15) }, onPlus = { start = shift(start, 15) })
        }
        Spacer(Modifier.height(16.dp))
        Label("Ends")
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Stepper(hhmm(end), onMinus = { end = shift(end, -15) }, onPlus = { end = shift(end, 15) })
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, d ->
                val bit = 1 shl i
                Chip(d, selected = days and bit != 0, modifier = Modifier.weight(1f)) { days = days xor bit }
            }
        }
        Spacer(Modifier.height(10.dp))
        val length = draft.lengthMinutes
        Body(
            when {
                length == 0 -> "Start and end can't be the same."
                length > FocusSession.MAX_MINUTES -> "${formatMinutes(length)} — capped at ${formatMinutes(FocusSession.MAX_MINUTES)}."
                else -> "${formatMinutes(length)} · ${daysLabel(days)}"
            }
        )
        Spacer(Modifier.height(24.dp))
        DialogButtons(if (isNew) "Cancel" else "Delete", if (isNew) onDismiss else onDelete, "Save") {
            if (length > 0 && days != 0) onSave(draft)
        }
    }
}

// ================================================================
// Building blocks
// ================================================================

@Composable
private fun Header(title: String, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
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
}

@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.KeyboardArrowLeft,
            contentDescription = "Back",
            tint = Ink,
            modifier = Modifier
                .size(30.dp)
                .tap(onClick = onBack),
        )
        Spacer(Modifier.width(8.dp))
        Text(title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(28.dp))
    Label(title, color = Ink)
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Faint)
    )
    Column(Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun Caption(text: String) {
    Text(text, color = Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            Chip(label(option), selected = option == selected, modifier = Modifier.weight(1f)) { onSelect(option) }
        }
    }
}

/** Each option previews itself: "Aa" set in that font. */
@Composable
private fun FontPicker(options: List<FontChoice>, selected: FontChoice, onSelect: (FontChoice) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { font ->
            val on = font == selected
            Column(
                Modifier
                    .weight(1f)
                    .tap(onClick = { onSelect(font) })
                    .then(if (on) Modifier.background(Ink) else Modifier.border(1.dp, Faint))
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Aa", color = if (on) Paper else Ink, fontSize = 22.sp, fontFamily = font.family(context))
                Spacer(Modifier.height(4.dp))
                Text(font.label, color = if (on) Paper else Dim, fontSize = 8.sp, fontFamily = Mono, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tap(onClick = { onChange(!checked) })
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 16.sp)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = Dim, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        MonoSwitch(checked, onChange)
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tap(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Dim, fontSize = 12.sp)
        }
        Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = null, tint = Dim, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun InfoRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = Dim, fontSize = 13.sp, maxLines = 1)
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = null, tint = Dim, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun FixedRow(name: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, color = Ink, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Label("Always")
    }
}

@Composable
private fun CheckBox(checked: Boolean) {
    Box(
        Modifier
            .size(22.dp)
            .then(if (checked) Modifier.background(Ink) else Modifier.border(1.dp, Dim)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Outlined.Check, contentDescription = null, tint = Paper, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun AppCheckRow(
    app: AppEntry,
    apps: AppsModel,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val iconStyle = LocalSettings.current.iconStyle
    val icon by produceState(apps.cachedIcon(app), app.key) {
        if (value == null) value = apps.icon(app)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .tap(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconStyle != IconStyle.HIDDEN) {
            val bmp = icon
            if (bmp != null) {
                Image(
                    bmp,
                    contentDescription = null,
                    colorFilter = if (iconStyle == IconStyle.GRAYSCALE) Grayscale else null,
                    modifier = Modifier.size(30.dp),
                )
            } else {
                Box(
                    Modifier
                        .size(30.dp)
                        .background(Faint, CircleShape)
                )
            }
            Spacer(Modifier.width(16.dp))
        }
        Text(
            app.label,
            color = Ink,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}
