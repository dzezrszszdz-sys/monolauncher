package app.monolauncher

import android.os.Process
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Gyökér: képernyőváltás, A–Z sáv, keresés, menük. */
@Composable
fun LauncherRoot(vm: LauncherViewModel) {
    BackHandler(enabled = vm.searchOpen || vm.screen != Screen.HOME) {
        if (vm.searchOpen) vm.searchOpen = false else vm.screen = Screen.HOME
    }

    // Amíg egy app nyílik, a launcher finoman hátrébb húzódik és elhalványul (mélység-érzet).
    val launchScale by animateFloatAsState(
        if (vm.launching) 0.93f else 1f,
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "launchScale",
    )
    val launchAlpha by animateFloatAsState(
        if (vm.launching) 0.35f else 1f,
        spring(stiffness = Spring.StiffnessLow),
        label = "launchAlpha",
    )

    // Külön grafikai réteg csak az animáció alatt kell.
    val settled = launchScale == 1f && launchAlpha == 1f

    LaunchedEffect(Unit) {
        if (vm.startupMs < 0) {
            val elapsed = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
            if (elapsed < 15_000) vm.startupMs = elapsed
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .then(
                if (settled) Modifier else Modifier.graphicsLayer {
                    scaleX = launchScale
                    scaleY = launchScale
                    alpha = launchAlpha
                }
            )
            .background(Ui.bg)
    ) {
        val d = Dims(maxWidth, maxHeight)

        // Háttérkép (ha van) sötétítéssel; különben tiszta fekete.
        vm.wallpaper?.let { wp ->
            // A sötétítés színszűrővel történik: nincs külön, a teljes képernyőt takaró réteg.
            Image(
                bitmap = wp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.tint(Color.Black.copy(alpha = vm.wallpaperDim), BlendMode.SrcOver),
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedContent(
            targetState = vm.screen,
            transitionSpec = {
                val toAll = targetState == Screen.ALL
                (fadeIn(tween(320, easing = LinearOutSlowInEasing)) +
                    slideInVertically(tween(380, easing = FastOutSlowInEasing)) { if (toAll) it / 10 else -it / 10 } +
                    scaleIn(tween(380, easing = FastOutSlowInEasing), initialScale = 0.96f)) togetherWith
                    (fadeOut(tween(180)) + scaleOut(tween(280, easing = FastOutSlowInEasing), targetScale = 0.97f))
            },
            label = "screen",
        ) { s ->
            if (s == Screen.HOME) HomeScreen(vm, d) else AllAppsScreen(vm, d)
        }

        // --- A–Z sáv (a két képernyő közt megmarad, így a húzás nem szakad meg) ---
        val entries = vm.letters.size + 1
        val pitch = minOf(d.h * 0.0237f, d.h * 0.6f / entries)
        val railH = pitch * entries
        AlphabetRail(
            letters = vm.letters,
            active = if (vm.screen == Screen.HOME) STAR else vm.activeLetter,
            pitch = pitch,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = d.h * 0.645f - railH / 2, end = d.w * 0.07f - 18.dp),
            onSelect = { ch ->
                vm.railTouch = ch
                if (ch == STAR) {
                    vm.screen = Screen.HOME
                } else {
                    vm.requestLetter(ch)
                    vm.screen = Screen.ALL
                }
            },
            onRelease = {
                vm.railTouch = null
                vm.scrollRequest = null
            },
        )

        // --- nagy betű-buborék húzás közben ---
        AnimatedVisibility(
            visible = vm.railTouch != null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.8f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.9f),
        ) {
            Text(
                text = railGlyph(vm.railTouch ?: STAR),
                color = Ui.fg,
                fontSize = 110.sp,
                fontWeight = FontWeight.Thin,
            )
        }

        // --- keresés ---
        AnimatedVisibility(
            visible = vm.searchOpen,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
        ) {
            SearchOverlay(vm, d)
        }

        if (vm.perfUnlocked && (vm.showFps || vm.showMem || vm.showTemp)) {
            PerfOverlay(
                vm,
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, top = 4.dp),
            )
        }

        vm.menuApp?.let { AppMenuDialog(vm, it) }
        if (vm.settingsOpen) SettingsSheet(vm)
        if (vm.perfOpen) PerformanceSheet(vm)
    }
}

/** Főképernyő: óra + kedvencek. Húzás fel = összes app, le = keresés, oldalra = gyors appok. */
@Composable
fun HomeScreen(vm: LauncherViewModel, d: Dims) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val favs = vm.favoriteApps
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val listTop = d.h * 0.334f
    val maxRows = ((d.h - listTop - navBottom - 8.dp) / d.rowH).toInt().coerceAtLeast(1)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.settingsOpen = true
                })
            }
            .pointerInput(Unit) {
                var dx = 0f
                var dy = 0f
                val threshold = 72.dp.toPx()
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = {
                        if (abs(dy) > abs(dx)) {
                            if (dy < -threshold) {
                                vm.screen = Screen.ALL
                            } else if (dy > threshold) {
                                vm.searchOpen = true
                            }
                        } else {
                            if (dx < -threshold) {
                                vm.swipe(ctx, left = true)
                            } else if (dx > threshold) {
                                vm.swipe(ctx, left = false)
                            }
                        }
                    },
                    onDragCancel = { dx = 0f; dy = 0f },
                ) { change, amount ->
                    change.consume()
                    dx += amount.x
                    dy += amount.y
                }
            }
    ) {
        // Óra: a magasság 27%-a körül középre igazítva.
        Box(
            Modifier.fillMaxWidth().height(d.h * 0.54f),
            contentAlignment = Alignment.Center,
        ) {
            ClockView(vm)
        }

        Column(Modifier.fillMaxSize().padding(top = listTop)) {
            if (favs.isEmpty()) {
                Text(
                    text = "Még nincsenek kedvencek.\nHúzd felfelé az ujjad az összes alkalmazáshoz,\nmajd tartsd nyomva egy alkalmazás nevét.",
                    color = Ui.dim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Light,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(start = d.iconStart, top = 12.dp),
                )
            }
            favs.take(maxRows).forEach { app ->
                AppRow(
                    app = app,
                    mono = vm.monoIcons,
                    d = d,
                    onClick = { from -> vm.open(ctx, view, app.key, from) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.menuApp = app
                    },
                )
            }
        }
    }
}

/** Összes alkalmazás ábécé szerint, A–Z sávval és kereső-gombbal. */
@Composable
fun AllAppsScreen(vm: LauncherViewModel, d: Dims) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val apps = vm.visibleApps
    val listState = rememberLazyListState()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // A sávról érkező ugrási kérés.
    LaunchedEffect(vm.scrollRequest, apps) {
        vm.scrollRequest?.let { r ->
            val idx = apps.indexOfFirst { it.letter == r.letter }
            if (idx >= 0) listState.scrollToItem(idx)
        }
    }
    // Az aktuális betű követése a sávon.
    LaunchedEffect(listState, apps) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { i ->
            vm.activeLetter = apps.getOrNull(i)?.letter
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .padding(top = statusTop + 14.dp, start = d.iconStart, end = d.w * 0.14f)
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, Ui.line, RoundedCornerShape(22.dp))
                .clickable { vm.searchOpen = true }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Keresés", color = Ui.dim, fontSize = 14.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 6.dp, bottom = navBottom + 24.dp),
        ) {
            items(apps, key = { it.key }) { app ->
                AppRow(
                    app = app,
                    mono = vm.monoIcons,
                    d = d,
                    onClick = { from -> vm.open(ctx, view, app.key, from) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.menuApp = app
                    },
                )
            }
        }
    }
}
