package app.monolauncher

import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: LauncherViewModel) {
    var dialog by remember { mutableIntStateOf(-1) }   // 0 = bal, 1 = jobb, 2 = elrejtettek
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setWallpaper(uri)
    }

    ModalBottomSheet(
        onDismissRequest = { vm.settingsOpen = false },
        containerColor = Ui.card,
        contentColor = Ui.fg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Ui.faint) },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("Beállítások", color = Ui.fg, fontSize = 22.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(20.dp))

            Label("Óra stílusa")
            PillRow(
                options = listOf("Nagy", "Egysoros", "Két soros", "Körvonal", "Analóg"),
                selected = vm.clockStyle,
                onSelect = vm::updateClockStyle,
            )

            Label("Betűvastagság")
            PillRow(
                options = listOf("Ultra vékony", "Vékony", "Könnyű"),
                selected = when (vm.clockWeight) { 100 -> 0; 200 -> 1; else -> 2 },
                onSelect = { vm.updateClockWeight(listOf(100, 200, 300)[it]) },
            )

            if (vm.clockStyle != 1) {
                Label("Óra mérete")
                Slider(
                    value = vm.clockSize,
                    onValueChange = vm::updateClockSize,
                    onValueChangeFinished = vm::commitClockSize,
                    valueRange = 48f..110f,
                    colors = SliderDefaults.colors(
                        thumbColor = Ui.fg,
                        activeTrackColor = Ui.fg,
                        inactiveTrackColor = Ui.faint,
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))
            SettingSwitch("24 órás idő", vm.use24h, vm::updateUse24h)
            SettingSwitch("Dátum megjelenítése", vm.showDate, vm::updateShowDate)
            SettingSwitch("Akkumulátor százalék", vm.showBattery, vm::updateShowBattery)
            SettingSwitch("Egyszínű (fehér) ikonok", vm.monoIcons, vm::updateMonoIcons)

            Spacer(Modifier.height(8.dp))
            Label("Háttér")
            SettingLink("Háttérkép", if (vm.wallpaperMode == 1) "Csere" else "Saját kép választása") {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            if (vm.wallpaperMode != 0) {
                SettingLink("Alapértelmezett háttér", "Visszaállítás") { vm.resetWallpaper() }
            }
            if (vm.wallpaperMode != 2) {
                SettingLink("Háttér kikapcsolása", "Fekete") { vm.clearWallpaper() }
            }
            if (vm.wallpaper != null) {
                Text("Sötétítés", color = Ui.fg, fontSize = 15.sp)
                Slider(
                    value = vm.wallpaperDim,
                    onValueChange = vm::updateWallpaperDim,
                    onValueChangeFinished = vm::commitWallpaperDim,
                    valueRange = 0f..0.9f,
                    colors = SliderDefaults.colors(
                        thumbColor = Ui.fg,
                        activeTrackColor = Ui.fg,
                        inactiveTrackColor = Ui.faint,
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))
            Label("Gesztusok")
            Text(
                "Fel: összes alkalmazás  ·  Le: keresés",
                color = Ui.dim, fontSize = 13.sp, fontWeight = FontWeight.Light,
            )
            Spacer(Modifier.height(4.dp))
            SettingLink("Balra húzás", appName(vm, vm.swipeLeftApp, "Kamera")) { dialog = 0 }
            SettingLink("Jobbra húzás", appName(vm, vm.swipeRightApp, "Telefon")) { dialog = 1 }

            val hiddenCount = vm.hidden.count { k -> vm.apps.any { it.key == k } }
            SettingLink("Elrejtett alkalmazások", hiddenCount.toString()) {
                if (hiddenCount > 0) dialog = 2
            }

            if (vm.perfUnlocked) {
                SettingLink("Mono Performance", "Megnyitás") {
                    vm.settingsOpen = false
                    vm.perfOpen = true
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Alapértelmezett kezdőképernyővé tétel: Beállítások → Alkalmazások → " +
                    "Alapértelmezett alkalmazások → Kezdőképernyő alkalmazás.",
                color = Ui.dim, fontSize = 12.sp, fontWeight = FontWeight.Light, lineHeight = 18.sp,
            )

            // 7 koppintás a verziószámra: feloldja a rejtett Mono Performance oldalt.
            val ctx = LocalContext.current
            val version = remember {
                try {
                    ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0)).versionName ?: ""
                } catch (e: Exception) { "" }
            }
            var taps by remember { mutableIntStateOf(0) }
            var lastTap by remember { mutableLongStateOf(0L) }
            Text(
                "Mono Launcher $version",
                color = Ui.faint,
                fontSize = 11.sp,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        val now = SystemClock.elapsedRealtime()
                        taps = if (now - lastTap > 1500) 1 else taps + 1
                        lastTap = now
                        if (taps >= 7) {
                            taps = 0
                            if (!vm.perfUnlocked) {
                                vm.unlockPerf()
                                Toast.makeText(ctx, "Mono Performance feloldva", Toast.LENGTH_SHORT).show()
                            }
                        } else if (taps >= 4 && !vm.perfUnlocked) {
                            Toast.makeText(ctx, "Még ${7 - taps} koppintás…", Toast.LENGTH_SHORT).show()
                        }
                    },
            )
        }
    }

    when (dialog) {
        0 -> AppPickerDialog("Balra húzás", vm.visibleApps, "Alapértelmezett (Kamera)",
            onPick = { vm.updateSwipeLeft(it?.key); dialog = -1 }, onDismiss = { dialog = -1 })
        1 -> AppPickerDialog("Jobbra húzás", vm.visibleApps, "Alapértelmezett (Telefon)",
            onPick = { vm.updateSwipeRight(it?.key); dialog = -1 }, onDismiss = { dialog = -1 })
        2 -> AppPickerDialog(
            "Elrejtett alkalmazások – koppints a visszahozáshoz",
            vm.apps.filter { it.key in vm.hidden }, null,
            onPick = { it?.let { a -> vm.unhide(a.key) }; dialog = -1 },
            onDismiss = { dialog = -1 },
        )
    }
}

private fun appName(vm: LauncherViewModel, key: String?, fallback: String): String =
    vm.apps.firstOrNull { it.key == key }?.label ?: fallback

@Composable
fun Label(text: String) {
    Text(
        text = text.uppercase(),
        color = Ui.dim,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PillRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { i, text ->
            val on = i == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) Ui.fg else Color.Transparent)
                    .border(1.dp, if (on) Ui.fg else Ui.line, RoundedCornerShape(20.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, color = if (on) Color.Black else Ui.fg, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SettingSwitch(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, color = Ui.fg, fontSize = 15.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Ui.fg,
                checkedBorderColor = Ui.fg,
                uncheckedThumbColor = Ui.dim,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = Ui.dim,
            ),
        )
    }
}

@Composable
fun SettingLink(text: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, color = Ui.fg, fontSize = 15.sp)
        Text(value, color = Ui.dim, fontSize = 14.sp)
    }
}
