package goshkow.addhead;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

/**
 * Main plugin bootstrap.
 *
 * <p>AddHeads keeps built-in behavior intentionally narrow:
 * chat decoration plus reusable placeholders for external plugins.</p>
 */
public final class AddHeads extends JavaPlugin {

    private SkinService skinService;
    private HeadPlaceholderExpansion placeholderExpansion;
    private PlayerPreferenceService preferenceService;
    private LanguageManager languageManager;
    private SettingsSessionService settingsSessionService;
    private SettingsMenu settingsMenu;
    private UpdateCheckerService updateCheckerService;
    private final ConcurrentMap<UUID, Component> tabBaseNames = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Component> tabRenderedNames = new ConcurrentHashMap<>();
    private NamespacedKey tabBaseNameKey;
    private NamespacedKey tabRenderedNameKey;
    private int tabRefreshTaskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        refreshConfigDefaults();
        skinService = new SkinService(this);
        preferenceService = new PlayerPreferenceService(this);
        languageManager = new LanguageManager(this);
        settingsSessionService = new SettingsSessionService();
        settingsMenu = new SettingsMenu(this, settingsSessionService);
        tabBaseNameKey = new NamespacedKey(this, "tab_base_name");
        tabRenderedNameKey = new NamespacedKey(this, "tab_rendered_name");
        preferenceService.load();
        languageManager.reload(getConfig().getString("language.file", "en-us.yml"));

        // Decorate the final chat component after external formatters finish their work.
        getServer().getPluginManager().registerEvents(new AsyncChatListener(this, skinService), this);

        getServer().getPluginManager().registerEvents(new SkinSyncListener(this, skinService), this);
        getServer().getPluginManager().registerEvents(new AdminNoticeListener(this), this);
        getServer().getPluginManager().registerEvents(new SettingsMenuListener(this, settingsMenu, settingsSessionService), this);
        skinService.start(getSkinRefreshIntervalTicks());
        updateCheckerService = new UpdateCheckerService(this);
        updateCheckerService.start();
        startTabRefreshTask();
        Bukkit.getScheduler().runTask(this, this::refreshAllTabListNames);

