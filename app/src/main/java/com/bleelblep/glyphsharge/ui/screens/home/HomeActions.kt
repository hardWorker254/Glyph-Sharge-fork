package com.bleelblep.glyphsharge.ui.screens.home

/**
 * The test / enable / disable commands for every glyph feature.
 *
 * These used to be eighteen separate parameters on `MainScreen`, which made
 * the call site run to forty lines and hid the fact that all six features
 * expose exactly the same three commands. Grouping them means the home screen
 * takes one parameter instead of eighteen, and adding a feature no longer
 * changes a twenty-parameter signature.
 */
data class HomeActions(
    // Power Peek
    val onTestPowerPeek: () -> Unit,
    val onEnablePowerPeek: () -> Unit,
    val onDisablePowerPeek: () -> Unit,

    // Pulse Lock
    val onTestPulseLock: () -> Unit,
    val onEnablePulseLock: () -> Unit,
    val onDisablePulseLock: () -> Unit,

    // Screen Off
    val onTestScreenOff: () -> Unit,
    val onEnableScreenOff: () -> Unit,
    val onDisableScreenOff: () -> Unit,

    // NFC
    val onTestNfc: () -> Unit,
    val onEnableNfc: () -> Unit,
    val onDisableNfc: () -> Unit,

    // Charging Animation
    val onTestChargingAnimation: () -> Unit,
    val onEnableChargingAnimation: () -> Unit,
    val onDisableChargingAnimation: () -> Unit,

    // Low Battery
    val onTestLowBattery: () -> Unit,
    val onEnableLowBattery: () -> Unit,
    val onDisableLowBattery: () -> Unit
)
