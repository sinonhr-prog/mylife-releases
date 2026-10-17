# Спецификация сборочного окружения (Toolchain)

Проверено: 16.10.2026.
Статус: зафиксировано для этапа H01 (шаг H01.1).

Документ фиксирует проверенную совместимую комбинацию инструментов для нативного Android-приложения «Моя жизнь» в соответствии с [ARCHITECTURE.md](ARCHITECTURE.md), [SECURITY.md](SECURITY.md), [BUILD.md](../harness/BUILD.md) и [SOURCES.md](SOURCES.md).

---

## 1. Базовые платформенные параметры

| Параметр | Значение | Обоснование / Первичный источник |
|---|---|---|
| **minSdk** | `29` (Android 10) | Требование проекта: поддержка устройств от 2020 года (включая Vivo X200 Pro mini и телефоны на Android 10+). Аппаратный Keystore, BiometricPrompt, Scoped Storage. |
| **compileSdk** | `35` (Android 15) | Актуальная версия платформы Android для сборки с поддержкой современных API и 16 KB page sizes. |
| **targetSdk** | `35` (Android 15) | Требования к поведению системы и разрешениям (включая POST_NOTIFICATIONS на Android 13+). |
| **JDK (Java)** | OpenJDK 17 LTS (или 21 LTS) | Базовый целевой уровень байткода: Java 17 (`JavaVersion.VERSION_17`). Требуется для AGP 8.7+ и Gradle 8.9+. |
| **Целевые ABI** | `arm64-v8a`, `armeabi-v7a`, `x86_64` | `arm64-v8a` — основной для современных устройств (Vivo X200 Pro mini); `x86_64` — для эмулятора разработки. |
| **Выравнивание страниц памяти** | 16 KB (`16 KB page sizes`) | Обязательная поддержка 16 KB страниц памяти для нативных библиотек (SQLCipher) на Android 15+. |

---

## 2. Сборочные инструменты и плагины

| Инструмент / Плагин | Идентификатор | Версия | Обоснование совместимости |
|---|---|---|---|
| **Gradle** | Wrapper (bin distribution) | `8.9` | Полная совместимость с AGP 8.7.0, Kotlin 2.0.21 и JDK 17/21. Официальный дистрибутив с проверкой контрольной суммы. |
| **Android Gradle Plugin (AGP)** | `com.android.application` / `com.android.library` | `8.7.0` | Стабильный релиз AGP для SDK 35, поддержка Kotlin 2.0, встроенный Android Lint. |
| **Kotlin** | `org.jetbrains.kotlin.android` / `jvm` | `2.0.21` | Архитектура Kotlin 2.0 K2 компилятора, стабильная генерация кода. |
| **Compose Compiler Plugin** | `org.jetbrains.kotlin.plugin.compose` | `2.0.21` | Встроенный компилятор Compose, поставляемый напрямую в составе Kotlin 2.0.21. |
| **KSP (Kotlin Symbol Processing)** | `com.google.devtools.ksp` | `2.0.21-1.0.28` | Для кодогенерации Room 2.6.1 под Kotlin 2.0.21. |
| **ktlint Gradle Plugin** | `org.jlleitschuh.gradle.ktlint` | `12.1.1` | Статический анализ форматирования кода (движок ktlint 1.3.1). |

### Контрольная сумма дистрибутива Gradle Wrapper

- **Distribution URL:** `https://services.gradle.org/distributions/gradle-8.9-bin.zip`
- **SHA-256:** `d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab`
- **Источник проверки:** `https://services.gradle.org/distributions/gradle-8.9-bin.zip.sha256`

---

## 3. Зависимости и библиотеки времени выполнения

Все зависимости объявляются через Version Catalog (`gradle/libs.versions.toml`) с фиксацией версий без динамических диапазонов `+`.

### UI и Compose (каталог `libs.versions.toml`)

| Библиотека | Координаты | Версия | Назначение |
|---|---|---|---|
| **AndroidX Compose BOM** | `androidx.compose:compose-bom` | `2024.10.00` | Согласованный набор библиотек Jetpack Compose (UI 1.7.4, Foundation 1.7.4, Material 3 1.3.0). |
| **Material 3** | `androidx.compose.material3:material3` | (из BOM) | Реализация дизайна «Бумага / Фокус» с кастомными токенами темы. |
| **Compose UI** | `androidx.compose.ui:ui` | (из BOM) | Базовый рендеринг Compose. |
| **Compose UI Tooling Preview** | `androidx.compose.ui:ui-tooling-preview` | (из BOM) | Локальный превью-рендеринг в IDE без сетевых вызовов. |
| **Activity Compose** | `androidx.activity:activity-compose` | `1.9.3` | Интеграция `ComponentActivity` с Compose. |
| **Navigation Compose** | `androidx.navigation:navigation-compose` | `2.8.3` | Локальная навигация по экранам приложения. |
| **Lifecycle ViewModel Compose** | `androidx.lifecycle:lifecycle-viewmodel-compose` | `2.8.6` | Управление состоянием экрана и ViewModels. |
| **Lifecycle Runtime Compose** | `androidx.lifecycle:lifecycle-runtime-compose` | `2.8.6` | Подписка на Flow с учётом жизненного цикла UI. |

### База данных и локальное хранилище

