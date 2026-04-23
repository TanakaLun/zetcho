package io.tl.haptic

import androidx.annotation.StringRes

enum class HapticMode {
    IDLE,
    AUDIO,
    PRESET
}

enum class PresetType(@StringRes val labelRes: Int) {
    ROSE(R.string.preset_rose),
    GOLD(R.string.preset_gold),
    OCEAN(R.string.preset_ocean),
    PASSION(R.string.preset_passion)
}