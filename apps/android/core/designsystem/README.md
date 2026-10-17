# core/designsystem

Цветовые токены, типографика, общие Compose-компоненты.

Статус на 16.10.2026: код и Gradle-конфигурация созданы, unit-тесты модуля выполнены успешно. Это не приёмка безопасности или runtime на устройстве.

Зависимости: Compose/Material; без storage/security.

Контракты и данные: [модель](../../../../docs/DATA_MODEL.md), [архитектура](../../../../docs/ARCHITECTURE.md).

Проверенная unit-команда: `./gradlew.bat :core:designsystem:testDebugUnitTest` для Android-модуля; для чистого model — `:core:model:test`. В Windows тесты прошли через временный ASCII-путь; подробности в toolchain.md корня проекта. Команды выполняются из apps/android. Проверки app и общий lint — в родительском README.


H10: общие кнопки Focus имеют minHeight48dp. Приложение сохраняет белый/графитовый стиль, системную типографику, прокрутку больших форм и адаптивную высоту действий. Реальная проверка320dp/200% находится в app VaultLayoutTest; unit ThemeTest не заменяет UI acceptance.
