package com.powerk.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.powerk.app.R
import com.powerk.app.Server
import com.powerk.app.Snapshot
import com.powerk.app.components.MessageCard
import com.powerk.app.components.EmptyState
import com.powerk.app.components.StripCard

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardScreen(
    snapshot: Snapshot?,
    error: String?,
    loading: Boolean,
    pending: Map<String, Boolean>,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onCommand: (String, Int, Boolean) -> Unit,
    onIconChange: (String, Int?, String) -> Unit,
    onStripTap: (String) -> Unit,
    onScan: () -> Unit = {},
) {
    val strips = snapshot?.strips.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            DashboardHeader(
                stripCount = strips.size,
                onlineCount = strips.count { it.online },
                configured = Server.configured,
                loading = loading,
                onRefresh = onRefresh,
            )
        }

        // Summary tiles
        if (strips.isNotEmpty()) {
            item {
                val totalW = strips.sumOf { it.powerW }
                val outletsOn = strips.sumOf { s -> s.outlets.count { it.on } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryTile(label = "Strips", value = "${strips.size}", sub = "${strips.count { it.online }} online", modifier = Modifier.weight(1f))
                    SummaryTile(label = "Power", value = "${totalW.toInt()} W", sub = "$outletsOn outlets on", modifier = Modifier.weight(1f))
                }
            }
        }

        if (error != null) {
            item { MessageCard(error, isError = true, icon = Icons.Filled.Warning) }
        }

        if (!Server.configured) {
            item {
                EmptyState(
                    title = stringResource(R.string.no_server_title),
                    body = stringResource(R.string.no_server_body),
                    action = stringResource(R.string.open_settings),
                    onAction = onOpenSettings,
                )
            }
        } else {
            items(strips, key = { it.mac }) { strip ->
                StripCard(
                    strip = strip,
                    showMac = strips.size > 1,
                    pending = pending,
                    onCommand = onCommand,
                    onIconChange = onIconChange,
                    onTap = { onStripTap(strip.mac) },
                )
            }

            if (snapshot != null && strips.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.no_strip_title),
                        body = stringResource(R.string.no_strip_body),
                        action = stringResource(R.string.refresh),
                        onAction = onRefresh,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    stripCount: Int,
    onlineCount: Int,
    configured: Boolean,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 6.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            val subtitle = when {
                !configured -> stringResource(R.string.header_no_server)
                onlineCount > 0 -> stringResource(R.string.header_online, onlineCount, Server.host)
                stripCount > 0 -> stringResource(R.string.header_known_offline, stripCount)
                else -> stringResource(R.string.header_waiting, Server.host)
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRefresh, enabled = configured && !loading) {
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        androidx.compose.foundation.layout.Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