| Библиотека | Координаты | Версия | Назначение |
|---|---|---|---|
| **Room Runtime** | `androidx.room:room-runtime` | `2.6.1` | Локальное хранилище данных (main.db и vault.db). |
| **Room KTX** | `androidx.room:room-ktx` | `2.6.1` | Поддержка Kotlin Coroutines и Flow в DAO. |
| **Room Compiler** | `androidx.room:room-compiler` | `2.6.1` | Генерация реализаций DAO через KSP. |
| **SQLCipher for Android** | `net.zetetic:sqlcipher-android` | `4.6.1` | Полнодисковое шифрование баз данных SQLite с поддержкой 16 KB page sizes. |
| **AndroidX SQLite** | `androidx.sqlite:sqlite` | `2.4.0` | Интерфейс открывателя SQLite для интеграции SQLCipher с Room. |

### Криптография, безопасность и сериализация

| Библиотека | Координаты | Версия | Назначение |
|---|---|---|---|
| **AndroidX Biometric** | `androidx.biometric:biometric` | `1.1.0` | Авторизация криптографических операций через BiometricPrompt с CryptoObject. |
| **AndroidX Security Crypto** | `androidx.security:security-crypto` | `1.1.0-alpha06` | Утилиты для работы с Android Keystore (MasterKeys / EncryptedFile) при необходимости. |
| **Стандартные JCA / JCE** | `javax.crypto.*`, `java.security.*` | Платформа | AES-256-GCM, HMAC-SHA256, SecureRandom, Keystore provider. |
| **Kotlinx Serialization JSON** | `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.7.3` | Версионированная сериализация структуры зашифрованного архива резервной копии. |
| **Kotlinx Coroutines** | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | `1.9.0` | Асинхронные операции в фоновых потоках Dispatchers.IO / Default. |

### Тестирование и качество

| Библиотека | Координаты | Версия | Назначение |
|---|---|---|---|
| **JUnit 4** | `junit:junit` | `4.13.2` | Базовый фреймворк модульного тестирования. |
| **AndroidX Test Core** | `androidx.test:core` | `1.6.1` | Среда для инструментальных тестов. |
| **AndroidX Test Runner** | `androidx.test:runner` | `1.6.1` | Запуск AndroidJUnit4 тестов. |
| **AndroidX Test Ext JUnit** | `androidx.test.ext:junit` | `1.2.1` | JUnit4 расширения для Android. |
| **Espresso Core** | `androidx.test.espresso:espresso-core` | `3.6.1` | UI-автоматизация тестов взаимодействия. |
| **Compose UI Test JUnit4** | `androidx.compose.ui:ui-test-junit4` | (из BOM) | Проверка Compose-компонентов и навигации. |

---

## 4. Инварианты безопасности и ограничение сети

1. **Запрет INTERNET:** Приложение не декларирует разрешение `android.permission.INTERNET` в манифесте. Сетевые стеки и сокеты отсутствуют.
2. **Запрет телеметрии:** Никаких Firebase, Crashlytics, Google Analytics, Sentry или внешних SDK сбора данных.
3. **Запрет внешних шрифтов / изображений:** Шрифты только системные; изображения только локальные зашифрованные вложения.
4. **Запрет облачного бэкапа:** `android:allowBackup="false"`, `android:fullBackupContent="false"`, `android:dataExtractionRules` явно запрещают облачное копирование и автоматический трансфер.

---

## 5. Инструкции по подготовке среды разработчика

Для сборки проекта на локальной машине разработчика требуются:

1. **JDK 17 LTS:**
   ```powershell
   winget install EclipseAdoptium.Temurin.17.JDK
   # Или Microsoft.OpenJDK.17
   ```
2. **Android Command Line Tools / SDK:**
   - Переменная окружения `ANDROID_HOME` (например, `%LOCALAPPDATA%\Android\Sdk`).
   - Пакеты SDK: `platforms;android-35`, `platforms;android-29`, `build-tools;35.0.0`, `platform-tools`.
3. **Устройство или эмулятор:**
   - Физическое устройство Vivo X200 Pro mini (с включённой USB-отладкой) или эмулятор Android 10 (API 29).

---

## 6. Текущий статус хоста (ENV-001)

На момент фиксации H01.1 на текущей машине в PATH отсутствуют бинарные файлы `java`, `javac` и `adb`. Версии toolchain зафиксированы нормативно на основе официальных таблиц совместимости Google и Gradle для воспроизводимой генерации проекта в H01.2–H01.4.

## Фактическая установка SDK — 16.10.2026

- Официальные Command-line Tools 22.0: commandlinetools-win-15859902_latest.zip; SHA-256 90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a, проверен до распаковки. Источник: https://developer.android.com/studio#command-line-tools-only .
- Android SDK Platforms: API 29 revision 5 и API 35 revision 2; Build Tools 34.0.0 и 35.0.0; Platform Tools 37.0.1. Установка sdkmanager завершилась exit 0.
- ANDROID_HOME задан на уровне пользователя, SDK в стандартном локальном каталоге Android/Sdk. Абсолютный путь не записывается в versioned конфигурацию. Открытые терминалы нужно перезапустить или обновить переменные процесса.
- Gradle: android.useAndroidX=true; heap 2 ГБ, workers 2. AGP 8.7.0 предупреждает о SDK XML v4, сборка проходит; предупреждение остаётся ENV-007.
- Для unit-тестов в Windows/JDK 17 требуется ASCII-путь: исходная кириллица вызвала ClassNotFoundException. В проверке использован временный subst P: на корень проекта без переноса файлов; после проверки alias снимается. Для повторения проверить, что P: свободен, создать alias, запустить Wrapper из P:/apps/android с JAVA_HOME/ANDROID_HOME, затем снять alias через subst P: /D.
- :app:assembleDebug успешно создал debug APK. 7 unit-тестов прошли. Это не подтверждает runtime на API 29/Vivo и не завершает H01; connectedDebugAndroidTest выполнил 0 реальных тестов.
