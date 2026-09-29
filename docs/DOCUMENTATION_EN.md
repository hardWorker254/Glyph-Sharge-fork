# Glyph Sharge (Glyph Zen) — Project Documentation

> Complete technical guide for developers. Current for version **1.0.31** (versionCode 1031).
> Russian version: [DOCUMENTATION_RU.md](DOCUMENTATION_RU.md)

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Requirements & Getting Started](#2-requirements--getting-started)
3. [Architecture](#3-architecture)
4. [Glyph Layer](#4-glyph-layer)
5. [Data Layer](#5-data-layer)
6. [Services](#6-services)
7. [**Adding a New Service (Quickstart)**](#7-adding-a-new-service-quickstart)
8. [UI Layer](#8-ui-layer)
9. [Navigation](#9-navigation)
10. [Music visualiser](#10-music-visualiser)
11. [Resources & Localization](#11-resources--localization)
12. [Build & Configuration](#12-build--configuration)
13. [Known Issues & Gotchas](#13-known-issues--gotchas)
14. [**Custom Animations (Lua)**](#14-custom-animations-lua)

---

## 1. Project Overview

**Glyph Sharge** is an app for controlling the **Nothing Glyph** light interface on Nothing phones.
It gives full control over the LEDs: charge indication, notifications, security, personalization.

> ⚠️ This is **not** an official Nothing Technology Limited product.

### Key Features

| Area | Capabilities |
|------|--------------|
| 🔌 Power | Charging animations, battery level display, low-battery alerts, Power Peek (shake to check battery) |
| 🔒 Security | Pulse Lock (animation on unlock), Screen Off (animation on lock) |
| 📡 Integration | NFC glyphs on payment events, a glyph animation when a VPN connects |
| 🎨 Personalization | 6 theme styles, Nothing fonts (NType Headline, NDot 55 Caps), scalable text sizes, your own glyph animations written in Lua |
| ⚙️ System | Quiet hours, boot persistence, logging |

### Supported Devices

| Device | Model | Status |
|--------|-------|--------|
| Nothing Phone (1) | 20111 | No testers |
| Nothing Phone (2) | 22111 | No testers |
| Nothing Phone (2a) | 23111 / 23113 | No testers |
| Nothing Phone (3a) | 24111 | ✅ Full support |

Device detection happens in [DeviceType](../app/src/main/java/com/bleelblep/glyphsharge/glyph/device/DeviceType.kt)
via `Common.is20111()` and friends. On an unsupported device the app still
launches, but the glyphs will not work.

### Key Project Facts

| Parameter | Value |
|-----------|-------|
| Package / namespace | `com.bleelblep.glyphsharge` |
| Application ID | `com.bleelblep.glyphsharge` |
| minSdk / targetSdk | **34** / 34 (Android 14+) |
| compileSdk | 36 |
| JVM target | 17 |
| Language | Kotlin 1.9.10, Jetpack Compose (Material 3) |
| DI | Hilt 2.50 (KSP) |
| Settings storage | SharedPreferences (file `glyphzen_settings`) |
| Animation scripts | LuaJ 3.0.1 (`org.luaj:luaj-jse`) — a pure-JVM Lua 5.2 |
| Glyph SDK | `app/libs/KetchumSDK_Community_20250319.jar` (official Nothing key) |

> [!IMPORTANT]
> There is **no debug mode for glyphs** — the app uses the official Nothing API key
> declared in `AndroidManifest.xml` as `<meta-data android:name="NothingKey" .../>`.
> There is no need to substitute `"test"`.

---

## 2. Requirements & Getting Started

### Requirements

- **JDK 17** (mandatory — the active build script sets `sourceCompatibility 17`)
- **Android Studio** Hedgehog (2023.1.1) or newer
- **Android SDK** 36 (compile), 34 (min)
- A physical Nothing phone to verify glyphs

### Clone & Build

```bash
git clone https://github.com/hardWorker254/Glyph-Sharge-fork.git
cd Glyph-Sharge-fork
```

Open in Android Studio, wait for the Gradle sync, then:

```bash
# Debug build and install
./gradlew app:assembleDebug
./gradlew app:installDebug

# Release build
./gradlew app:assembleRelease
```

> [!NOTE]
> **Every build script is Kotlin DSL** — `build.gradle.kts`, `settings.gradle.kts` and
> `app/build.gradle.kts`. There is no Groovy script anywhere in the project, and every
> plugin and library version comes from `gradle/libs.versions.toml`, which is the single
> source of truth.

### Testing on Device

An emulator has no glyphs. Test on a real Nothing Phone.
The app still launches without one — `isNothingPhone()` returns `false` and the feature
cards stay inactive, so UI work can be done on any device.

---

## 3. Architecture

The project follows **MVVM + Unidirectional Data Flow + Repository + Hilt DI**.

```mermaid
graph TD
    UI["UI Layer<br/>Compose Screens, Cards, Dialogs"]
    STUDIO["CustomAnimationsActivity<br/>Lua animation studio"]
    VM["ViewModel<br/>HomeViewModel"]
    SVC["Services Layer<br/>9 ForegroundServices"]
    CTRL["FeatureServiceController<br/>feature to service routing"]
    SPEC["FeatureSpec registry<br/>feature to service and preference"]
    REPO["SettingsRepository<br/>SharedPreferences"]
    CREPO["CustomAnimationRepository<br/>.glyphlua files + index"]
    COORD["GlyphFeatureCoordinator<br/>mutual exclusion"]
    GM["GlyphManager<br/>Ketchum SDK session"]
    ANIM["GlyphAnimationManager<br/>animation entry points"]
    SRUN["ScriptRunner<br/>VM to hardware bridge"]
    LUA["LuaScriptEngine<br/>sandbox and watchdog"]
    REND["GlyphRenderer<br/>frame drawing engine"]
    PROF["DeviceProfileFactory<br/>per-model LED layout"]
    BOOT["BootCompletedReceiver"]

    UI --> VM
    UI --> REPO
    UI --> STUDIO
    STUDIO --> CREPO
    STUDIO --> SRUN
    VM --> CTRL
    VM --> REPO
    VM --> ANIM
    CTRL --> SVC
    CTRL --> SPEC
    SPEC --> SVC
    SPEC --> REPO
    CTRL --> REPO
    SVC --> REPO
    SVC --> COORD
    SVC --> ANIM
    COORD --> GM
    ANIM --> REND
    ANIM --> PROF
    ANIM --> CREPO
    ANIM --> SRUN
    SRUN --> LUA
    SRUN --> REND
    REND --> GM
    REND --> PROF
    BOOT --> SVC
    BOOT --> REPO
```

### Layers

| Layer | Package | Responsibility |
|-------|---------|----------------|
| **Glyph** | `glyph/` | Nothing SDK session, low-level channel work, all animations, LED access arbitration. Split into `device/` (per-model layout), `engine/` (frame drawing), `animations/`, `audio/` and `battery/` (the effects), `script/` (user-written Lua animations) |
| **Data** | `data/` | User settings, migrations, defaults |
| **Services** | `services/` | Foreground services reacting to system events, plus the `FeatureSpec` registry |
| **UI** | `ui/` | Compose screens, cards, dialogs, themes, fonts, navigation |
| **DI** | `di/` | The one `@Module`: provides the settings `SharedPreferences` under the `@GlyphPrefs` qualifier |
| **Utils** | `utils/` | Logging |
| **Receiver** | `receiver/` | Restores services after reboot |

### Directory Structure

```
app/src/main/java/com/bleelblep/glyphsharge/
├── GlyphZenApplication.kt      # @HiltAndroidApp (class GlyphShargeApplication)
├── MainActivity.kt             # Entry point, the only Activity of the app proper
├── CustomAnimationsActivity.kt # The Lua animation studio (separate Activity, not in the NavHost)
├── glyph/
│   ├── GlyphManager.kt         # SDK session, device registration
│   ├── GlyphAnimationManager.kt# Public façade: the animation entry points
│   ├── GlyphFeatureCoordinator.kt # Mutex, withStrip(), and the enum GlyphFeature
│   ├── AnimationCatalog.kt     # enum GlyphAnimationId (the settings id strings)
│   ├── RunTrace.kt             # A durable trace of what each feature actually drew
│   ├── device/                 # Per-model LED layout and timings
│   │   ├── DeviceType.kt       # Model detection, in one place
│   │   ├── DeviceProfile.kt    # Profile data classes
│   │   └── DeviceProfileFactory.kt
│   ├── engine/                 # Frame building, run flag, error handling
│   │   └── GlyphRenderer.kt
│   ├── animations/             # The animations themselves
│   │   ├── AnimationRunner.kt  # anim { }: the guards and the renderer lifecycle
│   │   ├── BuiltInAnimations.kt# The ten named sequences, one line each
│   │   ├── SequenceAnimations.kt
│   │   ├── ParticleAnimations.kt
│   │   └── AudioAnimations.kt  # The music visualiser's painter
│   ├── audio/                  # The visualiser's input and its playback
│   │   ├── AudioFrame.kt / AudioFrameFeed.kt / AudioAnalysis.kt
│   │   ├── AudioAnalyzer.kt / Fft.kt
│   │   ├── MusicVisualisation.kt # play() and the settings preview()
│   │   ├── MusicVisualizationMode.kt / SyntheticTrack.kt
│   │   └── PlaybackAudioSource.kt # The MediaProjection capture
│   ├── script/                 # User-written Lua animations
│   │   ├── ScriptAnimation.kt  # Model: name, source, id shaped custom:<12 hex>
│   │   ├── ScriptFileFormat.kt # The .glyphlua container (encode/decode/file name)
│   │   ├── LuaScriptEngine.kt  # LuaJ: the sandbox, the watchdog, validate()
│   │   ├── ScriptPlayback.kt   # Hosting a script under the built-in guards
│   │   ├── ScriptSession.kt    # Per-run state: deadline, budget, interruptible waits
│   │   ├── ScriptTarget.kt     # glyph.target, and which picker offers what
│   │   ├── GlyphLuaApi.kt      # The glyph table — the whole language for a script
│   │   └── ScriptRunner.kt     # The only place the VM meets real hardware
│   └── battery/                # Charging and power-peek bar
│       ├── BatteryState.kt
│       └── BatteryGlyphAnimator.kt
├── data/
│   ├── SettingsRepository.kt   # Thin façade over the slices below
│   ├── SettingsPrefs.kt        # reified get/put over SharedPreferences
│   ├── ThemeSettings.kt / FontSettings.kt / GlyphServiceSettings.kt
│   ├── FeatureSettings.kt      # The eight features' switches, ids and durations
│   ├── QuietHoursSettings.kt / LanguageSettings.kt / UserPresenceSettings.kt
│   ├── SettingsMigrations.kt   # First-run defaults and the version steps
│   ├── SettingsDiagnostics.kt  # dumpAllSettings()
│   └── CustomAnimationRepository.kt # Script files + index, export through MediaStore
├── services/
│   ├── GlyphForegroundService.kt   # Master service
│   ├── FeatureServiceController.kt # Starts, stops and reads features
│   ├── FeatureSpec.kt          # The feature → service → preference registry
│   ├── ChargingAnimationService.kt
│   ├── PowerPeekService.kt
│   ├── PulseLockService.kt
│   ├── ScreenOffGlyphService.kt
│   ├── NfcGlyphService.kt
│   ├── LowBatteryAlertService.kt
│   ├── QuietHoursService.kt
│   ├── VpnConnectedService.kt
│   └── MusicVisualizerService.kt
├── receiver/
│   └── BootCompletedReceiver.kt
├── ui/
│   ├── components/            # Cards, dialogs, layout
│   │   ├── FeatureCards.kt    # The 7 feature cards; VpnConnected.kt holds the 8th
│   │   ├── GlyphAnimations.kt # The animation catalogue + rememberAnimationOptions()
│   │   ├── GlyphDependencies.kt # rememberGlyphAnimationManager()
│   │   ├── cards/             # ContentCard, FeatureCard, WideFeatureCardWithToggle
│   │   ├── controls/          # MorphingToggleButton
│   │   ├── dialogs/           # FeatureDialogScaffold, FeatureConfirmationFlow
│   │   └── layout/            # SettingsScaffold, DraggableSettingsCard
│   ├── screens/               # Screens
│   │   └── animations/       # Studio: list, editor
│   ├── navigation/            # Routes, GlyphNavHost
│   ├── state/                 # FeatureUiState, HomeUiState
│   ├── theme/                 # Themes, fonts, typography, LocalSettings
│   ├── utils/                 # HapticUtils
│   └── viewmodel/             # HomeViewModel, CustomAnimationsViewModel,
│                              # AnimationStudioViewModel
├── utils/
│   └── LoggingManager.kt
└── di/
    └── AppModule.kt           # The single @Module: @GlyphPrefs SharedPreferences
```

### Key Architectural Decisions

**One place routes features to services.** [FeatureSpec](../app/src/main/java/com/bleelblep/glyphsharge/services/FeatureSpec.kt)
is the whole mapping as **data**: one record per feature pairing its service class, its stop
action, its two preference lambdas and its service-off message.
[FeatureServiceController](../app/src/main/java/com/bleelblep/glyphsharge/services/FeatureServiceController.kt)
only decides *what* to do with a feature, never *which* service or preference that is, and
`FeatureSpecs.init` fails the build-up if the list and the enum ever disagree. Both the
Activity and the ViewModel drive services through the same controller.

**Mutual exclusion over the LEDs.** Several services may want the glyphs at once.
[GlyphFeatureCoordinator](../app/src/main/java/com/bleelblep/glyphsharge/glyph/GlyphFeatureCoordinator.kt)
holds a `Mutex` — only one feature owns the LEDs at a time. `withStrip(owner, preempt, …)`
is the only way a service takes it: the block runs under the lock, the release is in a
`finally`, and a failed acquisition returns `null` without ever entering the block. The
second caller gets a refusal after 500 ms and yields gracefully.

**User-written animations are a separate engine behind the existing façade.** The Lua
scripts got neither their own branch inside the services nor their own renderer. A feature
still stores one id string, and `GlyphAnimationManager.playAnimation(id, durationMs)` checks
`ScriptAnimation.isCustomId(id)` **before** the built-in `GlyphAnimationId.of(id)` lookup and
hands over to `ScriptRunner`:

```
feature setting (an id shaped custom:<12 hex>)
  → CustomAnimationRepository (source lookup)
  → GlyphAnimationManager.playAnimation
  → ScriptRunner.runScript          (Dispatchers.Default, RendererHost)
  → LuaScriptEngine.run             (LuaJ, sandbox + watchdog)
  → GlyphRenderer → GlyphManager    (the LEDs)
```

A script runs under the **same** guards and the same strip blanking as a built-in
animation, so a feature that has picked one behaves like a feature that has picked any
other. Details in [section 14](#14-custom-animations-lua).

**The studio is a separate Activity, not a NavHost route.**
[CustomAnimationsActivity](../app/src/main/java/com/bleelblep/glyphsharge/CustomAnimationsActivity.kt)
is declared with `android:exported="false"` and `parentActivityName=".MainActivity"`.
It owns its own back stack (a `BackHandler` between the list and the editor) and its own
`ActivityResultLauncher`s for `OpenDocument`/`CreateDocument` — system file dialogs should
not leak into the shared settings navigation graph. A new card in `SettingsScreen`
(`settings_card_custom_animations`, with the saved-script count) launches it.

> [!IMPORTANT]
> **`GlyphFeature` is a single enum**, declared at the end of
> [GlyphFeatureCoordinator.kt](../app/src/main/java/com/bleelblep/glyphsharge/glyph/GlyphFeatureCoordinator.kt):
> `PULSE_LOCK`, `POWER_PEEK`, `LOW_BATTERY`, `SCREEN_OFF`, `NFC`, `CHARGING_ANIMATION`,
> `MUSIC_VISUALIZER`, `VPN_CONNECTED`. The feature list, the service wiring and the UI all
> agree on it, so a feature cannot be switched on without something behind it. See
> [section 7](#7-adding-a-new-service-quickstart).

---

## 4. Glyph Layer

### GlyphManager

`@Singleton`, constructor-injected. A wrapper over the official `com.nothing.ketchum` SDK.
It owns **only** the SDK session — no animation logic, no hard-coded channel numbers.

```kotlin
fun initialize()                 // SDK init, lazy mCallback, bind the system service
fun isNothingPhone(): Boolean    // device model check
fun openSession()                // open the glyph session
fun closeSession()               // close the session
val isSessionActive: Boolean     // session state
val isServiceConnected: Boolean  // is the Nothing service bound
var onSessionStateChanged: ((Boolean) -> Unit)?  // SDK → UI state mirror
fun forceEnsureSession(): Boolean// blocking wait for readiness (up to 2 s)
fun toggleGlyphService(): Boolean
fun turnOffAll()                 // turn every LED off
fun turnOnAllGlyphs()            // light every channel of the connected model
fun canPerformOperation(): Boolean
fun cleanup()
```

**Session lifecycle:**

1. `GlyphShargeApplication.onCreate` → `if (isNothingPhone()) glyphManager.initialize()`
2. `GlyphManager.mCallback.onServiceConnected` → `DeviceType.detect()` → `mGM.register(id)`
3. `HomeViewModel.toggleGlyphService(true)` → `openSession()`
4. `GlyphFeatureCoordinator.acquire()` → if the session is not active, `forceEnsureSession()`
5. `onStop` / `onDestroy` → when the preference is off, `closeSession()` + `cleanup()`

**Device detection** is centralised in `DeviceType.detect()`. The enum carries both the
`Common.is*` check and the SDK registration id, so "which phone is this?" is asked in exactly
one place instead of five `when` chains.

> [!NOTE]
> `forceEnsureSession()` uses a blocking `Thread.sleep(100)` in a loop for up to 2 seconds.
> It is called from `GlyphFeatureCoordinator.acquire()` through `withContext(Dispatchers.IO)`,
> so it blocks an IO thread for the whole wait if the Nothing service never binds.

### GlyphRenderer

`@Singleton`, constructor-injected. The **only** class that touches `GlyphManager.mGM` directly.
Animations are written as `suspend` extensions on it, which is what keeps the three shared
concerns in one place:

| Method | Purpose |
|--------|---------|
| `val isRunning: Boolean` | set by `start()` / `stop()`, polled by every animation loop |
| `turnOff()` | blank the strip; never throws |
| `frame(channels, brightness)` | a builder with channels lit at one brightness, or `null` |
| `builder()` | an empty builder, for per-channel brightness ramps |
| `toggle(builder, delayMs)` | show a frame, then optionally wait |
| `toggleChannels(channels, brightness, delayMs)` | `frame` + `toggle` |
| `pulse(channels, onMs, offMs, brightness)` | the "on, off, wait" primitive everything is built from |
| `onError(e, message, retryDelayMs)` | log a failed step and keep going — but rethrows `CancellationException` |

`GLYPH_MAX_BRIGHTNESS = 4000` is a top-level constant in the same file.

### GlyphAnimationManager

`@Singleton`. A thin façade over the work, which is split by concern so a change stays in
one place. It holds no drawing code and no renderer lifecycle of its own; what is left
here is the part that is about *the app* — which settings id a feature plays, how long it
may run, and how a caller bounds it:

| Concern | Where |
|---------|-------|
| Which channels exist on this phone | `glyph/device/DeviceProfileFactory` |
| Frame building, error handling, cancellation | `glyph/engine/GlyphRenderer` |
| The guards and the blank-strip-around-it lifecycle (`anim { }`) | `glyph/animations/AnimationRunner` |
| The ten named sequences, one line each | `glyph/animations/BuiltInAnimations` |
| What actually draws a sequence | `glyph/animations/SequenceAnimations`, `ParticleAnimations`, `AudioAnimations` |
| The music visualiser and its preview | `glyph/audio/MusicVisualisation` |
| Charging and the battery bar | `glyph/battery/BatteryGlyphAnimator` |
| Hosting a user-written Lua animation | `glyph/script/ScriptPlayback` |
| Executing the Lua itself | `glyph/script/ScriptRunner` |

`AnimationRunner` also owns the two things every entry point needs: the lazily resolved
`profile: DeviceProfile?` (`null` on unsupported hardware) and `isGlyphServiceEnabled()`.

**Base animations** (all `suspend`, all one-liners over `AnimationRunner.anim`):

| Function | Description |
|----------|-------------|
| `runWaveAnimation()` | Wave across segment groups |
| `runBeedahAnimation()` | Same rhythm without the pause between groups |
| `runSpiralAnimation()` | Spiral sweep with a 0.6 → 1.0 brightness ramp |
| `runC1SequentialAnimation()` | Sequential pass along the C strip, forward and back |
| `runLockPulseAnimation()` | "Padlock": a widening wedge 0.3 → 1.0 |
| `runPulseEffect(cycles: Int = 3)` | Blink, 300 ms on / 300 ms off |
| `runHeartbeatAnimation()` | Three "lub-dub" beats |
| `runMatrixRainAnimation()` | Falling drops with tails |
| `runFireworksAnimation()` | Launches and explosions with fade-out |
| `runDNAHelixAnimation()` | Two counter-rotating strands |

**Feature scenarios** — wrappers that check settings and call a base animation:

```kotlin
suspend fun playPulseLockAnimation()
suspend fun playLowBatteryAnimation()
suspend fun playScreenOffAnimation()
suspend fun playNfcAnimation()
suspend fun playPowerPeekAnimation(context: Context, onProgressUpdate: (Float) -> Unit = {})
suspend fun playChargingAnimationAnimation(context: Context, onProgressUpdate: (Float) -> Unit = {})
suspend fun playMusicVisualizerAnimation()
suspend fun previewMusicVisualizer(mode: MusicVisualizationMode)
suspend fun playVpnConnectedAnimation()
fun stopAnimations()
```

**Dispatch by id.** A private `playAnimation(id, durationMs)` first checks whether the id is a
user one (`ScriptAnimation.isCustomId(id)` → the `custom:` prefix) and, if so, calls
`playCustomAnimation(id, durationMs)`. Only otherwise is the stored id resolved through
`GlyphAnimationId.of(id)` and run through a `when` over the enum:

| id | Animation |
|----|-----------|
| `C1` | `runC1SequentialAnimation()` |
| `WAVE` | `runWaveAnimation()` |
| `BEEDAH` | `runBeedahAnimation()` |
| `LOCK` | `runLockPulseAnimation()` |
| `SPIRAL` | `runSpiralAnimation()` |
| `HEARTBEAT` | `runHeartbeatAnimation()` |
| `MATRIX` | `runMatrixRainAnimation()` |
| `FIREWORKS` | `runFireworksAnimation()` |
| `DNA` | `runDNAHelixAnimation()` |
| `PULSE` + everything else | `runPulseEffect(cycles)` |

> [!NOTE]
> `durationMs` is used **only** in the `PULSE` branch (`cycles = duration / 500`).
> Named animations run their own natural length and ignore the duration setting.
> A user script is different again: it is a program, so it ends when its own code
> ends, and `ScriptAnimation.SAFETY_CAP_MS` is the only ceiling on it.

**Custom animations.** Four public methods and the run cap the services ask for:

| Method | Purpose |
|--------|---------|
| `fun runCapMs(runtimeId: String, featureDurationMs: Long): Long` | The wall-clock cap that actually applies: the feature's Duration for a built-in, `SAFETY_CAP_MS` for a `custom:` id |
| `suspend fun playCustomAnimation(runtimeId: String): ScriptRunResult` | Runs a stored script by its `custom:` id. Checks the service toggle and `isNothingPhone()`, looks the source up in `CustomAnimationRepository` |
| `suspend fun previewScript(source: String, durationMs: Long): ScriptRunResult` | Runs an unsaved source from the editor. **No** service-toggle check: the studio is an explicit user action |
| `fun checkScript(source: String): String?` | Compiles without running, the Check button. `null` means the syntax is fine |
| `fun stopAnimations()` | Also calls `scriptRunner.stop()`, so a script is interrupted too |

`ScriptPlayback` owns the private `playScript { }` all three suspend entry points go
through: blank the strip, run the block, blank it again in a `finally`, and turn any
exception into a `ScriptRunResult(RUNTIME_ERROR, …)` — nothing escapes.

**The watchdog every service shares.** `runCapped(capMs, onTimeout = {})` runs a block on
its own dispatcher for at most `capMs`: a drawing coroutine, a timer that cancels and
joins it, then `stopAnimations()` for whatever the cancellation alone cannot reach. A block
that finishes on its own leaves the watchdog cancelled and nothing else happens, and a
capped run whose animation was cut off returns `Unit` rather than rethrowing the
cancellation.

**The `anim { }` guard.** `AnimationRunner.anim { }` is the shared plumbing every sequence
borrows: it checks the service toggle, the device support and the profile, blanks the
strip, runs the body, and blanks it again in a `finally`. The body is a
`suspend GlyphRenderer.(DeviceProfile) -> Unit`, so animations read as
`anim { runWaveAnimation(it) }`.

> [!TIP]
> The catalogue is an enum now: `GlyphAnimationId` in `glyph/AnimationCatalog.kt` owns the id
> strings, so a typo in a setting is a compile-time-visible mismatch rather than a silent
> fall-through to the pulse effect.

### The animation implementations

The drawing code is grouped by concern, and nothing in it knows about settings, scripts
or the service layer:

| File | Contents |
|------|----------|
| `glyph/animations/AnimationRunner.kt` | `anim { }`: the three guards, the renderer lifecycle, the lazy `profile` |
| `glyph/animations/BuiltInAnimations.kt` | the ten named sequences, one line each over the runner |
| `glyph/animations/SequenceAnimations.kt` | wave, beedah, pulse, heartbeat, C1, lock, spiral |
| `glyph/animations/ParticleAnimations.kt` | matrix rain, fireworks, DNA helix |
| `glyph/animations/AudioAnimations.kt` | `runMusicVisualization` and `resolveStrip` |
| `glyph/audio/MusicVisualisation.kt` | the visualiser's real run and its settings preview |
| `glyph/audio/SyntheticTrack.kt` | the made-up spectrum the preview plays on |
| `glyph/battery/BatteryGlyphAnimator.kt` | the charging / power-peek battery bar and its accents |
| `glyph/battery/BatteryState.kt` | `BatteryState` + `BatteryStateReader` (sticky broadcast) |
| `glyph/engine/GlyphRenderer.kt` | frame building, the run flag, error handling, `pulse` |
| `glyph/device/DeviceProfileFactory.kt` | the per-model channel layouts and timings |
| `glyph/device/DeviceType.kt` | the single source of truth for model detection |
| `glyph/device/DeviceProfile.kt` | `DeviceProfile`, `AnimGroup`, and the three tuning configs |
| `glyph/script/ScriptPlayback.kt` | hosting a script: the guards, the blanking, the cap |
| `glyph/script/LuaScriptEngine.kt` | The LuaJ sandbox, the watchdog, `validate()` |
| `glyph/script/GlyphLuaApi.kt` | The `glyph` table — the whole user-facing language |
| `glyph/script/ScriptRunner.kt` | The only place the VM is joined to `GlyphRenderer` |

Two conventions make the animations readable:

- every loop starts with `if (!isRunning) return` — that is what makes `stopAnimations()`
  take effect within one step instead of at the end of a 5-second sequence;
- an animation that cannot light a frame uses `builder() ?: break` rather than crashing, so
  the sequence degrades to the frames that do work.

The UI catalogue is the `GlyphAnimations` object in
[ui/components/GlyphAnimations.kt](../app/src/main/java/com/bleelblep/glyphsharge/ui/components/GlyphAnimations.kt):
10 `GlyphAnim(id, displayName, iconRes, isCustom = false)` entries and `getById(id, options)`
falling back to the first entry. `withCustomAnimations(custom, scope)` appends the user's
scripts **after** the built-ins, so the chip row keeps a stable layout. The composable
`rememberAnimationOptions(scope)` reads the repository through `CustomAnimationsViewModel`
(the pickers are plain Composables, not ViewModels) and returns a list backed by a
`StateFlow`. `PulseLock`, `LowBattery`, `NfcGlyph` and `ScreenOff` use the default
`ScriptScope.TRIGGER`; the visualiser passes `ScriptScope.MUSIC` and so sees every script.
Each of the four Test buttons has a branch: `if (selectedAnimation.isCustom)
playCustomAnimation(id)`.

### GlyphFeatureCoordinator

```kotlin
val currentOwner: StateFlow<GlyphFeature?>
suspend fun <T> withStrip(
    owner: GlyphFeature,
    preempt: Boolean = false,
    timeoutMs: Long = if (preempt) PREEMPT_TIMEOUT_MS else ACQUIRE_TIMEOUT_MS,
    onRelease: () -> Unit = {},
    block: suspend () -> T
): T?
suspend fun acquire(owner: GlyphFeature, timeoutMs: Long = 500L): Boolean
suspend fun acquireNow(owner: GlyphFeature, timeoutMs: Long = 1_500L): Boolean
fun release(owner: GlyphFeature)
```

`withStrip` is what every service uses. It takes the lock, runs the block, and releases in
a `finally` — the release has to be there, because the strip is one shared resource behind
a `Mutex`: a service that returns, throws or is cancelled without releasing leaves the
*lock* held, and `release()` ignores a caller that is no longer the owner, so nothing can
take it back. A failed acquisition returns `null` rather than running the block, which is
how a caller tells "never got the strip" from "held it" without its own flag. `onRelease`
runs from the same `finally`, *after* `release`, so the LEDs are already off — the order the
services' own teardown expects.

`acquire()` grabs the `Mutex` with a timeout (a `tryLock` in a poll loop, deliberately not
`withTimeoutOrNull { lock.lock() }`, which could cancel a coroutine while it holds the
lock), sets `_currentOwner` on success, and raises the glyph session if needed.
`acquireNow()` additionally asks the current owner to stop drawing first, and is what
`preempt = true` goes through: an unlock or a lock is something the user just did, and the
interrupted owner is asked to return rather than dispossessed, so it can release in its own
`finally`. `release()` turns the LEDs off **first** (`turnOffAll()`), and only then hands the
mutex to the next owner — calls from a non-owner are ignored.

---

## 5. Data Layer

### Settings

Storage is **SharedPreferences**, file `glyphzen_settings`, provided once by
[di/AppModule.kt](../app/src/main/java/com/bleelblep/glyphsharge/di/AppModule.kt) under the
`@GlyphPrefs` qualifier. There are no `Flow`/`StateFlow` — every getter is synchronous, and
screens poll via `remember` plus `refreshFeatures()` in `onResume`.

The settings are split by concern, one class per slice, each owning its own `KEY_*`
constants and defaults:

| Slice | Owns |
|-------|------|
| `ThemeSettings` | dark theme, `AppThemeStyle` |
| `FontSettings` | `FontVariant`, the custom-fonts switch, the four size scales |
| `GlyphServiceSettings` | the master switch, vibration intensity, the shake steps |
| `FeatureSettings` | the eight features' switches, animation ids, durations, thresholds |
| `QuietHoursSettings` | the window and `isCurrentlyInQuietHours()` |
| `LanguageSettings` | the `language` code |
| `UserPresenceSettings` | the unlock bookkeeping Pulse Lock reads |
| `SettingsMigrations` | first-run defaults, the version steps, the legacy vibration fixup |
| `SettingsDiagnostics` | `dumpAllSettings()` |
| `SettingsPrefs.kt` | reified `SharedPreferences.getSetting` / `putSetting` helpers |

`SettingsPrefs` exists because spelling out `prefs.getBoolean(KEY, false)` and
`prefs.edit { putBoolean(KEY, value) }` for every key is where the two halves of a setting
drift apart: nothing ties the type used to save to the type used to read. The reified type
parameter makes the compiler check that pairing instead.

**`SettingsRepository` is a thin façade over the slices** — 78 one-line forwards and not a
single preference key, default or `SharedPreferences` call left in it. It is a compatibility
surface rather than a design: rewriting all ~25 call sites in the same change that split the
storage would have put every settings read in the app behind review at once, for a refactor
whose whole claim is that it changes no behaviour. Every signature is unchanged from the
monolith, so deleting a method here is a compile error somewhere real and the façade cannot
quietly fall behind the slices it forwards to.

**`SettingsMigrations` runs the three passes** — `applyFirstRunDefaults()` (guarded by a
`first_run_completed` flag, writing `false` for every feature flag, `HEADLINE` for the font,
`use_custom_fonts = true`, scales `1.0f`), `applyVersionMigrations()` (one step per version
marker, each checking `prefs.contains(KEY)` before writing so a lost marker cannot wipe user
toggles) and `normalizeLegacyVibrationIntensity()` (the legacy `1..255` value into a
`0.1f..1.0f` scale). It runs them from its own `init` block *and* from the explicit
`applyMigrations()` call `GlyphShargeApplication.onCreate` makes; an `applied` flag makes the
second path a no-op, so the guarantee — defaults on disk before any getter can run — holds
whichever arrives first. `SettingsRepository` takes it as a constructor argument precisely to
keep that guarantee structural.

**Public API of the façade (grouped):**

| Group | Methods |
|-------|---------|
| Theme | `saveTheme` / `getTheme`, `saveThemeStyle` / `getThemeStyle` |
| Font | `saveFontVariant` / `getFontVariant`, `saveUseCustomFonts` / `getUseCustomFonts`, `saveFontSizeSettings` / `getFontSizeSettings`, `clearFontSizeCustomization`, `getFontSizeSettingsForFont` |
| Master switch | `saveGlyphServiceEnabled` / `getGlyphServiceEnabled` |
| Power Peek | `savePowerPeekEnabled` / `isPowerPeekEnabled`, `savePowerPeekThreshold` / `getPowerPeekThreshold`, `getShakeIntensityLevel`, `savePowerPeekDuration` / `getPowerPeekDuration`, `saveVibrationIntensity` / `getVibrationIntensity` |
| Pulse Lock | `savePulseLockEnabled` / `isPulseLockEnabled`, `…AnimationId`, `…Duration` |
| Low Battery | `saveLowBatteryEnabled` / `isLowBatteryEnabled`, `…Threshold`, `…AnimationId`, `…Duration` |
| Screen Off | `saveScreenOffFeatureEnabled` / `isScreenOffFeatureEnabled`, `…AnimationId`, `…Duration` |
| NFC | `saveNfcFeatureEnabled` / `isNfcFeatureEnabled`, `…AnimationId`, `…AnimationDuration` |
| Charging | `saveChargingAnimationEnabled` / `isChargingAnimationEnabled`, `…Duration` |
| Music visualiser | `saveMusicVizEnabled` / `isMusicVizEnabled`, `…AnimationId`, `…Sensitivity`, `…ScreenOffOnly` |
| VPN Connected | `saveVpnConnectedEnabled` / `isVpnConnectedEnabled`, `…AnimationId`, `…Duration` |
| User presence | `isUserPresentExpected`, `markUserPresentSeen`, `markUserPresentMissing` |
| Quiet hours | `saveQuietHoursEnabled` / `isQuietHoursEnabled`, `saveQuietHoursStartHour/Minute`, `saveQuietHoursEndHour/Minute`, `isCurrentlyInQuietHours()` |
| Language | `getAppLanguageCode` / `saveAppLanguageCode` |
| Debug | `dumpAllSettings()` (writes to Logcat only when `Log.isLoggable`) |

Public constants: `SHAKE_SOFT=12.0f`, `SHAKE_EASY=15.0f`, `SHAKE_MEDIUM=18.0f`,
`SHAKE_HARD=22.0f`, `SHAKE_HARDEST=28.0f`.

> [!NOTE]
> The charging animation has **no** animation-selection setting (`saveChargingAnimationId`
> does not exist) — it always uses the fixed `playChargingAnimationAnimation`.

### CustomAnimationRepository

`@Singleton`, the second repository of the data layer — for the user's own animations.
[CustomAnimationRepository.kt](../app/src/main/java/com/bleelblep/glyphsharge/data/CustomAnimationRepository.kt)

**Sources in files, the index in preferences.** A script is edited often and can grow past
what a preference value should hold, so each one lives in its own file at
`filesDir/glyph_scripts/<id>.glyphlua`, while SharedPreferences
`glyphsharge_custom_animations` only holds a compact JSON array (`org.json`) — enough to draw
the list without touching the disk.

The list is exposed as a `StateFlow<List<ScriptAnimation>>`: the animation pickers on the
Pulse Lock, Low Battery, NFC and Screen Off cards are several screens away from the editor,
so saving a script has to update the chips everywhere at once.

| Group | Methods |
|-------|---------|
| Read | `reload()`, `getById(id)`, `findByRuntimeId(runtimeId)`, `animations: StateFlow` |
| Write | `draft(template)`, `save(animation)`, `rename(id, name)`, `delete(id)`, `duplicate(id)` |
| Import | `suspend readText(uri)`, `suspend importFrom(text, fallbackName)` |
| Export | `suspend exportTo(uri, animation)` (system file picker), `suspend exportToDownloads(animation)` (MediaStore) |

> [!IMPORTANT]
> **Import always assigns a fresh id** (`ScriptAnimation.newId()`), even when the file header
> carries a foreign one. Otherwise importing a file that is already on the phone would
> silently overwrite the local copy. Names are de-duplicated with a `(2)`, `(3)`… suffix —
> two identical names are indistinguishable in a chip row.
>
> `exportToDownloads()` goes through `MediaStore.Downloads` with the `IS_PENDING` protocol
> (API 29+), so **no permission is required**; `exportTo(uri)` works through
> `ActivityResultContracts.CreateDocument`.

`R.string.studio_starter_script` is the template a brand new animation starts from. It
deliberately uses only `glyph.ch.all`/`glyph.ch.c` and never hard-codes a channel number, so
it works on every supported phone.


---

## 6. Services

### Overview

| Service | Purpose | Trigger |
|---------|---------|---------|
| `GlyphForegroundService` | Persistent notification, keeps the process alive. Owns no LED logic | `FeatureServiceController.stopAll()` |
| `ChargingAnimationService` | Animation on charger connect/disconnect | `POWER_CONNECTED` / `POWER_DISCONNECTED` |
| `PowerPeekService` | Battery check by shaking while the screen is off | Accelerometer |
| `PulseLockService` | Animation on device unlock | `ACTION_USER_PRESENT` |
| `ScreenOffGlyphService` | Animation on screen lock | `ACTION_SCREEN_OFF` |
| `NfcGlyphService` | Animation on NFC event | NFC Intent via the Activity |
| `LowBatteryAlertService` | Low battery notification | `ACTION_BATTERY_CHANGED` |
| `QuietHoursService` | Silence during a scheduled window | `AlarmManager` |
| `MusicVisualizerService` | Spectrum visualisation of whatever is playing | `Visualizer` + `AudioManager` |
| `VpnConnectedService` | Animation when a VPN connects | A `NetworkCallback` on `TRANSPORT_VPN` — not a broadcast |

> [!NOTE]
> **`VpnConnectedService` is the one trigger that is not a broadcast.** Android broadcasts no
> "VPN connected" event, so the service registers a `ConnectivityManager.NetworkCallback` for
> `NetworkCapabilities.TRANSPORT_VPN` and declares `ACCESS_NETWORK_STATE` — the *state*, not
> the network itself. `registerNetworkCallback` delivers `onAvailable` immediately for a VPN
> that is **already** up, which is indistinguishable from a connect the user just made, so the
> service reads the current state *before* registering and keeps a `wasConnected` latch:
> `onAvailable` plays the animation only on a genuine false → true edge, while `onLost` and
> `onUnavailable` clear the latch so the next `onAvailable` is read as the connect event it is.
> A VPN that is already up when the service starts therefore leaves the strip dark — the
> animation belongs to the connect event, not to the service being started.

### Service Lifecycle Contract

Every feature service must implement **all six** points:

```kotlin
@AndroidEntryPoint
class MyService : Service() {

    // This service's own registry entry, which owns the run gate: my switch
    // and the master Glyph switch, in one answer.
    private val spec = FeatureSpecs.of(GlyphFeature.MY_FEATURE)

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureCoordinator: GlyphFeatureCoordinator

    // 1. Action constants
    companion object {
        const val ACTION_START = "com.bleelblep.glyphsharge.MY_FEATURE_START"
        const val ACTION_STOP  = "com.bleelblep.glyphsharge.MY_FEATURE_STOP"
        private const val TAG = "MyService"
        private const val NOTIF_ID = 1020
        private const val NOTIF_CHANNEL_ID = "MyServiceChannel"
    }

    // 2. Creation: notification channel, WakeLock, receivers
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerReceivers()
    }

    // 3. Start: startForeground() in the first lines
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        if (intent?.action == ACTION_STOP) { shutDown(); return START_NOT_STICKY }
        if (!spec.isRunnable(settingsRepository)) { shutDown(); return START_NOT_STICKY }
        return START_STICKY
    }

    // 4. Destruction: unregister receivers, release WakeLock, cancel coroutines
    override fun onDestroy() {
        runCatching { unregisterReceiver(myReceiver) }
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        serviceJob.cancel()
        super.onDestroy()
    }

    // 5. The service does not bind
    override fun onBind(intent: Intent?): IBinder? = null

    // 6. Self-restart after being swiped from recents
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!spec.isRunnable(settingsRepository)) return
        startForegroundService(Intent(this, MyService::class.java).apply { action = ACTION_START })
    }
}
```

### Rules That Must Not Be Broken

> [!IMPORTANT]
> 1. **`startForeground()` at the top of `onStartCommand`** — otherwise Android 12+ throws
>    `ForegroundServiceDidNotStartInTimeException`.
> 2. **All 10 services use `foregroundServiceType="specialUse"`** (the music visualiser adds
>    `mediaProjection`) and must declare their `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE`
>    in the manifest — a Google Play requirement.
> 3. **Guard every trigger through `spec.isRunnable(settingsRepository)`** — the feature's own
>    flag and the master `getGlyphServiceEnabled()` answered together, so `onStartCommand`,
>    `onTaskRemoved` and the event handler cannot pass one and fail another — plus quiet hours
>    `isCurrentlyInQuietHours()` where it applies.
> 4. **Always `runCatching { release() }`** around WakeLock and `unregisterReceiver` — both
>    throw if the resource was never acquired.
> 5. **Take the strip only through `featureCoordinator.withStrip { }`**, never by calling
>    `acquire()` and `release()` by hand: the release has to be in a `finally` or the mutex
>    stays held and every other feature freezes.
> 6. **Bound the animation with `glyphAnimationManager.runCapped(capMs) { }`** — the shared
>    watchdog, even when the animation itself runs longer.

### Canonical Animation Trigger

`withStrip` hands the strip over, `runCapped` bounds the run, and the teardown rides on
`onRelease` so it happens after the LEDs are off and only when they were really taken:

```kotlin
private fun triggerMyFeature() {
    animationScope.launch {
        if (!spec.isRunnable(settingsRepository)) return@launch
        if (settingsRepository.isCurrentlyInQuietHours()) return@launch
        try {
            val played = featureCoordinator.withStrip(
                owner = GlyphFeature.MY_FEATURE,
                // `preempt = true` when the user just did the thing that
                // triggered this; a feature that would rather be skipped
                // leaves it false and loses the strip in 500 ms.
                onRelease = { runCatching { if (wakeLock.isHeld) wakeLock.release() } }
            ) {
                val duration = settingsRepository.getMyFeatureDuration()
                runCatching { wakeLock.acquire(duration + 1000L) }

                glyphAnimationManager.runCapped(capMs = duration) {
                    glyphAnimationManager.playMyFeatureAnimation()
                }
            }
            if (played == null) {
                Log.d(TAG, "Strip busy by ${featureCoordinator.currentOwner.value} – skipping")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in my feature sequence", e)
        }
    }
}
```

> [!TIP]
> The WakeLock is acquired **inside** the block and released from `onRelease`. A busy strip
> returns before the block runs, so the WakeLock was never taken and there is nothing to
> undo — which is why a failed acquisition is a plain `return`, not an `onRelease` call.
> A service that also promotes itself to foreground for the sequence does the same with
> `onRelease = { stopForegroundCompat() }`.

### FeatureServiceController

The mapping itself is **data**, not code: [FeatureSpec.kt](../app/src/main/java/com/bleelblep/glyphsharge/services/FeatureSpec.kt)
holds one record per feature, and the controller only decides *what* to do with it.

```kotlin
data class FeatureSpec(
    val feature: GlyphFeature,
    val serviceClass: Class<out Service>,
    val stopAction: String,
    val isEnabled: (SettingsRepository) -> Boolean,
    val saveEnabled: (SettingsRepository, Boolean) -> Unit,
    @StringRes val serviceOffMessage: Int,
) {
    fun isRunnable(settings: SettingsRepository): Boolean =
        isEnabled(settings) && settings.getGlyphServiceEnabled()
}

object FeatureSpecs {
    val all: List<FeatureSpec>
    fun of(feature: GlyphFeature): FeatureSpec
    fun allFor(features: Iterable<GlyphFeature>): List<FeatureSpec>
}
```

> [!NOTE]
> The preference accessors are lambdas over `SettingsRepository` rather than methods on it,
> so the registry stays declarative and the settings layer keeps having no idea that
> features exist as a group. `FeatureSpecs.init` checks that every `GlyphFeature` entry has
> a spec and throws otherwise — an entry missing from a `Map` lookup does not compile, and
> a feature with no spec has no service to start and no preference to read.

Public API: `readAll()`, `start(feature)`, `stop(feature)`, `apply(feature, enabled)`,
`startAllEnabled()`, `stopAll()`.

`startAllEnabled()` and `stopAll()` iterate `GlyphFeature.entries`, so a new feature is picked
up automatically — there is nothing to register separately.

### BootCompletedReceiver

`@AndroidEntryPoint`, works via `goAsync()` + `Dispatchers.IO`. Restores in two tiers with a
`TIER2_DELAY_MS = 100L` delay:

1. **Tier 1** — `GlyphForegroundService`, if the master switch is on.
2. **Tier 2** — the seven features in order: PowerPeek, LowBattery, PulseLock, ScreenOff, NFC,
   Charging Animation, VPN Connected. Each guarded by its own flag **and** by the shared
   `glyphOn`.
3. **Quiet hours** — last, independent of the glyphs.

> [!NOTE]
> The music visualiser is deliberately **not** in tier 2: it captures other apps' audio
> through a `MediaProjection` token, and a token cannot be obtained from the background — only
> from an Activity result. A boot-time start would either be refused outright or come up with
> no capture and sit there showing a dead card, so the app asks the user to open it instead.
>
> VPN Connected **is** in tier 2 even when a VPN survived the reboot: the service has to be
> watching to see the next connect, and it reads the current state before it registers, so a
> carried-over VPN is classified as "already up" instead of being replayed as a connect the
> user never made.

---

## 7. Adding a New Service (Quickstart)

> [!TIP]
> This is the step-by-step procedure for **adding a new feature with its own foreground
> service**, modelled on `ScreenOffGlyphService` / `LowBatteryAlertService`.
> Example: a feature that plays a progress bar whenever the phone is picked up.
> A real, committed example to read the four steps against is the VPN Connected feature —
> [VpnConnectedService.kt](../app/src/main/java/com/bleelblep/glyphsharge/services/VpnConnectedService.kt)
> and [VpnConnected.kt](../app/src/main/java/com/bleelblep/glyphsharge/ui/components/VpnConnected.kt)
> — which additionally shows the one trigger that is **not** a `BroadcastReceiver`.

A feature is four pieces of wiring, and only four: **an enum value, a registry entry, a
service, and a card.** Everything the feature needs to be *known* — which service backs it,
which preference holds its switch, what to say when the Glyph service is off — is data in
one record, so nothing else in the app has to learn that the service exists.

The order is **bottom-up** (enum → registry → service → UI) so the project either compiles at
every moment or fails with a comprehensible compiler error.

### Change Map

| # | Step | File |
|---|------|------|
| 1 | The feature's name | [GlyphFeatureCoordinator.kt](../app/src/main/java/com/bleelblep/glyphsharge/glyph/GlyphFeatureCoordinator.kt) |
| 2 | Its service, stop action, preference and message | [FeatureSpec.kt](../app/src/main/java/com/bleelblep/glyphsharge/services/FeatureSpec.kt) |
| 3 | The service itself | **new** `services/…Service.kt` |
| 4 | The card and its dialog | **new** `ui/components/….kt` |

Alongside those four, the mechanical parts: settings keys, the manifest entry, the strings
and — if the feature should survive a reboot — one line in the boot receiver.

---

### Step 1. Add the value to `GlyphFeature`

`GlyphFeature` is the single enumeration at the end of
[GlyphFeatureCoordinator.kt](../app/src/main/java/com/bleelblep/glyphsharge/glyph/GlyphFeatureCoordinator.kt),
and the feature list, the service wiring and the UI all agree on it. Add your value there:

```kotlin
enum class GlyphFeature {
    PULSE_LOCK,
    POWER_PEEK,
    LOW_BATTERY,
    SCREEN_OFF,
    NFC,
    CHARGING_ANIMATION,
    MUSIC_VISUALIZER,
    PROGRESS_ON_PICKUP,          // ← new
}
```

> [!NOTE]
> The order matches `FeatureSpecs.all` so the registry can be read against the enum. There
> is no second enum to keep in sync, and no `when` anywhere that has to learn the new value
> by hand — `startAllEnabled()`, `stopAll()` and `readAll()` all iterate
> `GlyphFeature.entries`.

---

### Step 2. Add one `FeatureSpec` entry

In [FeatureSpec.kt](../app/src/main/java/com/bleelblep/glyphsharge/services/FeatureSpec.kt)
add one record to `FeatureSpecs.all`, next to the others:

```kotlin
FeatureSpec(
    feature = GlyphFeature.PROGRESS_ON_PICKUP,
    serviceClass = ProgressOnPickupService::class.java,
    stopAction = ProgressOnPickupService.ACTION_STOP,
    isEnabled = { it.isProgressOnPickupEnabled() },
    saveEnabled = { repo, enabled -> repo.saveProgressOnPickupEnabled(enabled) },
    serviceOffMessage = R.string.progress_on_pickup_toast
)
```

That is the whole mapping. The two lambdas are how the preference is read and written, and
`isRunnable(settings)` — the gate every service asks before it acts — comes with the record
rather than being spelled out again in each service:

```kotlin
fun isRunnable(settings: SettingsRepository): Boolean =
    isEnabled(settings) && settings.getGlyphServiceEnabled()
```

`FeatureSpecs.init` throws at class-load time if the enum has a value this list does not, so
a half-registered feature fails immediately and loudly instead of at the first start intent.
`startAllEnabled()`, `stopAll()` and `readAll()` need no edit: they iterate the enum.

> [!IMPORTANT]
> `serviceOffMessage` is the toast this feature already shows when its dialog is opened
> while the Glyph service is off. It belongs in the record because it is the same kind of
> fact — one per feature, forgotten independently of the others — and because reusing the
> wording each feature already has beats adding a second, slightly different message next
> to it. `HomeViewModel.setFeatureEnabled` reads it straight from the registry.

---

### Step 3. Write the service

Create `services/ProgressOnPickupService.kt` from the template in
[section 6](#service-lifecycle-contract), and give it two things that every feature
service does the same way: **take the strip through `withStrip`, and bound the run through
`runCapped`.**

```kotlin
@AndroidEntryPoint
class ProgressOnPickupService : Service() {

    // This service's own registry entry, which owns the run gate: my switch
    // and the master Glyph switch, in one answer.
    private val spec = FeatureSpecs.of(GlyphFeature.PROGRESS_ON_PICKUP)

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureCoordinator: GlyphFeatureCoordinator

    // … lifecycle: onCreate, onStartCommand, onDestroy, onBind, onTaskRemoved …

    private fun triggerProgressBar() {
        animationScope.launch {
            if (!spec.isRunnable(settingsRepository)) return@launch
            if (settingsRepository.isCurrentlyInQuietHours()) return@launch

            try {
                val played = featureCoordinator.withStrip(
                    owner = GlyphFeature.PROGRESS_ON_PICKUP,
                    // `preempt = true` only for something the user just did
                    // (an unlock, a lock, a tap): it interrupts the current
                    // owner instead of losing the strip in 500 ms.
                    onRelease = { stopForegroundCompat() }
                ) {
                    val duration = settingsRepository.getProgressOnPickupDuration()

                    startForeground(NOTIF_ID, buildNotification())

                    glyphAnimationManager.runCapped(
                        capMs = duration,
                        onTimeout = { Log.d(TAG, "Duration limit reached") }
                    ) {
                        glyphAnimationManager.playProgressOnPickupAnimation()
                    }
                }
                if (played == null) {
                    Log.d(TAG, "Strip busy by ${featureCoordinator.currentOwner.value} – skipping")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in progress bar sequence", e)
            }
        }
    }
}
```

**Why `withStrip` and not `acquire()`/`release()` by hand.** The strip is one shared
resource behind a `Mutex`, so a service that returns, throws or is cancelled without
releasing leaves the *lock* held, not merely the LEDs lit — and `release()` ignores a
caller that is no longer the owner, so nothing can take it back and every other feature logs
"strip busy" on every trigger until the process dies. `withStrip` puts the release in a
`finally`, and returns `null` when the strip was never taken, so the caller can tell "never
got it" from "held it" without a flag of its own.

`onRelease` runs from that same `finally`, **after** `release`, so the LEDs are already off —
the order a service's own teardown (WakeLock, `stopForeground`) expects. It does not run
when acquisition failed, because then nothing was taken and nothing needs undoing; a service
that promotes itself to foreground inside the block therefore stays foregrounded on the one
path where the block never ran.

**Why `runCapped` and not a hand-rolled watchdog.** It is the same sequence eight services
used to copy-paste — a drawing coroutine, a timer that cancels and joins it, a
`stopAnimations()` for whatever the cancellation cannot reach — with the one mistake each
copy got slightly differently handled. It also joins the animation before returning, which
is what keeps the strip from being released while frames are still being drawn.

Key points for the rest of the service:

- `@AndroidEntryPoint` for injection
- `ACTION_START` / `ACTION_STOP` in the `companion object` prefixed `com.bleelblep.glyphsharge.`
- `startForeground()` as the first statement in `onStartCommand`
- `CoroutineScope(Dispatchers.Main + SupervisorJob())`
- Register any `BroadcastReceiver` via `ContextCompat.registerReceiver(...)`

---

### Step 4. Add the card and its dialog

Create `ui/components/ProgressOnPickup.kt`, modelled on
[ScreenOff.kt](../app/src/main/java/com/bleelblep/glyphsharge/ui/components/ScreenOff.kt):
a config data class, a confirmation dialog over `FeatureConfirmationFlow`, and the
configuration dialog behind it.

**The card takes no settings parameter.** It reads the store from the composition, which is
what makes a card callable on its own — from a preview, or from anywhere else that just
wants the card:

```kotlin
@Composable
fun ProgressOnPickupCard(
    isEnabled: Boolean,
    isServiceActive: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.progress_on_pickup_title),
    description: String = stringResource(R.string.progress_on_pickup_description),
    icon: Painter,
    iconSize: Int = 32,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val toastText = stringResource(R.string.progress_on_pickup_toast)

    WideFeatureCardWithToggle(
        title = title,
        description = description,
        icon = icon,
        isServiceActive = isServiceActive,
        isFeatureEnabled = isEnabled,
        onFeatureToggle = onEnabledChange,
        onCardClick = {
            if (isServiceActive) showDialog = true
            else Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        },
        modifier = modifier,
        iconSize = iconSize
    )

    if (showDialog && isServiceActive) {
        ProgressOnPickupConfirmationDialog(
            onTest = { onTest(); showDialog = false },
            onEnable = { onEnabledChange(true); showDialog = false },
            onDisable = { onEnabledChange(false); showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}
```

**The configuration dialog reads the store from the composition too:**

```kotlin
@Composable
fun ProgressOnPickupEnableDialog(
    onConfirm: (ProgressOnPickupConfig) -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsRepository = LocalSettingsRepository.current
    val haptic = LocalHapticFeedback.current
    val vibrationIntensity = LocalVibrationIntensity.current
    val scope = rememberCoroutineScope()

    val currentlyEnabled = remember { settingsRepository.isProgressOnPickupEnabled() }
    var durationSeconds by remember {
        mutableFloatStateOf((settingsRepository.getProgressOnPickupDuration() / 1000f))
    }

    // … AlertDialog: sliders mutate local state, FeatureSaveButtons writes on Save …

    // A Test button plays it on the real strip:
    val glyphAnimationManager = rememberGlyphAnimationManager()
    scope.launch {
        runCatching { glyphAnimationManager.playProgressOnPickupAnimation() }
    }
}
```

`rememberGlyphAnimationManager()` in
[GlyphDependencies.kt](../app/src/main/java/com/bleelblep/glyphsharge/ui/components/GlyphDependencies.kt)
is the whole answer to "how does a plain Composable reach the manager": it resolves
`HomeViewModel` through `hiltViewModel()`, which works from inside a dialog exactly as it
does from a screen, and hands back the same singleton the feature services hold.

**Configuration-dialog recipe** (identical across the features):

1. Read current values **once** into `remember` (or `rememberFloatStateOf` for sliders).
2. Changes either write straight to the repository **or** only on Save — the codebase
   currently prefers the former for animation selection and the latter for sliders.
3. Body: a scrollable `Column` (`heightIn(max = 400.dp)`, `spacedBy(20.dp)`) of `Card`s
   coloured with `themeCardContainerColor()`, sliders tinted with `themePrimaryActionColor()`,
   the value shown via `ThemedValueBadge`.
4. Every slider movement fires `HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)`.
5. Buttons — `FeatureSaveButtons`.

---

### The mechanical parts

None of these is architecture; all of them are needed for the feature to be reachable.

**Settings keys.** The keys and the accessors go in
[FeatureSettings.kt](../app/src/main/java/com/bleelblep/glyphsharge/data/FeatureSettings.kt)
(the slice that owns the features), and the façade forwards them from
[SettingsRepository.kt](../app/src/main/java/com/bleelblep/glyphsharge/data/SettingsRepository.kt)
so the registry's lambdas have something to call:

```kotlin
fun saveProgressOnPickupEnabled(enabled: Boolean) = prefs.putSetting(KEY_PROGRESS_ENABLED, enabled)

fun isProgressOnPickupEnabled(): Boolean = prefs.getSetting(KEY_PROGRESS_ENABLED, false)

fun saveProgressOnPickupDuration(durationMs: Long) =
    prefs.putSetting(KEY_PROGRESS_DURATION, durationMs)

fun getProgressOnPickupDuration(): Long =
    prefs.getSetting(KEY_PROGRESS_DURATION, DEFAULT_PROGRESS_DURATION)
```

and in `applyVersionMigrations()` a new step, which is what writes the default for users who
already have the app installed:

```kotlin
if (lastMigrated < 115) {
    prefs.edit {
        if (!prefs.contains(FeatureSettings.KEY_PROGRESS_ENABLED)) {
            putBoolean(FeatureSettings.KEY_PROGRESS_ENABLED, false)
        }
        putInt(KEY_LAST_MIGRATED_VERSION, 115)
    }
}
```

> [!TIP]
> The `prefs.contains(...)` guard is mandatory — without it, a user who already enabled
> the feature loses that setting on update.

> [!IMPORTANT]
> The version number is *one past the highest step already in the file*, not a constant.
> `SettingsMigrations` currently ends at 114 (the VPN-connected step), so a feature added
> today takes 115. Reusing a number that is already taken produces a step that can never
> run, because the earlier one has already advanced the stored version past it.

**Manifest.** In [AndroidManifest.xml](../app/src/main/AndroidManifest.xml), next to the
other services:

```xml
<service
    android:name=".services.ProgressOnPickupService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="This service draws a progress bar on the glyph interface when the phone is picked up." />
</service>
```

> [!IMPORTANT]
> The `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` description must **accurately** match the service's
> purpose. Google Play rejects submissions with inaccurate wording — in the current code
> three services have copy-pasted "low battery alert" text.

Remember any `<uses-permission>` the service needs, and its own
`<uses-feature>` where a sensor is involved.

**Strings.** Copy an existing block such as `charging_animation_*` in
[values/strings.xml](../app/src/main/res/values/strings.xml) — title, description, the
service-off toast the `FeatureSpec` names, the how-it-works pair, the Test and Save labels —
and add **the same keys** in `res/values-ru-rRU/strings.xml` with translations.

> [!TIP]
> Localization coverage in this project is complete — 298 strings in `values/` and 298 in
> `values-ru-rRU/`. Keep parity, or the Russian UI falls back to English.

**Home screen entry.** In
[HomeFeatures.kt](../app/src/main/java/com/bleelblep/glyphsharge/ui/screens/home/HomeFeatures.kt)
add an `item {}` to `homeFeatureCards`. That function is pure wiring — a card needs nothing
but the toggle state and the two callbacks:

```kotlin
item {
    ProgressOnPickupCard(
        isEnabled = uiState.stateOf(GlyphFeature.PROGRESS_ON_PICKUP).isEnabled,
        isServiceActive = uiState.glyphServiceEnabled,
        onEnabledChange = { viewModel.setFeatureEnabled(GlyphFeature.PROGRESS_ON_PICKUP, it) },
        onTest = { viewModel.testFeature(GlyphFeature.PROGRESS_ON_PICKUP) },
        icon = rememberVectorPainter(image = Icons.Default.DirectionsWalk),
        modifier = Modifier.fillMaxWidth(),
        iconSize = 32
    )
}
```

> [!NOTE]
> Card order on screen is defined right here — this is the only place the list items
> are enumerated.

**The card's Test button.** `HomeViewModel.testFeature()` is an exhaustive `when` over the
enum, so the compiler will point at it; add the branch and the `TOAST_BY_FEATURE` entry:

```kotlin
GlyphFeature.PROGRESS_ON_PICKUP -> glyphAnimationManager.playProgressOnPickupAnimation()
```

**Boot restoration (optional).** In
[BootCompletedReceiver.kt](../app/src/main/java/com/bleelblep/glyphsharge/receiver/BootCompletedReceiver.kt),
inside `startServicesInOrder`, in the tier-2 block:

```kotlin
if (glyphOn && settingsRepository.isProgressOnPickupEnabled()) {
    Log.d(TAG, "Tier 2 – ProgressOnPickup")
    context.startForegroundServiceCompat(ProgressOnPickupService::class.java)
}
```

---

### Pre-Build Checklist

- [ ] Value added to the single `GlyphFeature` enum
- [ ] One `FeatureSpec` entry — service class, stop action, both preference lambdas, message
- [ ] Service takes the strip through `withStrip` and bounds the run through `runCapped`
- [ ] `startForeground()` at the top of `onStartCommand`
- [ ] `runCatching` around `wakeLock.release()` and `unregisterReceiver`
- [ ] Service declared in the manifest with `specialUse` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`
- [ ] Settings keys added, version migration step added
- [ ] Strings added in **both** `strings.xml` files
- [ ] `when` in `HomeViewModel.testFeature()` and `TOAST_BY_FEATURE` updated
- [ ] Card takes no `settingsRepository` parameter; the dialog reads `LocalSettingsRepository`

### Common Mistakes

| Symptom | Cause |
|---------|-------|
| `IllegalStateException: FeatureSpecs has no entry for …` | The enum value was added but no `FeatureSpec` record was |
| `ForegroundServiceDidNotStartInTimeException` | `startForeground()` not called early in `onStartCommand` |
| Feature toggles on but glyphs stay dark | `acquire()`/`release()` called by hand instead of `withStrip` — the mutex is stuck and every other feature reports "strip busy" forever |
| The feature works once, then never again | A service that is cancelled while holding the lock; `withStrip` puts the release in a `finally` for exactly this |
| A toast appears instead of the dialog | The "Glyph service" master switch is off — check `isServiceActive` |
| The animation never stops at the duration setting | The run was launched with `launch` instead of `runCapped` |
| Service does not start after reboot | Not added to `BootCompletedReceiver`, or the feature flag is `false` |
| Localized text is empty | Key added to only one of the `strings.xml` files |

---

## 8. UI Layer

### Themes

```kotlin
enum class AppThemeStyle { CLASSIC, Y2K, NEON, AMOLED, PASTEL, EXPRESSIVE }
```

- `CLASSIC` — standard Material 3
- `Y2K` — chrome, cyber
- `NEON` — electric high-contrast
- `AMOLED` — true black, minimal
- `PASTEL` — soft colors
- `EXPRESSIVE` — Material 3 Expressive (asymmetric shapes, `CutCornerShape`)

Stored in `ThemeState` (`@Singleton`) as `mutableStateOf`, persisted via
`SettingsRepository.saveThemeStyle` under the `theme_style` key. Read failures fall back to `CLASSIC`.

**Root composable:**

```kotlin
@Composable
fun GlyphZenTheme(
    themeState: ThemeState,
    fontState: FontState,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
)
```

It publishes `LocalFontState` and `LocalThemeState` (both `staticCompositionLocalOf`).
Called from two places: `MainActivity.setupUI()` and `CustomAnimationsActivity` — the studio
is a separate Activity with its own back stack, and it still has to be themed.

**Color schemes:** 12 schemes in `ColorSchemes.kt` (dark and light for each style).
`getColorScheme(themeStyle, isDark)` is an exhaustive `when` **without `else`**, so a new enum
value breaks compilation before you can forget to add a scheme.

**Color resolvers** in `ThemeColors.kt` (read `LocalThemeState`):
`themeCardContainerColor()`, `themePrimaryActionColor()`, `themeSettingsButtonColor()`,
`themeSecondaryButtonColors()`.

**The settings store reaches the tree through a composition local.** Each Activity provides
it once, next to the haptic strength:

```kotlin
CompositionLocalProvider(
    LocalSettingsRepository provides settingsRepository,
    LocalVibrationIntensity provides settingsRepository.getVibrationIntensity(),
) { /* the whole screen tree */ }
```

`LocalSettingsRepository` is what a card, a dialog or a settings section reads, instead of
taking a parameter every caller would have to thread down. `LocalVibrationIntensity` is kept
apart because `HapticUtils` is a plain object called from click handlers, and a handler
cannot read a repository — a float in the composition is what lets the value be injected
once and passed down as an ordinary argument.

> [!NOTE]
> The `dynamicColor` parameter is never passed `true` anywhere — Material You dynamic colors
> are effectively disabled and only the custom schemes are used.

### Fonts

```kotlin
enum class FontVariant { HEADLINE, NDOT, SYSTEM }
enum class FontCategory { DISPLAY, TITLE, BODY, LABEL }
data class FontSizeSettings(displayScale, titleScale, bodyScale, labelScale)
```

- `HEADLINE` → `R.font.ntype_82_headline` (NType Headline)
- `NDOT` → `R.font.ndot55caps` (NDot 55 Caps)
- `SYSTEM` → `FontFamily.Default`

`FontState` (`@Singleton`) holds `currentVariant`, `useCustomFonts`, `fontSizeSettings`, and
exposes `getTitleFont()` / `getBodyFont()`. Scaling is applied in `createTypography(...)` —
each of the 15 Material 3 slots is multiplied by its category factor. `SYSTEM` defaults to
`0.8f` factors, the others to `1.0f`.

> [!NOTE]
> The `res/font/*.xml` files (`fonts.xml`, `ntype_headline.xml`, `ntype_regular.xml`) are
> **not used by code** — `FontState` builds `FontFamily` straight from the `.otf` files.
> Also, `ntype_regular.xml` actually points at `ndot55caps` (a naming mistake).

### Components

| Component | File | Purpose |
|-----------|------|---------|
| `ContentCard` | `cards/ContentCards.kt` | Base card: spring press animation, haptics |
| `FeatureCard` | `cards/ContentCards.kt` | Icon + title + description |
| `SquareFeatureCard` | `cards/ContentCards.kt` | 1:1 square card, dimmed when the service is off |
| `WideFeatureCardWithToggle` | `cards/ContentCards.kt` | Feature card with an overlaid toggle |
| `GlyphControlCard` | `cards/ContentCards.kt` | Master glyph switch card |
| `PowerPeekCard` … `MusicVisualizerCard`, `VpnConnectedCard` | `FeatureCards.kt`, `VpnConnected.kt` | The 8 feature cards |
| `rememberGlyphAnimationManager()` | `GlyphDependencies.kt` | The manager behind every card's Test button |
| `MorphingToggleButton` | `controls/ToggleButtons.kt` | Toggle with a morphing animation |
| `ThreeStateFontMorphingButton` | `controls/ToggleButtons.kt` | Three-state font picker |
| `FeatureDialogScaffold` | `dialogs/` | Dialog frame: title, subtitle, "how it works" block |
| `FeatureConfirmationFlow` | `dialogs/` | Two-state machine: confirm → configure |
| `FeatureConfirmationButtons` | `CommonDialogComponents.kt` | Test / ⚙ / Cancel row |
| `FeatureSaveButtons` | `CommonDialogComponents.kt` | Save / Disable / Cancel row |
| `ThemedValueBadge` | `CommonDialogComponents.kt` | Badge showing the current slider value |
| `SettingsScaffold` | `layout/` | Shared screen chrome: `LargeTopAppBar` + `LazyColumn` |
| `DraggableSettingsCard` | `layout/` | Swipe-to-navigate card; it has an optional `onClick` (used by the "Custom Animations" card) |
| `HomeSectionHeader`, `FeatureGrid` | `layout/SectionLayout.kt` | Section header and grid |

### Screens

| Screen | File | Signature |
|--------|------|-----------|
| Home | `screens/home/HomeScreen.kt` | `HomeScreen(onOpenSettings, modifier, viewModel = hiltViewModel())` |
| Settings | `screens/SettingsScreen.kt` | `SettingsScreen(onBackClick, onThemeSettingsClick, onFontSettingsClick, onQuietHoursSettingsClick, onLanguageSettingsClick)` |
| Theme | `screens/ThemeSettingsScreen.kt` | `ThemeSettingsScreen(onBackClick, modifier)` |
| Fonts | `screens/FontSettingsScreen.kt` | `FontSettingsScreen(fontState, onNavigateBack, modifier)` |
| Quiet hours | `screens/QuietHoursSettingsScreen.kt` | `QuietHoursSettingsScreen(onBackClick)` |
| Language | `screens/LanguageSettingsScreen.kt` | `LanguageSettingsScreen(onBackClick, onLanguageChanged, modifier)` |
| Animation list | `screens/animations/AnimationListScreen.kt` | The user's scripts: open, create, duplicate, delete, import, two exports |
| Animation editor | `screens/animations/AnimationEditorScreen.kt` | Name, code field, Check / Glyph buttons, console, a collapsible `glyph` cheat sheet |

> [!NOTE]
> **No screen takes a settings parameter.** Each one resolves the store from
> `LocalSettingsRepository.current` and its ViewModel from `hiltViewModel()`, which is what
> leaves a screen reachable from a preview or a test without the nav host. The last two rows
> live **outside `GlyphNavHost`** — their host is `CustomAnimationsActivity`. See
> [section 14](#14-custom-animations-lua).

### HomeViewModel

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val glyphManager: GlyphManager,
    val glyphAnimationManager: GlyphAnimationManager,
    private val serviceController: FeatureServiceController,
    private val playbackAudioSource: PlaybackAudioSource
) : ViewModel() {

    val uiState: StateFlow<HomeUiState>          // master switch + feature map
    val messages: Flow<String>                    // one-shot events → Toast
    val musicCaptureSource: PlaybackAudioSource    // the singleton the visualiser card asks
    fun refreshFeatures()
    fun onSessionStateChanged(isActive: Boolean)
    fun toggleGlyphService(enabled: Boolean)
    fun setFeatureEnabled(feature: GlyphFeature, enabled: Boolean)
    fun testFeature(feature: GlyphFeature)
    fun onMusicCaptureResult(resultCode: Int, data: Intent?)
    fun setNfcDispatchHook(hook: (Boolean) -> Unit)
}
```

`HomeUiState` holds `glyphServiceEnabled: Boolean` and
`features: Map<GlyphFeature, FeatureUiState>`, accessed via `stateOf(feature)`.

One-shot messages travel through `Channel<String>(Channel.BUFFERED)` → `receiveAsFlow()`,
because a Toast must not be held in state.

`setNfcDispatchHook` is the exception: `enableForegroundDispatch` requires a real Activity,
which a ViewModel must not hold, so the Activity registers its own hook.

### AnimationStudioViewModel

```kotlin
@HiltViewModel
class AnimationStudioViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: CustomAnimationRepository,
    private val glyphAnimationManager: GlyphAnimationManager,
    private val glyphManager: GlyphManager
) : ViewModel() {

    val uiState: StateFlow<StudioUiState>   // list + editor + console
    val messages: StateFlow<String?>        // one-shot Toasts
    fun newAnimation() / open(id) / closeEditor()
    fun updateName(name) / updateSource(source) / save()
    fun check()                              // compile only
    fun runOnGlyph()                         // on the real glyph
    fun stop()
    fun delete(id) / duplicate(id)
    fun importFrom(uri) / exportTo(uri, id) / exportToDownloads(id)
    fun consumeMessage()
}
```

`StudioUiState` is a single immutable snapshot: `animations`, `isEditorOpen`, `editing`,
`name`, `source`, `isDirty`, `console`, `isRunning`.

`isEditorOpen` is stored rather than derived from `editing` on purpose: a brand new script
has a draft to edit, but a state where the editor opens on an empty buffer would then be
unrepresentable — which is exactly the bug a derived flag invites.

**Check** compiles the source and reports the first syntax error without touching the
glyph. **Glyph** sends the same source through `GlyphAnimationManager.previewScript`, i.e.
the code path the feature services use, which is the only true test. There is no on-screen
preview: a schematic of the LED layout is not the same as the phone lighting up, and a
second "run" mode invites mistaking one for the other. The pre-flight checks (a phone with
no Glyph interface, a session that was never opened) live here rather than inside the
manager, because they are about what the *studio* is looking at.

The run is capped by `ScriptAnimation.SAFETY_CAP_MS`, not by a studio-specific limit: a
script is a program, it runs until its code is done, and Stop is the user's way out.

---

## 9. Navigation

Only `Routes` and `GlyphNavHost` exist — navigation is built in code, there is no XML graph.

```kotlin
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val THEME_SETTINGS = "theme_settings"
    const val FONT_SETTINGS = "font_settings"
    const val QUIET_HOURS_SETTINGS = "quiet_hours_settings"
    const val LANGUAGE_SETTINGS = "language_settings"
}
```

> [!TIP]
> The constants were extracted into an object on purpose: routes used to be bare string
> literals scattered around, and a typo (`"hidden_settings"`) compiled fine and only
> crashed at runtime.

`GlyphNavHost` registers six `composable(...)` destinations, all transitions are
`MaterialSharedAxisZ` (fade + scale, 200/120 ms).

> [!IMPORTANT]
> `CustomAnimationsActivity` is the app's **second** Activity, and it appears neither in
> `GlyphNavHost` nor in `Routes`. It has its own `BackHandler` between the list and the
> editor and its own `ActivityResultLauncher`s for the system file dialogs. Making the
> studio part of the settings nav graph is therefore its own task, not an extra
> `composable(...)`.

### Adding a New Screen

1. Add `const val MY_SCREEN = "my_screen"` to `Routes`.
2. Add `composable(Routes.MY_SCREEN) { MyScreen(onBackClick = { navController.popBackStack() }) }`
   inside the `NavHost { }` lambda in `GlyphNavHost.kt`. Transitions apply automatically.
3. Add an `onMySettingsClick` callback to the parent screen calling `navController.navigate(Routes.MY_SCREEN)`.
4. Build the screen on `SettingsScaffold(title, onBackClick) { item { … } }`.

---

## 10. Music visualiser

Draws whatever the phone is playing on the Glyph strip, for as long as there is
any. It is the only service with no trigger: everything else waits for an event,
this one watches a stream.

### The layers

| Concern | Where |
|---------|-------|
| Which channels a mode paints on | `glyph.animations.AudioAnimations` — `resolveStrip` |
| The spectrum maths | `glyph.audio.AudioAnalysis` (pure, no Android types) |
| Owning the `Visualizer` | `glyph.audio.AudioAnalyzer` |
| The `MediaProjection` capture the service drives | `glyph.audio.PlaybackAudioSource` |
| The frame both captures are read through | `glyph.audio.AudioFrameFeed` |
| Which mode the user picked | `glyph.audio.MusicVisualizationMode` |
| Playing it, and the settings preview | `glyph.audio.MusicVisualisation` |
| Starting, stopping, slicing | `services.MusicVisualizerService` |

### The six modes

`BARS` (equaliser), `WAVE` (scrolling level trace), `MIRROR` (bars outwards
from the centre), `BEAT` (a wash that snaps on every kick), `MATRIX` (rain
driven by the bands), `VORTEX` (two counter-rotating rings turned by the bass).

All six are one loop — `runMusicVisualization` — with a `when` on the mode, so
pacing, the idle behaviour and the per-frame state live exactly once. At
~30 fps.

> [!IMPORTANT]
> **`resolveStrip` is what makes this work on four phones.** Phone (1) has four
> channels in the C strip against Phone (3a)'s twenty. Below eight channels the
> visualiser falls back to `profile.all`: a twenty-bar equaliser squeezed into
> four channels is four blinking dots.

### Yielding the strip

`GlyphFeatureCoordinator` gives the strip to one feature at a time, and the six
short features all take it with `withStrip`, which loses rather than fights. A
visualiser that held the strip for a whole track would swallow every charging
animation, low-battery alert and screen-off effect.

So it paints for `SLICE_MS` (1.5 s), releases, waits `YIELD_GAP_MS` (60 ms) and
takes the strip again. A trigger landing in the gap — 4% of the time — is served
immediately; the rest wait at most 1.5 s. The price is a dark seam every 1.5 s,
which is the trade the design settled on: the alternative is a feature that
silently never fires.

### What is captured, and what is not

> [!WARNING]
> `Visualizer` on session `0` reads the **whole output mix**, not one app's
> audio. That is the only stream a non-system app can read on modern Android.
> Notifications, ringtones and system sounds are in the same buffer. The PCM
> never leaves `AudioAnalyzer` except as `0..1` levels: nothing is recorded,
> stored or transmitted, and the "How it works" card in the app says so.
>
> The buffer is only *used* while something musical is playing, and
> `AudioManager.isMusicActive()` plus the analysed level decide that.

`RECORD_AUDIO` gates the whole thing and is requested when the user switches
the feature on, the same way Power Peek asks for its permission.

### Degrading

If the device's FFT stream carries nothing while the waveform is loud,
`AudioAnalyzer` switches to banding the waveform: the spectrum then tracks
loudness rather than pitch, and logs
`FFT is empty while the waveform is loud`. Every mode still reacts to music.

---

## 11. Resources & Localization

```
app/src/main/res/
├── font/        ntype_82_headline.otf, ntype_82_regular.otf, ndot55caps.otf, *.xml
├── drawable/    _44.xml, _78.xml, _23_24px.xml, su.png, theme icons
├── values/      strings.xml (298), colors.xml (24), themes.xml
├── values-night/ colors.xml (5 dark-theme overrides)
├── values-ru-rRU/ strings.xml (298) — full coverage
└── xml/         file_paths.xml, backup_rules.xml, data_extraction_rules.xml
```

**Localization.** Two locales: English (`values/`, the default) and Russian (`values-ru-rRU/`),
298 strings each. In-app language switching (en / ru / system) is stored under the `"language"`
key and applied through `Context.applyLocale(code)` in `MainActivity.attachBaseContext`
plus an Activity restart.

The studio added 50 `studio_*` strings and 3 `settings_card_custom_animations*` ones in
**both** locales. The locale is applied in `CustomAnimationsActivity.attachBaseContext` too,
because that is a separate Activity with its own context.

> [!WARNING]
> Some UI text is **hardcoded in English** and bypasses `strings.xml`:
> `GlyphAnimations.displayName`, the "Start"/"Cancel" labels in `SquareFeatureCard`,
> `HomeScreen` (`"Glyph Sharge"`, `"Features"`, `"Settings"`),
> `HomeViewModel` (`"Glyph service is already enabled"`, `"Error: …"`), and
> `TOAST_BY_FEATURE`.
> `AnimationListScreen` and `AnimationEditorScreen`, on the other hand, do go through
> `stringResource` — the studio's own screens and console lines are all `studio_*` strings
> resolved by the ViewModel through `context.getString`.

**Permissions** (`AndroidManifest.xml`): `VIBRATE`, `WAKE_LOCK`, `WRITE_SETTINGS`,
`WRITE_EXTERNAL_STORAGE`, `com.nothing.ketchum.permission.ENABLE`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_SPECIAL_USE`, `FOREGROUND_SERVICE_DATA_SYNC`,
`FOREGROUND_SERVICE_MEDIA_PROJECTION`, `SYSTEM_ALERT_WINDOW`, `RECEIVE_BOOT_COMPLETED`,
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `DISABLE_KEYGUARD`, `TURN_SCREEN_ON`,
`SCHEDULE_EXACT_ALARM`, `NFC`, `ACCESS_NETWORK_STATE` (VPN Connected — the state, not
the network), `RECORD_AUDIO`, `READ_MEDIA_AUDIO`.

---

## 12. Build & Configuration

### Build scripts

Every script is Kotlin DSL, and `gradle/libs.versions.toml` is the single source of truth
for every plugin and library version — no version is declared twice.

| What | File | Key values |
|------|------|------------|
| Versions | `gradle/libs.versions.toml` | AGP 8.13.2, Kotlin 1.9.10, KSP 1.9.10-1.0.13, Hilt 2.50, Compose compiler 1.5.3 |
| Root | `build.gradle.kts` | plugin aliases only, each `apply false` |
| Settings | `settings.gradle.kts` | `google()` / `mavenCentral()`, `FAIL_ON_PROJECT_REPOS`, includes `:app` |
| App | `app/build.gradle.kts` | namespace + applicationId `com.bleelblep.glyphsharge`, compileSdk 36, min/target 34, JVM 17, versionCode 1031, versionName 1.0.31 |

Two details are easy to get wrong when reading the app script:

- **The Compose compiler is a version, not a plugin.** Kotlin 1.9.x predates
  `org.jetbrains.kotlin.plugin.compose` — that plugin starts at Kotlin 2.0 — so the version
  is supplied through `composeOptions { kotlinCompilerExtensionVersion }`.
- **Annotation processing runs on KSP alone.** Hilt's compiler is a `ksp(...)` dependency and
  no second annotation processor is applied, so there is no second processing pass per
  compile.

### Dependencies

Compose BOM 2024.02.00, Navigation Compose 2.7.7, lifecycle-runtime 2.7.0,
Hilt 2.50, `material-icons-extended` 1.6.0, Ketchum SDK (`libs/*.jar`),
**LuaJ 3.0.1** (`org.luaj:luaj-jse`) — the engine behind user-written animations.
`kotlinx-coroutines` is not declared; it arrives transitively.

Declared in `app/build.gradle.kts` but **unused**: `material3-window-size-class`,
`lottie-compose` (there is no onboarding flow in the project), and the whole
`androidTestImplementation` set — the `androidTest` source set does not exist in this
project, although `testInstrumentationRunner` is declared.

### Tests

```bash
./gradlew :app:testDebugUnitTest
```

**118 unit tests, 9 suites, all pure JVM** — no emulator, no device, no Robolectric. Run in
about 2 seconds.

| Suite | Tests | What it pins |
|-------|-------|--------------|
| `glyph/script/LuaScriptEngineTest` | 21 | A script really runs: drawing, loops, channel groups, `glyph.hold`, `glyph.exit`, syntax and runtime errors, the watchdog against an infinite loop and against `pcall`, interrupting a long `glyph.hold`, the absence of dangerous globals, `validate()`, and `glyph.target` read back by the script and reported in the result |
| `glyph/script/ScriptTargetTest` | 15 | The target classifier: quote styles, spacing, **line and block comments**, unknown values falling back to `ANY`, and which picker offers what |
| `glyph/script/ScriptFileFormatTest` | 7 | `encode`/`decode` round trip, a headerless import, a name with a newline, safe file names, the `custom:` namespace |
| `glyph/audio/FftTest` | 17 | Energy lands in the right bin, silence is exact zeros, magnitude is linear in amplitude, the size is a power of two, cached plans do not interfere |
| `glyph/audio/AudioFrameTest` | 13 | `bandsInto` peaks rather than averages, no band is lost at any segment count, the caller's array is the one written, `isSilent` against the floor, locale-independent `toString()` |
| `glyph/audio/BeatDetectorTest` | 12 | The rolling average, the cooldown, a constant level not beating forever, `reset()` between tracks |
| `glyph/audio/AudioAnalysisTest` | 6 | The calibration every mode's brightness rests on: a note lands in its band, silence produces nothing, a quiet room still reads as silence |
| `glyph/audio/MusicVisualizationModeTest` | 11 | Stored ids resolve, a `custom:<uuid>` script id is **not** a mode, and every mode is classified as idle-friendly or not |
| `glyph/device/DeviceProfileFactoryTest` | 16 | The per-model channel tables: every group names wired channels, nothing is wired twice, the budgets progress with the hardware, and Phone (2)'s second C run stays out of the battery bar |

The suites are grouped by **what breaks silently**, not by package. Every one of them covers
code whose failure mode is a strip that looks plausible and is wrong:

> [!IMPORTANT]
> A wrong constant in the FFT, an off-by-one in a channel range, a missed band edge or a
> locale-dependent number all produce output that renders fine and cannot be told apart
> from correct by looking at it. That is what these tests are for, and it is why they
> assert on known signals rather than on internals.

`app/build.gradle.kts` carries a block that the suites depend on:

```kotlin
testOptions {
    unitTests {
        // android.util.Log is a stub in JVM unit tests; the glyph layer only
        // uses it for diagnostics, so returning defaults is enough.
        isReturnDefaultValues = true
    }
}
```

> [!IMPORTANT]
> `android.util.Log` is a stub in JVM tests, so without `isReturnDefaultValues = true` any
> `Log.d` call inside the script layer fails the test with
> `RuntimeException: Method d in android.util.Log not mocked`.
> The script tests replace the hardware with a `FakeHost` — a `GlyphScriptHost` that records
> the frames a real run would have drawn — which is the same shape `ScriptRunner.RendererHost`
> presents to the VM, so a green run is evidence that the hosting path works.

#### What is not covered

> [!NOTE]
> The **services**, the **Compose UI** and **Hilt wiring** have no tests, and the
> `androidTest` source set is absent, so there is no instrumentation run to fall back on. A
> service is a foreground component driven by broadcasts on a phone that has a Glyph strip,
> and there is no JVM substitute for that — the honest coverage for them is a real Nothing
> Phone and manual checks.
> Anything that is pure logic belongs in the suites above instead: pull the decision out of
> the `when` and test it there, as `MusicVisualizationMode.isIdleFriendly` and
> `ScriptSession.result` already are.

### Release

```bash
./gradlew app:assembleRelease
```

`isMinifyEnabled = false` — R8 never runs, so `proguard-rules.pro` is not applied to the
shipped build.

> [!NOTE]
> If minification is ever turned on, one rule in `proguard-rules.pro` is load-bearing rather
> than defensive: `-keepclassmembers enum com.bleelblep.glyphsharge.ui.theme.**` keeps the
> constants of `AppThemeStyle` and `FontVariant`, which are persisted by `.name` and read
> back through `valueOf(String)`. The default Android rules keep `values()` and
> `valueOf(String)` but not the constants themselves, so an obfuscated build would stop
> recognising every stored value and silently fall back to the default theme.

---

## 13. Known Issues & Gotchas

> [!WARNING]
> This section is not a complaint list — it is a list of places where the code behaves
> in a non-obvious way. Check them before changing behaviour.

### Architecture

| Issue | Where | Consequence |
|-------|-------|-------------|
| **Empty stubs in `MainActivity`** | `startPersistentGlyphService()`, `maybeRestoreSession()`, `writeLogToUri()` | `GlyphForegroundService` **does not start** on a normal app launch — only after reboot |
| Quiet hours has no `GlyphFeature` | `MainActivity.startQuietHoursService()` | It is started with a bare `Intent` because it is deliberately outside the feature registry — every `GlyphFeature` value goes through the controller |

### Logging

`LoggingManager` is disabled by default: `isLoggingEnabled = false`, and
`setLoggingEnabled(true)` is **never called**. All 22 logging methods are no-ops,
including `logSessionState` and `logSDKOperation` inside `GlyphManager`.
`shareLogs()` and `LoggingManager.exportLogs()` are never called either.

> [!NOTE]
> When debugging glyphs, enable logging manually:
> `LoggingManager.initialize(this)` already runs in `GlyphShargeApplication.onCreate`,
> but you must add `setLoggingEnabled(true)` yourself.

### GlyphManager

- `forceEnsureSession()` blocks a thread with `Thread.sleep` for up to 2 seconds.
- `turnOnAllGlyphs()` is public API but has no caller.
- `onServiceDisconnected` calls `cleanup()`, which resets the initialised flag while
  `mCallback` stays registered; reconnection is only reached from `handleError`.
- `handleError` recovers only from the two exact messages `"Session not active"` and
  `"Service not connected"`. Any other `GlyphException` tears the session down.

### Glyph layer after the refactor

The `glyph` package was split by concern. The things worth knowing:

- `GlyphManager` no longer duplicates model detection — `DeviceType.detect()` is the only
  place that calls `Common.is*`.
- `GlyphManager` no longer hard-codes channel ranges in `turnOnAllGlyphs()`; it reads them
  from `DeviceProfileFactory`.
- `GlyphRenderer` is the only class that touches `mGM`; animations never build frames
  themselves.
- The per-model numbers live in two tables in `DeviceProfileFactory` instead of being spread
  through four ~45-line profile literals.
- `GlyphFeatureCoordinator` still uses `turnOffAll()` directly rather than going through
  `GlyphRenderer`. It is a blanking call, not a drawing one, so this is intentional.

### Script layer (`glyph/script/`)

The places where the code behaves in a non-obvious way:

- `debug` is **loaded**, used, and only then blanked. Until `Globals.debuglib` is set the VM
  never consults a hook at all, so the library cannot be removed before the watchdog is
  attached, and it cannot be left visible either — a script would clear the watchdog with
  `debug.sethook`. Do not reorder `buildGlobals`.
- The hook is written straight into `LuaThread.state.hookfunc` / `hookcount` rather than
  through `debug.sethook`, because the `debug` table is already hidden from the script.
- The watchdog throws the Java class `ScriptAbortedError` (an `Error`, not an `Exception`).
  A Lua error would be caught by `pcall` inside the script and the loop would keep burning CPU.
- Waits are sliced at `CANCEL_POLL_MS = 16L`, and a single `glyph.hold` is capped at
  `MAX_HOLD_MS = 60_000L`. A plain `Thread.sleep` inside a glyph call would keep the strip
  lit for the whole requested time after the user pressed stop.
- `print` is redirected into the studio console, but `os` and `io` are gone, so a script has
  neither stdout nor a file system.
- `ScriptRunner.check(source)` returns `null` when the connected phone's profile is unknown:
  there is nothing to compile against, and the Check button then says nothing.
- `ScriptRunner.RendererHost` bridges the suspending renderer to the blocking host with
  `runBlocking`. The caller is always off the main thread — otherwise the UI blocks for a frame.
- `previewScript` deliberately does **not** check the master glyph toggle (the studio is an
  explicit user action), while `playCustomAnimation` does (by then it is a feature running).
- `forPreview()` in `DeviceProfileFactory` always returns the Phone (3a) layout. Nothing
  in `main/` calls it any more — the studio has no on-screen preview — and nothing that
  lights real LEDs ever did; only the tests use it.

### UI

- `ChargingAnimationConfig.isEnabled` and `PowerPeekConfig.enableWhenScreenOff` are written
  but have **no matching repository keys** — the "only when screen is off" switch in the
  Power Peek dialog does nothing.
- `ThreeStateFontToggle` and `FontState.getDisplayFont()` are unused.
- `GlyphControlCard.illustrationRes` is accepted but never used in the body.
- `QuietHoursSettingsScreen` contains an empty `LaunchedEffect(Unit)` with only a comment,
  so `quietHoursEnabled` can go stale.

### Manifest

- `android:name=".GlyphShargeApplication"` matches the class name, but the **file is called
  `GlyphZenApplication.kt`** — the file name does not match its single class.
- The `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` text for `NfcGlyphService`,
  `ChargingAnimationService` and `LowBatteryAlertService` is **copy-pasted** and talks about
  "low battery alerts" — wrong for NFC and charging.
- `WRITE_EXTERNAL_STORAGE` is declared but unnecessary: the app only writes to
  `getExternalFilesDir` and `cacheDir`. It is a no-op on API 29+ anyway.
- `FOREGROUND_SERVICE_DATA_SYNC` is declared but no service uses it.
- `BootCompletedReceiver` is `android:exported="false"` — works on many OEM ROMs, but
  `true` is conventional for system broadcasts.
- The Nothing API key is committed in the manifest rather than moved to `BuildConfig`
  or a gradle property.

### Resources

Unused but shipped: `phone1.png` (1.8 MB), `phone1dark.png` (1.9 MB),
`batterystory.png` (1.9 MB), `resource__.xml`, `blur_on_24px.xml`, `graph_6_24px.xml`,
`activity_zone_24px.xml` — **≈5.6 MB** of dead weight. Also unused: the themes
`Theme.GlyphSharge.Dark` and `Theme.GlyphSharge.Transparent`, and the leftover template
colors `purple_200/500/700`, `teal_200/700`.

---

## 14. Custom Animations (Lua)

On top of the ten built-in animations, the user can write their own — in **Lua 5.2**,
executed by the pure-JVM [LuaJ](https://github.com/luaj/luaj) 3.0.1 VM
(`org.luaj:luaj-jse`). This is not a plugin and nothing is fetched from the internet: a
script lives entirely in `filesDir/glyph_scripts`, runs in a sandbox, and can do nothing but
light channels, wait, and read the battery.

### Opening the studio

**Settings → Custom Animations** (the "Custom Animations / Write your own in Lua" card also
shows how many scripts are saved). It is a separate Activity, not a settings route.

In the list every script has a menu: open, duplicate, delete, "Save to Downloads" and
"Export to a file". Renaming is not one of them — the editor's name field is the single
place a name gets edited. At the top there are Import and, **only once at least one script
is saved**, New: on an empty studio the call to action in the middle of the screen is the
only button that matters, so a second one up there would only compete with it. **New**
creates a draft from `R.string.studio_starter_script` — a wave along the C strip
that works on every supported phone.

### The editor: two buttons

| Button | What it does | What it does not prove |
|--------|--------------|------------------------|
| **Check** | Compiles the source, drawing nothing, and prints the first syntax error | Semantics: `glyph.group('nope')` is not caught |
| **Glyph** | Sends the same source through `GlyphAnimationManager.previewScript` — the very path the feature services use | — |

There is deliberately no on-screen run mode: a schematic of the LED layout is not the same
as the phone lighting up, and a second "run" button invites mistaking one for the other.
Check the syntax, then play it on the glyph — a phone with no Glyph interface, or a session
that was never opened, says so in the console rather than looking like a script that
silently did nothing.

While a run is in flight the second button turns into **Stop**. The only ceiling on a run is
`ScriptAnimation.SAFETY_CAP_MS = 30_000L`, which catches a script that never returns; normal
scripts end when their own code ends. Under the buttons sits the console (last 4 lines),
and under the code field a collapsible `glyph API reference` cheat sheet.

> [!NOTE]
> A script file **is its whole body**. The engine wraps the source in
> `local function __glyph_main() … end` and calls it immediately, so `local` declarations
> behave the way you expect, a top-level `return` is legal, and the file has no required
> structure at all. The Lua standard library is available: `math`, `string`, `table`,
> `pairs`/`ipairs`, `assert`, and so on.

### The `glyph` API reference

The `glyph` table is the **entire** language available to a script. An argument in square
brackets is optional; "channels" means `{ 1, 2, 3 }`, a single number `3`, or a group name
`"c"`.

#### Drawing

| Call | Meaning |
|------|---------|
| `glyph.set(channels, brightness, [holdMs])` | Light channels and leave them lit. `brightness = glyph.MAX` and 0 ms hold by default |
| `glyph.setAll([brightness], [holdMs])` | The same for every channel on the phone |
| `glyph.off([holdMs])` | Blank the strip, then wait |
| `glyph.hold(ms)` | Wait, interruptibly. A single call is capped at 60,000 ms |
| `glyph.pulse(channels, onMs, offMs, [brightness])` | On → all off → pause. Defaults 200 / 100 / `MAX` |
| `glyph.blinkAll([onMs], [offMs], [brightness], [times])` | Blink the whole strip. Defaults 150 / 150 / `MAX` / 1 |

#### Movement

| Call | Meaning |
|------|---------|
| `glyph.sweep(channels, [stepMs], [brightness], [reverse])` | Progressive pass: 1 channel, 2, 3… then blank. Step defaults to 80 ms |
| `glyph.wave(channels, [stepMs], [brightness], [trail])` | A travelling wave: `trail` segments stay lit behind the head, fading out. Step 80 ms, trail 0 |
| `glyph.spiral([cycles], [stepMs], [brightness])` | A pass along the `glyph.ch.spiral` order, out and back. Defaults 1 / 60 ms |
| `glyph.heartbeat([beats], [beatMs], [gapMs], [brightness])` | "Lub-dub". Defaults 3 / 150 / 120 ms |
| `glyph.batteryBar([percent], [ms])` | Fills the C strip like charging does. Percent defaults to `glyph.battery()`, duration to 2000 ms |

#### Channels and device

| Call | Meaning |
|------|---------|
| `glyph.MAX` | `4000` — the brightest a channel goes. Anything above is clamped, below 0 blanks |
| `glyph.ch.all` / `.a` / `.b` / `.c` / `.d` / `.e` | Channel groups from this phone's `DeviceProfile` |
| `glyph.ch.nonC` | Every channel except the C strip |
| `glyph.ch.spiral` | The spiral pass order |
| `glyph.ch.pulse` | The pulse segments |
| `glyph.group("name")` | The same as a list, by name: `"c"`, `"all"`, `"nonC"`… An unknown name is an error |
| `glyph.device` | The device type, e.g. `PHONE3A` |

> [!TIP]
> The groups come from the device profile, so one and the same script behaves correctly on
> a Phone (1) and on a Phone (3a) — you never need to know a channel number.

#### Run state

| Call | Meaning |
|------|---------|
| `glyph.time()` | Milliseconds since the run started |
| `glyph.frame()` | How many frames have been drawn |
| `glyph.battery()` | Charge level, 0..100 |
| `glyph.charging()` | Whether the phone is charging |
| `glyph.running()` | `false` once the animation has been stopped. The basis of a `while glyph.running() do` loop |

> [!WARNING]
> All five are **functions**, the parentheses are required: `while glyph.running do`
> without them is always true (the value is a function, and a function is truthy in Lua),
> so the loop is killed by the watchdog's wall-clock limit rather than by the stop request.
> `glyph.MAX` and `glyph.device` are the only plain values (a number and a string) and take
> no parentheses.
>
> Keep this in mind when reading the sources: the comment in `GlyphLuaApi.kt` and the
> `API_REFERENCE` cheat sheet in the editor both write `glyph.running` and `glyph.battery`
> without parentheses. The code is authoritative — with parentheses.

#### Randomness and maths

| Call | Meaning |
|------|---------|
| `glyph.rnd(a, b)` | An integer in `a..b` (the order does not matter) |
| `glyph.rndFloat()` | A float in `0..1` |
| `glyph.seed(n)` | Fixes the generator, so the pattern is reproducible |
| `glyph.ease(t, kind)` | The curve for `t` in `0..1`: `linear`, `in`, `out`, `inout`, `bounce`, `wave`, `pulse`. An unknown name = `linear` |

#### Control and output

| Call | Meaning |
|------|---------|
| `glyph.exit()` | Finish successfully right now — a normal ending, not a kill |
| `glyph.log(values…)` | A line in the studio console |
| `print(values…)` | The same; `print` is redirected into the console, not to stdout |
| `glyph.target = "music"` | **A declaration, not a setting** — see below |

#### Which service a script is for

```lua
glyph.target = "music"
```

A script with no `glyph.target` is offered by every picker, which is how it
worked before the visualiser existed. A script that says `"music"` appears **only**
in the visualiser's picker and disappears from Pulse Lock, NFC, Low Battery and
Screen Off — which is what a script reading `glyph.audio` wants, because
everywhere else every value is zero.

| Rule | Why |
|------|-----|
| It must come **before the first `glyph.set()`** | After that the script is already running in whichever service started it; a quiet re-filing would claim a guarantee that was never true |
| An unknown name is an **error**, not a silent fallback | A misspelling would otherwise make the script vanish from every picker with nothing to explain why |
| The editor shows the current target under the name field | Derived from the buffer, so it tracks every keystroke |

How the pickers know before anything has run: `ScriptTarget.detectIn(source)`
scans the source with the same regex the runtime uses. The runtime capture
through `__newindex` is authoritative and comes back in `ScriptRunResult.target`;
the scan exists only so the pickers can classify a script nobody has run yet.

#### Audio — the visualiser's input

Only non-zero while `MusicVisualizerService` is running with a live capture.
In the studio, and in every other feature, everything reads `0` / `false`.

| Read | Meaning |
|------|---------|
| `glyph.audio.active` | `true` while a capture is feeding the values below |
| `glyph.audio.level` | Overall loudness, `0..1` |
| `glyph.audio.bass` | Energy of the lowest third, `0..1` — where a kick lives |
| `glyph.audio.mid` | Energy of the middle third |
| `glyph.audio.treble` | Energy of the top third |
| `glyph.audio.beat` | `true` on the single frame a beat was detected |
| `glyph.audio.bands(n)` | A table of `n` values in `0..1`, resampled from the 32 analysed bands |

> [!NOTE]
> These are **values, not functions** — `glyph.audio.bass`, not
> `glyph.audio.bass()`. A function is truthy in Lua, so `while glyph.audio.bass do`
> would never end. `bands(n)` *is* a function, because it takes an argument.

**A script visualiser:**

```lua
glyph.target = "music"

local strip = glyph.ch.c
if #strip < 4 then strip = glyph.ch.all end

while glyph.running do
  local b = glyph.audio.bands(#strip)
  for i = 1, #strip do
    glyph.set({ strip[i] }, glyph.MAX * b[i], 25)
  end
  if not glyph.audio.active then glyph.hold(60) end
end
```

### Examples

**A simple wave along the strip** — this is also the starter template:

```lua
-- A wave along the long C strip.
-- The whole file is the animation: this is plain Lua.
-- Try changing 60 to 120, or glyph.MAX to 2000.

local strip = glyph.ch.c
local step = 60

for i = 1, #strip do
  glyph.set({ strip[i] }, glyph.MAX)
  glyph.hold(step)
end

-- Fade the whole strip out, one segment at a time.
for i = #strip, 1, -1 do
  glyph.set({ strip[i] }, 1500)
  glyph.hold(step)
end
```

**Advanced: a battery bar that reacts to the real charge level, with easing and random
flickers:**

```lua
-- A battery bar that looks at the real charge level.
glyph.seed(42)                       -- the pattern repeats from run to run

local strip = glyph.ch.c
local n = #strip
local step = 70

local function bar()
  local lit = math.min(n, math.floor(glyph.battery() / 100 * n) + 1)
  for i = 1, n do
    if i <= lit then
      -- The head of the bar is brighter: an inout curve, not a linear ramp.
      local b = 400 + 3000 * glyph.ease(i / n, "inout")
      glyph.set({ strip[i] }, b)
    else
      glyph.set({ strip[i] }, 0)
    end
    glyph.hold(step)
  end
end

while glyph.running() do
  if glyph.battery() >= 100 then
    glyph.spiral(1, 40, glyph.MAX)   -- full charge: a spiral
  else
    bar()
  end

  if glyph.charging() and glyph.rnd(1, 100) > 60 then
    -- Now and then, a flicker on a random segment.
    glyph.pulse({ strip[glyph.rnd(1, n)] }, 40, 60, glyph.MAX)
  else
    glyph.off(120)
  end
end

glyph.off()                          -- leave cleanly if the animation was stopped
```

### Scripts on the feature cards

A saved script is **not** bound to a particular feature. It shows up in the animation picker
of Pulse Lock, Low Battery, NFC and Screen Off — right after the ten built-ins, with the
shared custom icon and its own name, and its stored id looks like `custom:<12 hex>`.

The one exception is `glyph.target = "music"`, which restricts a script to the music
visualiser's picker. The rule is one-sided: the trigger features hide music scripts, and
the visualiser lists scripts with no target as well as music ones — because "no target"
means "anywhere", and the reverse is not true.

The charging animation and Power Peek have no picker at all and were left without scripts:
the charging bar is fixed by its own design.

Each of those four Test buttons looks at the `isCustom` flag and calls
`playCustomAnimation(id)` instead of the built-in branch.

> [!IMPORTANT]
> For a script, the **Duration** setting means something different from what it means for
> the built-in animations. Named built-ins ignore it; a script has no duration at all —
> `runCapMs()` hands the watchdog `ScriptAnimation.SAFETY_CAP_MS` for a `custom:` id instead
> of the feature's slider, and the feature dialog hides the duration control when a script
> is selected, because a control that changes nothing is worse than no control.
> That is also why a script behaves correctly with the screen off — it ends when its code
> ends, not "whenever the loop finally finishes".

### Export and import

| Action | Where | How |
|--------|------|-----|
| Save to Downloads | The public Downloads folder | `MediaStore.Downloads` with the `IS_PENDING` protocol (API 29+), **no permission** |
| Export to a file… | Anywhere | The system `ActivityResultContracts.CreateDocument` dialog |
| Import | From anywhere | `ActivityResultContracts.OpenDocument`; the MIME filter is deliberately widened to `*/*` |

On import the file name becomes the default script name, with a `(2)` suffix when it
collides. **The id is always fresh** — importing a file that is already on the phone cannot
overwrite it.

### The `.glyphlua` file format

A plain-text Lua file with a commented-out header. There is no container: the header *is*
comments, so an exported file runs in any Lua 5.2 host with nothing to unwrap.

```lua
-- Glyph Sharge animation
-- format: 1
-- name: My Heartbeat
-- id: 4f3c9a11b2e7
-- created: 1712345678000
-- updated: 1712345678000

glyph.set(glyph.ch.c, glyph.MAX)
```

| Field | Meaning |
|-------|---------|
| `-- Glyph Sharge animation` | The marker: the first line of the file |
| `format` | The format version, currently `1` |
| `name` | The display name |
| `id` | The identifier; replaced with a fresh one on import anyway |
| `created` / `updated` | Timestamps in milliseconds |

> [!NOTE]
> The header is a **run of comment lines at the very top**; the first line without `--`
> ends it. A file without our header is still imported — it becomes a new animation with
> the fallback name, which is exactly what someone who hand-wrote a script in another
> editor expects. A file with no Lua body is rejected with `InvalidScriptException`.
>
> The export file name comes from `suggestedFileName()`: spaces and characters the file
> system rejects become `_`, and an empty result falls back to `animation.glyphlua`.

### Safety: the sandbox and the watchdog

Scripts arrive from outside the app, so the environment is built by **subtraction** from the
standard LuaJ globals (`JsePlatform.standardGlobals()`):

| Removed | Why |
|---------|-----|
| `io`, `os` | File system and process control |
| `luajava` | The **real** escape hatch: LuaJ's reflection bridge into Java. Removed first |
| `load`, `loadstring`, `dofile`, `loadfile`, `require`, `module` | Compiling or loading code at runtime |
| `package` | Module loading |
| `coroutine` | The hook state lives on the thread, so a fresh coroutine would run unchecked |
| `collectgarbage` | A script could stall the VM |
| `newproxy` | The same class of escape through userdata |
| `debug` | `debug.sethook()` would remove the watchdog |

`print` is redirected to the studio console. `DebugLib` itself **is** loaded: until
`Globals.debuglib` is set the VM never consults a hook at all. The library is installed,
the watchdog is attached, and only then is `debug` blanked — a script can neither see it
nor clear it.

**Two watchdog limits**, both checked from a count hook with a 20,000-instruction interval:

| Limit | Value | What it stops |
|-------|-------|----------------|
| Wall clock | `ScriptAnimation.SAFETY_CAP_MS` (30,000 ms) | A script that runs longer than anyone is willing to watch |
| Instructions | 200,000,000 executed bytecodes | A script that burns CPU but never touches the clock |

> [!WARNING]
> The count hook is the **only** mechanism that interrupts a tight loop: call, return and
> line events are never generated inside one. The watchdog throws the Java class
> `ScriptAbortedError` (an `Error`, not an `Exception`), so `pcall` inside a script
> **cannot** swallow it. This is covered by the test
> `pcall cannot swallow the watchdog`.

**The interruptible wait.** Every pause is sliced into `CANCEL_POLL_MS = 16` ms chunks and
re-checks the stop flag after each one, so a stop is noticed within ~16 ms instead of at the
end of a long `glyph.hold`. The engine also blanks the strip in a `finally` — after any
outcome, including an error, the glyphs are never left half-lit.

### Where everything lives

| File | Role |
|------|------|
| `glyph/script/ScriptAnimation.kt` | The model: name, source, `custom:<12 hex>` id, `newId()`, `runtimeIdOf()`, `isCustomId()`, `stripPrefix()` |
| `glyph/script/ScriptFileFormat.kt` | The `.glyphlua` container: `encode()`, `decode()`, `suggestedFileName()`, `InvalidScriptException` |
| `glyph/script/LuaScriptEngine.kt` | Sandbox assembly, the watchdog, `run()`, `validate()`, `ScriptStatus` |
| `glyph/script/ScriptSession.kt` | Per-run state: deadline, instruction budget, frame counter, randomness, interruptible waits |
| `glyph/script/GlyphLuaApi.kt` | The `glyph` table — the whole user-facing language |
| `glyph/script/ScriptTarget.kt` | `ScriptTarget` (the declaration) and `ScriptScope` (which picker offers what) |
| `glyph/script/ScriptRunner.kt` | `runScript()` / `stop()` / `check()` plus the private `RendererHost` bridging the suspending renderer to a blocking host |
| `data/CustomAnimationRepository.kt` | Files, index, import, both exports, the starter script |
| `CustomAnimationsActivity.kt` | The studio Activity, the file pickers, the `BackHandler` |
| `ui/screens/animations/*.kt` | List and editor |
| `ui/viewmodel/AnimationStudioViewModel.kt` | Studio state, Check / Glyph |
| `app/src/test/.../glyph/script/*Test.kt` | 21 + 7 + 15 unit tests; run with `./gradlew :app:testDebugUnitTest` |
| `app/src/test/.../glyph/audio/*Test.kt` | 17 + 13 + 12 + 6 + 11 unit tests over the transform, the frame, the beat detector and the calibration |
| `app/src/test/.../glyph/device/DeviceProfileFactoryTest.kt` | 16 unit tests over the per-model channel tables |

---

*This documentation reflects the code at version 1.0.31. When the service layer changes,
update [section 7](#7-adding-a-new-service-quickstart); when you add a call to the `glyph`
table, update [section 14](#14-custom-animations-lua).*

