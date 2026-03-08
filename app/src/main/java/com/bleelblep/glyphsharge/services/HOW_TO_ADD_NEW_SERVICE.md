## 1. GlyphFeature enum

**Файл:** `com/bleelblep/glyphsharge/glyph/GlyphFeatureCoordinator.kt`

Добавь `NFC` в enum:

```kotlin
package com.bleelblep.glyphsharge.glyph

/**
 * Перечисление всех фич, которые могут захватывать LED-ы.
 * Используется [GlyphFeatureCoordinator] для арбитрации доступа.
 */
enum class GlyphFeature {
    POWER_PEEK,
    GLYPH_GUARD,
    PULSE_LOCK,
    LOW_BATTERY,
    SCREEN_OFF,
    NFC,            // ← ДОБАВИТЬ
    BATTERY_STORY,
    MANUAL_TEST
}
```

## 2. GlyphAnimationManager

**Файл:** `com/bleelblep/glyphsharge/glyph/GlyphAnimationManager.kt`

Добавь функцию `playNfcAnimation`:

```kotlin
/**
 * Play a nfc animation
 */
suspend fun playNfcAnimation(id: String) {
   if (!isGlyphServiceEnabled()) return

   val durationMs = settingsRepository.getScreenOffDuration()
   val cyclesFromDuration = (durationMs / 500L).toInt().coerceAtLeast(1)

   when (id) {
      "C1" -> runC1SequentialAnimation()
      "WAVE" -> runWaveAnimation()
      "BEEDAH" -> runBeedahAnimation()
      "LOCK" -> runLockPulseAnimation()
      "PULSE" -> runPulseEffect(cyclesFromDuration)
      "SPIRAL" -> runSpiralAnimation()
      "HEARTBEAT" -> runHeartbeatAnimation()
      "MATRIX" -> runMatrixRainAnimation()
      "FIREWORKS" -> runFireworksAnimation()
      "DNA" -> runDNAHelixAnimation()
      else -> runPulseEffect(cyclesFromDuration)
   }
}
```

> **Где добавить:** рядом с `playScreenOffAnimation()`, `playPulseLockAnimation()`, `playLowBatteryAnimation()` — они все следуют одному паттерну.

---

## 3. SettingsRepository

**Файл:** `com/bleelblep/glyphsharge/data/SettingsRepository.kt`
ВАЖНО! Необходимо также вставить этот код в ui/theme/SettingsRepository.kt, изменив имя пакета сверху
### 3.1 Константы (в `companion object`)

```kotlin
// NFC keys
private const val KEY_NFC_FEATURE_ENABLED    = "nfc_feature_enabled"
private const val KEY_NFC_ANIMATION_ID       = "nfc_animation_id"
private const val KEY_NFC_ANIMATION_DURATION = "nfc_animation_duration"

// NFC default
private const val DEFAULT_NFC_ANIMATION_DURATION = 3000L
```

### 3.2 First-run defaults

```kotlin
// В applyFirstRunDefaults(), внутри prefs.edit { ... }:
putBoolean(KEY_NFC_FEATURE_ENABLED, false)
```

### 3.3 Миграция (v111)

```kotlin
if (lastMigrated < 111) {
    prefs.edit {
        if (!prefs.contains(KEY_NFC_FEATURE_ENABLED)) {
            putBoolean(KEY_NFC_FEATURE_ENABLED, false)
        }
        putInt(KEY_LAST_MIGRATED_VERSION, 111)
    }
    Log.i(TAG, "Migration to 111 applied")
}
```

### 3.4 Функции доступа

```kotlin
// NFC
fun saveNfcFeatureEnabled(enabled: Boolean) =
    prefs.edit { putBoolean(KEY_NFC_FEATURE_ENABLED, enabled) }
fun isNfcFeatureEnabled(): Boolean =
    prefs.getBoolean(KEY_NFC_FEATURE_ENABLED, false)

fun saveNfcAnimationId(id: String) =
    prefs.edit { putString(KEY_NFC_ANIMATION_ID, id) }
fun getNfcAnimationId(): String =
    prefs.getString(KEY_NFC_ANIMATION_ID, "C1") ?: "C1"

fun saveNfcAnimationDuration(durationMs: Long) =
    prefs.edit { putLong(KEY_NFC_ANIMATION_DURATION, durationMs) }
fun getNfcAnimationDuration(): Long =
    prefs.getLong(KEY_NFC_ANIMATION_DURATION, DEFAULT_NFC_ANIMATION_DURATION)
```

