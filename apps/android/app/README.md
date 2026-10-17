# app

Обновлятор: Kotlin-клиент расположен в feature/settings/updater. Исправление процесса выпуска UPD-002 (17.10.2026) добавляет Gradle properties `appVersionCode` и `appVersionName`; без них локальная сборка сохраняет 1 / 0.1.0. На Windows передавать каждое свойство в кавычках: `./gradlew.bat :app:assembleDebug '-PappVersionCode=8' '-PappVersionName=0.1.8'`. Для последующих выпусков выбирать строго растущий номер. Генератор `.github/scripts/generate-update-manifest.mjs` требует `AAPT2_PATH`, читает конечный APK и не допускает metadata с другой версией. CI debug-подпись не является постоянным каналом обновлений.

Запуск приложения, навигация, AppContainer и пользовательские разделы.

Статус на 16.10.2026: код и Gradle-конфигурация созданы, unit-тесты модуля выполнены успешно. Это не приёмка безопасности или runtime на устройстве.

Зависимости: Все core-модули; UI использует только интерфейсы репозиториев.

Контракты и данные: [модель](../../../docs/DATA_MODEL.md), [архитектура](../../../docs/ARCHITECTURE.md).

Проверенная unit-команда: `./gradlew.bat :app:testDebugUnitTest` для Android-модуля; для чистого model — `:core:model:test`. В Windows тесты прошли через временный ASCII-путь; подробности в toolchain.md корня проекта. Команды выполняются из apps/android. Проверки app и общий lint — в родительском README.

