package app.monolauncher

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppInfo,
    mono: Boolean,
    d: Dims,
    onClick: (Rect?) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current.applicationContext
    val icon by produceState<ImageBitmap?>(initialValue = null, app.key, mono) {
        value = IconLoader.load(ctx, app.key, mono)
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var iconBounds by remember { mutableStateOf<Rect?>(null) }
    // Rugószerű, természetes visszajelzés: lenyomáskor besüpped, elengedéskor finoman visszalendül.
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "scale",
    )
    val dim by animateFloatAsState(
        if (pressed) 0.6f else 1f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "dim",
    )
    val iconAlpha by animateFloatAsState(if (icon != null) 1f else 0f, tween(220), label = "iconAlpha")

    val iconSize = 26.dp
    val gap = (d.textStart - d.iconStart - iconSize).coerceAtLeast(8.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(d.rowH)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = dim
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onClick(iconBounds) },
                onLongClick = onLongClick,
            )
            .padding(start = d.iconStart),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(iconSize).onGloballyPositioned { iconBounds = it.boundsInWindow() }) {
            icon?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().alpha(iconAlpha),
                )
            }
        }
        Spacer(Modifier.width(gap))
        Text(
            text = app.label,
            color = Ui.fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = d.w * 0.14f),
        )
    }
}
