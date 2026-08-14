# Getting Started with Glyph Sharge

This guide will help you set up and start using Glyph Sharge on your Nothing Phone.

## 📋 Prerequisites

### Hardware Requirements

- **Nothing Phone (1)** (Device 20111)
- **Nothing Phone (2)** (Device 22111)
- **Nothing Phone (2a)** (Device 23111 / 23113)
- **Nothing Phone (3a)** (Device 24111)

### Software Requirements

- Android 14 (API 34) or higher
- Nothing OS 2.0 or later recommended
- Enabled Developer Options (for debugging)

## 📥 Installation

### Option 1: Install from Release APK

1. Navigate to the [Releases page](https://github.com/hardWorker254/Glyph-Sharge-fork/releases)
2. Download the latest APK file (`GlyphSharge-vX.X.X.apk`)
3. Enable "Install from Unknown Sources" in your device settings
4. Open the downloaded APK and follow installation prompts
5. Launch the app from your app drawer

### Option 2: Build from Source

#### Clone the Repository

```bash
git clone https://github.com/hardWorker254/Glyph-Sharge-fork.git
cd Glyph-Sharge-fork
```

#### Build with Gradle

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

The APK will be located at:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release-unsigned.apk`

#### Build with Android Studio

1. Open Android Studio
2. Select **File > Open** and navigate to the project folder
3. Wait for Gradle sync to complete
4. Click **Build > Build Bundle(s) / APK(s) > Build APK(s)**

## ⚙️ Initial Setup

### First Launch

1. **Open the app** — The app will automatically detect if you're using a Nothing Phone
2. **Grant Permissions** — Accept the following permissions when prompted:
   - Notifications
   - Background Service
   - NFC (for NFC Glyph feature)
   
3. **Complete Onboarding** — Follow the interactive tutorial

### Required Permissions

| Permission | Purpose | Required |
|------------|---------|----------|
| `FOREGROUND_SERVICE` | Run glyph services in background | Yes |
| `POST_NOTIFICATIONS` | Show service notifications | Yes |
| `RECEIVE_BOOT_COMPLETED` | Auto-start services on boot | Optional |
| `NFC` | NFC transaction detection | For NFC feature |
| `VIBRATE` | Haptic feedback | Optional |
| `WAKE_LOCK` | Keep CPU awake during animations | Yes |

### Configure Settings

Navigate to **Settings** to customize:

1. **Theme** — Choose Light, Dark, or System theme
2. **Font** — Adjust font family and sizes
3. **Language** — Select your preferred language
4. **Quiet Hours** — Set scheduled silent periods

## 🎯 Quick Start Guide

### Enable Your First Feature

#### Power Peek (Recommended for First Use)

Power Peek shows battery level using glyph patterns when you shake your phone.

1. Open Glyph Sharge
2. Find the **Power Peek** card on the home screen
3. Toggle the switch to **Enable**
4. Tap **Test** to see a demo
5. Shake your phone to activate

#### Pulse Lock

Adds glyph animation when you unlock your phone.

1. Find the **Pulse Lock** card
2. Enable the feature
3. Choose an animation pattern
4. Lock and unlock your phone to test

### Understanding Services

Glyph Sharge uses foreground services to maintain glyph functionality:

| Service | When Active | Notification |
|---------|-------------|--------------|
| PulseLockService | When enabled | Persistent |
| PowerPeekService | When enabled | Persistent |
| LowBatteryAlertService | When battery < threshold | Only on alert |
| ScreenOffGlyphService | When enabled | Persistent |
| NfcGlyphService | When enabled | Persistent |
| ChargingAnimationService | While charging | Only while charging |
| QuietHoursService | During scheduled hours | Silent |

## 🔧 Troubleshooting

### App Crashes on Launch

1. Clear app data: **Settings > Apps > Glyph Sharge > Storage > Clear Data**
2. Reinstall the app
3. Ensure you're using a supported Nothing Phone device

### Glyphs Not Working

1. **Check Device Compatibility**: Verify you have a Nothing Phone
2. **Restart Glyph Service**: 
   - Go to Settings
   - Toggle "Glyph Service" off and on
3. **Reboot Device**: Restart your phone
4. **Check Permissions**: Ensure all required permissions are granted

### Services Not Starting

1. Disable battery optimization for Glyph Sharge:
   - **Settings > Apps > Glyph Sharge > Battery > Unrestricted**
2. Allow background activity
3. Check notification permissions

### NFC Feature Not Working

1. Ensure NFC is enabled in system settings
2. Make sure the app has NFC permission
3. Test with an NFC tag or payment terminal

### Battery Drain Issues

1. Review active services in the app
2. Disable unused features
3. Enable **Quiet Hours** for nighttime
4. Reduce animation durations

## 📱 Navigation Guide

### Home Screen

The main screen displays feature cards:

- **Toggle Switch** — Enable/disable each feature
- **Card Tap** — Opens configuration dialog
- **Test Button** — Preview the feature
- **Status Indicator** — Shows if service is active

### Settings Screen

Access via the gear icon (⚙️):

- **Theme Settings** — Visual customization
- **Font Settings** — Typography adjustments
- **Vibration Settings** — Haptic intensity
- **Quiet Hours** — Scheduled silence
- **Language** — App language

### Hidden Settings

Access hidden developer settings by tapping the version number 7 times in **Settings > About**.

## 🎨 Customization

### Creating Custom Animations

1. Navigate to **Animations** in settings
2. Tap **+ Create New**
3. Design your pattern using the channel editor
4. Save with a descriptive name
5. Assign to any feature

### Theme Colors

Glyph Sharge supports Material You dynamic colors:

1. Go to **Settings > Theme**
2. Select **Dynamic Colors** to match your wallpaper
3. Or choose from preset color schemes

## 📊 Understanding Logs

For debugging, enable logging:

1. Go to **Settings > Advanced > Enable Logging**
2. Reproduce the issue
3. Export logs via **Settings > Export Logs**
4. Include logs when reporting bugs

## 🆘 Getting Help

### Before Asking for Help

1. ✅ Check this documentation
2. ✅ Review the FAQ
3. ✅ Search existing GitHub issues
4. ✅ Try basic troubleshooting steps

### Reporting Bugs

When creating a bug report, include:

- Device model (e.g., Nothing Phone (2))
- Nothing OS version
- App version
- Steps to reproduce
- Expected vs actual behavior
- Screenshots or screen recordings
- Exported logs (if applicable)

### Community Resources

- [GitHub Issues](https://github.com/hardWorker254/Glyph-Sharge-fork/issues)
- [Discussions Forum](https://github.com/hardWorker254/Glyph-Sharge-fork/discussions)
- [Nothing Community Forums](https://community.nothing.tech/)

---

**Next Steps:**
- Explore [Features](FEATURES.md) to learn about all capabilities
- Read about [Architecture](ARCHITECTURE.md) for technical details
- Check the [FAQ](FAQ.md) for common questions
