# Модель данных и контракты

Это проект схемы, не созданные таблицы. Идентификаторы UUID; время аудита — Instant UTC; пользовательское локальное время напоминания хранится отдельно с ZoneId. Миграции последовательные, schema version явная, destructive reset запрещён.

## Основная зашифрованная база

| Сущность | Поля и ограничения |
|---|---|
| Category | id, name, kind PRODUCT/PLACE/NOTE/TASK, iconKey из локального списка, sortOrder; предустановленные категории не мешают пользовательским |
| Product | id, categoryId, title 1–160, description до 20 000, purchaseUrl до 2048, rating nullable Int 1–10, purchasedOn nullable LocalDate, priceMinor nullable Long, currency nullable ISO code, favorite, archivedAt, createdAt, updatedAt |
| Place | id, categoryId, name, addressText, description, visitStatus WANT/VISITED, lastVisitedAt nullable Instant (ручная отметка), favorite, rating nullable 1–10, createdAt, updatedAt |
| Note | id, title до 160, body до 100 000, favorite, createdAt, updatedAt |
| Task | id, title до 160, description, dueAt nullable, status OPEN/DONE, priority LOW/NORMAL/HIGH, completedAt nullable, createdAt, updatedAt |
| Reminder | id, taskId nullable FK, title, fireInstant или localTime+ZoneId+repeatRule, enabled, createdAt, updatedAt |
| Attachment | id, encryptedRelativePath, mimeAllowlisted, bytes, width, height, encryptionFormatVersion, keyId, thumbnailId nullable |
| ProductAttachment / PlaceAttachment | recordId FK, attachmentId FK, position; типизированные связи вместо непроверяемого polymorphic ownerId |
| Tag и связи | уникальное нормализованное имя, явные связи с конкретными типами |
| ScheduleOutbox | id, reminderId, revision, operation UPSERT/CANCEL, processedAt nullable; без секретных текстов |

Цены — целые минимальные денежные единицы, не Float. Валюта обязательна при цене; сумма неотрицательна. Оценка не выбрана — null. Ссылка: разрешён http/https как текст; javascript/file/content схемы и автоматическое исполнение запрещены. Выход в браузер вне MVP по умолчанию.

Текст нормализовать при поиске, исходный сохранять. Пустое обязательное поле отклонять до записи. Лимиты — начальные продуктовые решения; увеличение требует проверки объёма/производительности, а не ручного обхода валидатора.

## Сейф

VaultEntry: id, title, website, username, password, secretNote, createdAt, updatedAt. Всё включая метаданные находится в vault.db. В первой версии без истории паролей, TOTP и файлов. Поля пароля не trim/normalize; сохранять точные символы. Случайные пароли генерируются CSPRNG с равномерным выбором без modulo bias.

MainEntityId и VaultEntryId различаются типами. Общий SearchRepository не имеет зависимости на VaultRepository. Избранное главной не ссылается на vault.

## Выборки дашборда

Текущие задачи — OPEN, просроченные первыми, затем dueAt по возрастанию с null последними; ближайшие напоминания — enabled и вычисленный следующий сигнал. Места — lastVisitedAt != null по убыванию; ручная дата не должна быть в будущем. Последние добавления — ограниченное объединение main-записей по createdAt, с типом и ID для перехода; изменения не меняют дату добавления. Дополнительный стабильный ключ сортировки — id. Архивированные/удалённые записи и vault не включать. Индекс Place(lastVisitedAt); при добавлении поля существующие данные получают null, а не updatedAt. Для MVP достаточно последнего посещения, полный журнал посещений не требуется.

## Репозитории и ответственность

- ProductRepository / PlaceRepository / NoteRepository / TaskRepository: observe paged list, get, create, update, archive/delete; типизированные ошибки.
- ReminderRepository: manage rule; SchedulerPort: apply/cancel minimal projection с revision, идемпотентно.
- VaultRepository: только при VaultSession, без передачи key bytes в UI.
- AttachmentStore: import encrypted, open bounded decrypted stream, delete; UI не получает путь к ключу.
- BackupService: export snapshot, inspect archive, restore into staging; CancellationSignal/Flow прогресса, без секретов в progress payload.
- SessionController: unlock main/vault, lock, состояние; UI получает capability/сессию, а не ключи.

Конкретные сигнатуры фиксировать в этапе 1–3 после компилируемого прототипа. Интерфейс не выдаёт Room Entity наружу. Атомарная запись задачи+события планирования принадлежит storage; отмена/повтор не создают дубли.

## Удаление, миграции и архив

Связи FK проверяются; категория с записями требует явного переноса/отмены. Удаление записи и метаданных файлов — транзакция, удаление файла — повторяемая cleanup операция. Если процесс погиб, orphan-файлы удаляются после сверки, существующие вложения не теряются.

Архив содержит schemaVersion, exportVersion, creationInstant, счётчики типов, записи и шифрованный поток вложений с проверкой целостности. Имена файлов генерирует приложение; архив не задаёт абсолютные пути. Неизвестная будущая major version отклоняется без изменений. Существующая запись не заменяется «совпадающим id» в частичном merge — merge вне MVP.
