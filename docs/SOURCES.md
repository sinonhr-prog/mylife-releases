# Источники и граница выводов

Проверены 16.10.2026. Это первичные документы, а выбранная архитектура — проектная рекомендация, не готовый рецепт безопасности от поставщиков. Версии библиотек намеренно не закреплены до совместимого технического прототипа.

- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations): слои UI/data, ViewModel, поток состояния. Основание для разделения UI и хранения.
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore): неэкспортируемые ключи, авторизация использования, аппаратные уровни зависят от устройства. Не основание обещать защиту на захваченной ОС.
- [Biometric authentication](https://developer.android.com/identity/sign-in/biometric-auth): работа BiometricPrompt/CryptoObject и ограничения версий. Сам факт показа диалога не равен защите ключа.
- [Android Auto Backup](https://developer.android.com/identity/data/autobackup): отдельно проверять правила облака и переноса; allowBackup=false не универсальная гарантия отключения D2D на OEM.
- [SQLCipher for Android](https://github.com/sqlcipher/sqlcipher-android): поддерживаемый Android-пакет и интеграция SQLite/Room. Версию и native ABI проверить при bootstrap.
- [RFC 9106](https://www.rfc-editor.org/info/rfc9106/): Argon2, параметры и тестовые векторы; фактический бюджет устройства измерить.
- [OWASP MASVS](https://mas.owasp.org/MASVS/): категории требований мобильной защиты. Используется как карта проверок, не как сертификат готового проекта.
- [Android alarms](https://developer.android.com/develop/background-work/services/alarms): точные/неточные сигналы, ограничения ОС и разрешения.
- [Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission): запрос доступа к уведомлениям на новых Android.
- [Clipboard handling](https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling): sensitive flag и риски копирования секретов; очистка не устраняет уже прочитанные копии.
- [Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker): доступ к выбранным изображениям; выбор провайдера не следует считать автоматически локальным.
- [16 KB page sizes](https://developer.android.com/guide/practices/page-sizes): отдельная совместимость native dependencies.
- [Autofill services](https://developer.android.com/identity/autofill/autofill-services): основа будущего автозаполнения, в текущий MVP не входит.
- [OpenAI: prompting](https://learn.chatgpt.com/docs/prompting): цель, контекст, формат результата, границы. Адаптированные запросы находятся в PROMPTING.md.
- [OpenAI: AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md): постоянные инструкции и область их действия. Репозиторные инструкции не заменяют текущую задачу.
