package goshkow.addhead;

import goshkow.addhead.api.AddHeadsProvider;
import goshkow.addhead.api.SkinTexture;
import goshkow.addhead.api.event.AddHeadsReloadEvent;
import goshkow.addhead.api.event.AddHeadsSkinResolvedEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    private AddHeadsApiProvider apiProvider;
    private ProtocolLibTabViewHook protocolLibTabViewHook;
    private TabApiBridge tabApiBridge;
    private final ConcurrentMap<UUID, Component> tabBaseNames = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Component> tabRenderedNames = new ConcurrentHashMap<>();
    private NamespacedKey tabBaseNameKey;
    private NamespacedKey tabRenderedNameKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        refreshConfigDefaults();
        skinService = new SkinService(this);
        preferenceService = new PlayerPreferenceService(this);
        languageManager = new LanguageManager(this);
        settingsSessionService = new SettingsSessionService();
        settingsMenu = new SettingsMenu(this, settingsSessionService);
        apiProvider = new AddHeadsApiProvider(this);
        tabBaseNameKey = new NamespacedKey(this, "tab_base_name");
        tabRenderedNameKey = new NamespacedKey(this, "tab_rendered_name");
        preferenceService.load();
        languageManager.reload(getConfig().getString("language.file", "en-us.yml"));
        getServer().getServicesManager().register(AddHeadsProvider.class, apiProvider, this, ServicePriority.Normal);

        // Decorate the final chat component after external formatters finish their work.
        getServer().getPluginManager().registerEvents(new AsyncChatListener(this, skinService), this);

        getServer().getPluginManager().registerEvents(new SkinSyncListener(this, skinService), this);
        getServer().getPluginManager().registerEvents(new AdminNoticeListener(this), this);
        getServer().getPluginManager().registerEvents(new SettingsMenuListener(this, settingsMenu, settingsSessionService), this);
        skinService.start(getCacheRefreshIntervalTicks());
        protocolLibTabViewHook = new ProtocolLibTabViewHook(this);
        if (!protocolLibTabViewHook.start()) {
            protocolLibTabViewHook = null;
        }
        boolean tabReloadNeeded = synchronizeTabPluginConfig();
        startTabApiBridge();
        if (tabReloadNeeded) {
            scheduleTabReload();
        }
        updateCheckerService = new UpdateCheckerService(this);
        updateCheckerService.start();

        // Placeholder outputs are the supported compatibility layer for custom layouts and external plugins.
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
        if (protocolLibTabViewHook != null) {
            protocolLibTabViewHook.stop();
            protocolLibTabViewHook = null;
        }
        if (tabApiBridge != null) {
            tabApiBridge.stop();
            tabApiBridge = null;
        }
        restoreAllTabListNames();
        if (preferenceService != null) {
            preferenceService.save();
        }
        if (apiProvider != null) {
            getServer().getServicesManager().unregister(AddHeadsProvider.class, apiProvider);
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
        boolean tabReloadNeeded = synchronizeTabPluginConfig();
        if (tabApiBridge == null) {
            startTabApiBridge();
        }
        if (tabReloadNeeded) {
            scheduleTabReload();
        }
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            captureTabBaseName(player);
            syncPremiumDefaults(player);
        }
        refreshAllTabListNames();
        Bukkit.getPluginManager().callEvent(new AddHeadsReloadEvent());
    }

    public void reloadLanguage() {
        if (languageManager != null) {
            languageManager.reload(getConfig().getString("language.file", "en-us.yml"));
        }
    }

    public void restartSkinService() {
        if (skinService != null) {
            skinService.clear();
            skinService.start(getCacheRefreshIntervalTicks());
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

    public int getChatHeadSpacing() {
        return resolveHeadSpacing("formatting.chat-head-spacing", "formatting.chat-head-space", 2);
    }

    public boolean isChatHeadShadowEnabled() {
        return getConfig().getBoolean("formatting.chat-head-shadow", true);
    }

    public int getTabHeadSpacing() {
        return resolveHeadSpacing("formatting.tab-head-spacing", "formatting.tab-head-space", 2);
    }

    public boolean isTabHeadShadowEnabled() {
        return getConfig().getBoolean("formatting.tab-head-shadow", false);
    }

    public boolean isPremiumFeatureEnabled() {
        return getConfig().getBoolean("premium.enabled", false);
    }

    public String getPremiumMode() {
        return getConfig().getString("premium.mode", "auto_permission");
    }

    public String getLanguageFile() {
        return getConfig().getString("language.file", "en-us.yml");
    }

    public List<String> getAvailableLanguageFiles() {
        return languageManager.getAvailableLanguageFiles();
    }

    public long getCacheRefreshIntervalSeconds() {
        long seconds = getConfig().getLong("cache-refresh-interval-seconds", -1L);
        if (seconds < 0L) {
            seconds = getConfig().getLong("skin-refresh-interval-seconds", -1L);
        }
        if (seconds < 0L) {
            seconds = getConfig().getLong("tab.refresh-interval-seconds", -1L);
        }
        if (seconds < 0L) {
            long ticks = getConfig().getLong("skin-refresh-interval-ticks", 1200L);
            seconds = Math.max(1L, Math.round(ticks / 20.0d));
        }
        if (seconds < 0L) {
            seconds = 60L;
        }
        return Math.max(1L, seconds);
    }

    public long getCacheRefreshIntervalTicks() {
        return getCacheRefreshIntervalSeconds() * 20L;
    }

    public long getUpdateCheckIntervalHours() {
        return Math.max(1L, getConfig().getLong("update-check.interval-hours", 6L));
    }

    public long getUpdateCheckIntervalTicks() {
        return getUpdateCheckIntervalHours() * 60L * 60L * 20L;
    }

    public SkinTexture getResolvedSkinTexture(UUID playerId, String playerName) {
        Utils.TextureData texture = Utils.getCurrentTextureData(skinService, playerId, playerName);
        if (texture == null) {
            return null;
        }
        return new SkinTexture(texture.value(), texture.signature());
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

    public AddHeadsApiProvider getApiProvider() {
        return apiProvider;
    }

    public TabApiBridge getTabApiBridge() {
        return tabApiBridge;
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

        Component base = resolveCurrentTabBaseName(player);
        tabBaseNames.put(playerId, base);
        writeStoredTabBaseName(player, base);
    }

    public void forgetTabBaseName(UUID playerId) {
        if (playerId != null) {
            tabBaseNames.remove(playerId);
            tabRenderedNames.remove(playerId);
            if (protocolLibTabViewHook != null) {
                protocolLibTabViewHook.forgetPlayer(playerId);
            }
        }
    }

    public void refreshTabListName(org.bukkit.entity.Player player) {
        if (player == null) {
            return;
        }
        if (tabApiBridge != null && tabApiBridge.isManagingTabFormatting()) {
            tabApiBridge.refreshTarget(player);
            return;
        }
        if (isPerViewerTabRenderingEnabled()) {
            protocolLibTabViewHook.refreshEntryForAllViewers(player);
            return;
        }
        Component base = tabBaseNames.computeIfAbsent(player.getUniqueId(), ignored -> {
            Component stored = readStoredTabBaseName(player);
            if (stored != null) {
                return stored;
            }
            Component current = resolveCurrentTabBaseName(player);
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
        Component headed = Utils.prependTabHead(
                skinService,
                base,
                player.getUniqueId(),
                player.getName(),
                getTabHeadSpacing(),
                isTabHeadShadowEnabled()
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

    public void refreshTabView(org.bukkit.entity.Player viewer) {
        if (viewer == null) {
            return;
        }
        if (tabApiBridge != null && tabApiBridge.isManagingTabFormatting()) {
            tabApiBridge.refreshViewer(viewer);
            return;
        }
        if (isPerViewerTabRenderingEnabled()) {
            protocolLibTabViewHook.refreshViewer(viewer);
            return;
        }
        refreshTabListName(viewer);
    }

    public void refreshTabViewLater(org.bukkit.entity.Player viewer, long delayTicks) {
        if (viewer == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> refreshTabView(viewer), Math.max(0L, delayTicks));
    }

    public void refreshAllTabListNames() {
        if (tabApiBridge != null && tabApiBridge.isManagingTabFormatting()) {
            tabApiBridge.refreshAll();
            return;
        }
        if (isPerViewerTabRenderingEnabled()) {
            protocolLibTabViewHook.refreshAllViewers();
            return;
        }
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
        if (tabApiBridge != null && tabApiBridge.isManagingTabFormatting()) {
            tabApiBridge.clearTarget(player);
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

    public boolean isTabPluginPresent() {
        return getServer().getPluginManager().getPlugin("TAB") != null;
    }

    public boolean isTabApiCompatibilityEnabled() {
        return tabApiBridge != null && tabApiBridge.isAvailable();
    }

    public boolean isTabFormattingCompatibilityEnabled() {
        return tabApiBridge != null && tabApiBridge.isManagingTabFormatting();
    }

    private File getTabConfigFile() {
        return new File(new File(getDataFolder().getParentFile(), "TAB"), "config.yml");
    }

    private void startTabApiBridge() {
        if (!isTabFeatureEnabled()) {
            if (tabApiBridge != null) {
                tabApiBridge.stop();
                tabApiBridge = null;
            }
            return;
        }

        TabApiBridge bridge = new TabApiBridge(this);
        if (bridge.start()) {
            tabApiBridge = bridge;
            return;
        }

        tabApiBridge = null;
        if (!isTabPluginPresent()) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (tabApiBridge != null || !isTabPluginPresent()) {
                return;
            }
            TabApiBridge delayedBridge = new TabApiBridge(this);
            if (delayedBridge.start()) {
                tabApiBridge = delayedBridge;
                refreshAllTabListNames();
            }
        }, 40L);
    }

    private void scheduleTabReload() {
        if (!isTabPluginPresent()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab reload"), 20L);
    }

    public boolean isTabListFormattingLikelyEnabled() {
        if (!isTabPluginPresent()) {
            return false;
        }

        File tabConfigFile = getTabConfigFile();
        if (!tabConfigFile.isFile()) {
            return true;
        }

        YamlConfiguration tabConfig = YamlConfiguration.loadConfiguration(tabConfigFile);
        return tabConfig.getBoolean("tablist-name-formatting.enabled", true);
    }

    public boolean synchronizeTabPluginConfig() {
        if (!isTabPluginPresent() || !isTabFeatureEnabled()) {
            return false;
        }

        File tabConfigFile = getTabConfigFile();
        if (!tabConfigFile.isFile()) {
            return false;
        }

        YamlConfiguration tabConfig = YamlConfiguration.loadConfiguration(tabConfigFile);
        boolean changed = false;

        if (tabConfig.getBoolean("components.minimessage-support", true)) {
            tabConfig.set("components.minimessage-support", false);
            changed = true;
        }

        boolean disableShadowForHeads = !isTabHeadShadowEnabled();
        if (tabConfig.getBoolean("components.disable-shadow-for-heads", false) != disableShadowForHeads) {
            tabConfig.set("components.disable-shadow-for-heads", disableShadowForHeads);
            changed = true;
        }

        if (!changed) {
            return false;
        }

        try {
            tabConfig.save(tabConfigFile);
            getLogger().info("Synchronized TAB component settings for AddHeads.");
            return true;
        } catch (IOException exception) {
            getLogger().warning("Failed to update TAB config for AddHeads compatibility: " + exception.getMessage());
            return false;
        }
    }

    public boolean toggleChatFor(org.bukkit.entity.Player player) {
        return preferenceService.toggleChat(player.getUniqueId(), isChatEnabledFor(player));
    }

    public boolean toggleTabFor(org.bukkit.entity.Player player) {
        boolean enabled = preferenceService.toggleTab(player.getUniqueId(), isTabEnabledFor(player));
        refreshTabView(player);
        return enabled;
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

    public boolean isPremiumPlayerDetected(org.bukkit.entity.Player player) {
        return isPremiumPlayer(player);
    }

    public void syncPremiumDefaults(org.bukkit.entity.Player player) {
        if (player == null || preferenceService == null) {
            return;
        }

        if (!isPremiumFeatureEnabled()) {
            preferenceService.clearPremiumState(player.getUniqueId());
            return;
        }

        boolean premium = isPremiumPlayer(player);
        UUID playerId = player.getUniqueId();
        boolean hadState = preferenceService.hasPremiumState(playerId);
        boolean previous = hadState && preferenceService.isPremiumState(playerId);
        preferenceService.setPremiumState(playerId, premium);

        if (!premium) {
            return;
        }

        if (!hadState || !previous) {
            preferenceService.setChat(playerId, !getConfig().getBoolean("premium.disable-chat-heads", false));
            preferenceService.setTab(playerId, !getConfig().getBoolean("premium.disable-tab-heads", false));
            refreshTabView(player);
        }
    }

    public boolean isPerViewerTabRenderingEnabled() {
        return protocolLibTabViewHook != null;
    }

    private boolean isPremiumPlayer(org.bukkit.entity.Player player) {
        String mode = getConfig().getString("premium.mode", "auto_permission");
        String permission = getConfig().getString("premium.permission", "addhead.premium");
        boolean hasPermission = permission != null && !permission.isBlank() && player.hasPermission(permission);

        if ("permission".equalsIgnoreCase(mode)) {
            return hasPermission;
        }

        boolean autoDetected = isPremiumAutoDetected(player);
        if ("auto".equalsIgnoreCase(mode)) {
            return autoDetected;
        }

        if ("auto_permission".equalsIgnoreCase(mode)) {
            return hasPermission || autoDetected;
        }

        return hasPermission || autoDetected;
    }

    private boolean isPremiumAutoDetected(org.bukkit.entity.Player player) {
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

    private Component resolveCurrentTabBaseName(org.bukkit.entity.Player player) {
        Component current = currentTabBaseName(player);
        Component rendered = tabRenderedNames.get(player.getUniqueId());
        if (rendered != null && rendered.equals(current)) {
            Component storedBase = readStoredTabBaseName(player);
            if (storedBase != null) {
                return storedBase;
            }
        }

        Component storedRendered = readStoredTabRenderedName(player);
        if (storedRendered != null && storedRendered.equals(current)) {
            Component storedBase = readStoredTabBaseName(player);
            if (storedBase != null) {
                return storedBase;
            }
        }

        return current;
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

    private Component readStoredTabRenderedName(org.bukkit.entity.Player player) {
        if (tabRenderedNameKey == null || player == null) {
            return null;
        }
        String json = player.getPersistentDataContainer().get(tabRenderedNameKey, PersistentDataType.STRING);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return GsonComponentSerializer.gson().deserialize(json);
        } catch (RuntimeException exception) {
            return null;
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

    private int resolveHeadSpacing(String newPath, String legacyBooleanPath, int defaultValue) {
        Object configured = getConfig().get(newPath);
        if (configured instanceof Number number) {
            return clampHeadSpacing(number.intValue());
        }
        if (configured instanceof String text) {
            try {
                return clampHeadSpacing(Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
                // Fall back to legacy value or default.
            }
        }

        if (getConfig().isBoolean(legacyBooleanPath)) {
            return getConfig().getBoolean(legacyBooleanPath) ? defaultValue : 0;
        }
        return clampHeadSpacing(defaultValue);
    }

    private int clampHeadSpacing(int value) {
        return Math.max(0, Math.min(10, value));
    }

    public void fireSkinResolvedEvent(UUID playerId, String playerName, Utils.TextureData textureData) {
        if (playerId == null || textureData == null || textureData.value() == null || textureData.value().isBlank()) {
            return;
        }
        SkinTexture texture = new SkinTexture(textureData.value(), textureData.signature());
        Bukkit.getScheduler().runTask(this, () ->
                Bukkit.getPluginManager().callEvent(new AddHeadsSkinResolvedEvent(playerId, playerName, texture))
        );
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
}
