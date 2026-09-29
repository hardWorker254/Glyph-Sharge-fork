# ⚡ Glyph Sharge

> 🔐 **Note**: Glyph Sharge uses an **official Nothing API key**, so **debug mode is not required** for any glyph functionality.

---

**Power. Protect. Personalize. All through light.**

[![Download](https://img.shields.io/badge/Download-Latest-red?style=for-the-badge)](https://github.com/hardWorker254/Glyph-Sharge-fork/releases/)
[![Version](https://img.shields.io/badge/Version-1.0.31-blue?style=for-the-badge)](https://github.com/hardWorker254/Glyph-Sharge-fork/releases)
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
- **VPN Connected** — your chosen animation plays once when a VPN connection is established

### 🎵 Personalization
- 6 unique theme styles (Classic, Y2K, Neon, AMOLED, Pastel, Expressive)
- **Music Visualizer** — the Glyph strip reacts to whatever is playing, in six
  styles: bars, wave, mirror, beat, matrix, vortex
- Works on every supported Nothing model, from Phone (1)'s 4 C-strip segments
  up to Phone (3a)'s 20
- Your own visuals in Lua — `glyph.target = "music"` makes a studio script show
  up only in the visualiser, and `glyph.audio.bass` / `bands()` feed it the spectrum
- Yields the Glyph to charging, lock and low-battery features instead of holding it
- Custom font support with official Nothing fonts (NType Headline, NDot 57 Caps)
- Scalable text sizes for accessibility
- Material You dynamic theming
- **Custom animations written in Lua** — a built-in animation studio (Settings → Custom Animations) with a sandboxed editor, on-screen and on-glyph preview, and `.glyphlua` import/export

### ⚙️ Advanced Controls
- Quiet Hours mode for scheduled silence
- Custom glyph patterns and animations
- Boot-on-start service persistence
- Comprehensive logging system
- Your own scripts appear in the animation picker of Pulse Lock, Low Battery, NFC and Screen Off

---

## 📱 Supported Devices

| Device | Model Number | SDK Support |
|--------|--------------|-------------|
| Nothing Phone (1) | 20111 | No testers |
| Nothing Phone (2) | 22111 | No testers |
| Nothing Phone (2a) | 23111 / 23113 | No testers |
| Nothing Phone (3a) | 24111 | ✅ Full |

---

## 🚀 Quick Start

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK** version 17 or higher
- **Android SDK** API 36 for compiling, API 34+ (Android 14+) minimum
- **Nothing Phone** with Glyph Interface support

### Installation

1. **Clone the repository:**
```bash
git clone https://github.com/hardWorker254/Glyph-Sharge-fork.git
cd Glyph-Sharge-fork
```

2. **Open in Android Studio**

3. **Sync Gradle dependencies**

> ⚠️ **Important**: The app uses an **official Nothing API key**, so debug mode is NOT required for glyph functionality.

---

## 📁 Project Structure

```
app/
├── src/main/
│   ├── java/com/bleelblep/glyphsharge/
│   │   ├── di/                          # Dependency Injection (Hilt)
│   │   │   └── AppModule.kt             # Provides the one @GlyphPrefs SharedPreferences
│   │   │
│   │   ├── glyph/                       # Glyph Interface Management
│   │   │   ├── GlyphManager.kt          # Nothing SDK session
│   │   │   ├── GlyphAnimationManager.kt # Public façade: the animation entry points
│   │   │   ├── GlyphFeatureCoordinator.kt # Mutex + enum GlyphFeature — sole strip arbitrator
│   │   │   ├── AnimationCatalog.kt      # The animation ids the settings store
│   │   │   ├── RunTrace.kt
│   │   │   ├── device/                  # Per-model LED layout and timings
│   │   │   ├── engine/                  # Frame drawing, run flag, error handling
│   │   │   ├── animations/              # Built-in animations + the shared runner
│   │   │   │   ├── AnimationRunner.kt   #   Guards and the blank-strip lifecycle
│   │   │   │   ├── BuiltInAnimations.kt
│   │   │   │   ├── AudioAnimations.kt
│   │   │   │   ├── ParticleAnimations.kt
│   │   │   │   └── SequenceAnimations.kt
│   │   │   ├── audio/                   # Music visualiser: capture, FFT, synthetic track
│   │   │   │   ├── MusicVisualisation.kt
│   │   │   │   ├── SyntheticTrack.kt
│   │   │   │   └── ...
│   │   │   ├── script/                  # User-written Lua animations (LuaJ sandbox)
│   │   │   │   ├── LuaScriptEngine.kt    #   Sandbox, watchdog, validate()
│   │   │   │   ├── GlyphLuaApi.kt        #   The `glyph` table — the whole script language
│   │   │   │   ├── ScriptSession.kt      #   Per-run state and interruptible waits
│   │   │   │   ├── ScriptRunner.kt       #   The only bridge from the VM to the LEDs
│   │   │   │   ├── ScriptPlayback.kt     #   Hosting a script animation on the strip
│   │   │   │   ├── ScriptTarget.kt       #   Which pickers may offer a script
│   │   │   │   ├── ScriptFileFormat.kt   #   The .glyphlua container
│   │   │   │   └── ScriptAnimation.kt    #   Model, custom:<12 hex> ids
│   │   │   └── battery/                 # Charging / Power Peek bar
│   │   │
│   │   ├── services/                    # Background Services
│   │   │   ├── FeatureSpec.kt           # Feature → service → preference registry
│   │   │   ├── FeatureServiceController.kt
│   │   │   ├── GlyphForegroundService.kt
│   │   │   ├── ChargingAnimationService.kt
│   │   │   ├── NfcGlyphService.kt
│   │   │   ├── LowBatteryAlertService.kt
│   │   │   ├── PowerPeekService.kt
│   │   │   ├── PulseLockService.kt
│   │   │   ├── QuietHoursService.kt
│   │   │   ├── ScreenOffGlyphService.kt
│   │   │   ├── VpnConnectedService.kt
│   │   │   └── MusicVisualizerService.kt
│   │   │
│   │   ├── ui/                          # UI Components
│   │   │   ├── components/              # Reusable Composables
│   │   │   │   ├── FeatureCards.kt      # One card per GlyphFeature
│   │   │   │   ├── GlyphAnimations.kt   # Animation catalogue + rememberAnimationOptions
│   │   │   │   ├── GlyphDependencies.kt # rememberGlyphAnimationManager()
│   │   │   │   ├── CommonDialogComponents.kt
│   │   │   │   ├── cards/               # ContentCard, FeatureCard, SquareFeatureCard
│   │   │   │   ├── controls/            # Morphing toggles
│   │   │   │   ├── dialogs/             # Shared dialog scaffold and confirmation flow
│   │   │   │   └── layout/              # Settings scaffold, section header, feature grid
│   │   │   ├── screens/                 # App Screens
│   │   │   │   ├── home/                # Home
│   │   │   │   └── animations/          # Animation studio: list, editor, preview
│   │   │   ├── navigation/              # Routes and the NavHost
│   │   │   ├── state/                   # FeatureUiState, HomeUiState
│   │   │   ├── viewmodel/               # Home, animation studio, custom animations
│   │   │   ├── theme/                   # Themes, typography, CompositionLocals
│   │   │   └── utils/                   # HapticUtils
│   │   │
│   │   ├── data/                        # Settings store: slices + a thin facade
│   │   │   ├── ThemeSettings.kt         #   Each slice owns its own keys
│   │   │   ├── FontSettings.kt
│   │   │   ├── GlyphServiceSettings.kt
│   │   │   ├── FeatureSettings.kt
│   │   │   ├── QuietHoursSettings.kt
│   │   │   ├── LanguageSettings.kt
│   │   │   ├── UserPresenceSettings.kt
│   │   │   ├── SettingsMigrations.kt
│   │   │   ├── SettingsDiagnostics.kt
│   │   │   ├── SettingsPrefs.kt         #   Typed reified read/write helpers
│   │   │   ├── SettingsRepository.kt    # Facade forwarding to the slices
│   │   │   └── CustomAnimationRepository.kt  # User scripts: files + index, import/export
│   │   │
│   │   ├── receiver/                    # Broadcast Receivers
│   │   │   └── BootCompletedReceiver.kt
│   │   │
│   │   ├── utils/                       # Utilities
│   │   │   └── LoggingManager.kt
│   │   │
│   │   ├── GlyphZenApplication.kt       # Application Class
│   │   ├── MainActivity.kt              # Main Activity
│   │   └── CustomAnimationsActivity.kt  # Animation Studio (separate Activity)
│   │
│   ├── res/                             # Android Resources
│   └── AndroidManifest.xml
│
├── src/test/java/com/bleelblep/glyphsharge/
│   └── glyph/                           # 118 unit tests: audio, device, script
│
├── build.gradle.kts                     # Build Configuration
└── proguard-rules.pro

gradle/libs.versions.toml                 # The single source of every plugin and library version
```

---

## 🏗 Architecture

Glyph Sharge follows modern Android development best practices:

- **MVVM (Model-View-ViewModel)** — Separation of concerns
- **Dependency Injection** — Hilt for scalable DI. `di/AppModule.kt` provides the one settings `SharedPreferences` under the `@GlyphPrefs` qualifier; the UI reaches the rest of the graph through `@HiltViewModel` and `hiltViewModel()`
- **Repository Pattern** — `data/` keeps the settings in `SharedPreferences`, split into one slice per concern (`ThemeSettings`, `FontSettings`, `FeatureSettings`, …); `SettingsRepository` is a thin facade that forwards to them
- **Single Activity Architecture** — Jetpack Compose Navigation (plus one dedicated Activity for the animation studio, which owns its own file pickers and back stack)
- **Unidirectional Data Flow** — Predictable state management
- **One strip, one owner** — `GlyphFeatureCoordinator` holds a `Mutex` behind the single `GlyphFeature` enum, so only one feature drives the LEDs at a time; services take the strip through `withStrip(...)` and bound a run through `GlyphAnimationManager.runCapped(...)`
- **Features as data** — `services/FeatureSpec.kt` maps every feature to its service, its stop action and its preference in one record, so `FeatureServiceController` never hard-codes which service a feature means
- **Script runtime** — user-written Lua animations run on [LuaJ](https://github.com/luaj/luaj) 3.0.1 (`org.luaj:luaj-jse`, a pure-JVM Lua 5.2) in a sandbox stripped of `io`, `os`, `luajava`, `load`/`dofile`/`require`, `coroutine` and `debug`, with a watchdog that enforces the feature's Duration setting and a 200,000,000-instruction ceiling

---

## 🎨 Tech Stack

Every version below lives in `gradle/libs.versions.toml` — the single source of truth for plugins and libraries. The build is Kotlin DSL (`.kts`) in both the root and `app/`.

| Layer | Choice | Version |
|-------|--------|---------|
| Language | Kotlin | 1.9.10 |
| Build | Android Gradle Plugin | 8.13.2 |
| Annotation processing | KSP (Hilt compiler) | 1.9.10-1.0.13 |
| Dependency injection | Hilt | 2.50 |
| UI | Jetpack Compose, Material 3 | BOM 2024.02.00, material3 1.2.0 |
| Compose compiler | `composeOptions.kotlinCompilerExtensionVersion` | 1.5.3 |
| Navigation | Navigation Compose | 2.7.7 |
| Scripting | LuaJ (`org.luaj:luaj-jse`) | 3.0.1 |
| Tests | JUnit 4, Robolectric | 4.13.2, 4.11.1 |

- **JDK 17**, `compileSdk 36`, `minSdk 34`, `targetSdk 34`
- **No `kapt`** — Hilt's compiler runs through KSP, so the module runs one annotation-processing pass per compile
- Release builds ship with `isMinifyEnabled = false`

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

| Component | File | Description |
|-----------|------|-------------|
| `FeatureCard`, `SquareFeatureCard`, `WideFeatureCardWithToggle` | `ui/components/cards/ContentCards.kt` | Feature presentation, square and wide-with-toggle variants |
| `GlyphControlCard` | `ui/components/cards/ContentCards.kt` | The master Glyph on/off card |
| `ContentCard` | `ui/components/cards/ContentCards.kt` | Custom content container |
| `PowerPeekCard`, `PulseLockCard`, `LowBatteryAlertCard`, `ScreenOffCard`, `NfcGlyphCard`, `ChargingAnimationCard`, `MusicVisualizerCard`, `VpnConnectedCard` | `ui/components/FeatureCards.kt`, `ui/components/VpnConnected.kt` | One card per `GlyphFeature` |
| `SettingsScaffold`, `DraggableSettingsCard` | `ui/components/layout/SettingsScaffold.kt`, `.../DraggableSettingsCard.kt` | Settings screen frame and swipe-to-navigate card |
| `HomeSectionHeader`, `FeatureGrid` | `ui/components/layout/SectionLayout.kt` | Section header and the feature grid |
| `MorphingToggleButton`, `ThreeStateFontMorphingButton` | `ui/components/controls/ToggleButtons.kt` | Morphing on/off toggle |
| `ThreeStateFontToggle` | `ui/components/FontSettingsComponents.kt` | HEADLINE / NDOT / SYSTEM picker |
| `FeatureDialogScaffold`, `FeatureConfirmationFlow` | `ui/components/dialogs/` | Shared dialog chrome for every feature |

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
| `MusicVisualizerService` | Spectrum visualisation of whatever is playing |
| `VpnConnectedService` | Plays the chosen animation when a VPN connects |

---

## 📚 Documentation

Comprehensive documentation is available in the [`docs/`](docs/) folder:

### 📘 Full Documentation (recommended starting point)

| Document | Description | Language |
|----------|-------------|----------|
| **[🇷🇺 Полная документация](docs/DOCUMENTATION_RU.md)** | Полное техническое руководство: архитектура, все слои, сервисы, **Quickstart по добавлению нового сервиса**, **справочник по своим анимациям на Lua**, подводные камни | 🇷🇺 Russian |
| **[🇬🇧 Full Documentation](docs/DOCUMENTATION_EN.md)** | Complete technical guide: architecture, all layers, services, **service Quickstart**, **custom Lua animations reference**, gotchas | 🇬🇧 English |

### 📄 Supplementary Documents

| Document | Description | Language |
|----------|-------------|----------|
| [🏗 Architecture](docs/ARCHITECTURE.md) | Architectural decisions and patterns | 🇷🇺 Russian |
| [🚀 Getting Started](docs/GETTING_STARTED.md) | Developer quick start guide | 🇷🇺 Russian |
| [🇷🇺 Main Documentation](docs/RU_MAIN.md) | Complete user & developer guide | 🇷🇺 Russian |
| [🎨 UI Components](UI_COMPONENTS_DOCUMENTATION.md) | Detailed UI components reference | 🇬🇧 English |

### Documentation Overview

- **Full Documentation** — the primary reference. Covers the build setup, the Glyph / Data / Services / UI layers, the service lifecycle contract, a step-by-step **"Adding a new service"** walkthrough for all 13 touchpoints, the **custom Lua animations** section (editor, full `glyph` API reference, sandbox and watchdog, `.glyphlua` format), and a catalogue of known issues
- **Architecture** — MVVM pattern, dependency injection, repository pattern, the Lua script engine, and service architecture
- **Getting Started** — Environment setup, build commands, debugging tips, and common issues
- **Main Documentation** — Full feature documentation, API reference, and usage examples
- **UI Components** — Theme system, card components, custom animations, the animation studio screens, and a styling guide

> 💡 **Writing a custom animation?** Start with the Lua section:
> [RU](docs/DOCUMENTATION_RU.md#13-пользовательские-анимации-lua) ·
> [EN](docs/DOCUMENTATION_EN.md#13-custom-animations-lua)

> 💡 **Adding a new feature?** Start with the Quickstart section:
> [RU](docs/DOCUMENTATION_RU.md#7-добавление-нового-сервиса-quickstart) ·
> [EN](docs/DOCUMENTATION_EN.md#7-adding-a-new-service-quickstart)

---

## 📄 License

This is **NOT** an official Nothing Technology Limited product.

The app uses an official Nothing API key for Glyph Interface functionality.

---

*Documentation last updated for version 1.0.31*

Made with ⚡ for Nothing Phone community
