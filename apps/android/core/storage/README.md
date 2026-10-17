# core/storage

Две БД, DAO, реализации репозиториев, вложения, миграции и архивы.

Статус на 16.10.2026: код и Gradle-конфигурация созданы, unit-тесты модуля выполнены успешно. Это не приёмка безопасности или runtime на устройстве.

Зависимости: core:model и core:security.

Контракты и данные: [модель](../../../../docs/DATA_MODEL.md), [архитектура](../../../../docs/ARCHITECTURE.md).

Проверенная unit-команда: `./gradlew.bat :core:storage:testDebugUnitTest` для Android-модуля; для чистого model — `:core:model:test`. В Windows тесты прошли через временный ASCII-путь; подробности в toolchain.md корня проекта. Команды выполняются из apps/android. Проверки app и общий lint — в родительском README.


H10 fixes: LifeVaultArchiveService совместим с v1, использует потоковый AEAD/ZIP без plaintext staging. Limiting reader применяется до записи; keys/manifest ограничены в памяти. Export требует закрытые DB/WAL; restore включает данные и envelopes в один recoverable batch. Тесты добавлены для промежуточного cache и late failure. SQLCipher factory получает копию session buffer. См. [ADR0004](../../../../docs/adr/0004-streaming-archive-recovery.md); это не замена fresh-device/device fault acceptance.
