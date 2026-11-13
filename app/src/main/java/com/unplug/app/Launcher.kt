package com.unplug.app

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ================================================================
// Drawer physics: the drawer follows your finger, then springs open/closed.
// ================================================================

class DrawerController(private val scope: CoroutineScope) {
    /** Screen height in px; the drawer's fully-closed offset. */
    var height by mutableFloatStateOf(0f)
        private set

    /** 0 = fully open, [height] = fully closed. */
    var offset by mutableFloatStateOf(0f)
        private set

    private var job: Job? = null

    val progress: Float
        get() = if (height <= 0f) 0f else (1f - offset / height).coerceIn(0f, 1f)

    val isVisible: Boolean get() = height > 0f && offset < height

    fun setHeight(h: Float) {
        if (h <= 0f) return
        val wasClosed = height == 0f || offset >= height
        height = h
        if (wasClosed || offset > h) offset = h
    }

    /** Moves the drawer by [delta] px and returns how much it actually moved. */
    fun dragBy(delta: Float): Float {
        if (height <= 0f) return 0f
        job?.cancel()
        val old = offset
        offset = (old + delta).coerceIn(0f, height)
        return offset - old
    }

    fun settle(velocity: Float) {
        val open = when {
            velocity < -900f -> true
            velocity > 900f -> false
            else -> offset < height * 0.5f
        }
        animateTo(if (open) 0f else height, velocity)
    }

    fun expand() = animateTo(0f, 0f)
    fun collapse() = animateTo(height, 0f)

    fun snapClosed() {
        job?.cancel()
        offset = height
    }

    private fun animateTo(target: Float, velocity: Float) {
        job?.cancel()
        job = scope.launch {
            animate(
                initialValue = offset,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
            ) { value, _ -> offset = value }
        }
    }
}

// ================================================================
// Root
// ================================================================

@Composable
fun LauncherRoot(
    apps: AppsModel,
    settingsStore: SettingsStore,
    blockedAt: Long,
    blockedReason: String,
    blockedPackage: String?,
    homePressedAt: Long,
) {
    UnplugTheme(settingsStore.value) {
        SystemBarsEffect()
        LauncherContent(apps, settingsStore, blockedAt, blockedReason, blockedPackage, homePressedAt)
        // First launch: short walkthrough on top of everything.
        AnimatedVisibility(
            visible = !settingsStore.value.onboarded,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(300)),
        ) {
            Onboarding(onDone = { settingsStore.update { it.copy(onboarded = true) } })
        }
    }
}

/** Keeps status/nav bar icons and the window background in step with the theme. */
@Composable
private fun SystemBarsEffect() {
    val context = LocalContext.current
    val dark = LocalPalette.current.isDark
    val paper = Paper
    LaunchedEffect(dark) {
        val activity = context as? ComponentActivity ?: return@LaunchedEffect
        val style = if (dark) {
            SystemBarStyle.dark(android.graphics.Color.BLACK)
        } else {
            SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.BLACK)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        (context as Activity).window.setBackgroundDrawable(ColorDrawable(paper.toArgb()))
    }
}

