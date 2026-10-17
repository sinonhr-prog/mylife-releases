---
name: mylife-compose-focus
description: Implement the approved Paper Focus Android interface with a left drawer and private dashboard. Use for Compose UI and navigation in My Life; do not redesign the approved layout or introduce bottom tabs.
---

# Compose: Бумага / Фокус

Референс: design/dashboard.html с layout=focus и design/previews/paper-focus.png. Поведение: docs/PRODUCT.md; поля/сортировка: docs/DATA_MODEL.md.

- Белый и графитовый, спокойная системная типографика, без цветных акцентов/онлайн-карт/загрузки изображений по ссылкам.
- Главная: ближайшее актуальное дело в выделенной карточке, остальные дела ниже без дублирования; затем напоминания, последние посещения, последние добавления. Если задач нет — полезное empty state, не демонстрационная задача.
- Просроченные дела не скрывать за красивой карточкой будущего события: согласованный порядок в DATA_MODEL.md. Даты посещения ручные, не updatedAt.
- ☰ слева открывает drawer со всеми категориями. Back/нажатие снаружи закрывают, текущий пункт отмечен; меню и список прокручиваются, фокус не уходит под меню.
- Нижней навигации нет. Добавление доступно отдельно. Крупный шрифт/48 dp targets/TalkBack и экран 320–430 dp важнее буквального повторения размеров браузерного скриншота.
- ViewModel → repository, без DAO/keys в Compose. Проверять empty/loading/error/locked, process death и возврат из системного picker.
- Если нужна перемена утверждённого сценария, сначала оформить решение. Макет не является хранилищем и не доказывает безопасность.
