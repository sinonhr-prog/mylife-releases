# reminders

Интерфейс напоминаний, диагностика доставки и адаптер Android AlarmManager. Применяются app/AGENTS.md и контракты PRODUCT/TESTING в корне docs.

ViewModel использует интерфейсы репозиториев. UI не обращается к DAO или ключам. Незавершённая форма хранится как ReminderDraft: пустое название допустимо до сохранения. ReminderItem создаётся после проверки ввода; инварианты доменной модели сохраняются.

Проверки из apps/android: :app:testDebugUnitTest, :app:compileDebugAndroidTestKotlin, :app:assembleDebug, :app:lintDebug. Synthetic Compose regression: :app:connectedDebugAndroidTest с -PdebugApplicationIdSuffix=.qa и -Pandroid.testInstrumentationRunnerArguments.class=local.mylife.RemindersScreenTest. Тест проверяет открытие пустой формы, ошибку пустого названия, отмену и сохранение; использует только in-memory repository.

Проверено 17.10.2026: app unit43/43, Compose API29 1/1, debug build/компиляция тестов PASS, lint0 ошибок/75 предупреждений. Доставка на Vivo, Doze, reboot и разрешения этим UI-тестом не подтверждаются.
