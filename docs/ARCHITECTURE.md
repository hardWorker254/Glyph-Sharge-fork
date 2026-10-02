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
- Локальный источник: один `SharedPreferences`, разбитый на срезы по областям
- Файлы пользовательских скриптов (`.glyphlua`) через `CustomAnimationRepository`
- Внешние API (Nothing Glyph SDK)

### 2. Dependency Injection (Hilt)

Приложение использует Hilt для внедрения зависимостей:

```kotlin
@Singleton
class GlyphManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) { ... }

@AndroidEntryPoint
class MainActivity : ComponentActivity() { ... }

@HiltViewModel
class HomeViewModel @Inject constructor(...) : ViewModel()
```

**Модули:**
- `di/AppModule.kt` — единственный `@Module` в приложении. Он провайдит один
  `@Singleton @GlyphPrefs SharedPreferences` (файл `glyphzen_settings`) — тот самый, который
  читает каждый срез настроек. Квалификатор `@GlyphPrefs` существует, чтобы срез не мог
  получить «какой-нибудь» `SharedPreferences`: `Context` один, а файлов настроек может быть
  несколько.

**Как UI достаёт зависимости:**
- Экраны получают `ViewModel` через `hiltViewModel()` — `HomeScreen` и `GlyphNavHost` не принимают
  ни одного параметра с зависимостью
- Настройки попадают в дерево композиции через `LocalSettingsRepository`
  (`ui/theme/LocalSettings.kt`), который каждая Activity провайдит у себя в корне
- Интенсивность вибрации лежит рядом в `LocalVibrationIntensity`: `HapticUtils` — обычный
  `object`, вызываемый из обработчиков кликов, а обработчик не может запросить репозиторий
- Кнопки «Test» в диалогах фич берут менеджер анимаций через
  `rememberGlyphAnimationManager()` (`ui/components/GlyphDependencies.kt`), который возвращает
  тот же экземпляр, что держит `HomeViewModel`

### 3. Repository Pattern

В `data/` лежит ровно одно хранилище — `SharedPreferences`, разбитый на срезы по
областям. Каждый срез владеет своими ключами и дефолтами, а `SettingsRepository` — тонкий
фасад поверх них.

```kotlin
@Singleton
class SettingsRepository @Inject constructor(
    private val theme: ThemeSettings,
    private val fonts: FontSettings,
    private val glyphService: GlyphServiceSettings,
    private val features: FeatureSettings,
    private val quietHours: QuietHoursSettings,
    private val language: LanguageSettings,
    private val userPresence: UserPresenceSettings,
    @Suppress("unused") private val migrations: SettingsMigrations,
    private val diagnostics: SettingsDiagnostics
) {
    fun saveThemeStyle(themeStyle: AppThemeStyle) = theme.saveThemeStyle(themeStyle)
    fun getThemeStyle(): AppThemeStyle = theme.getThemeStyle()
    // ...остальные методы — та же история
}
```

Ключи, дефолты и миграции от этого не изменились: миграции лежат в `SettingsMigrations`,
дефолты — в самих срезах, а фасад не содержит ни одного ключа.

### 4. Single Activity Architecture

