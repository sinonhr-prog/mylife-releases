---
name: mylife-execute-slice
description: Execute one authorized My Life Android plan step with scoped changes and evidence. Use for this repository's implementation phases; not for unrelated projects or implicit permission to start a new phase.
---

# Выполнение шага

Прочитать корневые AGENTS/README, текущий профиль, harness/CURRENT.md и карточку Hxx. Пути в этом skill отсчитываются от корня проекта.

1. Сверить реальный model ID и разрешённый scope, затем next/state/lock. Не выбирать профиль по стилю ответа.
2. Прочитать nearest module AGENTS/README и контракты затронутых данных. Назвать один проверяемый результат шага, границы файлов и проверку.
3. Реализовать минимальный связный срез. Не объединять новую функцию и несвязанный рефакторинг. Синтетические данные не выдавать за сохранённые пользовательские записи.
4. При обнаружении противоречия остановить зависимую часть и сохранить конкретный вопрос/варианты; независимую работу завершать. Не менять продукт ради доступной библиотеки.
5. Выполнить применимые проверки по harness/BUILD.md и карточке. Записать ошибки в docs/knowledge/ERRORS.md до повторной попытки.
6. Обновить CURRENT, evidence, handoff и STATUS. Следующий шаг — только после проверки текущего; следующий этап — после gate и в рамках разрешения.

Общий протокол: harness/PROTOCOL.md. Не запрашивать подтверждение рутинных обратимых действий внутри уже разрешённого scope.
