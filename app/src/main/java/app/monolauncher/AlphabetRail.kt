package app.monolauncher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val STAR = '★'

fun railGlyph(c: Char): String = when (c) {
    STAR -> "☆"
    '#' -> "○"
    else -> c.toString()
}

/** Függőleges A–Z sáv: koppintásra és húzásra is működik. */
@Composable
fun AlphabetRail(
    letters: List<Char>,
    active: Char?,
    pitch: Dp,
    onSelect: (Char) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val entries = remember(letters) { listOf(STAR) + letters }
    var heightPx by remember { mutableIntStateOf(1) }
    var last by remember { mutableStateOf<Char?>(null) }
    val onSelectState = rememberUpdatedState(onSelect)
    val onReleaseState = rememberUpdatedState(onRelease)

    fun pick(y: Float) {
        val idx = ((y / heightPx) * entries.size).toInt().coerceIn(0, entries.lastIndex)
        val ch = entries[idx]
        if (ch != last) {
            last = ch
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelectState.value(ch)
        }
    }

    Column(
        modifier = modifier
            .width(36.dp)
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(entries) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pick(down.position.y)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach {
                            if (it.pressed) pick(it.position.y)
                            it.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    last = null
                    onReleaseState.value()
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        entries.forEach { ch ->
            val isActive = ch == active
            Box(
                modifier = Modifier.fillMaxWidth().height(pitch),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = railGlyph(ch),
                    color = if (isActive) Ui.fg else Ui.fg.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
