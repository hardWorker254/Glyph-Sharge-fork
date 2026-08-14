# Архитектура Glyph Sharge

Этот документ описывает архитектурные решения и паттерны, используемые в проекте Glyph Sharge.

## Обзор архитектуры

Приложение построено на основе современных рекомендаций Android разработки с использованием следующих паттернов:

### 1. MVVM (Model-View-ViewModel)

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│     View        │────▶│   ViewModel     │────▶│     Model       │
│  (Composables)  │◀────│  (State Logic)  │◀────│  (Repository)   │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

**View (UI Layer)**
- Jetpack Compose компоненты
- Наблюдение за StateFlow/LiveData
- Обработка пользовательских событий

**ViewModel (Presentation Layer)**
- Бизнес-логика UI
- Управление состоянием
- Координация с Repository

**Model (Data Layer)**
- Repository для доступа к данным
- Локальные источники (Room, DataStore)
- Внешние API (Nothing Glyph SDK)

### 2. Dependency Injection (Hilt)

Приложение использует Hilt для внедрения зависимостей:

```kotlin
@Singleton
class GlyphManager @Inject constructor(
    @ApplicationContext private val context: Context
) { ... }

@AndroidEntryPoint
class MainActivity : ComponentActivity() { ... }
```

**Модули:**
- `AppModule` — предоставление синглтонов приложения
- `GlyphComponent` — компоненты для glyph-менеджеров

### 3. Repository Pattern

```kotlin
interface SettingsRepository {
    suspend fun getThemeSettings(): ThemeSettings
    suspend fun updateThemeSettings(settings: ThemeSettings)
    // ...
}

class SettingsRepositoryImpl @Inject constructor(
    private val dao: SettingsDao,
    private val dataStore: DataStore<Preferences>
) : SettingsRepository { ... }
```

### 4. Single Activity Architecture

```
MainActivity
    └── NavHost
        ├── SettingsScreen
        ├── ThemeSettingsScreen
        ├── FontSettingsScreen
        ├── LanguageSettingsScreen
        └── QuietHoursSettingsScreen
```

### 5. Unidirectional Data Flow

```
User Event → ViewModel → Repository → Data Source
                ↓
            StateFlow
                ↓
              UI (Compose)
```

## Слои приложения

### Presentation Layer (UI)

**Расположение:** `ui/`

```
ui/
├── components/     # Переиспользуемые компоненты
├── screens/        # Экраны приложения
├── theme/          # Темы и стили
└── utils/          # UI утилиты
```

**Ответственность:**
- Отображение данных
- Обработка пользовательского ввода
- Анимации и визуальные эффекты

### Domain Layer (Business Logic)

**Расположение:** `glyph/`, `services/`

```
glyph/
├── GlyphManager.kt           # Управление сессией глифов
├── GlyphAnimationManager.kt  # Управление анимациями
└── GlyphFeatureCoordinator.kt # Координация функций

services/
├── GlyphForegroundService.kt
├── ChargingAnimationService.kt
├── NfcGlyphService.kt
├── LowBatteryAlertService.kt
├── PowerPeekService.kt
├── PulseLockService.kt
├── QuietHoursService.kt
└── ScreenOffGlyphService.kt
```

**Ответственность:**
- Бизнес-логика приложения
- Координация между компонентами
- Управление состоянием системы

### Data Layer

**Расположение:** `data/`

```
data/
├── SettingsRepository.kt
└── local/
    └── Migrations.kt
```

**Ответственность:**
- Хранение данных
- Предоставление данных бизнес-слою
- Абстракция источников данных

## Компоненты Glyph Interface

### GlyphManager

Центральный класс для работы с Nothing Glyph SDK.

**Основные обязанности:**
- Инициализация SDK
- Управление сессией
- Контроль каналов глифов
- Обработка ошибок и восстановление

```kotlin
class GlyphManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun initialize()
    fun openSession()
    fun closeSession()
    fun turnOnAllGlyphs()
    fun turnOffAll()
    // ...
}
```

### GlyphAnimationManager

Управляет анимациями глифов.

**Основные обязанности:**
- Регистрация анимаций
- Воспроизведение паттернов
- Синхронизация с сервисами

### GlyphFeatureCoordinator

Координирует взаимодействие между различными функциями глифов.

**Основные обязанности:**
- Приоритизация функций
- Разрешение конфликтов
- Координация сервисов

## Фоновые сервисы

Каждый сервис реализует конкретную функцию:

| Сервис | Функция | Тип |
|--------|---------|-----|
| GlyphForegroundService | Поддержание сессии | Foreground |
| ChargingAnimationService | Анимация зарядки | Bound |
| NfcGlyphService | NFC интеграция | Bound |
| LowBatteryAlertService | Мониторинг батареи | Broadcast Receiver |
| PowerPeekService | Встряхивание для проверки | Sensor-based |
| PulseLockService | Анимации при включении | Bound |
| QuietHoursService | Тихие часы | Scheduled |
| ScreenOffGlyphService | Анимации при выключении | Bound |

## Навигация

Используется Navigation Compose с типобезопасной навигацией:

```kotlin
@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = "settings") {
        composable("settings") { SettingsScreen() }
        composable("theme") { ThemeSettingsScreen() }
        composable("font") { FontSettingsScreen() }
        // ...
    }
}
```

## Управление состоянием

### StateFlow для реактивного состояния

```kotlin
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    fun updateTheme(theme: AppThemeStyle) {
        viewModelScope.launch {
            repository.saveTheme(theme)
            _uiState.update { it.copy(theme = theme) }
        }
    }
}
```

### sealed class для представления состояний

```kotlin
sealed class GlyphState {
    object Disconnected : GlyphState()
    object Connecting : GlyphState()
    object Connected : GlyphState()
    data class Error(val message: String) : GlyphState()
}
```

## Обработка ошибок

### Глобальный обработчик ошибок

```kotlin
class LoggingManager @Inject constructor() {
    fun logError(tag: String, message: String, throwable: Throwable?)
    fun logSessionState(state: String, details: String)
    fun logSDKOperation(operation: String, result: String)
}
```

### Восстановление после ошибок

```kotlin
private fun handleError(error: Exception) {
    when (error) {
        is GlyphException -> {
            when (error.message) {
                "Session not active" -> attemptReconnection()
                "Service not connected" -> attemptReconnection()
                else -> cleanup()
            }
        }
        else -> cleanup()
    }
}
```

## Масштабируемость

### Модульность

Приложение структурировано по функциональным модулям:
- glyph — управление_glyph interface
- services — фоновые сервисы
- ui — пользовательский интерфейс
- data — слой данных

Это позволяет:
- Легко добавлять новые функции
- Изолировать изменения
- Упростить тестирование

### Расширяемость

Новые функции добавляются через:
1. Создание нового сервиса в `services/`
2. Добавление UI компонентов в `ui/components/`
3. Обновление координатора `GlyphFeatureCoordinator`

---

*Документация актуальна для версии 1.0.30*
