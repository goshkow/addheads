# Changelog

## 1.1.0

- Added a public AddHeads API with service registration through Bukkit `ServicesManager`.
- Added reusable API models for render targets, render options, output formats, and skin textures.
- Added API events for plugin reloads and resolved skin updates.
- Added richer export formats for both the public API and PlaceholderAPI.
- Added format-aware placeholders such as `format_*`, `chat_*`, and `tab_*`.
- Reworked spacing after heads into configurable spacing counts instead of simple on/off toggles.
- Added separate spacing and shadow defaults for chat heads and player-list heads.
- Unified the cache refresh cycle so one configurable interval now drives skin cache refresh and player-list head reapplication.
- Added a confirmation flow for update downloads, with chat confirmation before queueing the file into `plugins/update`.
- Moved GitHub and Modrinth release sources into the plugin itself and left only the update-check enable/interval settings in config.
- Reworked premium detection to support `auto`, `auto_permission`, and `permission` modes without plugin-specific premium hooks.
- Preserved premium player preferences across joins and only apply premium defaults when a player transitions into premium state.
- Updated documentation to describe AddHeads as a universal head provider for chat, player lists, placeholders, and third-party integrations.

## 1.0.1

- Added automatic update checks for GitHub and Modrinth releases.
- Added clickable admin notifications for new releases.
- Added config and language backfill improvements.
- Fixed chat hover inheritance so links and other hover events are preserved.
- Improved the settings GUI layout and inactive premium state handling.
- Added per-command sound feedback for successful actions.
- Added toggle permissions for chat and tab head visibility.
- Updated the plugin version to `1.0.1`.

## 1.0.0

- Initial release.
- Added player head rendering in chat.
- Added PlaceholderAPI head placeholders.
- Added SkinsRestorer-aware skin resolution.
- Added shared skin caching with automatic refresh.
- Added localized settings GUI and language switching.
- Added premium-aware defaults and personal toggles.
