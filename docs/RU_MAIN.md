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

- Nothing Phone (1) — модель 20111
- Nothing Phone (2) — модель 22111
- Nothing Phone (2a) — модели 23111, 23113
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

4. Соберите и установите приложение:
```bash
./gradlew assembleDebug
```

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
│   │   │   ├── AppModule.kt       # Модули DI
│   │   │   └── GlyphComponent.kt  # Компоненты DI
│   │   │
│   │   ├── glyph/                 # Управление Glyph Interface
│   │   │   ├── GlyphManager.kt          # Основной менеджер глифов
│   │   │   ├── GlyphAnimationManager.kt # Менеджер анимаций
│   │   │   └── GlyphFeatureCoordinator.kt # Координатор функций
│   │   │
│   │   ├── services/              # Фоновые сервисы
│   │   │   ├── GlyphForegroundService.kt
│   │   │   ├── ChargingAnimationService.kt
│   │   │   ├── NfcGlyphService.kt
│   │   │   ├── LowBatteryAlertService.kt
│   │   │   ├── PowerPeekService.kt
│   │   │   ├── PulseLockService.kt
│   │   │   ├── QuietHoursService.kt
│   │   │   └── ScreenOffGlyphService.kt
│   │   │
│   │   ├── ui/                    # UI компоненты
│   │   │   ├── components/        # Переиспользуемые компоненты
│   │   │   ├── screens/           # Экраны приложения
│   │   │   ├── theme/             # Темы и стили
│   │   │   └── utils/             # UI утилиты
│   │   │
│   │   ├── data/                  # Слой данных
│   │   │   ├── SettingsRepository.kt
│   │   │   └── local/             # Локальное хранилище (Room)
│   │   │
│   │   ├── receiver/              # Broadcast receivers
│   │   │   └── BootCompletedReceiver.kt
│   │   │
│   │   ├── utils/                 # Общие утилиты
│   │   │   ├── LoggingManager.kt
│   │   │   └── WatermarkHelper.kt
│   │   │
│   │   ├── GlyphZenApplication.kt # Application класс
│   │   └── MainActivity.kt        # Главная активность
│   │
│   ├── res/                       # Ресурсы Android
│   │   ├── values/                # Строки, цвета, темы
│   │   └── ...
│   │
│   └── AndroidManifest.xml        # Манифест приложения
│
└── build.gradle                   # Конфигурация сборки
```

### Архитектурные паттерны

Приложение следует современным рекомендациям Android разработки:

- **MVVM (Model-View-ViewModel)** — разделение логики и UI
- **Dependency Injection** — Hilt для внедрения зависимостей
- **Repository Pattern** — абстракция доступа к данным
- **Single Activity Architecture** — навигация через Compose Navigation
- **Unidirectional Data Flow** — поток данных в одном направлении

---

## Основные компоненты

### Glyph Manager

**Файл:** `glyph/GlyphManager.kt`

Центральный класс для управления Nothing Glyph Interface. Предоставляет методы для:

- Инициализации и подключения к сервису глифов
- Открытия/закрытия сессии
- Управления отдельными каналами глифов
- Включения/выключения всех глифов
- Проверки состояния сессии
- Автоматического переподключения

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

Каждая модель Nothing Phone имеет свою карту каналов:

#### Phone (1) — 20111
```kotlin
object Phone1 {
    const val A1 = 0
    const val B1 = 1
    const val C1 = 2  // Through C4 = 5
    const val E1 = 6
    const val D1_1 = 7  // Through D1_8 = 14
}
```

#### Phone (2) — 22111
```kotlin
object Phone2 {
    const val A1 = 0
    const val A2 = 1
    const val B1 = 2
    const val C1_1 = 3  // Through C1_16 = 18
    const val C2 = 19   // Through C6 = 23
    const val E1 = 24
    const val D1_1 = 25 // Through D1_8 = 32
}
```

#### Phone (2a) — 23111/23113
```kotlin
object Phone2a {
    const val A = 25
    const val B = 24
    const val C1 = 0    // Through C24 = 23
}
```

#### Phone (3a) — 24111
```kotlin
object Phone3a {
    const val A1 = 20   // Through A11 = 30
    const val B1 = 31   // Through B5 = 35
    const val C1 = 0    // Through C20 = 19
}
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

