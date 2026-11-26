package app.monolauncher

import android.app.ActivityManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.FrameMetrics
import android.view.Window
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
//  Mérések
// ---------------------------------------------------------------------------------------------

data class FrameSample(val frames: Int, val janks: Int, val worstMs: Float)

/**
 * A ténylegesen kirajzolt képkockákat méri (Window.OnFrameMetricsAvailableListener).
 * Nem kényszerít extra képkockákat, ezért az akkumulátort gyakorlatilag nem terheli;
 * ha a képernyő áll, az FPS 0 – ez a helyes érték (nem rajzol semmit).
 */
class FrameMonitor(private val window: Window) {
    private var frames = 0
    private var janks = 0
    private var worstNs = 0L
    private var running = false
    private val handler = Handler(Looper.getMainLooper())

    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
        frames++
        val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        val deadline = metrics.getMetric(FrameMetrics.DEADLINE)
        if (total > deadline) janks++
        janks += dropped
        if (total > worstNs) worstNs = total
    }

    fun start() {
        if (running) return
        window.addOnFrameMetricsAvailableListener(listener, handler)
        running = true
    }

    fun stop() {
        if (!running) return
        window.removeOnFrameMetricsAvailableListener(listener)
        running = false
    }

    /** Az előző mintavétel óta összegyűlt adatok; nullázza a számlálókat. */
    fun sample(): FrameSample {
        val s = FrameSample(frames, janks, worstNs / 1_000_000f)
        frames = 0
        janks = 0
        worstNs = 0
        return s
    }
}

object PerfInfo {
    private const val MB = 1024L * 1024L

    fun heapMb(): Long {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory() + Debug.getNativeHeapAllocatedSize()) / MB
    }

    private fun batteryIntent(ctx: Context): Intent? =
        ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    fun batteryTempC(ctx: Context): String {
        val t = batteryIntent(ctx)?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return if (t == null || t == Int.MIN_VALUE) "?" else "%.1f".format(t / 10f)
    }

    private fun thermalName(ctx: Context): String {
        val pm = ctx.getSystemService(PowerManager::class.java) ?: return "?"
        return when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "normál"
            PowerManager.THERMAL_STATUS_LIGHT -> "enyhe"
            PowerManager.THERMAL_STATUS_MODERATE -> "közepes"
            PowerManager.THERMAL_STATUS_SEVERE -> "súlyos"
            PowerManager.THERMAL_STATUS_CRITICAL -> "kritikus"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "vészhelyzet"
            else -> "leállás előtt"
        }
    }

    fun report(ctx: Context, vm: LauncherViewModel): String {
        val rt = Runtime.getRuntime()
        val usedHeap = (rt.totalMemory() - rt.freeMemory()) / MB
        val maxHeap = rt.maxMemory() / MB
        val native = Debug.getNativeHeapAllocatedSize() / MB
        val mi = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val pss = mi.totalPss / 1024
        val sys = ActivityManager.MemoryInfo()
        ctx.getSystemService(ActivityManager::class.java)?.getMemoryInfo(sys)
        val refresh = try { ctx.display?.refreshRate ?: 0f } catch (e: Exception) { 0f }
        val level = batteryPercent(batteryIntent(ctx))
        val version = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName
        } catch (e: Exception) { "?" }

        return buildString {
            appendLine("Mono Launcher $version")
            appendLine("Eszköz: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Kijelző frissítés: ${"%.0f".format(refresh)} Hz")
            appendLine()
            appendLine("Indulás (hidegindítás): " + if (vm.startupMs >= 0) "${vm.startupMs} ms" else "nincs adat (melegindítás)")
            appendLine("Alkalmazáslista: ${vm.apps.size} app, ${vm.appListMs} ms")
            val n = IconLoader.count()
            appendLine("Ikonbetöltés: " + if (n > 0) "%.1f ms / ikon (%d db)".format(IconLoader.avgMs(), n) else "nincs adat")
            appendLine()
            appendLine("Launcher memória: heap $usedHeap / $maxHeap MB, natív $native MB, PSS $pss MB")
            appendLine("Rendszer RAM: szabad ${sys.availMem / MB} / ${sys.totalMem / MB} MB" + if (sys.lowMemory) " (KEVÉS!)" else "")
            appendLine("Akkumulátor: ${if (level >= 0) "$level%" else "?"}, ${batteryTempC(ctx)} °C")
            appendLine("Hőállapot: ${thermalName(ctx)}")
            vm.lastTestResult?.let {
                appendLine()
                appendLine("Animációteszt: $it")
            }
        }.trimEnd()
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

// ---------------------------------------------------------------------------------------------
//  Kis kijelző a képernyő sarkában
// ---------------------------------------------------------------------------------------------

