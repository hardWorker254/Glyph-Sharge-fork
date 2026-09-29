# Glyph-Sharge UI Components Documentation

Complete reference guide for all UI elements, components, and styling systems.

---

## Table of Contents

1. [Overview](#overview)
2. [Theme System](#theme-system)
3. [Core Card Components](#core-card-components)
4. [Custom Animations & Visual Effects](#custom-animations--visual-effects)
5. [Specialized Components](#specialized-components)
6. [Animation Studio (Lua)](#animation-studio-lua)
7. [Utility Systems](#utility-systems)
8. [Usage Examples](#usage-examples)
9. [Integration Guide](#integration-guide)

---

## Overview

**Glyph-Sharge** is a Material 3 Jetpack Compose Android application with comprehensive UI components for Nothing Phone glyph interface management. The `ui/` package is ~8,100 lines of production Compose code.

**Technology Stack:**
- Jetpack Compose (Material Design 3)
- Kotlin
- Hilt 2.50 for dependency injection
- Hardware-accelerated haptic feedback
- Dynamic theming system
- LuaJ 3.0.1 for the user-written animation studio (see [Animation Studio (Lua)](#animation-studio-lua))

---

## Theme System

### 1. Color System (`app/src/main/java/com/bleelblep/glyphsharge/ui/theme/Color.kt`)

```kotlin
// App Primary Colors
val GlyphZenRed = Color(0xFFd71921)
val GlyphZenRedDark = Color(0xFFa01419)

// Nothing Phone Colors
val NothingRed = Color(0xFFD71921)
val NothingGray = Color(0xFF2D2D2D)
val NothingGreen = Color(0xFF00FF00)
val NothingWhite = Color(0xFFFFFFFF)
val NothingBlack = Color(0xFF000000)
val NothingViolate = Color(0xFF674FA3)

// Light Theme Colors
val NothingLightBackground = Color(0xFFF5F5F5)
val NothingLightSurface = Color(0xFFFFFFFF)

// Dark Theme Colors
val NothingDarkBackground = Color(0xFF121212)
val NothingDarkSurface = Color(0xFF1E1E1E)
```

### 2. Shape System (`app/src/main/java/com/bleelblep/glyphsharge/ui/theme/Shapes.kt`)

Corners are picked per theme style. Y2K and Neon use tight corners, Expressive uses asymmetric ones, everything else falls back to the Material 3 defaults:

```kotlin
internal fun getShapes(themeStyle: AppThemeStyle): M3Shapes = when (themeStyle) {
    AppThemeStyle.EXPRESSIVE -> M3Shapes(
        extraSmall = RoundedCornerShape(topStart = 8.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 8.dp),
        small = RoundedCornerShape(topStart = 16.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 16.dp),
        medium = RoundedCornerShape(topStart = 24.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 24.dp),
        large = RoundedCornerShape(topStart = 32.dp, topEnd = 36.dp, bottomStart = 36.dp, bottomEnd = 32.dp),
        extraLarge = RoundedCornerShape(topStart = 40.dp, topEnd = 44.dp, bottomStart = 44.dp, bottomEnd = 40.dp)
    )
    AppThemeStyle.Y2K -> M3Shapes(extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(4.dp), medium = RoundedCornerShape(6.dp), large = RoundedCornerShape(8.dp), extraLarge = RoundedCornerShape(12.dp))
    AppThemeStyle.NEON -> M3Shapes(extraSmall = RoundedCornerShape(0.dp), small = RoundedCornerShape(2.dp), medium = RoundedCornerShape(4.dp), large = RoundedCornerShape(8.dp), extraLarge = RoundedCornerShape(12.dp))
    else -> M3Shapes()
}
```

### 3. Typography System (`app/src/main/java/com/bleelblep/glyphsharge/ui/theme/Type.kt`)

Every size is the Material 3 metric multiplied by the matching scale from `FontSizeSettings`, so the font settings screen retypes the whole app without touching a single call site:

```kotlin
fun createTypography(
    titleFont: FontFamily,
    bodyFont: FontFamily,
    fontSizeSettings: FontSizeSettings = FontSizeSettings()
) = Typography(
    displayLarge = TextStyle(
        fontFamily = titleFont,
        fontWeight = FontWeight.Normal,
        fontSize = (57 * fontSizeSettings.displayScale).sp,
        lineHeight = (64 * fontSizeSettings.displayScale).sp,
        letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = titleFont,
        fontWeight = FontWeight.Normal,
        fontSize = (32 * fontSizeSettings.titleScale).sp,
        lineHeight = (40 * fontSizeSettings.titleScale).sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = titleFont,
        fontWeight = FontWeight.Normal,
        fontSize = (22 * fontSizeSettings.titleScale).sp,
        lineHeight = (28 * fontSizeSettings.titleScale).sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = bodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = (16 * fontSizeSettings.bodyScale).sp,
        lineHeight = (24 * fontSizeSettings.bodyScale).sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = bodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = (12 * fontSizeSettings.labelScale).sp,
        lineHeight = (16 * fontSizeSettings.labelScale).sp,
        letterSpacing = 0.5.sp
    )
    // … displayMedium/displaySmall, headlineMedium/headlineSmall,
    // titleMedium/titleSmall, bodyMedium/bodySmall, labelLarge/labelSmall
)
```

`app/src/main/java/com/bleelblep/glyphsharge/ui/theme/Typography.kt` picks between that and the Expressive variant:

```kotlin
internal fun getTypography(
    themeStyle: AppThemeStyle,
    fontState: FontState? = null
): Material3Typography = when (themeStyle) {
    AppThemeStyle.EXPRESSIVE -> { /* fixed Material 3 expressive metrics */ }
    else -> createTypography(
        titleFont = fontState?.getTitleFont() ?: FontFamily.Default,
        bodyFont = fontState?.getBodyFont() ?: FontFamily.Default,
        fontSizeSettings = fontState?.fontSizeSettings ?: FontSizeSettings()
    )
}
```

### 4. Font State Management (`app/src/main/java/com/bleelblep/glyphsharge/ui/theme/FontState.kt`)

The custom font system with the official Nothing fonts. `FontState` is a `@Singleton` injected with the settings store, and it seeds itself from that store on construction — the state is a cache of the persisted choice, never the source of truth:

```kotlin
enum class FontVariant {
    HEADLINE,    // NType Headline (official Nothing font)
    NDOT,        // NDot 57 Caps (official Nothing font)
    SYSTEM       // Default system font
}

enum class FontCategory { DISPLAY, TITLE, BODY, LABEL }

data class FontSizeSettings(
    val displayScale: Float = 1.0f,
    val titleScale: Float = 1.0f,
    val bodyScale: Float = 1.0f,
    val labelScale: Float = 1.0f
) {
    companion object {
        fun getDefaultForFont(fontVariant: FontVariant): FontSizeSettings
    }
}

@Singleton
class FontState @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    var currentVariant by mutableStateOf(settingsRepository.getFontVariant())
        private set
    var useCustomFonts by mutableStateOf(settingsRepository.getUseCustomFonts())
        private set
    var fontSizeSettings by mutableStateOf(settingsRepository.getFontSizeSettingsForFont(currentVariant))
        private set

    // Official Nothing fonts
    val headlineFont = FontFamily(
        Font(R.font.ntype_82_headline, FontWeight.Normal),
        Font(R.font.ntype_82_headline, FontWeight.Medium),
        Font(R.font.ntype_82_headline, FontWeight.Bold)
    )

    val ndotFont = FontFamily(
        Font(R.font.ndot55caps, FontWeight.Normal),
        Font(R.font.ndot55caps, FontWeight.Medium),
        Font(R.font.ndot55caps, FontWeight.Bold)
    )

    val regularFont = FontFamily(
        Font(R.font.ntype_82_regular, FontWeight.Normal),
        Font(R.font.ntype_82_regular, FontWeight.Medium),
        Font(R.font.ntype_82_regular, FontWeight.Bold)
    )

    val systemFont = FontFamily.Default

    fun setFontVariant(variant: FontVariant)
    fun toggleCustomFonts()
    fun updateFontSize(category: FontCategory, scale: Float)
    fun resetFontSizes()
    fun getTitleFont(): FontFamily
    fun getBodyFont(): FontFamily
    fun getDisplayFont(): FontFamily
    fun getFontDescription(): String
}
```

**Fonts in use:** `ntype_82_headline.otf` and `ntype_82_regular.otf` for HEADLINE, `ndot55caps.otf` for NDOT, and the platform default for SYSTEM.

### 5. App Theme Styles (`app/src/main/java/com/bleelblep/glyphsharge/ui/theme/ThemeState.kt`)

**6 Complete Theme Configurations:**

```kotlin
enum class AppThemeStyle {
    CLASSIC,      // Clean, standard Material 3 theme
    Y2K,          // Chrome, cyber, futuristic aesthetic
    NEON,         // High contrast electric colors
    AMOLED,       // True black with minimal design
    PASTEL,       // Soft, dreamy colors
    EXPRESSIVE    // Vibrant, bold Material 3 expressive
}

@Singleton
class ThemeState @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    val isDarkTheme: Boolean get() = _isDarkTheme
    val themeStyle: AppThemeStyle get() = _themeStyle

    fun toggleTheme()
    fun setDarkTheme(darkMode: Boolean)
    fun setThemeStyle(style: AppThemeStyle)
}
```

Each style has a light and a dark `ColorScheme` in
`app/src/main/java/com/bleelblep/glyphsharge/ui/theme/ColorSchemes.kt`, selected by:

```kotlin
internal fun getColorScheme(themeStyle: AppThemeStyle, isDark: Boolean): ColorScheme =
    when (themeStyle) {
        AppThemeStyle.CLASSIC    -> if (isDark) ClassicDarkColorScheme    else ClassicLightColorScheme
        AppThemeStyle.Y2K        -> if (isDark) Y2KDarkColorScheme        else Y2KLightColorScheme
        AppThemeStyle.NEON       -> if (isDark) NeonDarkColorScheme       else NeonLightColorScheme
        AppThemeStyle.AMOLED     -> if (isDark) AmoledDarkColorScheme     else AmoledLightColorScheme
        AppThemeStyle.PASTEL     -> if (isDark) PastelDarkColorScheme     else PastelLightColorScheme
        AppThemeStyle.EXPRESSIVE -> if (isDark) ExpressiveDarkColorScheme else ExpressiveLightColorScheme
    }
```

`GlyphZenTheme` (`app/src/main/java/com/bleelblep/glyphsharge/ui/theme/GlyphZenTheme.kt`) is the composable that ties colour, type and shapes together, and it is also where `LocalFontState` and `LocalThemeState` are provided.

**Example Theme - Y2K Dark:**

```kotlin
val Y2KDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00D4FF),         // Cyan
    onPrimary = Color(0xFF000F1A),
    primaryContainer = Color(0xFF0077B5),
    onPrimaryContainer = Color(0xFFBBEBFF),

    secondary = Color(0xFFFF0099),       // Magenta
    onSecondary = Color(0xFF1A0014),
    secondaryContainer = Color(0xFFCC0077),
    onSecondaryContainer = Color(0xFFFFB3E0),

    tertiary = Color(0xFF00FF41),        // Lime Green
    onTertiary = Color(0xFF001A0A),
    tertiaryContainer = Color(0xFF00CC33),
    onTertiaryContainer = Color(0xFFB3FFD1),

    background = Color(0xFF000B14),
    onBackground = Color(0xFF00D4FF),
    surface = Color(0xFF001629),
    onSurface = Color(0xFF00D4FF),

    surfaceVariant = Color(0xFF1A2B3D),
    onSurfaceVariant = Color(0xFF66B3E0),
    outline = Color(0xFF0099CC),
    outlineVariant = Color(0xFF003D5C),
    scrim = Color(0xFF000000),

    error = Color(0xFFFF0066),
    onError = Color(0xFF1A0014),
    errorContainer = Color(0xFFCC0052),
    onErrorContainer = Color(0xFFFFB3D1),

    surfaceTint = Color(0xFF00D4FF)
)
```

### 6. Composition Locals (`app/src/main/java/com/bleelblep/glyphsharge/ui/theme/LocalSettings.kt`)

This is how a card, a dialog or a settings section gets the settings store: it reads it from the composition instead of taking it as a parameter, so no caller has to thread it down and the component stays callable on its own.

```kotlin
// LocalSettings.kt
val LocalSettingsRepository = staticCompositionLocalOf<SettingsRepository> {
    error("SettingsRepository should be provided at the composition root")
}

val LocalVibrationIntensity = staticCompositionLocalOf { DEFAULT_VIBRATION_INTENSITY } // 0.66f
```

Both are provided once per Activity, in `MainActivity.setupUI` and in
`CustomAnimationsActivity.setContent`:

```kotlin
CompositionLocalProvider(
    LocalSettingsRepository provides settingsRepository,
    LocalVibrationIntensity provides settingsRepository.getVibrationIntensity(),
) {
    // every screen below reads what it needs from the composition
}
```

- **`LocalSettingsRepository`** — the settings store. The default throws rather than returning a stub, because a screen that read a placeholder would quietly write the user's settings into nothing.
- **`LocalVibrationIntensity`** — the user's 0..1 haptic strength. It is kept apart from the store because `HapticUtils` is a plain object called from click handlers, and a handler cannot read a repository. Reading the float in a composable and handing it to `HapticUtils` as an ordinary argument is what makes that work.

`LocalFontState` and `LocalThemeState` come from `GlyphZenTheme.kt` and are provided by the theme block itself; both throw if read outside one.

---

## Core Card Components

Every card in the app is built from one base composable, `ContentCard`. It is the only place the shared press animation, the haptics call and the theme-aware shape live, so a new card variant is a layout decision rather than a new set of animations.

### 1. ContentCard - Base Card Component

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/components/cards/ContentCards.kt`

```kotlin
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
)
```

**Features:**
- Animated press scale (0.98f, `Spring.DampingRatioHighBouncy`)
- Haptic feedback on click, at the user's own intensity from `LocalVibrationIntensity`
- `surfaceContainer` under EXPRESSIVE, `surface` otherwise
- Cut corners under EXPRESSIVE, `MaterialTheme.shapes.large` otherwise

**Usage Example:**
```kotlin
ContentCard(title = "Battery Status") {
    Text("Your battery is at 75% and charging")
    Spacer(modifier = Modifier.height(8.dp))
}
```

### 2. FeatureCard - Icon, Title, Description

```kotlin
@Composable
fun FeatureCard(
    title: String,
    description: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Int = 32,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    iconTint: Color? = null
)
```

**Features:**
- Icon on top, `Spacer(weight = 1f)`, then title and description
- Press scale 0.95f, haptic on click
- Title ellipsised to one line, description to two

**Usage Example:**
```kotlin
FeatureCard(
    title = "Power Peek",
    description = "Shake to view the battery level",
    icon = painterResource(R.drawable._44),
    onClick = { onCardClick() }
)
```

### 3. SquareFeatureCard - Grid Tile

```kotlin
@Composable
fun SquareFeatureCard(
    title: String,
    description: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Int = 40,
    isServiceActive: Boolean = true,
    skipConfirmation: Boolean = false,
    iconTint: Color? = null
)
```

**Features:**
- `aspectRatio(1f)` tile for a grid layout
- Fades to `alpha = 0.3f` while the glyph service is off
- Presses release after 150 ms, then either open the confirmation dialog or, with `skipConfirmation = true`, call `onClick` straight away

**Usage Example:**
```kotlin
FeatureGrid(spacing = 16) {
    SquareFeatureCard(
        title = "Power Peek",
        description = "Shake to view the battery level",
        icon = painterResource(R.drawable._44),
        isServiceActive = glyphServiceEnabled,
        onClick = { viewModel.testFeature(GlyphFeature.POWER_PEEK) }
    )
}
```

### 4. WideFeatureCardWithToggle - Feature Card With a Switch

```kotlin
@Composable
fun WideFeatureCardWithToggle(
    title: String,
    description: String,
    icon: Painter,
    isServiceActive: Boolean,
    isFeatureEnabled: Boolean,
    onFeatureToggle: (Boolean) -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Int = 32,
    height: Int = 140
)
```

**Features:**
- Tint tells you the state at a glance: `NothingViolate` when the service is off, `NothingGreen` when the feature is on, `NothingRed` otherwise
- A `MorphingToggleButton` sits in the top-end corner, offset 12 dp in
- The card itself is only a tap target — nothing about the dialog it opens lives here

**Usage Example:**
```kotlin
WideFeatureCardWithToggle(
    title = "Power Peek",
    description = "Shake to view the battery level",
    icon = painterResource(R.drawable._44),
    isServiceActive = isServiceActive,
    isFeatureEnabled = isEnabled,
    onFeatureToggle = onEnabledChange,
    onCardClick = { showDialog = true }
)
```

### 5. GlyphControlCard - Master Glyph Switch

```kotlin
@Composable
fun GlyphControlCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes illustrationRes: Int? = null
)
```

**Features:**
- The top card on the home screen: the master switch for the whole glyph service
- 120 dp tall, no haptics of its own — the embedded `MorphingToggleButton` handles that
- `surfaceContainer` under EXPRESSIVE, `surface` otherwise

**Usage Example:**
```kotlin
GlyphControlCard(
    enabled = uiState.glyphServiceEnabled,
    onEnabledChange = viewModel::toggleGlyphService
)
```

### 6. FeatureCards - The Eight Home Screen Cards

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/components/FeatureCards.kt` (seven cards) and `app/src/main/java/com/bleelblep/glyphsharge/ui/components/VpnConnected.kt` (the eighth)

`PowerPeekCard`, `PulseLockCard`, `LowBatteryAlertCard`, `ScreenOffCard`, `NfcGlyphCard`, `ChargingAnimationCard`, `MusicVisualizerCard` and `VpnConnectedCard` all share one shape: the toggle state arrives as `isEnabled` and leaves through `onEnabledChange`, which persists the preference and starts or stops the backing service. None of them takes a settings repository.

```kotlin
@Composable
fun PowerPeekCard(
    isEnabled: Boolean,
    isServiceActive: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTestPowerPeek: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.power_peek_title),
    description: String = stringResource(id = R.string.power_peek_description),
    icon: Painter,
    iconSize: Int = 32,
)
```

**Features:**
- Card stays visible while the glyph service is off, but refuses to open: the tap shows the service-off toast instead of the feature's dialog
- `LowBatteryAlertCard`, `NfcGlyphCard`, `ChargingAnimationCard`, `MusicVisualizerCard` and `VpnConnectedCard` read `LocalSettingsRepository.current` for the values their confirmation dialog saves; `PowerPeekCard` and `PulseLockCard` do not need to, because the save happens inside the enable dialog
- Title and description default to string resources and can be overridden by the caller

**Usage Example:**
```kotlin
PowerPeekCard(
    isEnabled = uiState.stateOf(GlyphFeature.POWER_PEEK).isEnabled,
    isServiceActive = glyphServiceEnabled,
    onEnabledChange = onPowerPeekToggle,
    onTestPowerPeek = { viewModel.testFeature(GlyphFeature.POWER_PEEK) },
    icon = painterResource(id = R.drawable._44),
    modifier = Modifier.fillMaxWidth(),
    iconSize = 32
)
```

---

## Custom Animations & Visual Effects

### 1. GlyphAnimations - The Animation Catalogue

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/components/GlyphAnimations.kt`

Built-in animations are a fixed list, but the user can add their own in the studio, so a picker never renders `list` on its own: it calls `withCustomAnimations` with whatever the repository currently holds. That keeps a new script visible on Pulse Lock, Low Battery, NFC and Screen Off without those four screens knowing anything about the studio.

```kotlin
object GlyphAnimations {
    data class GlyphAnim(
        val id: String,
        val displayName: String,
        @DrawableRes val iconRes: Int,
        /** `true` for a Lua animation, which the duration setting bounds. */
        val isCustom: Boolean = false
    )

    val list = listOf(
        GlyphAnim("C1", "C1 Sequential", R.drawable.su),
        GlyphAnim("WAVE", "Wave", R.drawable._78),
        GlyphAnim("BEEDAH", "Beedah", R.drawable._78),
        GlyphAnim("PULSE", "Pulse", R.drawable._44),
        GlyphAnim("LOCK", "Padlock Sweep", R.drawable._23_24px),
        GlyphAnim("SPIRAL", "Spiral", R.drawable._78),
        GlyphAnim("HEARTBEAT", "Heartbeat", R.drawable._44),
        GlyphAnim("MATRIX", "Matrix Rain", R.drawable._78),
        GlyphAnim("FIREWORKS", "Fireworks", R.drawable._44),
        GlyphAnim("DNA", "DNA Helix", R.drawable._23_24px)
    )

    fun withCustomAnimations(
        custom: List<ScriptAnimation>,
        scope: ScriptScope = ScriptScope.TRIGGER,
    ): List<GlyphAnim>

    fun getById(id: String, options: List<GlyphAnim> = list): GlyphAnim
}
```

**Features:**
- Ten built-ins: `C1`, `WAVE`, `BEEDAH`, `PULSE`, `LOCK`, `SPIRAL`, `HEARTBEAT`, `MATRIX`, `FIREWORKS`, `DNA`
- `withCustomAnimations` puts built-ins first and the user's scripts at the end, so a picker keeps a stable layout and the part that changes is always at the end of the chip row
- `scope` filters the scripts. The four trigger features pass `ScriptScope.TRIGGER` and never see a script that declared `glyph.target = "music"`; the visualiser passes `ScriptScope.MUSIC` and sees everything, because a script that named no target means "anywhere"
- `getById` falls back to the first entry when a stored id no longer resolves — the script was deleted, or the picker was handed a stale list — so the feature still has something valid selected

**Usage Example:**
```kotlin
// Inside a feature dialog:
val animationOptions = rememberAnimationOptions()
var selectedAnimation by remember {
    mutableStateOf(GlyphAnimations.getById(settingsRepository.getPulseLockAnimationId(), animationOptions))
}
```

### 2. rememberAnimationOptions - The Live Script List

```kotlin
@Composable
fun rememberAnimationOptions(scope: ScriptScope = ScriptScope.TRIGGER): List<GlyphAnimations.GlyphAnim>
```

**Features:**
- Reads the studio's script list from `CustomAnimationsViewModel` through `hiltViewModel()` + `collectAsStateWithLifecycle`
- The list is a `StateFlow` on purpose: a script saved in the studio has to appear on all four feature cards at once, including the one already open behind it
- Memoised on `(custom, scope)`, so a picker that recomposes for another reason does not rebuild its chip row

**Usage Example:**
```kotlin
// LowBattery.kt, PulseLock.kt, NfcGlyph.kt, ScreenOff.kt
val animationOptions = rememberAnimationOptions()

// MusicVisualizer.kt — a private helper over the same store, because the
// visualiser offers the scripts in a separate list from the six built-in
// visualisation modes.
@Composable
private fun rememberMusicScriptOptions(): List<ScriptAnimation> {
    val viewModel: CustomAnimationsViewModel = hiltViewModel()
    val scripts by viewModel.animations.collectAsStateWithLifecycle()
    return remember(scripts) { scripts.filter { it.target == ScriptTarget.MUSIC } }
}
```

### 3. rememberGlyphAnimationManager - The Manager Behind Every Test Button

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/components/GlyphDependencies.kt`

```kotlin
@Composable
fun rememberGlyphAnimationManager(): GlyphAnimationManager =
    hiltViewModel<HomeViewModel>().glyphAnimationManager
```

**Features:**
- The configuration dialogs are plain Composables, so they cannot inject for themselves
- `HomeViewModel` already holds the manager because it plays these features' animations, and `hiltViewModel()` resolves from inside a dialog as well as from a screen — so this hands back the very instance the home screen uses
- One line, and the same manager across every "Test" button

**Usage Example:**
```kotlin
// PulseLock.kt
val glyphAnimationManager = rememberGlyphAnimationManager()

scope.launch {
    if (selectedAnimation.isCustom) {
        glyphAnimationManager.playCustomAnimation(selectedAnimation.id)
    } else {
        glyphAnimationManager.runSpiralAnimation()
    }
}
```

### 4. MaterialSharedAxisZ - Route Transitions

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/navigation/MaterialSharedAxisZ.kt`

```kotlin
object MaterialSharedAxisZ {
    fun enterTransition(): EnterTransition
    fun exitTransition(): ExitTransition
    fun popEnterTransition(): EnterTransition
    fun popExitTransition(): ExitTransition
}
```

**Features:**
- Pushing a destination fades and scales in from 0.98 over 200 ms; popping fades and scales out towards 1.02 over 120 ms, so the reverse trip mirrors the forward one
- `FastOutSlowInEasing` on the way in, `EaseOut` on the way out
- Applied to the whole `NavHost` in `GlyphNavHost` rather than per screen, so every destination moves the same way and the settings stack reads as one surface

**Usage Example:**
```kotlin
NavHost(
    navController = navController,
    startDestination = Routes.HOME,
    enterTransition = { MaterialSharedAxisZ.enterTransition() },
    exitTransition = { MaterialSharedAxisZ.exitTransition() },
    popEnterTransition = { MaterialSharedAxisZ.popEnterTransition() },
    popExitTransition = { MaterialSharedAxisZ.popExitTransition() }
) { /* destinations */ }
```

---

## Specialized Components

### 1. Morphing Controls - Toggles That Change Shape

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/components/controls/ToggleButtons.kt`

The app's signature control. The switch is not a switch: it is a pill that grows, straightens and gains a checkmark when it is on.

```kotlin
@Composable
fun MorphingToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabledIcon: @Composable () -> Unit = { /* CheckCircle, 28 dp */ },
    disabledIcon: @Composable () -> Unit = { /* Close, 24 dp */ }
)

@Composable
fun ThreeStateFontMorphingButton(
    currentVariant: FontVariant,
    onVariantSelected: (FontVariant) -> Unit,
    modifier: Modifier = Modifier
)
```

**Features:**
- Size, corner radius, background and icon all animate together, so the state is readable mid-transition
- 88 × 40 dp off, 60 × 60 dp on; corner radius 12 dp → 30 dp
- `NothingRed` when on, `NothingGray` when off — or `primary` / `surfaceContainerHigh` under EXPRESSIVE
- Press scale 0.93f, active scale 1.07f, both spring-based
- A 300 ms colour tween and a `scaleIn + fadeIn` / `fadeOut` icon swap
- `ThreeStateFontMorphingButton` is the same idea for the font family, so the settings row can change the app's typeface without opening a screen

**Usage Example:**
```kotlin
MorphingToggleButton(
    checked = isFeatureEnabled,
    onCheckedChange = onFeatureToggle,
    modifier = Modifier
        .align(Alignment.TopEnd)
        .offset(x = (-12).dp, y = 12.dp)
)
```

### 2. Shared Dialog Chrome

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/components/dialogs/`

Every feature dialog is the same centred title, the same "how it works" card and the same 24 dp container shape. Only the strings, the sliders and the buttons differ.

```kotlin
// FeatureDialogScaffold.kt
@Composable
fun FeatureDialogScaffold(
    title: String,
    subtitle: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    howItWorksTitle: String? = null,
    howItWorksDescription: String? = null,
    content: (@Composable () -> Unit)? = null
)

// FeatureConfirmationFlow.kt
@Composable
fun FeatureConfirmationFlow(
    title: String,
    subtitle: String,
    howItWorksTitle: String,
    howItWorksDescription: String,
    testLabel: String,
    onTest: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
    settings: @Composable (
        onConfirm: () -> Unit,
        onDisable: () -> Unit,
        onDismiss: () -> Unit
    ) -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = false
)
```

**Features:**
- Pass `howItWorksTitle` and `howItWorksDescription` for the standard explanation card, or `content` for a custom body — the enable dialogs use the latter to host their slider rows
- `FeatureConfirmationFlow` owns the two-step shape every feature shares: a confirmation dialog with a gear button that swaps it for the feature's configuration dialog
- The `settings` slot receives the three callbacks it needs to close itself and report the outcome, so the feature does not think about the flow's internal state
- `dismissible` defaults to `false`. Most features are deliberately modal; Screen Off is not.

**Usage Example:**
```kotlin
// PowerPeek.kt
FeatureConfirmationFlow(
    title = stringResource(R.string.power_peek_title),
    subtitle = stringResource(R.string.power_peek_description),
    howItWorksTitle = stringResource(R.string.power_peek_how_it_works_title),
    howItWorksDescription = stringResource(R.string.power_peek_how_it_works_description),
    testLabel = stringResource(R.string.power_peek_button_test),
    onTest = onTestPowerPeek,
    onEnable = onEnablePowerPeek,
    onDisable = onDisablePowerPeek,
    onDismiss = onDismiss,
    modifier = modifier,
    settings = { onConfirm, onDisable, onDismissSettings ->
        PowerPeekEnableDialog(
            onConfirm = { onConfirm() },
            onDisable = onDisable,
            onDismiss = onDismissSettings
        )
    }
)
```

### 3. Feature Dialogs - One Per Feature

**Locations:** `app/src/main/java/com/bleelblep/glyphsharge/ui/components/`

| File | Confirmation dialog | Configuration dialog |
| --- | --- | --- |
| `PowerPeek.kt` | `PowerPeekConfirmationDialog` | `PowerPeekEnableDialog` |
| `PulseLock.kt` | `PulseLockConfirmationDialog` | `PulseLockEnableDialog` |
| `LowBattery.kt` | `LowBatteryAlertConfirmationDialog` | `LowBatteryAlertEnableDialog` |
| `ScreenOff.kt` | `ScreenOffConfirmationDialog` | `ScreenOffEnableDialog` |
| `NfcGlyph.kt` | `NfcGlyphConfirmationDialog` | `NfcGlyphEnableDialog` |
| `ChargingAnimation.kt` | `ChargingAnimationConfirmationDialog` | `ChargingAnimationEnableDialog` |
| `MusicVisualizer.kt` | `MusicVisualizerConfirmationDialog` | `MusicVisualizerEnableDialog` |

A confirmation dialog takes only callbacks — it is a question, and it has no settings of its own:

```kotlin
@Composable
fun PowerPeekConfirmationDialog(
    modifier: Modifier = Modifier,
    onTestPowerPeek: () -> Unit,
    onEnablePowerPeek: () -> Unit,
    onDisablePowerPeek: () -> Unit,
    onDismiss: () -> Unit
)
```

The configuration dialog is where the settings are read and written, and it reads them from the composition:

```kotlin
@Composable
fun PowerPeekEnableDialog(
    modifier: Modifier = Modifier,
    onConfirm: (PowerPeekConfig) -> Unit,
    onDismiss: () -> Unit,
    onDisable: () -> Unit
)
```

**Usage Example:**
```kotlin
// PowerPeekEnableDialog — the store comes from the composition; the card has
// none to pass on.
val settingsRepository = LocalSettingsRepository.current
val haptic = LocalHapticFeedback.current
// The user's haptic strength is a setting, so it is read once per composition
// here and handed to HapticUtils, which cannot fetch it itself.
val vibrationIntensity = LocalVibrationIntensity.current
val context = LocalContext.current

val currentlyEnabled = remember { settingsRepository.isPowerPeekEnabled() }
var shakeThreshold by remember { mutableFloatStateOf(settingsRepository.getPowerPeekThreshold()) }
var durationSeconds by remember {
    mutableFloatStateOf((settingsRepository.getPowerPeekDuration() / 1000f).coerceIn(2f, 10f))
}

// The slider works in five steps and the stored value is a named threshold, so
// the two are translated at the ends of the drag.
var sliderStep by remember {
    mutableFloatStateOf(
        when (shakeThreshold) {
            SettingsRepository.SHAKE_EASY    -> 1f
            SettingsRepository.SHAKE_MEDIUM  -> 2f
            SettingsRepository.SHAKE_HARD    -> 3f
            SettingsRepository.SHAKE_HARDEST -> 4f
            else                             -> 0f
        }
    )
}

Slider(
    value = sliderStep,
    onValueChange = { raw ->
        HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
        sliderStep = raw.coerceIn(0f, 4f)
    },
    onValueChangeFinished = {
        shakeThreshold = when (sliderStep.roundToInt()) {
            3    -> SettingsRepository.SHAKE_HARD
            4    -> SettingsRepository.SHAKE_HARDEST
            2    -> SettingsRepository.SHAKE_MEDIUM
            1    -> SettingsRepository.SHAKE_EASY
            else -> SettingsRepository.SHAKE_SOFT
        }
    },
    valueRange = 0f..4f,
    steps = 0,
    modifier = Modifier.fillMaxWidth(),
    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
)
```

A card whose confirmation dialog has to report back a configuration — Low Battery is the one — threads that config out through the callback, so the card can persist it before closing:

```kotlin
LowBatteryAlertConfirmationDialog(
    onTestAlert = { onTestAlert(); showDialog = false },
    onEnableAlert = { config ->
        settingsRepository.saveLowBatteryEnabled(config.isEnabled)
        settingsRepository.saveLowBatteryThreshold(config.threshold)
        settingsRepository.saveLowBatteryAnimationId(config.animationId)
        settingsRepository.saveLowBatteryDuration(config.durationMs)
        onEnabledChange(config.isEnabled)
        showDialog = false
    },
    onDisableAlert = { onEnabledChange(false); showDialog = false },
    onDismiss = { showDialog = false }
)
```

### 4. CommonDialogComponents - Shared Pieces

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/components/CommonDialogComponents.kt`

```kotlin
@Composable
fun ThemedValueBadge(
    value: String,
    modifier: Modifier = Modifier
)

@Composable
fun FeatureConfirmationButtons(
    primaryLabel: String,
    onPrimary: () -> Unit,
    onSettings: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
)

@Composable
fun FeatureSaveButtons(
    isSaving: Boolean,
    isCurrentlyEnabled: Boolean,
    enableLabel: String,
    onSave: () -> Unit,
    onDisable: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
)
```

**Features:**
- `ThemedValueBadge` is the read-only value chip next to a slider: `primaryContainer` under EXPRESSIVE, `surfaceVariant` otherwise
- `FeatureConfirmationButtons` is the Test / Settings / Cancel stack every confirmation dialog ends with; `FeatureSaveButtons` is the Save / Disable / Cancel stack the configuration dialogs end with
- Both put a 52 dp `ElevatedButton` on top and two half-width buttons below, and both use `themePrimaryActionColor()` for the primary action
- Haptics are per button: medium for the primary, light for the two secondary

### 5. Screen & Section Layout

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/components/layout/`

```kotlin
// SettingsScaffold.kt
@Composable
fun SettingsScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: LazyListScope.() -> Unit
)

// SectionLayout.kt
@Composable
fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier
)

@Composable
fun FeatureGrid(
    modifier: Modifier = Modifier,
    spacing: Int = 16,
    content: @Composable RowScope.() -> Unit
)

// DraggableSettingsCard.kt
@Composable
fun DraggableSettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = Color.Unspecified,
    onNavigate: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
)
```

**Features:**
- `SettingsScaffold` is the chrome for every scrolling screen: a collapsing `LargeTopAppBar` over a `LazyColumn`, transparent at the top and `surface` once the bar collapses, with status-bar insets on the list and navigation-bar padding at the bottom
- `onBackClick = null` draws no back arrow — the home screen navigates forward, not back
- `DraggableSettingsCard` is the app's signature interaction: swipe a settings row sideways and it tints towards `primaryContainer` and lifts in proportion to the drag. Past half the screen width it snaps back in 200 ms and fires `onNavigate`; released earlier it springs back bouncily
- `onNavigate = null` makes the card inert; `onClick` is the separate tap target the studio entry needs, because it opens a different Activity
- Every settings row is one of these — only the title, the optional subtitle and the trailing control differ

**Usage Example:**
```kotlin
DraggableSettingsCard(
    title = stringResource(id = R.string.settings_card_custom_animations),
    subtitle = stringResource(
        id = R.string.settings_card_custom_animations_count,
        animationCount
    ),
    onNavigate = onClick,
    onClick = onClick
)
```

### 6. FontSettingsComponents - Font Customization

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/components/FontSettingsComponents.kt`

```kotlin
@Composable
fun ToggleCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    statusText: @Composable (Boolean) -> String = { "" }
)

@Composable
fun ThreeStateFontToggle(
    currentVariant: FontVariant,
    onVariantSelected: (FontVariant) -> Unit,
    modifier: Modifier = Modifier
)

@Composable
fun SimpleFontSelector(
    currentVariant: FontVariant,
    onVariantSelected: (FontVariant) -> Unit,
    modifier: Modifier = Modifier
)

@Composable
fun FontSizeControls(
    fontSizeSettings: FontSizeSettings,
    onSizeChanged: (FontCategory, Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
)

@Composable
fun FontPreview(
    fontState: FontState,
    modifier: Modifier = Modifier
)
```

**Features:**
- `ThreeStateFontToggle` is a row of three morphing buttons (HEADLINE / NDOT / SYSTEM), each with its own shape, colour, size and letter
- Size 44 dp → 52 dp on selection, spring-based; the label is set in that variant's own font, so the button is a sample
- Unselected background is `surfaceVariant`; selected is `NothingViolate` for HEADLINE and NDOT, `NothingRed` for SYSTEM
- `ToggleCard` is the settings row with a `Switch` — a `NothingViolate` thumb on a 50 % alpha track
- `FontSizeControls` is one slider per `FontCategory`, and `FontPreview` renders a sample in the current family and scale

**Usage Example:**
```kotlin
ThreeStateFontToggle(
    currentVariant = fontState.currentVariant,
    onVariantSelected = { newVariant ->
        fontState.setFontVariant(newVariant)
    }
)
```

### 7. Screen State Models

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/state/FeatureModels.kt`

```kotlin
@Immutable
data class FeatureUiState(
    val feature: GlyphFeature,
    val isEnabled: Boolean = false,
    val isServiceActive: Boolean = true
)

@Immutable
data class HomeUiState(
    val glyphServiceEnabled: Boolean = false,
    val features: Map<GlyphFeature, FeatureUiState> = emptyMap()
) {
    fun stateOf(feature: GlyphFeature): FeatureUiState =
        features[feature] ?: FeatureUiState(feature)
}
```

**Features:**
- A card stays visible when the glyph service is off but refuses to open, so `isServiceActive` travels with the toggle state rather than with the card
- `stateOf` never returns null, so a feature without an entry yet still renders with sane defaults
- The eight features are `GlyphFeature.PULSE_LOCK`, `POWER_PEEK`, `LOW_BATTERY`, `SCREEN_OFF`, `NFC`, `CHARGING_ANIMATION`, `MUSIC_VISUALIZER`, `VPN_CONNECTED`, declared once in `app/src/main/java/com/bleelblep/glyphsharge/glyph/GlyphFeatureCoordinator.kt` where the strip is arbitrated between them


---

## Animation Studio (Lua)

The studio is where the user writes their own glyph animations in Lua. It is reached from
**Settings → Custom Animations** and hosted by its own Activity rather than the settings
`NavHost`. The full script language reference lives in
[the main documentation](docs/DOCUMENTATION_EN.md#14-custom-animations-lua); this section
covers the UI pieces.

### 1. CustomAnimationsActivity - Studio Host

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/CustomAnimationsActivity.kt`

```kotlin
@AndroidEntryPoint
class CustomAnimationsActivity : ComponentActivity() {

    companion object {
        /** The entry point used by the settings card. */
        fun intent(context: Context): Intent =
            Intent(context, CustomAnimationsActivity::class.java)
    }
}
```

**Features:**
- `android:exported="false"` with `parentActivityName=".MainActivity"` — nothing outside the app can open a code editor
- Own `ActivityResultLauncher`s: `OpenDocument` for import, `CreateDocument` for "Export to a file"
- `BackHandler` between the list and the editor, so the system back always means "leave this screen" and never "fall into the settings graph"
- Applies the chosen locale in `attachBaseContext`, like `MainActivity` does
- Provides `LocalSettingsRepository` and `LocalVibrationIntensity` in its `setContent`, so the studio's screens find the store and the user's own haptic strength already in the composition
- Opens the Glyph session in `onStart()` and closes it in `onStop()` only if it was the one that opened it, so the Glyph button works when the user walks straight from the home screen into the studio
- Stops any running script in `onStop()` and `onDestroy()` — leaving the studio must never leave the strip lit

**Why a separate Activity:** the studio has its own back stack, its own system file pickers
and a code editor. None of that belongs in the shared settings navigation, and it keeps the
blast radius small — a script run here goes through the same `GlyphAnimationManager` the
feature services use, but nothing on this screen can change a feature's configuration.

### 2. AnimationListScreen - Saved Scripts

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/screens/animations/AnimationListScreen.kt`

```kotlin
@Composable
fun AnimationListScreen(
    animations: List<ScriptAnimation>,
    onBackClick: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onPickImportFile: () -> Unit,
    onExportToDownloads: (String) -> Unit,
    onExportToFile: (String) -> Unit,
    modifier: Modifier = Modifier
)
```

**Features:**
- One row per saved script: name, character count, and the last edit time
- Per-row overflow menu: open, duplicate, delete, save to Downloads, export to a file
- Import in the top bar always; New only once `animations.isNotEmpty()`, so an empty studio keeps a single call to action — the one in the middle
- Empty state with a call to action instead of a blank list
- No rename in the row menu: a row is a target, not a form. The editor's name field is the one place a name is edited

**Usage Example:**
```kotlin
AnimationListScreen(
    animations = state.animations,
    onBackClick = { finish() },
    onOpen = viewModel::open,
    onCreate = { viewModel.newAnimation() },
    onDelete = viewModel::delete,
    onDuplicate = viewModel::duplicate,
    onPickImportFile = { importLauncher.launch(IMPORT_MIME_TYPES) },
    onExportToDownloads = viewModel::exportToDownloads,
    onExportToFile = { id -> promptExport(id) }
)
```

### 3. AnimationEditorScreen - Code, Console, API Reference

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/screens/animations/AnimationEditorScreen.kt`

```kotlin
@Composable
fun AnimationEditorScreen(
    name: String,
    source: String,
    console: List<ConsoleLine>,
    isDirty: Boolean,
    isRunning: Boolean,
    onBackClick: () -> Unit,
    onNameChange: (String) -> Unit,
    onSourceChange: (String) -> Unit,
    onSave: () -> Unit,
    onCheck: () -> Unit,
    onRunGlyph: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
)
```

**Features:**
- **Check** compiles the source and prints the first syntax error; nothing is drawn
- **Glyph** plays it for real through `GlyphAnimationManager.previewScript` — the only verdict that counts
- The run button becomes **Stop** while a run is in flight; `isRunning` drives both the icon and the hint line
- A line under the name field reports the script's `glyph.target`, read back out of the source on every keystroke, so the author finds out the line has to go *before* the first `glyph.set()` instead of wondering why nothing ever picked it up
- `BasicTextField` code surface in a private `CodeEditor`: monospace, scrolls in both axes, no floating form decoration
- Collapsible `glyph API reference` card listing every call and what it does — its contents are the private `API_REFERENCE` list next to the composable, so adding a binding in `GlyphLuaApi.kt` and documenting it here stays one edit
- The title shows the dirty variant while there are unsaved changes, and a Save action in the app bar
- There is no on-screen preview: a schematic of the LED layout is not the same as the phone lighting up, and a second run mode invites mistaking one for the other

> [!NOTE]
> The cheat sheet is an abbreviated index, not the contract. It writes `glyph.running` and
> `glyph.battery / .charging` without parentheses, but in `GlyphLuaApi.kt` all five
> runtime-state accessors are bound as Lua **functions** — a script must call
> `glyph.running()`, `glyph.battery()` and `glyph.charging()`. Without the parentheses a
> value like `glyph.running` is always truthy, so a `while` loop never winds down.
> `glyph.MAX` and `glyph.device` really are plain values. The full reference, and the full
> list of calls, are in
> [the main documentation](docs/DOCUMENTATION_EN.md#14-custom-animations-lua).

**Usage Example:**
```kotlin
AnimationEditorScreen(
    name = state.name,
    source = state.source,
    console = state.console,
    isDirty = state.isDirty,
    isRunning = state.isRunning,
    onBackClick = { viewModel.closeEditor() },
    onNameChange = viewModel::updateName,
    onSourceChange = viewModel::updateSource,
    onSave = viewModel::save,
    onCheck = viewModel::check,
    onRunGlyph = { viewModel.runOnGlyph() },
    onStop = viewModel::stop
)
```

### 4. AnimationStudioViewModel - Studio State

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/viewmodel/AnimationStudioViewModel.kt`

```kotlin
@HiltViewModel
class AnimationStudioViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: CustomAnimationRepository,
    private val glyphAnimationManager: GlyphAnimationManager,
    private val glyphManager: GlyphManager
) : ViewModel() {

    val uiState: StateFlow<StudioUiState>
    val messages: StateFlow<String?>

    fun newAnimation(); fun open(id: String); fun closeEditor()
    fun updateName(name: String); fun updateSource(source: String); fun save()
    fun delete(id: String); fun duplicate(id: String)
    fun check()
    fun runOnGlyph(); fun stop()
    fun importFrom(uri: Uri)
    fun exportTo(uri: Uri, id: String)
    fun exportToDownloads(id: String)
    fun consumeMessage()

    companion object {
        const val RUN_DURATION_MS = ScriptAnimation.SAFETY_CAP_MS
    }
}
```

**State:**
```kotlin
data class StudioUiState(
    val animations: List<ScriptAnimation> = emptyList(),
    val isEditorOpen: Boolean = false,
    val editing: ScriptAnimation? = null,
    val name: String = "",
    val source: String = "",
    val isDirty: Boolean = false,
    val console: List<ConsoleLine> = emptyList(),
    val isRunning: Boolean = false
)

data class ConsoleLine(val text: String, val isError: Boolean = false)
```

**Features:**
- Collects `repository.animations` into the UI state, so the list updates as soon as a script is saved
- `isEditorOpen` is stored, not derived from `editing`: a brand new script has a draft to edit, but an editor that opens on an empty buffer would then be impossible to represent
- `runOnGlyph` pre-flights the hardware — a phone with no Glyph interface and a session that was never open both report through the console rather than looking like a script that did nothing
- A run has no duration parameter: a script runs until its code is done, Stop is the user's way out, and `SAFETY_CAP_MS` only catches a script that never returns
- Turns a `ScriptRunResult` into console lines: frames and elapsed time, or the error message, per `ScriptStatus`
- Messages flow through a `StateFlow<String?>` consumed once by the Activity and shown as a Toast

### 5. CustomAnimationsViewModel - The Script List For the Feature Cards

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/viewmodel/CustomAnimationsViewModel.kt`

```kotlin
@HiltViewModel
class CustomAnimationsViewModel @Inject constructor(
    private val repository: CustomAnimationRepository
) : ViewModel() {

    val animations: StateFlow<List<ScriptAnimation>>
    val animationCount: StateFlow<Int>
}
```

**Features:**
- The animation pickers are plain Composables — a chip row in a feature dialog, a count on the settings screen — so the repository reaches them through a ViewModel: `hiltViewModel()` resolves from inside a dialog as well as from a screen
- `animations` re-exposes the repository's own flow rather than copying it, so two collectors would not watch two copies of the same list
- `animationCount` is derived from the same flow with `SharingStarted.WhileSubscribed(5_000L)`: long enough that scrolling the settings row off screen and back does not restart the collection
- One injected store means a script saved in the studio appears on every feature card at once

**How a saved script reaches the feature cards:**
```kotlin
// Inside any feature dialog (PulseLock, LowBattery, NfcGlyph, ScreenOff):
val animationOptions = rememberAnimationOptions()          // built-ins + the user's scripts
var selectedAnimation by remember {
    mutableStateOf(GlyphAnimations.getById(settingsRepository.getPulseLockAnimationId(), animationOptions))
}
val glyphAnimationManager = rememberGlyphAnimationManager()

// Test button:
scope.launch {
    if (selectedAnimation.isCustom) {
        glyphAnimationManager.playCustomAnimation(selectedAnimation.id)
    } else {
        when (selectedAnimation.id) {
            "SPIRAL"    -> glyphAnimationManager.runSpiralAnimation()
            "HEARTBEAT" -> glyphAnimationManager.runHeartbeatAnimation()
            "MATRIX"    -> glyphAnimationManager.runMatrixRainAnimation()
            "FIREWORKS" -> glyphAnimationManager.runFireworksAnimation()
            "DNA"       -> glyphAnimationManager.runDNAHelixAnimation()
            else        -> glyphAnimationManager.playLowBatteryAnimation()
        }
    }
}
```


---

## Utility Systems

### 1. HapticUtils - Comprehensive Vibration Management

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/utils/HapticUtils.kt`

Every tap in the app goes through one of the five `trigger*Feedback` helpers, or through `performHapticWithIntensity` directly.

```kotlin
object HapticUtils {
    object Intensity {
        const val OFF = 0
        const val LIGHT = 85          // ~33% of max
        const val MEDIUM = 170        // ~66% of max
        const val STRONG = 255        // 100% max amplitude
        const val DEFAULT = VibrationEffect.DEFAULT_AMPLITUDE // -1
    }

    // Durations, private: the public API picks one from the HapticType.
    private const val SHORT_DURATION = 50L
    private const val MEDIUM_DURATION = 100L
    private const val LONG_DURATION = 200L

    // Predefined haptic effects, taking an Android amplitude (1-255)
    fun performLightHaptic(context: Context, hapticFeedback: HapticFeedback, intensity: Int = Intensity.LIGHT)
    fun performMediumHaptic(context: Context, hapticFeedback: HapticFeedback, intensity: Int = Intensity.MEDIUM)
    fun performStrongHaptic(context: Context, hapticFeedback: HapticFeedback, intensity: Int = Intensity.STRONG)
    fun performClickHaptic(context: Context, hapticFeedback: HapticFeedback, intensity: Int = Intensity.LIGHT)
    fun performSuccessHaptic(context: Context, hapticFeedback: HapticFeedback, intensity: Int = Intensity.MEDIUM)
    fun performErrorHaptic(context: Context, hapticFeedback: HapticFeedback, intensity: Int = Intensity.STRONG)

    // Generic haptic with type, on the user's own 0.0-1.0 scale
    fun performHapticWithIntensity(
        context: Context,
        hapticFeedback: HapticFeedback,
        userIntensity: Float,
        type: HapticType = HapticType.LIGHT
    )

    // The five helpers the UI actually calls
    fun triggerLightFeedback(hapticFeedback: HapticFeedback, context: Context, intensity: Float)
    fun triggerMediumFeedback(hapticFeedback: HapticFeedback, context: Context, intensity: Float)
    fun triggerStrongFeedback(hapticFeedback: HapticFeedback, context: Context, intensity: Float)
    fun triggerSuccessFeedback(hapticFeedback: HapticFeedback, context: Context, intensity: Float)
    fun triggerErrorFeedback(hapticFeedback: HapticFeedback, context: Context, intensity: Float)

    // Utility functions
    fun testVibrationIntensity(context: Context, intensity: Int)
    fun testExactAmplitude(hapticFeedback: HapticFeedback, context: Context, userIntensity: Float)
    fun performCustomVibration(context: Context, duration: Long, amplitude: Int)
    fun performCustomVibrationPattern(context: Context, timings: LongArray, amplitudes: IntArray)
    fun validateIntensity(intensity: Int, vibrator: Vibrator): Int
    fun getVibrator(context: Context): Vibrator
    fun convertUserIntensityToAndroidAmplitude(userIntensity: Float): Int
    fun getVibrationInfo(context: Context): VibrationInfo
}

enum class HapticType {
    LIGHT, MEDIUM, STRONG, CLICK, SUCCESS, ERROR
}

data class VibrationInfo(
    val hasVibrator: Boolean,
    val hasAmplitudeControl: Boolean,
    val supportedEffects: List<Int>
)
```

**Features:**
- Two intensity scales on purpose: the `Int` constants are Android amplitudes, and the `Float` helpers take the user's 0.0-1.0 setting and convert it
- Compose's `HapticFeedback` fires first, so the system setting is respected, and the custom vibration only follows when the amplitude is not `DEFAULT_AMPLITUDE`
- `VibratorManager` on Android O+ and `Vibrator` below it, resolved in one place
- `validateIntensity` clamps against the device's own reported range, and `getVibrationInfo` is the capability check for anything that needs fine-grained amplitude control
- `intensity` on the `trigger*` helpers is the user's own setting, injected by the caller because a click handler cannot read a repository — the value arrives from `LocalVibrationIntensity`

**Usage Examples:**

```kotlin
// The way a click handler does it
val haptic = LocalHapticFeedback.current
val vibrationIntensity = LocalVibrationIntensity.current
val context = LocalContext.current

Button(onClick = {
    HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
    onClick()
}) { /* … */ }

// The primary action of a dialog, a notch stronger
HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)

// Going through a type explicitly
HapticUtils.performHapticWithIntensity(
    context,
    hapticFeedback,
    userIntensity = 0.66f,
    type = HapticType.MEDIUM
)

// Check device capabilities
val info = HapticUtils.getVibrationInfo(context)
if (info.hasAmplitudeControl) {
    // Use fine-grained amplitude control
}
```

### 2. Themed Color Helpers

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/theme/ThemeColors.kt`

Five `@Composable` functions that answer "what colour is this control on this theme", so no screen repeats the same `when` over `AppThemeStyle`.

```kotlin
@Composable fun themeCardContainerColor(): Color
@Composable fun themePrimaryActionColor(): Color
@Composable fun themeSettingsButtonColor(): Color
@Composable fun themeSettingsButtonContentColor(): Color
@Composable fun themeSecondaryButtonColors(): ButtonColors
```

**Features:**
- AMOLED, CLASSIC and everything else each get their own answer; a new style is one `when` branch per function
- `themeCardContainerColor()` is what every feature dialog's inner cards use, which is why the dialogs look right on all six styles without anyone styling them
- `themePrimaryActionColor()` is the one accent on the Save / Enable / Test button of every dialog
- `themeSecondaryButtonColors()` returns a whole `ButtonColors`, so the quiet buttons next to a primary action match the theme too

**Usage Example:**
```kotlin
val cardColor = themeCardContainerColor()
val accent = themePrimaryActionColor()
val secBtnColors = themeSecondaryButtonColors()

Card(
    colors = CardDefaults.cardColors(containerColor = cardColor),
    shape = RoundedCornerShape(16.dp)
) { /* … */ }

Button(
    onClick = { /* … */ },
    colors = secBtnColors,
    shape = RoundedCornerShape(12.dp)
) { /* … */ }
```

---

## Usage Examples

### Example 1: Feature Card with a Progress Bar

```kotlin
ContentCard(title = "Charging Progress") {
    Text(
        "Fast charging enabled",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(12.dp))

    LinearProgressIndicator(
        progress = { 0.75f },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "75%",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "15 min remaining",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

### Example 2: Settings Screen

A screen takes no dependencies. The settings store comes from `LocalSettingsRepository`, the theme and font state from their own locals, and the studio's script count from `hiltViewModel()`:

```kotlin
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onThemeSettingsClick: () -> Unit = {},
    onFontSettingsClick: () -> Unit = {},
    onQuietHoursSettingsClick: () -> Unit = {},
    onLanguageSettingsClick: () -> Unit = {},
) {
    val fontState = LocalFontState.current
    val themeState = LocalThemeState.current
    val context = LocalContext.current

    val customAnimations: CustomAnimationsViewModel = hiltViewModel()
    val customAnimationCount by customAnimations.animationCount.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = stringResource(id = R.string.settings_title),
        onBackClick = onBackClick
    ) {
        item {
            TypographySettingsCard(
                onNavigate = onFontSettingsClick,
                trailing = {
                    if (fontState.useCustomFonts) {
                        ThreeStateFontMorphingButton(
                            currentVariant = fontState.currentVariant,
                            onVariantSelected = { variant ->
                                fontState.setFontVariant(variant)
                            }
                        )
                    }
                }
            )
        }

        item {
            CustomAnimationsCard(
                animationCount = customAnimationCount,
                onClick = { context.startActivity(CustomAnimationsActivity.intent(context)) }
            )
        }
    }
}
```

### Example 3: Custom Card with Actions

```kotlin
ContentCard(
    title = "Power Management",
    content = {
        Text(
            "Optimize battery usage and charging patterns",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { /* View Stats */ },
                modifier = Modifier.weight(1f)
            ) {
                Text("View Stats")
            }

            Button(
                onClick = { /* Optimize */ },
                modifier = Modifier.weight(1f)
            ) {
                Text("Optimize")
            }
        }
    }
)
```

### Example 4: Settings Row With a Quick Toggle

A `DraggableSettingsCard` can host a control in its trailing slot, so a whole section is one swipe away and still adjustable without leaving the screen:

```kotlin
DraggableSettingsCard(
    title = stringResource(id = R.string.settings_card_typography),
    onNavigate = onFontSettingsClick,
    trailing = {
        ThreeStateFontMorphingButton(
            currentVariant = fontState.currentVariant,
            onVariantSelected = { variant -> fontState.setFontVariant(variant) }
        )
    }
)
```

### Example 5: Animated Button with Haptic Feedback

```kotlin
@Composable
fun AnimatedActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val vibrationIntensity = LocalVibrationIntensity.current
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
    )

    Button(
        onClick = {
            HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
            onClick()
        },
        modifier = modifier
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            }
    ) {
        Text(text)
    }
}
```

---

## Integration Guide

### Prerequisites

**1. Dependencies Required:**

Versions are pinned in `gradle/libs.versions.toml` and referenced from `app/build.gradle.kts` as `libs.*` aliases:

```kotlin
dependencies {
    // Compose BOM 2024.02.00
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)          // 1.2.0
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)

    // Activity Compose 1.8.2
    implementation(libs.androidx.activity.compose)

    // Lifecycle 2.7.0 — collectAsStateWithLifecycle
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Navigation Compose 2.7.7
    implementation(libs.androidx.navigation.compose)

    // Hilt 2.50 + hiltViewModel() in composables
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)

    // LuaJ 3.0.1 — the VM behind the animation studio
    implementation(libs.luaj.jse)
}
```

**2. Minimum SDK:**
```kotlin
minSdk = 34  // Android 14+ only
```

**3. Permissions:**

Add to `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.VIBRATE" />
```

### Font Setup

**1. Add Font Files:**

These files are already in `app/src/main/res/font/`:
- `ntype_82_headline.otf` - NType Headline (official Nothing font)
- `ndot55caps.otf` - NDot 57 Caps (official Nothing font)
- `ntype_82_regular.otf` - NType Regular (official Nothing font)

`ntype_headline.xml` and `ntype_regular.xml` are the variable-font family declarations that pair with them.

**Note:** These are official Nothing fonts. You may need to use your own fonts or obtain proper licensing.

**2. Create Font Resources:**

```kotlin
// FontState.kt
val headlineFont = FontFamily(
    Font(R.font.ntype_82_headline, FontWeight.Normal),
    Font(R.font.ntype_82_headline, FontWeight.Medium),
    Font(R.font.ntype_82_headline, FontWeight.Bold)
)
```

### Theme Configuration

**1. CompositionLocals:**

Four of them, and all four fail loudly if a composable reads one that nobody provided:

```kotlin
// GlyphZenTheme.kt
val LocalFontState = staticCompositionLocalOf { createPlaceholderFontState() }
val LocalThemeState = staticCompositionLocalOf { createPlaceholderThemeState() }

