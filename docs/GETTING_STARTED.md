# Быстрый старт для разработчиков

Это руководство поможет вам начать разработку в проекте Glyph Sharge.

## Предварительные требования

### Обязательные
- **Android Studio** Hedgehog (2023.1.1) или новее
- **JDK 17** или выше
- **Android SDK** с API 34+ (Android 14+)
- **Nothing Phone** с поддержкой Glyph Interface (для тестирования)

### Рекомендуемые
- Плагин Kotlin для Android Studio
- Git для контроля версий
- ADB (Android Debug Bridge)

## Настройка окружения

### 1. Клонирование репозитория

```bash
git clone https://github.com/hardWorker254/Glyph-Sharge-fork.git
cd Glyph-Sharge-fork
```

### 2. Открытие проекта

1. Запустите Android Studio
2. Выберите **File → Open**
3. Укажите путь к папке проекта
4. Дождитесь завершения синхронизации Gradle

### 3. Проверка зависимостей

Убедитесь, что все зависимости загрузились:
- Nothing Ketchum SDK (`libs/KetchumSDK_Community_20250319.jar`)
- Hilt для DI
- Jetpack Compose библиотеки
- Room для базы данных

Если есть ошибки, выполните:
```bash
./gradlew clean
./gradlew build --refresh-dependencies
```

## Сборка проекта

### Debug сборка

```bash
# Из командной строки
./gradlew assembleDebug

# APK будет создан в:
# app/build/outputs/apk/debug/app-debug.apk
```

### Release сборка

```bash
./gradlew assembleRelease

# APK будет создан в:
# app/build/outputs/apk/release/app-release.apk
```

### Установка на устройство

```bash
# Сборка и установка
./gradlew installDebug

# Только установка существующего APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Запуск и отладка

### На эмуляторе

> ⚠️ **Важно**: Glyph функции работают только на реальных устройствах Nothing Phone. Эмулятор можно использовать только для разработки UI.

1. Создайте эмулятор с API 34+
2. Нажмите **Run** в Android Studio
3. Выберите эмулятор из списка

### На реальном устройстве

1. Включите **Режим разработчика** на Nothing Phone
2. Включите **Отладку по USB**
3. Подключите устройство к компьютеру
4. Разрешите отладку на устройстве
5. Нажмите **Run** в Android Studio

## Структура кода

### Основные пакеты

```
com.bleelblep.glyphsharge/
├── di/                    # Dependency Injection
├── glyph/                 # Логика управления глифами
├── services/              # Фоновые сервисы
├── ui/                    # Пользовательский интерфейс
│   ├── components/        # Переиспользуемые компоненты
│   ├── screens/           # Экраны приложения
│   └── theme/             # Темы и стили
├── data/                  # Слой данных
└── utils/                 # Утилиты
```

### Где что находится

| Задача | Расположение |
|--------|-------------|
| Добавить новый экран | `ui/screens/` |
| Создать UI компонент | `ui/components/` |
| Изменить тему | `ui/theme/` |
| Работа с глифами | `glyph/` |
| Фоновый сервис | `services/` |
| Хранение данных | `data/` |
| DI конфигурация | `di/` |

## Внесение изменений

### Добавление нового экрана

1. Создайте файл в `ui/screens/`:
```kotlin
@Composable
fun NewFeatureScreen(
    viewModel: NewFeatureViewModel = hiltViewModel(),
    navController: NavController
) {
    // Ваш код
}
```

2. Добавьте маршрут в навигацию (`MainActivity.kt`):
```kotlin
composable("new_feature") { NewFeatureScreen() }
```

3. Добавьте кнопку перехода в меню настроек

### Создание нового сервиса

1. Создайте класс в `services/`:
```kotlin
@Singleton
class NewFeatureService @Inject constructor(
    @ApplicationContext private val context: Context
) : Service() {
    // Реализация сервиса
}
```

2. Зарегистрируйте в `AndroidManifest.xml`

3. Добавьте методы управления в `GlyphManager`

### Добавление темы

1. Откройте `ui/theme/ThemeColors.kt`
2. Добавьте цветовую схему:
```kotlin
val CustomColorScheme = lightColorScheme(
    primary = Color(...),
    secondary = Color(...),
    // ...
)
```

3. Добавьте стиль в `AppThemeStyle`:
```kotlin
enum class AppThemeStyle {
    // ...
    CUSTOM
}
```

### Работа с Glyph Manager

Пример использования:

```kotlin
@Inject lateinit var glyphManager: GlyphManager

