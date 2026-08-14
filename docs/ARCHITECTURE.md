# Architecture Documentation

Technical architecture and code structure documentation for developers.

## 🏗 System Architecture Overview

Glyph Sharge follows **Clean Architecture** principles with a modified **MVVM** (Model-View-ViewModel) pattern, optimized for Android development with Jetpack Compose.

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐ │
│  │  Screens    │  │  Components  │  │   ViewModels        │ │
│  │  (Compos-   │  │  (Reusable   │  │   (State &          │ │
│  │   ables)    │  │   UI)        │  │    Business Logic)  │ │
│  └─────────────┘  └──────────────┘  └─────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│                      Domain Layer                            │
│  ┌─────────────────┐  ┌──────────────────────────────────┐  │
│  │  Use Cases      │  │   Models / Entities              │  │
│  │  (Optional)     │  │   (Business Objects)             │  │
│  └─────────────────┘  └──────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                       Data Layer                             │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐ │
│  │Repositories │  │  Data Sources│  │   External SDK      │ │
│  │             │  │  - Room DB   │  │   - Glyph SDK       │ │
│  │             │  │  - SharedPreferences                  │ │
│  │             │  │  - Broadcast Receivers                │ │
│  └─────────────┘  └──────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## 📁 Package Structure

```
com.bleelblep.glyphsharge/
│
├── GlyphZenApplication.kt      # Application class (Hilt setup)
├── MainActivity.kt              # Single Activity architecture
│
├── di/                          # Dependency Injection
│   ├── AppModule.kt             # App-wide dependencies
│   ├── DatabaseModule.kt        # Room database setup
│   └── GlyphComponent.kt        # Glyph-related DI
│
├── data/                        # Data Layer
│   ├── model/                   # Data models
│   │   └── ChargingSession.kt   # Room entity
│   │
│   ├── repository/              # Repositories
│   │   └── ChargingSessionRepository.kt
│   │
│   ├── local/                   # Local data sources
│   │   ├── GlyphShargeDatabase.kt  # Room database
│   │   ├── ChargingSessionDao.kt   # Data Access Object
│   │   └── Migrations.kt           # Schema migrations
│   │
│   └── SettingsRepository.kt    # SharedPreferences wrapper
│
├── glyph/                       # Glyph Interface Core
│   ├── GlyphManager.kt          # SDK wrapper & session management
│   ├── GlyphFeatureCoordinator.kt  # Mutex-based LED access
│   └── GlyphAnimationManager.kt # Animation playback
│
├── services/                    # Foreground Services
│   ├── PulseLockService.kt         # Unlock animation
│   ├── PowerPeekService.kt         # Shake-to-check battery
│   ├── LowBatteryAlertService.kt   # Battery warnings
│   ├── ScreenOffGlyphService.kt    # Screen-off animations
│   ├── NfcGlyphService.kt          # NFC transaction feedback
│   ├── ChargingAnimationService.kt # Charging animations
│   ├── QuietHoursService.kt        # Scheduled silence
│   └── GlyphForegroundService.kt   # Base service class
│
├── receiver/                    # Broadcast Receivers
│   └── BootCompletedReceiver.kt # Auto-start on boot
│
├── ui/                          # UI Layer (Jetpack Compose)
│   ├── screens/                 # Full screens
│   │   ├── SettingsScreen.kt
│   │   ├── ThemeSettingsScreen.kt
│   │   ├── FontSettingsScreen.kt
│   │   ├── QuietHoursSettingsScreen.kt
│   │   └── LanguageSettingsScreen.kt
│   │
│   ├── components/              # Reusable components
│   │   ├── FeatureCards.kt      # Feature card composables
│   │   ├── CardComponents.kt    # Generic card layouts
│   │   ├── CommonDialogComponents.kt
│   │   ├── ChargingAnimation.kt
│   │   ├── PowerPeek.kt
│   │   ├── PulseLock.kt
│   │   ├── NfcGlyph.kt
│   │   ├── ScreenOff.kt
│   │   ├── LowBattery.kt
│   │   ├── WatermarkBox.kt
│   │   └── FontSettingsComponents.kt
│   │
│   ├── theme/                   # Theming system
│   │   ├── Color.kt             # Color definitions
│   │   ├── Type.kt              # Typography
│   │   ├── Theme.kt             # Theme configuration
│   │   ├── ThemeColors.kt       # Dynamic colors
│   │   └── FontState.kt         # Font state management
│   │
│   ├── utils/                   # UI utilities
│   │   └── HapticUtils.kt       # Vibration patterns
│   │
│   └── viewmodel/               # ViewModels
│       └── BatteryStoryViewModel.kt
│
└── utils/                       # General utilities
    ├── LoggingManager.kt        # Centralized logging
    └── WatermarkHelper.kt       # Watermark utilities
```

