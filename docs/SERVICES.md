# Background Services Documentation

Comprehensive guide to Glyph Sharge's foreground services.

## 📡 Overview

Glyph Sharge uses **foreground services** to maintain persistent glyph functionality even when the app is not in focus. Each feature has its own dedicated service for isolation and reliability.

---

## Service Architecture

### Common Pattern

All services follow a consistent architecture:

```kotlin
@AndroidEntryPoint
class ExampleService : Service() {
    
    // 1. Companion object with constants
    companion object {
        const val ACTION_START = "..."
        const val ACTION_STOP = "..."
        const val NOTIF_ID = 1000
    }
    
    // 2. Hilt injections
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureCoordinator: GlyphFeatureCoordinator
    
    // 3. Coroutine scopes
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // 4. Lifecycle methods
    override fun onCreate() { }
    override fun onStartCommand() { }
    override fun onBind() { return null }
    override fun onDestroy() { }
}
```

---

## Service Details

### 🔓 PulseLockService

**Purpose:** Plays glyph animation when device is unlocked.

**File:** `services/PulseLockService.kt`

**Notification ID:** 1010

**Actions:**
- `ACTION_START` — Start listening for unlock events
- `ACTION_STOP` — Stop service

**Broadcast Receiver:**
```kotlin
private val unlockReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_USER_PRESENT) {
            playPulseLockSequence()
        }
    }
}
```

**Workflow:**
1. Register for `ACTION_USER_PRESENT` broadcast
2. On unlock, acquire glyph lock
3. Play selected animation
4. Release glyph lock

**Configuration:**
- Animation ID
- Duration (default: 5000ms)
- Sound effect (optional)
- Haptic feedback

---

### ⚡ PowerPeekService

**Purpose:** Shows battery level via glyphs when phone is shaken.

**File:** `services/PowerPeekService.kt`

**Notification ID:** 1013

**Actions:**
- `ACTION_START` — Enable shake detection
- `ACTION_STOP` — Disable service

**Sensor Implementation:**
```kotlin
class PowerPeekService : Service(), SensorEventListener {
    
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    
    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        
        val acceleration = sqrt(x*x + y*y + z*z)
        
        if (acceleration > threshold) {
            triggerPowerPeek()
        }
    }
}
```

**Workflow:**
1. Register accelerometer listener
2. Monitor for shake gesture
3. Check screen is off (to avoid conflict)
4. Acquire glyph lock
5. Display battery pattern
6. Release lock after duration

**Configuration:**
- Shake sensitivity threshold
- Display duration
- Vibration intensity

---

### 🔋 LowBatteryAlertService

**Purpose:** Alerts user when battery reaches critical level.

**File:** `services/LowBatteryAlertService.kt`

**Notification ID:** 1338

**Actions:**
- `ACTION_START_LOW_BATTERY_ALERT`
- `ACTION_STOP_LOW_BATTERY_ALERT`
- `ACTION_TEST_ALERT`

**Battery Monitoring:**
```kotlin
private val batteryReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
        
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = level * 100 / scale
        
        if (pct <= threshold && !charging) {
            playLowBatterySequence()
        }
    }
}
```

**Features:**
- Configurable threshold (5%-30%)
- Auto-reset when charging resumes
- Optional sound alert
- Wake lock for guaranteed delivery

---

### 🌙 ScreenOffGlyphService

**Purpose:** Plays animation when screen turns off.

**File:** `services/ScreenOffGlyphService.kt`

**Notification ID:** 1011

**Actions:**
- `ACTION_START` — Enable feature
- `ACTION_STOP` — Disable feature

**Implementation:**
```kotlin
private val screenOffReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_SCREEN_OFF) {
            playScreenOffSequence()
        }
    }
}
```

**Workflow:**
1. Listen for `ACTION_SCREEN_OFF`
2. Acquire glyph lock immediately
3. Play farewell animation
4. Release lock (service stays active)

**Configuration:**
- Animation selection
- Duration (1-5 seconds)

---

### 📡 NfcGlyphService

**Purpose:** Provides glyph feedback for NFC transactions.

**File:** `services/NfcGlyphService.kt`

**Notification ID:** 1012

**Actions:**
- `ACTION_START` — Enable NFC monitoring
- `ACTION_STOP` — Disable service

**Monitored Events:**
- `ACTION_TRANSACTION_DETECTED` — HCE payment
- Forwarded tag discovery intents

**Special Feature:**
```kotlin
companion object {
    /**
     * Forward NFC intents from Activity to service
     */
    fun forwardNfcIntent(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, NfcGlyphService::class.java).apply {
            putExtra("NFC_INTENT", intent)
        }
        context.startService(serviceIntent)
    }
}
```

**Use Cases:**
- Contactless payment confirmation
- NFC tag read feedback
- Security awareness

---

### 🔌 ChargingAnimationService

**Purpose:** Animations for power connect/disconnect events.

**File:** `services/ChargingAnimationService.kt`

**Notification ID:** 1014

**Actions:**
- `ACTION_START` — Enable monitoring
- `ACTION_STOP` — Disable service

**Power Monitoring:**
```kotlin
private val powerConnectionReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_POWER_CONNECTED -> 
                triggerChargingAnimation()
            Intent.ACTION_POWER_DISCONNECTED -> 
                triggerChargingAnimation()
        }
    }
}
```

**Features:**
- Different animations for connect/disconnect
- Optional progress indication
- Wake lock during animation

---

### 🤫 QuietHoursService

