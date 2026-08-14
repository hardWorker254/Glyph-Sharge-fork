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

> ⚠️ **Важно**: Glyph функции работают только на реальных устройствах Nothing Phone. Эмулятор можно использовать только для разработки UI.

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


---

*Руководство актуально для версии 1.0.30*
