# Glyph Sharge Features

Complete guide to all features available in Glyph Sharge.

## 🎯 Overview

Glyph Sharge provides a comprehensive suite of glyph-based features designed to enhance your Nothing Phone experience. Each feature can be independently enabled, configured, and customized.

---

## Core Features

### ⚡ Power Peek

**Description:** Check your battery level at a glance using glyph patterns — no need to turn on your screen.

**How it Works:**
- Uses accelerometer to detect when you pick up and shake your phone
- Displays battery percentage using glyph channel patterns
- Automatically turns off after a few seconds

**Configuration Options:**
- Enable/Disable toggle
- Shake sensitivity threshold
- Display duration (1-10 seconds)
- Vibration intensity feedback

**Service:** `PowerPeekService`

**Use Cases:**
- Quick battery check without waking the screen
- Discreet battery monitoring during meetings
- Battery check while phone is face-down

---

### 🔓 Pulse Lock (Glow Gate)

**Description:** Add a spectacular glyph animation every time you unlock your phone.

**How it Works:**
- Listens for `ACTION_USER_PRESENT` broadcast
- Plays selected glyph animation sequence
- Optional sound effect synchronization

**Configuration Options:**
- Enable/Disable toggle
- Animation selection (preset or custom)
- Animation duration
- Sound effect toggle
- Haptic feedback intensity

**Service:** `PulseLockService`

**Use Cases:**
- Personalize unlock experience
- Visual confirmation of successful unlock
- Show off your phone's glyph capabilities

---

### 🔋 Low Battery Alert

**Description:** Get a visual glyph warning when your battery reaches a critical level.

**How it Works:**
- Monitors battery level continuously
- Triggers alert when battery drops below threshold
- Can include sound and vibration

**Configuration Options:**
- Enable/Disable toggle
- Battery threshold (5%-30%)
- Animation pattern
- Alert duration
- Sound effect toggle
- Repeat interval

**Service:** `LowBatteryAlertService`

**Use Cases:**
- Never miss low battery warnings
- Visual alert in noisy environments
- Customizable urgency levels

---

### 🌙 Screen Off Glyph

**Description:** Play a glyph animation whenever you turn off your screen.

**How it Works:**
- Detects `ACTION_SCREEN_OFF` broadcast
- Plays farewell animation
- Smooth transition to sleep mode

**Configuration Options:**
- Enable/Disable toggle
- Animation selection
- Duration (1-5 seconds)
- Delay before activation

**Service:** `ScreenOffGlyphService`

**Use Cases:**
- Satisfying screen-off feedback
- Visual confirmation phone is locking
- Personalized goodbye animation

---

### 📡 NFC Glyph

**Description:** Get glyph feedback during NFC transactions and tag interactions.

**How it Works:**
- Monitors NFC transaction broadcasts
- Detects HCE payment events
- Plays animation on successful transaction
- Supports tag discovery events

**Configuration Options:**
- Enable/Disable toggle
- Animation for payments
- Animation for tag reads
- Duration settings
- Success/failure differentiation

**Service:** `NfcGlyphService`

**Use Cases:**
- Contactless payment confirmation
- NFC tag interaction feedback
- Enhanced security awareness

---

### 🔌 Charging Animation

**Description:** Beautiful glyph animations when you connect or disconnect power.

**How it Works:**
- Monitors `ACTION_POWER_CONNECTED` and `ACTION_POWER_DISCONNECTED`
- Plays different animations for connect/disconnect
- Can show charging progress

**Configuration Options:**
- Enable/Disable toggle
- Connection animation
- Disconnection animation
- Progress indication style
- Animation speed

**Service:** `ChargingAnimationService`

**Use Cases:**
- Visual charging confirmation
- Cable connection verification
- Aesthetic charging experience

---

### 🤫 Quiet Hours

**Description:** Schedule periods when glyph notifications are silenced.

**How it Works:**
- Uses AlarmManager for precise scheduling
- Automatically enables/disables based on time
- Maintains service state across reboots

**Configuration Options:**
- Enable/Disable toggle
- Start time (hour:minute)
- End time (hour:minute)
- Recurrence (daily/weekdays/weekends)
- Override for emergencies

