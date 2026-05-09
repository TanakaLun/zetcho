package io.tl.haptic

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

class HapticViewModel : ViewModel() {
    var isPlaying by mutableStateOf(false)
    var isProcessing by mutableStateOf(false)
    var currentMode by mutableStateOf(HapticMode.IDLE)
    var currentPreset by mutableStateOf<PresetType?>(null)

    var isShowingSettings by mutableStateOf(false)
    var isSensualMode by mutableStateOf(false)
    var vibrationBoost by mutableFloatStateOf(1.0f)
    var dynamicFrequency by mutableStateOf(true)

    var intensity = mutableFloatStateOf(0f)
    var rawWaveformData by mutableStateOf<List<Float>>(List(64) { 0f })
    
    var originalFileName by mutableStateOf("audio") 

    private var presetJob: Job? = null

    fun updateWaveform(data: List<Float>) {
        rawWaveformData = data
    }

    fun stopAll() {
        isPlaying = false
        currentMode = HapticMode.IDLE
        currentPreset = null
        intensity.floatValue = 0f
        rawWaveformData = List(64) { 0f }
        presetJob?.cancel()
    }

    fun startPreset(type: PresetType) {
        stopAll()
        currentMode = HapticMode.PRESET
        currentPreset = type
        isPlaying = true

        presetJob = viewModelScope.launch {
            var time = 0f
            while (isActive) {
                // 根据预设类型生成不同的能量波形
                val energy = when (type) {
                    PresetType.ROSE -> abs(sin(time * 2f)) * 0.5f // 温柔起伏
                    PresetType.GOLD -> if (time % 2f < 0.5f) 0.8f else 0.1f // 庄重宏大，脉冲
                    PresetType.OCEAN -> abs(sin(time * 0.5f)) * 0.9f // 深邃共振
                    PresetType.PASSION -> abs(sin(time * 10f)) * 0.9f + (Math.random().toFloat() * 0.2f) // 狂野打击
                }.coerceIn(0f, 1f)

                intensity.floatValue = energy
                
                // 模拟波形数据以驱动 UI 动画
                val simulatedWave = List(64) { i ->
                    (sin(i * 0.2f + time * 5f) * energy * 0.8f) + (Math.random().toFloat() * energy * 0.2f)
                }
                rawWaveformData = simulatedWave

                time += 0.05f
                delay(50)
            }
        }
    }
}
