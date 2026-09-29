# Glyph Sharge Documentation

Полная документация для проекта Glyph Sharge — приложения для управления Nothing Glyph Interface.

## 📑 Оглавление

1. [Обзор проекта](#обзор-проекта)
2. [Быстрый старт](#быстрый-старт)
3. [Архитектура приложения](#архитектура-приложения)
4. [Основные компоненты](#основные-компоненты)
5. [UI компоненты](#ui-компоненты)
6. [Сервисы](#сервисы)
7. [Настройка и сборка](#настройка-и-сборка)
8. [Технологический стек](#технологический-стек)

---

## Обзор проекта

**Glyph Sharge** — это приложение для Android, которое предоставляет полный контроль над Nothing Glyph Interface. Приложение позволяет управлять световыми индикаторами (глифами) на телефонах Nothing для различных целей: индикация заряда, уведомления, безопасность и персонализация.

### Ключевые возможности

- 🔌 **Управление питанием** — мониторинг батареи и анимации зарядки
- 🔒 **Функции безопасности** — NFC глифы, блокировка пульсом
- 💡 **Персонализация** — настройка паттернов света и тем
- 🎨 **Material You дизайн** — современный интерфейс с динамическими темами
- ⚙️ **Глубокая интеграция** — использование официального Nothing API

### Поддерживаемые устройства

- Nothing Phone (1) — модель 20111 ( Нет тестировщиков, не гарантировано)
- Nothing Phone (2) — модель 22111 ( Нет тестировщиков, не гарантировано)
- Nothing Phone (2a) — модели 23111, 23113 ( Нет тестировщиков, не гарантировано)
- Nothing Phone (3a) — модель 24111

---

## Быстрый старт

### Требования

- **Android Studio** Hedgehog (2023.1.1) или новее
- **JDK** версии 17 или выше
- **Android SDK** API 34+ (Android 14+)
- **Nothing Phone** с поддержкой Glyph Interface

### Установка

1. Клонируйте репозиторий:
```bash
git clone https://github.com/hardWorker254/Glyph-Sharge-fork.git
cd Glyph-Sharge-fork
```

2. Откройте проект в Android Studio

3. Синхронизируйте Gradle зависимости

### Важное примечание

> ⚠️ **Приложение использует официальный Nothing API ключ**, поэтому режим отладки не требуется для работы функций глифов.

---

## Архитектура приложения

### Структура проекта

```
app/
├── src/main/
│   ├── java/com/bleelblep/glyphsharge/
│   │   ├── di/                    # Dependency Injection (Hilt)
│   │   │   └── AppModule.kt       # Провайдер @GlyphPrefs — единственный SharedPreferences
│   │   │
│   │   ├── glyph/                 # Управление Glyph Interface
│   │   │   ├── GlyphManager.kt          # Сессия Nothing SDK
│   │   │   ├── GlyphAnimationManager.kt # Точки входа анимаций
│   │   │   ├── GlyphFeatureCoordinator.kt # Единственный арбитр полосы LED
│   │   │   ├── AnimationCatalog.kt     # id анимаций
│   │   │   ├── RunTrace.kt
│   │   │   ├── device/                 # Раскладка LED по модели
│   │   │   ├── engine/                 # Движок отрисовки кадров
│   │   │   ├── animations/             # Встроенные анимации + общий раннер
│   │   │   │   ├── AnimationRunner.kt   #   Проверки и гашение полосы
│   │   │   │   ├── BuiltInAnimations.kt
│   │   │   │   └── ...
│   │   │   ├── audio/                   # Визуализатор: захват, FFT, синтетический трек
│   │   │   ├── script/                 # Пользовательские анимации на Lua (LuaJ)
│   │   │   └── battery/                # Анимация заряда и Power Peek
│   │   │
│   │   ├── services/              # Фоновые сервисы
│   │   │   ├── FeatureSpec.kt         # Реестр фича → сервис → настройка
│   │   │   ├── FeatureServiceController.kt
│   │   │   ├── GlyphForegroundService.kt
│   │   │   ├── ChargingAnimationService.kt
│   │   │   ├── NfcGlyphService.kt
│   │   │   ├── LowBatteryAlertService.kt
│   │   │   ├── PowerPeekService.kt
│   │   │   ├── PulseLockService.kt
│   │   │   ├── QuietHoursService.kt
│   │   │   ├── ScreenOffGlyphService.kt
│   │   │   └── MusicVisualizerService.kt
│   │   │
│   │   ├── ui/                    # UI компоненты
│   │   │   ├── components/        # Переиспользуемые компоненты
│   │   │   │   ├── FeatureCards.kt      # По карточке на каждый GlyphFeature
│   │   │   │   ├── GlyphAnimations.kt   # Каталог анимаций
│   │   │   │   ├── GlyphDependencies.kt # rememberGlyphAnimationManager()
│   │   │   │   ├── cards/, controls/, dialogs/, layout/
│   │   │   ├── screens/           # Экраны приложения
│   │   │   ├── navigation/        # Маршруты и NavHost
│   │   │   ├── state/             # FeatureUiState, HomeUiState
│   │   │   ├── viewmodel/         # ViewModel'ы экранов
│   │   │   ├── theme/             # Темы и стили
│   │   │   └── utils/             # UI утилиты (HapticUtils)
│   │   │
│   │   ├── data/                  # Настройки: срезы + тонкий фасад
│   │   │   ├── ThemeSettings.kt, FontSettings.kt, GlyphServiceSettings.kt
│   │   │   ├── FeatureSettings.kt, QuietHoursSettings.kt, LanguageSettings.kt
│   │   │   ├── UserPresenceSettings.kt
│   │   │   ├── SettingsMigrations.kt, SettingsDiagnostics.kt
│   │   │   ├── SettingsPrefs.kt   # Типизированные reified-хелперы чтения/записи
│   │   │   ├── SettingsRepository.kt   # Фасад, делегирующий срезам
│   │   │   └── CustomAnimationRepository.kt # Скрипты пользователя: файлы + индекс
│   │   │
│   │   ├── receiver/              # Broadcast receivers
│   │   │   └── BootCompletedReceiver.kt
│   │   │
│   │   ├── utils/                 # Общие утилиты
│   │   │   └── LoggingManager.kt
│   │   │
│   │   ├── GlyphZenApplication.kt # Application класс
│   │   ├── MainActivity.kt        # Главная активность
│   │   └── CustomAnimationsActivity.kt # Студия анимаций (отдельная активность)
│   │
│   ├── res/                       # Ресурсы Android
│   │   ├── values/                # Строки, цвета, темы
│   │   └── ...
│   │
│   └── AndroidManifest.xml        # Манифест приложения
│
└── build.gradle.kts                 # Конфигурация сборки
```

### Архитектурные паттерны

Приложение следует современным рекомендациям Android разработки:

- **MVVM (Model-View-ViewModel)** — разделение логики и UI
- **Dependency Injection** — Hilt; `di/AppModule.kt` провайдит единственный `SharedPreferences` настроек под квалификатором `@GlyphPrefs`, UI получает остальной граф через `@HiltViewModel` и `hiltViewModel()`
- **Repository Pattern** — `data/` хранит настройки в `SharedPreferences`, разбитом на срезы по областям (`ThemeSettings`, `FontSettings`, `FeatureSettings`, …); `SettingsRepository` — тонкий фасад, делегирующий срезам
- **Single Activity Architecture** — навигация через Compose Navigation (плюс отдельная активность студии анимаций со своим стеком и файловыми диалогами)
- **Unidirectional Data Flow** — поток данных в одном направлении
- **Одна полоса — один владелец** — `GlyphFeatureCoordinator` держит `Mutex` за единственным enum `GlyphFeature`; сервисы берут полосу через `withStrip(...)`, а прогон ограничивают по времени через `GlyphAnimationManager.runCapped(...)`

---

## Основные компоненты

### Glyph Manager

**Файл:** `glyph/GlyphManager.kt`

Центральный класс для управления Nothing Glyph Interface. Отвечает **только** за сессию SDK.
Предоставляет методы для:

- Инициализации и подключения к сервису глифов
- Открытия/закрытия сессии
- Определения модели устройства
- Включения/выключения всех глифов
- Проверки состояния сессии
- Автоматического переподключения

Номера каналов он не хранит — они живут в `glyph/device/DeviceProfileFactory.kt`.

#### Пример использования

```kotlin
@Inject lateinit var glyphManager: GlyphManager

// Инициализация
glyphManager.initialize()

// Открытие сессии
glyphManager.openSession()

// Включение всех глифов на максимум
glyphManager.turnOnAllGlyphs()

// Выключение всех глифов
glyphManager.turnOffAll()

// Закрытие сессии
glyphManager.closeSession()
```

### Каналы глифов по устройствам

Карта каналов каждой модели описана в `glyph/device/DeviceProfileFactory.kt` — по одной
функции на модель (`phone1()`, `phone2()`, `phone2a()`, `phone3a()`). Определение самой
модели живёт в `glyph/device/DeviceType.kt`.

| Модель | A | B | C | C2 | D | E |
|--------|---|---|---|---|---|---|
| Phone (1) — 20111 | 0 | 1 | 2–5 | — | 7–14 | 6 |
| Phone (2) — 22111 | 0–1 | 2 | 3–18 | 19–23 | 25–32 | 24 |
| Phone (2a) — 23111/23113 | 25 | 24 | 0–23 | — | — | — |
| Phone (3a) — 24111 | 20–30 | 31–35 | 0–19 | — | — | — |

```kotlin
// Получить профиль подключённого устройства
val profile = DeviceProfileFactory.forConnectedDevice()
profile?.all      // все каналы в порядке физической разводки
profile?.c        // центральная C-полоса — основа для анимаций
```

---

## Сервисы

Приложение использует несколько фоновых сервисов для реализации функций:

### GlyphForegroundService
Основной сервис для поддержания сессии глифов в фоне. Обеспечивает постоянную доступность Glyph Manager.

### ChargingAnimationService
Сервис для отображения анимаций во время зарядки устройства. Показывает прогресс зарядки через глифы.

### NfcGlyphService
Сервис для работы с NFC-метками и активации глифов при сканировании.

### LowBatteryAlertService
Сервис мониторинга батареи и оповещения о низком уровне заряда через глифы.

### PowerPeekService
Сервис для функции "Power Peek" — показ уровня заряда при встряхивании телефона.

### PulseLockService
Сервис для функции блокировки пульсом — проверка ритма сердца для разблокировки.

### ScreenOffGlyphService
Сервис для активации глифов при выключенном экране (уведомления, будильники).

### QuietHoursService
Сервис для режима "Тихие часы" — отключение уведомлений в заданное время.

---

## UI компоненты

### Тема и стилизация

Приложение использует **Material Design 3** с полностью кастомизируемой системой тем.

#### Доступные темы

```kotlin
enum class AppThemeStyle {
    CLASSIC,      // Классический Material 3
    Y2K,          // Хром, кибер, футуристичный
    NEON,         // Высококонтрастные электрические цвета
    AMOLED,       // Истинно чёрный с минимализмом
    PASTEL,       // Мягкие, пастельные тона
    EXPRESSIVE    // Яркий, смелый Material 3 Expressive
}
```

#### Система шрифтов

Поддержка официальных шрифтов Nothing:
- **NType Headline** — для заголовков
- **NDot 57 Caps** — для акцентов
- **System** — системный шрифт по умолчанию

Масштабирование размеров шрифта по категориям:
- Display (57sp, 45sp, 36sp)
- Headline (32sp, 28sp, 24sp)
- Title (22sp, 16sp, 14sp)
- Body (16sp, 14sp, 12sp)
- Label (14sp, 12sp, 11sp)

### Карточки (Cards)

#### StandardCard
Базовый компонент карточки с полной функциональностью:
- Анимированное нажатие (scale 0.98f)
- Тактильная отдача
- Тематические стили
- Гибкие слоты контента

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

#### SimpleCard
Минималистичная версия карточки.

#### IconCard
Карточка с акцентом на иконку.

#### ActionCard
Карточка с призывом к действию.

#### ContentCard
Карточка с произвольным содержимым.

### Специализированные компоненты

#### WavyProgressIndicator
Продвинутый индикатор прогресса с синусоидальными волнами:

```kotlin
// Неопределённый прогресс
LinearWavyProgressIndicator(
    amplitude = 0.8f,
    wavelength = 32.dp,
    waveSpeed = 20.dp,
    modifier = Modifier.fillMaxWidth()
)

// Определённый прогресс (60%)
LinearWavyProgressIndicator(
    progress = 0.6f,
    amplitude = 1.0f,
    wavelength = 24.dp,
    modifier = Modifier.fillMaxWidth()
)
```

#### TransparentTopAppBar
Панель приложения с прозрачностью при скролле:
- Прозрачная в верхней позиции
- Становится сплошной при прокрутке
- Плавные цветовые переходы

### Экраны приложения

#### SettingsScreen
Главный экран настроек со всеми основными опциями.

#### ThemeSettingsScreen
Экран выбора и настройки темы оформления.

#### FontSettingsScreen
Экран настройки шрифтов и размеров текста.

#### LanguageSettingsScreen
Экран выбора языка приложения.

#### QuietHoursSettingsScreen
Экран настройки режима "Тихие часы".

---

## Лицензия

Это НЕ официальный репозиторий Nothing Technology Limited.

Приложение использует официальный Nothing API ключ для работы с Glyph Interface.

---

## Контакты и поддержка

- **Репозиторий**: https://github.com/hardWorker254/Glyph-Sharge-fork
- **Релизы**: https://github.com/hardWorker254/Glyph-Sharge-fork/releases

---

*Документация актуальна для версии 1.0.31*
