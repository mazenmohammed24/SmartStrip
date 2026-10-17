package com.powerk.app.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerk.app.NotificationItem
import com.powerk.app.StripsViewModel
import com.powerk.app.components.ConfirmDialog
import com.powerk.app.components.EmptyState
import com.powerk.app.components.SegTabs
import com.powerk.app.components.Shimmer
import com.powerk.app.components.ScreenHeader
import com.powerk.app.components.VCard
import com.powerk.app.components.ago

@Composable
fun ActivityScreen(vm: StripsViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            ScreenHeader("Activity", if (vm.unread > 0) "${vm.unread} unread" else "All caught up", actions = {
                if (tab == 0) {
                    if (vm.unread > 0) IconButton(onClick = { vm.markAllRead() }) { Icon(Icons.Rounded.DoneAll, "Mark all read") }
                    if (vm.notifications.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, "Delete all alerts") }
                } else if (vm.events.isNotEmpty()) {
                    IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, "Delete all logs") }
                }
            })
        }
        item(key = "tabs") {
            SegTabs(listOf(if (vm.unread > 0) "Alerts (${vm.unread})" else "Alerts", "Log"), tab, { tab = it })
            Spacer(Modifier.height(4.dp))
        }
        if (tab == 0) {
            if (!vm.loaded) {
                items(3, key = { "sk$it" }) { Shimmer(Modifier.fillMaxWidth().height(76.dp)) }
            } else if (vm.notifications.isEmpty()) {
                item(key = "empty") { EmptyState("No alerts", "Overloads, offline strips and schedule results show up here.") }
            } else {
                items(vm.notifications, key = { it.id }) { n -> Box(Modifier.animateItem()) { AlertRow(n, vm) } }
            }
        } else {
            if (vm.events.isEmpty()) {
                item(key = "empty") { EmptyState("No activity yet", "Switching and automation events are listed here.") }
            } else {
                items(vm.events, key = { it.id }) { e ->
                    VCard(Modifier.fillMaxWidth().animateItem(), padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.message, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(e.stripName, e.type).joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Text(ago(e.createdAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = if (tab == 0) "Delete all alerts?" else "Delete all logs?",
            text = "This clears them on the server for every device. It can't be undone.",
            confirm = "Delete all",
            onConfirm = { if (tab == 0) vm.clearNotifications() else vm.clearEvents() },
            onDismiss = { confirmClear = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertRow(n: NotificationItem, vm: StripsViewModel) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it != SwipeToDismissBoxValue.Settled) vm.deleteNotification(n.id)
        true
    })
    SwipeToDismissBox(
        state = state,
        modifier = Modifier.animateContentSize(),
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
            }
        },
    ) {
        val unread = n.readAt == null
        VCard(Modifier.fillMaxWidth(), onClick = { if (unread) vm.markRead(n.id) }, padding = 14.dp) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(
                        when {
                            !unread -> MaterialTheme.colorScheme.outlineVariant
                            n.severity == "critical" || n.severity == "error" -> MaterialTheme.colorScheme.error
                            n.severity == "warning" -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                    ),
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(n.title, fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (n.body.isNotBlank()) Text(n.body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Text(ago(n.createdAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
