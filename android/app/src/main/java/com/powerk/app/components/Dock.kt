package com.powerk.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.fillMaxSize
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
import com.powerk.app.AppElevation
import com.powerk.app.AppReducedMotion
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

data class DockItem(val icon: ImageVector, val label: String, val badge: Int = 0)

/**
 * Small unread-count pill shared by the dock and the rail. The ring lifts it off the
 * frosted glass so it reads as a deliberate badge, not a warning sticker.
 */
@Composable
fun NavBadge(count: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    if (count <= 0) return
    Box(
        modifier.height(16.dp).widthIn(min = 16.dp).clip(CircleShape)
            .background(scheme.error)
            .border(1.5.dp, scheme.surface.copy(alpha = 0.92f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 9) "9+" else count.toString(),
            Modifier.padding(horizontal = 4.dp),
            fontSize = 9.5.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.onError,
            maxLines = 1,
        )
    }
}

/** Floating frosted-glass tab dock: real backdrop blur with a layered depth treatment. */
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
    val shape = RoundedCornerShape(28.dp)
    val frosted = HazeBlurStyle {
        blurEnabled(true)
        blurRadius(36.dp)
        noiseFactor(0.012f)
        backgroundColor(Color.Transparent)
        colorEffects(listOf(HazeColorEffect.tint(scheme.surface.copy(alpha = if (light) 0.55f else 0.38f))))
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.BottomCenter) {
            // Two stacked shadows: a tight contact shadow plus a wide ambient one, so the bar
            // reads as floating above the page instead of painted onto it.
            BoxWithConstraints(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().height(66.dp)
                    .shadow(3.dp, shape, clip = false)
                    .shadow(AppElevation.nav, shape, clip = false),
            ) {
                val pad = 7.dp
                val itemW = (maxWidth - pad * 2) / items.size
                val reduced = AppReducedMotion
                val pillX by animateDpAsState(
                    pad + itemW * selected.coerceAtLeast(0),
                    if (reduced) snap() else spring(dampingRatio = 0.85f, stiffness = 380f),
                    label = "dockPill",
                )
                Box(
                    Modifier.fillMaxSize()
                        .clip(shape)
                        .hazeBlur(input = HazeInput.Backdrop(hazeState), style = frosted)
                        // Glass edge: brighter along the top where the light comes from.
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(
                                    if (light) Color.White.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.26f),
                                    if (light) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f),
                                ),
                            ),
                            shape,
                        ),
                ) {
                    // Sheen across the upper half of the glass.
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color.White.copy(alpha = if (light) 0.16f else 0.08f),
                                0.55f to Color.Transparent,
                            ),
                        ),
                    )
                    Box(Modifier.fillMaxSize()) {
                        // Selected item: a soft accent capsule with a hairline edge and its own
                        // small shadow — a lit tile inside the glass, not a solid paint bar.
                        val capsule = RoundedCornerShape(20.dp)
                        Box(
                            Modifier
                                .offset(x = pillX)
                                .padding(horizontal = 3.dp, vertical = 9.dp)
                                .width(itemW - 6.dp)
                                .fillMaxHeight()
                                .shadow(3.dp, capsule, clip = false)
                                .clip(capsule)
                                // Opaque tint: the frosted glass behind would otherwise show
                                // through as a shifting lighter blob inside the capsule.
                                .background(
                                    Brush.verticalGradient(
                                        listOf(capsuleFill(strong = true), capsuleFill()),
                                    ),
                                )
                                .border(1.dp, scheme.primary.copy(alpha = 0.36f), capsule),
                        )
                        Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = pad), horizontalArrangement = Arrangement.SpaceBetween) {
                            items.forEachIndexed { i, item -> DockTab(item, i == selected, Modifier.width(itemW)) { onSelect(i) } }
                        }
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
    val reduced = AppReducedMotion
    val scale by animateFloatAsState(if (pressed && !reduced) 0.92f else 1f, spring(0.6f, Spring.StiffnessMedium), label = "dockPress")
    val lift by animateFloatAsState(if (on && !reduced) 1.06f else 1f, spring(0.6f, Spring.StiffnessMediumLow), label = "dockLift")
    val tint by animateColorAsState(if (on) scheme.primary else scheme.onSurfaceVariant, spring(stiffness = Spring.StiffnessMediumLow), label = "dockTint")
    Column(
        modifier.fillMaxHeight().clickable(interactionSource = source, indication = null, onClick = onClick)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.graphicsLayer { scaleX = lift; scaleY = lift }) {
            Icon(item.icon, item.label, Modifier.size(21.dp), tint = tint)
            val badgeScale by animateFloatAsState(
                if (item.badge > 0) 1f else 0f,
                if (reduced) snap() else spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
                label = "badgePop",
            )
            NavBadge(
                item.badge,
                Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-5).dp)
                    .graphicsLayer {
                        scaleX = badgeScale
                        scaleY = badgeScale
                        alpha = badgeScale.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin.Center
                    },
            )
        }
        // Tight icon-over-label stack; explicit line height kills the extra font leading.
        Text(
            item.label,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
