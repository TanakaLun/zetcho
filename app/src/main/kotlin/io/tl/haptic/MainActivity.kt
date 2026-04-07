package io.tl.haptic

import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.lifecycle.lifecycleScope
import io.tl.haptic.ui.*
import io.tl.haptic.ui.theme.HapticGeneratorTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

class MainActivity : ComponentActivity() {
    private val viewModel: HapticViewModel by viewModels()
    private var mediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { loadAudio(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            while(true) {
                if (viewModel.isPlaying && viewModel.intensity.floatValue > 0.05f) {
                    triggerHaptic(viewModel.intensity.floatValue)
                }
                delay(60)
            }
        }

        setContent {
            HapticGeneratorTheme(isSensualMode = viewModel.isSensualMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (viewModel.isShowingSettings) {
                        SettingsPage(viewModel)
                    } else {
                        MainScreen(
                            viewModel = viewModel,
                            onOpenFilePicker = { filePicker.launch(arrayOf("audio/*")) },
                            onTogglePlay = { handleTogglePlay() }
                        )
                    }
                }
            }
        }
    }

    private fun handleTogglePlay() {
        if (viewModel.isPlaying) {
            viewModel.stopAll()
            mediaPlayer?.pause()
            visualizer?.enabled = false
        } else {
            if (viewModel.currentMode == HapticMode.AUDIO && mediaPlayer != null) {
                mediaPlayer?.start()
                visualizer?.enabled = true
                viewModel.isPlaying = true
            }
        }
    }

    private fun loadAudio(uri: Uri) {
        viewModel.stopAll()
        mediaPlayer?.release()
        visualizer?.release()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                prepare()
                setOnCompletionListener { 
                    viewModel.stopAll()
                    visualizer?.enabled = false
                }
            }
            
            visualizer = Visualizer(mediaPlayer!!.audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        waveform?.let { data ->
                            val points = List(64) { i ->
                                val idx = (i * (data.size / 64)).coerceIn(0, data.size - 1)
                                ((data[idx].toInt() and 0xFF) - 128) / 128f
                            }
                            viewModel.updateWaveform(points)
                        }
                    }

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        fft?.let { safeFft ->
                            var energy = 0f
                            for (i in 1..12) {
                                val r = safeFft[i * 2].toFloat()
                                val im = safeFft[i * 2 + 1].toFloat()
                                energy += hypot(r, im)
                            }
                            viewModel.intensity.floatValue = (energy / 600f).coerceIn(0f, 1f)
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)
                enabled = true
            }
            
            viewModel.currentMode = HapticMode.AUDIO
            mediaPlayer?.start()
            viewModel.isPlaying = true
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun triggerHaptic(intensity: Float) {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        val boost = if (viewModel.isSensualMode) 1.8f else 1.0f
        val finalStrength = (intensity * intensity * 255 * boost * viewModel.vibrationBoost).toInt().coerceIn(0, 255)
        vibrator.vibrate(VibrationEffect.createOneShot(50, finalStrength))
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        visualizer?.release()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: HapticViewModel, onOpenFilePicker: () -> Unit, onTogglePlay: () -> Unit) {
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
                .consumeWindowInsets(innerPadding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().height(300.dp),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                RealtimeWaveform(viewModel.rawWaveformData, viewModel.isPlaying)
            }

            Text(
                text = if (viewModel.isPlaying) stringResource(R.string.status_playing) else stringResource(R.string.status_idle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { showSourceDialog = true }, modifier = Modifier.size(72.dp)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(48.dp))
                LargeFloatingActionButton(
                    onClick = { onTogglePlay() },
                    shape = RoundedCornerShape(24.dp),
                    containerColor = if (viewModel.isPlaying) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(40.dp))
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
}

@Composable
fun RealtimeWaveform(data: List<Float>, isPlaying: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    val phase by rememberInfiniteTransition().animateFloat(
        initialValue = 0f, targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing))
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
                trailingContent = { Switch(checked = true, onCheckedChange = {}) }
            )
        }
    }
}
