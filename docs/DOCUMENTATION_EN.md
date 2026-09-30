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
| ⚡ Quick Access | Two tiles in the shade: the master Glyph service and the music visualiser |

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
    SVC["Services Layer<br/>10 ForegroundServices"]
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
│   │   ├── LogLevel.kt         # INFO / WARN / ERROR, and the one line of console text
│   │   ├── ScriptRunner.kt     # The only place the VM meets real hardware
│   │   └── module/             # The six names require("…") can resolve, and nothing else
│   │       ├── ModuleRegistry.kt   # The closed set, and the require that reads it
│   │       ├── ModuleBuilder.kt    # value / live / func — assembling one module safely
│   │       ├── ModuleSourceScan.kt # require("…") names Check must reject before a phone does
│   │       ├── LuaArgs.kt          # Positional arguments for module functions
│   │       └── GlyphTimeModule.kt, GlyphBatteryModule.kt, GlyphNetModule.kt,
│   │           GlyphSensorModule.kt, GlyphLogModule.kt, GlyphUtilModule.kt
│   ├── net/                    # What glyph.net reads
│   │   ├── NetworkSnapshot.kt  # connected / wifi / metered / vpn, taken together
│   │   └── NetworkSource.kt    # ConnectivityManager behind a 1 s cache
│   ├── sensor/                 # What glyph.sensor reads
│   │   ├── SensorSnapshot.kt   # x / y / z / magnitude / shaken, one sample
│   │   ├── SensorControl.kt    # What opens and closes the listener for one run
│   │   └── SensorSource.kt     # The listener itself, and the threshold that counts a shake
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
│   ├── MusicVisualizerService.kt
│   └── GlyphServiceSwitch.kt    # The master switch, one place for the app and the tile
├── tiles/                    # Quick Settings tiles (the shade)
│   ├── GlyphServiceTileService.kt      # Master Glyph service
│   ├── MusicVisualizerTileService.kt   # Music visualiser
│   └── TileStateBus.kt                 # "A switch you mirror has moved"
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
| `fun checkScript(source: String): ScriptCheckResult` | Compiles without running, the Check button. A typed verdict, not a message: `ScriptCheckStatus.OK` / `SYNTAX_ERROR` / `MISSING_MODULE` |
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
deliberately uses only `glyph.ch.c` and never hard-codes a channel number, so it works on
every supported phone; and it `require`s `glyph.util`, because a template that taught the
flat API would teach the one the modules were meant to replace. It divides its step by the
length of the C strip — 4 segments on a Phone (1), 16 on a Phone (2), 24 on a Phone (2a),
20 on a Phone (3a) — so one pass takes about the same time everywhere: 1200 ms on three
models and 1184 ms on the fourth. The old template hardcoded 60 ms, which crawled on the
Phone (1) and blurred on the Phone (2a).

> [!IMPORTANT]
> **Only the comments are localised; the Lua is byte-for-byte identical** in
> `values/strings.xml` and `values-ru-rRU/strings.xml`. That is what lets a Russian author
> read the same file as an English one, and `StarterScriptTest` fails the build if the two
> halves ever drift apart.


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

### Quick Settings Tiles

Two tiles in the shade, both `TileService`s in `tiles/`:

| Tile | What a tap does |
|------|-----------------|
| `GlyphServiceTileService` | Flips the master Glyph switch: opens or closes the Ketchum session, writes the flag, brings `GlyphForegroundService` up and starts or stops every feature behind it |
| `MusicVisualizerTileService` | Turns the visualiser on or off, and switches the master Glyph service on by itself if it was off |

On an unsupported device both report `STATE_UNAVAILABLE`: a switch that cannot be turned back
on should not be offered.

**One switch, two places.** The master switch lives in
[`GlyphServiceSwitch`](../app/src/main/java/com/bleelblep/glyphsharge/services/GlyphServiceSwitch.kt),
and both the home card and the tile call it. `apply()` is `suspend`, because opening a session
waits for the system service to bind (`forceEnsureSession`, up to two seconds) and a blocking
call from `TileService.onClick()` would freeze the shade. The flag is written **before** the
services are started: `FeatureSpec.isRunnable` reads it in `onStartCommand`, and
`GlyphForegroundService` restarts itself from `onDestroy` for as long as the flag is on.

**The tile shows what is happening.** The feature switch and the master switch are two separate
preferences. Turning the master off leaves the feature switched on in the settings but not
running, which is why `MusicVisualizerTileService.isRunning()` requires both — otherwise a lit
tile sits over a strip that has gone dark. A tap acts on what the tile displays, not on one flag.

**The visualiser's permissions.** A `MediaProjection` token comes back only as an Activity
result: a tile can neither show the system dialog nor request `RECORD_AUDIO`. So the tap collapses
the shade through `startActivityAndCollapse()` and opens `MainActivity` with
`ACTION_ENABLE_MUSIC`, which runs the same two-request chain as the card's switch and then closes
through `finishAndRemoveTask()` — `finish()` alone leaves the task in the recents, which on
Nothing reads as "the app minimised" rather than "the question was asked and closed". The chain
lives in the Activity and not in the card because the visualiser card is the last one in the list
and may not be composed yet when the app opens from a tile.