@Composable
private fun LauncherContent(
    apps: AppsModel,
    settingsStore: SettingsStore,
    blockedAt: Long,
    blockedReason: String,
    blockedPackage: String?,
    homePressedAt: Long,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = rememberHaptics()
    val scope = rememberCoroutineScope()
    val drawer = remember { DrawerController(scope) }
    val settings = settingsStore.value

    var favorites by remember { mutableStateOf(Favorites.load(context)) }
    var query by remember { mutableStateOf("") }
    var focusSheet by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var statsOpen by remember { mutableStateOf(false) }
    var sosOpen by remember { mutableStateOf(false) }
    var unlockOpen by remember { mutableStateOf(false) }
    var pausePkg by remember { mutableStateOf<String?>(null) }
    var appMenu by remember { mutableStateOf<AppEntry?>(null) }
    var homeMenu by remember { mutableStateOf(false) }
    var isDefaultHome by remember { mutableStateOf(HomeRole.isDefault(context)) }
    var sessionVersion by remember { mutableIntStateOf(0) }

    // A clock that ticks exactly on each second.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1000L - System.currentTimeMillis() % 1000L)
            value = System.currentTimeMillis()
        }
    }

    val remaining = remember(now, sessionVersion) { FocusSession.remainingMs(context) }
    val active = remaining > 0
    val duration = remember(sessionVersion, active) { FocusSession.durationMs(context) }
    val serviceOn = remember(now) { BlockerService.isEnabled(context) }

    // Session start / finish side effects.
    var wasActive by remember { mutableStateOf(active) }
    var lastDuration by remember { mutableLongStateOf(duration) }
    var finishedMinutes by remember { mutableIntStateOf(0) }
    var finishedAt by remember { mutableLongStateOf(0L) }
    SideEffect { if (active) lastDuration = duration }
    LaunchedEffect(active) {
        if (wasActive && !active) {
            finishedMinutes = (lastDuration / MIN).toInt()
            finishedAt = System.currentTimeMillis()
        }
        if (active) {
            drawer.snapClosed()
            focusSheet = false
            settingsOpen = false
            statsOpen = false
            homeMenu = false
            appMenu = null
            query = ""
            NotificationSilencer.sweepNow()
        }
        if (wasActive != active) FocusWidget.updateAll(context)
        wasActive = active
    }

    // Why the blocker sent us here.
    LaunchedEffect(blockedAt) {
        if (blockedAt == 0L) return@LaunchedEffect
        when (blockedReason) {
            BlockerService.REASON_UNLOCK -> if (FocusSession.isActive(context)) unlockOpen = true
            BlockerService.REASON_PAUSE -> pausePkg = blockedPackage
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isDefaultHome = HomeRole.isDefault(context)
        favorites = Favorites.load(context)
        scope.launch { apps.refresh() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        drawer.snapClosed()
        query = ""
        appMenu = null
        homeMenu = false
        pausePkg = null
    }
    // Pressing Home while already home: tidy up.
    LaunchedEffect(homePressedAt) {
        if (homePressedAt > 0L) {
            drawer.collapse()
            focusSheet = false
            settingsOpen = false
            statsOpen = false
            appMenu = null
            homeMenu = false
            pausePkg = null
            query = ""
        }
    }

    val drawerVisible by remember { derivedStateOf { drawer.isVisible } }
    // A launcher never "exits" on Back; it only closes what's on top.
    // (Settings, stats and the pause screen have their own Back handlers.)
    BackHandler {
        when {
            focusSheet -> focusSheet = false
            drawerVisible -> drawer.collapse()
        }
    }

    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefaultHome = HomeRole.isDefault(context)
    }

    fun toggleFavorite(entry: AppEntry) {
        val next = when {
            entry.key in favorites -> favorites - entry.key
            favorites.size >= Favorites.MAX -> {
                Toast.makeText(context, "Home holds ${Favorites.MAX} apps", Toast.LENGTH_SHORT).show()
                return
            }
            else -> favorites + entry.key
        }
        favorites = next
        Favorites.save(context, next)
    }

    val labelOf: (String?) -> String = { pkg ->
        apps.apps.firstOrNull { it.packageName == pkg }?.label ?: "That app"
    }
    val onLaunch: (AppEntry, Rect) -> Unit = { entry, from -> launchApp(context, view, entry, from) }
    val onAppLongPress: (AppEntry) -> Unit = {
        haptic(HapticFeedbackType.LongPress)
        appMenu = it
    }

    val blocked = blockedAt > 0L && SystemClock.elapsedRealtime() - blockedAt < 5_000L
    val message: String? = when {
        blocked && blockedReason == BlockerService.REASON_LIMIT ->
            "${labelOf(blockedPackage)} has reached today's limit."
        active && blocked && blockedReason == BlockerService.REASON_POWER ->
            "Power menu is locked until the timer ends."
        active && blocked && blockedReason == BlockerService.REASON_APP ->
            "That app is locked until the timer ends."
        active -> "Unlocks at " + clockParts(context, now + remaining, settings.hourFormat)
            .let { (t, a) -> if (a != null) "$t $a" else t }
        finishedAt > 0L && now - finishedAt < 15_000L -> "Done. ${formatMinutes(finishedMinutes)} unplugged."
        else -> null
    }

    val favoriteEntries = remember(favorites, apps.apps) { favorites.mapNotNull { apps.byKey(it) } }
    val allowedEntries = remember(settings.exceptions, apps.apps) {
        apps.apps.filter { it.packageName in settings.exceptions }.distinctBy { it.packageName }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .onSizeChanged { drawer.setHeight(it.height.toFloat()) }
    ) {
        HomeScreen(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Home recedes as the drawer rises.
                    val p = drawer.progress
                    alpha = 1f - p
                    val s = 1f - 0.05f * p
                    scaleX = s
                    scaleY = s
                },
            now = now,
            active = active,
            remaining = remaining,
            duration = duration,
            favorites = favoriteEntries,
            allowed = allowedEntries,
            showPinHint = apps.apps.isNotEmpty(),
            isDefaultHome = isDefaultHome,
            message = message,
            drawer = drawer,
            onLaunch = onLaunch,
            onAppLongPress = onAppLongPress,
            onHomeLongPress = {
                haptic(HapticFeedbackType.LongPress)
                homeMenu = true
            },
            onFocus = {
                if (!active) {
                    haptic(HapticFeedbackType.LongPress)
                    focusSheet = true
                }
            },
            onSos = { sosOpen = true },
            onSetDefault = {
                val intent = HomeRole.requestIntent(context)
                if (intent != null) roleRequest.launch(intent) else openHomeSettings(context)
            },
        )

        if (!active) {
            AppDrawer(
                model = apps,
                favorites = favorites,
                query = query,
                onQuery = { query = it },
                drawer = drawer,
                onLaunch = onLaunch,
                onLongPress = onAppLongPress,
                onOpenSettings = { settingsOpen = true },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (drawer.height == 0f) 0f else 1f
                        translationY = drawer.offset
                    },
            )
        }

        SheetVisibility(focusSheet && !active) {
            FocusSheet(
                serviceOn = serviceOn,
                extraAllowed = allowedEntries.size,
                onClose = { focusSheet = false },
                onStart = { minutes ->
                    FocusSession.start(context, minutes * MIN)
                    sessionVersion++
                    haptic(HapticFeedbackType.LongPress)
                    focusSheet = false
                    finishedAt = 0L
                },
            )
        }

        SheetVisibility(settingsOpen && !active) {
            SettingsSheet(
                store = settingsStore,
                apps = apps,
                serviceOn = serviceOn,
                isDefaultHome = isDefaultHome,
                onOpenStats = { statsOpen = true },
                onClose = { settingsOpen = false },
            )
        }

        SheetVisibility(statsOpen && !active) {
            StatsSheet(onClose = { statsOpen = false })
        }

        SheetVisibility(pausePkg != null) {
            val pkg = pausePkg
            if (pkg != null) {
                PauseScreen(
                    appLabel = labelOf(pkg),
                    seconds = settings.pauseSeconds,
                    onOpen = {
                        Passes.grant(pkg, settings.pauseGraceMinutes)
                        pausePkg = null
                        context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                            runCatching { context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    },
                    onCancel = { pausePkg = null },
                )
            }
        }

        if (sosOpen) SosDialog(settings.emergencyNumber, onDismiss = { sosOpen = false })

        if (unlockOpen && active) {
            UnlockDialog(
                onUnlocked = {
                    unlockOpen = false
                    sessionVersion++
                    Toast.makeText(context, "Focus ended", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { unlockOpen = false },
            )
        }

        appMenu?.let { entry ->
            val pinned = entry.key in favorites
            ActionSheet(
                title = entry.label,
                actions = listOf(
                    (if (pinned) "Remove from home" else "Pin to home") to { toggleFavorite(entry) },
                    "App info" to { openAppInfo(context, entry.packageName) },
                    "Uninstall" to { uninstallApp(context, entry.packageName) },
                ),
                onDismiss = { appMenu = null },
            )
        }

        if (homeMenu && !active) {
            ActionSheet(
                title = "Unplug",
                actions = listOf(
                    "Launcher settings" to { settingsOpen = true },
                    "Your stats" to { statsOpen = true },
                    "Choose home app" to { openHomeSettings(context) },
                    "Focus blocker" to { openAccessibilitySettings(context) },
                ),
                onDismiss = { homeMenu = false },
            )
        }
    }
}

@Composable
private fun SheetVisibility(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(320)) { it / 4 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(240)) { it / 4 },
    ) { content() }
}

