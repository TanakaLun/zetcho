package io.tl.haptic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.tl.haptic.HapticViewModel
import io.tl.haptic.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(viewModel: HapticViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.isShowingSettings = false }) { 
                        Icon(Icons.Default.ArrowBack, null) 
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(24.dp)) {
            Text(stringResource(R.string.setting_boost_title), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = viewModel.vibrationBoost,
                onValueChange = { viewModel.vibrationBoost = it },
                valueRange = 1.0f..3.0f,
                steps = 20
            )
            Text(
                text = stringResource(R.string.setting_boost_label, viewModel.vibrationBoost), 
                color = MaterialTheme.colorScheme.primary
            )
            
            HorizontalDivider(Modifier.padding(vertical = 24.dp))
            
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_dynamic_title)) },
                supportingContent = { Text(stringResource(R.string.setting_dynamic_desc)) },
                trailingContent = { 
                    Switch(
                        checked = viewModel.dynamicFrequency, 
                        onCheckedChange = { viewModel.dynamicFrequency = it }
                    ) 
                }
            )
        }
    }
}
