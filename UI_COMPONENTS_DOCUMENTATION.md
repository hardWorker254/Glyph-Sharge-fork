# Glyph-Sharge UI Components Documentation

Complete reference guide for all UI elements, components, and styling systems.

---

## Table of Contents

1. [Overview](#overview)
2. [Theme System](#theme-system)
3. [Core Card Components](#core-card-components)
4. [Custom Animations & Visual Effects](#custom-animations--visual-effects)
5. [Specialized Components](#specialized-components)
6. [Utility Systems](#utility-systems)
7. [Usage Examples](#usage-examples)
8. [Integration Guide](#integration-guide)

---

## Overview

**Glyph-Sharge** is a Material 3 Jetpack Compose Android application with comprehensive UI components for Nothing Phone glyph interface management. The codebase contains ~14,000 lines of production-grade component code.

**Technology Stack:**
- Jetpack Compose (Material Design 3)
- Kotlin
- Custom animations with Canvas API
- Hardware-accelerated haptic feedback
- Dynamic theming system

---

## Theme System

### 1. Color System (`app/src/main/java/com/bleelblep/glyphsharge/ui/theme/Color.kt`)

```kotlin
// Brand Colors
val GlyphZenRed = Color(0xFFd71921)
val GlyphZenRedDark = Color(0xFFa01419)
val NothingRed = Color(0xFFD71921)
val NothingGray = Color(0xFF666666)
val NothingAccent = Color(0xFF00FF00)
val NothingWhite = Color(0xFFFFFFFF)
val NothingBlack = Color(0xFF000000)

// Light Theme
val NothingLightBackground = Color(0xFFF5F5F5)
val NothingLightSurface = Color(0xFFFFFFFF)

// Dark Theme
val NothingDarkBackground = Color(0xFF121212)
val NothingDarkSurface = Color(0xFF1E1E1E)
```

### 2. Shape System (`app/src/main/java/com/bleelblep/glyphsharge/ui/theme/Shape.kt`)

```kotlin
val Shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)
```

### 3. Typography System (`app/src/main/java/com/bleelblep/glyphsharge/ui/theme/Type.kt`)

Dynamic typography with scalable font sizes:

```kotlin
fun createTypography(
    titleFont: FontFamily,
    bodyFont: FontFamily,
    fontSizeSettings: FontSizeSettings = FontSizeSettings()
) = Typography(
    // Display Styles (largest text)
    displayLarge = TextStyle(
        fontFamily = titleFont,
        fontSize = 57.sp * displayScale,
        fontWeight = FontWeight.Bold
    ),
    displayMedium = TextStyle(
        fontFamily = titleFont,
        fontSize = 45.sp * displayScale,
        fontWeight = FontWeight.Bold
    ),
    displaySmall = TextStyle(
        fontFamily = titleFont,
        fontSize = 36.sp * displayScale,
        fontWeight = FontWeight.Bold
    ),

    // Headline Styles
    headlineLarge = TextStyle(
        fontFamily = titleFont,
        fontSize = 32.sp * titleScale,
        fontWeight = FontWeight.Bold
    ),
    headlineMedium = TextStyle(
        fontFamily = titleFont,
        fontSize = 28.sp * titleScale,
        fontWeight = FontWeight.SemiBold
    ),
    headlineSmall = TextStyle(
        fontFamily = titleFont,
        fontSize = 24.sp * titleScale,
        fontWeight = FontWeight.SemiBold
    ),

    // Title Styles
    titleLarge = TextStyle(
        fontFamily = titleFont,
        fontSize = 22.sp * titleScale,
        fontWeight = FontWeight.Medium
    ),
    titleMedium = TextStyle(
        fontFamily = titleFont,
        fontSize = 16.sp * titleScale,
        fontWeight = FontWeight.Medium
    ),
    titleSmall = TextStyle(
        fontFamily = titleFont,
        fontSize = 14.sp * titleScale,
        fontWeight = FontWeight.Medium
    ),

    // Body Styles
    bodyLarge = TextStyle(
        fontFamily = bodyFont,
        fontSize = 16.sp * bodyScale,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontFamily = bodyFont,
        fontSize = 14.sp * bodyScale,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontFamily = bodyFont,
        fontSize = 12.sp * bodyScale,
        fontWeight = FontWeight.Normal
    ),

    // Label Styles
    labelLarge = TextStyle(
        fontFamily = bodyFont,
        fontSize = 14.sp * labelScale,
        fontWeight = FontWeight.Medium
    ),
    labelMedium = TextStyle(
        fontFamily = bodyFont,
        fontSize = 12.sp * labelScale,
        fontWeight = FontWeight.Medium
    ),
    labelSmall = TextStyle(
        fontFamily = bodyFont,
        fontSize = 11.sp * labelScale,
        fontWeight = FontWeight.Medium
    )
)
```

### 4. Font State Management (`app/src/main/java/com/bleelblep/glyphsharge/ui/theme/FontState.kt`)

Custom font system with official Nothing fonts:

```kotlin
enum class FontVariant {
    HEADLINE,    // NType Headline (official Nothing font)
    NDOT,        // NDot 57 Caps (official Nothing font)
    SYSTEM       // Default system font
}

@Singleton
class FontState @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    var currentVariant by mutableStateOf(FontVariant.SYSTEM)
    var useCustomFonts by mutableStateOf(true)
    var fontSizeSettings by mutableStateOf(FontSizeSettings())

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

    fun setFontVariant(variant: FontVariant) { /* ... */ }
    fun updateFontSize(category: FontCategory, scale: Float) { /* ... */ }
    fun resetFontSizes() { /* ... */ }
}
```

### 5. App Theme Styles (`app/src/main/java/com/bleelblep/glyphsharge/ui/theme/Theme.kt`)

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
```

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

    surfaceVariant = Color(0xFF1A2F3D),
    onSurfaceVariant = Color(0xFF7DD3FF),
    outline = Color(0xFF00A3CC),

    error = Color(0xFFFF5252),
    onError = Color(0xFFFFFFFF)
)
```

---

## Core Card Components

### 1. StandardCard - Base Card Component

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/components/StandardCard.kt`

The primary card component with full feature set:

```kotlin
@Composable
fun StandardCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    description: String? = null,
    icon: ImageVector? = null,
    actionText: String? = null,
    onCardClick: (() -> Unit)? = null,
    onActionClick: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    elevation: Dp = 1.dp,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
)
```

**Features:**
- Animated press scale (0.98f on interaction)
- Haptic feedback on click
- Theme-aware styling (different shapes for EXPRESSIVE theme)
- Cut corners for EXPRESSIVE theme
- Flexible content slots

**Usage Example:**
```kotlin
StandardCard(
    title = "Battery Status",
    subtitle = "Last updated 5 minutes ago",
    description = "Your battery is at 75% and charging",
    icon = Icons.Default.BatteryChargingFull,
    actionText = "View Details",
    onCardClick = { navigateToBatteryDetails() },
    onActionClick = { showBatteryDialog() }
)
```

### 2. SimpleCard - Minimal Card Variant

```kotlin
@Composable
fun SimpleCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
)
```

**Usage Example:**
```kotlin
SimpleCard(
    title = "Quick Settings",
    description = "Adjust your preferences",
    onClick = { navigateToSettings() }
)
```

### 3. IconCard - Card with Icon Prominence

```kotlin
@Composable
fun IconCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    actionText: String? = null,
    onCardClick: (() -> Unit)? = null,
    onActionClick: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
)
```

**Usage Example:**
```kotlin
IconCard(
    title = "Notifications",
    subtitle = "3 new messages",
    icon = Icons.Default.Notifications,
    actionText = "View All",
    onActionClick = { showNotifications() }
)
```

### 4. ActionCard - Call-to-Action Card

```kotlin
@Composable
fun ActionCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    actionText: String,
    onActionClick: () -> Unit,
    content: (@Composable ColumnScope.() -> Unit)? = null
)
```

**Usage Example:**
```kotlin
ActionCard(
    title = "Enable Power Peek",
    description = "Shake to view battery level",
    actionText = "Enable Now",
    onActionClick = { enablePowerPeek() }
)
```

### 5. ContentCard - Custom Layout Card

```kotlin
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
)
```

**Usage Example:**
```kotlin
ContentCard(
    title = "Battery Statistics",
    content = {
        Text("Charged: 3 times today")
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = 0.75f,
            modifier = Modifier.fillMaxWidth()
        )
    }
)
```

---

## Custom Animations & Visual Effects

### 1. WavyProgressIndicator - Advanced Custom Animation

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/components/WavyProgressIndicator.kt`

Indeterminate and determinate progress with sine wave patterns:

```kotlin
// Indeterminate wavy progress
@Composable
fun LinearWavyProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    trackColor: Color = WavyProgressIndicatorDefaults.trackColor,
    strokeWidth: Dp = WavyProgressIndicatorDefaults.LinearIndicatorStrokeWidth,
    trackStrokeWidth: Dp = WavyProgressIndicatorDefaults.LinearTrackStrokeWidth,
    gapSize: Dp = WavyProgressIndicatorDefaults.LinearIndicatorTrackGapSize,
    amplitude: Float = 1.0f,
    wavelength: Dp = WavyProgressIndicatorDefaults.LinearIndeterminateWavelength,
    waveSpeed: Dp = wavelength
)

// Determinate progress (with percentage)
@Composable
fun LinearWavyProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    trackColor: Color = WavyProgressIndicatorDefaults.trackColor,
    strokeWidth: Dp = WavyProgressIndicatorDefaults.LinearIndicatorStrokeWidth,
    trackStrokeWidth: Dp = WavyProgressIndicatorDefaults.LinearTrackStrokeWidth,
    gapSize: Dp = WavyProgressIndicatorDefaults.LinearIndicatorTrackGapSize,
    amplitude: Float = 1.0f,
    wavelength: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength
)
```

**Features:**
- Mathematical sine wave generation using Canvas
- Performance optimized with memoized calculations
- Configurable amplitude, wavelength, and speed
- Infinite animation for indeterminate mode
- Progress clipping for determinate mode

**Default Values:**
```kotlin
object WavyProgressIndicatorDefaults {
    val LinearIndicatorStrokeWidth = 4.dp
    val LinearTrackStrokeWidth = 4.dp
    val LinearIndicatorTrackGapSize = 0.dp
    val LinearIndeterminateWavelength = 32.dp
    val LinearDeterminateWavelength = 24.dp

    val indicatorColor: Color
        @Composable get() = MaterialTheme.colorScheme.primary

    val trackColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant
}
```

**Usage Examples:**

```kotlin
// Indeterminate wavy progress
LinearWavyProgressIndicator(
    amplitude = 0.8f,
    wavelength = 32.dp,
    waveSpeed = 20.dp,
    modifier = Modifier.fillMaxWidth()
)

// Determinate progress showing 60%
LinearWavyProgressIndicator(
    progress = 0.6f,
    amplitude = 1.0f,
    wavelength = 24.dp,
    modifier = Modifier.fillMaxWidth()
)

// Custom styled wavy progress
LinearWavyProgressIndicator(
    progress = 0.75f,
    color = Color.Cyan,
    trackColor = Color.DarkGray,
    strokeWidth = 6.dp,
    amplitude = 1.2f,
    modifier = Modifier.fillMaxWidth()
)
```

### 2. TransparentTopAppBar - Scroll-Aware App Bar

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/components/TransparentTopAppBar.kt`

```kotlin
@Composable
fun TransparentTopAppBar(
    title: String,
    onBackClick: () -> Unit,
    scrollState: LazyListState,
    actions: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
)
```

**Features:**
- Transparent when at top of scroll
- Becomes solid background on scroll
- Haptic feedback on back button
- Status bar window insets support
- Smooth color transitions

**Usage Example:**
```kotlin
val scrollState = rememberLazyListState()

Scaffold(
    topBar = {
        TransparentTopAppBar(
            title = "Settings",
            onBackClick = { navController.navigateUp() },
            scrollState = scrollState,
            actions = {
                IconButton(onClick = { /* action */ }) {
                    Icon(Icons.Default.MoreVert, "More")
                }
            }
        )
    }
) { paddingValues ->
    LazyColumn(state = scrollState, contentPadding = paddingValues) {
        // Content
    }
}
```

### 3. WatermarkBox - Diagonal Repeating Watermark

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/components/WatermarkBox.kt`

```kotlin
@Composable
fun WatermarkBox(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = "TESTING",
    color: Color = Color.Gray,
    alpha: Float = 0.12f,
    rotation: Float = 315f,
    fontSize: TextUnit = 14.sp,
    spacing: Dp = 48.dp,
    content: @Composable () -> Unit
)
```

**Features:**
- Native Canvas rendering using Android Paint
- Diagonal 315° rotation
- Repeated tiling pattern
- Configurable opacity and font size
- Lightweight implementation

**Usage Example:**
```kotlin
WatermarkBox(
    enabled = BuildConfig.DEBUG,
    text = "PREVIEW",
    color = Color.Red,
    alpha = 0.15f,
    spacing = 64.dp
) {
    // Your content here
    YourAppContent()
}
```

### 4. EmojiPainter - Emoji as Painter

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/components/EmojiPainter.kt`

```kotlin
@Composable
fun rememberEmojiPainter(
    emoji: String,
    fontSizeDp: Float = 32f
): Painter
```

**Features:**
- Converts emoji to Painter for use in Icon components
- Preserves full color of emoji glyphs
- Handles multi-codepoint emoji correctly

**Usage Example:**
```kotlin
Icon(
    painter = rememberEmojiPainter("🔋", fontSizeDp = 24f),
    contentDescription = "Battery",
    tint = Color.Unspecified  // Don't tint emoji
)
```

---

## Specialized Components

### 1. HapticTestCard - Vibration Testing

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/components/HapticTestCard.kt`

```kotlin
@Composable
fun HapticTestCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true
)

@Composable
fun HapticSettingsCard(
    settingsRepository: SettingsRepository,
    onNavigateToFullSettings: () -> Unit,
    modifier: Modifier = Modifier
)
```

**Features:**
- Shows current haptic intensity (Light/Medium/Strong)
- Quick intensity selection buttons
- Real-time haptic preview
- Color-coded intensity indicators
- Animated button state transitions

**Usage Example:**
```kotlin
HapticSettingsCard(
    settingsRepository = settingsRepository,
    onNavigateToFullSettings = {
        navController.navigate("haptic_settings")
    }
)

HapticTestCard(
    title = "Light Haptic",
    description = "Subtle vibration feedback",
    onClick = {
        HapticUtils.performLightHaptic(context, haptic, intensity)
    }
)
```

### 2. ExpressiveComponents - Material 3 Expressive Patterns

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/components/ExpressiveComponents.kt`

```kotlin
@Composable
fun SplitButtonGroup(
    actions: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.buttonColors()
)

@Composable
fun ExtendedFabMenu(
    items: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier
)

@Composable
fun ExpressiveProgressIndicator(
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer
)
```

**Usage Example:**
```kotlin
SplitButtonGroup(
    actions = listOf(
        "Enable" to { enableFeature() },
        "Test" to { testFeature() },
        "Configure" to { configureFeature() }
    )
)

ExtendedFabMenu(
    items = listOf(
        "New Item" to { createNew() },
        "Import" to { importData() },
        "Export" to { exportData() }
    )
)
```

### 3. FontSettingsComponents - Font Customization

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/ui/components/FontSettingsComponents.kt`

```kotlin
@Composable
fun ThreeStateFontToggle(
    currentVariant: FontVariant,
    onVariantSelected: (FontVariant) -> Unit,
    modifier: Modifier = Modifier
)
```

**Features:**
- Three-state morphing toggle (HEADLINE / NDOT / SYSTEM)
- Shape animation on selection
- Color animation for different variants
- Size animation (44.dp → 52.dp on selection)
- Spring-based animations for smooth transitions

**Visual States:**
- **HEADLINE**: Purple (0xFF7B1FA2) - "H" label
- **NDOT**: Purple (0xFF7B1FA2) - "N" label
- **SYSTEM**: Red (0xFFd71921) - "S" label

**Usage Example:**
```kotlin
ThreeStateFontToggle(
    currentVariant = fontState.currentVariant,
    onVariantSelected = { newVariant ->
        fontState.setFontVariant(newVariant)
    }
)
```

---

## Utility Systems

### 1. HapticUtils - Comprehensive Vibration Management

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/utils/HapticUtils.kt`

```kotlin
object HapticUtils {
    object Intensity {
        const val OFF = 0
        const val LIGHT = 85          // ~33%
        const val MEDIUM = 170        // ~66%
        const val STRONG = 255        // 100%
        const val DEFAULT = -1
    }

    object Timing {
        const val SHORT_DURATION = 50L
        const val MEDIUM_DURATION = 100L
        const val LONG_DURATION = 200L
    }

    // Predefined haptic effects
    fun performLightHaptic(
        context: Context,
        haptic: HapticFeedback?,
        intensity: Float
    )

    fun performMediumHaptic(
        context: Context,
        haptic: HapticFeedback?,
        intensity: Float
    )

    fun performStrongHaptic(
        context: Context,
        haptic: HapticFeedback?,
        intensity: Float
    )

    fun performClickHaptic(
        context: Context,
        haptic: HapticFeedback?,
        intensity: Float
    )

    fun performSuccessHaptic(
        context: Context,
        haptic: HapticFeedback?,
        intensity: Float
    )

    fun performErrorHaptic(
        context: Context,
        haptic: HapticFeedback?,
        intensity: Float
    )

    // Generic haptic with type
    fun performHapticWithIntensity(
        context: Context,
        haptic: HapticFeedback?,
        userIntensity: Float,
        type: HapticType
    )

    // Utility functions
    fun convertUserIntensityToAndroidAmplitude(userIntensity: Float): Int
    fun testVibrationIntensity(context: Context, intensity: Float)
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
- Cross-API vibrator support (Android O+)
- Amplitude control with validation
- Pattern-based vibration effects
- Device capability detection
- Intensity conversion (0.0-1.0 to 1-255)

**Usage Examples:**

```kotlin
// Simple predefined haptics
HapticUtils.performClickHaptic(context, hapticFeedback, 0.5f)
HapticUtils.performSuccessHaptic(context, hapticFeedback, 0.8f)
HapticUtils.performErrorHaptic(context, hapticFeedback, 1.0f)

// Using haptic types
HapticUtils.performHapticWithIntensity(
    context,
    hapticFeedback,
    userIntensity = 0.66f,
    type = HapticType.MEDIUM
)

// Test vibration
HapticUtils.testVibrationIntensity(context, intensity = 0.75f)

// Check device capabilities
val info = HapticUtils.getVibrationInfo(context)
if (info.hasAmplitudeControl) {
    // Use fine-grained amplitude control
}
```

### 2. AnimationUtils - Hardware-Accelerated Animations

**Location:** `app/src/main/java/com/bleelblep/glyphsharge/utils/AnimationUtils.kt`

```kotlin
object AnimationUtils {
    object Timing {
        const val FAST = 150
        const val NORMAL = 250
        const val SLOW = 350
    }

    object Scale {
        const val SUBTLE_IN = 0.97f
        const val SUBTLE_OUT = 1.03f
        const val GENTLE_IN = 0.95f
        const val GENTLE_OUT = 1.05f
    }

    val SmoothEasing = FastOutSlowInEasing
    val QuickEasing = EaseOut
    val ResponsiveEasing = EaseIn

    fun createSmoothEnterTransition(
        duration: Int = Timing.NORMAL,
        scaleFrom: Float = Scale.GENTLE_IN
    ): EnterTransition

    fun createSmoothExitTransition(
        duration: Int = Timing.NORMAL,
        scaleTo: Float = Scale.GENTLE_OUT
    ): ExitTransition

    object Springs {
        val Bouncy = spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
        val Smooth = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
        val Quick = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        )
    }
}
```

**Usage Examples:**

```kotlin
// Using predefined animations
AnimatedVisibility(
    visible = isVisible,
    enter = AnimationUtils.createSmoothEnterTransition(),
    exit = AnimationUtils.createSmoothExitTransition()
) {
    YourContent()
}

// Using spring animations
val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.95f else 1f,
    animationSpec = AnimationUtils.Springs.Smooth
)

// Using timing constants
LaunchedEffect(key1) {
    delay(AnimationUtils.Timing.NORMAL.toLong())
    doSomething()
}
```

---

## Usage Examples

### Example 1: Feature Card with Progress

```kotlin
StandardCard(
    title = "Charging Progress",
    subtitle = "Fast charging enabled",
    icon = Icons.Default.BatteryChargingFull,
    content = {
        LinearWavyProgressIndicator(
            progress = 0.75f,
            amplitude = 0.8f,
            wavelength = 32.dp,
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
)
```

### Example 2: Settings Screen with Haptic Feedback

```kotlin
@Composable
fun SettingsScreen(
    navController: NavController,
    settingsRepository: SettingsRepository
) {
    val scrollState = rememberLazyListState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TransparentTopAppBar(
                title = "Settings",
                onBackClick = { navController.navigateUp() },
                scrollState = scrollState
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = scrollState,
            contentPadding = paddingValues,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                HapticSettingsCard(
                    settingsRepository = settingsRepository,
                    onNavigateToFullSettings = {
                        HapticUtils.performClickHaptic(context, haptic, 0.5f)
                        navController.navigate("haptic_settings")
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                ThreeStateFontToggle(
                    currentVariant = fontState.currentVariant,
                    onVariantSelected = { variant ->
                        HapticUtils.performLightHaptic(context, haptic, 0.4f)
                        fontState.setFontVariant(variant)
                    }
                )
            }
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

### Example 4: Watermarked Preview Screen

```kotlin
@Composable
fun PreviewScreen() {
    WatermarkBox(
        enabled = BuildConfig.DEBUG,
        text = "PREVIEW BUILD",
        color = MaterialTheme.colorScheme.error,
        alpha = 0.15f,
        fontSize = 16.sp,
        spacing = 72.dp
    ) {
        // Your main content
        LazyColumn {
            items(previewItems) { item ->
                StandardCard(
                    title = item.title,
                    description = item.description,
                    icon = item.icon,
                    onCardClick = { /* handle click */ }
                )
            }
        }
    }
}
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
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = AnimationUtils.Springs.Quick
    )

    Button(
        onClick = {
            HapticUtils.performClickHaptic(context, haptic, 0.5f)
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

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.8.2")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
}
```

**2. Minimum SDK:**
```kotlin
minSdk = 26  // Android 8.0 (Oreo)
```

**3. Permissions:**

Add to `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.VIBRATE" />
```

### Font Setup

**1. Add Font Files:**

Place these font files in `app/src/main/res/font/`:
- `ntype_82_headline.ttf` - NType Headline (official Nothing font)
- `ndot55caps.ttf` - NDot 57 Caps (official Nothing font)
- `ntype_82_regular.ttf` - NType Regular (official Nothing font)

**Note:** These are official Nothing fonts. You may need to use your own fonts or obtain proper licensing.

**2. Create Font Resources:**

```kotlin
// In your theme setup
val headlineFont = FontFamily(
    Font(R.font.ntype_82_headline, FontWeight.Normal),
    Font(R.font.ntype_82_headline, FontWeight.Medium),
    Font(R.font.ntype_82_headline, FontWeight.Bold)
)
```

### Theme Configuration

**1. Set up CompositionLocals:**

```kotlin
// Create composition locals for theme state
val LocalThemeState = compositionLocalOf<ThemeState> {
    error("No ThemeState provided")
}

val LocalFontState = compositionLocalOf<FontState> {
    error("No FontState provided")
}
```

**2. Wrap Your App:**

```kotlin
@Composable
fun YourApp() {
    val themeState = remember { ThemeState() }
    val fontState = remember { FontState() }

    CompositionLocalProvider(
        LocalThemeState provides themeState,
        LocalFontState provides fontState
    ) {
        AppTheme(
            darkTheme = themeState.isDarkMode,
            themeStyle = themeState.currentStyle
        ) {
            // Your app content
            NavHost(...)
        }
    }
}
```

### Using Components

**1. Import Components:**

```kotlin
import com.bleelblep.glyphsharge.ui.components.StandardCard
import com.bleelblep.glyphsharge.ui.components.WavyProgressIndicator
import com.bleelblep.glyphsharge.utils.HapticUtils
import com.bleelblep.glyphsharge.utils.AnimationUtils
```

**2. Use in Composables:**

```kotlin
@Composable
fun MyScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LazyColumn {
        item {
            StandardCard(
                title = "My Feature",
                description = "Feature description",
                icon = Icons.Default.Star,
                onCardClick = {
                    HapticUtils.performClickHaptic(context, haptic, 0.5f)
                    // Handle click
                }
            )
        }
    }
}
```

### Customization Tips

**1. Override Card Colors:**

```kotlin
StandardCard(
    title = "Custom Card",
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
)
```

**2. Adjust Haptic Intensity:**

```kotlin
// Store user preference
val userHapticIntensity = 0.75f  // 75% intensity

HapticUtils.performHapticWithIntensity(
    context,
    haptic,
    userHapticIntensity,
    HapticType.MEDIUM
)
```

**3. Custom Wave Animations:**

```kotlin
LinearWavyProgressIndicator(
    progress = batteryLevel / 100f,
    color = when {
        batteryLevel > 80 -> Color.Green
        batteryLevel > 20 -> Color.Yellow
        else -> Color.Red
    },
    amplitude = 1.2f,
    wavelength = 28.dp
)
```

---

## Performance Considerations

### 1. Animation Optimization

- **WavyProgressIndicator** uses memoized calculations for efficient rendering
- Spring animations use appropriate damping ratios to prevent jank
- Scale animations on cards use hardware layer when possible

### 2. Card Rendering

- Minimal recomposition with `remember` and `derivedStateOf`
- Efficient state management with `mutableStateOf`
- Proper key usage in LazyColumn/LazyRow

### 3. Haptic Performance

- Device capability detection before operations
- API level checks for compatibility
- Graceful fallback for older devices

### 4. Memory Management

- Emoji painter caches bitmap representations
- Font families loaded once and reused
- Color schemes pre-computed

---

## File Structure Summary

**Total Component Code:** ~14,044 lines

### Main Component Files

```
app/src/main/java/com/bleelblep/glyphsharge/ui/
├── components/
│   ├── StandardCard.kt (272 lines) - Core card system
│   ├── WavyProgressIndicator.kt (317 lines) - Custom animations
│   ├── HapticTestCard.kt (250 lines) - Vibration integration
│   ├── FontSettingsComponents.kt (500+ lines) - Font management
│   ├── PowerPeekComponents.kt (1000+ lines) - Shake detection
│   ├── PulseLockComponents.kt (1300+ lines) - Unlock animations
│   ├── LowBatteryAlertComponents.kt (1300+ lines) - Battery alerts
│   ├── CardExamples.kt (2100+ lines) - Usage examples
│   ├── HomeCardTemplates.kt (5100+ lines) - Template variations
│   ├── TransparentTopAppBar.kt (65 lines) - App bar
│   ├── WatermarkBox.kt (92 lines) - Watermark overlay
│   └── EmojiPainter.kt (42 lines) - Emoji support
├── theme/
│   ├── Color.kt - Color definitions
│   ├── Type.kt - Typography configuration
│   ├── Shape.kt - Shape definitions
│   ├── FontState.kt - Font state management
│   └── Theme.kt - Complete theme system (6 styles)
└── utils/
    ├── HapticUtils.kt (377 lines) - Vibration system
    └── AnimationUtils.kt (98 lines) - Animation helpers
```

---

## Quick Reference Card Patterns

The codebase includes 10 ready-to-use card patterns in `CardQuickReference.kt`:

1. **Feature Card** - Standard feature presentation
2. **Settings Card** - Navigation to settings with icon
3. **Status Card** - Informational display
4. **Statistics Card** - Display metrics/data
5. **Control Card** - Action buttons (On/Off)
6. **Notification Card** - Alert-style presentation
7. **Progress Card** - Progress tracking with wavy indicator
8. **List Item Card** - For use in LazyColumn
9. **Empty State Card** - When no data available
10. **Error State Card** - Error handling display

See `CardExamples.kt` for complete implementations of all patterns.

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

---

**Last Updated:** 2025-11-29
**Component Version:** Based on Glyph-Sharge codebase
**Jetpack Compose:** 1.6.0+
**Material Design:** 3 (Material You)
