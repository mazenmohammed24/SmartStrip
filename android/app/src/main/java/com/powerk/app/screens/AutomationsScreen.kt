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
import com.powerk.app.R
import com.powerk.app.ScheduleItem
import com.powerk.app.Server
import com.powerk.app.Snapshot
import com.powerk.app.components.EmptyState
import com.powerk.app.components.MessageCard
import com.powerk.app.components.MetricPill
import com.powerk.app.components.ScreenTitle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AutomationsScreen(snapshot: Snapshot?) {
    val strips = snapshot?.strips.orEmpty()
    var schedules by remember { mutableStateOf<List<ScheduleItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            try { schedules = Api.schedules(Server.base, Server.token); error = null }
            catch (e: Exception) { error = e.message }
        }
    }

    LaunchedEffect(Unit) { load() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle(stringResource(R.string.automations_title), stringResource(R.string.automations_body)) }
        item {
            if (strips.isNotEmpty()) {
                FilledTonalButton(
                    onClick = {
                        val first = strips.first()
                        scope.launch {
                            Api.saveSchedule(Server.base, Server.token, ScheduleItem("", "Evening lights", first.mac, null, "on", "18:00", listOf(0,1,2,3,4,5,6), true))
                            load()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.new_automation)) }
            }
        }
        if (schedules.isEmpty()) {
            item { EmptyState(stringResource(R.string.automations_empty_title), stringResource(R.string.automations_empty_body)) }
        }
        items(schedules, key = { it.id }) { s ->
            val strip = strips.firstOrNull { it.mac == s.stripId }
            val target = s.relayIndex?.let { stringResource(R.string.outlet_n, it + 1) } ?: stringResource(R.string.all_outlets)
            ScheduleCard(
                schedule = s,
                stripName = strip?.name ?: s.stripId,
                target = target,
                onToggle = { scope.launch { Api.saveSchedule(Server.base, Server.token, s.copy(enabled = !s.enabled)); load() } },
                onRunNow = { scope.launch { Api.runScheduleNow(Server.base, Server.token, s.id) } },
                onDelete = { scope.launch { Api.deleteSchedule(Server.base, Server.token, s.id); load() } },
            )
        }
        error?.let { item { MessageCard(it, isError = true, icon = Icons.Filled.Warning) } }
    }
}

@Composable
private fun ScheduleCard(
    schedule: ScheduleItem,
    stripName: String,
    target: String,
    onToggle: () -> Unit,
    onRunNow: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(schedule.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                MetricPill(if (schedule.enabled) stringResource(R.string.enabled) else stringResource(R.string.disabled))
            }
            Text(
                "$stripName · $target · ${schedule.action} · ${schedule.time}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onRunNow) { Text(stringResource(R.string.run_now)) }
                OutlinedButton(onClick = onToggle) { Text(if (schedule.enabled) stringResource(R.string.disable) else stringResource(R.string.enable)) }
                OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}