```
MainActivity
    └── GlyphNavHost                 # маршруты — константы в ui/navigation/Routes.kt
        ├── HomeScreen               # home
        ├── SettingsScreen           # settings
        ├── ThemeSettingsScreen      # theme_settings
        ├── FontSettingsScreen       # font_settings
        ├── LanguageSettingsScreen   # language_settings
        └── QuietHoursSettingsScreen # quiet_hours_settings

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
│                   #   + GlyphDependencies.kt (rememberGlyphAnimationManager)
│                   #   + cards/, controls/, dialogs/, layout/
├── screens/        # Экраны приложения
│   ├── home/       #   Главный экран
│   └── animations/ #   Студия Lua-анимаций: список, редактор, превью
├── navigation/     # Routes и GlyphNavHost
├── state/          # FeatureUiState, HomeUiState
├── theme/          # Темы, типографика, CompositionLocals
├── utils/          # UI утилиты (HapticUtils)
└── viewmodel/      # HomeViewModel, AnimationStudioViewModel, CustomAnimationsViewModel
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
├── GlyphAnimationManager.kt   # Точки входа анимаций (фасад)
├── GlyphFeatureCoordinator.kt # Mutex + enum GlyphFeature — единственный арбитр полосы
├── AnimationCatalog.kt        # id анимаций
├── RunTrace.kt
├── device/                    # Раскладка LED и тайминги по модели
├── engine/                    # Сборка кадров и обработка ошибок
├── animations/                # Сами анимации
│   ├── AnimationRunner.kt     #   Общие проверки и жизненный цикл «погасить — отрисовать — погасить»
│   ├── BuiltInAnimations.kt   #   Базовые анимации
│   ├── AudioAnimations.kt
│   ├── ParticleAnimations.kt
│   └── SequenceAnimations.kt
├── audio/                     # Визуализатор музыки
│   ├── MusicVisualisation.kt
│   ├── AudioAnalyzer.kt, AudioAnalysis.kt, AudioFrame.kt, AudioFrameFeed.kt
│   ├── Fft.kt
│   ├── MusicVisualizationMode.kt, PlaybackAudioSource.kt
│   └── SyntheticTrack.kt      # Синтетический трек для превью
├── script/                    # Пользовательские анимации на Lua
│   ├── ScriptAnimation.kt     # Модель скрипта, id custom:<12 hex>
│   ├── ScriptFileFormat.kt    # Контейнер .glyphlua
│   ├── LuaScriptEngine.kt     # LuaJ: песочница, сторож, validate()
│   ├── ScriptSession.kt       # Состояние прогона и прерываемые паузы
│   ├── GlyphLuaApi.kt         # Таблица glyph — весь язык скрипта
│   ├── LogLevel.kt            # INFO / WARN / ERROR и строка консоли
│   ├── ScriptPlayback.kt      # Хостинг скрипта на полосе
│   ├── ScriptTarget.kt        # Каким пикерам виден скрипт
│   ├── ScriptRunner.kt        # Связь VM с GlyphRenderer
│   └── module/                # Шесть имён, которые резолвит require("…")
│       ├── ModuleRegistry.kt  # Закрытый набор и require, который его читает
│       ├── ModuleBuilder.kt   # value / live / func — сборка одного модуля
│       ├── ModuleSourceScan.kt# Имена require("…"), которые Check обязан отвергнуть
│       ├── LuaArgs.kt         # Позиционные аргументы функций модулей
│       └── GlyphTimeModule.kt, GlyphBatteryModule.kt, GlyphNetModule.kt,
│           GlyphSensorModule.kt, GlyphLogModule.kt, GlyphUtilModule.kt
├── net/                       # То, что читает glyph.net
│   ├── NetworkSnapshot.kt     # connected / wifi / metered / vpn одним моментом
│   └── NetworkSource.kt       # ConnectivityManager за кэшем в 1 с
├── sensor/                    # То, что читает glyph.sensor
│   ├── SensorSnapshot.kt      # x / y / z / magnitude / shaken, одно измерение
│   ├── SensorControl.kt       # Что открывает и закрывает слушатель прогона
│   └── SensorSource.kt        # Слушатель и порог встряхивания
└── battery/                   # Анимация заряда и Power Peek

services/
├── FeatureSpec.kt             # Реестр: фича → сервис → стоп-действие → настройка
├── FeatureServiceController.kt
├── GlyphForegroundService.kt
├── ChargingAnimationService.kt
├── NfcGlyphService.kt
├── LowBatteryAlertService.kt
├── PowerPeekService.kt
├── PulseLockService.kt
├── QuietHoursService.kt
├── ScreenOffGlyphService.kt
├── VpnConnectedService.kt
└── MusicVisualizerService.kt
```

**Ответственность:**
- Бизнес-логика приложения
- Координация между компонентами
- Управление состоянием системы

**Реестр как «core».** `FeatureSpecs` — это то, что у проекта нет отдельного слоя `core/`:
единственное место, где фича сопоставлена со своим сервисом, стоп-действием, парой
переключателей настроек и сообщением «выключи глиф-сервис». `FeatureServiceController` решает,
*что* делать с фичей, но никогда — *какой* это сервис и какая настройка.

### Data Layer

**Расположение:** `data/`

Хранилище — `SharedPreferences`. Ключи поделены между срезами по областям; фасад
`SettingsRepository` — тонкий слой над ними.