> [!WARNING]
> **The token lives until the system takes it back:** a screen lock, another projection, or a
> stop from the shade revokes it. So the first tap on a phone with no live token opens the app,
> and so does the first tap after every lock screen. There is no way around it — the platform
> hands out no token without an Activity.

**State updates.** `tiles/TileStateBus.kt` announces "a switch you mirror has moved" on two
channels: an in-app broadcast (`RECEIVER_NOT_EXPORTED`) for a tile that is listening right now,
and `TileService.requestListeningState()` for one that is not, because the shade is closed. Only
the second call makes the system deliver `onStartListening()`. The subscription therefore lives
for the lifetime of the service, not for the `onStartListening()`/`onStopListening()` window.

> [!NOTE]
> Android allows a tile to start a foreground service: tapping a UI element belonging to the app
> is one of the background-start exemptions. Starting an *Activity* from a tile is not allowed
> (a `TileService` has no such privilege from Android 14 on), hence `startActivityAndCollapse()`.
> Verified in the manifest: `android:exported="true"` is required, and
> `android.permission.BIND_QUICK_SETTINGS_TILE` restricts the service to the system.

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
>    `mediaProjection`). Nine of them also declare `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE`
>    in the manifest, which is a Google Play requirement; `GlyphForegroundService` declares
>    no property, because it renders nothing and only keeps the process alive.
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
> purpose. Google Play rejects submissions with inaccurate wording, and this manifest did
> carry it: `NfcGlyphService` and `ChargingAnimationService` both declared the
> `LowBatteryAlertService` text verbatim. Both now describe the work they actually do.

Remember any `<uses-permission>` the service needs, and its own
`<uses-feature>` where a sensor is involved.

**Strings.** Copy an existing block such as `charging_animation_*` in
[values/strings.xml](../app/src/main/res/values/strings.xml) — title, description, the
service-off toast the `FeatureSpec` names, the how-it-works pair, the Test and Save labels —
and add **the same keys** in `res/values-ru-rRU/strings.xml` with translations.

> [!TIP]
> Localization coverage in this project is complete — 301 strings in `values/` and 301 in
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

`ConsoleLine` carries a `LogLevel` (`INFO` / `WARN` / `ERROR`) rather than an
`isError: Boolean` flag. A script's own output now reaches this console, and that output
has three severities; a flag with two states cannot tell a warning from a fatal note, which
is exactly the pair a script debugging itself needs to separate. The studio's own outcome
line uses the same three, so one rule colours the whole card.

**Check** compiles the source and reports the first thing wrong with it, without touching
the glyph. That is now two findings rather than one, because the two need different words:
a file that does not parse, and a file that parses perfectly and then names a module this
build does not ship.

`isEditorOpen` is stored rather than derived from `editing` on purpose: a brand new script
has a draft to edit, but a state where the editor opens on an empty buffer would then be
unrepresentable — which is exactly the bug a derived flag invites.

**Glyph** sends the same source through `GlyphAnimationManager.previewScript`, i.e.
the code path the feature services use, which is the only true test. There is no on-screen
preview: a schematic of the LED layout is not the same as the phone lighting up, and a
second "run" mode invites mistaking one for the other. The pre-flight checks (a phone with
no Glyph interface, a session that was never opened) live here rather than inside the
manager, because they are about what the *studio* is looking at.