// LocalSettings.kt
val LocalSettingsRepository = staticCompositionLocalOf<SettingsRepository> {
    error("SettingsRepository should be provided at the composition root")
}
val LocalVibrationIntensity = staticCompositionLocalOf { DEFAULT_VIBRATION_INTENSITY }
```

**2. Wrap Your App:**

`ThemeState` and `FontState` are injected singletons, so the Activity passes them into the theme rather than remembering them; the theme block provides the two locals itself:

```kotlin
@Composable
fun YourApp(themeState: ThemeState, fontState: FontState, settingsRepository: SettingsRepository) {
    GlyphZenTheme(themeState = themeState, fontState = fontState) {
        // The two values that never change for the life of the Activity are
        // published once, here, so anything below reads them rather than
        // carrying them as parameters.
        CompositionLocalProvider(
            LocalSettingsRepository provides settingsRepository,
            LocalVibrationIntensity provides settingsRepository.getVibrationIntensity(),
        ) {
            GlyphNavHost()
        }
    }
}
```

### Using Components

**1. Import Components:**

```kotlin
import com.bleelblep.glyphsharge.ui.components.cards.ContentCard
import com.bleelblep.glyphsharge.ui.components.FeatureCards
import com.bleelblep.glyphsharge.ui.components.controls.MorphingToggleButton
import com.bleelblep.glyphsharge.ui.components.layout.SettingsScaffold
import com.bleelblep.glyphsharge.ui.theme.LocalSettingsRepository
import com.bleelblep.glyphsharge.ui.theme.LocalVibrationIntensity
import com.bleelblep.glyphsharge.ui.utils.HapticUtils
```

**2. Use in Composables:**

A card needs no settings parameter — it reads the store from the composition:

```kotlin
@Composable
fun MyScreen() {
    val settingsRepository = LocalSettingsRepository.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val vibrationIntensity = LocalVibrationIntensity.current

    SettingsScaffold(title = "My Feature") {
        item {
            ContentCard(
                title = "My Feature",
                onClick = {
                    HapticUtils.triggerLightFeedback(haptic, context, vibrationIntensity)
                    settingsRepository.saveLowBatteryAnimationId("PULSE")
                }
            ) {
                Text("Feature description")
            }
        }
    }
}
```

### Customization Tips

**1. Override Card Colors:**

`ContentCard` picks its own colours from the theme, so a card that needs a different treatment wraps one in a `Card` and passes the colours through `ContentCard`'s slot:

```kotlin
Card(
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
) {
    ContentCard {
        Text("Custom Card")
    }
}
```

**2. Adjust Haptic Intensity:**

The intensity is a setting, published once at the Activity root. A component reads it from the composition and hands it to `HapticUtils` as an ordinary argument:

```kotlin
val vibrationIntensity = LocalVibrationIntensity.current  // the user's 0..1 setting