```
data/
├── ThemeSettings.kt         # Тема: тёмная/светлая, стиль
├── FontSettings.kt          # Вариант шрифта и размеры по категориям
├── GlyphServiceSettings.kt  # Главный переключатель глифов, вибрация, язык устройства
├── FeatureSettings.kt       # Вкл/выкл, анимация и длительность каждой фичи
├── QuietHoursSettings.kt    # Расписание тихих часов
├── LanguageSettings.kt      # Язык приложения
├── UserPresenceSettings.kt  # Данные о присутствии пользователя
├── SettingsMigrations.kt    # Переносы старых настроек
├── SettingsDiagnostics.kt   # Диагностика хранилища
├── SettingsPrefs.kt         # Типизированные reified-хелперы чтения/записи
├── SettingsRepository.kt    # Фасад: 78 однострочных делегатов срезам
└── CustomAnimationRepository.kt   # Скрипты пользователя: файлы + индекс
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

Фасад: публичные точки входа для всего, что зажигает полосу. Отрисовки в нём нет — здесь
только то, что касается *приложения*: какой id из настроек играть, сколько он может длиться
и как вызывающий себя ограничивает. Публичных точек входа **24**, из них `suspend` — 21;
остальные три (`runCapMs`, `checkScript`, `stopAnimations`) обычные, потому что ничего не
рисуют. Все они безопасно вызываются конкурентно: `stopAnimations()` переворачивает флаг
отмены рендерера, а по окончании или отмене последовательности глифы всегда гасятся.

Работа разделена по назначению, чтобы изменение оставалось в одном месте:

| Задача | Где |
|--------|-----|
| Какие каналы есть на этом телефоне | `device/DeviceProfileFactory` |
| Сборка кадров, ошибки, отмена | `engine/GlyphRenderer` |
| Проверки и жизненный цикл «погасить — отрисовать — погасить» | `animations/AnimationRunner` |
| Волна / обход / мигание / частицы | `animations/BuiltInAnimations`, `AudioAnimations`, `ParticleAnimations`, `SequenceAnimations` |
| Визуализатор музыки и его превью | `audio/MusicVisualisation` |
| Заряд и полоса батареи | `battery/BatteryGlyphAnimator` |
| Хостинг пользовательского Lua-скрипта | `script/ScriptPlayback` |
| Исполнение самого Lua | `script/ScriptRunner` |

`AnimationRunner` владеет ленивым `DeviceProfile?` (`null` на неподдерживаемом железе) и
методом `anim(...)` — общей обвязкой, из которой берут и встроенные последовательности, и
визуализатор, и полоса батареи.

### Ограничение прогона: `runCapped`

Скрипт и любая анимация работают на общем железе, поэтому у прогона есть потолок. Вместо того
чтобы каждому сервису копировать связку «запустить → watchdog → отменить», она живёт
один раз в `GlyphAnimationManager.runCapped(capMs, onTimeout, block)`:

```kotlin
suspend fun <T> runCapped(capMs: Long, onTimeout: () -> Unit = {}, block: suspend () -> T): T
```

Прогон стартует на `Dispatchers.Default`, сторож спит `capMs`; если анимация закончилась сама,
сторож отменяется и не срабатывает. Если сработал — работа отменяется и дожидается своей
очистки, затем вызывается `stopAnimations()` и `onTimeout()`, чтобы сервис мог сделать то, о
чём менеджер не знает (например, выключить звук low-battery алерта).

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
удаляются `io`, `os`, `package`, `module`, `dofile`, `loadfile`, `load`, `loadstring`,
**`luajava`** (рефлексивный мост в Java — настоящий выход из песочницы), `coroutine`,
`collectgarbage`, `newproxy`. `DebugLib` загружается, сторож навешивается, и только затем
таблица `debug` обнуляется. `print` перенаправлен в консоль студии.

**`require` — исключение, и не случайно.** Он тоже в `BANNED_GLOBALS`: там стоит запись о
том, до чего достаёт *штатный* `require`, то есть до файловой системы через `package.path`.
Сразу после цикла запретов он перезаписывается на `ModuleRegistry.requireFor(session)`,
которая резолвит ровно шесть имён в памяти — `glyph.time`, `glyph.battery`, `glyph.net`,
`glyph.sensor`, `glyph.log`, `glyph.util` — и больше ничего. Пути поиска за ней нет,
файловой системы нет, `package` по-прежнему `nil`. Убрать `require` из списка было бы
логичнее на вид, но написало бы в коде «скрипты не могут делать require», а это перестало
быть правдой.

**Фабрики, а не таблицы.** `ModuleRegistry` хранит `(ScriptSession) -> LuaTable`, и каждый
прогон получает свежую таблицу. Одновременно скрипты исполняют несколько foreground-сервисов
(анимация зарядки и визуализация музыки делают это регулярно), и общая таблица позволила бы
двум прогонам читать и писать состояние друг друга. Каждое поле модуля при этом резолвится
через `__index` **при каждом чтении**: часы, заряд и положение телефона меняются, пока идёт
анимация, и именно ради реакции на них модуль и спрашивают. Ни `glyph.sensor` (слушатель
запускается на первом чтении поля, а не на `require`), ни `glyph.net` (только состояние сети,
без сокета) не добавили ни сервиса, ни разрешения.

**Проверка исходника.** `LuaScriptEngine.validate` компилирует — а этого мало: `require
("glyph.nett")` — корректное выражение, опечатка переживает чистый разбор и взрывается на
глифе посреди анимации. `ModuleSourceScan` вырезает комментарии и находит имена, которых нет
в реестре; `validate` возвращает типизированный `ScriptCheckResult`
(`OK` / `SYNTAX_ERROR` / `MISSING_MODULE`), и ошибка разбора сообщается первой, потому что у
нечитаемого файла осмысленного `require` нет.

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
`AnimationEditorScreen`), состояние — `AnimationStudioViewModel`.

Способ проверить скрипт один: **Check** компилирует исходник и сообщает о первой найденной
проблеме, ничего не рисуя, — а это теперь две разные находки, ошибка синтаксиса и `require`,
называющий несуществующий модуль, потому что слова им нужны разные; **Glyph** запускает его на
настоящем железе — единственный вердикт, который что-то значит. Экранного превью нет
намеренно: схема раскладки светодиодов — не то же самое, что загоревшаяся полоса, а второй
режим запуска провоцирует спутать одно с другим. `DeviceProfileFactory.forPreview()`
существует как универсальная сетка Phone (3a), но настоящие светодиоды её не используют.

Консоль студии показывает собственный вывод скрипта — `print(...)` и `glyph.log.*` — а затем
вердикт о прогоне. `ScriptRunResult` несёт `logLines: List<ScriptLogLine>`, прочитанные из
сессии до её разбора, потому что `finally` движка закрывает сессию и чистит активный слот
на каждом выходе; вызывающий, спросивший строки позже, получил бы пустой список ровно на
прогонах, которые важнее всего. `ConsoleLine` несёт `LogLevel` (`INFO` / `WARN` / `ERROR`),
а не флаг `isError`, потому что у вывода скрипта три серьёзности, и отличить
предупреждение от fatal-заметки двухсостоянийный флаг не может.

Сессия глифов принадлежит тому экрану, который сейчас впереди: `MainActivity` закрывает её в
`onStop()`, когда выключен сервис, — а `CustomAnimationsActivity` открывает сессию у себя и
возвращает её ровно в том виде, в каком нашёл. В `onStop()` студия гасит текущий прогон:
выход из студии не должен оставить полосу горящей.

### GlyphFeatureCoordinator

Единственный арбитр полосы LED. За ним стоит `Mutex` и единственный в проекте enum
`GlyphFeature` — семь значений, которые перечисляют всё, что умеет зажигать глифы. Одновременно
полосу держит ровно один владелец; текущий публикуется в `currentOwner: StateFlow<GlyphFeature?>`.

**Основные обязанности:**
- Приоритизация фич
- Разрешение конфликтов
- Выдача и освобождение полосы

`withStrip(owner, preempt, timeoutMs, onRelease, block): T?` — это и есть тот способ, которым
сервис берёт полосу, запускает анимацию и отдаёт полосу назад. Освобождение стоит в `finally`
внутри самого `withStrip`, потому что вернувшийся, бросивший или отменённый сервис оставил бы
захваченным не просто свет, а сам `Mutex` — и `release` игнорирует того, кто уже не владелец.
`preempt = true` для того, что пользователь только что сделал (разблокировка, тап, выключение
экрана): текущий владелец не смещается, а получает команду прекратить рисование и сам
отпускает полосу в своём `finally`. `null` означает, что полоса не была взята, — вызывающий
отличает это от «взял и отработал» без собственного флага.

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
| VpnConnectedService | Анимация при подключении VPN | NetworkCallback на TRANSPORT_VPN |
| MusicVisualizerService | Визуализатор музыки | Foreground (MediaProjection) |

Сопоставление «фича → сервис → стоп-действие → переключатель настройки» лежит не в сервисах,
а в `services/FeatureSpec.kt`: `FeatureSpec` описывает фичу одной записью, а `FeatureSpecs`
(`all`, `of(feature)`) — единственный список. `FeatureServiceController`
берёт оттуда всё, кроме решения *что* делать. `FeatureSpec.isRunnable(settings)` отвечает сразу
на оба вопроса, которые задаёт себе каждый сервис: включён ли мой собственный переключатель и
включён ли главный глиф-сервис.

### Плитки быстрых настроек

Две плитки в шторке — `tiles/GlyphServiceTileService` и `tiles/MusicVisualizerTileService`. Это
`TileService`, то есть обычные сервисы, но объявленные иначе:

```xml
<service
    android:name=".tiles.MusicVisualizerTileService"
    android:exported="true"
    android:icon="@drawable/ic_tile_music"
    android:label="@string/tile_music_label"      <!-- не длиннее 18 символов -->
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
    <intent-filter>
        <action android:name="android.service.quicksettings.action.QS_TILE" />
    </intent-filter>
    <meta-data android:name="android.service.quicksettings.ACTIVE_TILE" android:value="true" />
    <meta-data android:name="android.service.quicksettings.TOGGLEABLE_TILE" android:value="true" />