The console for a finished run is the script's own output followed by the outcome, in that
order, so a script that logs its way to a failure reads top to bottom. A run that logged
nothing is just the outcome line — there is no empty section to render and no "the script
said nothing" filler to explain an absence.

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
├── values/      strings.xml (301), colors.xml (24), themes.xml
├── values-night/ colors.xml (5 dark-theme overrides)
├── values-ru-rRU/ strings.xml (301) — full coverage
└── xml/         file_paths.xml, backup_rules.xml, data_extraction_rules.xml
```

**Localization.** Two locales: English (`values/`, the default) and Russian (`values-ru-rRU/`),
301 strings each. In-app language switching (en / ru / system) is stored under the `"language"`
key and applied through `Context.applyLocale(code)` in `MainActivity.attachBaseContext`
plus an Activity restart.

The studio added 53 `studio_*` strings and 3 `settings_card_custom_animations*` ones in
**both** locales. The locale is applied in `CustomAnimationsActivity.attachBaseContext` too,
because that is a separate Activity with its own context.

> [!IMPORTANT]
> `studio_starter_script` is the one string whose **Lua must not be translated** — only its
> comments differ per locale, and `StarterScriptTest` fails the build if the two halves stop
> being byte-for-byte identical.

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
the network, and also what `glyph.net` reads), `RECORD_AUDIO`, `READ_MEDIA_AUDIO`.

> [!NOTE]
> The six `require`d modules added **no permission**. `glyph.net` reads
> `ACCESS_NETWORK_STATE`, which was already declared for the VPN feature and is not a
> runtime permission either; `glyph.sensor` reads the accelerometer, which needs none.

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

**213 unit tests, 22 suites, all pure JVM** — no emulator, no device, no Robolectric. Run in
about 2 seconds.

| Suite | Tests | What it pins |
|-------|-------|--------------|
| `glyph/script/LuaScriptEngineTest` | 22 | A script really runs: drawing, loops, channel groups, `glyph.hold`, `glyph.exit`, syntax and runtime errors, the watchdog against an infinite loop and against `pcall`, interrupting a long `glyph.hold`, the absence of dangerous globals, `validate()`, and `glyph.target` read back by the script and reported in the result |
| `glyph/script/ScriptTargetTest` | 15 | The target classifier: quote styles, spacing, **line and block comments**, unknown values falling back to `ANY`, and which picker offers what |
| `glyph/script/ScriptFileFormatTest` | 7 | `encode`/`decode` round trip, a headerless import, a name with a newline, safe file names, the `custom:` namespace |
| `glyph/script/ScriptValidateResultTest` | 5 | The typed Check verdict: a clean file is `OK` with no message, an unparseable one is `SYNTAX_ERROR`, a typo'd `require` is `MISSING_MODULE` and *not* a syntax error, and a parse failure outranks a module in the same file |
| `glyph/script/ScriptRunResultLogTest` | 5 | The script's own output survives to the caller: `print` lands on the result, `glyph.log` keeps the level it was given, `glyph.log.error` stops the run and its line is still there, a silent run is an empty list rather than `null`, and the lines are read *before* the session is torn down |
| `glyph/script/StarterScriptTest` | 6 | The template the first script anyone ever runs: present in both locales, the Lua halves **byte-for-byte identical**, it calls `require`, it runs on all four supported phones, one pass takes about the same time on each, and the brightness really ramps |
| `glyph/script/ScriptSandboxSecurityTest` | 11 | The sandbox, attacked: no dangerous global is reachable even with `require` installed, `require` refuses a name that is not a string, the `glyph` metatable does not leak the host, neither `pcall` nor `xpcall` swallows the watchdog, spin and `repeat` loops are stopped by the budget, and deep recursion and a metamethod error do not escape the engine |
| `glyph/script/ScriptSandboxResourceTest` | 9 | What the instruction watchdog does not cover: a doubling string and a large `rep` stay bounded, a single log line is unbounded while the line count is capped, one run cannot poison the next one's module table, the strip is blanked whatever the script did, the observable surface is exactly the documented one, and the sensor listener is taken lazily and given back exactly once |
| `glyph/script/module/ModuleRegistryTest` | 7 | A `require` in a **real sandbox** reaches the six modules and nothing else: identity within a run, an unknown name raising with the available list, a fresh table per run — and `io`, `os`, `dofile`, `load`, `loadstring`, `luajava`, `coroutine`, `collectgarbage` all still `nil` with `require` installed |
| `glyph/script/module/ModuleSourceScanTest` | 9 | The scan behind Check: both quote styles, a misspelt name, **a commented-out `require` is not reported**, duplicates collapsed, and a syntax error outranking a module in the same file |
| `glyph/script/module/GlyphTimeModuleTest` | 5 | `minuteOfDay`, the 22:00 and 06:00 edges of `isNight` (half-open, both ways), the three brightness levels, and the clock being re-read on every access |
| `glyph/script/module/GlyphBatteryModuleTest` | 4 | `percent` and `charging` come from the device, `level()` translates a percentage into segments of *this* phone's C strip and never leaves it, and `bar(ms)` draws it |
| `glyph/script/module/GlyphNetModuleTest` | 6 | Every field for a Wi-Fi connection, nothing for a disconnected one, VPN and metered reported correctly, the values are booleans, the state is re-read on every access, and `require` is still the same table twice |
| `glyph/script/module/GlyphSensorModuleTest` | 13 | The five fields as numbers and a boolean, re-read every time — and the whole lifecycle: `require` starts nothing, the first read registers once, the run gives the sensor back on **every** exit path including the watchdog abort, and a run that never read it releases nothing |
| `glyph/script/module/GlyphLogModuleTest` | 4 | `info` and `warn` let the run continue, `error` raises and stops it, the level is reported exactly once, and a failure is blamed on the module that raised it rather than with a doubled prefix |
| `glyph/script/module/GlyphUtilModuleTest` | 11 | `clamp` (including a reversed range), `lerp` held at both ends, `mapRange` over a reversed and over a degenerate range, `shuffle` keeping every channel and leaving the original alone, and `shuffle` refusing a non-table |
| `glyph/audio/FftTest` | 17 | Energy lands in the right bin, silence is exact zeros, magnitude is linear in amplitude, the size is a power of two, cached plans do not interfere |
| `glyph/audio/AudioFrameTest` | 13 | `bandsInto` peaks rather than averages, no band is lost at any segment count, the caller's array is the one written, `isSilent` against the floor, locale-independent `toString()` |
| `glyph/audio/BeatDetectorTest` | 12 | The rolling average, the cooldown, a constant level not beating forever, `reset()` between tracks |
| `glyph/audio/AudioAnalysisTest` | 6 | The calibration every mode's brightness rests on: a note lands in its band, silence produces nothing, a quiet room still reads as silence |
| `glyph/audio/MusicVisualizationModeTest` | 11 | Stored ids resolve, a `custom:<uuid>` script id is **not** a mode, and every mode is classified as idle-friendly or not |
| `glyph/device/DeviceProfileFactoryTest` | 15 | The per-model channel tables: every group names wired channels, nothing is wired twice, the budgets progress with the hardware, and Phone (2)'s second C run stays out of the battery bar |

The suites are grouped by **what breaks silently**, not by package. Every one of them covers
code whose failure mode is a strip that looks plausible and is wrong:

> [!IMPORTANT]
> A wrong constant in the FFT, an off-by-one in a channel range, a missed band edge or a
> locale-dependent number all produce output that renders fine and cannot be told apart
> from correct by looking at it. That is what these tests are for, and it is why they
> assert on known signals rather than on internals.

`ModuleRegistryTest` deserves its own note: every script in it is run **through the real
engine**, not by calling `ModuleRegistry` directly. The claim under test is not "the map
has six entries" — it is that a `require` in a sandbox with the whole escape hatch shut
reaches those six and nothing else, which a test that skipped the sandbox would pass
with `io` and `luajava` wide open.

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
- `require` **is** in `BANNED_GLOBALS`, and is then deliberately overwritten by
  `ModuleRegistry.requireFor(session)` immediately after the ban loop. Leaving it on the
  list is deliberate: the list is an honest record of what *Lua's* `require` can reach
  (the file system, via `package.path`), and taking it off would read as "scripts cannot
  require", which stopped being true. The replacement resolves six in-memory names and
  nothing else — no search path, no `package`.
- `ModuleRegistry` holds `(ScriptSession) -> LuaTable` **factories**, never built tables.
  Several foreground services run scripts at once — charging and the music visualiser
  routinely are — and a shared table would let those two runs read and write each other's
  state. A fresh table per run is the whole of the isolation; the `require` cache itself is
  a local inside `requireFor`, not a field, for the same reason.
- Every module field is resolved through an `__index` metamethod on **every read**, never
  snapshotted at build time. The two mistakes `ModuleBuilder` exists to prevent are a
  moving value stored as a plain field, and a function bound without a prefix so a failure
  blames `glyph.clamp` when the author called `glyph.util.clamp`.
- `glyph.sensor` starts its listener on the **first field read**, not in `build`. Registering
  in `require` would make the *name* of the feature the expensive thing — a 50 Hz listener
  held for the whole run on any animation that so much as mentions the module. The
  matching release is `ScriptSession.close()`, called from the engine's `finally` on every
  exit path including the watchdog abort.
- `ScriptRunResult.logLines` is read from the session inside `run()`, on the way out. The
  `finally` immediately after closes the session and clears the engine's active slot, so a
  caller that asked for the lines after `run` returned would race that teardown and get an
  empty list on exactly the runs that mattered — the ones that failed halfway through
  logging.
- `ScriptRunner.check(source)` returns `ScriptCheckResult.OK` when the connected phone's
  profile is unknown: there is nothing to compile against, and the Check button then says
  nothing. The typed result is the same honest "no complaint" it returned before.
- `ModuleSourceScan` reuses `ScriptTarget.BLOCK_COMMENT` / `LINE_COMMENT` rather than
  retyping them. A script the pickers classify correctly and Check then rejects would be a
  far more confusing bug than a duplicated pair of regexes.
- `ModuleRegistry.requireFor` is written out longhand rather than going through
  `GlyphLuaApi.luaFunction`, because that wrapper catches `Exception` and `LuaError` *is* a
  `RuntimeException` — the error raised inside would be caught by the very wrapper that
  raised it, and the author would read the message twice. `GlyphLogModule.error` has the
  same reason for throwing a plain exception rather than calling `LuaValue.error`.
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
- The two tiles are declared `android:exported="true"` with
  `android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"`. Neither is a typo:
  without `exported="true"` the tile never appears in the add-tile list, and
  `BIND_QUICK_SETTINGS_TILE` keeps the service reachable only by the system.
  `GlyphServiceTileService` carries no `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` because it draws
  nothing and holds no service of its own.

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
light channels, wait, read the device's own state through six `require`d modules, and write
lines to the studio console.

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
| **Check** | Compiles the source, drawing nothing, and reports the first thing wrong with it: a syntax error, **or** a `require` naming a module this build does not ship | Semantics: `glyph.group('nope')` is not caught |
| **Glyph** | Sends the same source through `GlyphAnimationManager.previewScript` — the very path the feature services use | — |

