# Android workspace

Читать корневые AGENTS.md/README.md и спецификацию затронутого изменения. Этот файл охватывает весь Android workspace.

minSdk 29; Kotlin/Compose; без INTERNET и внешних сервисов. Команды в README.md пока плановые. Не объявлять build/tests выполненными до создания Wrapper и реального запуска. После bootstrap обновить README каждого модуля с точными задачами.

Проверять native ABI/16 KB страницы для SQLCipher и KDF. Все Gradle версии фиксировать. Запрет dynamic versions, destructive migrations и plaintext тестовых ключей в production-коде.
