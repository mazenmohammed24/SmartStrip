package com.powerk.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerk.app.AppElevation

/**
 * Vertical navigation rail for expanded windows (tablets, foldables, split-screen,
 * resizable windows). Same destinations, badges and press feedback as the compact dock.
 */
@Composable
fun NavRail(items: List<DockItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.padding(horizontal = 12.dp).statusBarsPadding().navigationBarsPadding(),
        shape = RoundedCornerShape(26.dp),
        color = scheme.surfaceContainerLow.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.8f)),
        shadowElevation = AppElevation.nav,
    ) {
        Column(
            Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items.forEachIndexed { i, item -> RailTab(item, i == selected) { onSelect(i) } }
        }
    }
}

@Composable
private fun RailTab(item: DockItem, on: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (on) capsuleFill() else Color.Transparent, tween(180), label = "railBg")
    val edge by animateColorAsState(if (on) scheme.primary.copy(alpha = 0.32f) else Color.Transparent, tween(180), label = "railEdge")
    val fg by animateColorAsState(if (on) scheme.primary else scheme.onSurfaceVariant, tween(180), label = "railFg")
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = bg,
        border = BorderStroke(1.dp, edge),
        modifier = Modifier.width(74.dp).height(58.dp).tap(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Icon(item.icon, item.label, Modifier.size(21.dp), tint = fg)
                    NavBadge(
                        item.badge,
                        Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-6).dp),
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(item.label, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
            }
        }
    }
}