// ================================================================
// Home screen
// ================================================================

@Composable
private fun HomeScreen(
    modifier: Modifier,
    now: Long,
    active: Boolean,
    remaining: Long,
    duration: Long,
    favorites: List<AppEntry>,
    allowed: List<AppEntry>,
    showPinHint: Boolean,
    isDefaultHome: Boolean,
    message: String?,
    drawer: DrawerController,
    onLaunch: (AppEntry, Rect) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
    onHomeLongPress: () -> Unit,
    onFocus: () -> Unit,
    onSos: () -> Unit,
    onSetDefault: () -> Unit,
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val pullThreshold = with(LocalDensity.current) { 90.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }

    Column(
        modifier
            .pointerInput(active) {
                detectTapGestures(onLongPress = { if (!active) onHomeLongPress() })
            }
            .draggable(
                state = rememberDraggableState { delta ->
                    // Up opens the drawer; down (when it's closed) pulls the notification shade.
                    val used = drawer.dragBy(delta)
                    if (used == 0f && delta > 0f) pull += delta
                },
                orientation = Orientation.Vertical,
                enabled = !active,
                onDragStarted = { pull = 0f },
                onDragStopped = { velocity ->
                    if (pull > pullThreshold) BlockerService.openNotifications()
                    drawer.settle(velocity)
                    pull = 0f
                },
            )
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ---- status line ----
        val battery = remember(now / 30_000L) { batteryText(context) }
        Row(
            Modifier
                .fillMaxWidth()
                .height(26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(
                when {
                    active -> "Focus mode"
                    settings.showDate -> dateText(now)
                    else -> ""
                },
                color = Ink.copy(alpha = 0.85f),
            )
            Spacer(Modifier.weight(1f))
            if (settings.showBattery) Label(battery)
            if (active) {
                Spacer(Modifier.width(12.dp))
                SosPill(onSos)
            }
        }

        Spacer(Modifier.height(24.dp))

        // ---- watch / countdown ----
        val ringSize = (LocalConfiguration.current.screenWidthDp * 0.66f).dp.coerceAtMost(270.dp)
        if (active) {
            val focusTarget = if (duration > 0) 1f - remaining.toFloat() / duration else 0f
            val focus by animateFloatAsState(focusTarget, tween(600), label = "focus")
            TickRing(fraction = { focus }, modifier = Modifier.size(ringSize)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatCountdown(remaining),
                        color = Ink,
                        fontSize = if (remaining >= 3_600_000L) 36.sp else 48.sp,
                        style = digitsStyle(),
                        maxLines = 1,
                    )
                    Label("remaining")
                }
            }
        } else {
            ClockView(
                now = now,
                size = ringSize,
                modifier = Modifier.tap(onClick = { openClock(context) }),
            )
        }

        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .height(44.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = message, label = "message") { m ->
                if (m != null) Body(m, Modifier.fillMaxWidth(), center = true)
            }
        }

        // ---- pinned apps (or allowed apps during focus) ----
        Spacer(Modifier.weight(1f))
        if (!active) {
            AppList(favorites, onLaunch, onAppLongPress)
            if (favorites.isEmpty() && showPinHint) {
                Body("Swipe up for all apps.\nLong-press one to pin it here.", Modifier.fillMaxWidth())
            }
            if (!isDefaultHome) {
                Spacer(Modifier.height(16.dp))
                DefaultHomeBanner(onSetDefault)
            }
        } else if (allowed.isNotEmpty()) {
            Label("Also allowed", Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            AppList(allowed, onLaunch, onLongPress = null)
        } else {
            Body("Only Phone and WhatsApp until then.", center = true)
        }

        // ---- dock ----
        Spacer(Modifier.height(24.dp))
        Dock(active = active, onFocus = onFocus)

        Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
            if (!active) {
                Icon(
                    Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "All apps",
                    tint = Dim,
                    modifier = Modifier
                        .size(28.dp)
                        .tap(onClick = { drawer.expand() }),
                )
            }
        }
    }
}