        // Placeholder outputs are the supported compatibility layer for TAB and similar plugins.
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new HeadPlaceholderExpansion(this);
            boolean registered = placeholderExpansion.register();
            if (registered) {
                getLogger().info("PlaceholderAPI expansion registered successfully.");
            } else {
                getLogger().warning("Failed to register PlaceholderAPI expansion.");
            }
        } else {
            getLogger().warning("PlaceholderAPI not found, placeholder expansion is disabled.");
        }

        if (getServer().getPluginManager().getPlugin("SkinsRestorer") != null) {
            getLogger().info("SkinsRestorer detected, custom skins will be used when available.");
        }

        AddHeadsCommand command = new AddHeadsCommand(this);
        if (getCommand("addhead") != null) {
            getCommand("addhead").setExecutor(command);
            getCommand("addhead").setTabCompleter(command);
        }
    }

    @Override
    public void onDisable() {
        if (skinService != null) {
            skinService.stop();
            skinService.clear();
        }
        if (updateCheckerService != null) {
            updateCheckerService.stop();
        }
        stopTabRefreshTask();
        restoreAllTabListNames();
        if (preferenceService != null) {
            preferenceService.save();
        }
        tabBaseNames.clear();
        tabRenderedNames.clear();
    }

    public void reloadPlugin() {
        restoreAllTabListNames();
        tabBaseNames.clear();
        tabRenderedNames.clear();
        reloadConfig();
        refreshConfigDefaults();
        if (preferenceService != null) {
            preferenceService.load();
        }
        reloadLanguage();
        restartSkinService();
        if (updateCheckerService != null) {
            updateCheckerService.restart();
        }
        startTabRefreshTask();
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            captureTabBaseName(player);
        }
        refreshAllTabListNames();
    }

    public void reloadLanguage() {
        if (languageManager != null) {
            languageManager.reload(getConfig().getString("language.file", "en-us.yml"));
        }
    }

    public void restartSkinService() {
        if (skinService != null) {
            skinService.clear();
            skinService.start(getSkinRefreshIntervalTicks());
        }
    }

    public boolean isChatFeatureEnabled() {
        return getConfig().getBoolean("chat", true);
    }

    public boolean isPlaceholderFeatureEnabled() {
        return getConfig().getBoolean("placeholder", true);
    }

    public boolean isTabFeatureEnabled() {
        return getConfig().getBoolean("tab.enabled", true);
    }

    public boolean isChatHeadSpaceEnabled() {
        return getConfig().getBoolean("formatting.chat-head-space", true);
    }

    public boolean isTabHeadSpaceEnabled() {
        return getConfig().getBoolean("formatting.tab-head-space", true);
    }

    public boolean isPremiumFeatureEnabled() {
        return getConfig().getBoolean("premium.enabled", false);
    }

    public String getPremiumMode() {
        return getConfig().getString("premium.mode", "auto");
    }

    public String getLanguageFile() {
        return getConfig().getString("language.file", "en-us.yml");
    }

    public List<String> getAvailableLanguageFiles() {
        return languageManager.getAvailableLanguageFiles();
    }

    public long getSkinRefreshIntervalSeconds() {
        long seconds = getConfig().getLong("skin-refresh-interval-seconds", -1L);
        if (seconds < 0L) {
            long ticks = getConfig().getLong("skin-refresh-interval-ticks", 400L);
            seconds = Math.max(1L, Math.round(ticks / 20.0d));
        }
        return Math.max(1L, seconds);
    }

    public long getSkinRefreshIntervalTicks() {
        return getSkinRefreshIntervalSeconds() * 20L;
    }

    public long getUpdateCheckIntervalHours() {
        return Math.max(1L, getConfig().getLong("update-check.interval-hours", 6L));
    }

    public long getUpdateCheckIntervalTicks() {
        return getUpdateCheckIntervalHours() * 60L * 60L * 20L;
    }

    public void openSettingsMenu(org.bukkit.entity.Player player) {
        if (settingsMenu != null) {
            settingsMenu.open(player);
        }
    }

    public void reopenSettingsMenuLater(org.bukkit.entity.Player player) {
        if (settingsMenu != null) {
            settingsMenu.reopenLater(player);
        }
    }

    public void cancelSettingsInput(org.bukkit.entity.Player player) {
        if (settingsMenu != null) {
            settingsMenu.cancelPendingInput(player);
        }
    }

    public SkinService getSkinService() {
        return skinService;
    }

    public PlayerPreferenceService getPreferenceService() {
        return preferenceService;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public UpdateCheckerService getUpdateCheckerService() {
        return updateCheckerService;
    }

    public String message(String key) {
        return prefix() + languageManager.get(key);
    }

    public String message(String key, Map<String, String> placeholders) {
        return prefix() + languageManager.get(key, placeholders);
    }

    public boolean shouldRenderChatHeadFor(net.kyori.adventure.audience.Audience viewer) {
        if (!(viewer instanceof org.bukkit.entity.Player player)) {
            return true;
        }
        return isChatFeatureEnabled() && isChatEnabledFor(player);
    }

    public boolean shouldRenderChatHeadFor(org.bukkit.command.CommandSender viewer) {
        if (!(viewer instanceof org.bukkit.entity.Player player)) {
            return true;
        }
        return isChatFeatureEnabled() && isChatEnabledFor(player);
    }

    public boolean shouldRenderTabHeadFor(org.bukkit.entity.Player viewer) {
        return isTabFeatureEnabled() && isTabEnabledFor(viewer);
    }

    public void captureTabBaseName(org.bukkit.entity.Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Component stored = readStoredTabBaseName(player);
        if (stored != null) {
            tabBaseNames.put(playerId, stored);
            return;
        }

        Component base = currentTabBaseName(player);
        tabBaseNames.put(playerId, base);
        writeStoredTabBaseName(player, base);
    }

    public void forgetTabBaseName(UUID playerId) {
        if (playerId != null) {
            tabBaseNames.remove(playerId);
        }
    }

    public void refreshTabListName(org.bukkit.entity.Player player) {
        if (player == null) {
            return;
        }
        Component base = tabBaseNames.computeIfAbsent(player.getUniqueId(), ignored -> {
            Component stored = readStoredTabBaseName(player);
            if (stored != null) {
                return stored;
            }
            Component current = currentTabBaseName(player);
            writeStoredTabBaseName(player, current);
            return current;
        });

        if (!shouldRenderTabHeadFor(player)) {
            player.playerListName(base);
            tabRenderedNames.remove(player.getUniqueId());
            clearStoredTabRenderedName(player);
            return;
        }

        Component current = currentTabBaseName(player);
        Component headed = Utils.prependHead(
                skinService,
                base,
                player.getUniqueId(),
                player.getName(),
                isTabHeadSpaceEnabled()
        );
        if (headed.equals(current)) {
            tabRenderedNames.put(player.getUniqueId(), headed);
            writeStoredTabRenderedName(player, headed);
            return;
        }
        tabRenderedNames.put(player.getUniqueId(), headed);
        writeStoredTabRenderedName(player, headed);
        player.playerListName(headed);
    }

    public void refreshTabListNameLater(org.bukkit.entity.Player player, long delayTicks) {
        if (player == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> refreshTabListName(player), Math.max(0L, delayTicks));
    }

    public void refreshAllTabListNames() {
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            refreshTabListName(player);
        }
    }

    public void restoreAllTabListNames() {
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            restoreTabListName(player);
        }
    }

    public void restoreTabListName(org.bukkit.entity.Player player) {
        if (player == null) {
            return;
        }
        Component base = tabBaseNames.computeIfAbsent(player.getUniqueId(), ignored -> {
            Component stored = readStoredTabBaseName(player);
            return stored != null ? stored : currentTabBaseName(player);
        });
        player.playerListName(base);
        tabRenderedNames.remove(player.getUniqueId());
        clearStoredTabRenderedName(player);
    }

    public boolean shouldRenderTabHeadFor(java.util.UUID viewerId) {
        if (viewerId == null) {
            return isTabFeatureEnabled();
        }
        org.bukkit.entity.Player viewer = getServer().getPlayer(viewerId);
        if (viewer == null) {
            if (preferenceService.hasTabPreference(viewerId)) {
                return isTabFeatureEnabled() && preferenceService.isTabEnabled(viewerId);
            }
            return isTabFeatureEnabled();
        }
        return shouldRenderTabHeadFor(viewer);
    }

    public boolean shouldReceiveUpdateNotifications(org.bukkit.entity.Player player) {
        return player != null && (player.isOp() || player.hasPermission("addhead.settings") || player.hasPermission("addhead.reload"));
    }

    public boolean toggleChatFor(org.bukkit.entity.Player player) {
        return preferenceService.toggleChat(player.getUniqueId(), isChatEnabledFor(player));
    }

    public boolean toggleTabFor(org.bukkit.entity.Player player) {
        boolean enabled = preferenceService.toggleTab(player.getUniqueId(), isTabEnabledFor(player));
        refreshTabListName(player);
        return enabled;
    }

    public TabFixResult fixTabMiniMessageSetting() {
        Plugin tabPlugin = getServer().getPluginManager().getPlugin("TAB");
        if (!(tabPlugin instanceof JavaPlugin tabJavaPlugin)) {
            return new TabFixResult(false, "command.tab-fix.not-installed", Map.of());
        }

        File configFile = new File(tabJavaPlugin.getDataFolder(), "config.yml");
        if (!configFile.isFile()) {
            return new TabFixResult(false, "command.tab-fix.config-missing", Map.of());
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        boolean currentValue = config.getBoolean("components.minimessage-support", true);
        if (!currentValue) {
            dispatchTabReload();
            return new TabFixResult(false, "command.tab-fix.already-disabled", Map.of());
        }

        config.set("components.minimessage-support", false);
        try {
            config.save(configFile);
        } catch (IOException exception) {
            String error = exception.getMessage() == null ? "unknown" : exception.getMessage();
            return new TabFixResult(false, "command.tab-fix.save-failed", Map.of("error", error));
        }

        dispatchTabReload();
        return new TabFixResult(true, "command.tab-fix.disabled", Map.of());
    }

    private void dispatchTabReload() {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab reload");
    }

    private boolean isChatEnabledFor(org.bukkit.entity.Player player) {
        UUID playerId = player.getUniqueId();
        if (preferenceService.hasChatPreference(playerId)) {
            return preferenceService.isChatEnabled(playerId);
        }
        return !isPremiumSuppressed(player, "premium.disable-chat-heads");
    }

    private boolean isTabEnabledFor(org.bukkit.entity.Player player) {
        if (!isTabFeatureEnabled()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (preferenceService.hasTabPreference(playerId)) {
            return preferenceService.isTabEnabled(playerId);
        }
        return !isPremiumSuppressed(player, "premium.disable-tab-heads");
    }

    private boolean isPremiumSuppressed(org.bukkit.entity.Player player, String path) {
        if (!getConfig().getBoolean("premium.enabled", false)) {
            return false;
        }
        if (!isPremiumPlayer(player)) {
            return false;
        }
        return getConfig().getBoolean(path, false);
    }

    private boolean isPremiumPlayer(org.bukkit.entity.Player player) {
        String mode = getConfig().getString("premium.mode", "auto");
        if ("permission".equalsIgnoreCase(mode)) {
            String permission = getConfig().getString("premium.permission", "addhead.premium");
            return permission != null && !permission.isBlank() && player.hasPermission(permission);
        }

        if ("limboauth".equalsIgnoreCase(mode) || "auto".equalsIgnoreCase(mode)) {
            Boolean limboAuthPremium = resolvePremiumFromLimboAuth(player);
            if (limboAuthPremium != null) {
                return limboAuthPremium;
            }
        }

        if (getServer().getOnlineMode()) {
            return true;
        }

        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + player.getName()).getBytes(StandardCharsets.UTF_8));
        return !offlineUuid.equals(player.getUniqueId());
    }

    private Component currentTabBaseName(org.bukkit.entity.Player player) {
        Component name = player.playerListName();
        return name != null ? name : Component.text(player.getName());
    }

    private Component readStoredTabBaseName(org.bukkit.entity.Player player) {
        if (tabBaseNameKey == null || player == null) {
            return null;
        }
        String json = player.getPersistentDataContainer().get(tabBaseNameKey, PersistentDataType.STRING);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return GsonComponentSerializer.gson().deserialize(json);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void writeStoredTabBaseName(org.bukkit.entity.Player player, Component component) {
        if (tabBaseNameKey == null || player == null || component == null) {
            return;
        }
        try {
            player.getPersistentDataContainer().set(
                    tabBaseNameKey,
                    PersistentDataType.STRING,
                    GsonComponentSerializer.gson().serialize(component)
            );
        } catch (RuntimeException ignored) {
            // Persistent storage is best-effort; in-memory fallback still works.
        }
    }

    private void writeStoredTabRenderedName(org.bukkit.entity.Player player, Component component) {
        if (tabRenderedNameKey == null || player == null || component == null) {
            return;
        }
        try {
            player.getPersistentDataContainer().set(
                    tabRenderedNameKey,
                    PersistentDataType.STRING,
                    GsonComponentSerializer.gson().serialize(component)
            );
        } catch (RuntimeException ignored) {
            // Persistent storage is best-effort; in-memory fallback still works.
        }
    }

    private void clearStoredTabRenderedName(org.bukkit.entity.Player player) {
        if (tabRenderedNameKey == null || player == null) {
            return;
        }
        try {
            player.getPersistentDataContainer().remove(tabRenderedNameKey);
        } catch (RuntimeException ignored) {
            // Best effort only.
        }
    }

    private void startTabRefreshTask() {
        stopTabRefreshTask();
        long intervalSeconds = Math.max(1L, getConfig().getLong("tab.refresh-interval-seconds", 20L));
        long intervalTicks = intervalSeconds * 20L;
        tabRefreshTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::refreshAllTabListNames, intervalTicks, intervalTicks);
    }

    private void stopTabRefreshTask() {
        if (tabRefreshTaskId != -1) {
            Bukkit.getScheduler().cancelTask(tabRefreshTaskId);
            tabRefreshTaskId = -1;
        }
    }

    private Boolean resolvePremiumFromLimboAuth(org.bukkit.entity.Player player) {
        Plugin limboAuthPlugin = getServer().getPluginManager().getPlugin("LimboAuth");
        if (limboAuthPlugin == null) {
            limboAuthPlugin = getServer().getPluginManager().getPlugin("LimboAUF");
        }
        if (limboAuthPlugin == null) {
            return null;
        }

        List<Object> targets = new ArrayList<>();
        targets.add(limboAuthPlugin);
        for (String accessor : List.of("getPremiumService", "getAuthService", "getApi", "getService", "getManager")) {
            try {
                Method method = limboAuthPlugin.getClass().getMethod(accessor);
                Object target = method.invoke(limboAuthPlugin);
                if (target != null) {
                    targets.add(target);
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next accessor.
            }
        }

        for (Object target : targets) {
            List<Method> methods = new ArrayList<>();
            methods.add(findMethod(target.getClass(), "isPremium", org.bukkit.entity.Player.class));
            methods.add(findMethod(target.getClass(), "isPremium", UUID.class));
            methods.add(findMethod(target.getClass(), "isPremium", String.class));
            methods.add(findMethod(target.getClass(), "isPlayerPremium", org.bukkit.entity.Player.class));
            methods.add(findMethod(target.getClass(), "isPlayerPremium", UUID.class));
            methods.add(findMethod(target.getClass(), "isPlayerPremium", String.class));
            methods.add(findMethod(target.getClass(), "hasPremium", org.bukkit.entity.Player.class));
            methods.add(findMethod(target.getClass(), "hasPremium", UUID.class));
            methods.add(findMethod(target.getClass(), "hasPremium", String.class));

            for (Method method : methods) {
                if (method == null || !method.getReturnType().equals(boolean.class) && !method.getReturnType().equals(Boolean.class)) {
                    continue;
                }
                try {
                    Object argument = switch (method.getParameterTypes()[0].getName()) {
                        case "org.bukkit.entity.Player" -> player;
                        case "java.util.UUID" -> player.getUniqueId();
                        case "java.lang.String" -> player.getName();
                        default -> null;
                    };
                    if (argument == null) {
                        continue;
                    }
                    Object value = method.invoke(target, argument);
                    if (value instanceof Boolean bool) {
                        return bool;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Try the next candidate.
                }
            }
        }

        return null;
    }

    private Method findMethod(Class<?> type, String name, Class<?> parameterType) {
        try {
            return type.getMethod(name, parameterType);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private String prefix() {
        String value = getConfig().getString("messages.prefix", "&b[AddHeads] &r");
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', value);
    }

    private void refreshConfigDefaults() {
        File configFile = new File(getDataFolder(), "config.yml");
        YamlConfiguration fileConfig = YamlConfiguration.loadConfiguration(configFile);
        YamlConfiguration defaultConfig = loadBundledConfig();
        if (defaultConfig == null) {
            return;
        }

        if (mergeMissingConfigKeys(fileConfig, defaultConfig)) {
            try {
                fileConfig.save(configFile);
            } catch (IOException exception) {
                getLogger().warning("Failed to update config.yml with new defaults: " + exception.getMessage());
            }
        }
        getConfig().setDefaults(defaultConfig);
        getConfig().options().copyDefaults(true);
    }

    private YamlConfiguration loadBundledConfig() {
        try (InputStream in = getResource("config.yml")) {
            if (in == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            getLogger().warning("Failed to load bundled config defaults: " + exception.getMessage());
            return null;
        }
    }

    private boolean mergeMissingConfigKeys(YamlConfiguration target, ConfigurationSection templateSection) {
        return mergeMissingConfigKeys(target, templateSection, "");
    }

    private boolean mergeMissingConfigKeys(YamlConfiguration target, ConfigurationSection templateSection, String path) {
        boolean changed = false;
        for (String key : templateSection.getKeys(false)) {
            String fullPath = path.isEmpty() ? key : path + "." + key;
            Object templateValue = templateSection.get(key);
            if (templateValue instanceof ConfigurationSection) {
                if (!target.isConfigurationSection(fullPath)) {
                    if (target.contains(fullPath)) {
                        target.set(fullPath, null);
                    }
                    target.createSection(fullPath);
                    changed = true;
                }
                ConfigurationSection childSection = templateSection.getConfigurationSection(key);
                if (childSection != null) {
                    changed |= mergeMissingConfigKeys(target, childSection, fullPath);
                }
                continue;
            }

            if (!target.contains(fullPath)) {
                target.set(fullPath, templateValue);
                changed = true;
            }
        }
        return changed;
    }

    public record TabFixResult(boolean changed, String messageKey, Map<String, String> placeholders) {
    }
}