**Purpose:** Scheduled silence periods for glyph notifications.

**File:** `services/QuietHoursService.kt`

**Notification ID:** 1004

**Actions:**
- `ACTION_START_QUIET_HOURS` — Manual start
- `ACTION_STOP_QUIET_HOURS` — Manual stop
- `ACTION_QUIET_HOURS_START` — Scheduled start (alarm)
- `ACTION_QUIET_HOURS_END` — Scheduled end (alarm)

**Alarm Scheduling:**
```kotlin
private fun scheduleExactAlarm(isStartAlarm: Boolean) {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, if (isStartAlarm) startHour else endHour)
        set(Calendar.MINUTE, if (isStartAlarm) startMinute else endMinute)
    }
    
    val pendingIntent = PendingIntent.getBroadcast(
        this,
        if (isStartAlarm) RC_START else RC_END,
        Intent(this, QuietHoursReceiver::class.java).apply {
            action = if (isStartAlarm) ACTION_QUIET_HOURS_START 
                     else ACTION_QUIET_HOURS_END
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        calendar.timeInMillis,
        pendingIntent
    )
}
```

**Features:**
- Exact alarms (works even in Doze mode)
- Auto-reschedule for daily recurrence
- Silent notification during active period

---

## Service Lifecycle Management

### Starting Services

```kotlin
// For Android O+ (API 26+)
val intent = Intent(context, PulseLockService::class.java).apply {
    action = PulseLockService.ACTION_START
}
ContextCompat.startForegroundService(context, intent)
```

### Stopping Services

```kotlin
val intent = Intent(context, PulseLockService::class.java).apply {
    action = PulseLockService.ACTION_STOP
}
context.startService(intent)
```

### Notification Channels

Each service creates its own notification channel:

```kotlin
private fun createNotificationChannel() {
    val channel = NotificationChannel(
        CHANNEL_ID,
        "Service Name",
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = "Service notification"
        setShowBadge(false)
    }
    
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)
}
```

---

## Service Coordination

### Lock Acquisition Pattern

All services use `GlyphFeatureCoordinator` for exclusive LED access:

```kotlin
scope.launch {
    if (featureCoordinator.acquire(GlyphFeature.POWER_PEEK)) {
        try {
            // Safe to use glyphs
            glyphAnimationManager.playAnimation(animationId)
        } finally {
            featureCoordinator.release(GlyphFeature.POWER_PEEK)
        }
    } else {
        Log.w(TAG, "Could not acquire glyph lock")
    }
}
```

### Priority System

When multiple features request glyphs simultaneously:

1. **Low Battery Alert** — Highest priority (safety)
2. **NFC Transaction** — High priority (security)
3. **Power Peek** — Medium priority (utility)
4. **Pulse Lock** — Medium priority (UX)
5. **Screen Off** — Lower priority (cosmetic)
6. **Charging Animation** — Lower priority (cosmetic)
7. **Manual Demo** — Lowest priority (user-initiated)

---

## Boot Persistence

### BootCompletedReceiver

Services can auto-start on device boot:

```kotlin
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {
    
    @Inject lateinit var settingsRepository: SettingsRepository
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            
            if (settingsRepository.isPulseLockEnabled()) {
                context.startService(Intent(context, PulseLockService::class.java).apply {
                    action = PulseLockService.ACTION_START
                })
            }
            
            // ... check other enabled services
        }
    }
}
```

**Manifest Registration:**
```xml
<receiver
    android:name=".receiver.BootCompletedReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

---

## Battery Optimization

### Wake Locks

Services use partial wake locks sparingly:

```kotlin
private lateinit var wakeLock: PowerManager.WakeLock

wakeLock = powerManager.newWakeLock(
    PowerManager.PARTIAL_WAKE_LOCK,
    "GlyphSharge:ServiceName"
).apply {
    acquire(timeoutMs)  // Always use timeout!
}
```

### Best Practices

1. **Minimize active time** — Release locks ASAP
2. **Use timeouts** — Prevent battery drain
3. **Check necessity** — Don't run if feature disabled
4. **Respect Quiet Hours** — Silence during scheduled periods

---

## Troubleshooting

### Service Not Starting

**Check:**
1. ✅ Permission granted (`FOREGROUND_SERVICE`)
2. ✅ Notification channel created
3. ✅ Action string matches
4. ✅ Service declared in manifest

### Service Crashes

**Debug Steps:**
1. Check logcat for exceptions
2. Verify DI injection successful
3. Ensure notification not null
4. Test on target device

### Battery Drain

**Solutions:**
1. Reduce active service count
2. Shorten animation durations
3. Enable Quiet Hours
4. Update to latest version

---

## Service Comparison

| Service | Trigger | Persistent | Notification | Priority |
|---------|---------|------------|--------------|----------|
| PulseLock | Unlock event | Yes | Low | Medium |
| PowerPeek | Shake gesture | Yes | Low | Medium |
| LowBattery | Battery threshold | Yes | High (on alert) | **High** |
| ScreenOff | Screen off | Yes | Low | Lower |
| NfcGlyph | NFC event | Yes | Low | **High** |
| ChargingAnim | Power change | No (temp) | None | Lower |
| QuietHours | Scheduled | Yes | Silent | N/A |

---

**Related Documentation:**
- [Features](FEATURES.md) — User-facing feature descriptions
- [Architecture](ARCHITECTURE.md) — Technical implementation details
- [API Reference](API_REFERENCE.md) — Method-level documentation
