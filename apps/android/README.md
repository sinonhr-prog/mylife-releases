# Android workspace

Статус на 16.10.2026: сборочные файлы, Gradle Wrapper 8.9 и исходный каркас созданы. JDK 17 установлен и проверен. SDK установлен, ANDROID_HOME задан; :app:assembleDebug создаёт debug APK. Все 7 unit-тестов проходят через ASCII-путь Windows (см. docs/toolchain.md в корне проекта). Instrumentation пока выполняет 0 тестов; runtime не проверен.

Модули: app, core:model, core:security, core:storage, core:designsystem. Границы — в ../../docs/ARCHITECTURE.md относительно корня проекта: фактически [архитектура](../../docs/ARCHITECTURE.md).

Команды из этого каталога (в Windows unit-тесты запускать через ASCII-путь, см. [toolchain](../../docs/toolchain.md)):

- ./gradlew.bat :app:assembleDebug
- ./gradlew.bat :app:lintDebug testDebugUnitTest
- ./gradlew.bat connectedDebugAndroidTest
- ./gradlew.bat ktlintCheck (после настройки плагина)

Проверены :app:assembleDebug, :core:model:test и testDebugUnitTest всех четырёх Android-модулей. connectedDebugAndroidTest возвращает exit 0 без реальных тестов; lint/format в текущем запуске не выполнялись. Запуск — установка debug APK на тестовое устройство через Android Studio. Данные только синтетические до security gate.

Файлы ключей подписи и local.properties не коммитить. Runtime не использует сеть; build-зависимости загружаются отдельно при подготовке окружения.