### 3.5 Dump

```kotlin
// В dumpAllSettings():
NFC Feature enabled: ${isNfcFeatureEnabled()}
NFC Animation ID: ${getNfcAnimationId()}
NFC Animation Duration: ${getNfcAnimationDuration()}ms
```

---

## 4. NfcGlyphService

**Файл:** `com/bleelblep/glyphsharge/services/NfcGlyphService.kt`

> Создать целиком — файл уже был предоставлен ранее. Ключевые моменты:

```
NfcGlyphService : Service()
├── companion object
│   ├── ACTION_START / ACTION_STOP
│   ├── ACTION_NFC_TAG_FORWARDED (internal)
│   └── forwardNfcIntent(context, intent)  // вызывается из Activity.onNewIntent
│
├── BroadcastReceiver (ACTION_TRANSACTION_DETECTED — HCE/payments)
│
├── onStartCommand()
│   ├── ACTION_STOP → shutDown()
│   ├── ACTION_NFC_TAG_FORWARDED → triggerGlyphAnimation()
│   └── default → startForeground + START_STICKY
│
├── triggerGlyphAnimation()
│   ├── Check isNfcFeatureEnabled + getGlyphServiceEnabled
│   ├── Check isCurrentlyInQuietHours
│   ├── featureCoordinator.acquire(GlyphFeature.NFC)
│   ├── glyphAnimationManager.playNfcAnimation(animationId)
│   ├── watchdog delay(duration) → stop
│   └── featureCoordinator.release(GlyphFeature.NFC)
│
└── Notification (IMPORTANCE_LOW, ongoing)
```

---

## 5. MainActivity

**Файл:** `com/bleelblep/glyphsharge/MainActivity.kt`

### 5.1 Новые поля

```kotlin
private var nfcAdapter: NfcAdapter? = null
private var nfcPendingIntent: PendingIntent? = null
```

### 5.2 Новые импорты

```kotlin
import android.app.PendingIntent
import android.nfc.NfcAdapter
```

### 5.3 Lifecycle hooks

```kotlin
// onCreate() — добавить:
initializeNfcDispatch()

// onResume() — добавить:
enableNfcForegroundDispatch()

// onPause() — ДОБАВИТЬ МЕТОД:
override fun onPause() {
    super.onPause()
    disableNfcForegroundDispatch()
}

// onNewIntent() — ДОБАВИТЬ МЕТОД:
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    NfcGlyphService.forwardNfcIntent(this, intent)
}
```

### 5.4 Новые private-методы

```kotlin
private fun initializeNfcDispatch() {
    nfcAdapter = NfcAdapter.getDefaultAdapter(this)
    if (nfcAdapter == null) {
        Log.w(TAG, "NFC not available")
        return
    }
    nfcPendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

private fun initializeNfcFeature() {
    val enabled = settingsRepository.isNfcFeatureEnabled()
    val intent = Intent(this, NfcGlyphService::class.java).apply {
        action = if (enabled) NfcGlyphService.ACTION_START
                 else NfcGlyphService.ACTION_STOP
    }
    if (enabled) startForegroundServiceCompat(intent)
    else stopService(intent)
}

private fun enableNfcForegroundDispatch() {
    if (!settingsRepository.isNfcFeatureEnabled()) return
    try {
        nfcAdapter?.enableForegroundDispatch(this, nfcPendingIntent, null, null)
    } catch (e: Exception) {
        Log.e(TAG, "NFC dispatch error", e)
    }
}

private fun disableNfcForegroundDispatch() {
    try { nfcAdapter?.disableForegroundDispatch(this) }
    catch (_: Exception) {}
}
```

### 5.5 Публичные методы

```kotlin
fun testNfcAnimation() = lifecycleScope.launch {
    runCatching {
        glyphAnimationManager.playNfcAnimation(
            settingsRepository.getNfcAnimationId()
        )
    }.onFailure { Log.e(TAG, "NFC test error", it) }
}

fun enableNfcFeature() {
    if (nfcAdapter == null) { showToast("NFC not available"); return }
    if (nfcAdapter?.isEnabled == false) { showToast("Enable NFC in settings"); return }
    settingsRepository.saveNfcFeatureEnabled(true)
    initializeNfcFeature()
    enableNfcForegroundDispatch()
    showToast("NFC Glyph Animation enabled")
}

fun disableNfcFeature() {
    settingsRepository.saveNfcFeatureEnabled(false)
    initializeNfcFeature()
    disableNfcForegroundDispatch()
    showToast("NFC Glyph Animation disabled")
}
```

