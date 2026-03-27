# Changelog

## 1.0.2

- Reworked tab head insertion so AddHeads now prepends the head directly to the in-game player list name.
- The default tab path no longer requires additional TAB configuration or placeholder setup.
- Tab head insertion is now universal and works with vanilla player lists as well as tab-related plugins that rewrite the list name later.
- Added a dedicated `tab.enabled` switch and `tab.refresh-interval-seconds` setting for the automatic player-list head path.
- Fixed duplicate tab heads during plugin reload by restoring the raw player-list name before reapplying the head.
- Updated tab visibility handling so the player-side toggle now removes or restores the actual tab head, not only placeholder output.
- Added additional skin resolution fallbacks so tab heads can still resolve more reliably when the primary source does not return a texture.
- Updated README, config notes, and release metadata to match the new placeholder-free default tab flow.

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
- Added TAB compatibility checks and repair command.
