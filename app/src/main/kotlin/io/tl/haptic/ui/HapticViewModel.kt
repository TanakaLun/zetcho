package io.tl.haptic.ui

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.tl.haptic.R
import kotlinx.coroutines.*
import kotlin.math.*

enum class HapticMode { IDLE, AUDIO, PRESET }

enum class PresetType(val labelRes: Int) {
    ROSE(R.string.preset_rose),
    GOLD(R.string.preset_gold),
    OCEAN(R.string.preset_ocean),
    PASSION(R.string.preset_passion)
}

class HapticViewModel : ViewModel() {
    var currentMode by mutableStateOf(HapticMode.IDLE)
    var isPlaying by mutableStateOf(false)
    var isSensualMode by mutableStateOf(false)
    var isShowingSettings by mutableStateOf(false)
    var vibrationBoost by mutableFloatStateOf(1.0f)
    
    var intensity = mutableFloatStateOf(0f)
    val rawWaveformData = mutableStateListOf<Float>().apply { repeat(64) { add(0f) } }
    
    private var activeJob: Job? = null

    fun updateWaveform(newData: List<Float>) {
        newData.forEachIndexed { index, value ->
            if (index < rawWaveformData.size) rawWaveformData[index] = value
        }
    }

    fun startPreset(type: PresetType) {
        stopAll()
        currentMode = HapticMode.PRESET
        isPlaying = true
        activeJob = viewModelScope.launch(Dispatchers.Default) {
            var time = 0f
            while (isActive) {
                val value = when (type) {
                    PresetType.ROSE -> (sin(time) * 0.4f + 0.5f).coerceIn(0f, 1f)
                    PresetType.GOLD -> if (sin(time * 3f) > 0.6f) 1.0f else 0.1f
                    PresetType.OCEAN -> abs(sin(time * 0.4f) * cos(time * 0.1f))
                    PresetType.PASSION -> (0.4f + (Math.random() * 0.6f).toFloat())
                }
                intensity.floatValue = value
                val points = List(64) { i -> (value * sin(i * 0.2f + time)).coerceIn(-1f, 1f) }
                withContext(Dispatchers.Main) { updateWaveform(points) }
                time += 0.1f
                delay(50)
            }
        }
    }

    fun stopAll() {
        activeJob?.cancel()
        isPlaying = false
        intensity.floatValue = 0f
        rawWaveformData.fill(0f)
    }
}
