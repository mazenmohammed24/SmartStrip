package com.powerk.app.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.powerk.app.Server
import com.powerk.app.Strip
import com.powerk.app.outlet
import com.powerk.app.components.EmptyState
import com.powerk.app.components.IconPickerMenu
import com.powerk.app.components.MessageCard
import com.powerk.app.components.MetricPill
import com.powerk.app.components.OutletTile
import com.powerk.app.components.iconGlyph
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StripDetailScreen(stripMac: String, onBack: () -> Unit) {
    var strip by remember { mutableStateOf<Strip?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var editName by remember { mutableStateOf(false) }
    var nameValue by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var iconPickerOpen by remember { mutableStateOf(false) }
    val pending = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()

    fun load() {
        loading = true
        scope.launch {
            try {
                val snap = Api.snapshot(Server.base, Server.token)
                strip = snap.strips.firstOrNull { it.mac == stripMac }
                strip?.let { nameValue = it.name }
                error = null
            } catch (e: Exception) { error = e.message }
            loading = false
        }
    }

    LaunchedEffect(stripMac) { load() }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_strip_confirm_title)) },
            text = { Text(stringResource(R.string.delete_strip_confirm_body, strip?.name ?: stripMac)) },
            confirmButton = {
                Button(onClick = {
                    showDeleteDialog = false
                    scope.launch {
                        try { Api.deleteStrip(Server.base, Server.token, stripMac); onBack() }
                        catch (e: Exception) { error = e.message }
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    strip?.let { s ->
        IconPickerMenu(
            expanded = iconPickerOpen,
            selected = s.icon,
            onDismiss = { iconPickerOpen = false },
            onSelect = { icon ->
                iconPickerOpen = false
                scope.launch {
                    try { Api.updateStripIcon(Server.base, Server.token, stripMac, icon); load() }
                    catch (e: Exception) { error = e.message }
                }
            },
        )
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack, modifier = Modifier.size(40.dp), contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                if (!loading && strip != null) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (loading) {
            item { CircularWavyProgressIndicator(Modifier.fillMaxWidth()) }
        }
        strip?.let { s ->
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(onClick = { iconPickerOpen = true }, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) {
                                Text(fontSize = MaterialTheme.typography.titleLarge.fontSize, text = iconGlyph(s.icon))
                            }
                            Spacer(Modifier.size(12.dp))
                            if (editName) {
                                OutlinedTextField(value = nameValue, onValueChange = { nameValue = it }, singleLine = true, label = { Text(stringResource(R.string.strip_name)) }, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    editName = false
                                    scope.launch {
                                        try { Api.updateStrip(Server.base, Server.token, stripMac, nameValue); load() }
                                        catch (e: Exception) { error = e.message }
                                    }
                                }) { Icon(Icons.Filled.Edit, null) }
                            } else {
                                Text(s.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                IconButton(onClick = { editName = true; nameValue = s.name }) { Icon(Icons.Filled.Edit, null) }
                            }
                        }
                        HorizontalDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricPill("${s.powerW.roundToInt()} W")
                            MetricPill("${s.energyKwh} kWh")
                            if (s.online) MetricPill(stringResource(R.string.online)) else MetricPill(stringResource(R.string.offline))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { scope.launch { Api.setOutlet(Server.base, Server.token, s.mac, 0, true); load() } }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.all_on)) }
                            OutlinedButton(onClick = { scope.launch { Api.setOutlet(Server.base, Server.token, s.mac, 0, false); load() } }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.all_off)) }
                        }
                    }
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.outlets_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        (1..4).forEach { n ->
                            val outletItem = s.outlet(n)
                            if (outletItem != null) {
                                val isPending = pending.containsKey("${s.mac}:$n")
                                val isOn = pending["${s.mac}:$n"] ?: outletItem.on
                                OutletTile(
                                    outlet = outletItem,
                                    on = isOn,
                                    pending = isPending,
                                    enabled = !isPending,
                                    onToggle = { on ->
                                        if (!isPending) {
                                            pending["${s.mac}:$n"] = on
                                            scope.launch {
                                                try { Api.setOutlet(Server.base, Server.token, s.mac, n, on); load() }
                                                catch (e: Exception) { error = e.message }
                                                finally { pending.remove("${s.mac}:$n") }
                                            }
                                        }
                                    },
                                    onIconChange = { icon ->
                                        scope.launch {
                                            try { Api.updateOutletIcon(Server.base, Server.token, s.mac, n, icon); load() }
                                            catch (e: Exception) { error = e.message }
                                        }
                                    },
                                )
                                if (n < 4) HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
        error?.let { item { MessageCard(it, isError = true, icon = Icons.Filled.Warning) } }
        if (!loading && strip == null && error == null) {
            item { EmptyState(stringResource(R.string.strip_not_found), stringResource(R.string.strip_not_found_body), stringResource(R.string.refresh), onAction = { load() }) }
        }
    }
}
