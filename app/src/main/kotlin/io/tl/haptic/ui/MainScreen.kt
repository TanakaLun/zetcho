package io.tl.haptic.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.tl.haptic.*
import kotlin.math.sin
import io.tl.haptic.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: HapticViewModel,
    onOpenFilePicker: () -> Unit,
    onTogglePlay: () -> Unit,
    onExport: () -> Unit
) {
    var showSourceDialog by remember { mutableStateOf(false) }
    var showPresetDialog by remember { mutableStateOf(false) }
    var showWarning by remember { mutableStateOf(false) }
    var clickCount by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(R.string.app_name), modifier = Modifier.clickable { 
                        if (++clickCount >= 3) { showWarning = true; clickCount = 0 }
                    })
                },
                actions = { 
                    IconButton(onClick = { viewModel.isShowingSettings = true }) { 
                        Icon(Icons.Default.Settings, null) 
                    } 
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                if (viewModel.isProcessing) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    RealtimeWaveform(viewModel.rawWaveformData, viewModel.isPlaying)
                }
            }

            Text(
                text = when {
                    viewModel.isProcessing -> stringResource(R.string.status_processing)
                    viewModel.isPlaying && viewModel.currentMode == HapticMode.PRESET -> 
                        stringResource(R.string.status_preset, stringResource(viewModel.currentPreset?.labelRes ?: R.string.app_name))
                    viewModel.isPlaying -> stringResource(R.string.status_playing)
                    else -> stringResource(R.string.status_idle)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 导出按钮
                FilledTonalIconButton(
                    onClick = onExport, 
                    modifier = Modifier.size(64.dp),
                    enabled = !viewModel.isPlaying && !viewModel.isProcessing && viewModel.currentMode == HapticMode.AUDIO 
                ) {
                    Icon(Icons.Default.Share, stringResource(R.string.btn_export))
                }
                
                Spacer(Modifier.width(24.dp))
                
                // 播放/暂停
                LargeFloatingActionButton(
                    onClick = onTogglePlay,
                    shape = RoundedCornerShape(24.dp),
                    containerColor = if (viewModel.isPlaying) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(40.dp))
                }

                Spacer(Modifier.width(24.dp))

                // 添加来源
                FilledTonalIconButton(onClick = { showSourceDialog = true }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Default.Add, stringResource(R.string.btn_import))
                }
            }
        }
    }

    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text(stringResource(R.string.warning_title)) },
            text = { Text(stringResource(R.string.warning_content)) },
            confirmButton = { 
                Button(onClick = { viewModel.isSensualMode = true; showWarning = false }) { 
                    Text(stringResource(R.string.btn_continue)) 
                } 
            },
            dismissButton = {
                TextButton(onClick = { showWarning = false }) { Text(stringResource(R.string.btn_back)) }
            }
        )
    }

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text(stringResource(R.string.dialog_title_source)) },
            confirmButton = {},
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { showSourceDialog = false; showPresetDialog = true }, modifier = Modifier.fillMaxWidth()) { 
                        Text(stringResource(R.string.option_preset)) 
                    }
                    OutlinedButton(onClick = { showSourceDialog = false; onOpenFilePicker() }, modifier = Modifier.fillMaxWidth()) { 
                        Text(stringResource(R.string.option_custom)) 
                    }
                }
            }
        )
    }

    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text(stringResource(R.string.dialog_title_preset)) },
            confirmButton = { TextButton(onClick = { showPresetDialog = false }) { Text(stringResource(R.string.btn_back)) } },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PresetType.values()) { type ->
                        ElevatedCard(onClick = { viewModel.startPreset(type); showPresetDialog = false }) {
                            ListItem(headlineContent = { Text(stringResource(type.labelRes)) })
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun RealtimeWaveform(data: List<Float>, isPlaying: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    val phase by rememberInfiniteTransition(label = "").animateFloat(
        initialValue = 0f, targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)), label = ""
    )

    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val path = Path()
        val step = width / (data.size - 1)

        path.moveTo(0f, centerY)

        for (i in 0 until data.size - 1) {
            val x1 = i * step
            val y1 = centerY + (if (isPlaying) data[i] else sin(i * 0.2f + phase) * 0.05f) * centerY * 0.8f
            val x2 = (i + 1) * step
            val y2 = centerY + (if (isPlaying) data[i + 1] else sin((i + 1) * 0.2f + phase) * 0.05f) * centerY * 0.8f

            val cp1X = x1 + (x2 - x1) / 3f
            val cp2X = x1 + 2f * (x2 - x1) / 3f
            path.cubicTo(cp1X, y1, cp2X, y2, x2, y2)
        }

        drawPath(path, color, style = Stroke(width = 8f, cap = StrokeCap.Round))
    }
}
