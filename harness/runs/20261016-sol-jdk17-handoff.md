# Handoff: настройка JDK 17

- RunId: 20261016-sol-jdk17; H01.2 BLOCKED; model gpt-6.1-sol, identitySource operator-confirmed (владелец явно подтвердил точный ID в чате).
- Scope: текущая команда владельца установить JDK 17, затем запустить сборку и инструментальные проверки. Продуктовые функции не изменялись.
- JDK установлен через winget с проверкой хэша установщика; версия package 17.0.20.101, Java Temurin 17.0.20.1+1. Machine JAVA_HOME задан установщиком. В сборочном процессе JAVA_HOME/PATH обновлялись явно; старые терминалы перезапустить.
- Gradle 8.9 запущен. Добавлен apps/android/gradle.properties с android.overridePathCheck=true по сообщению AGP и диагностике BooleanOption из скачанного AGP. Передача незаключённых в кавычки -P/-D через текущий запуск PowerShell не сняла проверку; точный механизм непередачи не доказан. Файл свойств снял проверку. Отключение предупреждения не доказывает последующую Unicode-совместимость.
- Последняя команда: ./gradlew.bat :app:assembleDebug connectedDebugAndroidTest, cwd apps/android, exit 1: SDK location not found. Сборка FAIL, выполнение тестов NOT_RUN. Kotlin-тестов в androidTest — 0. SDK/эмулятор не устанавливались, личное устройство не затрагивалось.
- verify-project PASS, harness validate PASS, harness.test 15/15 PASS в этом запуске; это не Android runtime проверки.
- Git не инициализирован, diff недоступен. Существующий каркас сохранён. Предыдущий H01 имеет handoff/evidence, но предполагаемого JSON run-report нет; старые PASS не агрегировались.
- Изменения: apps/android/gradle.properties, apps/android/README.md; CURRENT/STATUS/ERRORS и новые evidence/report/handoff. Исторические evidence не переписывались.
- Ошибки: ENV-004 (ограничения инструментов), BUILD-001 (путь), ENV-005 (SDK).
- Следующий шаг: Android SDK platform 35/build-tools/platform-tools и SDK location; build/unit/lint/format; реальные instrumentation tests и устройства API 29/Vivo по разрешённому scope. Нельзя закрывать H01 без обязательных gates.
- Lock: 20261016-sol-jdk17; освобождается штатной командой после записи. State не изменялся, gate/close не запускались.