There is deliberately no on-screen run mode: a schematic of the LED layout is not the same
as the phone lighting up, and a second "run" button invites mistaking one for the other.
Check the file, then play it on the glyph — a phone with no Glyph interface, or a session
that was never opened, says so in the console rather than looking like a script that
silently did nothing.

> [!NOTE]
> Check's two findings are reported in a fixed order, and the order is the point: a file
> that does not compile is reported **first and alone**. A file that is not valid Lua has
> no meaningful `require` in it — the names inside it are noise — and telling the author
> about a module before telling them the file cannot be read sends them looking in the
> wrong place. The two also get different words in the console, because reporting a typo as
> a syntax error sends the author hunting for a bracket that was never missing.

While a run is in flight the second button turns into **Stop**. The only ceiling on a run is
`ScriptAnimation.SAFETY_CAP_MS = 30_000L`, which catches a script that never returns; normal
scripts end when their own code ends. Under the buttons sits the console (last 4 lines),
and under the code field one collapsible `glyph API reference` cheat sheet: the `glyph`
calls first, then — under their own heading — `require` and the six modules.

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
| `glyph.batteryBar([percent], [ms])` | Fills the C strip like charging does. Percent defaults to `glyph.battery`, duration to 2000 ms |

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

| Read | Kind | Meaning |
|------|------|---------|
| `glyph.elapsed()` | function | Milliseconds since the run started |
| `glyph.frame()` | function | How many frames have been drawn |
| `glyph.battery` | **value** | Charge level, `0..100` |
| `glyph.charging` | **value** | Whether the phone is charging |
| `glyph.running` | **value** | `false` once the animation has been stopped. The basis of a `while glyph.running do` loop |