---

## 🔧 Dependency Injection (Hilt)

### Application Module

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideGlyphManager(@ApplicationContext context: Context): GlyphManager {
        return GlyphManager(context)
    }
    
    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository {
        return SettingsRepository(context)
    }
}
```

### Database Module

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GlyphShargeDatabase {
        return Room.databaseBuilder(
            context,
            GlyphShargeDatabase::class.java,
            "glyph_database"
        ).addMigrations(MIGRATION_1_2).build()
    }
    
    @Provides
    fun provideChargingSessionDao(database: GlyphShargeDatabase): ChargingSessionDao {
        return database.chargingSessionDao()
    }
}
```

### Service Injection

Services use `@AndroidEntryPoint` for injection:

```kotlin
@AndroidEntryPoint
class PowerPeekService : Service() {
    
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureCoordinator: GlyphFeatureCoordinator
    
    // ...
}
```

---

## 🎯 Core Components

### GlyphManager

**Purpose:** Wrapper around Nothing's Ketchum SDK providing simplified API.

**Responsibilities:**
- Initialize Glyph service connection
- Manage session lifecycle (open/close)
- Provide device-specific channel mappings
- Handle errors and reconnection
- Turn glyphs on/off

**Key Methods:**
```kotlin
fun initialize()                    // Start service binding
fun openSession()                   // Begin glyph session
fun closeSession()                  // End glyph session
fun turnOffAll()                    // Turn off all LEDs
fun turnOnAllGlyphs()              // Maximum brightness alert
fun forceEnsureSession(): Boolean  // Ensure session for bypass
fun isNothingPhone(): Boolean      // Device check
```

**Device Channel Mapping:**
```kotlin
// Phone (1) - 15 channels
object Phone1 {
    const val A1 = 0
    const val B1 = 1
    const val C1 = 2  // Through C4 = 5
    const val E1 = 6
    const val D1_1 = 7  // Through D1_8 = 14
}

// Phone (2) - 33 channels
object Phone2 {
    const val A1 = 0
    const val A2 = 1
    const val B1 = 2
    const val C1_1 = 3  // Through C1_16 = 18
    const val C2 = 19   // Through C6 = 23
    const val E1 = 24
    const val D1_1 = 25 // Through D1_8 = 32
}

// Phone (2a) - 26 channels
object Phone2a {
    const val A = 25
    const val B = 24
    const val C1 = 0    // Through C24 = 23
}

// Phone (3a) - 36 channels
object Phone3a {
    const val A1 = 20   // Through A11 = 30
    const val B1 = 31   // Through B5 = 35
    const val C1 = 0    // Through C20 = 19
}
```

---

### GlyphFeatureCoordinator

**Purpose:** Manages exclusive access to Glyph LEDs preventing conflicts.

**Implementation:**
```kotlin
@Singleton
class GlyphFeatureCoordinator @Inject constructor(
    private val glyphManager: GlyphManager
) {
    private val lock = Mutex()
    private val _currentOwner = MutableStateFlow<GlyphFeature?>(null)
    val currentOwner: StateFlow<GlyphFeature?> = _currentOwner.asStateFlow()
    
    suspend fun acquire(owner: GlyphFeature, timeoutMs: Long = 500L): Boolean {
        val acquired = withTimeoutOrNull(timeoutMs) {
            lock.lock()
            true
        } ?: false
        
        if (!acquired) return false
        
        _currentOwner.value = owner
        
        // Ensure session is active
        val ready = if (!glyphManager.isSessionActive) {
            withContext(Dispatchers.IO) {
                glyphManager.forceEnsureSession()
            }
        } else true
        
        if (!ready) {
            _currentOwner.value = null
            if (lock.isLocked) lock.unlock()
            return false
        }
        
        return true
    }
    
    fun release(owner: GlyphFeature) {
        if (_currentOwner.value != owner) return
        
        // Turn off LEDs before releasing lock
        runCatching { glyphManager.turnOffAll() }
        
        _currentOwner.value = null
        if (lock.isLocked) lock.unlock()
    }
}
```

**Usage Pattern:**
```kotlin
if (featureCoordinator.acquire(GlyphFeature.POWER_PEEK)) {
    try {
        // Play animation
        glyphAnimationManager.playAnimation(animationId)
    } finally {
        featureCoordinator.release(GlyphFeature.POWER_PEEK)
    }
}
```

---

### SettingsRepository

**Purpose:** Centralized settings management using SharedPreferences.

**Key Features:**
- Type-safe property access
- Flow-based reactive updates
- First-run defaults
- Version migrations

