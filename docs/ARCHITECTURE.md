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

CustomAnimationsActivity          # студия анимаций на Lua, вне NavHost
    └── BackHandler              # свой стек: список ↔ редактор
    └── ActivityResultLauncher   # OpenDocument / CreateDocument
```

> [!NOTE]
> `CustomAnimationsActivity` — единственное исключение из правила одной Activity.
> У неё собственный стек возврата и собственные системные файловые диалоги, которые
> не должны жить в общем навграфе настроек. В манифесте она помечена
> `android:exported="false"` и `parentActivityName=".MainActivity"`.

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
│                   #   + GlyphAnimations.kt (каталог + rememberAnimationOptions)
├── screens/        # Экраны приложения
│   └── animations/ #   Студия Lua-анимаций: список, редактор, превью
├── theme/          # Темы и стили
├── utils/          # UI утилиты
└── viewmodel/      # HomeViewModel, AnimationStudioViewModel
```

**Ответственность:**
- Отображение данных
- Обработка пользовательского ввода
- Анимации и визуальные эффекты

### Domain Layer (Business Logic)

**Расположение:** `glyph/`, `services/`

```
glyph/
├── GlyphManager.kt            # Сессия Nothing SDK
├── GlyphAnimationManager.kt   # Точки входа анимаций
├── GlyphFeatureCoordinator.kt # Координация функций
├── AnimationCatalog.kt        # id анимаций
├── device/                    # Раскладка LED и тайминги по модели
├── engine/                    # Сборка кадров и обработка ошибок
├── animations/                # Сами анимации
├── script/                    # Пользовательские анимации на Lua
│   ├── ScriptAnimation.kt     # Модель скрипта, id custom:<12 hex>
│   ├── ScriptFileFormat.kt    # Контейнер .glyphlua
│   ├── LuaScriptEngine.kt     # LuaJ: песочница, сторож, validate()
│   ├── ScriptSession.kt       # Состояние прогона и прерываемые паузы
│   ├── GlyphLuaApi.kt         # Таблица glyph — весь язык скрипта
│   └── ScriptRunner.kt        # Связь VM с GlyphRenderer
└── battery/                   # Анимация заряда и Power Peek

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
├── CustomAnimationRepository.kt   # Скрипты пользователя: файлы + индекс
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
- Обработка ошибок и восстановление

```kotlin
class GlyphManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun initialize()
    fun openSession()
    fun closeSession()
    fun turnOffAll()
    fun cleanup()
    // ...
}
```

Номера каналов он не хранит — они живут в `device/DeviceProfileFactory.kt`.

### GlyphRenderer

Движок отрисовки: сборка кадров, флаг `isRunning`, обработка ошибок и примитив `pulse()`.
Единственный класс, который обращается к `GlyphManager.mGM` напрямую.

### GlyphAnimationManager

Публичные точки входа для всех анимаций.

**Основные обязанности:**
- Проверка настроек и поддержки устройства
- Воспроизведение сценариев фич
- Диспетчеризация по id из настроек

Сама отрисовка вынесена в `animations/` и `battery/`, пользовательские скрипты — в `script/`.

### Пакет `glyph/script/` — движок пользовательских анимаций

Скрипты на Lua (движок **LuaJ 3.0.1**, `org.luaj:luaj-jse`, чисто-JVM Lua 5.2) выполняются
отдельным движком, но подключены к тому же фасаду. Идентификаторы скриптов
пространственно именованы: `custom:<12 hex>`, поэтому `ScriptAnimation.isCustomId(id)`
отличает их от встроенных id без таблицы соответствий.

**Поток данных:**

```
настройка фичи (id custom:<12 hex>)
  → CustomAnimationRepository          поиск исходника (файлы + индекс в SharedPreferences)
  → GlyphAnimationManager.playAnimation
        └─ isCustomId(id) ? playCustomAnimation() : GlyphAnimationId.of(id)
  → ScriptRunner.runScript(source, durationMs)   Dispatchers.Default
  → LuaScriptEngine.run                          песочница + сторож
  → GlyphScriptHost (RendererHost)               runBlocking поверх suspend-рендерера
  → GlyphRenderer → GlyphManager                  реальные светодиоды
```

**Границы подсистем.** Движок никогда не видит `GlyphRenderer`: он получает узкий интерфейс
`GlyphScriptHost` (`draw`, `blank`, `batteryPercent`, `isCharging`). Это делает его
тестируемым на обычной JVM и делает очевидным, что именно может скрипт. Единственное место,
где VM соединяется с железом, — `ScriptRunner`.

**Обход песочницы.** Окружение строится вычитанием из `JsePlatform.standardGlobals()`:
удаляются `io`, `os`, `package`, `require`, `module`, `dofile`, `loadfile`, `load`,
`loadstring`, **`luajava`** (рефлексивный мост в Java — настоящий выход из песочницы),
`coroutine`, `collectgarbage`, `newproxy`. `DebugLib` загружается, сторож навешивается,
и только затем таблица `debug` обнуляется. `print` перенаправлен в консоль студии.

**Сторож.** Скрипт — произвольный код, управляющий железом из foreground-сервиса, поэтому у
него два предела, оба проверяются из count-хука (интервал 20 000 инструкций): настенные часы
(настройка Duration фичи) и потолок в 200 000 000 инструкций. Хук бросает
`ScriptAbortedError : Error` — не Lua-ошибку, которую проглотил бы `pcall`. Любая пауза
нарезается кусками по 16 мс, поэтому остановка замечается сразу, а не в конце
`glyph.hold(60000)`.

### Студия анимаций

`CustomAnimationsActivity` — **отдельная Activity** (`android:exported="false"`),
а не маршрут `GlyphNavHost`. Свои `ActivityResultLauncher` для `OpenDocument` и
`CreateDocument` и `BackHandler` между списком и
редактором. Экраны: `ui/screens/animations/` (`AnimationListScreen`,
`AnimationEditorScreen`, `GlyphPreview`), состояние — `AnimationStudioViewModel`.

Два способа попробовать скрипт:

| Режим | Хост | Работает на эмуляторе |
|-------|------|------------------------|
| **Screen** | `PreviewHost` — записывает кадры `Map<Int, Int>` вместо зажигания LED | Да |
| **Glyph** | `ScriptRunner` → настоящий `GlyphRenderer` | Нет |

Первый использует `DeviceProfileFactory.forPreview()` — раскладку Phone (3a) как
универсальную сетку, чтобы превью работало на любом телефоне. Настоящие светодиоды
этот профиль никогда не используют.

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
  - glyph/script — движок пользовательских анимаций на Lua
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

Новые возможности для пользователя добавляются расширением таблицы `glyph`
в `GlyphLuaApi.kt` — это единственное место, где описан весь язык скрипта.

---

*Документация актуальна для версии 1.0.30*
