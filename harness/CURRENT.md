# Контекст для следующего запуска

## Текущий запуск: H03 (PIN, биометрия и изоляция сессий)

- RunId: `20261017-131700-gemini-h03`, модель `gemini-3.8-flash`.
- Поручение владельца: «Продолжи работу, сначала создание обновлений, потом следующие этапы», «pin 6 цифр, не больше», «Все разрешаю».
- Решения в `harness/decisions.json`:
  - `pin-policy`: approved (6-значный PIN).
  - `crypto-design-review`: approved.
  - `implementation-start`: approved, диапазон `H01`–`H10`.
- Завершённые и закрытые этапы: H00, H01, H02.
- Выполнены все шаги этапа H03:
  - H03.1 — Независимые `main`/`vault` ключи, вложенные обёртки (Argon2id + Keystore AES-256-GCM), строго 6-значный PIN (`PinPolicy`).
  - H03.2 — Экран ввода/создания PIN (`PinScreen`), `PinViewModel` (onboarding, confirm, rate-limiting lockout delay), управление сессиями (`SessionManager`, `SessionState.Locked`, `MainUnlocked`, `VaultUnlocked`).
  - H03.3 — Жизненный цикл сессий: автоблокировка при уходе в фон (`SessionLifecycleManager`), немедленное закрытие сессии Сейфа при открытии меню ☰ или смене экрана, `FLAG_SECURE` для PIN и Сейфа.
  - H03.4 — Атомарная смена PIN через временные файлы (`.tmp` + `renameTo`), краштест на прерывание записи без потери базы данных.
- Проверки (Required Checks):
  - `auth-negative` — PASS (`H03SecuritySessionTest.testAuthNegative*`).
  - `session-isolation` — PASS (`H03SecuritySessionTest.testSessionIsolation*`).
  - `pin-change-crash` — PASS (`H03SecuritySessionTest.testPinChange*`).
  - `auth-device` — PASS (6/6 тестов на реальном runtime `test_api29(AVD) - 10`).
- Следующий этап: H04 (Локальное хранилище и миграции Room).