HapticUtils.performHapticWithIntensity(
    context,
    haptic,
    vibrationIntensity,
    HapticType.MEDIUM
)
```

**3. Custom Progress Colours:**

```kotlin
LinearProgressIndicator(
    progress = { batteryLevel / 100f },
    color = when {
        batteryLevel > 80 -> Color.Green
        batteryLevel > 20 -> Color.Yellow
        else -> Color.Red
    }
)
```

---

## Performance Considerations

### 1. Animation Optimization

- Spring animations use appropriate damping ratios to prevent jank
- Press states reset through a `delay` inside `rememberCoroutineScope`, so a cancelled composition cannot leave a card stuck small
- The morphing toggle animates size, corner radius, colour and icon together, so nothing has to be recomputed per frame

### 2. Card Rendering

- Minimal recomposition with `remember` and `derivedStateOf`
- Efficient state management with `mutableStateOf`
- Proper key usage in LazyColumn/LazyRow
- `HomeUiState.stateOf()` returns a default rather than null, so a feature card never recomposes on a null check

### 3. Haptic Performance

- Device capability detection before operations
- API level checks for compatibility
- Graceful fallback for older devices
- The user's intensity is read once per composition, not per click

### 4. Memory Management

- Font families are built once in `FontState` and reused by every `Typography`
- Color schemes pre-computed

---

## File Structure Summary

**Total UI Code:** ~8,100 lines under `ui/`

### Main Component Files

```
app/src/main/java/com/bleelblep/glyphsharge/
├── MainActivity.kt (289 lines) - Composition root, publishes the settings locals
├── CustomAnimationsActivity.kt (257 lines) - Animation studio host
└── ui/
    ├── components/
    │   ├── FeatureCards.kt (350 lines) - Seven of the eight home screen cards
    │   ├── GlyphAnimations.kt (97 lines) - Animation catalogue + custom scripts
    │   ├── GlyphDependencies.kt (19 lines) - rememberGlyphAnimationManager()
    │   ├── FontSettingsComponents.kt (661 lines) - Font management
    │   ├── CommonDialogComponents.kt (258 lines) - Badges and dialog button stacks
    │   ├── PowerPeek.kt (262 lines) - Shake detection dialogs
    │   ├── PulseLock.kt (266 lines) - Unlock animation dialogs
    │   ├── LowBattery.kt (320 lines) - Battery alert dialogs
    │   ├── ScreenOff.kt (260 lines) - Screen off dialogs
    │   ├── NfcGlyph.kt (291 lines) - NFC dialogs
    │   ├── ChargingAnimation.kt (167 lines) - Charging animation dialogs
    │   ├── MusicVisualizer.kt (389 lines) - Visualiser dialogs
    │   ├── VpnConnected.kt (310 lines) - VPN connect dialogs
    │   ├── cards/ContentCards.kt (468 lines) - ContentCard, FeatureCard, WideFeatureCardWithToggle, GlyphControlCard
    │   ├── controls/ToggleButtons.kt (331 lines) - MorphingToggleButton, ThreeStateFontMorphingButton
    │   ├── dialogs/FeatureDialogScaffold.kt (112 lines) - Shared dialog chrome
    │   ├── dialogs/FeatureConfirmationFlow.kt (73 lines) - Confirm-then-configure flow
    │   ├── layout/SettingsScaffold.kt (129 lines) - Collapsing top bar over a LazyColumn
    │   ├── layout/DraggableSettingsCard.kt (200 lines) - Swipe-to-navigate settings row
    │   └── layout/SectionLayout.kt (61 lines) - Section header and feature grid
    ├── navigation/
    │   ├── GlyphNavHost.kt (104 lines) - The whole graph, no dependencies
    │   ├── MaterialSharedAxisZ.kt (52 lines) - Route transitions
    │   └── Routes.kt (17 lines) - Route constants
    ├── screens/
    │   ├── SettingsScreen.kt, ThemeSettingsScreen.kt, FontSettingsScreen.kt,
    │   │   QuietHoursSettingsScreen.kt, LanguageSettingsScreen.kt
    │   ├── animations/AnimationListScreen.kt (289 lines) - Saved scripts
    │   ├── animations/AnimationEditorScreen.kt (424 lines) - Code, console, API reference
    │   └── home/HomeScreen.kt, home/HomeFeatures.kt (284 lines)
    ├── state/FeatureModels.kt (29 lines) - FeatureUiState, HomeUiState
    ├── viewmodel/
    │   ├── HomeViewModel.kt (272 lines) - Home state, feature toggles, the animation manager
    │   ├── AnimationStudioViewModel.kt (323 lines) - Studio state, Check / Glyph runs
    │   └── CustomAnimationsViewModel.kt (50 lines) - Script list for the pickers
    ├── theme/
    │   ├── Color.kt (22 lines) - Color definitions
    │   ├── ColorSchemes.kt (447 lines) - 12 light/dark color schemes
    │   ├── Type.kt (118 lines) - createTypography()
    │   ├── Typography.kt (146 lines) - getTypography(), Expressive metrics
    │   ├── Shapes.kt (62 lines) - getShapes() per theme style
    │   ├── ThemeColors.kt (65 lines) - Themed color helpers
    │   ├── ThemeState.kt (50 lines) - AppThemeStyle, dark/light state
    │   ├── GlyphZenTheme.kt (71 lines) - Theme composable, LocalFontState, LocalThemeState
    │   ├── LocalSettings.kt (36 lines) - LocalSettingsRepository, LocalVibrationIntensity
    │   └── FontState.kt (162 lines) - Font state management
    └── utils/HapticUtils.kt (349 lines) - Vibration system
