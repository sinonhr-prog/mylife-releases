---
name: mylife-android-security
description: Apply the My Life security and recovery contract to Android keys, PIN, biometrics, storage, backups, reminders, and release. Use when a change crosses these boundaries; not as a substitute for specialist cryptographic review.
---

# Защита и восстановление

До изменения читать docs/SECURITY.md, DATA_MODEL.md, текущий криптографический ADR и gates карточки. Если ADR ещё не принят, production key envelopes не реализовывать по догадке.

- Никаких собственных криптопримитивов, plaintext fallback, PIN как raw DB key, открытых thumbnails/логов, секретов в SavedState/аргументах навигации.
- Main и vault имеют отдельные ключи/сессии. Открытие меню из сейфа закрывает vault session; dashboard/search никогда не читают vault.
- Биометрия привязана к CryptoObject. Проверить invalidation, process death, смену PIN с прерыванием и независимость от главной сессии на реальном Keystore.
- Системный backup и D2D проверяются отдельно. Ручной архив восстанавливается без старого Keystore, с новой локальной парой ключей/PIN.
- Restore: лимиты непроверенного ввода, валидация, encrypted staging, атомарное переключение, отмена/диск/сбой не меняют текущую БД. Все тесты на синтетических данных.
- Для напоминаний разрешена лишь минимальная фоновая проекция без текста. Не оставлять ключи main/vault в Worker ради удобства.
- Сетевых SDK/INTERNET в APK нет. Среда разработки модели может быть облачной: ей запрещены пользовательские секреты и личные данные.
- До реальных секретов необходимы отрицательные тесты, cross-device restore, API 29/Vivo и профильное ревью. Нельзя автоматически засчитать review другой моделью как заключение специалиста.