// Инициализация (обычно в Application)
glyphManager.initialize()

// Открытие сессии
glyphManager.openSession()

// Управление глифами
glyphManager.turnOnAllGlyphs()
glyphManager.turnOffAll()

// Закрытие сессии
glyphManager.closeSession()
```

## Тестирование

### Unit тесты

```bash
# Запуск всех тестов
./gradlew test

# Запуск конкретного теста
./gradlew test --tests "com.bleelblep.glyphsharge.ExampleUnitTest"
```

Расположение тестов: `app/src/test/java/`

### Instrumentation тесты

```bash
# Требует подключенного устройства/эмулятора
./gradlew connectedAndroidTest
```

Расположение тестов: `app/src/androidTest/java/`

### Ручное тестирование Glyph функций

> Требуется Nothing Phone!

1. Установите debug сборку
2. Откройте приложение
3. Проверьте каждую функцию:
   - Зарядка (Charging Animation)
   - NFC (NfcGlyphService)
   - Power Peek (встряхивание)
   - Quiet Hours
   - И т.д.

## Отладка

### Логи приложения

```bash
# Все логи
adb logcat

# Только логи приложения
adb logcat -s "GlyphSharge"

# Фильтрация по тегу
adb logcat -s "GlyphManager"
```

### Отладка UI

1. Включите **Show Layout Bounds** в настройках разработчика
2. Используйте **Layout Inspector** в Android Studio
3. Проверяйте рекомпозицию с помощью **Compose Compiler Metrics**

### Отладка Glyph SDK

```kotlin
// В LoggingManager.kt добавляются логи SDK операций
LoggingManager.logSDKOperation("OPERATION_NAME", "Details")
```

Смотрите логи:
```bash
adb logcat -s "GlyphManager" "GlyphSDK"
```

## Распространённые проблемы

### Ошибка: "SDK not found"

**Решение:** Убедитесь, что JAR файл SDK находится в `app/libs/`:
```bash
ls app/libs/KetchumSDK_Community_*.jar
```

### Ошибка компиляции Kotlin

**Решение:** Очистите и пересоберите:
```bash
./gradlew clean
./gradlew build
```

### Приложение вылетает при запуске

**Решение:**
1. Проверьте логи: `adb logcat`
2. Убедитесь, что Nothing Phone подключен
3. Проверьте разрешения в `AndroidManifest.xml`

### Glyph функции не работают

**Решение:**
1. Убедитесь, что это Nothing Phone
2. Проверьте, что сессия открыта: `glyphManager.isSessionActive`
3. Перезапустите сервис: `glyphManager.forceReconnect()`

## Полезные команды

```bash
# Очистка проекта
./gradlew clean

# Сборка без тестов
./gradlew assembleDebug -x test

# Анализ зависимостей
./gradlew dependencies

# Поиск дубликатов классов
./gradlew detectDuplicates

# Format кода
./gradlew ktlintFormat

# Проверка кода
./gradlew ktlintCheck
```

## Ресурсы для разработки

### Документация
- [Android Developers](https://developer.android.com/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material Design 3](https://m3.material.io/)
- [Hilt](https://dagger.dev/hilt/)
- [Nothing Glyph Developer Kit](https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit)

### Инструменты
- [ADB Commands](https://developer.android.com/tools/adb)
- [Android Studio Profiler](https://developer.android.com/studio/profile)
- [Compose Compiler Metrics](https://developer.android.com/jetpack/compose/metrics)

## Следующие шаги

1. Изучите архитектуру проекта ([ARCHITECTURE.md](ARCHITECTURE.md))
2. Посмотрите примеры кода в `ui/components/`
3. Попробуйте внести небольшое изменение
4. Запустите тесты
5. Отправьте Pull Request!

---

*Руководство актуально для версии 1.0.30*
