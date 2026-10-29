# Changelog

## 1.3.0

- Stopped rewriting `components.minimessage-support` inside TAB's own `config.yml`; the server owner's TAB parser mode is left untouched (#6).
- Rendered player-list heads with syntax TAB understands in both of its parsers (`<head_texture:HASH>`, `<head:UUID>`, `<head:NAME>`) instead of the previous `<head:signed_texture:...>`, `<head:id:...>`, `<head:name:...>` and `<head:texture:...>` forms, which TAB read as head definitions literally named `signed_texture`, `id`, `name` or `texture` (#6).
- Added full Folia support: the plugin is marked `folia-supported` and every scheduled task now goes through a new `Schedulers` facade that uses the global region, async and entity schedulers on Folia and the regular Bukkit scheduler elsewhere (#7).
- Player-list writes and inventory opens are dispatched to the thread that owns the player on Folia (#7).
- Skipped the ProtocolLib viewer path on Folia, where ProtocolLib is not supported, and used the per-player player-list path instead (#7).
- Kept the 1.2.8 watchdog fix, now expressed through the same scheduler facade: skin resolution never runs on a ticking thread, refresh cycles cannot overlap, and Mojang HTTP calls have connect and request timeouts (#9).
- Added `TabHeadMarkupTest` and rewrote `SkinServiceThreadingTest` to cover the TAB markup contract and the scheduling guarantees.

## 1.2.8

- Fixed a potential server watchdog freeze caused by synchronous skin resolution during player-list head refreshes.
- Moved skin refresh cycles to asynchronous scheduling to prevent slow skin lookups from blocking the main server thread.
- Prevented overlapping refresh cycles with a reentrancy guard.
- Avoided synchronous skin resolution on the main thread when the skin cache is cold, allowing cached data or fallback rendering while the cache is populated asynchronously.
- Added connection and request timeouts to AddHeads' Mojang profile and session server HTTP requests.
- Ensured player-list name refreshes are scheduled on the main server thread.
- Added automated threading tests for skin refresh scheduling, cache behavior, and player-list refresh handling.

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