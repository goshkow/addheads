# Changelog

## 1.2.0

- Added automatic native `TAB` API integration. If `TAB` is installed and player-list heads are enabled, AddHeads now switches to the built-in `TAB` compatibility path automatically with no extra AddHeads-side toggle.
- Fixed player-list head rendering with `TAB` when `tablist-name-formatting` is enabled, so heads now render correctly together with `TAB` prefixes, suffixes, colors, and other tab formatting options.
- Preserved external tab formatting more reliably so AddHeads prepends the head without wiping colors, prefixes, or suffixes from supported player-list plugins.
- Applied the same tab head settings to the `TAB` integration path, including correct skin loading from cache, tab head shadow handling, and configurable spacing after the head.
- Synchronized `TAB` component settings with AddHeads tab settings by forcing the compatible parser mode and matching `TAB` head-shadow behavior to AddHeads' tab shadow option.
- Kept viewer-based tab head rendering through ProtocolLib for the standard compatibility path, so `/hd toggletab` controls which heads the viewer sees instead of changing one global player-list name for everyone.
- Reapplied tab heads after joins, cache refresh cycles, `TAB` player-load events, and `TAB` reload events so formatted names and heads recover correctly after refreshes.
- Confirmed compatibility with `CMI`, `QuickBoardX`, and `PowerBoard` through the standard packet / scoreboard compatibility path, with no extra AddHeads-side integration toggle required.
- Included a broad set of tab compatibility fixes and stability improvements across the built-in player-list path and the native `TAB` path.

## 1.1.1

- Fixed duplicate player-list heads after reloads and repeated player-list refreshes.
- Improved player-list base name recovery so AddHeads does not stack its own rendered head prefix more than once.
- Kept premium default syncing stable across joins and refreshes.

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
