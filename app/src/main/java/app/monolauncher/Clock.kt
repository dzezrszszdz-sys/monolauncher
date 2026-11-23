package app.monolauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.IntState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun rememberNow(): State<LocalDateTime> {
    val ctx = LocalContext.current
    val now = remember { mutableStateOf(LocalDateTime.now()) }

    DisposableEffect(ctx) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                now.value = LocalDateTime.now()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        now.value = LocalDateTime.now()
        onDispose { ctx.unregisterReceiver(receiver) }
    }
    LifecycleResumeEffect(Unit) {
        now.value = LocalDateTime.now()
        onPauseOrDispose { }
    }
    return now
}

private fun batteryPercent(intent: Intent?): Int {
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level < 0 || scale <= 0) -1 else level * 100 / scale
}

@Composable
fun rememberBattery(): IntState {
    val ctx = LocalContext.current
    val level = remember { mutableIntStateOf(-1) }

    DisposableEffect(ctx) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                level.intValue = batteryPercent(intent)
            }
        }
        val sticky = ContextCompat.registerReceiver(
            ctx, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        level.intValue = batteryPercent(sticky)
        onDispose { ctx.unregisterReceiver(receiver) }
    }
    return level
}

@Composable
fun ClockView(vm: LauncherViewModel) {
    val now by rememberNow()
    val battery by rememberBattery()

    val time = remember(now, vm.use24h) {
        now.format(DateTimeFormatter.ofPattern(if (vm.use24h) "HH:mm" else "h:mm"))
    }
    val date = remember(now) {
        now.format(DateTimeFormatter.ofPattern("MMM d.", Locale.getDefault()))
            .uppercase(Locale.getDefault())
    }
    val batteryText = if (battery >= 0) "$battery%" else ""

    val info = TextStyle(
        color = Ui.fg,
        fontSize = 13.sp,
        letterSpacing = 3.sp,
        fontWeight = FontWeight.Normal,
    )

    if (vm.clockStyle == 0) {
        // Nagy, vékony óra + alatta dátum és akkumulátor.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Crossfade(targetState = time, animationSpec = tween(350), label = "time") { t ->
                Text(
                    text = t,
                    color = Ui.fg,
                    fontSize = vm.clockSize.sp,
                    fontWeight = FontWeight(vm.clockWeight),
                    letterSpacing = (-1).sp,
                    maxLines = 1,
                )
            }
            if (vm.showDate || (vm.showBattery && batteryText.isNotEmpty())) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    if (vm.showDate) Text(date, style = info)
                    if (vm.showBattery && batteryText.isNotEmpty()) Text(batteryText, style = info)
                }
            }
        }
    } else {
        // Egysoros stílus, mint a referenciaképen: 17:00   AUG. 30.   100%
        Row(
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(time, style = info.copy(fontSize = 16.sp))
            if (vm.showDate) Text(date, style = info.copy(fontSize = 16.sp))
            if (vm.showBattery && batteryText.isNotEmpty()) Text(batteryText, style = info.copy(fontSize = 16.sp))
        }
    }
}