**Example:**
```kotlin
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    fun isPowerPeekEnabled(): Boolean {
        return prefs.getBoolean(KEY_POWER_PEEK_ENABLED, true)
    }
    
    fun savePowerPeekEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_POWER_PEEK_ENABLED, enabled) }
    }
    
    fun getShakeThreshold(): Float {
        return prefs.getFloat(KEY_SHAKE_THRESHOLD, DEFAULT_SHAKE_THRESHOLD)
    }
}
```

---

## 🔄 Service Architecture

### Foreground Service Pattern

All glyph features use foreground services with consistent pattern:

```kotlin
@AndroidEntryPoint
class ExampleService : Service() {
    
    companion object {
        private const val NOTIF_ID = 1000
        private const val CHANNEL_ID = "ExampleServiceChannel"
        const val ACTION_START = "com.bleelblep.glyphsharge.EXAMPLE_START"
        const val ACTION_STOP = "com.bleelblep.glyphsharge.EXAMPLE_STOP"
    }
    
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureCoordinator: GlyphFeatureCoordinator
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        
        when (intent?.action) {
            ACTION_START -> startFeature()
            ACTION_STOP -> stopFeature()
        }
        
        return START_STICKY
    }
    
    private fun startFeature() {
        scope.launch {
            if (featureCoordinator.acquire(GlyphFeature.EXAMPLE)) {
                try {
                    // Execute feature logic
                } finally {
                    featureCoordinator.release(GlyphFeature.EXAMPLE)
                }
            }
        }
    }
    
    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
```

### Service Communication

Services communicate via broadcasts and intents:

```kotlin
// Starting a service
val intent = Intent(context, PulseLockService::class.java).apply {
    action = PulseLockService.ACTION_START
}
ContextCompat.startForegroundService(context, intent)

// Stopping a service
val intent = Intent(context, PulseLockService::class.java).apply {
    action = PulseLockService.ACTION_STOP
}
context.startService(intent)
```

---

## 🎨 UI Architecture

### Single Activity Pattern

Glyph Sharge uses single activity with Compose navigation:

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject lateinit var settingsRepository: SettingsRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            GlyphShargeTheme {
                val navController = rememberNavController()
                
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") { HomeScreen() }
                    composable("settings") { SettingsScreen() }
                    // ... other routes
                }
            }
        }
    }
}
```

### Composable Structure

```kotlin
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    settingsRepository: SettingsRepository
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = { TopAppBar(...) },
        content = { padding ->
            LazyColumn(modifier = Modifier.padding(padding)) {
                item { PowerPeekCard(...) }
                item { PulseLockCard(...) }
                item { LowBatteryCard(...) }
                // ... more cards
            }
        }
    )
}
```

### Theme System

```kotlin
@Composable
fun GlyphShargeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

---

## 📊 Data Flow

### Settings Flow

```
User Input → ViewModel → Repository → SharedPreferences
                ↓
            StateFlow
                ↓
            UI Update (Compose)
```

### Glyph Animation Flow

```
Trigger Event → Service → FeatureCoordinator.acquire()
                              ↓
                        GlyphManager
                              ↓
                        Glyph SDK
                              ↓
                    Hardware LEDs
                              ↓
                    FeatureCoordinator.release()
```

---

## 🔐 Security Considerations

- **No sensitive data storage** — Settings are non-sensitive preferences
- **Service isolation** — Each feature runs in isolated scope
- **Permission validation** — Runtime permission checks before operations
- **SDK encapsulation** — Glyph SDK calls wrapped in error handling

---

## 🧪 Testing Strategy

### Unit Tests

```kotlin
@Test
fun `SettingsRepository returns default value when not set`() {
    val repository = SettingsRepository(context)
    assertTrue(repository.isPowerPeekEnabled()) // Default is true
}
```

### Integration Tests

```kotlin
@Test
fun `GlyphFeatureCoordinator prevents concurrent access`() = runTest {
    val coordinator = GlyphFeatureCoordinator(glyphManager)
    
    val acquired1 = coordinator.acquire(GlyphFeature.PULSE_LOCK)
    val acquired2 = coordinator.acquire(GlyphFeature.POWER_PEEK)
    
    assertTrue(acquired1)
    assertFalse(acquired2) // Should fail due to lock
}
```

---

## 📈 Performance Optimizations

1. **Coroutine Scopes** — Proper cancellation and structured concurrency
2. **Mutex over Synchronized** — Non-blocking lock attempts
3. **StateFlow** — Efficient state propagation
4. **Lazy Initialization** — Services initialized on demand
5. **Build Optimization** — R8 shrinking and resource optimization

---

## 🔮 Future Architecture Plans

- Modularization into feature modules
- Multi-module navigation graph
- Enhanced testing with MockK
- CI/CD pipeline integration
- Automated performance monitoring

---

**For API details, see [API_REFERENCE.md](API_REFERENCE.md)**
