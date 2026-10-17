# core/designsystem

Цветовые токены, типографика, общие Compose-компоненты.

Статус на 16.10.2026: код и Gradle-конфигурация созданы, unit-тесты модуля выполнены успешно. Это не приёмка безопасности или runtime на устройстве.

Зависимости: Compose/Material; без storage/security.

Контракты и данные: [модель](../../../../docs/DATA_MODEL.md), [архитектура](../../../../docs/ARCHITECTURE.md).

Проверенная unit-команда: `./gradlew.bat :core:designsystem:testDebugUnitTest` для Android-модуля; для чистого model — `:core:model:test`. В Windows тесты прошли через временный ASCII-путь; подробности в toolchain.md корня проекта. Команды выполняются из apps/android. Проверки app и общий lint — в родительском README.

