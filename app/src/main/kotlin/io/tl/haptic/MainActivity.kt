package io.tl.haptic

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.tl.haptic.ui.MainScreen
import io.tl.haptic.ui.SettingsPage
import io.tl.haptic.ui.theme.HapticGeneratorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot

class MainActivity : ComponentActivity() {
    private val viewModel: HapticViewModel by viewModels()
    private var mediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private var currentHapticFile: File? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { 
            // 获取并保存原文件名
            viewModel.originalFileName = getFileNameFromUri(it)
            startHapticProcessing(it) 
        }
    }
    
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/ogg")) { uri ->
        uri?.let { destUri ->
            saveFileToSelectedUri(destUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkAudioPermission()

        // 核心：保留预设模式的马达驱动轮询
        lifecycleScope.launch {
            while (true) {
                // 仅在预设模式下手动触发震动，音频模式由底层 Haptic Channels 接管
                if (viewModel.isPlaying && viewModel.currentMode == HapticMode.PRESET && viewModel.intensity.floatValue > 0.05f) {
                    triggerHaptic(viewModel.intensity.floatValue)
                }
                delay(50)
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
                            onTogglePlay = { handleTogglePlay() },
                            onExport = { exportHapticToStorage() }
                        )
                    }
                }
            }
        }
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    private fun startHapticProcessing(uri: Uri) {
        viewModel.stopAll()
        mediaPlayer?.release()
        visualizer?.release()
        viewModel.isProcessing = true
        
        val targetFile = File(cacheDir, "haptic_session.ogg")
        HapticTranscoder.transcode(this, uri, targetFile) { success ->
            runOnUiThread {
                viewModel.isProcessing = false
                if (success) {
                    currentHapticFile = targetFile
                    initHapticPlayer(Uri.fromFile(targetFile))
                } else {
                    Toast.makeText(this, getString(R.string.toast_transcode_fail), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun initHapticPlayer(uri: Uri) {
        try {
            mediaPlayer = MediaPlayer().apply {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setHapticChannelsMuted(false) // 开启音频内嵌的触觉通道
                    .build()
                
                setAudioAttributes(attrs)
                setDataSource(this@MainActivity, uri)
                prepare()
                setOnCompletionListener { 
                    viewModel.stopAll()
                    visualizer?.enabled = false
                }
            }
            setupVisualizer(mediaPlayer!!.audioSessionId)
            mediaPlayer?.start()
            viewModel.currentMode = HapticMode.AUDIO
            viewModel.isPlaying = true
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun setupVisualizer(sessionId: Int) {
        try {
            visualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, sr: Int) {
                        waveform?.let { data ->
                            val points = List(64) { i ->
                                val idx = (i * (data.size / 64)).coerceIn(0, data.size - 1)
                                ((data[idx].toInt() and 0xFF) - 128) / 128f
                            }
                            viewModel.updateWaveform(points)
                        }
                    }

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, sr: Int) {
                        fft?.let { safeFft ->
                            var energy = 0f
                            for (i in 1..12) {
                                energy += hypot(safeFft[i*2].toFloat(), safeFft[i*2+1].toFloat())
                            }
                            viewModel.intensity.floatValue = (energy / 600f).coerceIn(0f, 1f)
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)
                enabled = true
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // 原版触发逻辑（用于 PRESET 模式）
    private fun triggerHaptic(intensity: Float) {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        val boost = if (viewModel.isSensualMode) 1.8f else 1.0f
        val finalStrength = (intensity * intensity * 255 * boost * viewModel.vibrationBoost).toInt().coerceIn(0, 255)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, finalStrength))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    private fun handleTogglePlay() {
        if (viewModel.currentMode == HapticMode.AUDIO) {
            if (mediaPlayer == null) return
            if (viewModel.isPlaying) {
                mediaPlayer?.pause()
                visualizer?.enabled = false
            } else {
                mediaPlayer?.start()
                visualizer?.enabled = true
            }
            viewModel.isPlaying = !viewModel.isPlaying
        } else if (viewModel.currentMode == HapticMode.PRESET) {
            viewModel.isPlaying = !viewModel.isPlaying
        }
    }
    
    private fun getFileNameFromUri(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "audio"
    }

    private fun exportHapticToStorage() {
        val file = currentHapticFile
        if (file == null || viewModel.currentMode != HapticMode.AUDIO) {
            Toast.makeText(this, getString(R.string.toast_export_fail), Toast.LENGTH_SHORT).show()
            return
        }
        
        // 提取原文件名（去掉后缀），加上 _haptic.ogg
        val baseName = viewModel.originalFileName.substringBeforeLast(".")
        val suggestedName = "${baseName}_haptic.ogg"
        
        // 调起系统文件管理器，让用户选择保存位置，并传入建议的文件名
        exportLauncher.launch(suggestedName)
    }
    
    private fun saveFileToSelectedUri(destUri: Uri) {
        val file = currentHapticFile ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(destUri)?.use { outStream ->
                    file.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                withContext(Dispatchers.Main) { 
                    Toast.makeText(this@MainActivity, getString(R.string.toast_export_success), Toast.LENGTH_SHORT).show() 
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_export_fail), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        visualizer?.release()
    }
}
