# Glyph Sharge Documentation

Welcome to the official documentation for **Glyph Sharge** — your ultimate control center for the Nothing Glyph Interface.

## 📚 Documentation Contents

- **[Getting Started](GETTING_STARTED.md)** — Installation, setup, and quick start guide
- **[Features](FEATURES.md)** — Complete overview of all features and capabilities
- **[Architecture](ARCHITECTURE.md)** — Technical architecture and code structure
- **[API Reference](API_REFERENCE.md)** — Detailed API documentation for developers
- **[UI Components](UI_COMPONENTS.md)** — UI components and customization guide
- **[Services](SERVICES.md)** — Background services and their functionality
- **[FAQ](FAQ.md)** — Frequently asked questions and troubleshooting

---

## 🚀 Quick Links

- [Download Latest Release](https://github.com/hardWorker254/Glyph-Sharge-fork/releases/tag/Main)
- [Nothing Glyph Developer Kit](https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit)
- [Report an Issue](https://github.com/hardWorker254/Glyph-Sharge-fork/issues)

---

## 📱 What is Glyph Sharge?

**Glyph Sharge** is a powerful Android application designed for Nothing Phone devices that provides complete control over the Glyph Interface. With a modern Material You design and deep system integration, it lets you:

- ⚡ **Manage Power** — Visual battery indicators and charging animations
- 🔐 **Enhance Security** — Glyph-based notifications for security events
- 🎨 **Personalize** — Custom glyph patterns and animations
- 🔔 **Smart Notifications** — Glyph alerts for calls, messages, and events

### Supported Devices

- Nothing Phone (1) — Device 20111
- Nothing Phone (2) — Device 22111
- Nothing Phone (2a) — Device 23111 / 23113
- Nothing Phone (3a) — Device 24111

---

## 🛠 Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose (Material Design 3) |
| Dependency Injection | Hilt |
| Async Operations | Kotlin Coroutines & Flow |
| Database | Room (SQLite) |
| SDK | Nothing Ketchum Glyph SDK |
| Min SDK | Android 14 (API 34) |
| Target SDK | Android 15 (API 35) |

---

## 📦 Project Structure

```
app/src/main/java/com/bleelblep/glyphsharge/
├── di/                     # Dependency Injection modules
├── data/                   # Data layer
│   ├── model/              # Data models
│   ├── repository/         # Repositories
│   └── local/              # Local database (Room)
├── glyph/                  # Glyph interface management
├── services/               # Background services
├── receiver/               # Broadcast receivers
├── ui/                     # UI layer
│   ├── screens/            # Composable screens
│   ├── components/         # Reusable UI components
│   ├── theme/              # Theme configuration
│   ├── utils/              # UI utilities
│   └── viewmodel/          # ViewModels
└── utils/                  # General utilities
```

---

## 🔗 Key Features Overview

### Core Features

| Feature | Description | Service |
|---------|-------------|---------|
| **Pulse Lock** | Glyph animation on device unlock | `PulseLockService` |
| **Power Peek** | Shake-to-check battery with glyphs | `PowerPeekService` |
| **Low Battery Alert** | Visual alert when battery is low | `LowBatteryAlertService` |
| **Screen Off Glyph** | Animation when screen turns off | `ScreenOffGlyphService` |
| **NFC Glyph** | Glyph feedback for NFC transactions | `NfcGlyphService` |
| **Charging Animation** | Custom animations while charging | `ChargingAnimationService` |
| **Quiet Hours** | Scheduled glyph silence periods | `QuietHoursService` |

### Advanced Features

- **Glyph Feature Coordinator** — Manages exclusive access to Glyph LEDs
- **Custom Animations** — User-defined glyph patterns
- **Material You Theming** — Dynamic color support
- **Multi-language Support** — Localization ready
- **Haptic Feedback** — Synchronized vibration patterns

---

## 🏗 Architecture

Glyph Sharge follows **Clean Architecture** principles with MVVM pattern:

```
┌─────────────────────────────────────────┐
│              UI Layer                    │
│    (Jetpack Compose + ViewModels)       │
├─────────────────────────────────────────┤
│           Domain Layer                   │
│      (Use Cases / Business Logic)        │
├─────────────────────────────────────────┤
│            Data Layer                    │
│  (Repositories + Data Sources + SDK)     │
└─────────────────────────────────────────┘
```

### Key Components

- **GlyphManager** — Wrapper around Nothing Glyph SDK
- **GlyphFeatureCoordinator** — Mutex-based LED access control
- **SettingsRepository** — SharedPreferences-backed settings
- **Foreground Services** — Long-running glyph operations

---

## ⚙️ Configuration

### Permissions Required

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.NFC" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

### Build Configuration

```kotlin
android {
    compileSdk = 35
    minSdk = 34
    targetSdk = 35
    
    // Nothing Glyph SDK
    implementation(files("libs/KetchumSDK_Community_20250319.jar"))
    
    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.48")
    
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.x.x"))
}
```

---

## 🧪 Testing

Run unit tests:
```bash
./gradlew test
```

Run instrumented tests:
```bash
./gradlew connectedAndroidTest
```

---

## 📄 License

This project is **NOT OFFICIAL** and is not affiliated with Nothing Technology Limited.

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📞 Support

- **Documentation**: Browse the [docs folder](docs/)
- **Issues**: Report bugs on [GitHub Issues](https://github.com/hardWorker254/Glyph-Sharge-fork/issues)
- **Discussions**: Join community discussions

---

## 🙏 Acknowledgments

- Nothing Technology Limited for the Glyph Developer Kit
- AndroidX and Jetpack Compose teams
- Hilt and Kotlin Coroutines communities

---

**Built with ❤️ for the Nothing Community**