**Service:** `QuietHoursService`

**Use Cases:**
- Sleep mode automation
- Meeting/gaming silent periods
- Scheduled do-not-disturb

---

## Advanced Features

### 🎨 Custom Animations

Create your own glyph patterns using the built-in animation editor.

**Capabilities:**
- Channel-by-channel brightness control
- Frame-by-frame animation
- Duration adjustment per frame
- Preview before saving
- Import/export animation files

**Technical Details:**
- Supports all glyph channels per device model
- Frame rate: 16-1000ms per frame
- Brightness levels: 0-4000
- Unlimited custom animations

---

### 🔄 Glyph Feature Coordinator

**Intelligent LED Access Management**

The `GlyphFeatureCoordinator` ensures only one feature controls the glyphs at any time:

- **Mutex-based locking** — Prevents conflicts
- **Priority system** — Important alerts take precedence
- **Timeout protection** — Prevents lock monopolization
- **Automatic cleanup** — Releases locks on feature completion

**Feature Priority Order:**
1. Low Battery Alert (highest)
2. NFC Transaction
3. Power Peek
4. Pulse Lock
5. Screen Off
6. Charging Animation
7. Manual Demo (lowest)

---

### 📊 Battery Story

Track and visualize your battery usage patterns with glyph-enhanced statistics.

**Features:**
- Charging session history
- Battery drain analytics
- Usage pattern insights
- Glyph-based data visualization

**Data Points:**
- Session start/end times
- Battery percentage changes
- Charging speed analysis
- Usage statistics

---

### 🎯 Haptic Feedback System

Synchronized vibration patterns complement glyph animations.

**Options:**
- Light, Medium, Heavy intensity
- Pattern synchronization
- Per-feature customization
- Accessibility modes

---

## Feature Comparison Table

| Feature | Trigger | Duration | Customizable | Service Type |
|---------|---------|----------|--------------|--------------|
| Power Peek | Shake gesture | 3-10s | ✅ | Foreground |
| Pulse Lock | Device unlock | 1-5s | ✅ | Foreground |
| Low Battery | Battery < threshold | 5-15s | ✅ | Foreground |
| Screen Off | Screen off | 1-5s | ✅ | Foreground |
| NFC Glyph | NFC event | 1-3s | ✅ | Foreground |
| Charging Anim | Power connect/disconnect | 2-10s | ✅ | Foreground |
| Quiet Hours | Scheduled time | Variable | ✅ | Foreground |

---

## Enabling/Disabling Features

### Via UI

1. Open Glyph Sharge
2. Find the feature card on home screen
3. Toggle the switch
4. Configure options if needed

### Via Settings

1. Go to **Settings**
2. Select the feature
3. Adjust configuration
4. Save changes

### Quick Settings Tile (Android 15+)

1. Pull down Quick Settings panel
2. Add Glyph Sharge tile
3. Tap to toggle main features

---

## Best Practices

### Battery Optimization

- Disable unused features
- Reduce animation durations
- Use Quiet Hours during sleep
- Lower haptic intensity

### Performance Tips

- Keep number of active services minimal
- Use shorter animation durations
- Avoid overlapping triggers
- Regular app updates

### Troubleshooting Features

If a feature stops working:

1. ✅ Check if service is enabled
2. ✅ Verify permissions granted
3. ✅ Restart the app
4. ✅ Reboot device
5. ✅ Clear app cache
6. ✅ Reinstall if necessary

---

## Feature Requests

Want a new feature? Submit your ideas:

1. Check existing requests on GitHub
2. Create detailed feature request
3. Include use case description
4. Provide examples if possible

**GitHub Issues:** https://github.com/hardWorker254/Glyph-Sharge-fork/issues

---

## Coming Soon

Planned future features:

- 🎵 Music visualizer mode
- 📞 Call notification patterns
- 💬 Message-specific glyphs
- 🌈 RGB color effects (where supported)
- ⏰ Alarm integration
- 📅 Calendar event reminders
- 🎮 Gaming mode enhancements

---

**Note:** Feature availability may vary by device model and Nothing OS version. Some features require specific hardware capabilities.