@Composable
fun PerfOverlay(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val window = ctx.findActivity()?.window ?: return
    val monitor = remember(window) { FrameMonitor(window) }
    var line by remember { mutableStateOf("") }

    DisposableEffect(monitor) {
        monitor.start()
        onDispose { monitor.stop() }
    }
    LaunchedEffect(vm.showFps, vm.showMem, vm.showTemp) {
        while (true) {
            val s = monitor.sample()
            val parts = mutableListOf<String>()
            if (vm.showFps) {
                parts += "FPS ${s.frames}" + if (s.janks > 0) " · akadás ${s.janks}" else ""
            }
            if (vm.showMem) parts += "RAM ${PerfInfo.heapMb()} MB"
            if (vm.showTemp) parts += "${PerfInfo.batteryTempC(ctx)} °C"
            line = parts.joinToString("  ·  ")
            delay(1000)
        }
    }

    Text(
        text = line,
        color = Ui.fg,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

// ---------------------------------------------------------------------------------------------
//  Animációteszt
// ---------------------------------------------------------------------------------------------

@Composable
fun AnimationTest(onFinished: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val refresh = remember { try { ctx.display?.refreshRate ?: 60f } catch (e: Exception) { 60f } }
    var timeMs by remember { mutableLongStateOf(0L) }
    var progress by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val nominal = (1_000_000_000L / refresh).toLong()
        val duration = 10_000_000_000L
        var start = 0L
        var last = 0L
        var frames = 0
        var janks = 0
        var worst = 0L
        while (true) {
            val t = androidx.compose.runtime.withFrameNanos { it }
            if (start == 0L) start = t
            if (last != 0L) {
                val dt = t - last
                frames++
                if (dt > nominal * 3 / 2) janks++
                if (dt > worst) worst = dt
            }
            last = t
            timeMs = (t - start) / 1_000_000
            progress = ((t - start).toFloat() / duration).coerceIn(0f, 1f)
            if (t - start >= duration) break
        }
        val seconds = (last - start) / 1e9
        val fps = if (seconds > 0) frames / seconds else 0.0
        val text = "átlag %.1f FPS (kijelző: %.0f Hz), legrosszabb képkocka %.1f ms, akadás %d / %d".format(
            fps, refresh, worst / 1e6, janks, frames
        )
        result = text
        onFinished(text)
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Canvas(Modifier.fillMaxSize()) {
                val tt = timeMs / 1000f
                for (i in 0 until 300) {
                    val a = tt * (0.6f + (i % 7) * 0.15f) + i
                    val x = (sin(a) * 0.45f + 0.5f) * size.width
                    val y = (cos(a * 0.8f + i * 0.3f) * 0.45f + 0.5f) * size.height
                    drawCircle(
                        color = Color.White.copy(alpha = 0.15f + (i % 5) * 0.12f),
                        radius = 6f + (i % 4) * 5f,
                        center = Offset(x, y),
                    )
                }
                // haladás-sáv alul
                drawRect(
                    color = Color.White,
                    topLeft = Offset(0f, size.height - 6f),
                    size = Size(size.width * progress, 6f),
                )
            }
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (result == null) {
                    Text("Animációteszt fut…", color = Ui.fg, fontSize = 16.sp, fontWeight = FontWeight.Light)
                } else {
                    Text("Kész", color = Ui.fg, fontSize = 22.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.height(12.dp))
                    Text(result ?: "", color = Ui.fg, fontSize = 14.sp)
                    Spacer(Modifier.height(24.dp))
                    SettingLink("Bezárás", "", onClose)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
//  Mono Performance oldal
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceSheet(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var testing by remember { mutableStateOf(false) }
    val report = remember(refreshKey, vm.lastTestResult) { PerfInfo.report(ctx, vm) }

    ModalBottomSheet(
        onDismissRequest = { vm.perfOpen = false },
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
            Text("Mono Performance", color = Ui.fg, fontSize = 22.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(16.dp))

            Label("Kijelzők")
            SettingSwitch("FPS mutató", vm.showFps, vm::updateShowFps)
            SettingSwitch("Memória (RAM)", vm.showMem, vm::updateShowMem)
            SettingSwitch("Akkumulátor hőmérséklet", vm.showTemp, vm::updateShowTemp)
            Text(
                "A mutató a képernyő bal felső sarkában jelenik meg, csak a launcherben látszik.",
                color = Ui.dim, fontSize = 12.sp, fontWeight = FontWeight.Light, lineHeight = 18.sp,
            )

            Spacer(Modifier.height(8.dp))
            Label("Teszt")
            SettingLink("Animációteszt", "10 mp") { testing = true }

            Spacer(Modifier.height(8.dp))
            Label("Diagnosztika")
            Text(
                text = report,
                color = Ui.fg,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(8.dp))
            SettingLink("Frissítés", "") { refreshKey++ }
            SettingLink("Másolás vágólapra", "") {
                clipboard.setText(AnnotatedString(report))
                Toast.makeText(ctx, "Másolva", Toast.LENGTH_SHORT).show()
            }

            Spacer(Modifier.height(8.dp))
            SettingLink("Mono Performance elrejtése", "") {
                vm.lockPerf()
                vm.perfOpen = false
            }
        }
    }

    if (testing) {
        AnimationTest(
            onFinished = { vm.lastTestResult = it },
            onClose = { testing = false },
        )
    }
}
