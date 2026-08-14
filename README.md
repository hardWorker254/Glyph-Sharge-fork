# ⚡ Glyph Sharge

> 🔐 **Note**: Glyph Sharge uses an **official Nothing API key**, so **debug mode is not required** for any glyph functionality.

---

**Power. Protect. Personalize. All through light.**

[![Download](https://img.shields.io/github/v/release/hardWorker254/Glyph-Sharge-fork?label=Download&color=D71921)](https://github.com/hardWorker254/Glyph-Sharge-fork/releases/tag/Main)
[![Platform](https://img.shields.io/badge/Platform-Nothing%20Phone-blue)](https://nothing.tech/)
[![Android](https://img.shields.io/badge/Android-14+-green)](https://www.android.com/)
[![License](https://img.shields.io/badge/License-Unofficial-orange)]()

📖 **[Documentation](docs/README.md)** | 🚀 **[Getting Started](docs/GETTING_STARTED.md)** | ✨ **[Features](docs/FEATURES.md)** | 🏗 **[Architecture](docs/ARCHITECTURE.md)**

---

## 🔌 What is Glyph Sharge?

**Glyph Sharge** is your ultimate control center for the Nothing Glyph Interface. With a modern Material You design and deep system integration, it lets you manage power, enhance security, and personalize your experience — all through your phone's glowing glyphs.

Whether you're checking charge levels or activating security features, Glyph Sharge turns your Nothing Phone into a functional and expressive light interface.

> ⚠️ **This is NOT AN OFFICIAL REPO** — This project is not affiliated with Nothing Technology Limited.

---

## ✨ Key Features

| Feature | Description |
|---------|-------------|
| 🔓 **Pulse Lock** | Glyph animation when unlocking your phone |
| ⚡ **Power Peek** | Check battery by shaking your phone |
| 🔋 **Low Battery Alert** | Visual warning when battery is low |
| 🌙 **Screen Off Glyph** | Animation when turning off screen |
| 📡 **NFC Glyph** | Feedback for NFC transactions |
| 🔌 **Charging Animation** | Beautiful animations while charging |
| 🤫 **Quiet Hours** | Scheduled silence periods |

➡️ **[See all features →](docs/FEATURES.md)**

---

## 📱 Supported Devices

- **Nothing Phone (1)** — Device 20111
- **Nothing Phone (2)** — Device 22111  
- **Nothing Phone (2a)** — Device 23111 / 23113
- **Nothing Phone (3a)** — Device 24111

---

## 🚀 Quick Start

### Installation

1. Download the latest APK from [Releases](https://github.com/hardWorker254/Glyph-Sharge-fork/releases)
2. Enable "Install from Unknown Sources" in settings
3. Install and open the app
4. Grant required permissions
5. Enable your first feature!

### Build from Source

```bash
# Clone repository
git clone https://github.com/hardWorker254/Glyph-Sharge-fork.git
cd Glyph-Sharge-fork

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

➡️ **[Full setup guide →](docs/GETTING_STARTED.md)**

---

## 📚 Documentation

| Document | Description |
|----------|-------------|
| [📖 Documentation Home](docs/README.md) | Overview and navigation |
| [🚀 Getting Started](docs/GETTING_STARTED.md) | Installation & setup guide |
| [✨ Features](docs/FEATURES.md) | Complete feature reference |
| [🏗 Architecture](docs/ARCHITECTURE.md) | Technical architecture |
| [⚙️ Services](docs/SERVICES.md) | Background services details |
| [🎨 UI Components](UI_COMPONENTS_DOCUMENTATION.md) | UI component reference |

---

## 🛠 Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material Design 3)
- **DI:** Hilt
- **Async:** Kotlin Coroutines & Flow
- **Database:** Room
- **SDK:** Nothing Ketchum Glyph SDK
- **Min SDK:** Android 14 (API 34)
- **Target SDK:** Android 15 (API 35)

---

## 📦 Project Structure

```
app/src/main/java/com/bleelblep/glyphsharge/
├── di/           # Dependency Injection
├── data/         # Data layer (Repository, Room, SharedPreferences)
├── glyph/        # Glyph interface management
├── services/     # Foreground services
├── receiver/     # Broadcast receivers
├── ui/           # UI layer (Compose)
│   ├── screens/  # App screens
│   ├── components/ # Reusable components
│   ├── theme/    # Theming system
│   └── viewmodel/ # ViewModels
└── utils/        # Utilities
```

➡️ **[See full architecture →](docs/ARCHITECTURE.md)**

---

## 🔧 Troubleshooting

### Glyphs Not Working?
1. Verify you have a Nothing Phone device
2. Toggle Glyph Service in settings
3. Restart the app
4. Reboot your device

### Services Not Starting?
1. Disable battery optimization for Glyph Sharge
2. Allow background activity
3. Check notification permissions

➡️ **[More troubleshooting tips →](docs/GETTING_STARTED.md#troubleshooting)**

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

- **📖 Documentation:** Browse the [docs folder](docs/)
- **🐛 Issues:** Report bugs on [GitHub Issues](https://github.com/hardWorker254/Glyph-Sharge-fork/issues)
- **💬 Discussions:** Join community discussions

---

## 📄 License

This project is **NOT OFFICIAL** and is not affiliated with Nothing Technology Limited.

All product names, logos, and brands are property of their respective owners.

---

## 🙏 Acknowledgments

- **Nothing Technology Limited** for the Glyph Developer Kit
- **AndroidX and Jetpack Compose** teams
- **Hilt and Kotlin Coroutines** communities

---

<p align="center">
  <strong>Built with ❤️ for the Nothing Community</strong>
</p>
