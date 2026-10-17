# Источники для запуска и prompting

Проверены 16.10.2026. Рекомендации по размеру шага, gates, контексту и lock — проектные решения. Они не являются обещанием качества модели, автоматической загрузки инструкций или сохранения контекста провайдером.

- [Gemini 3.8 Flash](https://ai.google.dev/gemini-api/docs/whats-new-gemini-3.8): точный ID gemini-3.8-flash и уровни thinking. Доступность аккаунта проверяется отдельно.
- [Google prompting](https://ai.google.dev/gemini-api/docs/prompting-strategies): чёткая задача, структура, контекст и ограничения; применено в основном профиле без копирования больших руководств.
- [Gemini GEMINI.md](https://geminicli.com/docs/cli/gemini-md/): контекст проекта, проверка загруженных файлов и относительные импорты. Наш GEMINI.md явно направляет к общим правилам; проверять загрузку в реальной оболочке.
- [Gemini skills](https://geminicli.com/docs/cli/skills/): workspace/user skills. harness/skills выбран как переносимый исходник с явным чтением, не как автоматически распознаваемый каталог.
- [Gemini Plan Mode](https://geminicli.com/docs/cli/plan-mode/): ограничения записи в режиме плана. Для H00, требующего файлов отчёта, использовать разрешённую документационную запись или стандартный режим с ограниченным scope; не обходить ограничения Plan Mode.
- [GPT‑6.1 Sol](https://developers.openai.com/api/docs/models/gpt-6.1-sol): точный model ID и поддержка reasoning. Используется тот же контракт проверки, что для Gemini.
- [GPT‑6 guide](https://developers.openai.com/api/docs/guides/latest-model?model=gpt-6.1-sol): семейные рекомендации адаптируются и проверяются на выбранной модели; не считать их результатами тестов этого проекта.
- [OpenAI prompting](https://learn.chatgpt.com/docs/prompting): цель, контекст, результат, границы.
- [OpenAI AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md): постоянные инструкции и области действия.
- [OpenAI skills](https://learn.chatgpt.com/docs/build-skills): компактные инструкции и специализированные ресурсы.
- [Исполнимые планы](https://developers.openai.com/cookbook/articles/codex_exec_plans): сохраняемый план, проверяемые этапы и возможность возобновления; наш layout адаптирован к существующему проекту.

Ограничение локальности приложения не делает облачную среду разработки локальной. В агентские запросы идут только исходники/документы и синтетические примеры, без пользовательских паролей, PIN, личных фото, архивов и ключей подписи.