</service>
```

`BIND_QUICK_SETTINGS_TILE` оставляет сервис доступным только системе, а `exported="true"`
всё равно нужен — без него плитка не появится в списке добавления. Две плитки на приложение —
это потолок, который задаёт сам Android; третья фича обоснована как отдельный экран или
уведомление, а не как ещё одна плитка.

**Один выключатель на два места.** Логику главного переключателя вынесли из `HomeViewModel` в
`services/GlyphServiceSwitch.kt`, и теперь её зовут и карточка, и плитка. Это единственное
место, которое знает про порядок: флаг пишется **до** старта сервисов (иначе сервис, читающий
флаг в `onStartCommand`, выключится сам, а `GlyphForegroundService` ещё и перезапустит себя из
`onDestroy`), а сессия открывается через `forceEnsureSession()`, который ждёт привязки системного
сервиса. Метод блокирующий, поэтому `apply()` — `suspend` и уводит работу с главного потока;
вызывающему не нужно помнить, что `TileService.onClick()` нельзя блокировать.

**Плитка не имеет права показывать больше, чем есть.** Переключатель фичи и главный
переключатель — два отдельных флага, а сервис проверяет оба. Выключение главного оставляет
фичу «включённой», но неработающей: это сохранённая настройка, а не то, что сейчас рисует
глифы. Поэтому `MusicVisualizerTileService.isRunning()` требует оба флага, и загорается плитка
тогда, когда визуализация действительно работает. Тап при этом считается от того, что плитка
показывает, а не от одного флага: тап по негорящей плитке обязан что-то включать.

**Обновление состояния.** `tiles/TileStateBus.kt` — один сигнал «переключатель, который ты
зеркалишь, сдвинулся», и он намеренно идёт двумя каналами:

- локальный broadcast (`RECEIVER_NOT_EXPORTED`) — для плитки, которая прямо сейчас слушает;
- `TileService.requestListeningState()` — для плитки, которая не слушает, потому что шторка
  закрыта. Только этот вызов заставляет систему прислать `onStartListening()`, а это единственный
  момент, в который платформа гарантирует, что плитка перечитает своё состояние.

Поэтому подписка живёт не окном `onStartListening()/onStopListening()`, а временем жизни
сервиса: чаще всего состояние меняется, когда шторка закрыта.

**Разрешения визуализации.** Токен `MediaProjection` возвращается только как результат
Activity, и плитка не может ни показать системный диалог, ни запросить `RECORD_AUDIO`. Поэтому
`MusicVisualizerTileService` вызывает `startActivityAndCollapse()` и передаёт запрос в
`MainActivity` через `ACTION_ENABLE_MUSIC`, а та — уже цепочкой из двух запросов, общей с
переключателем карточки. Цепочка живёт в `MainActivity`, а не в карточке, ещё и потому, что
карточка визуализации последняя в списке и к моменту открытия приложения из плитки может быть
ещё не скомпонована. Выдав разрешение, Activity закрывается через `finishAndRemoveTask()`:
`finish()` оставил бы задачу в списке недавних, что на шторке выглядит как «приложение
свернулось», а не как «вопрос задан и закрыт».

## Навигация

Используется Navigation Compose. Маршруты — константы в `ui/navigation/Routes.kt`, а весь граф
живёт в одном месте, `GlyphNavHost`:

```kotlin
@Composable
fun GlyphNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen() }
        composable(Routes.SETTINGS) { SettingsScreen(...) }
        composable(Routes.THEME_SETTINGS) { ThemeSettingsScreen(...) }
        composable(Routes.FONT_SETTINGS) { FontSettingsScreen(...) }
        // ...
    }
}
```

Хост не принимает зависимостей: каждый экран берёт нужное из композиции — хранилище из
`LocalSettingsRepository`, `ViewModel` из `hiltViewModel()`. Поэтому экран остаётся достижимым
из превью или теста без этого хоста.

## Управление состоянием

### StateFlow для реактивного состояния

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val glyphManager: GlyphManager,
    val glyphAnimationManager: GlyphAnimationManager,
    private val serviceController: FeatureServiceController,
    private val playbackAudioSource: PlaybackAudioSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()
}
```