### 5.6 Обновить `initializeServices()`

```kotlin
private fun initializeServices() {
    initServiceByPref(PowerPeekService::class.java,       settingsRepository.isPowerPeekEnabled())
    initServiceByPref(LowBatteryAlertService::class.java, settingsRepository.isLowBatteryEnabled())
    initServiceByPref(QuietHoursService::class.java,      settingsRepository.isQuietHoursEnabled())
    initializePulseLock()
    initializeGlyphGuard()
    initializeScreenOffFeature()
    initializeNfcFeature()              // ← ДОБАВИТЬ
}
```

### 5.7 Обновить `syncServicesAfterToggle()`

```kotlin
private fun syncServicesAfterToggle(glyphOn: Boolean) {
    val fgIntent = Intent(this, GlyphForegroundService::class.java)
    if (glyphOn) {
        if (settingsRepository.isPulseLockEnabled())       initializePulseLock()
        if (settingsRepository.isScreenOffFeatureEnabled()) initializeScreenOffFeature()
        if (settingsRepository.isNfcFeatureEnabled())       initializeNfcFeature()   // ← ДОБАВИТЬ
        startForegroundServiceCompat(fgIntent)
    } else {
        runCatching { startService(Intent(this, PulseLockService::class.java).apply { action = PulseLockService.ACTION_STOP }) }
        runCatching { startService(Intent(this, ScreenOffGlyphService::class.java).apply { action = ScreenOffGlyphService.ACTION_STOP }) }
        runCatching { startService(Intent(this, NfcGlyphService::class.java).apply { action = NfcGlyphService.ACTION_STOP }) }  // ← ДОБАВИТЬ
        stopService(fgIntent)
    }
}
```

### 5.8 Обновить `MainScreen` — параметры и вызов

```kotlin
// Параметры MainScreen:
onTestNfc: () -> Unit,
onEnableNfc: () -> Unit,
onDisableNfc: () -> Unit,

// В setContent / MainScreen(...):
onTestNfc    = ::testNfcAnimation,
onEnableNfc  = ::enableNfcFeature,
onDisableNfc = ::disableNfcFeature,
```

### 5.9 В `MainScreen` LazyColumn добавить карточку

```kotlin
item {
    NfcGlyphCard(
        title = "NFC Glyph",
        description = "Play a Glyph animation on NFC tap or contactless payment.",
        icon = rememberVectorPainter(image = Icons.Default.Nfc),
        modifier = Modifier.fillMaxWidth(),
        iconSize = 32,
        isServiceActive = glyphServiceEnabled,
        onTestNfc    = { requireGlyphService(onTestNfc) },
        onEnableNfc  = { requireGlyphService(onEnableNfc) },
        onDisableNfc = onDisableNfc,
        settingsRepository = settingsRepository
    )
}
```

---

## 6. NfcGlyphCard

**Файл:** `com/bleelblep/glyphsharge/ui/components/NfcGlyphCard.kt`

> Структура:

```
NfcGlyphCard
├── FeatureCard (140dp, иконка с тремя состояниями цвета)
├── MorphingToggleButton (toggle в углу)
├── NfcGlyphConfirmationDialog
│   ├── "How it works" — info card
│   ├── 🧪 Test Animation
│   ├── ⚙️ Settings → NfcGlyphConfigDialog
│   └── ✕ Cancel
└── NfcGlyphConfigDialog
    ├── 🎞️ Animation picker (FlowRow + FilterChip)
    ├── 🧪 Test selected animation
    ├── ⏱️ Duration slider (1s–10s)
    ├── ℹ️ Info note
    ├── 💾 Save / ⚡ Enable
    ├── ✖️ Disable
    └── ✕ Cancel
```

---

## 7. AndroidManifest.xml

Необходимо зарегестрировать сервис в манифесте

---

## 8. BootCompletedReceiver

**Файл:** `com/bleelblep/glyphsharge/receivers/BootCompletedReceiver.kt`

Добавь запуск `NfcGlyphService` при загрузке

