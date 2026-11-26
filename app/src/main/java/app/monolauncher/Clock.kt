package app.monolauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun rememberNow(): State<LocalDateTime> {
    val ctx = LocalContext.current
    val now = remember { mutableStateOf(LocalDateTime.now()) }

    // Csak akkor figyeli a percenkénti jelet, amikor a launcher látszik – háttérben nem ébreszti az appot.
    LifecycleStartEffect(ctx) {
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
        onStopOrDispose { ctx.unregisterReceiver(receiver) }
    }
    return now
}

fun batteryPercent(intent: Intent?): Int {
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level < 0 || scale <= 0) -1 else level * 100 / scale
}

@Composable
fun rememberBattery(): IntState {
    val ctx = LocalContext.current
    val level = remember { mutableIntStateOf(-1) }

    LifecycleStartEffect(ctx) {
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
        onStopOrDispose { ctx.unregisterReceiver(receiver) }
    }
    return level
}

/** Óra-stílusok: 0 nagy, 1 egysoros, 2 két soros, 3 körvonal, 4 analóg. */
@Composable
fun ClockView(vm: LauncherViewModel) {
    val now by rememberNow()
    val battery by rememberBattery()

    val time = remember(now, vm.use24h) {
        now.format(DateTimeFormatter.ofPattern(if (vm.use24h) "HH:mm" else "h:mm"))
    }
    val hours = remember(now, vm.use24h) {
        now.format(DateTimeFormatter.ofPattern(if (vm.use24h) "HH" else "h"))
    }
    val minutes = remember(now) { now.format(DateTimeFormatter.ofPattern("mm")) }
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
    val size = vm.clockSize.sp
    val weight = FontWeight(vm.clockWeight)
    val font = remember(vm.clockWeight) { clockFont(vm.clockWeight) }
    val boldFont = remember { clockFont(700) }

    @Composable
    fun InfoRow() {
        if (vm.showDate || (vm.showBattery && batteryText.isNotEmpty())) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                if (vm.showDate) Text(date, style = info)
                if (vm.showBattery && batteryText.isNotEmpty()) Text(batteryText, style = info)
            }
        }
    }

    when (vm.clockStyle) {
        1 -> {
            // Egysoros, mint a referenciaképen: 17:00   AUG. 30.   100%
            Row(
                horizontalArrangement = Arrangement.spacedBy(26.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(time, style = info.copy(fontSize = 16.sp))
                if (vm.showDate) Text(date, style = info.copy(fontSize = 16.sp))
                if (vm.showBattery && batteryText.isNotEmpty()) {
                    Text(batteryText, style = info.copy(fontSize = 16.sp))
                }
            }
        }

        2 -> {
            // Két soros: óra felül, perc alul.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(hours, color = Ui.fg, fontSize = size, fontFamily = font, fontWeight = weight, lineHeight = size * 0.95f, maxLines = 1)
                Text(minutes, color = Ui.fg, fontSize = size, fontFamily = font, fontWeight = weight, lineHeight = size * 0.95f, maxLines = 1)
                InfoRow()
            }
        }

        3 -> {
            // Körvonalas számok.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Crossfade(targetState = time, animationSpec = tween(350), label = "outline") { t ->
                    Text(
                        text = t,
                        style = TextStyle(
                            color = Ui.fg,
                            fontSize = size,
                            fontFamily = boldFont,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            drawStyle = Stroke(width = 3f),
                        ),
                        maxLines = 1,
                    )
                }
                InfoRow()
            }
        }

        4 -> {
            // Analóg óra: vékony vonalak, másodpercmutató nélkül.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val dial = (vm.clockSize * 1.9f).dp
                Canvas(Modifier.size(dial)) {
                    drawAnalog(now.hour % 12, now.minute)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
                InfoRow()
            }
        }

        else -> {
            // Nagy, vékony digitális óra.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Crossfade(targetState = time, animationSpec = tween(350), label = "time") { t ->
                    Text(
                        text = t,
                        color = Ui.fg,
                        fontSize = size,
                        fontFamily = font,
                        fontWeight = weight,
                        letterSpacing = (-1).sp,
                        maxLines = 1,
                    )
                }
                InfoRow()
            }
        }
    }
}

private fun DrawScope.drawAnalog(hour: Int, minute: Int) {
    val c = center
    val r = size.minDimension / 2f
    val thin = 1.5.dp.toPx()

    drawCircle(
        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
        radius = r - thin,
        center = c,
        style = Stroke(width = thin),
    )
    // órajelek
    for (i in 0 until 12) {
        val a = Math.toRadians(i * 30.0)
        val inner = r * (if (i % 3 == 0) 0.80f else 0.88f)
        val outer = r * 0.94f
        drawLine(
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = if (i % 3 == 0) 0.9f else 0.5f),
            start = Offset(c.x + inner * sin(a).toFloat(), c.y - inner * cos(a).toFloat()),
            end = Offset(c.x + outer * sin(a).toFloat(), c.y - outer * cos(a).toFloat()),
            strokeWidth = thin,
            cap = StrokeCap.Round,
        )
    }
    // mutatók
    val hourAngle = Math.toRadians(hour * 30.0 + minute * 0.5)
    val minAngle = Math.toRadians(minute * 6.0)
    drawLine(
        color = androidx.compose.ui.graphics.Color.White,
        start = c,
        end = Offset(c.x + r * 0.5f * sin(hourAngle).toFloat(), c.y - r * 0.5f * cos(hourAngle).toFloat()),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = androidx.compose.ui.graphics.Color.White,
        start = c,
        end = Offset(c.x + r * 0.76f * sin(minAngle).toFloat(), c.y - r * 0.76f * cos(minAngle).toFloat()),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = 4.dp.toPx(), center = c)
}
