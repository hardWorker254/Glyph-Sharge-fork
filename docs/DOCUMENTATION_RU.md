# Документация проекта Glyph Sharge (Glyph Zen)

> Полное техническое руководство для разработчиков. Актуально для версии **1.0.31** (versionCode 1031).
> Англоязычная версия: [DOCUMENTATION_EN.md](DOCUMENTATION_EN.md)

---

## Содержание

1. [Обзор проекта](#1-обзор-проекта)
2. [Требования и запуск](#2-требования-и-запуск)
3. [Архитектура](#3-архитектура)
4. [Слой Glyph](#4-слой-glyph)
5. [Слой данных](#5-слой-данных)
6. [Сервисы](#6-сервисы)
7. [**Добавление нового сервиса (Quickstart)**](#7-добавление-нового-сервиса-quickstart)
8. [Слой UI](#8-слой-ui)
9. [Навигация](#9-навигация)
10. [Музыкальная визуализация](#10-музыкальная-визуализация)
11. [Ресурсы и локализация](#11-ресурсы-и-локализация)
12. [Сборка и конфигурация](#12-сборка-и-конфигурация)
13. [Известные проблемы и подводные камни](#13-известные-проблемы-и-подводные-камни)
14. [**Пользовательские анимации (Lua)**](#14-пользовательские-анимации-lua)

---

## 1. Обзор проекта

**Glyph Sharge** — приложение для управления световой интерфейсией **Nothing Glyph** на телефонах Nothing.
Приложение даёт полный контроль над светодиодами: индикация заряда, уведомления, безопасность, персонализация.

> ⚠️ Это **не** официальный продукт Nothing Technology Limited.

### Ключевые возможности

| Область | Возможности |
|---------|-------------|
| 🔌 Питание | Анимации зарядки, отображение уровня батареи, оповещения о низком заряде, Power Peek (тряска → проверка батареи) |
| 🔒 Безопасность | Pulse Lock (анимация при разблокировке), Screen Off (анимация при блокировке) |
| 📡 Интеграция | NFC-глифы по событию оплаты, анимация глифов при подключении VPN |
| 🎨 Персонализация | 6 стилей тем, шрифты Nothing (NType Headline, NDot 55 Caps), масштабирование размеров текста, собственные анимации глифов на Lua |
| ⚙️ Система | Тихие часы, автозапуск после загрузки, журналирование |

### Поддерживаемые устройства

| Устройство | Модель | Статус |
|------------|--------|--------|
| Nothing Phone (1) | 20111 | Без тестировщиков |
| Nothing Phone (2) | 22111 | Без тестировщиков |
| Nothing Phone (2a) | 23111 / 23113 | Без тестировщиков |
| Nothing Phone (3a) | 24111 | ✅ Полная поддержка |

Определение модели выполняется в [DeviceType](../app/src/main/java/com/bleelblep/glyphsharge/glyph/device/DeviceType.kt)
через `Common.is20111()` и подобные проверки. На неподдерживаемом устройстве приложение всё
равно запускается, но глифы работать не будут.

### Ключевые факты проекта

| Параметр | Значение |
|----------|----------|
| Package / namespace | `com.bleelblep.glyphsharge` |
| Application ID | `com.bleelblep.glyphsharge` |
| minSdk / targetSdk | **34** / 34 (Android 14+) |
| compileSdk | 36 |
| JVM target | 17 |
| Язык | Kotlin 1.9.10, Jetpack Compose (Material 3) |
| DI | Hilt 2.50 (KSP) |
| Хранилище настроек | SharedPreferences (файл `glyphzen_settings`) |
| Скрипты анимаций | LuaJ 3.0.1 (`org.luaj:luaj-jse`) — чисто-JVM Lua 5.2 |
| SDK глифов | `app/libs/KetchumSDK_Community_20250319.jar` (официальный ключ Nothing) |

> [!IMPORTANT]
> В проекте **нет debug-режима для глифов** — используется официальный API-ключ Nothing,
> прописанный в `AndroidManifest.xml` как `<meta-data android:name="NothingKey" .../>`.
> Значение `"test"` подставлять не нужно.

---

## 2. Требования и запуск

### Требования

- **JDK 17** (обязательно — активный build-скрипт выставляет `sourceCompatibility 17`)
- **Android Studio** Hedgehog (2023.1.1) или новее
- **Android SDK** 36 (compile), 34 (min)
- Физический телефон Nothing для проверки глифов

### Клонирование и сборка

```bash
git clone https://github.com/hardWorker254/Glyph-Sharge-fork.git
cd Glyph-Sharge-fork
```

Открыть в Android Studio, дождаться синхронизации Gradle, затем:

```bash
# Debug-сборка и установка
./gradlew app:assembleDebug
./gradlew app:installDebug

# Релизная сборка
./gradlew app:assembleRelease
```

> [!NOTE]
> **Все build-скрипты — на Kotlin DSL**: `build.gradle.kts`, `settings.gradle.kts` и
> `app/build.gradle.kts`. Groovy-скриптов в проекте нет, и любая версия плагина или
> библиотеки берётся из `gradle/libs.versions.toml` — единственного источника правды.

### Проверка на устройстве

Глифы физически отсутствуют на эмуляторе. Проверяйте на реальном Nothing Phone.
Для проверки UI без устройства Nothing приложение всё равно запустится —
`isNothingPhone()` вернёт `false`, карточки останутся неактивными.

---

## 3. Архитектура

Проект построен на **MVVM + Unidirectional Data Flow + Repository + Hilt DI**.

```mermaid
graph TD
    UI["UI Layer<br/>Compose Screens, Cards, Dialogs"]
    STUDIO["CustomAnimationsActivity<br/>студия Lua-анимаций"]
    VM["ViewModel<br/>HomeViewModel"]
    SVC["Services Layer<br/>9 ForegroundServices"]
    CTRL["FeatureServiceController<br/>маршрутизация фичи → сервис"]
    SPEC["FeatureSpec registry<br/>фича → сервис и настройка"]
    REPO["SettingsRepository<br/>SharedPreferences"]
    CREPO["CustomAnimationRepository<br/>файлы .glyphlua + индекс"]
    COORD["GlyphFeatureCoordinator<br/>взаимное исключение"]
    GM["GlyphManager<br/>сессия Ketchum SDK"]
    ANIM["GlyphAnimationManager<br/>точки входа анимаций"]
    SRUN["ScriptRunner<br/>связь VM с железом"]
    LUA["LuaScriptEngine<br/>песочница и сторож"]
    REND["GlyphRenderer<br/>движок отрисовки кадров"]
    PROF["DeviceProfileFactory<br/>раскладка LED по модели"]
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

### Слои

| Слой | Пакет | Ответственность |
|------|-------|-----------------|
| **Glyph** | `glyph/` | Сессия Nothing SDK, низкоуровневая работа с каналами, все анимации, арбитраж доступа к LED. Разделён на `device/` (раскладка по модели), `engine/` (отрисовка кадров), `animations/`, `audio/` и `battery/` (сами эффекты), `script/` (пользовательские анимации на Lua) |
| **Data** | `data/` | Настройки пользователя, миграции, значения по умолчанию |
| **Services** | `services/` | Foreground-сервисы, реагирующие на системные события, плюс реестр `FeatureSpec` |
| **UI** | `ui/` | Compose-экраны, карточки, диалоги, темы, шрифты, навигация |
| **DI** | `di/` | Единственный `@Module`: отдаёт `SharedPreferences` настроек под квалификатором `@GlyphPrefs` |
| **Utils** | `utils/` | Логирование |
| **Receiver** | `receiver/` | Восстановление сервисов после перезагрузки |

### Структура каталогов

```
app/src/main/java/com/bleelblep/glyphsharge/
├── GlyphZenApplication.kt      # @HiltAndroidApp (класс GlyphShargeApplication)
├── MainActivity.kt             # Точка входа, единственная Activity самого приложения
├── CustomAnimationsActivity.kt # Студия Lua-анимаций (отдельная Activity, не в NavHost)
├── glyph/
│   ├── GlyphManager.kt         # Сессия SDK, регистрация устройства
│   ├── GlyphAnimationManager.kt# Фасад: публичные точки входа анимаций
│   ├── GlyphFeatureCoordinator.kt # Mutex, withStrip() и enum GlyphFeature
│   ├── AnimationCatalog.kt     # enum GlyphAnimationId (id из настроек)
│   ├── RunTrace.kt             # Устойчивый журнал того, что каждая фича реально нарисовала
│   ├── device/                 # Раскладка LED и тайминги по модели
│   │   ├── DeviceType.kt       # Определение модели — в одном месте
│   │   ├── DeviceProfile.kt    # Классы данных профиля
│   │   └── DeviceProfileFactory.kt
│   ├── engine/                 # Сборка кадров, флаг запуска, обработка ошибок
│   │   └── GlyphRenderer.kt
│   ├── animations/             # Сами анимации
│   │   ├── AnimationRunner.kt  # anim { }: проверки и жизненный цикл рендерера
│   │   ├── BuiltInAnimations.kt# Десять именованных последовательностей, по одной строке
│   │   ├── SequenceAnimations.kt
│   │   ├── ParticleAnimations.kt
│   │   └── AudioAnimations.kt  # рисовальщик музыкальной визуализации
│   ├── audio/                  # Вход визуализации и её воспроизведение
│   │   ├── AudioFrame.kt / AudioFrameFeed.kt / AudioAnalysis.kt
│   │   ├── AudioAnalyzer.kt / Fft.kt
│   │   ├── MusicVisualisation.kt # play() и превью из настроек
│   │   ├── MusicVisualizationMode.kt / SyntheticTrack.kt
│   │   └── PlaybackAudioSource.kt # Захват через MediaProjection
│   ├── script/                 # Пользовательские анимации на Lua
│   │   ├── ScriptAnimation.kt  # Модель: имя, исходник, id вида custom:<12 hex>
│   │   ├── ScriptFileFormat.kt # Контейнер .glyphlua (encode/decode/имя файла)
│   │   ├── LuaScriptEngine.kt  # LuaJ: песочница, сторож, validate()
│   │   ├── ScriptPlayback.kt   # Запуск скрипта под теми же гарантиями, что и встроенные
│   │   ├── ScriptSession.kt    # Состояние прогона: дедлайн, бюджет, прерываемые паузы
│   │   ├── ScriptTarget.kt     # glyph.target и то, какой пикер что предлагает
│   │   ├── GlyphLuaApi.kt      # Таблица glyph — весь язык для скрипта
│   │   └── ScriptRunner.kt     # Единственное место, где VM встречается с железом
│   └── battery/                # Анимация зарядки и Power Peek
│       ├── BatteryState.kt
│       └── BatteryGlyphAnimator.kt
├── data/
│   ├── SettingsRepository.kt   # Тонкий фасад над срезами ниже
│   ├── SettingsPrefs.kt        # Типизированные get/put поверх SharedPreferences
│   ├── ThemeSettings.kt / FontSettings.kt / GlyphServiceSettings.kt
│   ├── FeatureSettings.kt      # Переключатели, id и длительности восьми фич
│   ├── QuietHoursSettings.kt / LanguageSettings.kt / UserPresenceSettings.kt
│   ├── SettingsMigrations.kt   # Значения по умолчанию и шаги версий
│   ├── SettingsDiagnostics.kt  # dumpAllSettings()
│   └── CustomAnimationRepository.kt # Файлы скриптов + индекс, экспорт через MediaStore
├── services/
│   ├── GlyphForegroundService.kt   # Мастер-сервис
│   ├── FeatureServiceController.kt # Старт, стоп и чтение фич
│   ├── FeatureSpec.kt          # Реестр «фича → сервис → настройка»
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
│   ├── components/            # Карточки, диалоги, layout
│   │   ├── FeatureCards.kt    # 7 карточек фич; восьмая — в VpnConnected.kt
│   │   ├── GlyphAnimations.kt # Каталог анимаций + rememberAnimationOptions()
│   │   ├── GlyphDependencies.kt # rememberGlyphAnimationManager()
│   │   ├── cards/             # ContentCard, FeatureCard, WideFeatureCardWithToggle
│   │   ├── controls/          # MorphingToggleButton
│   │   ├── dialogs/           # FeatureDialogScaffold, FeatureConfirmationFlow
│   │   └── layout/            # SettingsScaffold, DraggableSettingsCard
│   ├── screens/               # Экраны
│   │   └── animations/       # Студия: список, редактор
│   ├── navigation/            # Routes, GlyphNavHost
│   ├── state/                 # FeatureUiState, HomeUiState
│   ├── theme/                 # Темы, шрифты, типографика, LocalSettings
│   ├── utils/                 # HapticUtils
│   └── viewmodel/             # HomeViewModel, CustomAnimationsViewModel,
│                              # AnimationStudioViewModel
├── utils/
│   └── LoggingManager.kt
└── di/
    └── AppModule.kt           # Единственный @Module: @GlyphPrefs SharedPreferences
```

### Ключевые архитектурные решения

**Одно место маршрутизирует фичи в сервисы.** [FeatureSpec](../app/src/main/java/com/bleelblep/glyphsharge/services/FeatureSpec.kt)
хранит весь маппинг как **данные**: по одной записи на фичу, в которой её класс сервиса,
действие остановки, две лямбды доступа к настройкам и сообщение о выключенном сервисе.
[FeatureServiceController](../app/src/main/java/com/bleelblep/glyphsharge/services/FeatureServiceController.kt)
решает только *что* делать с фичей, но никогда — какой это сервис и какая настройка;
`FeatureSpecs.init` роняет сборку, если список и enum когда-нибудь разойдутся. И Activity,
и ViewModel управляют сервисами через один и тот же контроллер.

**Взаимное исключение по LED.** Несколько сервисов могут одновременно захотеть включить глифы.
[GlyphFeatureCoordinator](../app/src/main/java/com/bleelblep/glyphsharge/glyph/GlyphFeatureCoordinator.kt)
держит `Mutex` — одновременно светодиодами владеет только одна фича. Единственный способ
забрать полосу — `withStrip(owner, preempt, …)`: блок выполняется под замком, освобождение
живёт в `finally`, а неудачный захват возвращает `null`, ни разу не войдя в блок. Второй
претендент получает отказ через 500 мс и корректно уступает.

**Пользовательские анимации — это отдельный движок за существующим фасадом.** Скрипты на Lua
не получили ни своей ветки в сервисах, ни своего рендерера. Настройка фичи по-прежнему хранит
одну строку-id, а `GlyphAnimationManager.playAnimation(id, durationMs)` **до** встроенного
`GlyphAnimationId.of(id)` проверяет `ScriptAnimation.isCustomId(id)` и уходит в `ScriptRunner`:

```
настройка фичи (id вида custom:<12 hex>)
  → CustomAnimationRepository (поиск исходника)
  → GlyphAnimationManager.playAnimation
  → ScriptRunner.runScript          (Dispatchers.Default, RendererHost)
  → LuaScriptEngine.run             (LuaJ, песочница + сторож)
  → GlyphRenderer → GlyphManager    (светодиоды)
```

Скрипт идёт под **теми же** гарантиями и с тем же гашением полосы, что и встроенная анимация,
поэтому фича, выбравшая скрипт, ведёт себя как фича, выбравшая любую другую анимацию.
Подробности — [раздел 14](#14-пользовательские-анимации-lua).

**Студия — отдельная Activity, а не маршрут NavHost.**
[CustomAnimationsActivity](../app/src/main/java/com/bleelblep/glyphsharge/CustomAnimationsActivity.kt)
объявлена в манифесте с `android:exported="false"` и `parentActivityName=".MainActivity"`.
У неё собственный стек возврата (`BackHandler` между списком и редактором) и собственные
`ActivityResultLauncher` для `OpenDocument`/`CreateDocument` — системные диалоги не должны
просачиваться в общий навграф настроек. Из `SettingsScreen` на неё ведёт карточка
`settings_card_custom_animations` со счётчиком сохранённых скриптов.

> [!IMPORTANT]
> **`GlyphFeature` — это единственный enum**, объявленный в конце
> [GlyphFeatureCoordinator.kt](../app/src/main/java/com/bleelblep/glyphsharge/glyph/GlyphFeatureCoordinator.kt):
> `PULSE_LOCK`, `POWER_PEEK`, `LOW_BATTERY`, `SCREEN_OFF`, `NFC`, `CHARGING_ANIMATION`,
> `MUSIC_VISUALIZER`, `VPN_CONNECTED`. Список фич, обвязка сервисов и UI согласованы с ним,
> поэтому фичу нельзя включить, не заведя за ней что-то работающее. См.
> [раздел 7](#7-добавление-нового-сервиса-quickstart).

---

## 4. Слой Glyph

### GlyphManager

`@Singleton`, инжектится через конструктор. Обёртка над официальным SDK `com.nothing.ketchum`.
Отвечает **только** за сессию SDK — ни логики анимаций, ни захардкоженных номеров каналов.

```kotlin
fun initialize()                 // инициализация SDK, ленивый mCallback, привязка к сервису
fun isNothingPhone(): Boolean    // проверка модели устройства
fun openSession()                // открыть сессию глифов
fun closeSession()               // закрыть сессию
val isSessionActive: Boolean     // состояние сессии
val isServiceConnected: Boolean  // привязан ли сервис Nothing
var onSessionStateChanged: ((Boolean) -> Unit)?  // зеркало состояния SDK в UI
fun forceEnsureSession(): Boolean// блокирующее ожидание готовности (до 2 с)
fun toggleGlyphService(): Boolean
fun turnOffAll()                 // погасить все LED
fun turnOnAllGlyphs()            // включить все каналы подключённой модели
fun canPerformOperation(): Boolean
fun cleanup()
```

**Жизненный цикл сессии:**

1. `GlyphShargeApplication.onCreate` → `if (isNothingPhone()) glyphManager.initialize()`
2. `GlyphManager.mCallback.onServiceConnected` → `DeviceType.detect()` → `mGM.register(id)`
3. `HomeViewModel.toggleGlyphService(true)` → `openSession()`
4. `GlyphFeatureCoordinator.acquire()` → если сессия не активна, `forceEnsureSession()`
5. `onStop` / `onDestroy` → при выключенной настройке `closeSession()` + `cleanup()`

**Определение модели** централизовано в `DeviceType.detect()`. Enum хранит и проверку
`Common.is*`, и id для регистрации в SDK, поэтому вопрос «какой это телефон?» задаётся ровно
в одном месте вместо пяти цепочек `when`.

> [!NOTE]
> `forceEnsureSession()` использует блокирующий `Thread.sleep(100)` в цикле до 2 секунд.
> Вызывается из `GlyphFeatureCoordinator.acquire()` через `withContext(Dispatchers.IO)`,
> то есть блокирует IO-поток на всё время ожидания, если сервис Nothing не привяжется.

### GlyphRenderer

`@Singleton`, инжектится через конструктор. **Единственный** класс, который напрямую работает
с `GlyphManager.mGM`. Анимации написаны как `suspend`-расширения на нём — благодаря этому три
общие для всех анимаций вещи живут в одном месте:

| Метод | Назначение |
|-------|------------|
| `val isRunning: Boolean` | ставится `start()` / `stop()`, проверяется в каждом цикле анимации |
| `turnOff()` | погасить полосу; никогда не бросает исключений |
| `frame(channels, brightness)` | билдер с включёнными каналами одной яркости либо `null` |
| `builder()` | пустой билдер — для ручных рамп яркости по каналам |
| `toggle(builder, delayMs)` | показать кадр и, опционально, подождать |
| `toggleChannels(channels, brightness, delayMs)` | `frame` + `toggle` |
| `pulse(channels, onMs, offMs, brightness)` | примитив «включить, выключить, подождать» |
| `onError(e, message, retryDelayMs)` | залогировать сбой и продолжить — но перебрасывает `CancellationException` |

`GLYPH_MAX_BRIGHTNESS = 4000` — верхнеуровневая константа в этом же файле.

### GlyphAnimationManager

`@Singleton`. Тонкий фасад над работой, которая разделена по назначению так, чтобы изменение
оставалось в одном месте. Он не содержит ни кода отрисовки, ни собственного жизненного цикла
рендерера; то, что осталось здесь, — часть про *приложение*: какой id настройки играет фича,
как долго ей можно идти и как вызывающий ограничивает прогон.

| Задача | Где |
|--------|-----|
| Какие каналы есть на этом телефоне | `glyph/device/DeviceProfileFactory` |
| Сборка кадров, обработка ошибок, отмена | `glyph/engine/GlyphRenderer` |
| Проверки и жизненный цикл с гашением полосы (`anim { }`) | `glyph/animations/AnimationRunner` |
| Десять именованных последовательностей, по одной строке | `glyph/animations/BuiltInAnimations` |
| Что именно рисует последовательность | `glyph/animations/SequenceAnimations`, `ParticleAnimations`, `AudioAnimations` |
| Музыкальная визуализация и её превью | `glyph/audio/MusicVisualisation` |
| Анимация зарядки и полоса батареи | `glyph/battery/BatteryGlyphAnimator` |
| Запуск пользовательской Lua-анимации | `glyph/script/ScriptPlayback` |
| Исполнение самого Lua | `glyph/script/ScriptRunner` |

`AnimationRunner` владеет ещё и двумя вещами, нужными каждой точке входа: лениво вычисляемым
`profile: DeviceProfile?` (`null` на неподдерживаемом железе) и `isGlyphServiceEnabled()`.

**Базовые анимации** (все `suspend`, все однострочники над `AnimationRunner.anim`):

| Функция | Описание |
|---------|----------|
| `runWaveAnimation()` | Волна по группам сегментов |
| `runBeedahAnimation()` | Тот же ритм без пауз между группами |
| `runSpiralAnimation()` | Спиральный проход с ростом яркости 0.6 → 1.0 |
| `runC1SequentialAnimation()` | Последовательный проход по C-полосе туда и обратно |
| `runLockPulseAnimation()` | «Замок»: расширяющийся клин 0.3 → 1.0 |
| `runPulseEffect(cycles: Int = 3)` | Мигание, 300 мс вкл/выкл |
| `runHeartbeatAnimation()` | Три удара «lub-dub» |
| `runMatrixRainAnimation()` | Падающие капли с хвостами |
| `runFireworksAnimation()` | Запуски и взрывы с затуханием |
| `runDNAHelixAnimation()` | Две встречно вращающиеся нити |

**Сценарии фич** — обёртки, которые проверяют настройки и вызывают базовую анимацию:

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

**Диспетчеризация по id.** Приватный `playAnimation(id, durationMs)` сначала проверяет,
не является ли id пользовательским (`ScriptAnimation.isCustomId(id)` → префикс `custom:`), и если
да — уходит в `playCustomAnimation(id, durationMs)`. Только иначе сохранённый id разбирается
через `GlyphAnimationId.of(id)` и выполняется `when` по enum:

| id | Анимация |
|----|----------|
| `C1` | `runC1SequentialAnimation()` |
| `WAVE` | `runWaveAnimation()` |
| `BEEDAH` | `runBeedahAnimation()` |
| `LOCK` | `runLockPulseAnimation()` |
| `SPIRAL` | `runSpiralAnimation()` |
| `HEARTBEAT` | `runHeartbeatAnimation()` |
| `MATRIX` | `runMatrixRainAnimation()` |
| `FIREWORKS` | `runFireworksAnimation()` |
| `DNA` | `runDNAHelixAnimation()` |
| `PULSE` + всё остальное | `runPulseEffect(cycles)` |

> [!NOTE]
> `durationMs` используется **только** в ветке `PULSE` (`cycles = duration / 500`).
> Именованные анимации идут своей естественной длиной, игнорируя настройку.
> С пользовательским скриптом всё иначе: это программа, поэтому она заканчивается, когда
> заканчивается её собственный код, и единственный потолок для неё — `ScriptAnimation.SAFETY_CAP_MS`.

**Пользовательские анимации.** Четыре публичных метода и потолок прогона, о котором спрашивают сервисы:

| Метод | Назначение |
|-------|-----------|
| `fun runCapMs(runtimeId: String, featureDurationMs: Long): Long` | Потолок по стенным часам, который реально применяется: Duration фичи для встроенной анимации, `SAFETY_CAP_MS` для id вида `custom:` |
| `suspend fun playCustomAnimation(runtimeId: String): ScriptRunResult` | Запуск сохранённого скрипта по `custom:`-id. Проверяет тумблер сервиса и `isNothingPhone()`, ищет исходник в `CustomAnimationRepository` |
| `suspend fun previewScript(source: String, durationMs: Long): ScriptRunResult` | Прогон несохранённого исходника из редактора. **Без** проверки тумблера: студия — явное действие пользователя |
| `fun checkScript(source: String): String?` | Компиляция без запуска, кнопка Check. `null` — синтаксис в порядке |
| `fun stopAnimations()` | Дополнительно вызывает `scriptRunner.stop()`, чтобы прервать и скрипт |

`ScriptPlayback` владеет приватным `playScript { }`, через который проходят все три suspend-точки
входа: гасит полосу, выполняет блок, гасит снова в `finally` и превращает любое исключение
в `ScriptRunResult(RUNTIME_ERROR, …)` — наружу не вылетает ничего.

**Общий сторож прогонов.** `runCapped(capMs, onTimeout = {})` выполняет блок в собственном
диспетчерере не дольше `capMs`: корутина отрисовки, таймер, который её отменяет и дожидается,
затем `stopAnimations()` для того, до чего отмена не дотягивается. Блок, завершившийся сам,
оставляет сторож отменённым и ничего больше не делает, а обрезанный по времени прогон
возвращает `Unit`, а не перебрасывает отмену.

**Обёртка `anim { }`.** `AnimationRunner.anim { }` — это общая обвязка, которую берёт в
аренду каждая последовательность: проверка тумблера сервиса, поддержки устройства и профиля,
гашение полосы, выполнение тела и повторное гашение в `finally`. Тело — это
`suspend GlyphRenderer.(DeviceProfile) -> Unit`, поэтому анимации читаются как
`anim { runWaveAnimation(it) }`.

> [!TIP]
> Каталог стал enum: `GlyphAnimationId` в `glyph/AnimationCatalog.kt` владеет строками id,
> поэтому опечатка в настройке — это заметное расхождение, а не тихий откат на pulse.

### Реализации анимаций

Код отрисовки сгруппирован по назначению, и ничего в нём не знает ни про настройки, ни про
скрипты, ни про сервисный слой:

| Файл | Содержимое |
|------|------------|
| `glyph/animations/AnimationRunner.kt` | `anim { }`: три проверки, жизненный цикл рендерера, ленивый `profile` |
| `glyph/animations/BuiltInAnimations.kt` | десять именованных последовательностей, по одной строке над раннером |
| `glyph/animations/SequenceAnimations.kt` | волна, beedah, pulse, heartbeat, C1, lock, spiral |
| `glyph/animations/ParticleAnimations.kt` | matrix rain, fireworks, DNA helix |
| `glyph/animations/AudioAnimations.kt` | `runMusicVisualization` и `resolveStrip` |
| `glyph/audio/MusicVisualisation.kt` | настоящий прогон визуализации и её превью из настроек |
| `glyph/audio/SyntheticTrack.kt` | выдуманный спектр, который играет превью |
| `glyph/battery/BatteryGlyphAnimator.kt` | полоса заряда для зарядки и Power Peek плюс акценты |
| `glyph/battery/BatteryState.kt` | `BatteryState` + `BatteryStateReader` (sticky broadcast) |
| `glyph/engine/GlyphRenderer.kt` | сборка кадров, флаг запуска, обработка ошибок, `pulse` |
| `glyph/device/DeviceProfileFactory.kt` | раскладки каналов и тайминги по моделям |
| `glyph/device/DeviceType.kt` | единственный источник правды об определении модели |
| `glyph/device/DeviceProfile.kt` | `DeviceProfile`, `AnimGroup` и три конфига таймингов |
| `glyph/script/ScriptPlayback.kt` | запуск скрипта: проверки, гашение, потолок |
| `glyph/script/LuaScriptEngine.kt` | LuaJ-песочница, сторож, `validate()` |
| `glyph/script/GlyphLuaApi.kt` | Таблица `glyph` — весь пользовательский язык |
| `glyph/script/ScriptRunner.kt` | Единственное место, где VM соединяется с `GlyphRenderer` |

Два соглашения делают анимации читаемыми:

- каждый цикл начинается с `if (!isRunning) return` — именно это позволяет `stopAnimations()`
  сработать за один шаг, а не в конце пятисекундной последовательности;
- если кадр построить не удалось, используется `builder() ?: break`, а не исключение,
  поэтому последовательность деградирует до тех кадров, которые отрисовать удалось.

Каталог анимаций для UI — объект `GlyphAnimations` в
[ui/components/GlyphAnimations.kt](../app/src/main/java/com/bleelblep/glyphsharge/ui/components/GlyphAnimations.kt):
10 записей `GlyphAnim(id, displayName, iconRes, isCustom = false)` и `getById(id, options)`
с откатом на первую запись. `withCustomAnimations(custom, scope)` дописывает скрипты пользователя
**после** встроенных, поэтому ряд чипов сохраняет стабильную разметку. Composable
`rememberAnimationOptions(scope)` читает репозиторий через `CustomAnimationsViewModel`
(пикеры — обычные composable, а не ViewModel) и возвращает список, подписанный на `StateFlow`.
`PulseLock`, `LowBattery`, `NfcGlyph` и `ScreenOff` используют дефолтный `ScriptScope.TRIGGER`;
визуализация передаёт `ScriptScope.MUSIC` и потому видит все скрипты. У каждой из четырёх
кнопок Test есть ветка: `if (selectedAnimation.isCustom) playCustomAnimation(id)`.

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

`withStrip` — это то, чем пользуется каждый сервис. Он берёт замок, выполняет блок и
освобождает полосу в `finally` — а освободить её обязательно, потому что полоса один общий
ресурс за `Mutex`: сервис, который вернулся, бросил исключение или был отменён, не отдав
замок, оставляет захваченной именно *блокировку*, а `release()` игнорирует вызов от
не-владельца, поэтому забрать её обратно уже никто не сможет. Неудачный захват возвращает
`null`, а не выполняет блок, — так вызывающий отличает «полосу так и не взял» от «взял и
держал» без собственного флага. `onRelease` выполняется из того же `finally`, *после*
`release`, поэтому светодиоды уже погашены — именно тот порядок, которого ждёт собственная
разборка сервиса.

`acquire()` берёт `Mutex` с таймаутом (`tryLock` в циле опроса, намеренно не
`withTimeoutOrNull { lock.lock() }`, который может отменить корутину прямо под замком), при
успехе выставляет `_currentOwner` и при необходимости поднимает сессию глифов. `acquireNow()`
дополнительно просит текущего владельца прекратить отрисовку, и именно через него проходит
`preempt = true`: разблокировка или блокировка — это то, что пользователь только что сделал,
и прерванного владельца просят вернуться, а не выбрасывают, чтобы он смог отпустить полосу
в своём `finally`. `release()` сначала гасит LED (`turnOffAll()`), и только потом отдаёт
мьютекс следующему владельцу; вызовы от не-владельца игнорируются.

---

## 5. Слой данных

### Настройки

Хранилище — **SharedPreferences**, файл `glyphzen_settings`, который единожды отдаёт
[di/AppModule.kt](../app/src/main/java/com/bleelblep/glyphsharge/di/AppModule.kt) под
квалификатором `@GlyphPrefs`. Никаких `Flow`/`StateFlow` — все геттеры синхронные, экраны
опросают состояние через `remember` плюс `refreshFeatures()` в `onResume`.

Настройки разбиты по назначению: по одному классу на срез, у каждого свои константы
`KEY_*` и свои значения по умолчанию.

| Срез | Что владеет |
|------|-------------|
| `ThemeSettings` | тёмная тема, `AppThemeStyle` |
| `FontSettings` | `FontVariant`, переключатель своих шрифтов, четыре масштаба |
| `GlyphServiceSettings` | мастер-переключатель, интенсивность вибрации, ступени встряхивания |
| `FeatureSettings` | переключатели, id анимаций, длительности и пороги восьми фич |
| `QuietHoursSettings` | окно тишины и `isCurrentlyInQuietHours()` |
| `LanguageSettings` | код `language` |
| `UserPresenceSettings` | учёт разблокировок, который читает Pulse Lock |
| `SettingsMigrations` | значения по умолчанию, шаги версий, починка легаси-вибрации |
| `SettingsDiagnostics` | `dumpAllSettings()` |
| `SettingsPrefs.kt` | типизированные `SharedPreferences.getSetting` / `putSetting` |

`SettingsPrefs` существует потому, что выписывать `prefs.getBoolean(KEY, false)` и
`prefs.edit { putBoolean(KEY, value) }` для каждого ключа — это ровно то место, где две
половины настройки расходятся: ничто не связывает тип записи с типом чтения. Типизированный
параметр функции заставляет компилятор проверять эту связку.

**`SettingsRepository` — тонкий фасад над срезами**: 78 однострочных переадресаций и ни
одного ключа настройки, значения по умолчанию или вызова `SharedPreferences` внутри. Это
не архитектурное решение, а поверхность совместимости: переписать все ~25 мест использования
тем же изменением, что разбило хранилище, значило бы за один раз поставить каждое чтение
настроек в приложении под ревью ради рефакторинга, чья главная претензия — что он не
меняет поведения. Все сигнатуры совпадают с монолитом, поэтому удаление метода отсюда —
это ошибка компиляции где-то настоящем, и фасад не может тихо отстать от срезов, которые
переадресует.

**`SettingsMigrations` выполняет три прохода** — `applyFirstRunDefaults()` (под флагом
`first_run_completed`, пишет `false` для всех флагов фич, `HEADLINE` для шрифта,
`use_custom_fonts = true`, масштабы `1.0f`), `applyVersionMigrations()` (по шагу на маркер
версии, каждый проверяет `prefs.contains(KEY)` перед записью, чтобы потерянный маркер не
сбросил пользовательские переключатели) и `normalizeLegacyVibrationIntensity()` (легаси-значение
вибрации `1..255` в шкалу `0.1f..1.0f`). Он запускает их и из собственного блока `init`, и из
явного вызова `applyMigrations()`, который делает `GlyphShargeApplication.onCreate`; флаг
`applied` делает второй путь пустым, так что гарантия — значения по умолчанию на диске до
запуска любого геттера — выполняется независимо от того, что пришло первым.
`SettingsRepository` принимает его конструктором именно ради этой структурной гарантии.

**Публичный API фасада (сгруппированно):**

| Группа | Методы |
|--------|--------|
| Тема | `saveTheme` / `getTheme`, `saveThemeStyle` / `getThemeStyle` |
| Шрифт | `saveFontVariant` / `getFontVariant`, `saveUseCustomFonts` / `getUseCustomFonts`, `saveFontSizeSettings` / `getFontSizeSettings`, `clearFontSizeCustomization`, `getFontSizeSettingsForFont` |
| Мастер-переключатель | `saveGlyphServiceEnabled` / `getGlyphServiceEnabled` |
| Power Peek | `savePowerPeekEnabled` / `isPowerPeekEnabled`, `savePowerPeekThreshold` / `getPowerPeekThreshold`, `getShakeIntensityLevel`, `savePowerPeekDuration` / `getPowerPeekDuration`, `saveVibrationIntensity` / `getVibrationIntensity` |
| Pulse Lock | `savePulseLockEnabled` / `isPulseLockEnabled`, `…AnimationId`, `…Duration` |
| Low Battery | `saveLowBatteryEnabled` / `isLowBatteryEnabled`, `…Threshold`, `…AnimationId`, `…Duration` |
| Screen Off | `saveScreenOffFeatureEnabled` / `isScreenOffFeatureEnabled`, `…AnimationId`, `…Duration` |
| NFC | `saveNfcFeatureEnabled` / `isNfcFeatureEnabled`, `…AnimationId`, `…AnimationDuration` |
| Charging | `saveChargingAnimationEnabled` / `isChargingAnimationEnabled`, `…Duration` |
| Музыкальная визуализация | `saveMusicVizEnabled` / `isMusicVizEnabled`, `…AnimationId`, `…Sensitivity`, `…ScreenOffOnly` |
| VPN Connected | `saveVpnConnectedEnabled` / `isVpnConnectedEnabled`, `…AnimationId`, `…Duration` |
| Присутствие пользователя | `isUserPresentExpected`, `markUserPresentSeen`, `markUserPresentMissing` |
| Тихие часы | `saveQuietHoursEnabled` / `isQuietHoursEnabled`, `saveQuietHoursStartHour/Minute`, `saveQuietHoursEndHour/Minute`, `isCurrentlyInQuietHours()` |
| Язык | `getAppLanguageCode` / `saveAppLanguageCode` |
| Отладка | `dumpAllSettings()` (пишет в Logcat только при `Log.isLoggable`) |

Публичные константы: `SHAKE_SOFT=12.0f`, `SHAKE_EASY=15.0f`, `SHAKE_MEDIUM=18.0f`,
`SHAKE_HARD=22.0f`, `SHAKE_HARDEST=28.0f`.

> [!NOTE]
> У анимации зарядки **нет** настройки выбора анимации (`saveChargingAnimationId` отсутствует) —
> используется фиксированный `playChargingAnimationAnimation`.

### CustomAnimationRepository

`@Singleton`, второй репозиторий слоя данных — на пользовательские анимации.
[CustomAnimationRepository.kt](../app/src/main/java/com/bleelblep/glyphsharge/data/CustomAnimationRepository.kt)

**Исходники в файлах, индекс в настройках.** Скрипт редактируют часто, и он бывает
длиннее, чем стоит хранить в значении настройки, поэтому каждый лежит отдельным файлом
`filesDir/glyph_scripts/<id>.glyphlua`, а SharedPreferences `glyphsharge_custom_animations`
содержит только компактный JSON-массив (`org.json`) для отрисовки списка без похода на диск.

Список отдаётся как `StateFlow<List<ScriptAnimation>>` — пикеры анимаций на карточках
Pulse Lock, Low Battery, NFC и Screen Off находятся на несколько экранов от редактора,
и сохранение скрипта должно обновить чипы сразу везде.

| Группа | Методы |
|--------|--------|
| Чтение | `reload()`, `getById(id)`, `findByRuntimeId(runtimeId)`, `animations: StateFlow` |
| Запись | `draft(template)`, `save(animation)`, `rename(id, name)`, `delete(id)`, `duplicate(id)` |
| Импорт | `suspend readText(uri)`, `suspend importFrom(text, fallbackName)` |
| Экспорт | `suspend exportTo(uri, animation)` (системный выбор файла), `suspend exportToDownloads(animation)` (MediaStore) |

> [!IMPORTANT]
> **Импорт всегда назначает новый id** (`ScriptAnimation.newId()`), даже если в заголовке
> файла есть чужой. Иначе импорт файла, который уже лежит на телефоне, молча затёр бы
> локальную копию. Имена дедуплицируются суффиксом `(2)`, `(3)`… — два одинаковых названия
> в ряду чипов неразличимы.
>
> `exportToDownloads()` идёт через `MediaStore.Downloads` с протоколом `IS_PENDING`
> (API 29+), поэтому **разрешения не требуется**; `exportTo(uri)` работает через
> `ActivityResultContracts.CreateDocument`.

`R.string.studio_starter_script` — шаблон, с которого начинается новая анимация. Он намеренно
использует только `glyph.ch.all`/`glyph.ch.c` и никогда не хардкодит номер канала, поэтому
работает на любом поддерживаемом телефоне.

---

## 6. Сервисы

### Обзор

| Сервис | Назначение | Триггер |
|--------|-----------|---------|
| `GlyphForegroundService` | Постоянное уведомление, держит процесс живым. Не содержит LED-логики | `FeatureServiceController.stopAll()` |
| `ChargingAnimationService` | Анимация при подключении/отключении зарядки | `POWER_CONNECTED` / `POWER_DISCONNECTED` |
| `PowerPeekService` | Проверка батареи встряхиванием при выключенном экране | Акселерометр |
| `PulseLockService` | Анимация при разблокировке | `ACTION_USER_PRESENT` |
| `ScreenOffGlyphService` | Анимация при блокировке экрана | `ACTION_SCREEN_OFF` |
| `NfcGlyphService` | Анимация по NFC-событию | NFC-Intent через Activity |
| `LowBatteryAlertService` | Оповещение о низком заряде | `ACTION_BATTERY_CHANGED` |
| `QuietHoursService` | Тишина в заданный интервал | `AlarmManager` |
| `MusicVisualizerService` | Спектр того, что сейчас играет | `Visualizer` + `AudioManager` |
| `VpnConnectedService` | Анимация при подключении VPN | `NetworkCallback` на `TRANSPORT_VPN` — не broadcast |

> [!NOTE]
> **Единственный триггер, который не broadcast, — у `VpnConnectedService`.** Android не
> рассылает событие «VPN подключён», поэтому сервис регистрирует
> `ConnectivityManager.NetworkCallback` для `NetworkCapabilities.TRANSPORT_VPN` и объявляет
> `ACCESS_NETWORK_STATE` — нужно *состояние*, а не сама сеть. `registerNetworkCallback`
> немедленно вызывает `onAvailable` для VPN, который **уже** подключён, а это неотличимо от
> только что сделанного пользователем подключения, поэтому сервис читает текущее состояние
> *до* регистрации и держит защёлку `wasConnected`: `onAvailable` проигрывает анимацию только
> на настоящем переходе false → true, а `onLost` и `onUnavailable` сбрасывают защёлку, чтобы
> следующий `onAvailable` читался как то самое событие подключения. VPN, поднятый до старта
> сервиса, поэтому оставляет полосу тёмной — анимация принадлежит событию подключения, а не
> факту запуска сервиса.

### Контракт жизненного цикла сервиса

Каждый фичевый сервис обязан реализовать **все шесть** точек:

```kotlin
@AndroidEntryPoint
class MyService : Service() {

    // This service's own registry entry, which owns the run gate: my switch
    // and the master Glyph switch, in one answer.
    private val spec = FeatureSpecs.of(GlyphFeature.MY_FEATURE)

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var glyphAnimationManager: GlyphAnimationManager
    @Inject lateinit var featureCoordinator: GlyphFeatureCoordinator

    // 1. Константы действий
    companion object {
        const val ACTION_START = "com.bleelblep.glyphsharge.MY_FEATURE_START"
        const val ACTION_STOP  = "com.bleelblep.glyphsharge.MY_FEATURE_STOP"
        private const val TAG = "MyService"
        private const val NOTIF_ID = 1020
        private const val NOTIF_CHANNEL_ID = "MyServiceChannel"
    }

    // 2. Создание: канал уведомлений, WakeLock, ресиверы
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerReceivers()
    }

    // 3. Запуск: обязательно startForeground() в первых строках
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        if (intent?.action == ACTION_STOP) { shutDown(); return START_NOT_STICKY }
        if (!spec.isRunnable(settingsRepository)) { shutDown(); return START_NOT_STICKY }
        return START_STICKY
    }

    // 4. Уничтожение: снять ресиверы, отпустить WakeLock, отменить корутины
    override fun onDestroy() {
        runCatching { unregisterReceiver(myReceiver) }
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        serviceJob.cancel()
        super.onDestroy()
    }

    // 5. Сервис не биндится
    override fun onBind(intent: Intent?): IBinder? = null

    // 6. Самовосстановление после снятия из недавних
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!spec.isRunnable(settingsRepository)) return
        startForegroundService(Intent(this, MyService::class.java).apply { action = ACTION_START })
    }
}
```

### Правила, которые нельзя нарушать

> [!IMPORTANT]
> 1. **`startForeground()` в начале `onStartCommand`** — иначе на Android 12+ система
>    выбросит `ForegroundServiceDidNotStartInTimeException`.
> 2. **Все 10 сервисов используют `foregroundServiceType="specialUse"`** (визуализация
>    добавляет `mediaProjection`) и обязаны объявлять свой
>    `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` в манифесте — это требование Google Play.
> 3. **Каждый триггер проходит через `spec.isRunnable(settingsRepository)`** — собственный
>    флаг фичи и мастер `getGlyphServiceEnabled()` отвечают вместе, поэтому `onStartCommand`,
>    `onTaskRemoved` и обработчик события не могут пройти один и не пройти другой, — плюс
>    тихие часы `isCurrentlyInQuietHours()` там, где это нужно.
> 4. **Всегда `runCatching { release() }`** для WakeLock и `unregisterReceiver` — они бросают
>    исключения, если ресурс не был захвачен.
> 5. **Полосу брать только через `featureCoordinator.withStrip { }`**, а не вызывать
>    `acquire()` и `release()` руками: освобождение обязано быть в `finally`, иначе мьютекс
>    остаётся захваченным и все остальные фичи замирают.
> 6. **Ограничивать прогон `glyphAnimationManager.runCapped(capMs) { }`** — общим сторожем,
>    даже если сама анимация идёт дольше.

### Канонический шаблон запуска анимации

`withStrip` передаёт полосу, `runCapped` ограничивает прогон, а разборка едет на `onRelease`,
поэтому происходит после того, как светодиоды погашены, и только если полоса действительно
досталась:

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
> WakeLock берётся **внутри** блока и отпускается из `onRelease`. Занятая полоса
> возвращает управление до входа в блок, поэтому WakeLock не был взят и отменять нечего —
> именно поэтому неудачный захват это простой `return`, а не вызов `onRelease`.
> Сервис, который на время последовательности выводит себя в foreground, делает то же самое
> через `onRelease = { stopForegroundCompat() }`.

### FeatureServiceController

Сам маппинг — это **данные**, а не код: [FeatureSpec.kt](../app/src/main/java/com/bleelblep/glyphsharge/services/FeatureSpec.kt)
хранит по одной записи на фичу, а контроллер решает только *что* с ней делать.

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
> Аксессоры настроек — лямбды над `SettingsRepository`, а не методы на ней, поэтому реестр
> остаётся декларативным, а слой настроек по-прежнему не имеет понятия о том, что фичи
> существуют как группа. `FeatureSpecs.init` проверяет, что у каждого `GlyphFeature` есть
> запись, и иначе бросает исключение — запись, выпавшая из `Map`, не компилируется, а у
> фичи без записи нет ни сервиса для запуска, ни настройки для чтения.

Публичный API: `readAll()`, `start(feature)`, `stop(feature)`, `apply(feature, enabled)`,
`startAllEnabled()`, `stopAll()`.

`startAllEnabled()` и `stopAll()` итерируют `GlyphFeature.entries`, поэтому новая фича
подхватывается автоматически — отдельно регистрировать её не нужно.

### BootCompletedReceiver

`@AndroidEntryPoint`, работает через `goAsync()` + `Dispatchers.IO`. Восстановление в два
уровня с задержкой `TIER2_DELAY_MS = 100L`:

1. **Уровень 1** — `GlyphForegroundService`, если включён мастер-переключатель.
2. **Уровень 2** — семь фич по порядку: PowerPeek, LowBattery, PulseLock, ScreenOff, NFC,
   Charging Animation, VPN Connected. Каждая — под своим флагом **и** под общим `glyphOn`.
3. **Тихие часы** — в конце, не зависит от глифов.

> [!NOTE]
> Музыкальная визуализация сознательно **не** входит в уровень 2: она захватывает звук
> других приложений через токен `MediaProjection`, а токен нельзя получить из фона — только
> из результата Activity. Запуск при загрузке либо отклонили бы, либо он поднялся бы без
> захвата и просто показывал бы мёртвую карточку, поэтому приложение просит открыть его самому.
>
> VPN Connected **входит** в уровень 2 даже если VPN пережил перезагрузку: сервис должен
> наблюдать, чтобы увидеть следующее подключение, а текущее состояние он читает до
> регистрации, поэтому уцелевший VPN считается «уже подключённым», а не воспроизводится как
> подключение, которого пользователь не делал.

---

## 7. Добавление нового сервиса (Quickstart)

> [!TIP]
> Это пошаговая инструкция: **как добавить новую фичу со своим foreground-сервисом**,
> снятая с `ScreenOffGlyphService` / `LowBatteryAlertService`.
> Пример: фича, которая играет полосу прогресса всякий раз, когда телефон берут в руку.
> Настоящий, уже в коде пример, по которому можно сверяться, — фича VPN Connected:
> [VpnConnectedService.kt](../app/src/main/java/com/bleelblep/glyphsharge/services/VpnConnectedService.kt)
> и [VpnConnected.kt](../app/src/main/java/com/bleelblep/glyphsharge/ui/components/VpnConnected.kt).
> Она идёт по тем же четырём шагам и заодно показывает единственный триггер, который **не**
> `BroadcastReceiver`.

Фича — это четыре куска обвязки, и только четыре: **значение в enum, запись в реестре,
сервис и карточка.** Всё, что приложению нужно *знать* о фиче — какой сервис её обслуживает,
в какой настройке лежит её переключатель, что сказать при выключенном сервисе глифов, —
хранится данными в одной записи, поэтому больше ничто в приложении не обязано узнавать,
что сервис существует.

Порядок выбран **снизу вверх** (enum → реестр → сервис → UI), чтобы проект в любой момент
либо компилировался, либо падал с понятной ошибкой компилятора.

### Сводная карта изменений

| # | Шаг | Файл |
|---|-----|------|
| 1 | Имя фичи | [GlyphFeatureCoordinator.kt](../app/src/main/java/com/bleelblep/glyphsharge/glyph/GlyphFeatureCoordinator.kt) |
| 2 | Её сервис, действие остановки, настройка и сообщение | [FeatureSpec.kt](../app/src/main/java/com/bleelblep/glyphsharge/services/FeatureSpec.kt) |
| 3 | Сам сервис | **новый** `services/…Service.kt` |
| 4 | Карточка и её диалог | **новый** `ui/components/….kt` |

Помимо этих четырёх — механические части: ключи настроек, запись в манифесте, строки и,
если фича должна пережить перезагрузку, одна строка в ресивере загрузки.

---

### Шаг 1. Добавьте значение в `GlyphFeature`

`GlyphFeature` — единственный enum, объявленный в конце
[GlyphFeatureCoordinator.kt](../app/src/main/java/com/bleelblep/glyphsharge/glyph/GlyphFeatureCoordinator.kt),
и с ним согласованы и список фич, и обвязка сервисов, и UI. Добавьте значение туда:

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
> Порядок совпадает с `FeatureSpecs.all`, чтобы реестр можно было читать напротив enum.
> Второго enum, который надо держать в синхроне, нет, и нет ни одного `when`, который
> обязан был бы узнать новое значение руками: `startAllEnabled()`, `stopAll()` и
> `readAll()` итерируют `GlyphFeature.entries`.

---

### Шаг 2. Добавьте одну запись `FeatureSpec`

В [FeatureSpec.kt](../app/src/main/java/com/bleelblep/glyphsharge/services/FeatureSpec.kt)
добавьте одну запись в `FeatureSpecs.all`, рядом с остальными:

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

Это весь маппинг. Две лямбды — это чтение и запись настройки, а
`isRunnable(settings)` — тот самый пропуск, который спрашивает каждый сервис перед
действием, — приезжает вместе с записью, а не повторяется в каждом сервисе заново:

```kotlin
fun isRunnable(settings: SettingsRepository): Boolean =
    isEnabled(settings) && settings.getGlyphServiceEnabled()
```

`FeatureSpecs.init` бросает исключение в момент загрузки класса, если в enum есть значение,
которого нет в этом списке, поэтому наполовину зарегистрированная фича падает сразу и
громко, а не на первом intent'е запуска. `startAllEnabled()`, `stopAll()` и `readAll()`
править не нужно: они итерируют enum.

> [!IMPORTANT]
> `serviceOffMessage` — это тост, который фича уже показывает, когда её диалог открывают при
> выключенном сервисе глифов. Он живёт в записи потому, что это факт того же рода — один на
> фичу, забываемый независимо от остальных, — и потому, что переиспользовать уже имеющуюся
> формулировку лучше, чем добавить рядом вторую, слегка отличную. `HomeViewModel.setFeatureEnabled`
> читает его прямо из реестра.

---

### Шаг 3. Напишите сервис

Создайте `services/ProgressOnPickupService.kt` по шаблону из
[раздела 6](#контракт-жизненного-цикла-сервиса) и дайте ему две вещи, которые делают
одинаково все фичевые сервисы: **забрать полосу через `withStrip` и ограничить прогон
через `runCapped`.**

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

**Почему `withStrip`, а не `acquire()`/`release()` руками.** Полоса — один общий ресурс за
`Mutex`, поэтому сервис, который вернулся, бросил исключение или был отменён, не отдав
замок, оставляет захваченной именно *блокировку*, а не просто зажжённые светодиоды, и
`release()` игнорирует вызов от не-владельца — значит забрать её обратно уже никто не сможет,
и каждая другая фича до гибели процесса будет писать в лог «полоса занята» на каждом
триггере. `withStrip` кладёт освобождение в `finally` и возвращает `null`, когда полоса так и
не была взята, поэтому вызывающий отличает «не получил» от «получил и держал» без
собственного флага.

`onRelease` выполняется из того же `finally`, **после** `release`, поэтому светодиоды уже
погашены — именно тот порядок, которого ждёт собственная разборка сервиса (WakeLock,
`stopForeground`). Он не выполняется, когда захват не удался: тогда ничего не брали и
отменять нечего, поэтому сервис, выводящий себя в foreground внутри блока, остаётся в
foreground ровно на том пути, где блок не выполнялся.

**Почему `runCapped`, а не самописный сторож.** Это та же последовательность, которую восемь
сервисов раньше копипастили — корутина отрисовки, таймер, который её отменяет и дожидается,
`stopAnimations()` для того, до чего отмена не достаёт, — с единственной ошибкой, которую
каждая копия решала чуть по-своему. Он ещё и дожидается анимации перед возвратом, а это
не даёт полосе освободиться, пока кадры ещё рисуются.

Ключевые моменты для остальной части сервиса:

- `@AndroidEntryPoint` для инъекции
- `ACTION_START` / `ACTION_STOP` в `companion object` с префиксом `com.bleelblep.glyphsharge.`
- `startForeground()` первым оператором в `onStartCommand`
- `CoroutineScope(Dispatchers.Main + SupervisorJob())`
- Регистрация любого `BroadcastReceiver` через `ContextCompat.registerReceiver(...)`

---

### Шаг 4. Добавьте карточку и её диалог

Создайте `ui/components/ProgressOnPickup.kt` по образцу
[ScreenOff.kt](../app/src/main/java/com/bleelblep/glyphsharge/ui/components/ScreenOff.kt):
дата-класс конфигурации, диалог подтверждения поверх `FeatureConfirmationFlow` и сам
диалог настройки.

**Карточка не принимает параметр настроек.** Она читает хранилище из композиции, и именно
это делает карточку вызываемой самой по себе — из превью или откуда угодно ещё, кому
нужна карточка:

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

**Диалог настройки тоже читает хранилище из композиции:**

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

`rememberGlyphAnimationManager()` в
[GlyphDependencies.kt](../app/src/main/java/com/bleelblep/glyphsharge/ui/components/GlyphDependencies.kt)
— это весь ответ на вопрос «как обычный composable дотягивается до менеджера»: он
разрешает `HomeViewModel` через `hiltViewModel()`, что работает внутри диалога ровно так же,
как с экрана, и отдаёт тот же синглтон, который держат фичевые сервисы.

**Рецепт диалога настройки** (одинаков для всех фич):

1. Прочитать текущие значения **один раз** в `remember` (или `rememberFloatStateOf` для слайдеров).
2. Изменения писать в репозиторий сразу **или** только по кнопке Save — в проекте
   преобладает первый вариант для выбора анимации, второй — для слайдеров.
3. Тело: прокручиваемый `Column` (`heightIn(max = 400.dp)`, `spacedBy(20.dp)`) из
   `Card` с `themeCardContainerColor()`, слайдеры через `themePrimaryActionColor()`,
   значение через `ThemedValueBadge`.
4. Каждое движение слайдера — `HapticUtils.triggerMediumFeedback(haptic, context, vibrationIntensity)`.
5. Кнопки — `FeatureSaveButtons`.

---

### Механические части

Ничто из этого не архитектура, но без этого фича недостижима.

**Ключи настроек.** Ключи и аксессоры живут в
[FeatureSettings.kt](../app/src/main/java/com/bleelblep/glyphsharge/data/FeatureSettings.kt)
(срез, владеющий фичами), а фасад переадресует их из
[SettingsRepository.kt](../app/src/main/java/com/bleelblep/glyphsharge/data/SettingsRepository.kt),
чтобы лямбдам реестра было что вызывать:

```kotlin
fun saveProgressOnPickupEnabled(enabled: Boolean) = prefs.putSetting(KEY_PROGRESS_ENABLED, enabled)

fun isProgressOnPickupEnabled(): Boolean = prefs.getSetting(KEY_PROGRESS_ENABLED, false)

fun saveProgressOnPickupDuration(durationMs: Long) =
    prefs.putSetting(KEY_PROGRESS_DURATION, durationMs)

fun getProgressOnPickupDuration(): Long =
    prefs.getSetting(KEY_PROGRESS_DURATION, DEFAULT_PROGRESS_DURATION)
```

и новый шаг в `applyVersionMigrations()` — именно он записывает значение по умолчанию тем,
у кого приложение уже стоит:

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
> Проверка `prefs.contains(...)` обязательна — без неё у пользователя, у которого фича
> уже включена, миграция сбросит настройку при обновлении.

> [!IMPORTANT]
> Номер версии — это *следующий за уже занятым*, а не константа. Сейчас `SettingsMigrations`
> заканчивается на 114 (шаг VPN-connected), поэтому фича, добавляемая сегодня, берёт 115.
> Повторное использование занятого номера даёт шаг, который никогда не выполнится: более
> ранний шаг уже передвинул сохранённую версию за него.

**Манифест.** В [AndroidManifest.xml](../app/src/main/AndroidManifest.xml), рядом с
остальными сервисами:

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
> Описание в `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` должно **точно** соответствовать назначению
> сервиса. Google Play отклоняет публикации с недостоверными формулировками —
> в текущем коде у трёх сервисов скопирован текст про «низкий заряд батареи».

Не забудьте `<uses-permission>`, если сервису что-то нужно, и собственный `<uses-feature>`,
если задействован датчик.

**Строки.** Скопируйте существующий блок вроде `charging_animation_*` в
[values/strings.xml](../app/src/main/res/values/strings.xml) — заголовок, описание, тост о
выключенном сервисе, который называет `FeatureSpec`, пару «как это работает», подписи Test
и Save — и добавьте **те же ключи** в `res/values-ru-rRU/strings.xml` с переводом.

> [!TIP]
> Покрытие локализации в проекте полное — 298 строк в `values/` и 298 в `values-ru-rRU/`.
> Сохраняйте паритет, иначе русский интерфейс покажет английский текст.

**Запись на главном экране.** В
[HomeFeatures.kt](../app/src/main/java/com/bleelblep/glyphsharge/ui/screens/home/HomeFeatures.kt)
добавьте `item {}` в `homeFeatureCards`. Эта функция — чистая обвязка: карточке нужны только
состояние переключателя и два колбэка:

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
> Порядок карточек на экране задаётся именно здесь — это единственное место, где
> перечисляются элементы списка.

**Кнопка Test карточки.** `HomeViewModel.testFeature()` — это исчерпывающий `when` по enum,
так что компилятор укажет на него сам; добавьте ветку и запись в `TOAST_BY_FEATURE`:

```kotlin
GlyphFeature.PROGRESS_ON_PICKUP -> glyphAnimationManager.playProgressOnPickupAnimation()
```

**Восстановление после загрузки (опционально).** В
[BootCompletedReceiver.kt](../app/src/main/java/com/bleelblep/glyphsharge/receiver/BootCompletedReceiver.kt),
внутри `startServicesInOrder`, в блоке второго уровня:

```kotlin
if (glyphOn && settingsRepository.isProgressOnPickupEnabled()) {
    Log.d(TAG, "Tier 2 – ProgressOnPickup")
    context.startForegroundServiceCompat(ProgressOnPickupService::class.java)
}
```

---

### Чек-лист перед сборкой

- [ ] Значение добавлено в единственный enum `GlyphFeature`
- [ ] Одна запись `FeatureSpec` — класс сервиса, действие остановки, обе лямбды настройки, сообщение
- [ ] Сервис берёт полосу через `withStrip` и ограничивает прогон через `runCapped`
- [ ] `startForeground()` в начале `onStartCommand`
- [ ] `runCatching` вокруг `wakeLock.release()` и `unregisterReceiver`
- [ ] Сервис объявлен в манифесте с `specialUse` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`
- [ ] Ключи настроек добавлены, шаг миграции версий добавлен
- [ ] Строки добавлены в **оба** `strings.xml`
- [ ] `when` в `HomeViewModel.testFeature()` и `TOAST_BY_FEATURE` обновлены
- [ ] Карточка не принимает параметр `settingsRepository`; диалог читает `LocalSettingsRepository`

### Частые ошибки

| Симптом | Причина |
|---------|---------|
| `IllegalStateException: FeatureSpecs has no entry for …` | Значение в enum добавлено, а запись `FeatureSpec` — нет |
| `ForegroundServiceDidNotStartInTimeException` | `startForeground()` не вызван в первых строках `onStartCommand` |
| Фича включается, но глифы не горят | `acquire()`/`release()` вызваны руками вместо `withStrip` — мьютекс залип, и каждая другая фича навсегда сообщает «полоса занята» |
| Фича работает один раз и больше никогда | Сервис отменили, пока он держал замок; `withStrip` кладёт освобождение в `finally` ровно ради этого |
| Вместо диалога всплывает тост | Выключен мастер-переключатель «Glyph service» — проверьте `isServiceActive` |
| Анимация никогда не останавливается по настройке длительности | Прогон запущен через `launch`, а не через `runCapped` |
| Сервис не стартует после перезагрузки | Не добавлен в `BootCompletedReceiver` либо флаг фичи `false` |
| Локализованный текст пустой | Ключ добавлен только в один из `strings.xml` |

---

## 8. Слой UI

### Темы

```kotlin
enum class AppThemeStyle { CLASSIC, Y2K, NEON, AMOLED, PASTEL, EXPRESSIVE }
```

- `CLASSIC` — стандартный Material 3
- `Y2K` — хром, киберпанк
- `NEON` — электрические контрастные цвета
- `AMOLED` — истинный чёрный, минимализм
- `PASTEL` — мягкие цвета
- `EXPRESSIVE` — Material 3 Expressive (асимметричные формы, `CutCornerShape`)

Хранится в `ThemeState` (`@Singleton`) как `mutableStateOf`, персистится в
`SettingsRepository.saveThemeStyle` под ключом `theme_style`. При ошибке чтения — `CLASSIC`.

**Корневой composable:**

```kotlin
@Composable
fun GlyphZenTheme(
    themeState: ThemeState,
    fontState: FontState,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
)
```

Публикует `LocalFontState` и `LocalThemeState` (оба `staticCompositionLocalOf`).
Вызывается из двух мест: `MainActivity.setupUI()` и `CustomAnimationsActivity` — студия
это отдельная Activity со своим стеком возврата, и ей тоже нужна тема.

**Цветовые схемы:** 12 схем в `ColorSchemes.kt` (тёмная и светлая для каждого стиля).
`getColorScheme(themeStyle, isDark)` — исчерпывающий `when` **без `else`**,
поэтому новое значение в enum сломает компиляцию до того, как вы забудьте про схему.

**Цвета-резолверы** в `ThemeColors.kt` (читают `LocalThemeState`):
`themeCardContainerColor()`, `themePrimaryActionColor()`, `themeSettingsButtonColor()`,
`themeSecondaryButtonColors()`.

**Хранилище настроек доходит до дерева через composition local.** Каждая Activity отдаёт
его один раз, рядом с силой хаптики:

```kotlin
CompositionLocalProvider(
    LocalSettingsRepository provides settingsRepository,
    LocalVibrationIntensity provides settingsRepository.getVibrationIntensity(),
) { /* the whole screen tree */ }
```

`LocalSettingsRepository` — то, что читает карточка, диалог или секция настроек, вместо
того чтобы принимать параметр, который пришлось бы протягивать каждому вызывающему.
`LocalVibrationIntensity` живёт отдельно, потому что `HapticUtils` — обычный объект,
вызываемый из обработчиков нажатий, а обработчик не умеет читать репозиторий: float в
композиции позволяет внедрять значение один раз и передавать вниз обычным аргументом.

> [!NOTE]
> Параметр `dynamicColor` нигде не передаётся `true` — Material You динамические цвета
> фактически отключены, используются только собственные схемы.

### Шрифты

```kotlin
enum class FontVariant { HEADLINE, NDOT, SYSTEM }
enum class FontCategory { DISPLAY, TITLE, BODY, LABEL }
data class FontSizeSettings(displayScale, titleScale, bodyScale, labelScale)
```

- `HEADLINE` → `R.font.ntype_82_headline` (NType Headline)
- `NDOT` → `R.font.ndot55caps` (NDot 55 Caps)
- `SYSTEM` → `FontFamily.Default`

`FontState` (`@Singleton`) держит `currentVariant`, `useCustomFonts`, `fontSizeSettings`
и выдаёт `getTitleFont()` / `getBodyFont()`. Масштабирование применяется в
`createTypography(...)` — каждый из 15 слотов Material 3 умножается на коэффициент
своей категории. Для `SYSTEM` коэффициенты по умолчанию `0.8f`, для остальных — `1.0f`.

> [!NOTE]
> Файлы `res/font/*.xml` (`fonts.xml`, `ntype_headline.xml`, `ntype_regular.xml`)
> **не используются кодом** — `FontState` собирает `FontFamily` напрямую из `.otf`.
> Кроме того, `ntype_regular.xml` указывает на `ndot55caps` (опечатка в имени).

### Компоненты

| Компонент | Файл | Назначение |
|-----------|------|-----------|
| `ContentCard` | `cards/ContentCards.kt` | Базовая карточка: пружинная анимация нажатия, хаптика |
| `FeatureCard` | `cards/ContentCards.kt` | Иконка + заголовок + описание |
| `SquareFeatureCard` | `cards/ContentCards.kt` | Квадратная карточка 1:1, приглушается при выключенном сервисе |
| `WideFeatureCardWithToggle` | `cards/ContentCards.kt` | Карточка фичи с наложенным переключателем |
| `GlyphControlCard` | `cards/ContentCards.kt` | Карточка мастер-переключателя глифов |
| `PowerPeekCard` … `MusicVisualizerCard`, `VpnConnectedCard` | `FeatureCards.kt`, `VpnConnected.kt` | 8 карточек фич |
| `rememberGlyphAnimationManager()` | `GlyphDependencies.kt` | Менеджер за кнопкой Test на каждой карточке |
| `MorphingToggleButton` | `controls/ToggleButtons.kt` | Переключатель с анимацией морфинга |
| `ThreeStateFontMorphingButton` | `controls/ToggleButtons.kt` | Трёхпозиционный выбор шрифта |
| `FeatureDialogScaffold` | `dialogs/` | Каркас диалога: заголовок, подзаголовок, блок «как это работает» |
| `FeatureConfirmationFlow` | `dialogs/` | Двухсостоянийный автомат: подтверждение → настройка |
| `FeatureConfirmationButtons` | `CommonDialogComponents.kt` | Ряд кнопок Test / ⚙ / Cancel |
| `FeatureSaveButtons` | `CommonDialogComponents.kt` | Ряд кнопок Save / Disable / Cancel |
| `ThemedValueBadge` | `CommonDialogComponents.kt` | Бейдж текущего значения слайдера |
| `SettingsScaffold` | `layout/` | Общий каркас всех экранов: `LargeTopAppBar` + `LazyColumn` |
| `DraggableSettingsCard` | `layout/` | Карточка с перетаскиванием для перехода; есть необязательный `onClick` (его использует карточка «Custom Animations») |
| `HomeSectionHeader`, `FeatureGrid` | `layout/SectionLayout.kt` | Заголовок секции и сетка |

### Экраны

| Экран | Файл | Сигнатура |
|-------|------|-----------|
| Главный | `screens/home/HomeScreen.kt` | `HomeScreen(onOpenSettings, modifier, viewModel = hiltViewModel())` |
| Настройки | `screens/SettingsScreen.kt` | `SettingsScreen(onBackClick, onThemeSettingsClick, onFontSettingsClick, onQuietHoursSettingsClick, onLanguageSettingsClick)` |
| Тема | `screens/ThemeSettingsScreen.kt` | `ThemeSettingsScreen(onBackClick, modifier)` |
| Шрифты | `screens/FontSettingsScreen.kt` | `FontSettingsScreen(fontState, onNavigateBack, modifier)` |
| Тихие часы | `screens/QuietHoursSettingsScreen.kt` | `QuietHoursSettingsScreen(onBackClick)` |
| Язык | `screens/LanguageSettingsScreen.kt` | `LanguageSettingsScreen(onBackClick, onLanguageChanged, modifier)` |
| Список анимаций | `screens/animations/AnimationListScreen.kt` | Скрипты пользователя: открыть, создать, дублировать, удалить, импорт, два экспорта |
| Редактор анимации | `screens/animations/AnimationEditorScreen.kt` | Имя, поле кода, кнопки Check / Glyph, консоль, сворачиваемая памятка по `glyph` |

> [!NOTE]
> **Ни один экран не принимает параметр настроек.** Каждый берёт хранилище из
> `LocalSettingsRepository.current`, а свою ViewModel — из `hiltViewModel()`, и именно
> это оставляет экран достижимым из превью или теста без навхоста. Последние две строки
> живут **вне `GlyphNavHost`** — их хостом является `CustomAnimationsActivity`. См.
> [раздел 14](#14-пользовательские-анимации-lua).

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

    val uiState: StateFlow<HomeUiState>          // главный переключатель + карта фич
    val messages: Flow<String>                    // одноразовые события → Toast
    val musicCaptureSource: PlaybackAudioSource    // синглтон, который просит карточка визуализации
    fun refreshFeatures()
    fun onSessionStateChanged(isActive: Boolean)
    fun toggleGlyphService(enabled: Boolean)
    fun setFeatureEnabled(feature: GlyphFeature, enabled: Boolean)
    fun testFeature(feature: GlyphFeature)
    fun onMusicCaptureResult(resultCode: Int, data: Intent?)
    fun setNfcDispatchHook(hook: (Boolean) -> Unit)
}
```

`HomeUiState` хранит `glyphServiceEnabled: Boolean` и
`features: Map<GlyphFeature, FeatureUiState>`; доступ через `stateOf(feature)`.

Одноразовые сообщения идут через `Channel<String>(Channel.BUFFERED)` → `receiveAsFlow()`,
потому что Toast нельзя держать в состоянии.

`setNfcDispatchHook` — исключение: `enableForegroundDispatch` требует настоящей Activity,
которую ViewModel удерживать не может, поэтому Activity регистрирует свой хук.

### AnimationStudioViewModel

```kotlin
@HiltViewModel
class AnimationStudioViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: CustomAnimationRepository,
    private val glyphAnimationManager: GlyphAnimationManager,
    private val glyphManager: GlyphManager
) : ViewModel() {

    val uiState: StateFlow<StudioUiState>   // список + редактор + консоль
    val messages: StateFlow<String?>        // одноразовые Toast
    fun newAnimation() / open(id) / closeEditor()
    fun updateName(name) / updateSource(source) / save()
    fun check()                              // только компиляция
    fun runOnGlyph()                         // на настоящем глифе
    fun stop()
    fun delete(id) / duplicate(id)
    fun importFrom(uri) / exportTo(uri, id) / exportToDownloads(id)
    fun consumeMessage()
}
```

`StudioUiState` — один неизменяемый снимок: `animations`, `isEditorOpen`, `editing`,
`name`, `source`, `isDirty`, `console`, `isRunning`.

`isEditorOpen` хранится, а не выводится из `editing`, намеренно: у совершенно нового
скрипта есть черновик для правки, но состояние, в котором редактор открывается на пустом
буфере, оказалось бы непредставимым — ровно та ошибка, которую провоцирует выведенный флаг.

**Check** компилирует исходник и сообщает о первой синтаксической ошибке, не касаясь глифа.
**Glyph** отправляет тот же исходник через `GlyphAnimationManager.previewScript`, то есть
по тому пути, которым пользуются фичевые сервисы, и это единственная настоящая проверка.
Экранного превью нет: схема раскладки светодиодов — не то же самое, что загоревшийся
телефон, а второй режим «запуска» провоцирует путать одно с другим. Предполётные проверки
(телефон без интерфейса глифов, незакрытая сессия) живут здесь, а не внутри менеджера,
потому что относятся к тому, на что смотрит *студия*.

Прогон ограничен `ScriptAnimation.SAFETY_CAP_MS`, а не каким-то студийным лимитом: скрипт —
это программа, она идёт, пока не дойдёт до конца своего кода, а Stop — это способ выйти.

---

## 9. Навигация

Есть только `Routes` и `GlyphNavHost` — навигация строится в коде, XML-графа нет.

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
> Константы вынесены в объект специально: маршруты были строковыми литералами
> в разных местах, и опечатка (`"hidden_settings"`) компилировалась, а падала только в рантайме.

`GlyphNavHost` регистрирует шесть `composable(...)`, все переходы —
`MaterialSharedAxisZ` (fade + scale, 200/120 мс).

> [!IMPORTANT]
> `CustomAnimationsActivity` — **вторая** Activity приложения, и её нет в `GlyphNavHost`
> и в `Routes`. У неё свой `BackHandler` между списком и редактором и свои
> `ActivityResultLauncher` для системных файловых диалогов. Если студии понадобится
> быть частью навграфа настроек, это отдельная задача, а не добавление `composable(...)`.

### Как добавить новый экран

1. Добавьте `const val MY_SCREEN = "my_screen"` в `Routes`.
2. Добавьте `composable(Routes.MY_SCREEN) { MyScreen(onBackClick = { navController.popBackStack() }) }`
   внутрь `NavHost { }` в `GlyphNavHost.kt`. Переходы подхватятся автоматически.
3. Добавьте колбэк `onMySettingsClick` в родительский экран и `navController.navigate(Routes.MY_SCREEN)`.
4. Стройте экран на `SettingsScaffold(title, onBackClick) { item { … } }`.

---

## 10. Музыкальная визуализация

Рисует на полосе глифов всё, что сейчас играет на телефоне, — пока играет.
Единственный сервис без триггера: остальные ждут события, этот следит за потоком.

### Слои

| Задача | Где |
|--------|-----|
| На каких каналах рисует режим | `glyph.animations.AudioAnimations` — `resolveStrip` |
| Математика спектра | `glyph.audio.AudioAnalysis` (чистые функции, без Android-типов) |
| Владение `Visualizer` | `glyph.audio.AudioAnalyzer` |
| Захват `MediaProjection`, которым управляет сервис | `glyph.audio.PlaybackAudioSource` |
| Кадр, через который читаются оба захвата | `glyph.audio.AudioFrameFeed` |
| Какой режим выбрал пользователь | `glyph.audio.MusicVisualizationMode` |
| Запуск, стоп, нарезка на куски | `services.MusicVisualizerService` |
| Проигрывание и превью из настроек | `glyph.audio.MusicVisualisation` |

### Шесть режимов

`BARS` (эквалайзер), `WAVE` (бегущая линия уровня), `MIRROR` (столбики от
центра), `BEAT` (заливка со вспышкой на каждом ударе), `MATRIX` (дождь, который
тянет спектр), `VORTEX` (два встречных кольца, которые крутит бас).

Все шесть — это один цикл `runMusicVisualization` с `when` по режиму, поэтому
темп, поведение в тишине и состояние между кадрами живут ровно в одном месте.
Частота — около 30 кадров/с.

> [!IMPORTANT]
> **Именно `resolveStrip` делает фичу рабочей на четырёх телефонах.** У Phone (1)
> в полосе C всего четыре канала против двадцати у Phone (3a). При восьми и
> меньше каналах визуализация берёт `profile.all`: эквалайзер на двадцать
> столбиков, вписанный в четыре канала, — это четыре мигающие точки.

### Как полоса достаётся другим фичам

`GlyphFeatureCoordinator` отдаёт полосу одному владельцу, и все шесть коротких
фич берут её через `withStrip`, которая проигрывает, а не борется. Визуализация,
держащая полосу весь трек, проглотила бы анимацию зарядки, алерт низкого
заряда и эффект выключения экрана.

Поэтому она рисует `SLICE_MS` (1.5 с), отпускает полосу, ждёт `YIELD_GAP_MS`
(60 мс) и берёт её снова. Триггер, попавший в паузу (4% времени), отрабатывает
сразу; остальные ждут не больше 1.5 с. Цена — тёмный шов раз в 1.5 с, и это тот
компромисс, на котором остановился план: альтернатива — фича, которая молча
не срабатывает никогда.

### Что захватывается, а что нет

> [!WARNING]
> `Visualizer` на сессии `0` читает **весь выходной микс**, а не звук одного
> приложения. Это единственный поток, доступный обычному приложению на
> современном Android. Уведомления, рингтоны и системные звуки лежат в том же
> буфере. PCM не покидает `AudioAnalyzer` никуда, кроме уровней `0..1`: ничего не
> записывается, не хранится и не передаётся, и карточка «Как это работает» в
> приложении говорит об этом прямо.
>
> Буфер используется, только когда играет что-то музыкальное: решают
> `AudioManager.isMusicActive()` и посчитанный уровень.

`RECORD_AUDIO` открывает весь доступ и запрашивается в момент включения фичи —
так же, как Power Peek просит своё разрешение.

### Деградация

Если FFT-поток устройства пуст, пока волнаформа громкая, `AudioAnalyzer`
переключается на разбиение волнаформы: спектр начинает следить за громкостью
вместо высоты, и в лог уходит
`FFT is empty while the waveform is loud`. Любой режим продолжает реагировать на
музыку.

---

## 11. Ресурсы и локализация

```
app/src/main/res/
├── font/        ntype_82_headline.otf, ntype_82_regular.otf, ndot55caps.otf, *.xml
├── drawable/    _44.xml, _78.xml, _23_24px.xml, su.png, иконки тем
├── values/      strings.xml (298), colors.xml (24), themes.xml
├── values-night/ colors.xml (5 переопределений для тёмной темы)
├── values-ru-rRU/ strings.xml (298) — полное покрытие
└── xml/         file_paths.xml, backup_rules.xml, data_extraction_rules.xml
```

**Локализация.** Две локали: английский (`values/`, по умолчанию) и русский
(`values-ru-rRU/`), по 298 строк в каждой. Переключение языка внутри приложения
(en / ru / system) хранится под ключом `"language"` и применяется через
`Context.applyLocale(code)` в `MainActivity.attachBaseContext` + перезапуск Activity.

Студия добавила 50 строк `studio_*` и 3 строки `settings_card_custom_animations*`
в **обе** локали. Язык применяется и в `CustomAnimationsActivity.attachBaseContext` —
это отдельная Activity со своим контекстом.

> [!WARNING]
> Часть UI-текста **захардкожена по-английски** и не проходит через `strings.xml`:
> `GlyphAnimations.displayName`, подписи «Start»/«Cancel» в `SquareFeatureCard`,
> `HomeScreen` (`"Glyph Sharge"`, `"Features"`, `"Settings"`),
> `HomeViewModel` (`"Glyph service is already enabled"`, `"Error: …"`), и
> `TOAST_BY_FEATURE`. При этом `AnimationListScreen` и `AnimationEditorScreen` идут
> через `stringResource` — собственные экраны студии и строки консоли — это все
> `studio_*`-строки, которые ViewModel получает через `context.getString`.

**Разрешения** (`AndroidManifest.xml`): `VIBRATE`, `WAKE_LOCK`, `WRITE_SETTINGS`,
`WRITE_EXTERNAL_STORAGE`, `com.nothing.ketchum.permission.ENABLE`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_SPECIAL_USE`, `FOREGROUND_SERVICE_DATA_SYNC`,
`FOREGROUND_SERVICE_MEDIA_PROJECTION`, `SYSTEM_ALERT_WINDOW`, `RECEIVE_BOOT_COMPLETED`,
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `DISABLE_KEYGUARD`, `TURN_SCREEN_ON`,
`SCHEDULE_EXACT_ALARM`, `NFC`, `ACCESS_NETWORK_STATE` (VPN Connected — состояние, а не
сама сеть), `RECORD_AUDIO`, `READ_MEDIA_AUDIO`.

---

## 12. Сборка и конфигурация

### Build-скрипты

Все скрипты — на Kotlin DSL, а `gradle/libs.versions.toml` — единственный источник правды
для каждой версии плагина и библиотеки; ни одна версия не объявлена дважды.

| Что | Файл | Ключевые значения |
|-----|------|-------------------|
| Версии | `gradle/libs.versions.toml` | AGP 8.13.2, Kotlin 1.9.10, KSP 1.9.10-1.0.13, Hilt 2.50, Compose compiler 1.5.3 |
| Корень | `build.gradle.kts` | только алиасы плагинов, каждый с `apply false` |
| Settings | `settings.gradle.kts` | `google()` / `mavenCentral()`, `FAIL_ON_PROJECT_REPOS`, включает `:app` |
| App | `app/build.gradle.kts` | namespace + applicationId `com.bleelblep.glyphsharge`, compileSdk 36, min/target 34, JVM 17, versionCode 1031, versionName 1.0.31 |

Две детали, которые легко прочитать неправильно в скрипте приложения:

- **Compose compiler — это версия, а не плагин.** Kotlin 1.9.x предшествует
  `org.jetbrains.kotlin.plugin.compose` — этот плагин начинается с Kotlin 2.0, — поэтому
  версия передаётся через `composeOptions { kotlinCompilerExtensionVersion }`.
- **Обработка аннотаций идёт только через KSP.** Компилятор Hilt подключён как `ksp(...)`,
  и второго процессора аннотаций не применяется, поэтому на каждую компиляцию приходится
  ровно один проход обработки.

### Зависимости

Compose BOM 2024.02.00, Navigation Compose 2.7.7, lifecycle-runtime 2.7.0,
Hilt 2.50, `material-icons-extended` 1.6.0, Ketchum SDK (`libs/*.jar`),
**LuaJ 3.0.1** (`org.luaj:luaj-jse`) — движок пользовательских анимаций.
`kotlinx-coroutines` объявлен неявно, приезжает транзитивно.

Объявлены в `app/build.gradle.kts`, но **не используются**: `material3-window-size-class`,
`lottie-compose` (онлайн-флоу в проекте нет) и весь набор `androidTestImplementation` —
source set `androidTest` в этом проекте отсутствует, хотя `testInstrumentationRunner`
объявлен.

### Тесты

```bash
./gradlew :app:testDebugUnitTest
```

**118 юнит-тестов, 9 наборов, всё чистый JVM** — без эмулятора, без устройства, без
Robolectric. Прогон занимает около двух секунд.

| Набор | Тестов | Что закрепляет |
|-------|--------|----------------|
| `glyph/script/LuaScriptEngineTest` | 21 | Что скрипт действительно выполняется: рисование, циклы, группы каналов, `glyph.hold`, `glyph.exit`, синтаксис и рантайм-ошибки, сторож против бесконечного цикла и против `pcall`, прерывание длинного `glyph.hold`, отсутствие опасных глобалов, `validate()`, а также `glyph.target`, прочитанный самим скриптом и попавший в результат |
| `glyph/script/ScriptTargetTest` | 15 | Классификатор цели: стиль кавычек, произвольные пробелы, **строчные и блочные комментарии**, откат к `ANY` на неизвестном значении и то, какой пикер что предлагает |
| `glyph/script/ScriptFileFormatTest` | 7 | Round trip `encode`/`decode`, импорт без заголовка, имя с переводом строки, безопасные имена файлов, пространство имён `custom:` |
| `glyph/audio/FftTest` | 17 | Энергия попадает в нужный бин, тишина даёт ровные нули, амплитуда линейна, размер — степень двойки, закэшированные планы не мешают друг другу |
| `glyph/audio/AudioFrameTest` | 13 | `bandsInto` берёт пик, а не среднее; при любом числе сегментов ни одна полоса не теряется; пишется массив вызывающего; `isSilent` по порогу; `toString()` не зависит от локали |
| `glyph/audio/BeatDetectorTest` | 12 | Скользящее среднее, кулдаун, постоянный уровень не барабанит вечно, `reset()` между треками |
| `glyph/audio/AudioAnalysisTest` | 6 | Калибровка, на которой держится яркость всех режимов: нота попадает в свою полосу, тишина не даёт ничего, тихая комната всё равно читается как тишина |
| `glyph/audio/MusicVisualizationModeTest` | 11 | Сохранённые id разрешаются, `custom:<uuid>` **не** является режимом, и каждый режим однозначно отнесён к проигрыванию вхолостую или нет |
| `glyph/device/DeviceProfileFactoryTest` | 16 | Таблицы каналов по моделям: каждая группа состоит из реально подключённых каналов, ничего не подключено дважды, бюджеты растут вместе с железом, а второй C-сегмент Phone (2) не попадает в полосу батареи |

Наборы сгруппированы **по тому, что ломается молча**, а не по пакетам. Каждый покрывает код,
чей отказ выглядит правдоподобно и при этом неверен:

> [!IMPORTANT]
> Неверная константа в FFT, сдвиг на единицу в диапазоне каналов, потерянная граница полос
> или зависящее от локали число дают результат, который отрисовывается нормально и неотличим
> от правильного при взгляде на него. Именно для этого тесты и существуют — и поэтому они
> проверяют известные сигналы, а не внутренности.

`app/build.gradle.kts` несёт блок, от которого наборы зависят:

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
> `android.util.Log` в JVM-тестах — заглушка, поэтому без `isReturnDefaultValues = true`
> любой вызов `Log.d` внутри слоя скриптов роняет тест с
> `RuntimeException: Method d in android.util.Log not mocked`.
> Скрипт-тесты подменяют железо на `FakeHost` — это `GlyphScriptHost`, записывающий кадры,
> которые нарисовал бы настоящий прогон, — то есть той же формы, что и
> `ScriptRunner.RendererHost` показывает VM, поэтому зелёный прогон заодно доказывает,
> что путь хостинга работает.

#### Что не покрыто

> [!NOTE]
> У **сервисов**, **Compose-интерфейса** и **сборки Hilt** тестов нет, а source set
> `androidTest` отсутствует, поэтому и instrumented-прогона, на который можно было бы
> опереться, тоже нет. Сервис — это foreground-компонент, управляемый broadcast-ами на
> телефоне с глифом, и JVM-аналога у него нет: честное покрытие здесь — настоящий
> Nothing Phone и ручная проверка.
> Всё, что является чистой логикой, попадает в наборы выше: вынесите решение из `when`
> и проверяйте его там, как уже сделано для `MusicVisualizationMode.isIdleFriendly`
> и `ScriptSession.result`.

### Релиз

```bash
./gradlew app:assembleRelease
```

`isMinifyEnabled = false` — R8 не запускается, поэтому `proguard-rules.pro` к
поставляемой сборке не применяется.

> [!NOTE]
> Если минимизация когда-нибудь включится, одно правило в `proguard-rules.pro` будет
> несущим, а не оборонительным: `-keepclassmembers enum com.bleelblep.glyphsharge.ui.theme.**`
> сохраняет константы `AppThemeStyle` и `FontVariant`, которые сохраняются по `.name` и
> читаются обратно через `valueOf(String)`. Штатные правила Android сохраняют `values()`
> и `valueOf(String)`, но не сами константы, поэтому обфусцированная сборка перестала бы
> узнавать любое сохранённое значение и молча падала бы на тему по умолчанию.

---

## 13. Известные проблемы и подводные камни

> [!WARNING]
> Этот раздел — не список жалоб, а список мест, где код ведёт себя неочевидно.
> Проверяйте их, прежде чем менять поведение.

### Архитектура

| Проблема | Где | Следствие |
|----------|-----|-----------|
| **Пустые стабы в `MainActivity`** | `startPersistentGlyphService()`, `maybeRestoreSession()`, `writeLogToUri()` | `GlyphForegroundService` **не стартует** при обычном запуске приложения — только после перезагрузки |
| У тихих часов нет `GlyphFeature` | `MainActivity.startQuietHoursService()` | Стартует обычным `Intent`, потому что сознательно вынесен за пределы реестра фич: каждое значение `GlyphFeature` идёт через контроллер |

### Логирование

`LoggingManager` выключен по умолчанию: `isLoggingEnabled = false`, а
`setLoggingEnabled(true)` **нигде не вызывается**. Все 22 метода логирования —
no-op, включая `logSessionState` и `logSDKOperation` внутри `GlyphManager`.
`shareLogs()` и `LoggingManager.exportLogs()` тоже не вызываются ни разу.

> [!NOTE]
> При отладке глифов включите логи вручную:
> `LoggingManager.initialize(this)` уже вызывается в `GlyphShargeApplication.onCreate`,
> но `setLoggingEnabled(true)` нужно добавить самому.

### GlyphManager

- `forceEnsureSession()` блокирует поток через `Thread.sleep` до 2 секунд.
- `turnOnAllGlyphs()` — публичный API, но вызовов нет.
- `onServiceDisconnected` вызывает `cleanup()`, который сбрасывает флаг инициализации,
  но `mCallback` остаётся зарегистрированным; переподключение берётся только из `handleError`.
- `handleError` восстанавливается только для двух точных сообщений — `"Session not active"`
  и `"Service not connected"`. Любой другой `GlyphException` рвёт сессию.

### Слой glyph после рефакторинга

Пакет `glyph` разделён по назначению. Важно знать следующее:

- `GlyphManager` больше не дублирует определение модели — `DeviceType.detect()` — единственное
  место, где вызывается `Common.is*`.
- `GlyphManager` больше не захардкодивает диапазоны каналов в `turnOnAllGlyphs()`; берёт их
  из `DeviceProfileFactory`.
- `GlyphRenderer` — единственный класс, который трогает `mGM`; анимации сами кадры не строят.
- Числа по моделям лежат в двух таблицах `DeviceProfileFactory` вместо четырёх литералов
  профиля по ~45 строк.
- `GlyphFeatureCoordinator` по-прежнему вызывает `turnOffAll()` напрямую, а не через
  `GlyphRenderer`. Это гашение, а не отрисовка, так что это осознанно.

### Слой скриптов (`glyph/script/`)

Места, где код ведёт себя неочевидно:

- `debug` **загружается**, используется и только потом обнуляется. Пока `Globals.debuglib`
  не установлен, VM вообще не спрашивает хук — убрать библиотеку до установки сторожа
  нельзя, а оставить её видимой нельзя, иначе скрипт снимет сторож через `debug.sethook`.
  Порядок в `buildGlobals` менять нельзя.
- Хук пишется прямо в `LuaThread.state.hookfunc` / `hookcount`, а не через `debug.sethook`,
  потому что таблица `debug` к этому моменту уже скрыта от скрипта.
- Сторож бросает Java-класс `ScriptAbortedError` (наследник `Error`, не `Exception`).
  Lua-ошибка была бы перехвачена `pcall` внутри скрипта, и цикл продолжил бы жечь CPU.
- Прерываемые паузы — срез по `CANCEL_POLL_MS = 16L`, а максимум одного `glyph.hold`
  ограничен `MAX_HOLD_MS = 60_000L`. Любой `Thread.sleep` внутри вызова глифа держал бы
  полосу горящей все запрошенное время после нажатия «стоп».
- `print` перенаправлен в консоль студии, но `os` и `io` удалены, так что у скрипта
  нет ни stdout, ни файловой системы.
- `ScriptRunner.check(source)` возвращает `null`, если профиль подключённого телефона
  неизвестен: проверить синтаксис нечем, и кнопка Check молча ничего не напишет.
- `ScriptRunner.RendererHost` мостит suspend-рендерер в блокирующий хост через `runBlocking`.
  Вызывающий всегда вне главного потока — иначе получите блокировку UI на весь кадр.
- `previewScript` сознательно **не** проверяет мастер-тумблер глифов (студия — явное
  действие пользователя), а `playCustomAnimation` проверяет (это уже работа фичи).
- `forPreview()` в `DeviceProfileFactory` всегда отдаёт раскладку Phone (3a). Больше
  ничего в `main/` её не вызывает — в студии нет экранного превью, — и никогда не вызывал
  то, что зажигает настоящие светодиоды; им пользуются только тесты.

### UI

- `ChargingAnimationConfig.isEnabled` и `PowerPeekConfig.enableWhenScreenOff`
  записываются, но соответствующих ключей в репозитории **нет** —
  переключатель «только при выключенном экране» в диалоге Power Peek ничего не делает.
- `ThreeStateFontToggle` и `FontState.getDisplayFont()` не используются.
- `GlyphControlCard.illustrationRes` принимается, но не используется в теле.
- `QuietHoursSettingsScreen` содержит пустой `LaunchedEffect(Unit)` с одним комментарием,
  поэтому `quietHoursEnabled` может устареть.

### Манифест

- `android:name=".GlyphShargeApplication"` совпадает с именем класса, но **файл называется
  `GlyphZenApplication.kt`** — имя файла не соответствует единственному классу внутри.
- Описания `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` у `NfcGlyphService`,
  `ChargingAnimationService` и `LowBatteryAlertService` **скопированы** и говорят
  про «низкий заряд батареи» — для NFC и зарядки это неверно.
- `WRITE_EXTERNAL_STORAGE` объявлен, но не нужен: приложение пишет только в
  `getExternalFilesDir` и `cacheDir`. На API 29+ разрешение ничего не делает.
- `FOREGROUND_SERVICE_DATA_SYNC` объявлен, но ни один сервис его не использует.
- `BootCompletedReceiver` помечен `android:exported="false"` — работает на многих
  OEM-прошивках, но для системных broadcast-ов обычно ставят `true`.
- API-ключ Nothing закоммичен в манифест, а не вынесен в `BuildConfig` / gradle-свойства.

### Ресурсы

Не используются, но весят на диске: `phone1.png` (1.8 МБ), `phone1dark.png` (1.9 МБ),
`batterystory.png` (1.9 МБ), `resource__.xml`, `blur_on_24px.xml`, `graph_6_24px.xml`,
`activity_zone_24px.xml` — **≈5.6 МБ** мёртвого веса. Также не используются темы
`Theme.GlyphSharge.Dark` и `Theme.GlyphSharge.Transparent`, и оставшиеся
шаблонные цвета `purple_200/500/700`, `teal_200/700`.

---

## 14. Пользовательские анимации (Lua)

Помимо десяти встроенных анимаций пользователь может написать свои — на **Lua 5.2**,
которую выполняет чисто-JVM движок [LuaJ](https://github.com/luaj/luaj) 3.0.1
(`org.luaj:luaj-jse`). Это не плагин и не чужой код из интернета: скрипт целиком
хранится в `filesDir/glyph_scripts`, выполняется в песочнице и умеет только зажигать
каналы, ждать и читать батарею.

### Как попасть в студию

**Настройки → Custom Animations** (карточка «Custom Animations / Write your own in Lua»
показывает, сколько скриптов сохранено). Это отдельная Activity, а не маршрут настроек.

В списке у каждого скрипта есть меню: открыть, дублировать, удалить, «Save to Downloads»
и «Export to a file». Переименования среди них нет — имя редактируется в одноимённом поле
редактора, и это единственное место, где оно меняется. Сверху — Import и, **только когда
сохранён хотя бы один скрипт**, New: на пустой студии единственная осмысленная кнопка —
та, что в середине экрана, и вторая наверху только спорила бы с ней. Кнопка **New**
создаёт черновик из `R.string.studio_starter_script` — волны вдоль C-полосы, которая
работает на любом поддерживаемом телефоне.

### Редактор: две кнопки

| Кнопка | Что делает | Чего не доказывает |
|--------|-----------|--------------------|
| **Check** | Компилирует исходник, ничего не рисуя, и печатает первую ошибку синтаксиса | Семантику: `glyph.group('nope')` не проверится |
| **Glyph** | Тот же исходник идёт в `GlyphAnimationManager.previewScript` — по тому же пути, что и анимации сервисов | — |

Экранного режима запуска нет намеренно: схема раскладки светодиодов — не то же самое, что
загоревшийся телефон, а вторая кнопка «запустить» провоцирует путать одно с другим.
Проверьте синтаксис, а потом проиграйте на глифе — телефон без интерфейса глифов или
незакрытая сессия скажут об этом в консоли, а не будут выглядеть как скрипт, который
молча ничего не сделал.

Пока прогон идёт, вторая кнопка превращается в **Stop**. Единственный потолок прогона —
`ScriptAnimation.SAFETY_CAP_MS = 30_000L`, который ловит скрипт, никогда не
возвращающийся; обычные скрипты заканчиваются вместе со своим кодом. Под кнопками —
консоль (последние 4 строки), а под полем кода — сворачиваемая памятка `glyph API reference`.

> [!NOTE]
> Файл скрипта — это **его тело целиком**. Движок оборачивает исходник в
> `local function __glyph_main() … end` и сразу вызывает, поэтому `local`-объявления
> работают как обычно, `return` на верхнем уровне разрешён, а обязательной структуры
> у файла нет вообще. Доступна стандартная библиотека Lua: `math`, `string`, `table`,
> `pairs`/`ipairs`, `assert` и т. д.

### Справочник API `glyph`

Таблица `glyph` — это **весь** язык, доступный скрипту. Аргумент в квадратных скобках
необязателен; «каналы» — это `{ 1, 2, 3 }`, одиночный номер `3` или имя группы `"c"`.

#### Рисование

| Вызов | Значение |
|-------|----------|
| `glyph.set(каналы, яркость, [удержание])` | Зажечь каналы и оставить горящими. По умолчанию `яркость = glyph.MAX`, удержание 0 мс |
| `glyph.setAll([яркость], [удержание])` | То же для всех каналов телефона |
| `glyph.off([удержание])` | Погасить полосу, затем подождать |
| `glyph.hold(мс)` | Подождать, оставаясь прерываемым. Один вызов ограничен 60 000 мс |
| `glyph.pulse(каналы, вклМс, выклМс, [яркость])` | Зажечь → погасить всё → пауза. По умолчанию 200 / 100 / `MAX` |
| `glyph.blinkAll([вклМс], [выклМс], [яркость], [раз])` | Мигание всей полосой. По умолчанию 150 / 150 / `MAX` / 1 |

#### Движение

| Вызов | Значение |
|-------|----------|
| `glyph.sweep(каналы, [шагМс], [яркость], [обратно])` | Нарастающий проход: 1 канал, 2, 3… затем гашение. По умолчанию шаг 80 мс |
| `glyph.wave(каналы, [шагМс], [яркость], [хвост])` | Бегущая волна: за головой остаётся `хвост` сегментов с затуханием. По умолчанию шаг 80 мс, хвост 0 |
| `glyph.spiral([циклы], [шагМс], [яркость])` | Проход по порядку `glyph.ch.spiral` туда и обратно. По умолчанию 1 / 60 мс |
| `glyph.heartbeat([удары], [ударМс], [паузаМс], [яркость])` | «Луб-дуб». По умолчанию 3 / 150 / 120 мс |
| `glyph.batteryBar([процент], [мс])` | Заполнить C-полосу как при зарядке. По умолчанию процент берётся из `glyph.battery()`, длительность 2000 мс |

#### Каналы и устройство

| Вызов | Значение |
|-------|----------|
| `glyph.MAX` | `4000` — верхняя яркость канала. Всё выше обрезается, всё ниже 0 гасит |
| `glyph.ch.all` / `.a` / `.b` / `.c` / `.d` / `.e` | Группы каналов из `DeviceProfile` этого телефона |
| `glyph.ch.nonC` | Все каналы, кроме C-полосы |
| `glyph.ch.spiral` | Порядок спирального прохода |
| `glyph.ch.pulse` | Сегменты пульса |
| `glyph.group("имя")` | То же списком по имени: `"c"`, `"all"`, `"nonC"`… Неизвестное имя — ошибка |
| `glyph.device` | Тип устройства, например `PHONE3A` |

> [!TIP]
> Группы берутся из профиля устройства, поэтому один и тот же скрипт корректно работает
> и на Phone (1), и на Phone (3a) — номера каналов знать не нужно.

#### Состояние прогона

| Вызов | Значение |
|-------|----------|
| `glyph.time()` | Миллисекунд с начала прогона |
| `glyph.frame()` | Сколько кадров уже нарисовано |
| `glyph.battery()` | Уровень заряда, 0..100 |
| `glyph.charging()` | Заряжается ли телефон |
| `glyph.running()` | `false`, как только анимацию остановили. Основа цикла `while glyph.running() do` |

> [!WARNING]
> Все пять — **функции**, скобки обязательны: `while glyph.running do` без скобок всегда
> истинно (значение — функция, а функция в Lua истинна), и цикл будет убит сторожем по
> времени выполнения, а не по остановке анимации. `glyph.MAX` и `glyph.device` — единственные
> действительные значения (число и строка), скобок у них нет.
>
> Учтите это и при чтении исходников: комментарий в `GlyphLuaApi.kt` и памятка
> `API_REFERENCE` в редакторе пишут `glyph.running` и `glyph.battery` без скобок.
> По коду правильно — со скобками.

#### Случайность и математика

| Вызов | Значение |
|-------|----------|
| `glyph.rnd(a, b)` | Целое в диапазоне `a..b` (порядок не важен) |
| `glyph.rndFloat()` | Вещественное в `0..1` |
| `glyph.seed(n)` | Фиксирует генератор — узор становится воспроизводимым |
| `glyph.ease(t, kind)` | Кривая по `t` в `0..1`: `linear`, `in`, `out`, `inout`, `bounce`, `wave`, `pulse`. Неизвестное имя = `linear` |

#### Управление и вывод

| Вызов | Значение |
|-------|----------|
| `glyph.exit()` | Закончить успешно прямо сейчас — обычное завершение, а не убийство |
| `glyph.log(значения…)` | Строка в консоли студии |
| `print(значения…)` | То же самое; `print` перенаправлен в консоль, а не в stdout |
| `glyph.target = "music"` | **Объявление, а не настройка** — см. ниже |

#### В каком сервисе живёт скрипт

```lua
glyph.target = "music"
```

Скрипт без `glyph.target` доступен во всех пикерах — так было до появления
визуализации. Скрипт со строкой `"music"` появляется **только** в пикере
визуализации и исчезает из Pulse Lock, NFC, Low Battery и Screen Off — именно
то, что нужно скрипту, читающему `glyph.audio`, потому что в остальных сервисах
все значения равны нулю.

| Правило | Почему |
|---------|--------|
| Объявление должно быть **до первого `glyph.set()`** | После него скрипт уже работает в том сервисе, который его запустил; тихо переложить его в другой значило бы обещать то, что неправда |
| Неизвестное имя — это **ошибка**, а не тихий откат | Опечатка иначе убрала бы скрипт из всех пикеров без единого объяснения |
| В редакторе цель показана под полем названия | Считается из буфера, поэтому следует за каждым нажатием клавиши |

Как пикеры узнают цель до первого запуска: `ScriptTarget.detectIn(source)`
просматривает исходник той же регуляркой, что используется в рантайме. Перехват
в рантайме через `__newindex` — источник истины, он возвращается в
`ScriptRunResult.target`; сканирование существует только ради пикеров, которые
должны классифицировать скрипт, который ещё никто не запускал.

#### Аудио — вход визуализации

Ненулевые значения только пока `MusicVisualizerService` работает с живым
захватом. В студии и в любой другой фиче всё читается как `0` / `false`.

| Чтение | Значение |
|--------|----------|
| `glyph.audio.active` | `true`, пока захват кормит значения ниже |
| `glyph.audio.level` | Общая громкость, `0..1` |
| `glyph.audio.bass` | Энергия нижней трети, `0..1` — там живёт удар бочки |
| `glyph.audio.mid` | Энергия средней трети |
| `glyph.audio.treble` | Энергия верхней трети |
| `glyph.audio.beat` | `true` на единственном кадре, где распознан бит |
| `glyph.audio.bands(n)` | Таблица из `n` значений `0..1`, пересэмплированная из 32 полос |

> [!NOTE]
> Это **значения, а не функции** — `glyph.audio.bass`, а не
> `glyph.audio.bass()`. Функция в Lua всегда истинна, поэтому
> `while glyph.audio.bass do` не закончился бы никогда. `bands(n)` — именно
> функция, потому что у неё есть аргумент.

**Скрипт-визуализация:**

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

### Примеры

**Простая волна вдоль полосы** — это и есть стартовый шаблон:

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

**Продвинутый: полоса заряда, реагирующая на батарею, со сглаживанием и случайными
вспышками:**

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

### Скрипты на карточках фич

Сохранённый скрипт **не связан** с конкретной фичей. Он появляется в ряд выбора анимации
у Pulse Lock, Low Battery, NFC и Screen Off — сразу после десяти встроенных, с общей
иконкой и своим именем, а сохранённый id имеет вид `custom:<12 hex>`.

Единственное исключение — `glyph.target = "music"`, который ограничивает скрипт пикером
музыкальной визуализации. Правило одностороннее: триггерные фичи скрывают музыкальные
скрипты, а визуализация показывает и скрипты без цели, и музыкальные, — потому что
«без цели» значит «где угодно», а обратное неверно.

Отдельные анимации зарядки и Power Peek выбора не имеют и остались без скриптов:
у зарядки фиксированная полоса по своему дизайну.

Кнопка Test у каждой из четырёх фич смотрит на флаг `isCustom` и уходит в
`playCustomAnimation(id)` вместо встроенной ветки.

> [!IMPORTANT]
> Для скрипта настройка **Duration** значит не то же, что для встроенных анимаций.
> Именованные встроенные её игнорируют, а у скрипта длительности нет вовсе:
> `runCapMs()` отдаёт сторожу `ScriptAnimation.SAFETY_CAP_MS` для id вида `custom:`
> вместо слайдера фичи, и диалог фичи прячет управление длительностью, когда выбран
> скрипт, потому что управление, которое ничего не меняет, хуже его отсутствия.
> Именно поэтому скрипт корректно отрабатывает и при выключенном экране — он
> заканчивается, когда заканчивается его код, а не «когда-нибудь допишет цикл».

### Экспорт и импорт

| Действие | Куда | Как |
|----------|------|-----|
| Save to Downloads | Публичная папка «Загрузки» | `MediaStore.Downloads` с протоколом `IS_PENDING` (API 29+), **без разрешений** |
| Export to a file… | Любое место | Системный диалог `ActivityResultContracts.CreateDocument` |
| Import | Откуда угодно | `ActivityResultContracts.OpenDocument`; MIME-фильтр намеренно расширен до `*/*` |

При импорте имя файла используется как имя скрипта по умолчанию, а при совпадении
добавляется суффикс `(2)`. **Id всегда новый** — файл, который уже лежит на телефоне,
импортом не перезапишется.

### Формат файла `.glyphlua`

Обычный текстовый файл Lua с закомментированным заголовком. Никакого контейнера:
заголовок — комментарии, поэтому экспортированный файл запускается в любом
Lua 5.2-хосте без распаковки.

```lua
-- Glyph Sharge animation
-- format: 1
-- name: My Heartbeat
-- id: 4f3c9a11b2e7
-- created: 1712345678000
-- updated: 1712345678000

glyph.set(glyph.ch.c, glyph.MAX)
```

| Поле | Смысл |
|------|-------|
| `-- Glyph Sharge animation` | Маркер: первая строка файла |
| `format` | Версия формата, сейчас `1` |
| `name` | Отображаемое имя |
| `id` | Идентификатор; при импорте всё равно заменяется новым |
| `created` / `updated` | Метки времени в миллисекундах |

> [!NOTE]
> Заголовок — это **последовательность строк-комментариев в самом начале файла**;
> первая строка без `--` его закрывает. Файл без нашего заголовка тоже импортируется —
> он становится новой анимацией с именем по умолчанию, что как раз и ожидает человек,
> написавший скрипт в стороннем редакторе. Файл без тела Lua
> отклоняется с `InvalidScriptException`.
>
> Имя файла для экспорта — `suggestedFileName()`: пробелы и недопустимые символы
> заменяются на `_`, при пустом результате берётся `animation.glyphlua`.

### Безопасность: песочница и сторож

Скрипты приходят извне приложения, поэтому окружение строится **вычитанием** из
стандартных глобалов LuaJ (`JsePlatform.standardGlobals()`):

| Убрано | Зачем |
|--------|-------|
| `io`, `os` | Файловая система и управление процессом |
| `luajava` | **Главный** выход: мост рефлексии LuaJ в Java. Убран обязательно |
| `load`, `loadstring`, `dofile`, `loadfile`, `require`, `module` | Компиляция и загрузка кода во время выполнения |
| `package` | Загрузка модулей |
| `coroutine` | Состояние хука живёт в потоке — свежая корутина выполнилась бы без проверки |
| `collectgarbage` | Скрипт может надолго занять VM принудительной сборкой |
| `newproxy` | Тот же класс побега через userdata |
| `debug` | `debug.sethook()` снял бы сторож |

`print` перенаправлен в консоль студии. `DebugLib` при этом **загружается**: пока
`Globals.debuglib` не установлен, VM не спрашивает хук вообще. Таблица устанавливается,
сторож навешивается, и только затем `debug` обнуляется — скрипт не может её ни увидеть,
ни снять.

**Два предела сторожа**, оба проверяются из count-хука с интервалом 20 000 инструкций:

| Предел | Значение | От чего спасает |
|--------|----------|-----------------|
| Настенные часы | `ScriptAnimation.SAFETY_CAP_MS` (30 000 мс) | Скрипт, который идёт дольше, чем пользователь готов смотреть |
| Инструкции | 200 000 000 выполненных байткод-команд | Скрипт, который грузит CPU, но никогда не касается часов |

> [!WARNING]
> Count- хук — **единственный** механизм, прерывающий плотный цикл: события вызова,
> возврата и строки внутри него не возникают. Сторож бросает Java-класс
> `ScriptAbortedError` (наследник `Error`, а не `Exception`), поэтому `pcall` внутри
> скрипта его **не перехватит**. Это проверяется тестом
> `pcall cannot swallow the watchdog`.

**Прерываемое ожидание.** Любая пауза нарезается кусками по `CANCEL_POLL_MS = 16` мс,
после каждого проверяется флаг остановки. Поэтому «стоп» замечается за ~16 мс, а не в
конце длинного `glyph.hold`. Движок дополнительно гасит полосу в `finally` — после любого
исхода, включая ошибку, глифы не остаются горящими наполовину.

### Где что лежит

| Файл | Роль |
|------|------|
| `glyph/script/ScriptAnimation.kt` | Модель: имя, исходник, id `custom:<12 hex>`, `newId()`, `runtimeIdOf()`, `isCustomId()`, `stripPrefix()` |
| `glyph/script/ScriptFileFormat.kt` | Контейнер `.glyphlua`: `encode()`, `decode()`, `suggestedFileName()`, `InvalidScriptException` |
| `glyph/script/LuaScriptEngine.kt` | Сборка песочницы, сторож, `run()`, `validate()`, `ScriptStatus` |
| `glyph/script/ScriptSession.kt` | Состояние прогона: дедлайн, бюджет инструкций, счётчик кадров, генератор, прерываемые паузы |
| `glyph/script/GlyphLuaApi.kt` | Таблица `glyph` — весь пользовательский язык |
| `glyph/script/ScriptTarget.kt` | `ScriptTarget` (объявление) и `ScriptScope` (какой пикер что предлагает) |
| `glyph/script/ScriptRunner.kt` | `runScript()` / `stop()` / `check()` и приватный `RendererHost`, мостящий suspend-рендерер в блокирующий хост |
| `data/CustomAnimationRepository.kt` | Файлы, индекс, импорт, оба экспорта, стартовый скрипт |
| `CustomAnimationsActivity.kt` | Activity студии, файловые пикеры, `BackHandler` |
| `ui/screens/animations/*.kt` | Список и редактор |
| `ui/viewmodel/AnimationStudioViewModel.kt` | Состояние студии, Check / Glyph |
| `app/src/test/.../glyph/script/*Test.kt` | 21 + 7 + 15 юнит-тестов, запуск: `./gradlew :app:testDebugUnitTest` |
| `app/src/test/.../glyph/audio/*Test.kt` | 17 + 13 + 12 + 6 + 11 юнит-тестов на преобразование, кадр, детектор битов и калибровку |
| `app/src/test/.../glyph/device/DeviceProfileFactoryTest.kt` | 16 юнит-тестов на таблицы каналов по моделям |

---

*Документация описывает состояние кода на версии 1.0.31. При изменении сервисного слоя
обновите [раздел 7](#7-добавление-нового-сервиса-quickstart), при добавлении вызовов
в таблицу `glyph` — [раздел 14](#14-пользовательские-анимации-lua).*
