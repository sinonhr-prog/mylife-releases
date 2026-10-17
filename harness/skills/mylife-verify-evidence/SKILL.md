---
name: mylife-verify-evidence
description: Verify My Life phase acceptance with fresh executable checks and source-bound evidence. Use at step verification, reviews, and phase completion; never treat documentation or a generated screenshot as Android runtime proof.
---

# Проверка доказательств

Прочитать docs/TESTING.md, harness/BUILD.md и requiredChecks текущей карточки. Различать плановую команду, доступную команду и реально выполненную команду.

- Для каждого критерия записать PASS/FAIL/NOT_RUN, command/cwd, exit code или наблюдение ручной проверки, время, устройство/API, artifact и текущий digest. Нулевое число выполненных тестов не считается покрытием.
- Если исходники изменились после проверки, перепроверить затронутое. Не повторять уже успешные несвязанные проверки без причины.
- Отрицательные тесты должны проверять инвариант: неверный ключ не открывает БД, отмена restore сохраняет прежние данные, vault не появляется в dashboard. Не подменять их проверкой того же литерала, что в реализации.
- Не редактировать ожидаемый результат только для получения PASS. Не скрывать known failures фильтром тестов.
- Санитизировать evidence до помещения в harness/runs; сохранить хэш. Не загружать реальные дампы/скриншоты/пароли в модель.
- Перед close сверить ручные решения, completedSteps и все requiredChecks; выполнить gate. Не выдавать gate validator за криптографический аудит или подтверждение личности модели.

После security/migration изменения обязательный полный соответствующий gate; после документационной правки достаточно статических проверок, если она не меняет исполняемые правила/валидатор.
