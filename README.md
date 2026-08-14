# ⚡ Glyph Sharge

> 🔐 **Note**: Glyph Sharge uses an **official Nothing API key**, so **debug mode is not required** for any glyph functionality.

---

**Power. Protect. Personalize. All through light.**

[![Download](https://img.shields.io/badge/Download-Latest-red?style=for-the-badge)](https://github.com/hardWorker254/Glyph-Sharge-fork/releases/tag/Main)
[![Version](https://img.shields.io/badge/Version-1.0.30-blue?style=for-the-badge)](https://github.com/hardWorker254/Glyph-Sharge-fork/releases)
[![Platform](https://img.shields.io/badge/Platform-Android%2014+-green?style=for-the-badge)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.10-purple?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

---

## 📖 Table of Contents

- [About](#-what-is-glyph-sharge)
- [Features](#-features)
- [Supported Devices](#-supported-devices)
- [Quick Start](#-quick-start)
- [Documentation](#-documentation)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Contributing](#-contributing)
- [License](#-license)

---

## 🔌 What is Glyph Sharge?

Glyph Sharge is your ultimate control center for the Nothing Glyph Interface. With a modern Material You design and deep system integration, it lets you manage power, enhance security, and personalize your experience — all through your phone's glowing glyphs.

Whether you're checking charge levels or activating security features, Glyph Sharge turns your Nothing Phone into a functional and expressive light interface.

**This is NOT AN OFFICIAL REPO.**

---

## ✨ Features

### 🔌 Power Management
- Real-time battery monitoring with glyph animations
- Charging progress visualization through light patterns
- Low battery alerts via glyph notifications
- Power Peek feature — shake to check battery level

### 🔒 Security Features
- NFC Glyph activation for smart tags
- Pulse Lock — heart rate verification for device security
- Screen-off glyph notifications

### 🎨 Personalization
- 6 unique theme styles (Classic, Y2K, Neon, AMOLED, Pastel, Expressive)
- Custom font support with official Nothing fonts (NType Headline, NDot 57 Caps)
- Scalable text sizes for accessibility
- Material You dynamic theming

### ⚙️ Advanced Controls
- Quiet Hours mode for scheduled silence
- Custom glyph patterns and animations
- Boot-on-start service persistence
- Comprehensive logging system

---

## 📱 Supported Devices

| Device | Model Number | SDK Support |
|--------|--------------|-------------|
| Nothing Phone (1) | 20111 | ✅ Full |
| Nothing Phone (2) | 22111 | ✅ Full |
| Nothing Phone (2a) | 23111 / 23113 | ✅ Full |
| Nothing Phone (3a) | 24111 | ✅ Full |

---

## 🚀 Quick Start

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK** version 17 or higher
- **Android SDK** API 34+ (Android 14+)
- **Nothing Phone** with Glyph Interface support

### Installation

1. **Clone the repository:**
```bash
git clone https://github.com/hardWorker254/Glyph-Sharge-fork.git
cd Glyph-Sharge-fork
```

2. **Open in Android Studio**

3. **Sync Gradle dependencies**

4. **Build and install:**
```bash
./gradlew assembleDebug
```

### Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test

# Clean project
./gradlew clean

# Install on device
./gradlew installDebug
```

> ⚠️ **Important**: The app uses an **official Nothing API key**, so debug mode is NOT required for glyph functionality.

---

## 📁 Project Structure

```
app/
├── src/main/
│   ├── java/com/bleelblep/glyphsharge/
│   │   ├── di/                          # Dependency Injection (Hilt)
│   │   │   ├── AppModule.kt
│   │   │   └── GlyphComponent.kt
│   │   │
│   │   ├── glyph/                       # Glyph Interface Management
│   │   │   ├── GlyphManager.kt
│   │   │   ├── GlyphAnimationManager.kt
│   │   │   └── GlyphFeatureCoordinator.kt
│   │   │
│   │   ├── services/                    # Background Services
│   │   │   ├── GlyphForegroundService.kt
│   │   │   ├── ChargingAnimationService.kt
│   │   │   ├── NfcGlyphService.kt
│   │   │   ├── LowBatteryAlertService.kt
│   │   │   ├── PowerPeekService.kt
│   │   │   ├── PulseLockService.kt
│   │   │   ├── QuietHoursService.kt
│   │   │   └── ScreenOffGlyphService.kt
│   │   │
│   │   ├── ui/                          # UI Components
│   │   │   ├── components/              # Reusable Composables
│   │   │   ├── screens/                 # App Screens
│   │   │   ├── theme/                   # Themes & Styling
│   │   │   └── utils/                   # UI Utilities
│   │   │
│   │   ├── data/                        # Data Layer
│   │   │   ├── SettingsRepository.kt
│   │   │   └── local/                   # Room Database
│   │   │
│   │   ├── receiver/                    # Broadcast Receivers
│   │   │   └── BootCompletedReceiver.kt
│   │   │
│   │   ├── utils/                       # Utilities
│   │   │   ├── LoggingManager.kt
│   │   │   └── WatermarkHelper.kt
│   │   │
│   │   ├── GlyphZenApplication.kt       # Application Class
│   │   └── MainActivity.kt              # Main Activity
│   │
│   ├── res/                             # Android Resources
│   └── AndroidManifest.xml
│
└── build.gradle                         # Build Configuration
```

---

## 🏗 Architecture

Glyph Sharge follows modern Android development best practices:

- **MVVM (Model-View-ViewModel)** — Separation of concerns
- **Dependency Injection** — Hilt for scalable DI
- **Repository Pattern** — Abstracted data access
- **Single Activity Architecture** — Jetpack Compose Navigation
- **Unidirectional Data Flow** — Predictable state management

---

## 🎨 UI Components

### Theme System

The app features **6 complete theme configurations**:

```kotlin
enum class AppThemeStyle {
    CLASSIC,      // Clean, standard Material 3
    Y2K,          // Chrome, cyber, futuristic
    NEON,         // High contrast electric colors
    AMOLED,       // True black minimalist
    PASTEL,       // Soft, dreamy colors
    EXPRESSIVE    // Bold Material 3 Expressive
}
```

### Font System

Official Nothing fonts with dynamic scaling:
- **NType Headline** — Headlines and titles
- **NDot 57 Caps** — Accents and special text
- **System** — Default system font

### Core Components

| Component | Description |
|-----------|-------------|
| `StandardCard` | Base card with animations and haptics |
| `SimpleCard` | Minimalist card variant |
| `IconCard` | Icon-focused card |
| `ActionCard` | Call-to-action card |
| `ContentCard` | Custom content container |
| `WavyProgressIndicator` | Animated sine wave progress |
| `TransparentTopAppBar` | Scroll-aware app bar |
| `WatermarkBox` | Diagonal watermark overlay |

---

## 🔧 Services

| Service | Purpose |
|---------|---------|
| `GlyphForegroundService` | Maintains glyph session in background |
| `ChargingAnimationService` | Displays charging animations |
| `NfcGlyphService` | NFC tag integration |
| `LowBatteryAlertService` | Battery level monitoring |
| `PowerPeekService` | Shake-to-check battery feature |
| `PulseLockService` | Heart rate security lock |
| `ScreenOffGlyphService` | Notifications with screen off |
| `QuietHoursService` | Scheduled silent mode |

---

## 🛠 Tech Stack

### Core
- **Language**: Kotlin 1.9.10
- **UI Framework**: Jetpack Compose + Material Design 3
- **Min SDK**: Android 14 (API 34)
- **Target SDK**: Android 14 (API 34)

### Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Android Gradle Plugin | 8.13.2 | Build system |
| Compose BOM | 2024.02.00 | Compose bill of materials |
| Material 3 | 1.2.0 | Design system |
| Hilt | 2.50 | Dependency injection |
| KSP | 1.9.10-1.0.13 | Symbol processing |
| Navigation Compose | 2.7.7 | In-app navigation |
| Room | 2.6.1 | Local database |
| Lottie Compose | 6.3.0 | Animations |
| Material Components | 1.11.0 | Additional widgets |

### SDK
- **Nothing Ketchum SDK** (`KetchumSDK_Community_20250319.jar`) — Official Glyph Interface SDK

---

## 📚 Documentation

Comprehensive documentation is available in the [`docs/`](docs/) folder:

| Document | Description | Language |
|----------|-------------|----------|
| [🏗 Architecture](docs/ARCHITECTURE.md) | Architectural decisions and patterns | 🇷🇺 Russian |
| [🚀 Getting Started](docs/GETTING_STARTED.md) | Developer quick start guide | 🇷🇺 Russian |
| [🇷🇺 Main Documentation](docs/RU_MAIN.md) | Complete user & developer guide | 🇷🇺 Russian |
| [🎨 UI Components](UI_COMPONENTS_DOCUMENTATION.md) | Detailed UI components reference | 🇬🇧 English |

### Documentation Overview

- **Architecture** — MVVM pattern, dependency injection, repository pattern, and service architecture
- **Getting Started** — Environment setup, build commands, debugging tips, and common issues
- **Main Documentation** — Full feature documentation, API reference, and usage examples
- **UI Components** — Theme system, card components, custom animations, and styling guide

---

## 🤝 Contributing

Contributions are welcome! Here's how you can help:

### How to Contribute

1. **Fork** the repository
2. **Create a feature branch** (`git checkout -b feature/amazing-feature`)
3. **Commit your changes** (`git commit -m 'Add amazing feature'`)
4. **Push to the branch** (`git push origin feature/amazing-feature`)
5. **Open a Pull Request**

### Development Guidelines

- Follow Kotlin coding conventions
- Write meaningful commit messages
- Test your changes thoroughly on Nothing Phone devices
- Update documentation if needed
- Ensure UI changes work with all 6 theme styles

### Areas for Contribution

- 🐛 Bug fixes
- ✨ New glyph features and animations
- 🎨 UI/UX improvements
- 📝 Documentation translations
- 🧪 Unit and integration tests
- ⚡ Performance optimizations

---

## 📄 License

This is **NOT** an official Nothing Technology Limited product.

The app uses an official Nothing API key for Glyph Interface functionality.

---

## 🔗 Links & Resources

| Resource | Description |
|----------|-------------|
| [📦 Releases](https://github.com/hardWorker254/Glyph-Sharge-fork/releases) | Download latest versions and release notes |
| [🐛 Issues](https://github.com/hardWorker254/Glyph-Sharge-fork/issues) | Report bugs or request features |
| [💬 Discussions](https://github.com/hardWorker254/Glyph-Sharge-fork/discussions) | Community discussions and Q&A |
| [📖 Nothing Developer Program](https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit) | Official Nothing Glyph SDK documentation |

---

## 📞 Support

For questions, issues, or suggestions:

1. Check the [documentation](docs/) first
2. Search existing [issues](https://github.com/hardWorker254/Glyph-Sharge-fork/issues)
3. Review [release notes](https://github.com/hardWorker254/Glyph-Sharge-fork/releases) for updates
4. Open a new issue if your problem isn't listed

---

*Documentation last updated for version 1.0.30*

Made with ⚡ for Nothing Phone community