> [!WARNING]
> `glyph.battery`, `glyph.charging` and `glyph.running` are **values** and take no
> parentheses. The opposite mistake is the expensive one: `while glyph.running() do` reads
> a *function* every iteration, and a function is truthy in Lua, so the loop can never end
> on the stop request and the run is killed by the watchdog's wall-clock limit instead. The
> correct form is `while glyph.running do`.
>
> `glyph.elapsed()`, `glyph.frame()` and `glyph.batteryBar()` really are functions — they
> take no argument, but they are bound as functions, and the editor's cheat sheet writes
> them with parentheses. `glyph.MAX` and `glyph.device` are plain values too.

> [!NOTE]
> **`glyph.time()` is now `glyph.elapsed()`.** It still returns the milliseconds since the
> run started — that is unchanged — but it is no longer called *time*, because the
> `glyph.time` module now owns the wall clock and two different things called "time" on one
> table is a name an author cannot hold in their head. `glyph.time()` still works as an
> alias, because these scripts are saved to the user's storage and nothing is going to
> re-save them; the first call in a run writes one WARN line to the console and no more.

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

> [!NOTE]
> What a script writes really does reach the console: the run carries its own lines back
> with the outcome, script first, so a `print(...)` trail reads in the order it was written.
> The severity lives in the **`glyph.log` module**, not in a field of the `glyph` table:
> `glyph.log` is a function, so `glyph.log.info` would fail with *attempt to index a
> function value*. The working path is `require("glyph.log").info` / `.warn` / `.error`,
> and `error` stops the run as well as writing the line.

> [!WARNING]
> The console colours WARN with the `tertiary` role, because the app's palette has no amber —
> and `tertiary` is **green** in the AMOLED and classic schemes. That is a known rough edge,
> not a design decision, and a green warning is a weak signal on its own: read the text, not
> just the colour. INFO is `onSurfaceVariant` and ERROR is `error`, both of which do read as
> themselves.

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
| `glyph.target = "any"` means the same to the pickers as declaring nothing | The explicit name reads better in the source, and `ScriptRunResult.target` then reports `ANY` where no declaration reports `null` |
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
| `glyph.audio.bands(n)` | A table of `n` values in `0..1`, resampled from the 32 analysed bands. `n` defaults to 8 and is clamped to `1..256` |

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

#### Modules — `require` and the six names

`require` is the one door the sandbox shut, and it is opened again — just, and only onto a
fixed list of six names. **There is no file system and no `package.path` behind it.** A name
either names a module this build ships, or the script gets an error that says which ones
it could have used.

```lua
local time = require("glyph.time")   -- bind it to a local; that is the whole idiom
```

| `require` name | What it gives |
|----------------|---------------|
| `glyph.time` | The wall clock: `hour`, `minute`, `minuteOfDay`, `isNight`, and the constants `DAY` / `DUSK` / `NIGHT` |
| `glyph.battery` | Charge: `percent`, `charging`, and `level()` / `bar(ms)` |
| `glyph.net` | Network: `connected`, `wifi`, `metered`, `vpn` |
| `glyph.sensor` | Movement: `x`, `y`, `z`, `magnitude`, `shaken` |
| `glyph.log` | The console, with severities: `info`, `warn`, `error` |
| `glyph.util` | Pure arithmetic: `clamp`, `lerp`, `mapRange`, `shuffle` |

