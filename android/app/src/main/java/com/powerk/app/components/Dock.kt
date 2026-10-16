package com.powerk.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DockItem(val icon: ImageVector, val label: String, val badge: Int = 0)

/** Floating glass tab dock with a spring-sliding indicator. */
@Composable
fun FloatingDock(items: List<DockItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.BottomCenter) {
            BoxWithConstraints(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().height(62.dp)
                    .shadow(18.dp, RoundedCornerShape(26.dp), ambientColor = scheme.primary.copy(alpha = 0.25f), spotColor = scheme.onSurface.copy(alpha = 0.35f)),
            ) {
                val pad = 6.dp
                val itemW = (maxWidth - pad * 2) / items.size
                val x by animateDpAsState(pad + itemW * selected.coerceAtLeast(0), spring(0.72f, Spring.StiffnessMedium), label = "dockX")
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = scheme.surface.copy(alpha = 0.84f),
                    border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.9f)),
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                ) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight()) {
                        Box(
                            Modifier.offset(x = x, y = pad).width(itemW).height(62.dp - pad * 2)
                                .background(scheme.primary.copy(alpha = 0.14f), RoundedCornerShape(20.dp)),
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
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, spring(0.55f, Spring.StiffnessMedium), label = "dockPress")
    val lift by animateFloatAsState(if (on) 1.08f else 1f, spring(0.6f, Spring.StiffnessMedium), label = "dockLift")
    val tint by animateColorAsState(if (on) scheme.primary else scheme.onSurfaceVariant, tween(160), label = "dockTint")
    Column(
        modifier.fillMaxHeight().clickable(interactionSource = source, indication = null, onClick = onClick)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.graphicsLayer { scaleX = lift; scaleY = lift }) {
            Icon(item.icon, item.label, Modifier.size(22.dp), tint = tint)
            if (item.badge > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-4).dp).background(scheme.error, CircleShape)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) { Text(if (item.badge > 9) "9+" else item.badge.toString(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = scheme.onError, maxLines = 1) }
            }
        }
        Text(item.label, fontSize = 10.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}
