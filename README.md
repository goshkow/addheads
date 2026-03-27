# AddHeads

[English](#english) | [Русский](#русский)

## English

Minimal Paper/Purpur plugin for adding player heads to chat and automatically prepending the head to each player's in-game list name.

### What It Does

AddHeads does not take over chat formatting.
It waits until another chat plugin finishes building the final component, then prepends the player's head while preserving the existing formatting, colors, links, hover events, and click events.

AddHeads focuses on chat decoration and automatically prepends the head to each player's list-name component through Paper's `playerListName(Component)` API.
It also provides PlaceholderAPI outputs for custom layouts, prefixes, scoreboards, and similar UI, but the default tab head path no longer depends on placeholder setup.

The plugin also includes admin-facing update checks for GitHub and Modrinth and a localized settings GUI for live config changes.

### Features

- player head rendering in chat using Adventure components
- PlaceholderAPI support
- SkinsRestorer-aware skin resolution
- shared skin cache with automatic refresh
- personal chat/tab visibility toggles
- localized settings GUI
- update checks from GitHub and Modrinth
- subtle local sound feedback for successful commands and menu actions
- live language switching
- automatic backfill of missing config and language keys on reload
- TAB compatibility checks and admin repair action
- universal automatic tab-head insertion without extra tab-plugin configuration

### Requirements

- Paper / Purpur `1.21.11+`
- Java `21`

Optional:

- PlaceholderAPI
- SkinsRestorer

### Installation

1. Build or download `AddHeads-1.0.2.jar`.
2. Place it into your server `plugins/` folder.
3. Start or restart the server.
4. Edit `config.yml` and `languages/*.yml` if needed.
5. Use `/hd settings` or `/hd reload` after making changes.

### Configuration

Main config: [`src/main/resources/config.yml`](./src/main/resources/config.yml)

Available settings:

- `chat`
- `placeholder`
- `tab.enabled`
- `tab.refresh-interval-seconds`
- `skin-refresh-interval-seconds`
- `formatting.chat-head-space`
- `formatting.tab-head-space`
- `language.file`
- `messages.prefix`
- `update-check.*`
- `premium.*`
- `addhead.togglechat`
- `addhead.toggletab`

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

Optional placeholder mode for custom layouts:

```txt
%addhead_tab%
```

Important for TAB:

- `%addhead_tab%` is still available for custom layouts, but it is not required for the default player-list head.
- `tab.enabled` controls the automatic player-list head injection.
- `tab.refresh-interval-seconds` controls how often AddHeads re-applies the head so it stays visible if another plugin rewrites the list name.
- `formatting.tab-head-space` controls whether AddHeads appends a trailing space after the head placeholder
- keeping that space enabled is strongly recommended; disabling it can make the output look cramped or misaligned

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
- see a cleaner, block-based status layout in the menu

`/hd fixtab` is an admin maintenance command kept for TAB repair. It is intentionally not shown in the public usage string.

### Permissions

```txt
addhead.settings
addhead.reload
addhead.premium
```

- `addhead.settings` allows opening the settings GUI.
- `addhead.reload` allows reloading the plugin and using TAB repair actions.
- Both `addhead.settings` and `addhead.reload` receive English-only update notices.
- `addhead.togglechat` allows toggling your own chat heads.
- `addhead.toggletab` allows toggling your own tab heads.
- `addhead.premium` is only used when `premium.mode=permission`.

The toggle permissions default to `true`, so server owners can revoke them with their permission plugin only when they want to block player-side toggles.

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
3. Paper profile update lookup
4. Mojang profile/session lookup by player name
5. cached value already resolved by AddHeads

### Notes

- AddHeads does not require a resource pack.
- No additional client mods are needed.
- Everything works on a vanilla client.
- AddHeads does not require any TAB-side placeholder setup for the default player-list head.
- The default path uses Paper's `playerListName(Component)` API, so it works without TAB configuration.
- The automatic tab head path is designed to work out of the box with vanilla player lists and with tab-related plugins without asking you to insert placeholders manually.
- `tab.enabled` can turn the automatic tab-head injection on or off globally.
- `tab.refresh-interval-seconds` keeps the head in sync when another plugin rewrites the player list.
- If no skin texture is available from SkinsRestorer or the live profile, AddHeads tries Paper's profile update API and then Mojang profile/session lookup before falling back to the normal vanilla head.
- If every texture source fails, the client falls back to the default Steve/Alex-style head automatically.
- Update checks parse version numbers from release names and tags, so custom release titles are fine.
- `%addhead_tab%` is still available for custom placeholder-based layouts, but it is optional for the default path.

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
- проверка TAB и админский repair

### Требования

- Paper / Purpur `1.21.11+`
- Java `21`

Опционально:

- PlaceholderAPI
- SkinsRestorer

### Установка

1. Соберите или скачайте `AddHeads-1.0.2.jar`.
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

### Recommended usage:

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

`/hd fixtab` - служебная админ-команда для исправления TAB. Она специально не показывается в публичном usage.

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

