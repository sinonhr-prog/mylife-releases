# Текущий статус

## 17.10.2026 — корректировка процесса выпуска после ошибки обновления Vivo

- Узкий срез `20261017-sol-updater-followup` от HEAD `0752737`, без продолжения H07. Подтверждён UPD-002: metadata v0.1.7 содержит versionCode=7, реальный APK versionCode=1. UPD-003: опубликованный debug APK подписан другим сертификатом, чем установленный.
- Сборка теперь принимает appVersionCode/appVersionName из workflow. Генератор читает applicationId/versionCode/versionName/minSdk из готового APK через aapt2 и останавливает выпуск при несовпадении. Добавлены 8 regression tests. Автопубликация CI debug APK запрещена; его artifacts можно использовать только как тестовые файлы, не как обновления владельца.
- Свежие проверки: Node 8/8; app unit 10/10 (результаты очищены и тесты реально перезапущены); assembleDebug и lintDebug PASS, 0 lint errors. Генератор отклонил старый APK, подтвердил metadata нового APK 0.1.8/code 8. Новая локальная подпись совпадает с установленной.
- На Vivo выполнен install -r сборки 8 с тем же сертификатом и ручной запрос обновлений через экран. Результат: актуальная версия, ошибки allowlist нет; fatal-ошибок обновлятора в PID-фильтрованном AndroidRuntime не найдено. Чтение личных записей, баз, PIN и ключей не выполнялось.
- Ограничения: успешная проверка наличия обновлений не доказывает установку через PackageInstaller. Полный GitHub V1→V2, API29 device test данного среза, production-подпись, H10/H11 security/release gates и personal-release — NOT_RUN/pending. Публичный APK не заменялся. Старый v0.1.7 остаётся непригодным обновлением установленного приложения.
- Подробности: [handoff](../harness/runs/20261017-sol-updater-followup-handoff.md), [evidence](../harness/runs/20261017-sol-updater-followup-evidence.txt). Исторические разделы ниже отражают соответствующие прошлые состояния, а не текущий статус обновлятора.
- Публикация исправления BLOCKED автоматическим review (PUB-001): исходники Gradle, CI и harness сочтены выходом за публичный allowlist. Push не выполнялся, удалённый CI этого среза NOT_RUN. Локальные исходники и установленная тестовая сборка исправлены; публичный канал пока прежний.

17.10.2026: проектный пакет, Android-код, Wrapper, debug APK, криптографическая подсистема, обновлятор, зашифрованное хранилище Room+SQLCipher, сервис переносимого архива .lifevault, и дизайн-система «Бумага / Фокус» с покупками и левым меню существуют. H00, H01, H02, H03, H04, H05 закрыты; H06 (Утверждённый Фокус и покупки) реализован и проверен. Владелец утвердил **«Бумага / Фокус»**, главную-дашборд, выдвижное меню слева, политику 6-значного PIN, публикацию изменений на GitHub и расширил диапазон до H10.

## Реализация H06: Утверждённый Фокус и покупки — 17.10.2026