```

---

## Quick Reference Card Patterns

Every card on every screen is one of six building blocks, so "which card do I want" is usually a one-line question.

**Feature cards** (`ui/components/cards/ContentCards.kt`)
1. **ContentCard** - the base; any slot layout you want, with the shared press animation and haptics
2. **FeatureCard** - icon, title, description; the building block for the two below
3. **SquareFeatureCard** - a 1:1 grid tile with an optional confirmation dialog
4. **WideFeatureCardWithToggle** - a feature row with a morphing switch, tinted by state
5. **GlyphControlCard** - the master glyph switch at the top of the home screen

**Settings cards** (`ui/components/layout/DraggableSettingsCard.kt`)
6. **DraggableSettingsCard** - a settings row that navigates on a horizontal swipe, with an optional trailing control

The eight home screen cards — seven in `ui/components/FeatureCards.kt`, plus `VpnConnectedCard` in `ui/components/VpnConnected.kt` — are all `WideFeatureCardWithToggle` plus a confirmation dialog from `ui/components/dialogs/`.

---

## License & Attribution

This codebase uses official Nothing Phone fonts and follows Material Design 3 guidelines. When using these components in your project:

- Ensure you have proper licensing for the Nothing fonts (or replace with your own)
- Maintain Material Design 3 compliance
- Test haptic feedback on physical devices
- Follow accessibility guidelines for visual and haptic elements

---

## Additional Resources

- [Material Design 3 Guidelines](https://m3.material.io/)
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [Android Haptic Feedback Best Practices](https://developer.android.com/develop/ui/views/haptics)
- [Canvas Drawing in Compose](https://developer.android.com/jetpack/compose/graphics/draw/overview)
- [LuaJ](https://github.com/luaj/luaj) — the Lua 5.2 VM behind the animation studio
- [Lua 5.2 Reference Manual](https://www.lua.org/manual/5.2/) — the language the `glyph` API sits in

---

**Last Updated:** 2025-11-29
**Component Version:** Based on Glyph-Sharge codebase
**Jetpack Compose:** 1.6.0+
**Material Design:** 3 (Material You)
