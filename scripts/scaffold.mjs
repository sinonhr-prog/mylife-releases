import fs from 'node:fs';
import path from 'node:path';
const root = path.resolve(import.meta.dirname, '..');
const write = (p, s) => { const f=path.join(root,p); fs.mkdirSync(path.dirname(f),{recursive:true}); if(!fs.existsSync(f)) fs.writeFileSync(f,s+'\n'); };
const modules = [
 ['app','Запуск приложения, навигация, AppContainer и пользовательские разделы.','Все core-модули; UI использует только интерфейсы репозиториев.','Проверять навигацию, lifecycle, locked/empty/error состояния; не обращаться к DAO и key bytes из Compose.'],
 ['core/model','Чистые типы, идентификаторы, ошибки и интерфейсы репозиториев.','Только Kotlin; без Android, Room и Compose.','Проверять инварианты рейтингов/дат/денег и совместимость контрактов; не добавлять DTO хранения.'],
 ['core/security','PIN, биометрия, key envelopes, сессии и криптографические адаптеры.','core:model; Keystore и проверенные криптобиблиотеки.','Все изменения требуют security tests, negative cases и ревью по docs/SECURITY.md. Не писать собственные примитивы.'],
 ['core/storage','Две БД, DAO, реализации репозиториев, вложения, миграции и архивы.','core:model и core:security.','Проверять реальные SQLCipher, миграции, диск/сбои, атомарность и cross-device restore. Не выпускать Room Entity наружу.'],
 ['core/designsystem','Цветовые токены, типографика, общие Compose-компоненты.','Compose/Material; без storage/security.','Максимум три базовых цвета, touch targets 48 dp, контраст, крупный шрифт; только синтетические Preview.']
];
write('apps/android/README.md',`# Android workspace

Статус: структура для реализации; build.gradle.kts, settings.gradle.kts, Wrapper и исходный Android-код ещё не созданы. Не открывать это как готовый собираемый проект.

Модули: app, core:model, core:security, core:storage, core:designsystem. Границы — в ../../docs/ARCHITECTURE.md относительно корня проекта: фактически [архитектура](../../docs/ARCHITECTURE.md).

После bootstrap из этого каталога планируются команды:

- ./gradlew.bat :app:assembleDebug
- ./gradlew.bat :app:lintDebug testDebugUnitTest
- ./gradlew.bat connectedDebugAndroidTest
- ./gradlew.bat ktlintCheck (после настройки плагина)

На этапе 1 закрепить JDK/SDK/AGP/Gradle/Kotlin и точные рабочие задачи; заменить плановые команды проверенными. Запуск — установка debug APK на тестовое устройство через Android Studio. Данные только синтетические до security gate.

Файлы ключей подписи и local.properties не коммитить. Runtime не использует сеть; build-зависимости загружаются отдельно при подготовке окружения.`);
write('apps/android/AGENTS.md',`# Android workspace

Читать корневые AGENTS.md/README.md и спецификацию затронутого изменения. Этот файл охватывает весь Android workspace.

minSdk 29; Kotlin/Compose; без INTERNET и внешних сервисов. Команды в README.md пока плановые. Не объявлять build/tests выполненными до создания Wrapper и реального запуска. После bootstrap обновить README каждого модуля с точными задачами.

Проверять native ABI/16 KB страницы для SQLCipher и KDF. Все Gradle версии фиксировать. Запрет dynamic versions, destructive migrations и plaintext тестовых ключей в production-коде.`);
for (const [p,purpose,deps,rule] of modules) {
 const base=`apps/android/${p}`;
 const task=p.replaceAll('/',':');
 const links=p==='app'?'../../..':'../../../..';
 write(`${base}/README.md`,`# ${p}\n\n${purpose}\n\nСтатус: каркас каталогов, кода и Gradle-конфигурации нет.\n\nЗависимости: ${deps}\n\nКонтракты и данные: [модель](${links}/docs/DATA_MODEL.md), [архитектура](${links}/docs/ARCHITECTURE.md).\n\nБудущая проверка после bootstrap: \`./gradlew.bat :${task}:testDebugUnitTest\` для Android-модуля; для чистого model — \`:core:model:test\`. Тип модуля и существование задач обязательно подтвердить на этапе 1. Команды выполняются из apps/android. Проверки app и общий lint — в родительском README.`);
 write(`${base}/AGENTS.md`,`# ${p}\n\nОтветственность: ${purpose}\n\nДопустимые зависимости: ${deps}\n\n${rule}\n\nДанные и совместимость: следовать [DATA_MODEL.md](${links}/docs/DATA_MODEL.md); изменения схемы/контракта сопровождаются миграцией и проверкой совместимости. Не менять чужие хранилища через внутренние классы.\n\nКоманды и статус — в README.md этого модуля и родительском Android README; до bootstrap они не выполнялись. Форматирование/lint общие для workspace; после bootstrap закрепить точную применимую задачу в README.\n\nПроверки безопасности и резервных копий: [SECURITY.md](${links}/docs/SECURITY.md), [TESTING.md](${links}/docs/TESTING.md). Реальные данные не использовать. Ошибки фиксировать в корневой docs/knowledge/ERRORS.md.`);
 for(const scope of ['main','test',...(p==='core/model'?[]:['androidTest'])]) {
  const suffix=p==='app'?'app':p.split('/')[1];
  fs.mkdirSync(path.join(root,base,`src/${scope}/kotlin/local/mylife/${suffix}`),{recursive:true});
 }
 if(p!=='core/model') fs.mkdirSync(path.join(root,base,'src/main/res'),{recursive:true});
}
const features={home:'Главная, избранное и сегодняшний обзор',catalog:'Категории, локальный общий поиск без сейфа',purchases:'Товары, фотографии, ссылки и рейтинг 1–10',places:'Магазины, парки, кафе, рестораны и свои категории',tasks:'Задачи, сроки и завершение',notes:'Текстовые заметки',reminders:'Правила напоминаний и диагностика доставки',vault:'Отдельный вход и записи паролей',settings:'PIN, тема, резервная копия и восстановление'};
for(const [name,purpose] of Object.entries(features)) write(`apps/android/app/src/main/kotlin/local/mylife/app/feature/${name}/README.md`,`# ${name}\n\n${purpose}. Будущий UI-пакет app, не отдельный Gradle-модуль.\n\nViewModel → публичный интерфейс репозитория; без DAO и ключей в UI. Нет прямых импортов внутренних классов другого feature. Правила app/AGENTS.md применяются. Сценарии, поля и проверки находятся в корневых docs/PRODUCT.md и docs/TESTING.md. Пока исходный код отсутствует.`);
write('apps/android/core/storage/schemas/README.md','# Room schemas\n\nЗдесь будут экспортированные схемы из реального Room compiler. Не создавать фиктивные schema JSON вручную. Схемы версионировать; тестировать миграции из каждой поддержанной версии.');
console.log('Структура модулей создана; Android-код не генерировался.');
