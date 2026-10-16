package com.powerk.app.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerk.app.StripsViewModel
import com.powerk.app.components.SegTabs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStripSheet(vm: StripsViewModel, onDismiss: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("Add a strip", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                if (tab == 0) "Find strips already on your network." else "Set up a new strip with your Wi-Fi.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            SegTabs(listOf("On network", "New strip setup"), tab, { tab = it })
            Box(Modifier.size(10.dp))
            Box(Modifier.weight(1f)) {
                if (tab == 0) ScanScreen(onStripAdopted = { vm.refresh(); onDismiss() })
                else Column(Modifier.verticalScroll(rememberScrollState())) { ProvisionWizardPanel() }
            }
        }
    }
}
