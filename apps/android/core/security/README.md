# core/security

PIN, биометрия, key envelopes, сессии и криптографические адаптеры.

Статус на 16.10.2026: код и Gradle-конфигурация созданы, unit-тесты модуля выполнены успешно. Это не приёмка безопасности или runtime на устройстве.

Зависимости: core:model; Keystore и проверенные криптобиблиотеки.

Контракты и данные: [модель](../../../../docs/DATA_MODEL.md), [архитектура](../../../../docs/ARCHITECTURE.md).

Проверенная unit-команда: `./gradlew.bat :core:security:testDebugUnitTest` для Android-модуля; для чистого model — `:core:model:test`. В Windows тесты прошли через временный ASCII-путь; подробности в toolchain.md корня проекта. Команды выполняются из apps/android. Проверки app и общий lint — в родительском README.


H10 fixes: StreamingAeadHelper предоставляет стандартные InputStream/OutputStream без раскрытия Tink type потребителю. KeyEnvelopeManager сохраняет совместимые v1 envelopes; обе обёртки PIN/restore обновляются через RecoverableFileBatch с recovery до открытия БД. PersistentLockoutTest и RecoverableFileBatchTest проверяют программные сбои; power-loss/OEM и профильное review остаются отдельными gates. Подробности: [ADR0004](../../../../docs/adr/0004-streaming-archive-recovery.md).
