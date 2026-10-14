package com.powerk.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.powerk.app.Api
import com.powerk.app.NotificationItem
import com.powerk.app.R
import com.powerk.app.Server
import com.powerk.app.components.EmptyState
import com.powerk.app.components.MessageCard
import com.powerk.app.components.MetricPill
import com.powerk.app.components.ScreenTitle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationsScreen() {
    var notifications by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            try { notifications = Api.notifications(Server.base, Server.token); error = null }
            catch (e: Exception) { error = e.message }
        }
    }

    LaunchedEffect(Unit) { load() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle(stringResource(R.string.notifications_title), stringResource(R.string.notifications_body)) }
        if (notifications.isNotEmpty()) {
            item {
                FilledTonalButton(
                    onClick = { scope.launch { Api.markAllNotificationsRead(Server.base, Server.token); load() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.mark_all_read)) }
            }
        }
        if (notifications.isEmpty()) {
            item { EmptyState(stringResource(R.string.notifications_empty_title), stringResource(R.string.notifications_empty_body), stringResource(R.string.refresh), onAction = { load() }) }
        }
        items(notifications, key = { it.id }) { n ->
            NotificationCard(
                notification = n,
                onMarkRead = { scope.launch { Api.markNotificationRead(Server.base, Server.token, n.id); load() } },
                onDelete = { scope.launch { Api.deleteNotification(Server.base, Server.token, n.id); load() } },
            )
        }
        error?.let { item { MessageCard(it, isError = true, icon = Icons.Filled.Warning) } }
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationItem,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    notification.title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (notification.readAt == null) FontWeight.SemiBold else FontWeight.Normal,
                )
                MetricPill(notification.severity)
            }
            Text(notification.body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text(notification.createdAt.take(16), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (notification.readAt == null) {
                    FilledTonalButton(onClick = onMarkRead) { Text(stringResource(R.string.mark_read)) }
                }
                OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}
