package goshkow.addhead;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds and applies the interactive AddHeads settings menu.
 */
public final class SettingsMenu {

    private static final int SIZE = 27;
    private static final int SLOT_CLOSE = 8;
    private static final int SLOT_LANGUAGE = 4;
    private static final int SLOT_CHAT = 10;
    private static final int SLOT_PLACEHOLDER = 11;
    private static final int SLOT_PREMIUM_ENABLED = 13;
    private static final int SLOT_PREMIUM_MODE = 14;
    private static final int SLOT_PREMIUM_CHAT = 15;
    private static final int SLOT_PREMIUM_TAB = 16;
    private static final int SLOT_REFRESH = 22;
    private static final int SLOT_RELOAD = 26;

    private final AddHeads plugin;
    private final SettingsSessionService sessions;

    public SettingsMenu(AddHeads plugin, SettingsSessionService sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
    }

    public void open(Player player) {
        player.openInventory(build(player));
    }

    public void reopenLater(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                open(player);
            }
        });
    }

    public boolean handleClick(Player player, int slot) {
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return true;
        }

        if (slot == SLOT_LANGUAGE) {
            cycleLanguage(player);
            return true;
        }

        if (slot == SLOT_CHAT) {
            toggleBooleanSetting(player, "chat", "settings.feedback.chat-enabled", "settings.feedback.chat-disabled", true);
            return true;
        }

        if (slot == SLOT_PLACEHOLDER) {
            toggleBooleanSetting(player, "placeholder", "settings.feedback.placeholder-enabled", "settings.feedback.placeholder-disabled", true);
            return true;
        }

        if (slot == SLOT_PREMIUM_ENABLED) {
            toggleBooleanSetting(player, "premium.enabled", "settings.feedback.premium-enabled", "settings.feedback.premium-disabled", true);
            return true;
        }

        if (slot == SLOT_PREMIUM_MODE) {
            if (plugin.isPremiumFeatureEnabled()) {
                cyclePremiumMode(player);
            }
            return true;
        }

        if (slot == SLOT_PREMIUM_CHAT) {
            if (plugin.isPremiumFeatureEnabled()) {
                toggleBooleanSetting(player, "premium.disable-chat-heads", "settings.feedback.premium-chat-enabled", "settings.feedback.premium-chat-disabled", false);
            }
            return true;
        }

        if (slot == SLOT_PREMIUM_TAB) {
            if (plugin.isPremiumFeatureEnabled()) {
                toggleBooleanSetting(player, "premium.disable-tab-heads", "settings.feedback.premium-tab-enabled", "settings.feedback.premium-tab-disabled", false);
            }
            return true;
        }

        if (slot == SLOT_REFRESH) {
            requestSkinRefreshInput(player);
            return true;
        }

        if (slot == SLOT_RELOAD && player.hasPermission("addhead.reload")) {
            plugin.reloadPlugin();
            player.sendMessage(plugin.message("settings.feedback.reloaded"));
            reopenLater(player);
            return true;
        }

        return false;
    }

    public void handleChatInput(Player player, String rawInput) {
        SettingsSessionService.PendingInput pending = sessions.consume(player.getUniqueId());
        if (pending == null) {
            return;
        }

        String input = rawInput == null ? "" : rawInput.trim();
        if (pending == SettingsSessionService.PendingInput.SKIN_REFRESH_SECONDS) {
            long seconds;
            try {
                String normalized = extractInteger(input);
                if (normalized.isEmpty()) {
                    throw new NumberFormatException("No digits found");
                }
                seconds = Long.parseLong(normalized);
            } catch (NumberFormatException exception) {
                sessions.begin(player.getUniqueId(), pending);
                player.sendMessage(plugin.message("settings.feedback.invalid-number"));
                sendCancelPrompt(player);
                return;
            }

            if (seconds < 1L || seconds > 86400L) {
                sessions.begin(player.getUniqueId(), pending);
                player.sendMessage(plugin.message("settings.feedback.invalid-number"));
                sendCancelPrompt(player);
                return;
            }

            FileConfiguration config = plugin.getConfig();
            config.set("skin-refresh-interval-seconds", seconds);
            plugin.saveConfig();
            plugin.restartSkinService();
            player.sendMessage(plugin.message("settings.feedback.skin-refresh-set", java.util.Map.of("value", String.valueOf(seconds))));
            reopenLater(player);
        }
    }

    private void cycleLanguage(Player player) {
        List<String> files = plugin.getAvailableLanguageFiles();
        if (files.isEmpty()) {
            return;
        }

        String current = plugin.getLanguageFile();
        int index = files.indexOf(current);
        String next = files.get(index >= 0 ? (index + 1) % files.size() : 0);

        plugin.getConfig().set("language.file", next);
        plugin.saveConfig();
        plugin.reloadLanguage();
        player.sendMessage(plugin.message("settings.feedback.language-changed", java.util.Map.of("value", stripExtension(next))));
        reopenLater(player);
    }

    private void cyclePremiumMode(Player player) {
        String current = plugin.getPremiumMode().toLowerCase(Locale.ROOT);
        List<String> modes = List.of("auto", "permission", "limboauth");
        int index = modes.indexOf(current);
        String next = modes.get(index >= 0 ? (index + 1) % modes.size() : 0);

        plugin.getConfig().set("premium.mode", next);
        plugin.saveConfig();
        player.sendMessage(plugin.message("settings.feedback.premium-mode", java.util.Map.of("value", next)));
        reopenLater(player);
    }

    private void toggleBooleanSetting(Player player, String path, String enabledKey, String disabledKey, boolean trueMeansEnabled) {
        FileConfiguration config = plugin.getConfig();
        boolean next = !config.getBoolean(path, false);
        config.set(path, next);
        plugin.saveConfig();
        boolean enabled = trueMeansEnabled ? next : !next;
        player.sendMessage(plugin.message(enabled ? enabledKey : disabledKey));
        reopenLater(player);
    }

    private void requestSkinRefreshInput(Player player) {
        sessions.begin(player.getUniqueId(), SettingsSessionService.PendingInput.SKIN_REFRESH_SECONDS);
        player.closeInventory();
        player.sendMessage(plugin.message("settings.prompt.skin-refresh"));
        sendCancelPrompt(player);
    }

    private Inventory build(Player player) {
        String title = ChatColor.stripColor(plugin.getLanguageManager().get("settings.menu.title"));
        if (title == null || title.isBlank()) {
            title = "AddHeads settings";
        }
        Inventory inventory = Bukkit.createInventory(new SettingsMenuHolder(), SIZE, colorize("&8" + title));

        inventory.setItem(SLOT_CLOSE, item(Material.BARRIER, plugin.getLanguageManager().get("settings.menu.close"), List.of(
                plugin.getLanguageManager().get("settings.lore.click-close")
        )));

        inventory.setItem(SLOT_LANGUAGE, item(Material.BOOK, plugin.getLanguageManager().get("settings.menu.language"), List.of(
                plugin.getLanguageManager().get("settings.lore.current", java.util.Map.of("value", stripExtension(plugin.getLanguageFile()))),
                plugin.getLanguageManager().get("settings.lore.click-cycle")
        )));

        inventory.setItem(SLOT_CHAT, item(plugin.isChatFeatureEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                plugin.getLanguageManager().get("settings.menu.chat"), List.of(
                        plugin.getLanguageManager().get("settings.lore.current", java.util.Map.of("value", booleanState(plugin.isChatFeatureEnabled()))),
                        plugin.getLanguageManager().get("settings.lore.click-toggle")
                )));

        inventory.setItem(SLOT_PLACEHOLDER, item(plugin.isPlaceholderFeatureEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                plugin.getLanguageManager().get("settings.menu.placeholder"), List.of(
                        plugin.getLanguageManager().get("settings.lore.current", java.util.Map.of("value", booleanState(plugin.isPlaceholderFeatureEnabled()))),
                        plugin.getLanguageManager().get("settings.lore.click-toggle")
                )));

        inventory.setItem(SLOT_PREMIUM_ENABLED, item(plugin.isPremiumFeatureEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                plugin.getLanguageManager().get("settings.menu.premium-enabled"), List.of(
                        plugin.getLanguageManager().get("settings.lore.current", java.util.Map.of("value", booleanState(plugin.isPremiumFeatureEnabled()))),
                        plugin.getLanguageManager().get("settings.lore.click-toggle"),
                        plugin.isPremiumFeatureEnabled()
                                ? plugin.getLanguageManager().get("settings.lore.click-cycle")
                                : plugin.getLanguageManager().get("settings.lore.premium-hidden")
                )));

        if (plugin.isPremiumFeatureEnabled()) {
            inventory.setItem(SLOT_PREMIUM_MODE, item(Material.COMPARATOR, plugin.getLanguageManager().get("settings.menu.premium-mode"), List.of(
                    plugin.getLanguageManager().get("settings.lore.current", java.util.Map.of("value", plugin.getPremiumMode())),
                    plugin.getLanguageManager().get("settings.lore.click-cycle")
            )));

            boolean premiumChatEnabled = !plugin.getConfig().getBoolean("premium.disable-chat-heads", false);
            inventory.setItem(SLOT_PREMIUM_CHAT, item(premiumChatEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                    plugin.getLanguageManager().get("settings.menu.premium-chat"), List.of(
                            plugin.getLanguageManager().get("settings.lore.current", java.util.Map.of("value", booleanState(premiumChatEnabled))),
                            plugin.getLanguageManager().get("settings.lore.click-toggle")
                    )));

            boolean premiumTabEnabled = !plugin.getConfig().getBoolean("premium.disable-tab-heads", false);
            inventory.setItem(SLOT_PREMIUM_TAB, item(premiumTabEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                    plugin.getLanguageManager().get("settings.menu.premium-tab"), List.of(
                            plugin.getLanguageManager().get("settings.lore.current", java.util.Map.of("value", booleanState(premiumTabEnabled))),
                            plugin.getLanguageManager().get("settings.lore.click-toggle")
                    )));
        }

        inventory.setItem(SLOT_REFRESH, item(Material.CLOCK, plugin.getLanguageManager().get("settings.menu.skin-refresh"), List.of(
                plugin.getLanguageManager().get("settings.lore.current", java.util.Map.of("value", String.valueOf(plugin.getSkinRefreshIntervalSeconds()))),
                plugin.getLanguageManager().get("settings.lore.click-enter")
        )));

        if (player.hasPermission("addhead.reload")) {
            inventory.setItem(SLOT_RELOAD, item(Material.ANVIL, plugin.getLanguageManager().get("settings.menu.reload"), List.of(
                    plugin.getLanguageManager().get("settings.lore.click-reload")
            )));
        }

        return inventory;
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colorize(name));
            meta.setLore(colorize(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private List<String> colorize(List<String> input) {
        List<String> result = new ArrayList<>(input.size());
        for (String line : input) {
            result.add(colorize(line));
        }
        return result;
    }

    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private String booleanState(boolean value) {
        return value ? plugin.getLanguageManager().get("settings.state.enabled") : plugin.getLanguageManager().get("settings.state.disabled");
    }

    public void cancelPendingInput(Player player) {
        sessions.clear(player.getUniqueId());
        player.sendMessage(plugin.message("settings.feedback.cancelled"));
        reopenLater(player);
    }

    private void sendCancelPrompt(Player player) {
        player.sendMessage(buildCancelLine());
    }

    private Component buildCancelLine() {
        String rawText = ChatColor.stripColor(plugin.getLanguageManager().get("settings.prompt.cancel"));
        if (rawText == null || rawText.isBlank()) {
            rawText = "Click here to cancel.";
        }

        return Component.text(rawText, NamedTextColor.GRAY)
                .decorate(net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.runCommand("/hd settings cancel"))
                .hoverEvent(HoverEvent.showText(Component.text("Return to the menu", NamedTextColor.WHITE)));
    }

    private String extractInteger(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        boolean negative = false;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '-' && builder.length() == 0 && !negative) {
                negative = true;
                builder.append(ch);
                continue;
            }
            if (Character.isDigit(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String stripExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.toLowerCase(Locale.ROOT).endsWith(".yml")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
    }
}