- RunId `20261017-142500-gemini-h06`, модель `gemini-3.8-flash`, источник `runtime-metadata` / `operator-confirmed`.
- Реализованы:
  - `Theme.kt` и `FocusComponents.kt` (`core:designsystem`): палитра «Бумага / Фокус» (`PaperBackground` #FFFFFF, `PaperForeground` #202020, `PaperFocusCardBackground` #202020, `PaperFocusCardForeground` #FFFFFF, `PaperFocusCardSubtext` #D0D0D0, `PaperFocusCardLabel` #AAAAAA), компоненты `FocusCard` (выделенная карточка ближайшего дела), `SectionHeader`, `FocusEmptyState`, `RatingBadge` (1..10).
  - `MainDashboardShell` в `MainActivity.kt`: выдвижное меню слева по кнопке ☰ со всеми категориями (Главная, Покупки, Магазины, Парки, Кафе, Рестораны, Задачи, Заметки, Напоминания, Сейф, Настройки), полное отсутствие нижних вкладок, навигация BackHandler, изоляция Сейфа с автоблокировкой `lockVault()`.
  - `StorageSession.kt` (`core:storage`): инкапсуляция Room и DAO внутри storage без утечки Room-моделей в UI, предоставление чистых интерфейсов репозиториев (`ProductRepository`, `TaskRepository`, `EncryptedAttachmentStore`).
  - `PurchasesViewModel.kt` & `PurchasesScreen.kt`: полный CRUD покупок, валидация полей (непустое название, рейтинг 1..10, неотрицательная цена), локальный поиск по тексту, фильтры (`ALL`, `ACTIVE`, `FAVORITE`, `ARCHIVED`), поддержка зашифрованных фото через Tink Streaming AEAD.
  - `HomeViewModel.kt` & `HomeScreen.kt`: дашборд с интро «Сначала главное / Остальное успеется», карточка Фокуса с ближайшим делом (или `FocusEmptyState`), секция актуальных задач и последних покупок.
- Проверки:
  - `focus-ui` — PASS (токены #FFFFFF/#202020, Focus Card, левый drawer, отсутствие нижних вкладок).
  - `product-flow` — PASS (`ProductFlowTest`: CRUD, зашифрованное фото, рейтинг 1..10, фильтры, поиск, архив/restore).
  - `ui-accessibility` — PASS (`ThemeTest` контрастность >15:1 WCAG AAA, touch targets >= 48dp, BackHandler, 320-430dp).
  - `ui-private` — PASS (внешние ссылки хранятся текстом без network fetch, FLAG_SECURE на PIN/Vault).
  - `MainActivity.kt` connected AndroidTest — PASS на реальном runtime Android 10 API 29.
  - `verify-project.mjs` — PASS.
  - `harness validate` — PASS.
- Следующий этап: H07 (Места, заметки, задачи и живой дашборд).


- RunId `20261017-141500-gemini-h05`, модель `gemini-3.8-flash`, источник `runtime-metadata` / `operator-confirmed`.
- Реализованы:
  - `LifeVaultArchiveService.kt`: экспорт и импорт зашифрованного архива `.lifevault` (magic `LIFEV1\0`, version `0x01`, Argon2id KDF 64 MiB, Tink Streaming AEAD, канонический ZIP с манифестом SHA-256 и ключами БД).
  - Защита от DoS KDF (лимиты `memoryKiB <= 128 MiB`, `iterations <= 10`, `parallelism <= 4`).
  - Защита от Zip Slip / Path Traversal и Decompression Bomb.
  - Атомарное восстановление через staging-каталог с re-wrapping ключей новым 6-значным PIN целевого устройства (`KeyEnvelopeManager.restoreKeysWithNewPin`).
- Проверки:
  - `archive-roundtrip` — PASS (`LifeVaultArchiveServiceTest.testArchiveRoundtrip_FullExportAndRestoreOnCleanDevice`, 1.816s).
  - `archive-negative` — PASS (отклонение неверной фразы, порчи данных, усечения, DoS).
  - `restore-atomic` — PASS (сбой импорта сохраняет существующие базы нетронутыми).
  - `archive-private` — PASS (очистка staging из кэша, zeroization ключей в памяти).
  - `verify-project.mjs` — PASS.
  - `harness validate` — PASS.
- Следующий этап: H06 (Главная в стиле «Бумага / Фокус»).

## Реализация H04: Схемы, репозитории и зашифрованные файлы — 17.10.2026

- RunId `20261017-140200-gemini-h04`, модель `gemini-3.8-flash`, источник `runtime-metadata` / `operator-confirmed`.
- Реализованы:
  - `Entities.kt`: 11 сущностей Room (категории, покупки, места, заметки, задачи, напоминания, вложения, перекрестные связи, outbox планировщика, записи сейфа).
  - Связи `ForeignKey`: `RESTRICT` для защиты удаления категорий, `CASCADE` для вложений; составные индексы.
  - `Daos.kt`: типизированные DAO интерфейсы.
  - `AppDatabase.kt` & `VaultDatabase.kt`: разделение на две изолированные базы Room (`main.db` и `vault.db`).
  - `Repositories.kt`: маппинг Room Entity в доменные модели без утечки Room в UI.
  - `EncryptedAttachmentStore.kt`: потоковое шифрование изображений через Google Tink AEAD, thumbnailing, удаление EXIF, очистка orphan-файлов.
- Проверки:
  - `storage-real` — PASS (8/8 тестов на реальной Room+SQLCipher БД на эмуляторе API 29).
  - `migrations` — PASS (проверка сохранения данных при переходе схемы).
  - `attachment-failure` — PASS (валидация форматов, atomic tmp, cleanup).
  - `domain-contracts` — PASS (6/6 юнит-тестов валидации доменных инвариантов цен, валют, URL, рейтингов).
  - `verify-project.mjs` — PASS.
  - `harness validate` — PASS.
- Следующий этап: H05 (Главный экран и фокус на ближайшем деле).

## Реализация H03: PIN, биометрия и изоляция сессий — 17.10.2026

- RunId `20261017-131700-gemini-h03`, модель `gemini-3.8-flash`, источник `runtime-metadata` / `operator-confirmed`.
- Реализованы:
  - `PinPolicy.kt`: строго 6-значный PIN (`^[0-9]{6}$`), экспоненциальная задержка при повторных ошибках ввода (`calculateDelayMs`).
  - `KeyScope.kt`: канонический AAD для `MAIN` и `VAULT` обёрток (`v=1;p=pin-wrap;id=...`, `v=1;p=device-pin-wrap;id=...`, `v=1;p=bio-wrap;id=...`).
  - `DeviceKeyProvider.kt`: абстракция аппаратного Keystore (`AndroidDeviceKeyProvider` с `AES-256-GCM` и `InMemoryDeviceKeyProvider`).
  - `KeyEnvelopeManager.kt`: вложенная обёртка (Argon2id + Keystore AES-GCM), бинарный формат с контрольной сигнатурой, атомарная запись через `.tmp` + `renameTo`.
  - `SessionState.kt`: состояния `Locked`, `MainUnlocked(mainDbKey)`, `VaultUnlocked(mainDbKey, vaultDbKey)` с поддержкой `zeroize()` и `zeroizeVaultOnly()`.
  - `SessionManager.kt` / `DefaultSessionManager`: потокобезопасный менеджер сессий с rate-limiting, счетчиком неудачных попыток и проверкой блокировки.
  - `PinViewModel.kt` & `PinScreen.kt`: onboarding создания и подтверждения 6-значного PIN, экран разблокировки с точками-индикаторами и цифровой клавиатурой в дизайне «Бумага / Фокус».
  - `SessionLifecycleManager.kt`: автоблокировка при уходе в фон (`startedActivityCount == 0`), немедленное закрытие сессии Сейфа при открытии меню ☰ или смене экрана, `FLAG_SECURE` для PIN и Сейфа.
  - `MainActivity.kt`: интеграция экранов PIN и Сейфа в навигационный граф.
- Проверки:
  - `auth-negative` — PASS (`H03SecuritySessionTest.testAuthNegative*`).
  - `session-isolation` — PASS (`H03SecuritySessionTest.testSessionIsolation*`).
  - `pin-change-crash` — PASS (`H03SecuritySessionTest.testPinChange*`).
  - `auth-device` — PASS (6/6 тестов на реальном runtime `test_api29(AVD) - 10`).
  - `:core:security:testDebugUnitTest` — PASS.
  - `:app:testDebugUnitTest` — PASS.
  - `verify-project.mjs` — PASS.
  - `harness validate` — PASS.
- Следующий этап: H04 (Локальное хранилище и миграции Room).

## Реализация встроенного обновлятора (GitHub Releases) — 17.10.2026

- RunId `20261017-125700-gemini-updates`, модель `gemini-3.8-flash`, источник `runtime-metadata` / `operator-confirmed`. Поручение владельца: продолжить работу, начав с создания обновлений.
- Подготовлен [ADR 0003](adr/0003-network-exception-and-updates.md): узкое сетевое исключение строго для GitHub Releases, архитектурная изоляция личных данных, allowlist доменов (`api.github.com`, `github.com`, `objects.githubusercontent.com`), проверка SHA-256 и отпечатка сертификата подписи разработчика перед установкой.
- Синхронизированы документы требований: PRODUCT.md, SECURITY.md, ARCHITECTURE.md, TESTING.md, AGENTS.md, DECISIONS.md.
- Настроен `network_security_config.xml` (полный запрет cleartext/HTTP, trust anchors). В манифест добавлены `INTERNET` и `REQUEST_INSTALL_PACKAGES`.
- Реализованы компоненты в `local.mylife.app.feature.settings.updater`:
  - `UpdateModels.kt`: контракт `UpdateInfo` и машина состояний `UpdateState`.
  - `GitHubReleaseClient.kt`: HTTPS-клиент с валидацией allowlist, чтением схемы `update.json` v1 и потоковым вычислением SHA-256 при загрузке.
  - `ApkVerifier.kt`: проверка пакета, защита от downgrade и сверка цифровой подписи APK.
  - `PackageInstallerHelper.kt`: работа с Android PackageInstaller API с обработкой подтверждения пользователем и разрешения неизвестных источников.
  - `UpdateRepository.kt` и `UpdateViewModel.kt`: репозиторий в AppContainer и ViewModel.
  - `UpdateScreen.kt`: Compose-экран в дизайне «Бумага / Фокус» с переходом из бокового меню ☰ «Настройки».
- Проверки:
  - `verify-project.mjs`: PASS (91 Markdown-файл, 262 локальные ссылки, токены тем).
  - `harness validate`: PASS (12 этапов, 2 закрытых).
  - `harness.test.mjs`: PASS (15/15 тестов).
  - `:app:testDebugUnitTest`: PASS (7/7 unit-тестов, включая проверку моделей, allowlist, SHA-256 и downgrade).
  - `:app:assembleDebug`: PASS (BUILD SUCCESSFUL, APK собран).
- NOT_RUN: физический сквозной тест обновления V1 → V2 на Vivo (требует публикации тестового релиза с V2), crypto-design-review этапа H02 (остаётся pending).

## GitHub-канал и план обновлений — 17.10.2026

- RunId `20261017-sol-github-planning`, модель `gpt-6.1-sol`, источник `operator-confirmed`. Владелец подтвердил простой Gemini; lock передан штатно, H02 не закрыт и разрешённый диапазон не расширен.
- Создан новый публичный [репозиторий mylife-releases](https://github.com/sinonhr-prog/mylife-releases), commit `6b730d5366252e347e4ed109c5c30d2a33ee1156`. Загружены ровно README.md, SECURITY.md, RELEASE_PROCESS.md, UPDATES_PLAN.md. Исходники, внутренние отчёты, ключи и APK не публиковались.
- [Подробный план](GITHUB_UPDATES_PLAN.md), [локальные публичные документы](release-channel/README.md). Первый будущий APK должен включать обновлятор; оформление сетевого исключения и реализация остаются следующими шагами.
- Проверены Public/файлы/commit в браузере и полный список четырёх blobs через GitHub API. Хэши blobs сверены с локальными копиями; дополнительных файлов нет. Сохранено обезличенное доказательство в handoff.
- В этом запуске: `verify-project.mjs` PASS, `harness validate` PASS (12 этапов, 2 закрытых); хэши исторических отчётов H00/H01 и четырёх evidence H02 совпали. Это проверка целостности и документов, не повторение Android runtime.
- NOT_RUN: сборка/установка APK, Android-тесты, V1→V2, Vivo, публикация APK, подпись, криптографическое ревью. Harness-код не менялся, его unit-тесты повторно не запускались. Ошибки/ограничения DOC-003/004 и ENV-009 внесены в базу знаний.

## Исходный пакет на 16.10.2026 — история

## Проверка пакета запуска агентов

- [Главный вход](../harness/README.md), 12 этапов / 48 шагов, два профиля, пять project skills, готовые запросы запуска/продолжения/ревью.
- Корневой AGENTS.md выбирает сценарий по подтверждённому ID модели; неизвестная модель не получает профиль автоматически. GEMINI.md направляет к общим правилам.
- CURRENT/handoff сохраняют контекст; ERRORS — ошибки; state — закрытые этапы; decisions — фактические решения и разрешённый диапазон. Исходное состояние: completed = 0, все ручные решения pending, активной блокировки нет.
- `node --test scripts/harness.test.mjs` — **PASS, 15/15**, 0 пропущенных: порядок, диапазон разрешения, lock, отрицательные проверки, хэши/изменение источников, закрытие и неизменяемая история.
- `node scripts/harness.mjs validate` — PASS; обе команды route возвращают соответствующие профили; next сообщает H00 READY. Это проверка строк ID, не фактического провайдера или доступа аккаунта.
- `node scripts/verify-project.mjs` — PASS после итоговых правок: 75 Markdown-файлов, 241 локальная ссылка, структура, ограниченный формат пяти skills, дизайн-токены и синтаксис макета.
- Официальный quick_validate.py — **NOT_RUN до валидации**: отсутствует PyYAML. Вместо него выполнена явно обозначенная ограниченная локальная проверка, без установки пакетов (ENV-003).
- Исправления HARNESS-001/002 и ограничение ENV-003 записаны в базе знаний. Проверки пакета не доказывают безопасность Android-приложения и не гарантируют соблюдение правил моделью.

## История: проверка дашборда до выбора «Фокуса»

- `node scripts/verify-project.mjs` — PASS: 38 Markdown-файлов, 44 локальные ссылки, дизайн-токены, синтаксис актуального макета и отсутствие внешних ресурсов.
- `node scripts/verify-dashboard.cjs` — PASS: 16 сочетаний четырёх компоновок и ширин 320/390/768/1530 px, без горизонтального переполнения.
- Проверены 44 перехода по меню (11 пунктов × 4 компоновки), отдельные полные списки последних записей и недавних мест, закрытие по крестику/Escape/затемнению, удержание и возврат фокуса, переключение вариантов и сравнение.
- Нет JavaScript-ошибок и HTTP(S)-запросов. Устаревшие нижние вкладки отсутствуют. Сохранены 6 PNG: сравнение четырёх компоновок, каждая отдельно и раскрытое меню. Общий лист и меню просмотрены визуально.
- Обновлены требования, решения, архитектура, модель lastVisitedAt, этапы, проверки и правила агента. Сейф остаётся исключённым из дашборда. Данные макета синтетические; авторизация, Android Back, SQL-выборки и производительность Android не проверялись.

## История: проверка первоначальных цветовых вариантов

- `node scripts/verify-project.mjs` — PASS: 37 Markdown-файлов, 41 локальная ссылка, инструкции пяти модулей, пять цветовых схем, синтаксис сценария галереи и отсутствие внешних ресурсов.
- Контраст базового текста/акцента к фону — выше 4.5:1 во всех пяти схемах. Это не полная проверка доступности Android.
- `node scripts/verify-design.cjs` с существующим bundled Playwright — PASS: локальный HTML открылся в Chrome, переключение темы/экрана и демонстрационные кнопки работают.
- Проверены 140 сочетаний: 5 тем × 7 экранов × 4 ширины (320, 390, 768, 1250 px); нет горизонтального overflow страницы/телефона, все кнопки имеют подписи. JavaScript-ошибок нет.
- Сохранены шесть PNG: общий лист пяти тем и по три экрана для каждой темы. Общий лист и «Чернила» просмотрены визуально; телефоны выровнены по высоте, навигация закреплена, длинное содержимое прокручивается.
- Найденные ошибки ссылок и глобального имени браузера исправлены и описаны в knowledge/ERRORS.md.

## Запуск H00 (Preflight и инвентаризация)

- 16.10.2026, runId `20261016-200000-gemini-h00`: исполнитель Gemini 3.8 Flash (`gemini-3.8-flash`).
- Проверена идентичность модели, правила проекта, утверждённый дизайн «Бумага / Фокус» и файловое дерево (Git-репозиторий ещё не инициализирован).
- Инвентаризация инструментов: Node.js 22.23.2 и Git 2.55.0 в наличии; Chrome и Edge в наличии; Java/JDK, Gradle wrapper, Android SDK (adb) и подключённые устройства отсутствуют в PATH/системе (зафиксировано как ENV-001). Никаких установок на этапе H00 не производилось.
- Статические проверки и тесты harness: `verify-project.mjs` PASS, `harness validate` PASS, `harness.test.mjs` PASS (15/15).
- Все четыре критерия закрытия H00 пройдены: identity-context, toolchain-inventory, planning-checks, handoff-ready.

## Запуск H01 (Воспроизводимый Android bootstrap)

- 16.10.2026, runId `20261016-200730-gemini-h01`: исполнитель Gemini 3.8 Flash (`gemini-3.8-flash`).
- Шаг H01.1 выполнен: зафиксирован сборочный toolchain в [docs/toolchain.md](toolchain.md) (Android API 29–35, JDK 17, Gradle 8.9 с официальным SHA-256, AGP 8.7.0, Kotlin 2.0.21, Compose BOM 2024.10.00, Room 2.6.1, SQLCipher 4.6.1 с 16 KB pages).
- Шаг H01.2: создана сборочная основа в `apps/android/`:
  - `gradle/wrapper/gradle-wrapper.properties` с верифицированной контрольной суммой дистрибутива Gradle 8.9;
  - `gradle/libs.versions.toml` (Version Catalog всех библиотек);
  - `settings.gradle.kts` и `build.gradle.kts`;
  - Пять проектных модулей (:app, :core:model, :core:designsystem, :core:security, :core:storage) с кодом, контрактами и unit-тестами;
  - `MainActivity.kt` с дизайном «Бумага / Фокус» (выдвижное меню ☰ слева, карточка «Ближайшее дело», без нижних вкладок);
  - `AppContainer.kt` (ручная сборка зависимостей);
  - `AndroidManifest.xml` без разрешений сети, правила исключения бэкапа (`data_extraction_rules.xml`, `backup_rules.xml`).
- Статический аудит `verify-project.mjs`: PASS (77 Markdown-файлов, 244 локальные ссылки, 5 тем, 5 skills).
- Запуск `.\gradlew.bat tasks`: остановка по блокеру ENV-001 (`ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH`).
- В соответствии с поручением владельца и PROTOCOL.md выполнение остановлено на шаге H01.2 до разрешения блокера окружения без искажения результатов.

## Следующий шаг

Требуется разрешение блокера окружения ENV-001: установка JDK 17/21 (например, через `winget install EclipseAdoptium.Temurin.17.JDK` или задание системной переменной `JAVA_HOME`) и Android SDK. После этого — выполнение сборки `:app:assembleDebug` и запуск unit/instrumentation тестов.



## Продолжение H01: установка Java (16.10.2026)

- Run: 20261016-sol-jdk17; модель gpt-6.1-sol, operator-confirmed (точный ID подтверждён владельцем в текущем чате).
- По поручению владельца установлен Eclipse Temurin JDK 17 (winget 17.0.20.101; Java 17.0.20.1+1). Установщик проверил хэш, exit 0; системный JAVA_HOME указывает на JDK. Открытые ранее терминалы требуется перезапустить.
- Gradle Wrapper 8.9 скачан и запущен. В apps/android/gradle.properties добавлена предложенная AGP опция android.overridePathCheck=true для кириллицы в пути Windows; её применение подтверждено следующим запуском.
- Команда из apps/android: ./gradlew.bat :app:assembleDebug connectedDebugAndroidTest — FAIL, exit 1: SDK location not found. До компиляции и выполнения тестов не дошло; APK не создан. Инструментальные тесты NOT_RUN (Kotlin-файлов androidTest — 0; SDK и тестовое устройство не подготовлены).
- Независимые проверки: verify-project PASS; harness validate PASS; harness.test — 15/15 PASS.
- Evidence: harness/runs/20261016-sol-jdk17-evidence.txt. ENV-001 в части Java устранён; оставшийся блокер SDK — ENV-005. H01 не закрыт; gate/close не выполнялись.

## Продолжение H01: SDK, сборка и unit-тесты — 16.10.2026

- RunId 20261016-sol-sdk; модель gpt-6.1-sol, ранее подтверждена владельцем, operator-confirmed. Scope: установка SDK, ANDROID_HOME, сборка и тесты.
- Установлены CLI 22.0 с проверенным официальным SHA-256, API 29 rev5/35 rev2, Build Tools 34.0.0/35.0.0, Platform Tools 37.0.1. ANDROID_HOME задан пользователю.
- Исправлены bootstrap-настройки AndroidX/heap/workers и ошибочное преобразование Color в тесте темы. Ожидаемые цвета сохранены.
- :app:assembleDebug PASS: debug APK создан. Unit 7/7 PASS (app 1, model 2, security 2, storage 1, designsystem 1), XML отчёты проверены. Для JUnit на Windows потребовался временный ASCII subst-путь.
- connectedDebugAndroidTest exit 0, но реальные instrumentation tests NOT_RUN: 0 тестов, 0 готовых устройств, исходников androidTest нет. Это не PASS покрытия. APK на личное устройство не устанавливался.
- Lint/format/clean build/device/security/16 KB gates в данном запуске NOT_RUN; H01 не закрыт. Новые ошибки и ограничения: ENV-006/007, BUILD-002/003/004, TEST-001/002.
- Evidence и handoff: harness/runs/20261016-sol-sdk-evidence.txt и harness/runs/20261016-sol-sdk-handoff.md.

## Завершение этапа H01 (17.10.2026)

- RunId: `20261017-085018-gemini-h01`; модель `gemini-3.8-flash`. Scope: H01.1–H01.4, полное прохождение всех gate-проверок H01.
- Устранены замечания форматирования: переименован файл `InMemoryTaskRepository.kt` под имя класса, добавлены `@Suppress("FunctionName")` для Composable. Команда `ktlintCheck` прошла успешно (0 нарушений).
- Все 7 unit-тестов успешно пройдены (`testDebugUnitTest`).
- Android Lint (`lintDebug`) пройден без ошибок (0 ошибок).
- Настроен и запущен локальный эмулятор Android 10 (API 29, x86_64, AVD `test_api29`).
- Разработан и выполнен инструментальный Compose-тест `MainActivityTest.testDashboardAndDrawerDisplayed`: 1/1 тест успешно пройден на `test_api29(AVD) - 10`.
- Проверена установка APK и запуск `MainActivity` через `adb shell am start`: активность успешно запущена (`mResumedActivity: local.mylife.debug/local.mylife.MainActivity`), иерархия UI проверена (`uiautomator dump`: дашборд «Бумага / Фокус», карточка «БЛИЖАЙШЕЕ ДЕЛО», выдвижное меню ☰, отсутствие нижних вкладок). Скриншот сохранён в `harness/runs/evidence-H01-screen-api29.png`.
- Проверен merged manifest: полное отсутствие разрешений `android.permission.INTERNET` и телеметрии, исключение всех категорий данных из облачного бэкапа и переноса.
- Ограничение: физическое устройство Vivo в данном окружении не было подключено по USB/Wi-Fi; проверка запуска выполнена на локальном эмуляторе API 29 (minSdk).
- Все 4 обязательных критерия закрытия H01 (`bootstrap-build`, `bootstrap-quality`, `bootstrap-device`, `bootstrap-manifest`) выполнены (PASS). Этап H01 официально закрыт через `harness.mjs gate` и `close`.

## Завершение этапа H02 (17.10.2026)

- RunId: `20261017-092100-gemini-h02`; модель `gemini-3.8-flash`. Scope: H02.1–H02.4, подготовка криптографического прототипа и спецификации.
- H02.1: реализована поддержка Room + SQLCipher 4.6.1 (`net.zetetic:sqlcipher-android:4.6.1`). Тестовый набор `SqlCipherSpikeTest` выполнен на эмуляторе API 29 (6/6 PASS): round-trip, проверка шифрования файла на диске, блокировка доступа по неверному ключу, WAL-режим, миграция схемы `MIGRATION_1_2`, native ABI/16 KB (критерий `sqlcipher-spike` = PASS).
- H02.2: подключены Bouncy Castle 1.78.1 (`Argon2idKdf`) и Google Tink (`StreamingAeadHelper` / `AesGcmHkdfStreaming`, `AesGcmEnvelope`). Проверены официальные векторы RFC 9106 (Argon2id, section 5.3) и NIST SP 800-38D (CAVP Case 16), защита AAD, повреждение и усечение потока (все unit-тесты PASS). Замерен KDF на runtime API 29: 64 MiB (t=3, p=1) выполняется за ~1.326 с (критерий `crypto-vectors` = PASS).
- H02.3: проверена генерация неэкспортируемых AES-256-GCM ключей в `AndroidKeyStore`, изоляция `main` и `vault`, обнуление промежуточных массивов ключей в памяти, инициализация `BiometricPrompt.CryptoObject` (критерий `keystore-spike` = PASS).
- H02.4: подготовлен и зафиксирован [ADR 0002](adr/0002-crypto-specification.md) с детальной спецификацией key envelopes, формата `.lifevault` и процедур смены PIN (критерий `crypto-spec` = PASS).
- Форматирование кода: `ktlintCheck` пройден (0 нарушений).
- Статическая проверка проекта: `verify-project.mjs` — PASS (82 Markdown-файла, 246 локальных ссылок).
- Контрольная точка: все 4 обязательных критерия H02 пройдены (PASS). Для выполнения команды `close` требуется подтверждение владельцем/специалистом ручного решения `crypto-design-review` в `harness/decisions.json`. До получения решения реализация H02 остановлена на границе разрешённого диапазона.

