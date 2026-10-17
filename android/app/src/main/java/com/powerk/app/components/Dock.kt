package com.powerk.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

data class DockItem(val icon: ImageVector, val label: String, val badge: Int = 0)

/** Floating frosted-glass tab dock: real backdrop blur with a soft translucent tint. */
@Composable
fun FloatingDock(
    hazeState: HazeState,
    items: List<DockItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val light = scheme.surface.luminance() > 0.5f
    val shape = RoundedCornerShape(30.dp)
    val frosted = HazeBlurStyle {
        blurEnabled(true)
        blurRadius(32.dp)
        noiseFactor(0.012f)
        backgroundColor(Color.Transparent)
        colorEffects(listOf(HazeColorEffect.tint(scheme.surface.copy(alpha = if (light) 0.50f else 0.34f))))
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.BottomCenter) {
            BoxWithConstraints(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().height(66.dp)
                    .shadow(14.dp, shape)
                    .clip(shape)
                    .hazeBlur(input = HazeInput.Backdrop(hazeState), style = frosted)
                    .border(
                        1.dp,
                        if (light) scheme.outlineVariant.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.16f),
                        shape,
                    ),
            ) {
                val pad = 7.dp
                val itemW = (maxWidth - pad * 2) / items.size
                val pillX by animateDpAsState(
                    pad + itemW * selected.coerceAtLeast(0),
                    spring(dampingRatio = 0.85f, stiffness = 380f),
                    label = "dockPill",
                )
                Box(Modifier.fillMaxWidth().fillMaxHeight()) {
                    Box(
                        Modifier
                            .offset(x = pillX)
                            .padding(vertical = pad)
                            .width(itemW)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(23.dp))
                            .background(Brush.verticalGradient(listOf(scheme.primary, scheme.primary.copy(alpha = 0.85f)))),
                    )
                    Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = pad), horizontalArrangement = Arrangement.SpaceBetween) {
                        items.forEachIndexed { i, item -> DockTab(item, i == selected, Modifier.width(itemW)) { onSelect(i) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockTab(item: DockItem, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(0.6f, Spring.StiffnessMedium), label = "dockPress")
    val lift by animateFloatAsState(if (on) 1.08f else 1f, spring(0.6f, Spring.StiffnessMediumLow), label = "dockLift")
    val tint by animateColorAsState(if (on) scheme.onPrimary else scheme.onSurfaceVariant, spring(stiffness = Spring.StiffnessMediumLow), label = "dockTint")
    Column(
        modifier.fillMaxHeight().clickable(interactionSource = source, indication = null, onClick = onClick)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.graphicsLayer { scaleX = lift; scaleY = lift }) {
            Icon(item.icon, item.label, Modifier.size(22.dp), tint = tint)
            val badgeScale by animateFloatAsState(
                if (item.badge > 0) 1f else 0f,
                spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
                label = "badgePop",
            )
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-4).dp)
                    .graphicsLayer {
                        scaleX = badgeScale
                        scaleY = badgeScale
                        alpha = badgeScale.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin.Center
                    },
            ) {
                Box(Modifier.background(scheme.error, CircleShape).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text(if (item.badge > 9) "9+" else item.badge.toString(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = scheme.onError, maxLines = 1)
                }
            }
        }
        Text(
            item.label,
            fontSize = 10.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