#### WatermarkBox
Контейнер с диагональным повторяющимся водяным знаком:

```kotlin
WatermarkBox(
    enabled = BuildConfig.DEBUG,
    text = "PREVIEW",
    color = Color.Red,
    alpha = 0.15f,
    spacing = 64.dp
) {
    YourAppContent()
}
```

#### EmojiPainter
Утилита для использования эмодзи как Painter в Icon:

```kotlin
Icon(
    painter = rememberEmojiPainter("🔋", fontSizeDp = 32f),
    contentDescription = "Battery"
)
```

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

## Настройка и сборка

### Конфигурация Gradle

#### Версии зависимостей

```groovy
ext {
    compose_version = '1.5.1'
    kotlin_version = '1.9.10'
    material3_version = '1.2.0'
}
```

#### Основные зависимости

- **Android Gradle Plugin**: 8.13.2
- **Kotlin**: 1.9.10
- **Compose BOM**: 2024.02.00
- **Material 3**: 1.2.0
- **Hilt**: 2.50
- **KSP**: 1.9.10-1.0.13
- **Navigation Compose**: 2.7.7
- **Room**: 2.6.1
- **Lottie Compose**: 6.3.0

#### Nothing Glyph SDK

Приложение использует официальный Nothing Ketchum SDK:
```groovy
implementation files('libs/KetchumSDK_Community_20250319.jar')
```

### Команды сборки

```bash
# Сборка debug версии
./gradlew assembleDebug

# Сборка release версии
./gradlew assembleRelease

# Запуск тестов
./gradlew test

# Очистка проекта
./gradlew clean

# Сборка и установка на устройство
./gradlew installDebug
```

### Настройка ProGuard (для release)

В файле `proguard-rules.pro` необходимо добавить правила для Nothing SDK и Hilt.

---

## Технологический стек

### Языки программирования
- **Kotlin** 1.9.10 — основной язык разработки

### UI Framework
- **Jetpack Compose** — декларативный UI
- **Material Design 3** — дизайн-система
- **Compose Animation** — анимации
- **Canvas API** — кастомная графика

### Архитектура и DI
- **Hilt** 2.50 — dependency injection
- **KSP** — обработка аннотаций
- **Repository Pattern** — слой данных

### Навигация
- **Navigation Compose** 2.7.7 — навигация между экранами

### Хранение данных
- **Room** 2.6.1 — локальная база данных
- **DataStore** — настройки приложения

### Дополнительные библиотеки
- **Lottie Compose** 6.3.0 — анимации Lottie
- **Material Components** 1.11.0 — дополнительные компоненты
- **Robolectric** 4.11.1 — тестирование

### SDK
- **Nothing Ketchum SDK** — официальный SDK для Glyph Interface

---

## Лицензия

Это НЕ официальный репозиторий Nothing Technology Limited.

Приложение использует официальный Nothing API ключ для работы с Glyph Interface.

---

## Контакты и поддержка

- **Репозиторий**: https://github.com/hardWorker254/Glyph-Sharge-fork
- **Релизы**: https://github.com/hardWorker254/Glyph-Sharge-fork/releases

---

## Вклад в проект

Для внесения изменений:

1. Создайте форк репозитория
2. Создайте ветку для вашей функции (`git checkout -b feature/amazing-feature`)
3. Закоммитьте изменения (`git commit -m 'Add amazing feature'`)
4. Отправьте в удалённый репозиторий (`git push origin feature/amazing-feature`)
5. Откройте Pull Request

---

*Документация актуальна для версии 1.0.30*
