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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.powerk.app.Api
import com.powerk.app.EventItem
import com.powerk.app.R
import com.powerk.app.Server
import com.powerk.app.Snapshot
import com.powerk.app.components.EmptyState
import com.powerk.app.components.MessageCard
import com.powerk.app.components.MetricPill
import com.powerk.app.components.ScreenTitle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActivityScreen(snapshot: Snapshot?) {
    val strips = snapshot?.strips.orEmpty()
    var events by remember { mutableStateOf<List<EventItem>>(emptyList()) }
    var filterStripId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            try { events = Api.events(Server.base, Server.token, filterStripId); error = null }
            catch (e: Exception) { error = e.message }
        }
    }

    LaunchedEffect(filterStripId) { load() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle(stringResource(R.string.activity_title), stringResource(R.string.activity_body)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { dropdownExpanded = true }) {
                    Text(strips.firstOrNull { it.mac == filterStripId }?.name ?: stringResource(R.string.all_strips))
                }
                DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.all_strips)) },
                        onClick = { filterStripId = null; dropdownExpanded = false },
                    )
                    strips.forEach { strip ->
                        DropdownMenuItem(
                            text = { Text(strip.name) },
                            onClick = { filterStripId = strip.mac; dropdownExpanded = false },
                        )
                    }
                }
            }
        }
        if (events.isEmpty()) {
            item { EmptyState(stringResource(R.string.activity_empty_title), stringResource(R.string.activity_empty_body), stringResource(R.string.refresh), onAction = { load() }) }
        }
        items(events, key = { it.id }) { event ->
            EventCard(event, event.stripName ?: "")
        }
        error?.let { item { MessageCard(it, isError = true, icon = Icons.Filled.Warning) } }
    }
}

@Composable
private fun EventCard(event: EventItem, stripName: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (event.type == "error") Icons.Filled.Warning else Icons.Filled.Info,
                contentDescription = null,
                tint = if (event.type == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(event.message, style = MaterialTheme.typography.bodyMedium)
                Text("$stripName · ${event.createdAt.take(16)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MetricPill(event.type)
        }
    }
}
