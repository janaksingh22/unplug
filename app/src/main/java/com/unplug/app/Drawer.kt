package com.unplug.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private sealed interface DrawerRow {
    val key: String
}

private data class HeaderRow(val letter: Char) : DrawerRow {
    override val key: String get() = "header:$letter"
}

private data class EntryRow(val app: AppEntry) : DrawerRow {
    override val key: String get() = app.key
}

/**
 * All apps: search on top, A–Z list with section letters, and a letter scrubber on the right.
 * Pull the list down from the top (or drag the handle) to close.
 */
@Composable
fun AppDrawer(
    model: AppsModel,
    favorites: List<String>,
    query: String,
    onQuery: (String) -> Unit,
    drawer: DrawerController,
    onLaunch: (AppEntry, Rect) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    var activeLetter by remember { mutableStateOf<Char?>(null) }

    val hidden = LocalSettings.current.hiddenApps
    val apps = remember(model.apps, hidden) { model.apps.filter { it.packageName !in hidden } }
    val q = query.trim()
    val filtered = remember(apps, q) {
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
    val rows: List<DrawerRow> = remember(filtered, q) {
        if (q.isNotEmpty()) {
            filtered.map { EntryRow(it) }
        } else {
            buildList<DrawerRow> {
                var last: Char? = null
                filtered.forEach {
                    val letter = letterOf(it.label)
                    if (letter != last) {
                        add(HeaderRow(letter))
                        last = letter
                    }
                    add(EntryRow(it))
                }
            }
        }
    }
    val headerIndex = remember(rows) {
        buildMap<Char, Int> { rows.forEachIndexed { i, r -> if (r is HeaderRow) put(r.letter, i) } }
    }
    val letters = remember(headerIndex) { headerIndex.keys.toList() }

    // Scrolling the list puts the keyboard away.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    // Every time the drawer closes, reset it to the top.
    val closed by remember { derivedStateOf { drawer.progress == 0f } }
    LaunchedEffect(closed) {
        if (closed) {
            focusManager.clearFocus()
            listState.scrollToItem(0)
        }
    }

    // Lets an over-scroll at the top of the list drag the whole drawer down.
    val connection = remember(drawer) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && drawer.offset > 0f) return Offset(0f, drawer.dragBy(available.y))
                return Offset.Zero
            }

            @Suppress("DEPRECATION")
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.Drag) {
                    return Offset(0f, drawer.dragBy(available.y))
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drawer.offset > 0f) {
                    drawer.settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Column(
        modifier
            .background(Paper)
            .pointerInput(Unit) { detectTapGestures { } } // don't let taps fall through to home
            .safeDrawingPadding()
    ) {
        // Grab handle.
        Box(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .draggable(
                    state = rememberDraggableState { drawer.dragBy(it) },
                    orientation = Orientation.Vertical,
                    onDragStopped = { drawer.settle(it) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 36.dp, height = 3.dp)
                    .background(Faint, RoundedCornerShape(2.dp))
            )
        }

        SearchField(
            query = query,
            onQuery = onQuery,
            count = apps.size,
            onGo = { filtered.firstOrNull()?.let { onLaunch(it, Rect.Zero) } },
            onOpenSettings = onOpenSettings,
        )

        Spacer(Modifier.height(4.dp))

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .nestedScroll(connection),
                    contentPadding = PaddingValues(start = 28.dp, end = 8.dp, bottom = 24.dp),
                ) {
                    items(rows, key = { it.key }, contentType = { if (it is HeaderRow) 0 else 1 }) { row ->
                        when (row) {
                            is HeaderRow -> Text(
                                row.letter.toString(),
                                color = Dim,
                                fontSize = 12.sp,
                                fontFamily = Mono,
                                modifier = Modifier
                                    .animateItem()
                                    .padding(top = 18.dp, bottom = 4.dp),
                            )
                            is EntryRow -> AppItem(
                                app = row.app,
                                model = model,
                                pinned = row.app.key in favorites,
                                onLaunch = onLaunch,
                                onLongPress = onLongPress,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    if (rows.isEmpty() && apps.isNotEmpty()) {
                        item { Body("No apps match “$q”.", Modifier.padding(top = 24.dp)) }
                    }
                }

                if (q.isEmpty() && letters.size > 1) {
                    LetterScrubber(
                        letters = letters,
                        onPick = { ch -> headerIndex[ch]?.let { i -> scope.launch { listState.scrollToItem(i) } } },
                        onActive = { activeLetter = it },
                    )
                }
            }

            activeLetter?.let { letter ->
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(76.dp)
                        .background(Paper, CircleShape)
                        .border(1.dp, Ink, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(letter.toString(), color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Light)
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    count: Int,
    onGo: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = TextStyle(color = Ink, fontSize = 17.sp, fontFamily = Mono),
        cursorBrush = SolidColor(Ink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        decorationBox = { inner ->
            Column {
                Row(
                    Modifier.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = Dim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Label("Search $count apps")
                        inner()
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Clear",
                            tint = Dim,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onQuery("") },
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Launcher settings",
                            tint = Dim,
                            modifier = Modifier
                                .size(20.dp)
                                .tap(onClick = onOpenSettings),
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Faint)
                )
            }
        },
    )
}

@Composable
private fun AppItem(
    app: AppEntry,
    model: AppsModel,
    pinned: Boolean,
    onLaunch: (AppEntry, Rect) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier,
) {
    val iconStyle = LocalSettings.current.iconStyle
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val icon by produceState(model.cachedIcon(app), app.key) {
        if (value == null) value = model.icon(app)
    }
    Row(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .tap(onLongClick = { onLongPress(app) }) { onLaunch(app, bounds) }
            .padding(vertical = if (iconStyle == IconStyle.HIDDEN) 13.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconStyle != IconStyle.HIDDEN) {
            val bmp = icon
            if (bmp != null) {
                Image(
                    bmp,
                    contentDescription = null,
                    colorFilter = if (iconStyle == IconStyle.GRAYSCALE) Grayscale else null,
                    modifier = Modifier.size(34.dp),
                )
            } else {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(Faint, CircleShape)
                )
            }
            Spacer(Modifier.width(18.dp))
        }
        Text(
            app.label,
            color = Ink,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (pinned) {
            Box(
                Modifier
                    .size(4.dp)
                    .background(Ink, CircleShape)
            )
        }
    }
}

/** Drag along the letters to jump through the list, with a tick of haptics per letter. */
@Composable
private fun LetterScrubber(letters: List<Char>, onPick: (Char) -> Unit, onActive: (Char?) -> Unit) {
    val haptic = rememberHaptics()
    var current by remember { mutableStateOf<Char?>(null) }

    Column(
        Modifier
            .width(28.dp)
            .fillMaxHeight()
            .padding(vertical = 12.dp)
            .pointerInput(letters) {
                fun pick(y: Float) {
                    val i = (y / size.height * letters.size).toInt().coerceIn(0, letters.lastIndex)
                    val ch = letters[i]
                    if (ch != current) {
                        current = ch
                        onActive(ch)
                        onPick(ch)
                        haptic(HapticFeedbackType.TextHandleMove)
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pick(down.position.y)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        pick(change.position.y)
                    }
                    current = null
                    onActive(null)
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { ch ->
            Text(
                ch.toString(),
                color = if (ch == current) Ink else Dim,
                fontSize = 10.sp,
                fontFamily = Mono,
            )
        }
    }
}
