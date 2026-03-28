# AddHeads

[English](#english) | [Русский](#русский)

## English

AddHeads is a Paper/Purpur plugin that adds player heads to chat, automatically prepends them to the in-game player list name, and exposes a reusable API for other plugins.

### What It Does

AddHeads does not take over chat formatting. It waits until another plugin finishes building the final chat component, then prepends the player's head while preserving colors, links, hover events, click events, and existing formatting.

For the in-game player list, AddHeads prepends the head automatically through Paper's `playerListName(Component)` API. No manual placeholder insertion is required for the default player-list path.

AddHeads also exposes a public API and PlaceholderAPI outputs so other plugins can request heads, textures, separators, and export formats directly.

### Features

- chat head rendering using Adventure components
- automatic player-list head insertion
- public Bukkit service API for other plugins
- PlaceholderAPI integration with multiple export formats
- shared skin cache with automatic refresh
- SkinsRestorer-aware texture resolution
- Mojang and Paper profile fallbacks
- localized settings GUI
- live config and language reload
- automatic config and language key backfill
- per-player toggles for chat and player-list heads
- update checks from GitHub and Modrinth

### Requirements

- Paper / Purpur `1.21.11+`
- Java `21`

Optional:

- PlaceholderAPI
- SkinsRestorer

### Installation

1. Build or download `AddHeads-1.1.0.jar`.
2. Place it into your server `plugins/` folder.
3. Start or restart the server.
4. Edit `config.yml` and `languages/*.yml` if needed.
5. Use `/hd settings` or `/hd reload` after making changes.

### Commands

```txt
/hd togglechat
/hd toggletab
/hd settings
/hd info
/hd reload
/hd update <latest|github|modrinth>
```

### Permissions

```txt
addhead.togglechat
addhead.toggletab
addhead.settings
addhead.reload
addhead.premium
```

- `addhead.togglechat` allows toggling your own chat heads.
- `addhead.toggletab` allows toggling your own player-list heads.
- `addhead.settings` allows opening the settings GUI.
- `addhead.reload` allows reloading the plugin.
- `addhead.premium` is used when `premium.mode=permission` or `premium.mode=auto_permission`.

### Configuration

Main config: [`src/main/resources/config.yml`](./src/main/resources/config.yml)

Key options:

- `chat`
- `placeholder`
- `tab.enabled`
- `cache-refresh-interval-seconds`
- `formatting.chat-head-spacing`
- `formatting.chat-head-shadow`
- `formatting.tab-head-spacing`
- `formatting.tab-head-shadow`
- `language.file`
- `messages.prefix`
- `update-check.enabled`
- `update-check.interval-hours`
- `premium.*`

Update sources for GitHub and Modrinth are built into the plugin and are not configured in `config.yml`.

When AddHeads starts or reloads, missing config keys are filled from the bundled template without overwriting your existing values.

### Public API

AddHeads registers a Bukkit service for direct integration:

```java
import goshkow.addhead.api.AddHeadsAPI;
import goshkow.addhead.api.AddHeadsProvider;
import goshkow.addhead.api.HeadFormat;
import goshkow.addhead.api.HeadRenderTarget;

AddHeadsProvider api = AddHeadsAPI.provider();
if (api != null) {
    String signedTag = api.getFormattedHead(player, HeadFormat.SIGNED_TAG, api.getDefaultOptions(HeadRenderTarget.TAB));
}
```

Available API models:

- `AddHeadsProvider`
- `AddHeadsAPI`
- `HeadFormat`
- `HeadRenderOptions`
- `HeadRenderTarget`
- `SkinTexture`

Built-in API events:

- `AddHeadsReloadEvent`
- `AddHeadsSkinResolvedEvent`

### PlaceholderAPI

Core placeholders:

- `%addhead_head%`
- `%addhead_tab%`
- `%addhead_texture_value%`
- `%addhead_texture_signature%`
- `%addhead_texture_hash%`
- `%addhead_skin_ready%`
- `%addhead_tab_visible%`

Format-aware placeholders:

- `%addhead_format_json%`
- `%addhead_format_signed_tag%`
- `%addhead_format_texture_tag%`
- `%addhead_format_id_tag%`
- `%addhead_format_name_tag%`
- `%addhead_format_texture_value%`
- `%addhead_format_texture_signature%`
- `%addhead_format_texture_hash%`
- `%addhead_format_signed_texture%`
- `%addhead_format_separator%`
- `%addhead_format_skin_ready%`

Target-aware placeholders:

- `%addhead_chat_json%`
- `%addhead_chat_separator%`
- `%addhead_tab_signed_tag%`
- `%addhead_tab_separator%`
- `%addhead_tab_json%`

The `chat_*` and `tab_*` forms use the plugin's default spacing and shadow settings for those targets.

### Skin Sources

AddHeads resolves textures in this order:

1. SkinsRestorer
2. live Paper player profile
3. Paper profile update lookup
4. Mojang profile/session lookup by player name
5. cached value already resolved by AddHeads

### Notes

- AddHeads does not require a resource pack.
- No additional client mods are needed.
- Everything works on a vanilla client.
- The default player-list head path does not require manual placeholder setup.
- AddHeads is designed to work with any plugin that rewrites chat or player-list names later.
- `%addhead_tab%` remains available as an optional export format for custom layouts, but it is not required for the default player-list path.
- `/hd update` queues a download and asks for a chat confirmation (`update`) before the file is copied into `plugins/update`.

### License

This project is distributed under the custom source-available license included in [`LICENSE`](./LICENSE). See [`NOTICE`](./NOTICE) for attribution lines.

## Русский

AddHeads — это плагин для Paper/Purpur, который добавляет головы игроков в чат, автоматически подставляет их в список игроков и отдаёт полноценный API для других плагинов.

### Что делает

AddHeads не перехватывает форматирование чата целиком. Он ждёт, пока другой плагин соберёт финальный компонент сообщения, а затем добавляет голову игрока в начало, не ломая цвета, ссылки, hover-события, click-события и остальное оформление.

Для списка игроков AddHeads автоматически добавляет голову через Paper API `playerListName(Component)`. Для стандартного пути никаких ручных placeholder-настроек не требуется.

Кроме этого, плагин отдаёт публичный API и PlaceholderAPI-форматы, чтобы другие плагины могли получать головы, текстуры, разделители и разные экспортные представления напрямую.

### Возможности

- головы игроков в чате через Adventure components
- автоматическая подстановка головы в список игроков
- публичный Bukkit service API
- PlaceholderAPI с несколькими форматами вывода
- общий кэш скинов с автообновлением
- поддержка SkinsRestorer
- fallback через Paper profile и Mojang profile/session lookup
- локализованное меню настроек
- reload конфига и языков на лету
- автоматическое добавление недостающих ключей в конфиг и языковые файлы
- персональные переключатели для чата и списка игроков
- проверка обновлений через GitHub и Modrinth

### Требования

- Paper / Purpur `1.21.11+`
- Java `21`

Опционально:

- PlaceholderAPI
- SkinsRestorer

### Установка

1. Соберите или скачайте `AddHeads-1.1.0.jar`.
2. Положите его в папку `plugins/`.
3. Запустите или перезапустите сервер.
4. При необходимости отредактируйте `config.yml` и `languages/*.yml`.
5. После изменений используйте `/hd settings` или `/hd reload`.

### Команды

```txt
/hd togglechat
/hd toggletab
/hd settings
/hd info
/hd reload
/hd update <latest|github|modrinth>
```

### Права

```txt
addhead.togglechat
addhead.toggletab
addhead.settings
addhead.reload
addhead.premium
```

- `addhead.togglechat` — переключение голов в чате для себя.
- `addhead.toggletab` — переключение голов в списке игроков для себя.
- `addhead.settings` — доступ к GUI настроек.
- `addhead.reload` — доступ к reload.
- `addhead.premium` — используется, когда `premium.mode=permission` или `premium.mode=auto_permission`.

### Конфиг

Главный конфиг: [`src/main/resources/config.yml`](./src/main/resources/config.yml)

Основные настройки:

- `chat`
- `placeholder`
- `tab.enabled`
- `cache-refresh-interval-seconds`
- `formatting.chat-head-spacing`
- `formatting.chat-head-shadow`
- `formatting.tab-head-spacing`
- `formatting.tab-head-shadow`
- `language.file`
- `messages.prefix`
- `update-check.enabled`
- `update-check.interval-hours`
- `premium.*`

Update sources for GitHub and Modrinth are built into the plugin and are not configured in `config.yml`.

При старте и reload плагин автоматически добавляет недостающие ключи из встроенного шаблона, не перезаписывая уже заданные значения.

### Публичный API

AddHeads регистрирует Bukkit service для прямой интеграции:

```java
import goshkow.addhead.api.AddHeadsAPI;
import goshkow.addhead.api.AddHeadsProvider;
import goshkow.addhead.api.HeadFormat;
import goshkow.addhead.api.HeadRenderTarget;

AddHeadsProvider api = AddHeadsAPI.provider();
if (api != null) {
    String signedTag = api.getFormattedHead(player, HeadFormat.SIGNED_TAG, api.getDefaultOptions(HeadRenderTarget.TAB));
}
```

Доступные модели API:

- `AddHeadsProvider`
- `AddHeadsAPI`
- `HeadFormat`
- `HeadRenderOptions`
- `HeadRenderTarget`
- `SkinTexture`

События API:

- `AddHeadsReloadEvent`
- `AddHeadsSkinResolvedEvent`

### PlaceholderAPI

Базовые placeholders:

- `%addhead_head%`
- `%addhead_tab%`
- `%addhead_texture_value%`
- `%addhead_texture_signature%`
- `%addhead_texture_hash%`
- `%addhead_skin_ready%`
- `%addhead_tab_visible%`

Форматные placeholders:

- `%addhead_format_json%`
- `%addhead_format_signed_tag%`
- `%addhead_format_texture_tag%`
- `%addhead_format_id_tag%`
- `%addhead_format_name_tag%`
- `%addhead_format_texture_value%`
- `%addhead_format_texture_signature%`
- `%addhead_format_texture_hash%`
- `%addhead_format_signed_texture%`
- `%addhead_format_separator%`
- `%addhead_format_skin_ready%`

Контекстные placeholders:

- `%addhead_chat_json%`
- `%addhead_chat_separator%`
- `%addhead_tab_signed_tag%`
- `%addhead_tab_separator%`
- `%addhead_tab_json%`

Формы `chat_*` и `tab_*` используют дефолтные настройки spacing и shadow для соответствующей цели.

### Источники скинов

AddHeads берёт текстуры в таком порядке:

1. SkinsRestorer
2. live Paper player profile
3. Paper profile update lookup
4. Mojang profile/session lookup по имени игрока
5. уже закэшированное значение AddHeads

### Примечания

- AddHeads не требует resource pack.
- Дополнительные клиентские моды не нужны.
- Всё работает на vanilla client.
- Для стандартной подстановки головы в список игроков ничего вручную настраивать не нужно.
- Плагин рассчитан на работу с любыми другими плагинами, которые форматируют чат или позже переписывают имя игрока в списке.
- `%addhead_tab%` остаётся как опциональный формат для кастомных раскладок, но для стандартного пути он не обязателен.

### Лицензия

Проект распространяется под кастомной source-available лицензией из [`LICENSE`](./LICENSE). Строки атрибуции находятся в [`NOTICE`](./NOTICE).
