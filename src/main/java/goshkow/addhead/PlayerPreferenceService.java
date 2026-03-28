package goshkow.addhead;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Stores per-player visibility preferences for AddHeads features.
 */
public final class PlayerPreferenceService {

    private final File file;
    private YamlConfiguration config;

    public PlayerPreferenceService(Plugin plugin) {
        this.file = new File(plugin.getDataFolder(), "preferences.yml");
    }

    public void load() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException ignored) {
        }
    }

    public boolean hasChatPreference(UUID playerId) {
        return config.contains(path(playerId, "chat"));
    }

    public boolean hasTabPreference(UUID playerId) {
        return config.contains(path(playerId, "tab"));
    }

    public boolean hasPremiumState(UUID playerId) {
        return config.contains(path(playerId, "premium"));
    }

    public boolean isPremiumState(UUID playerId) {
        return config.getBoolean(path(playerId, "premium"), false);
    }

    public boolean isChatEnabled(UUID playerId) {
        return config.getBoolean(path(playerId, "chat"), true);
    }

    public boolean isTabEnabled(UUID playerId) {
        return config.getBoolean(path(playerId, "tab"), true);
    }

    public boolean setChat(UUID playerId, boolean value) {
        config.set(path(playerId, "chat"), value);
        save();
        return value;
    }

    public boolean setTab(UUID playerId, boolean value) {
        config.set(path(playerId, "tab"), value);
        save();
        return value;
    }

    public boolean toggleChat(UUID playerId, boolean currentValue) {
        boolean next = !currentValue;
        config.set(path(playerId, "chat"), next);
        save();
        return next;
    }

    public boolean toggleTab(UUID playerId, boolean currentValue) {
        boolean next = !currentValue;
        config.set(path(playerId, "tab"), next);
        save();
        return next;
    }

    public void setPremiumState(UUID playerId, boolean premium) {
        config.set(path(playerId, "premium"), premium);
        save();
    }

    public void clearPremiumState(UUID playerId) {
        config.set(path(playerId, "premium"), null);
        save();
    }

    private String path(UUID playerId, String key) {
        return "players." + playerId + "." + key;
    }
}
