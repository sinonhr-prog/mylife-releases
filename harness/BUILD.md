# Сборка и проверяемые gates

## Сейчас и после H01

Сейчас доступны Node-скрипты проектного пакета. Android Wrapper, конфигурация и исходники ещё отсутствуют. Команды ниже — контракт для настройки H01, а не заявление, что их уже можно выполнить.

H00 только обнаруживает инструменты. H01 в разрешённом scope закрепляет совместимые JDK/Gradle/AGP/Kotlin/Compose/Room/SQLCipher, Android SDK и ABI; создаёт настоящий Wrapper с проверенной distribution checksum, version catalog и dependency verification. Источники версий — официальные compatibility tables, дата проверки и точные версии в docs/toolchain.md. Нельзя назвать сочетание совместимым без сборки и device smoke.

## Windows

Все Android-команды из `apps/android`, PowerShell. Не хранить абсолютные SDK-пути в репозитории: local.properties локальный и игнорируемый. Использовать Wrapper, не случайный глобальный Gradle. Убедиться, что process exit code соответствует реальной задаче; pipe/tee не должен скрывать отказ. Не цеплять разрушительные операции через cmd. Дополнительное ПО и device-изменения — в рамках разрешения, не обходить sandbox.

Целевой минимальный набор после H01 (точные задачи подтвердить через Wrapper tasks):

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :core:model:test
.\gradlew.bat :app:testDebugUnitTest :core:security:testDebugUnitTest :core:storage:testDebugUnitTest :core:designsystem:testDebugUnitTest
.\gradlew.bat lintDebug ktlintCheck
.\gradlew.bat connectedDebugAndroidTest
```

Instrumentation должен реально запускать app/security/storage tests на нужном устройстве; проверить численность выполненных тестов и отчёты, а не только exit 0. Задача без тестов не заменяет требуемое покрытие. Если задачи отличаются, обновить модульные README и этот документ фактическими командами, сохранив обязательные проверки.

## По типу изменения

- Модель/валидация: unit tests инвариантов, компиляция потребителей. Изменение контракта — relevant integration tests.
- UI: сборка, lint, затронутые Compose tests, 320–430 dp/крупный шрифт/TalkBack, блокировка/поворот/process death. Браузерные HTML screenshots — только референс, не доказательство Android UI.
- Хранение: настоящие Room+SQLCipher instrumentation, все поддержанные миграции, WAL/индексы/FK, ошибки диска/файлов, backup compatibility.
- Ключи/PIN/биометрия/архив: весь security gate карточки + negative tests + review; mock Keystore не заменяет API 29 и Vivo.
- Напоминания: unit времени/повторов и device тесты Doze/reboot/timezone/permissions; Worker не обещает точность сигнала.
- Зависимости/SDK/manifest: чистая сборка из зафиксированных зависимостей, merged manifest, native ABI/16 KB, лицензии и анализ уязвимостей. Интернет инструмента сборки не означает INTERNET в приложении.

## Release

H10 выполняет актуальные release-variant lint/tests/benchmark, static/runtime privacy checks, full restore на чистом устройстве и review. H11 строит подписанный артефакт только после разрешения; ключ подписи вне репозитория и отчётов. Сохранить APK SHA-256, versions, toolchain, commit/digest, sanitised evidence, known limitations. Проверить обновление с предыдущей схемы на тестовых данных. Откат старого APK не заменяет стратегию миграции данных.

Гейт не считается пройденным, если отсутствует устройство, нет результатов тестов, лог обрезан до неизвестного статуса, шаг завершился timeout, тест отключён или выполнен на другом исходном состоянии. Отмечать NOT_RUN/BLOCKED и продолжать только независимую работу. Не ослаблять критерий ради окончания этапа.