@Composable
private fun AppList(
    entries: List<AppEntry>,
    onLaunch: (AppEntry, Rect) -> Unit,
    onLongPress: ((AppEntry) -> Unit)?,
) {
    Column(Modifier.fillMaxWidth()) {
        entries.forEachIndexed { i, entry ->
            key(entry.key) { AppRow(i, entry, onLaunch, onLongPress) }
        }
    }
}

@Composable
private fun AppRow(
    index: Int,
    entry: AppEntry,
    onLaunch: (AppEntry, Rect) -> Unit,
    onLongPress: ((AppEntry) -> Unit)?,
) {
    val textSize = LocalSettings.current.homeTextSize.sp
    var bounds by remember { mutableStateOf(Rect.Zero) }
    // Staggered slide-in when the home screen appears.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(45L * index)
        appear.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                translationX = (1f - appear.value) * 28.dp.toPx()
            }
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .tap(onLongClick = onLongPress?.let { f -> { f(entry) } }) { onLaunch(entry, bounds) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("%02d".format(index + 1), color = Dim, fontSize = 11.sp, fontFamily = Mono)
        Spacer(Modifier.width(18.dp))
        Text(
            entry.label,
            color = Ink,
            fontSize = textSize.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DefaultHomeBanner(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tap(onClick = onClick)
            .border(1.dp, Faint)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Make Unplug your home screen", color = Ink, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Label("Tap to set as default")
        }
        Text("→", color = Ink, fontSize = 18.sp)
    }
}

@Composable
private fun Dock(active: Boolean, onFocus: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockButton(onClick = { openPhone(context) }) {
            Icon(Icons.Outlined.Phone, contentDescription = "Phone", tint = Ink, modifier = Modifier.size(22.dp))
        }

        // Center: start focus (or a lock while it's running). Double ring for depth.
        Box(
            Modifier
                .tap(onClick = onFocus)
                .size(88.dp)
                .border(1.dp, Faint, CircleShape)
                .padding(8.dp)
                .border(1.dp, Ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (active) {
                Icon(Icons.Outlined.Lock, contentDescription = "Locked", tint = Ink, modifier = Modifier.size(24.dp))
            } else {
                PowerGlyph()
            }
        }

        DockButton(onClick = { openWhatsApp(context) }) { ChatGlyph() }
    }
}

@Composable
private fun DockButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .tap(onClick = onClick)
            .size(60.dp)
            .border(1.dp, Faint, CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}
