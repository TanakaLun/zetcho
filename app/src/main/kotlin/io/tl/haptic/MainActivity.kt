package io.tl.haptic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
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
import androidx.core.content.ContextCompat
import io.tl.haptic.ui.theme.HapticGeneratorTheme
import kotlin.math.hypot
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private var lastVibrateTime: Long = 0

    private val rawWaveformData = mutableStateListOf<Float>().apply { repeat(32) { add(0f) } }
    private var bassIntensity = mutableStateOf(0f) 
    private var isPlaying = mutableStateOf(false)
    private var isAudioReady = mutableStateOf(false)

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { prepareAudio(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HapticGeneratorTheme {
                MainScreen(
                    waveformData = rawWaveformData,
                    intensity = bassIntensity.value,
                    isReady = isAudioReady.value,
                    isPlaying = isPlaying.value,
                    onImport = { filePicker.launch(arrayOf("audio/*")) },
                    onPlayPause = { togglePlay() }
                )
            }
        }
        checkPermissions()
    }

    private fun prepareAudio(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            mediaPlayer?.release()
            visualizer?.release()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                prepare()
                isAudioReady.value = true
            }

            visualizer = Visualizer(mediaPlayer!!.audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        waveform?.let { updateWaveformUI(it) }
                    }
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        fft?.let { processFft(it) }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)
                enabled = true
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.load_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateWaveformUI(data: ByteArray) {
        val step = data.size / 32
        for (i in 0 until 32) {
            val sample = (data[i * step].toInt() and 0xFF) - 128
            rawWaveformData[i] = sample.toFloat() / 128f
        }
    }

    private fun processFft(fft: ByteArray) {
        if (mediaPlayer?.isPlaying != true) return
        var bassEnergy = 0f
        for (i in 1..8) { 
            val real = fft[i * 2].toFloat()
            val imag = fft[i * 2 + 1].toFloat()
            bassEnergy += hypot(real, imag)
        }
        val normalizedBass = (bassEnergy / 500f).coerceIn(0f, 1f)
        bassIntensity.value = normalizedBass
        if (normalizedBass > 0.35f) {
            triggerHaptic(normalizedBass)
        }
    }

    private fun triggerHaptic(intensity: Float) {
        val now = System.currentTimeMillis()
        if (now - lastVibrateTime < 90) return
        val vibrator = getSystemService(Vibrator::class.java)
        val strength = (intensity * intensity * 255).toInt().coerceIn(10, 255)
        vibrator?.vibrate(VibrationEffect.createOneShot(40, strength))
        lastVibrateTime = now
    }

    private fun togglePlay() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying.value = false
            } else {
                it.start()
                isPlaying.value = true
            }
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    waveformData: List<Float>,
    intensity: Float,
    isReady: Boolean,
    isPlaying: Boolean,
    onImport: () -> Unit,
    onPlayPause: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.app_name)) }) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // 状态文字提示
            Text(
                text = if (isPlaying) stringResource(R.string.status_playing) else stringResource(R.string.status_idle),
                style = MaterialTheme.typography.titleMedium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )

            Card(
                modifier = Modifier.fillMaxWidth().height(260.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                RealtimeWaveform(waveformData, intensity, isPlaying)
            }

            LinearProgressIndicator(
                progress = { intensity },
                modifier = Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 20.dp),
                strokeCap = StrokeCap.Round
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onImport,
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.btn_import))
                }

                Spacer(modifier = Modifier.width(48.dp))

                LargeFloatingActionButton(
                    onClick = onPlayPause,
                    shape = RoundedCornerShape(24.dp),
                    containerColor = if (isPlaying) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.btn_play_pause),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RealtimeWaveform(waveformData: List<Float>, intensity: Float, isPlaying: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    val infiniteTransition = rememberInfiniteTransition(label = "idle")
    val idlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "phase"
    )

    val animatedPoints = waveformData.map {
        animateFloatAsState(
            targetValue = if (isPlaying) it * (1f + intensity) else 0f, 
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "point"
        ).value
    }

    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 50.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val path = Path()

        if (animatedPoints.isNotEmpty()) {
            val step = width / (animatedPoints.size - 1)
            path.moveTo(0f, centerY)
            for (i in 0 until animatedPoints.size - 1) {
                val idleOffset = if (!isPlaying) sin(i * 0.15f + idlePhase) * 10f else 0f
                val x1 = i * step
                val y1 = centerY + (animatedPoints[i] * centerY * 0.7f) + idleOffset
                val x2 = (i + 1) * step
                val idleOffset2 = if (!isPlaying) sin((i + 1) * 0.15f + idlePhase) * 10f else 0f
                val y2 = centerY + (animatedPoints[i + 1] * centerY * 0.7f) + idleOffset2
                val controlX = (x1 + x2) / 2
                path.cubicTo(controlX, y1, controlX, y2, x2, y2)
            }
        }

        drawPath(path = path, color = color, style = Stroke(width = 8f, cap = StrokeCap.Round))
    }
}