`HomeUiState` и `FeatureUiState` живут в `ui/state/FeatureModels.kt`. Всплывающие сообщения —
одноразовые события, а не состояние, поэтому они идут через `Channel`, а не через
`StateFlow`: держать Toast в состоянии означало бы показать его снова при каждой
рекомпозиции.

`glyphAnimationManager` у `HomeViewModel` — публичный: диалоги фич — обычные composable, и
`hiltViewModel()` резолвится внутри диалога так же, как внутри экрана. Это и есть точка
инъекции, которой пользуются их кнопки «Test» (см. `rememberGlyphAnimationManager()`).

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

### Логирование

`utils/LoggingManager.kt` — обычный `object`, а не инъецируемая зависимость: он пишет в
файл, а не хранит состояние приложения. `initialize(context)` вызывается при старте, дальше
всё через статические методы, а сам лог включается и выключается пользователем.

```kotlin
object LoggingManager {
    fun initialize(context: Context)
    fun setLoggingEnabled(enabled: Boolean)
    fun isLoggingEnabled(): Boolean
    fun log(tag: String, message: String)
    fun logSDKOperation(operation: String, details: String)
    fun logSessionState(state: String, details: String = "")
    fun logFrameOperation(operation: String, channels: List<Int>, brightness: Int? = null)
    fun exportLogs(): String
    // ...
}
```

