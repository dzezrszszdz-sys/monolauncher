package app.monolauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SearchOverlay(vm: LauncherViewModel, d: Dims) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }

    val results = remember(query, vm.visibleApps) {
        val q = normalize(query.trim())
        if (q.isEmpty()) emptyList()
        else vm.visibleApps
            .filter { it.sortKey.contains(q) }
            .sortedBy { if (it.sortKey.startsWith(q)) 0 else 1 }
    }

    fun open(app: AppInfo, from: Rect? = null) {
        keyboard?.hide()
        vm.searchOpen = false
        vm.open(ctx, view, app.key, from)
    }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ui.bg.copy(alpha = 0.98f))
            .pointerInput(Unit) { detectTapGestures { } }   // elnyeli az alatta lévő érintéseket
            .statusBarsPadding()
            .imePadding()
    ) {
        Spacer(Modifier.height(24.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Ui.fg, fontSize = 30.sp, fontWeight = FontWeight.Thin),
            cursorBrush = SolidColor(Ui.fg),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let { open(it) } }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.iconStart)
                .focusRequester(focus),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Keresés", color = Ui.faint, fontSize = 30.sp, fontWeight = FontWeight.Thin)
                }
                inner()
            },
        )
        Spacer(Modifier.height(20.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { it.key }) { app ->
                AppRow(
                    app = app,
                    mono = vm.monoIcons,
                    d = d,
                    onClick = { from -> open(app, from) },
                    onLongClick = { vm.menuApp = app },
                )
            }
        }
    }
}
