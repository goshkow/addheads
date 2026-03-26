# AddHeads

[English](#english) | [Русский](#русский)

## English

Minimal Paper/Purpur plugin for adding player heads to chat and exposing reusable head placeholders for other plugins.

### What It Does

AddHeads does not take over chat formatting.
It waits until another chat plugin finishes building the final component, then prepends the player's head while preserving the existing formatting, colors, links, hover events, and click events.

For tablists, prefixes, scoreboards, and similar UI, AddHeads provides PlaceholderAPI outputs instead of forcing one specific integration path.

### Features

- player head rendering in chat using Adventure components
- PlaceholderAPI support
- SkinsRestorer-aware skin resolution
- shared skin cache with automatic refresh
- personal chat/tab visibility toggles
- localized settings GUI
- live language switching
- automatic backfill of missing config and language keys on reload
- TAB compatibility checks and admin fix action for MiniMessage mode

### Requirements

- Paper / Purpur `1.21.11+`
- Java `21`

Optional:

- PlaceholderAPI
- SkinsRestorer

### Installation

1. Build or download `AddHeads-1.0.0.jar`.
2. Place it into your server `plugins/` folder.
3. Start or restart the server.
4. Edit `config.yml` and `languages/*.yml` if needed.
5. Use `/hd settings` or `/hd reload` after making changes.

### Configuration

Main config: [`src/main/resources/config.yml`](./src/main/resources/config.yml)

Available settings:

- `chat`
- `placeholder`
- `skin-refresh-interval-seconds`
- `language.file`
- `messages.prefix`
- `premium.*`

When AddHeads starts or reloads, it compares the server `config.yml` with the bundled template and fills in any missing keys without overwriting your existing values.

### PlaceholderAPI

Main placeholders:

- `%addhead_head%`
- `%addhead_tab%`
- `%addhead_texture_value%`
- `%addhead_texture_signature%`
- `%addhead_texture_hash%`
- `%addhead_skin_ready%`
- `%addhead_tab_visible%`

Recommended default for TAB:

```txt
%addhead_tab%
```

Important for TAB:

- TAB player head components require `components.minimessage-support: false`
- if MiniMessage support is enabled in TAB, `%addhead_tab%` may not render correctly
- AddHeads warns admins when it detects this TAB setting

Recommended usage:

- before the tab prefix
- directly inside the tab prefix
- inside LuckPerms prefixes if the target plugin supports PlaceholderAPI

### Commands

```txt
/hd togglechat
/hd toggletab
/hd settings
/hd info
```

`/hd settings` opens a localized GUI for admins with `addhead.settings`.

From there you can:

- cycle language files found in `languages/`
- toggle the main switches
- change premium defaults
- reload the plugin
- update the skin refresh interval in seconds

`/hd fixtab` is an admin maintenance command kept for TAB MiniMessage repair. It is intentionally not shown in the public usage string.

### Permissions

```txt
addhead.settings
addhead.reload
addhead.premium
```

- `addhead.settings` allows opening the settings GUI.
- `addhead.reload` allows reloading the plugin and using TAB repair actions.
- `addhead.premium` is only used when `premium.mode=permission`.

### Languages

Bundled languages:

- `en-us`
- `ru-ru`
- `es-es`
- `de-de`
- `fr-fr`
- `pt-br`
- `zh-cn`

Any `*.yml` file placed in `plugins/AddHeads/languages/` is picked up automatically and becomes available in the settings GUI.

If a language file is older than the current plugin version, missing keys are filled from the English template on reload.

### Skin Sources

AddHeads resolves skins in this order:

1. SkinsRestorer
2. live Paper player profile
3. cached value already resolved by AddHeads

### Notes

- AddHeads does not require a resource pack.
- No additional client mods are needed.
- Everything works on a vanilla client.
- AddHeads no longer injects heads into tab by itself.
- For TAB integration, add the placeholder explicitly in TAB's config.
- If your TAB setup does not render player head object components correctly, check TAB component settings.

### License

This project is distributed under a custom non-commercial source-available license included in [`LICENSE`](./LICENSE). See [`NOTICE`](./NOTICE) for attribution lines.

## Русский

Минимальный плагин для Paper/Purpur, который добавляет головы игроков в чат и отдаёт reusable head placeholders для других плагинов.

### Что делает

AddHeads не берёт на себя форматирование чата.
Он ждёт, пока другой плагин закончит собирать финальный компонент сообщения, а затем добавляет голову игрока в начало, сохраняя цвета, ссылки, hover- и click-events.

Для таба, префиксов, скорбордов и похожих интерфейсов AddHeads отдаёт PlaceholderAPI-форматы, а не навязывает один конкретный способ интеграции.

### Возможности

- отображение головы игрока в чате через Adventure components
- поддержка PlaceholderAPI
- поддержка SkinsRestorer
- общий кэш скинов с автообновлением
- персональные переключатели для чата и TAB
- локализованное меню настроек
- переключение языка на лету
- автоматическое добавление недостающих ключей в конфиг и языковые файлы при reload
- проверка TAB и админский фикс MiniMessage режима

### Требования

- Paper / Purpur `1.21.11+`
- Java `21`

Опционально:

- PlaceholderAPI
- SkinsRestorer

### Установка

1. Соберите или скачайте `AddHeads-1.0.0.jar`.
2. Положите его в папку `plugins/` на сервере.
3. Запустите или перезапустите сервер.
4. При необходимости отредактируйте `config.yml` и `languages/*.yml`.
5. После изменений используйте `/hd settings` или `/hd reload`.

### Конфиг

Главный конфиг: [`src/main/resources/config.yml`](./src/main/resources/config.yml)

Доступные настройки:

- `chat`
- `placeholder`
- `skin-refresh-interval-seconds`
- `language.file`
- `messages.prefix`
- `premium.*`

При старте и reload плагин сравнивает серверный `config.yml` со встроенным шаблоном и дополняет недостающие ключи, не трогая уже заданные значения.

### PlaceholderAPI

Основные плейсхолдеры:

- `%addhead_head%`
- `%addhead_tab%`
- `%addhead_texture_value%`
- `%addhead_texture_signature%`
- `%addhead_texture_hash%`
- `%addhead_skin_ready%`
- `%addhead_tab_visible%`

Рекомендуемый вариант для TAB:

```txt
%addhead_tab%
```

Важно для TAB:

- head components в TAB требуют `components.minimessage-support: false`
- если в TAB включён MiniMessage, `%addhead_tab%` может отображаться неправильно
- AddHeads предупреждает администраторов, если обнаруживает такую настройку TAB

Рекомендуемое использование:

- перед tab prefix
- прямо внутри tab prefix
- внутри префиксов LuckPerms, если целевой плагин поддерживает PlaceholderAPI

### Команды

```txt
/hd togglechat
/hd toggletab
/hd settings
/hd info
```

`/hd settings` открывает локализованное GUI для администраторов с правом `addhead.settings`.

Оттуда можно:

- переключать языки из папки `languages/`
- включать и выключать основные функции
- менять дефолты premium-режима
- перезагружать плагин
- менять интервал обновления скина в секундах

`/hd fixtab` - служебная админ-команда для исправления TAB MiniMessage. Она специально не показывается в публичном usage.

### Права

```txt
addhead.settings
addhead.reload
addhead.premium
```

- `addhead.settings` позволяет открыть GUI настроек.
- `addhead.reload` позволяет перезагружать плагин и использовать TAB repair actions.
- `addhead.premium` используется только когда `premium.mode=permission`.

### Языки

Встроенные языки:

- `en-us`
- `ru-ru`
- `es-es`
- `de-de`
- `fr-fr`
- `pt-br`
- `zh-cn`

Любой файл `*.yml`, который вы положите в `plugins/AddHeads/languages/`, будет автоматически подхвачен и появится в GUI.

Если файл перевода старее текущей версии плагина, недостающие ключи автоматически возьмутся из английского шаблона при reload.

### Источники скинов

AddHeads берёт скины в таком порядке:

1. SkinsRestorer
2. живой профиль Paper игрока
3. уже закэшированное значение AddHeads

### Примечания

- AddHeads не требует resource pack.
- Дополнительные client mods не нужны.
- Всё работает на vanilla client.
- AddHeads больше не пытается сам инжектить головы в TAB.
- Для интеграции с TAB нужно явно вставить placeholder в конфиг TAB.
- Если TAB не рендерит player head object components, проверьте настройки компонента TAB.

### Лицензия

Проект распространяется под кастомной некоммерческой source-available лицензией, текст которой находится в [`LICENSE`](./LICENSE). Строки авторства указаны в [`NOTICE`](./NOTICE).