Each in full:

| Module | Read | Kind | Meaning |
|--------|------|------|---------|
| `glyph.time` | `hour` | value | Hour of the day, `0..23`, in the device's own time zone |
| | `minute` | value | Minute of the hour, `0..59` |
| | `minuteOfDay` | value | Minutes since midnight, `0..1439` |
| | `isNight` | value | `true` from **22:00 up to 06:00** |
| | `DAY` / `DUSK` / `NIGHT` | constants | `4000` / `2200` / `0` — 100 %, 55 %, off |
| `glyph.battery` | `percent` | value | Charge, `0..100` |
| | `charging` | value | Whether the phone is on power |
| | `level()` | function | The percentage translated into **this phone's** segment count on the C strip, floored |
| | `bar(ms)` | function | Draws that bar. Same as `glyph.batteryBar`, default 2000 ms |
| `glyph.net` | `connected` | value | Some network is carrying traffic |
| | `wifi` | value | That network is Wi-Fi |
| | `metered` | value | The user is billed for the bytes, however they arrive |
| | `vpn` | value | The active network is a VPN |
| `glyph.sensor` | `x`, `y`, `z` | values | Acceleration along the short, long and screen-normal axes, **m/s², gravity included** |
| | `magnitude` | value | How hard, in m/s², whatever the direction — the number a tilt test actually wants |
| | `shaken` | value | `true` on the sample that passed the shake threshold |
| `glyph.log` | `info(…)` | function | An ordinary line |
| | `warn(…)` | function | A line the author should look at; the run continues |
| | `error(…)` | function | A line, **and then the run stops** |
| `glyph.util` | `clamp(v, lo, hi)` | function | `v` held inside the bounds; a reversed pair is swapped, not rejected |
| | `lerp(a, b, t)` | function | How far along `a` → `b` the position `t` is; `t` is held to `0..1` |
| | `mapRange(v, inLo, inHi, outLo, outHi)` | function | `v` from one range onto another; handles a reversed or a degenerate input range |
| | `shuffle(table)` | function | A **shuffled copy** of a Lua array, from the session's own generator |

A few rules cover almost everything a script author will trip over.

**Every field is live.** `hour`, `percent`, `vpn`, `magnitude` — all of them are resolved
on *every read* through an `__index` metamethod, not frozen when the module was built. A
snapshot would be worse than useless, because the whole reason to ask is a reaction: a loop
that dims at dusk, a bar that calms down when Wi-Fi returns. A value frozen at build time
would look like it worked right up until the thing changed.

> [!NOTE]
> The exception is where a module takes an argument: `level()`, `bar(ms)`,
> `mapRange(…)` and the other three `glyph.util` functions are functions precisely because
> something goes in. That is the same rule as `glyph.audio.bands(n)`, and it is worth
> checking which side of the line a name falls on before writing `while` around it.

**Two runs never share a module.** The registry holds *factory functions*
`(ScriptSession) -> LuaTable`, never built tables, and every run gets a fresh table. Several
foreground services run scripts at the same time — the charging animation and the music
visualiser routinely are — and a table built once and shared would let those two runs read
and write each other's state. `require("glyph.util") == require("glyph.util")` inside one
run, because a script is entitled to the caching identity Lua gives it; across two runs, a
different table entirely.

**The clock, the network and the accelerometer are one snapshot each.** Two *different*
fields are still two samples, but a single value is internally consistent: `magnitude` is
computed from the same `SensorEvent` as `x`, `y` and `z`, and the four `glyph.net` booleans
come from one read. A phone leaving a metered hotspot for home Wi-Fi passes through a moment
where the transport is already Wi-Fi but the metered flag has not cleared, and four
independent queries can hand a script a combination that never existed for any measurable
period.

**Nothing here adds a trigger, a service or a permission.** A module is read *inside* a run
that an existing feature has already woken — Pulse Lock, NFC, the charging animation, VPN
connected, screen off, low battery, the music visualiser. There is no ninth service
waiting on "the script said it was night", and no new permission:

| Module | Permission story |
|--------|------------------|
| `glyph.sensor` | **None.** The accelerometer needs no runtime permission on Android |
| `glyph.net` | **None.** Reading network *state* needs no runtime permission; `ACCESS_NETWORK_STATE` is already declared for the VPN feature. The module opens no socket, so it cannot become a way for a script to reach the network |
| the other four | Nothing to declare — the data is already in the process |

**A script that answers the time of day** — the shape most people reach a script for, and
the one the editor's second cheat-sheet card shows:

```lua
local time = require("glyph.time")

-- 22:00–06:00 the strip stays dark, 12:00–22:00 it dims,
-- 06:00–12:00 it runs at full brightness.
local level = time.DAY
if time.hour >= 12 then level = time.DUSK end
if time.isNight then level = time.NIGHT end

if level == 0 then
  glyph.off()
  return
end

glyph.setAll(level)
```