### Восстановление после ошибок

`GlyphManager` сам следит за сессией SDK и переподключается сам; `GlyphRenderer` глотает ошибку
отрисовки, логирует её и гасит полосу вместо того, чтобы выпустить её в сервис. Разбор по
сообщениям, который раньше прятался в обработчике, теперь живёт там, где возникла ошибка, —
и не дублируется в каждом вызывающем.

## Масштабируемость

### Модульность

Приложение структурировано по функциональным модулям:
- glyph — управление_glyph interface
  - glyph/script — движок пользовательских анимаций на Lua
  - glyph/animations — встроенные анимации и общий раннер
  - glyph/audio — визуализатор музыки
- services — фоновые сервисы и реестр фич
- ui — пользовательский интерфейс
- data — хранилище настроек и скрипты пользователя

Это позволяет:
- Легко добавлять новые функции
- Изолировать изменения
- Упростить тестирование

### Расширяемость

Новая функция добавляется в проекте четырьмя точками, а не двенадцатью:
1. Новое значение в `GlyphFeature` (`glyph/GlyphFeatureCoordinator.kt`) — единственный enum в проекте
2. Новая запись `FeatureSpec` в `services/FeatureSpec.kt`: сервис, стоп-действие, пара переключателей, сообщение
3. Сам сервис в `services/`, берущий полосу через `GlyphFeatureCoordinator.withStrip(...)` и ограничивающий прогон через `GlyphAnimationManager.runCapped(...)`
4. Карточка и диалог в `ui/components/`, читающие настройки из `LocalSettingsRepository`

Сопоставление фичи с сервисом и переключателем лежит в `FeatureSpecs`; `FeatureServiceController`
только исполняет, а `GlyphFeatureCoordinator` только арбитрирует — ни один из них не знает,
какая именно фича включена.

Новые возможности для пользователя добавляются двумя разными точками. Таблица `glyph`
расширяется в `GlyphLuaApi.kt` — это по-прежнему единственное место, где описан весь язык
скрипта. Новый **модуль** добавляется иначе: файл `Glyph<Имя>Module.kt` в
`glyph/script/module/` плюс одна запись `Имя.NAME to …::build` в `factories` реестра.
`ModuleBuilder` берёт на себя две ошибки, которые иначе пришлось бы ловить глазами:
значение, меняющееся во время прогона, сохранённое обычным полем, и функция, привязанная без
префикса имени модуля. Что появилось у пользователя, описано в
[разделе 14](DOCUMENTATION_RU.md#14-пользовательские-анимации-lua).

---

*Документация актуальна для версии 1.0.31*