#### What `require` is not

> [!IMPORTANT]
> `require("os")`, `require("io")` and `require("anything-else")` all **raise**. There is
> no fallback to the file system, no search path, and no `package` to configure one with:
> `package`, `dofile`, `loadfile`, `load`, `loadstring`, `luajava`, `coroutine`,
> `collectgarbage`, `newproxy` and `debug` are all still `nil`. The `require` that exists
> opens one known room — six tables in memory — and none of the others.

The error names what *is* available, because the overwhelmingly likely cause is a typo and
the fix is to read the right name off the message rather than to work out which half of
`require` is broken:

```
require: no module 'glyph.nett' (available: glyph.battery, glyph.log, glyph.net,
glyph.sensor, glyph.time, glyph.util)
```

> [!TIP]
> **Check catches that before you do.** Compiling proves nothing about `require` —
> `require("glyph.nett")` is a perfectly valid expression, so the typo survives a clean
> parse and then blows up on the real glyph, mid-animation, with a phone in someone's hand.
> `ModuleSourceScan` strips the Lua comments (reusing the same regexes
> `ScriptTarget.detectIn` uses) and reports the first name the registry does not have, so
> the Check button says *Module problem* rather than *Syntax error*. A commented-out
> `require` is deliberately not reported: a checker that cries wolf about a line the author
> already switched off gets switched off and never used again.

> [!WARNING]
> There is **no** `glyph.ui` module. It was discussed and deliberately left out. There is
> also no store of shareable animations, no `format:` version checking, no
> device-compatibility metadata in a script header and no `--#if` preprocessor — none of
> those exist, and a script that looks for them will not find them.

### Examples

**A simple wave along the strip** — this is the starter template, verbatim. The comments are
localised; the Lua below is byte-for-byte identical in every locale:

```lua
-- A wave along the long C strip.
-- The whole file is the animation: write plain Lua here.
-- It plays once, and stops when the code runs out.
-- Try changing the 600, or the two numbers inside util.lerp.

local util = require("glyph.util")

local strip = glyph.ch.c
local n = #strip

-- The C strip is 4 segments on a Phone (1) and 24 on a Phone (2a), so the
-- step is worked out from its length: one pass takes about the same time on
-- every phone. A fixed 60 ms would crawl on the small one and blur on the big
-- one.
local step = math.max(25, math.floor(600 / n))

-- util.lerp gives every segment its own brightness, so the wave has a head
-- and a tail instead of every segment being equally bright.
for i = 1, n do
  glyph.set({ strip[i] }, util.lerp(1000, glyph.MAX, i / n), step)
end

-- Fade the whole strip out, one segment at a time.
for i = n, 1, -1 do
  glyph.set({ strip[i] }, util.lerp(1000, glyph.MAX, i / n), step)
end
```

> [!NOTE]
> The step is *derived*, not written down, and that is the whole portability argument: a
> fixed 60 ms would crawl on a 4-segment Phone (1) and blur on a 24-segment Phone (2a).
> Dividing by `#strip` puts one pass at 1200 ms on three models and 1184 ms on the fourth.
> `math.max(25, …)` is the floor, because a 24-segment strip would otherwise ask for a
> 25 ms step and a longer one for a 4-segment strip would be unusably fast without it.

**Advanced: a battery bar that reacts to the real charge level, with easing and random
flickers.** This is the same job done with `require("glyph.battery")` — `level()` is the
translation from a percentage to *this phone's* segment count, which is the one thing the
flat API could not do:

```lua
-- A battery bar that looks at the real charge level.
local battery = require("glyph.battery")

glyph.seed(42)                       -- the pattern repeats from run to run

local strip = glyph.ch.c
local n = #strip
local step = 70

local function bar()
  local lit = math.min(n, battery.level())
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

while glyph.running do
  if battery.percent >= 100 then
    glyph.spiral(1, 40, glyph.MAX)   -- full charge: a spiral
  else
    bar()
  end

  if battery.charging and glyph.rnd(1, 100) > 60 then
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
| `load`, `loadstring`, `dofile`, `loadfile`, `module` | Compiling or loading code at runtime |
| `package` | Module loading — and the only thing that could give `require` a search path |
| `require` | Lua's own, which searches the file system. **Blanked like the rest, then deliberately replaced** — see below |
| `coroutine` | The hook state lives on the thread, so a fresh coroutine would run unchecked |
| `collectgarbage` | A script could stall the VM |
| `newproxy` | The same class of escape through userdata |
| `debug` | `debug.sethook()` would remove the watchdog |

> [!IMPORTANT]
> **`require` is on that list and is not a contradiction.** It is blanked in the same loop
> as everything else and then immediately overwritten with
> `ModuleRegistry.requireFor(session)`, which resolves the six names in
> [the modules section](#modules--require-and-the-six-names) and **nothing else** — no
> search path, no `package`, no file system. Leaving it on the ban list is deliberate: the
> list stays an honest record of what *Lua's* `require` can reach. Take it off and the
> table would read "scripts cannot require", which stopped being true.
>
> `package`, `dofile`, `loadfile`, `load`, `loadstring`, `luajava`, `coroutine`,
> `collectgarbage`, `newproxy` and `debug` all stay `nil` — permanently, and with
> `require` installed. `ModuleRegistryTest` runs scripts through the real engine to prove
> exactly that.

Two more suites put the sandbox under a load, and both go through the real
`LuaScriptEngine`: `ScriptSandboxSecurityTest` (11) attacks it — no dangerous global is
reachable even with `require` installed, the `glyph` metatable does not leak the host, and
neither `pcall` nor `xpcall` swallows the watchdog; `ScriptSandboxResourceTest` (9) goes
after what an instruction watchdog cannot see — a string doubling on every line, a large
`rep`, the number of log lines, a strip left half-lit, and a sensor listener that has to be
taken lazily and handed back exactly once.

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
| `glyph/script/LuaScriptEngine.kt` | Sandbox assembly, the watchdog, `run()`, `validate()`, `ScriptStatus`, `ScriptCheckStatus`, `ScriptCheckResult` |
| `glyph/script/ScriptSession.kt` | Per-run state: deadline, instruction budget, frame counter, randomness, interruptible waits, the collected log lines, the sensor latch |
| `glyph/script/GlyphLuaApi.kt` | The `glyph` table — the whole user-facing language |
| `glyph/script/ScriptTarget.kt` | `ScriptTarget` (the declaration) and `ScriptScope` (which picker offers what) |
| `glyph/script/LogLevel.kt` | `LogLevel` (`INFO` / `WARN` / `ERROR`) and `ScriptLogLine` — the severity a line was written at |
| `glyph/script/ScriptRunner.kt` | `runScript()` / `stop()` / `check()` plus the private `RendererHost` bridging the suspending renderer to a blocking host |
| `glyph/script/module/ModuleRegistry.kt` | The six names, the factories behind them, and the `require` that reads them |
| `glyph/script/module/ModuleBuilder.kt` | `value` / `live` / `func` — how one module table is assembled without freezing a moving value or losing the error prefix |
| `glyph/script/module/ModuleSourceScan.kt` | The `require("…")` names Check must reject before a phone does |
| `glyph/script/module/LuaArgs.kt` | Positional arguments for module functions: a missing slot is `nil`, and `nil` means "use the default" |
| `glyph/script/module/Glyph*Module.kt` | One file per module: `GlyphTimeModule`, `GlyphBatteryModule`, `GlyphNetModule`, `GlyphSensorModule`, `GlyphLogModule`, `GlyphUtilModule` |
| `glyph/net/NetworkSnapshot.kt` | `connected` / `wifi` / `metered` / `vpn`, guaranteed to describe the same instant |
| `glyph/net/NetworkSource.kt` | `ConnectivityManager` behind a 1,000 ms cache |
| `glyph/sensor/SensorSnapshot.kt` | `x` / `y` / `z` / `magnitude` / `shaken`, one sample, gravity included |
| `glyph/sensor/SensorControl.kt` | What opens and closes the listener for one run — separate from reading a value, so a test of the first need not provide the second |
| `glyph/sensor/SensorSource.kt` | The listener itself, and the threshold that counts as a shake |
| `data/CustomAnimationRepository.kt` | Files, index, import, both exports, the starter script |
| `CustomAnimationsActivity.kt` | The studio Activity, the file pickers, the `BackHandler` |
| `ui/screens/animations/*.kt` | List and editor, plus the editor cheat sheet — `API_REFERENCE` and, under its own heading in the same card, `MODULE_REFERENCE` |
| `ui/viewmodel/AnimationStudioViewModel.kt` | Studio state, Check / Glyph, the console |
| `app/src/test/.../glyph/script/*Test.kt` | 22 + 15 + 7 + 5 + 5 + 6 + 9 + 11 unit tests; run with `./gradlew :app:testDebugUnitTest` |
| `app/src/test/.../glyph/script/module/*Test.kt` | 7 + 9 + 5 + 4 + 6 + 13 + 4 + 11 unit tests over the registry, the source scan and the six modules |
| `app/src/test/.../glyph/audio/*Test.kt` | 17 + 13 + 12 + 6 + 11 unit tests over the transform, the frame, the beat detector and the calibration |
| `app/src/test/.../glyph/device/DeviceProfileFactoryTest.kt` | 15 unit tests over the per-model channel tables |

---

*This documentation reflects the code at version 1.0.31. When the service layer changes,
update [section 7](#7-adding-a-new-service-quickstart); when you add a call to the `glyph`
table **or a module under `glyph/script/module/`**, update [section 14](#14-custom-animations-lua) —
the `API_REFERENCE` and `MODULE_REFERENCE` lists in
`ui/screens/animations/AnimationEditorScreen.kt` are the same list a third time.*

